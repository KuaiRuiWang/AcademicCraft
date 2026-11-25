package com.wkr.aicode.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import com.wkr.aicode.admin.AdminAccountService;
import com.wkr.aicode.admin.AdminPermission;
import com.wkr.aicode.admin.AdminSessionService;
import com.wkr.aicode.aop.AuthInterceptor;
import com.wkr.aicode.constant.UserConstant;
import com.wkr.aicode.core.model.ModelEndpoints;
import com.wkr.aicode.core.model.ModelKeyCipher;
import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.GlobalExceptionHandler;
import com.wkr.aicode.mapper.PlatformAdminMapper;
import com.wkr.aicode.mapper.PlatformAdminRoleMapper;
import com.wkr.aicode.mapper.PlatformModelBindingMapper;
import com.wkr.aicode.mapper.PlatformModelMapper;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.service.PlatformModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class PlatformAdminIntegrationTest {
    private static final String ROOT_PASSWORD = "test-root-password";
    private static final String READER_PASSWORD = "test-reader-password";
    private static final String MODEL_KEY = "fixture-provider-key";
    private static final String MODEL_PAYLOAD = """
            {"name":"Flash 测试","protocol":"OPENAI_COMPATIBLE","baseUrl":"https://api.deepseek.com/",
             "modelId":"deepseek-flash","apiKey":"fixture-provider-key","maxTokens":4000,"temperature":0.2}
            """;

    private final ObjectMapper json = new ObjectMapper();
    private final ModelKeyCipher keys = new ModelKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private AdminAccountService accounts;
    private PlatformModelMapper models;
    private PlatformModelBindingMapper bindings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        var database = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        String migration = Files.readString(Path.of("sql/upgrade_platform_admin.sql"));
        String schema = migration.substring(0, migration.indexOf("ALTER TABLE project"))
                .replaceAll("(?i)collate\\s*=\\s*utf8mb4_unicode_ci", "")
                .replaceAll("(?i)on\\s+update\\s+CURRENT_TIMESTAMP", "")
                .replaceAll("(?i)comment\\s+'[^']*'", "");
        new ResourceDatabasePopulator(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8))).execute(database);
        var factory = new FlexSqlSessionFactoryBean();
        var configuration = new FlexConfiguration();
        factory.setConfiguration(configuration);
        factory.setDataSource(database);
        var sessionFactory = factory.getObject();
        configuration.addMapper(PlatformAdminMapper.class);
        configuration.addMapper(PlatformAdminRoleMapper.class);
        configuration.addMapper(PlatformModelMapper.class);
        configuration.addMapper(PlatformModelBindingMapper.class);
        var sql = new SqlSessionTemplate(sessionFactory);

        accounts = new AdminAccountService(sql.getMapper(PlatformAdminMapper.class), sql.getMapper(PlatformAdminRoleMapper.class));
        models = sql.getMapper(PlatformModelMapper.class);
        bindings = sql.getMapper(PlatformModelBindingMapper.class);
        ReflectionTestUtils.setField(accounts, "bootstrapUsername", "ops-root");
        ReflectionTestUtils.setField(accounts, "bootstrapPassword", ROOT_PASSWORD);
        accounts.bootstrap();
        accounts.bootstrap();
        assertEquals(1, accounts.accounts().size());

        var sessions = new AdminSessionService(redisFixture(), accounts);
        var modelService = new PlatformModelService(models, bindings, new ModelEndpoints(""), keys, new MockEnvironment());
        mvc = MockMvcBuilders.standaloneSetup(secure(new PlatformAdminController(sessions, accounts), sessions),
                        secure(new PlatformModelController(modelService), sessions))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void isolatesAccountsAndEnforcesCurrentRoleOnEveryRequest() throws Exception {
        var legacy = new MockHttpSession();
        legacy.setAttribute(UserConstant.USER_LOGIN_STATE, User.builder().id(10L).userRole("admin").build());
        mvc.perform(get("/admin/models").session(legacy))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
        mvc.perform(post("/admin/auth/login").session(legacy).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"consumer\",\"password\":\"old-password\"}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));

        String root = login("ops-root", ROOT_PASSWORD);
        long role = responseData(as(root, post("/admin/roles").contentType(APPLICATION_JSON)
                .content("{\"name\":\"模型查看\",\"permissions\":[\"MODEL_READ\"]}"))).path("id").asLong();
        JsonNode reader = responseData(as(root, post("/admin/accounts").contentType(APPLICATION_JSON)
                .content("""
                        {"username":"ops-reader","displayName":"只读账号","password":"test-reader-password",
                         "roleId":%d,"enabled":true}
                        """.formatted(role))));
        assertFalse(reader.has("passwordHash"));
        assertFalse(reader.has("sessionVersion"));
        long readerId = reader.path("id").asLong();
        String readerToken = login("ops-reader", READER_PASSWORD);
        as(readerToken, get("/admin/models")).andExpect(jsonPath("$.code").value(0));
        as(readerToken, post("/admin/models").contentType(APPLICATION_JSON).content(MODEL_PAYLOAD))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        as(readerToken, get("/admin/accounts")).andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));

        accounts.saveRole(role, new AdminAccountService.RoleInput("步骤查看", Set.of(AdminPermission.BINDING_READ)));
        as(readerToken, get("/admin/models")).andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        as(readerToken, get("/admin/model-bindings")).andExpect(jsonPath("$.code").value(0));
        as(root, delete("/admin/roles/" + role)).andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_ERROR.getCode()));
        accounts.saveAccount(readerId, new AdminAccountService.AccountInput("ops-reader", "只读账号", null, role, false));
        as(readerToken, get("/admin/auth/session")).andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
        as(root, post("/admin/auth/logout")).andExpect(jsonPath("$.code").value(0));
        as(root, get("/admin/auth/session")).andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
    }

    @Test
    void keepsCredentialsWriteOnlyAndBindsEachStepIndependently() throws Exception {
        String root = login("ops-root", ROOT_PASSWORD);
        JsonNode created = responseData(as(root, post("/admin/models").contentType(APPLICATION_JSON).content(MODEL_PAYLOAD)));
        long id = created.path("id").asLong();
        assertEquals("https://api.deepseek.com", created.path("baseUrl").asText());
        assertTrue(created.path("hasApiKey").asBoolean());
        assertFalse(created.has("apiKey"));
        assertFalse(created.has("apiKeyCiphertext"));
        assertFalse(responseData(as(root, get("/admin/models"))).toString().contains(MODEL_KEY));
        String ciphertext = models.selectOneById(id).getApiKeyCiphertext();
        assertNotEquals(MODEL_KEY, ciphertext);
        assertEquals(MODEL_KEY, keys.decrypt(ciphertext));

        bind(root, ModelStep.FRONTEND_GENERATION, id, 0);
        bind(root, ModelStep.THESIS_CHAPTER, id, 0);
        bind(root, ModelStep.THESIS_CHAPTER, id, 0);
        bind(root, ModelStep.LOGO_IMAGE, id, ErrorCode.PARAMS_ERROR.getCode());
        assertEquals("deepseek-flash", bindings.findModel("FRONTEND_GENERATION").getModelId());
        assertNull(bindings.findModel("BACKEND_GENERATION"));
        assertNull(bindings.findModel("THESIS_REWRITE"));

        String edit = """
                {"name":"Flash 新名称","protocol":"OPENAI_COMPATIBLE",
                 "baseUrl":"https://api.deepseek.com","modelId":"deepseek-flash"}
                """;
        as(root, put("/admin/models/" + id).contentType(APPLICATION_JSON).content(edit.replace("api.deepseek.com", "api.openai.com/v1")))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMS_ERROR.getCode()));
        as(root, put("/admin/models/" + id).contentType(APPLICATION_JSON).content(edit)).andExpect(jsonPath("$.code").value(0));
        var updated = bindings.findModel("THESIS_CHAPTER");
        assertEquals("Flash 新名称", updated.getName());
        assertEquals(ciphertext, updated.getApiKeyCiphertext());
        assertNull(updated.getMaxTokens());
        assertNull(updated.getTemperature());
        as(root, delete("/admin/models/" + id)).andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_ERROR.getCode()));
        bind(root, ModelStep.FRONTEND_GENERATION, null, 0);
        bind(root, ModelStep.THESIS_CHAPTER, null, 0);
        as(root, delete("/admin/models/" + id)).andExpect(jsonPath("$.code").value(0));
        assertNull(models.selectOneById(id));
    }

    private String login(String username, String password) throws Exception {
        return responseData(mvc.perform(post("/admin/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new PlatformAdminController.LoginInput(username, password))))).path("token").asText();
    }

    private void bind(String token, ModelStep step, Long id, int responseCode) throws Exception {
        as(token, put("/admin/model-bindings/" + step).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new PlatformModelController.BindingInput(id))))
                .andExpect(jsonPath("$.code").value(responseCode));
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private JsonNode responseData(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(jsonPath("$.code").value(0)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private Object secure(Object controller, AdminSessionService sessions) {
        var proxy = new AspectJProxyFactory(controller);
        proxy.addAspect(new AuthInterceptor(sessions));
        return proxy.getProxy();
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisFixture() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        var stored = new HashMap<String, String>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> {
            stored.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(call -> stored.remove(call.getArgument(0)) != null);
        return redis;
    }
}
