package com.wkr.aicode.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.GlobalExceptionHandler;
import com.wkr.aicode.model.dto.project.ProjectQueryRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.ProjectOrder;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectVO;
import com.wkr.aicode.service.UserService;
import com.wkr.aicode.service.impl.ProjectOrderServiceImpl;
import com.wkr.aicode.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

class ProjectTemplateAccessTest {

    private final ProjectServiceImpl projectService = spy(new ProjectServiceImpl());
    private final ProjectOrderServiceImpl orderService = spy(new ProjectOrderServiceImpl());
    private final Project template = Project.builder().id(11L).authorId(1L).name("库存台账").status(1)
            .price(BigDecimal.ZERO).sourceUrl("https://example.com/inventory.zip")
            .sourceAttribution("原创模板").licenseTerms("MIT").salesCount(0).build();
    private final ProjectOrder order = ProjectOrder.builder().id(21L).projectId(11L).buyerId(2L)
            .sellerId(1L).amount(BigDecimal.ZERO).status(1).build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        when(userService.getLoginUser(any())).thenReturn(User.builder().id(2L).userRole("user").build());
        ProjectController catalog = new ProjectController();
        ProjectOrderController orders = new ProjectOrderController();
        ReflectionTestUtils.setField(catalog, "projectService", projectService);
        ReflectionTestUtils.setField(catalog, "userService", userService);
        ReflectionTestUtils.setField(orders, "projectService", projectService);
        ReflectionTestUtils.setField(orders, "projectOrderService", orderService);
        ReflectionTestUtils.setField(orders, "userService", userService);
        ReflectionTestUtils.setField(orderService, "projectService", projectService);
        doReturn(template).when(projectService).getById(11L);
        doReturn(template).when(projectService).getOne(any(QueryWrapper.class));
        doReturn(order).when(orderService).getById(21L);
        mockMvc = MockMvcBuilders.standaloneSetup(catalog, orders)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "unlisted", "priced", "unlicensed"})
    void nonPublicTemplatesCannotBeViewedClaimedOrDownloaded(String state) throws Exception {
        switch (state) {
            case "draft" -> template.setStatus(0);
            case "unlisted" -> template.setStatus(2);
            case "priced" -> template.setPrice(BigDecimal.TEN);
            case "unlicensed" -> template.setLicenseTerms(null);
            default -> throw new IllegalArgumentException(state);
        }

        mockMvc.perform(get("/project/get/vo").param("id", "11"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ERROR.getCode()));
        mockMvc.perform(post("/projectOrder/create").contentType(APPLICATION_JSON).content("{\"projectId\":11}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ERROR.getCode()));
        mockMvc.perform(get("/projectOrder/download/21"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_ERROR.getCode()));
        mockMvc.perform(get("/projectOrder/get/vo").param("id", "21"))
                .andExpect(jsonPath("$.data.projectName").value("库存台账"))
                .andExpect(jsonPath("$.data.downloadAvailable").value(false))
                .andExpect(jsonPath("$.data.sourceUrl").doesNotExist());
    }

    @Test
    void publicListForcesPublishedStatusAndChecksTheLicense() {
        ProjectQueryRequest query = new ProjectQueryRequest();
        query.setStatus(0);
        String sql = projectService.getPublicTemplateQuery(query).from("project").toSQL();
        assertEquals(1, query.getStatus());
        assertTrue(sql.contains("TRIM(licenseTerms)"));
        assertTrue(sql.contains("TRIM(sourceAttribution)"));
    }

    @Test
    void approvedTemplateShowsTermsAndUsesTheGatedDownload() throws Exception {
        doNothing().when(projectService).incrementViewCount(11L);
        doReturn(BeanUtil.copyProperties(template, ProjectVO.class)).when(projectService).getProjectVO(template);
        mockMvc.perform(get("/project/get/vo").param("id", "11"))
                .andExpect(jsonPath("$.data.licenseTerms").value("MIT"))
                .andExpect(jsonPath("$.data.sourceUrl").doesNotExist());
        mockMvc.perform(get("/projectOrder/download/21")).andExpect(redirectedUrl(template.getSourceUrl()));
    }

    @Test
    void repeatedFreeClaimReturnsTheSameRecord() throws Exception {
        doReturn(null, order).when(orderService).getOne(any(QueryWrapper.class));
        doAnswer(invocation -> {
            ProjectOrder saved = invocation.getArgument(0);
            assertEquals(BigDecimal.ZERO, saved.getAmount());
            assertEquals(1, saved.getStatus());
            saved.setId(order.getId());
            return true;
        }).when(orderService).save(any(ProjectOrder.class));
        doReturn(true).when(projectService).updateById(any(Project.class));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/projectOrder/create").contentType(APPLICATION_JSON).content("{\"projectId\":11}"))
                    .andExpect(jsonPath("$.data").value(21));
        }
        verify(orderService).save(any(ProjectOrder.class));
    }
}
