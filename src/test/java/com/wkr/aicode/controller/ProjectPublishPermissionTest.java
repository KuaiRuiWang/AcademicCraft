package com.wkr.aicode.controller;

import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.admin.AdminAccountService;
import com.wkr.aicode.admin.AdminPermission;
import com.wkr.aicode.admin.AdminSessionService;
import com.wkr.aicode.aop.AuthInterceptor;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.GlobalExceptionHandler;
import com.wkr.aicode.model.dto.project.ProjectAddRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.enums.ProjectStatusEnum;
import com.wkr.aicode.model.enums.UserRoleEnum;
import com.wkr.aicode.service.UserService;
import com.wkr.aicode.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ProjectPublishPermissionTest {

    private final UserService userService = mock(UserService.class);
    private final AdminSessionService sessions = mock(AdminSessionService.class);
    private final ProjectServiceImpl projectService = spy(new ProjectServiceImpl());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "projectService", projectService);

        when(sessions.current(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(controller);
        proxyFactory.addAspect(new AuthInterceptor(sessions));
        ProjectController securedController = proxyFactory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(securedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Project project = new Project();
        project.setId(1L);
        project.setAuthorId(10L);
        project.setStatus(ProjectStatusEnum.TAKEN_DOWN.getValue());
        doReturn(project).when(projectService).getOne(any(QueryWrapper.class));
    }

    @ParameterizedTest
    @EnumSource(UserRoleEnum.class)
    void consumerAccountsCannotPublishTemplates(UserRoleEnum role) throws Exception {
        logInAs(role);
        mockMvc.perform(post("/project/add").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"测试项目\",\"categoryId\":1,\"price\":0}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
        verify(projectService, never()).createProject(any(ProjectAddRequest.class), anyLong());
    }

    @Test
    void platformPublisherProvidesItsOwnAdminIdentity() throws Exception {
        logInAsPublisher();
        doReturn(1L).when(projectService).createProject(any(ProjectAddRequest.class), anyLong());
        mockMvc.perform(post("/project/add").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"测试项目\",\"categoryId\":1,\"price\":0}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(projectService).createProject(any(ProjectAddRequest.class), eq(20L));
    }

    @Test
    void regularAuthorCannotRepublishProject() throws Exception {
        logInAs(UserRoleEnum.USER);

        mockMvc.perform(post("/project/update").contentType(APPLICATION_JSON)
                        .content("{\"id\":1,\"status\":" + ProjectStatusEnum.PUBLISHED.getValue() + "}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verify(projectService, never()).updateById(any(Project.class));
    }

    @Test
    void regularAuthorCanEditExistingProject() throws Exception {
        logInAs(UserRoleEnum.USER);
        doReturn(true).when(projectService).updateById(any(Project.class));

        mockMvc.perform(post("/project/update").contentType(APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"修改名称\",\"licenseTerms\":\"MIT\"}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(projectService).updateById(argThat(project -> "".equals(project.getLicenseTerms())));
    }

    @Test
    void adminCannotPublishAnUnreviewedDraft() throws Exception {
        logInAsPublisher();
        mockMvc.perform(post("/project/admin/update").contentType(APPLICATION_JSON)
                        .content("{\"id\":1,\"status\":1}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMS_ERROR.getCode()));
        verify(projectService, never()).updateById(any(Project.class));
    }

    @Test
    void adminCanPublishAfterProvidingFreeTemplateAndTerms() throws Exception {
        logInAsPublisher();
        doReturn(true).when(projectService).updateById(any(Project.class));

        mockMvc.perform(post("/project/admin/update").contentType(APPLICATION_JSON)
                        .content("""
                                {"id":1,"status":1,"price":0,"sourceUrl":"https://example.com/template.zip",
                                 "sourceAttribution":"原创模板","licenseTerms":"MIT，商用和再分发须保留许可声明"}
                                """))
                .andExpect(jsonPath("$.code").value(0));
    }

    private void logInAs(UserRoleEnum role) {
        User user = new User();
        user.setId(10L);
        user.setUserRole(role.getValue());
        when(userService.getLoginUser(any())).thenReturn(user);
    }

    private void logInAsPublisher() {
        doReturn(new AdminAccountService.Identity(20L, "发布管理员", "模板运营",
                Set.of(AdminPermission.TEMPLATE_WRITE))).when(sessions).current(any());
    }
}
