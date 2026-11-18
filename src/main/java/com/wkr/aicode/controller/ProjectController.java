package com.wkr.aicode.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.annotation.AuthCheck;
import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.DeleteRequest;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.constant.UserConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.dto.project.ProjectAddRequest;
import com.wkr.aicode.model.dto.project.ProjectQueryRequest;
import com.wkr.aicode.model.dto.project.ProjectUpdateRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectVO;
import com.wkr.aicode.service.ProjectService;
import com.wkr.aicode.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目商品 控制层。
 *
 * @author <a>百夜</a>
 */
@Tag(name = "projectController", description = "项目的发布、浏览、编辑、管理接口")
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @Resource
    private UserService userService;

    /**
     * 发布项目
     */
    @Operation(summary = "发布模板（管理员）", description = "管理员发布免费模板，需登记模板来源和许可条件")
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addProject(@RequestBody ProjectAddRequest addRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long projectId = projectService.createProject(addRequest, loginUser);
        return ResultUtils.success(projectId);
    }

    /**
     * 更新项目（仅本人可编辑）
     */
    @Operation(summary = "编辑项目", description = "作者可编辑自己的草稿和下架项目，公开模板由管理员维护")
    @PostMapping("/update")
    public BaseResponse<Boolean> updateProject(@RequestBody ProjectUpdateRequest updateRequest, HttpServletRequest request) {
        if (updateRequest == null || updateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = projectService.updateProject(updateRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 删除/下架项目（本人或管理员）
     */
    @Operation(summary = "删除项目", description = "项目作者或管理员删除项目（逻辑删除），仅本人或管理员可操作")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteProject(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        Project oldProject = projectService.getById(id);
        ThrowUtils.throwIf(oldProject == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldProject.getAuthorId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = projectService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 获取项目详情（公开）
     */
    @Operation(summary = "获取模板详情", description = "公开展示已上架的免费授权模板")
    @GetMapping("/get/vo")
    public BaseResponse<ProjectVO> getProjectVOById(@RequestParam long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Project project = projectService.getPublicTemplate(id);
        projectService.incrementViewCount(id);
        ProjectVO template = projectService.getProjectVO(project);
        template.setSourceUrl(null);
        return ResultUtils.success(template);
    }

    @Operation(summary = "获取可编辑的项目详情", description = "作者或管理员查看待编辑项目及其源码地址")
    @GetMapping("/edit/get/vo")
    public BaseResponse<ProjectVO> getEditableProjectVO(@RequestParam long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Project project = projectService.getById(id);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        ThrowUtils.throwIf(!project.getAuthorId().equals(loginUser.getId())
                        && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看待编辑项目");
        return ResultUtils.success(projectService.getProjectVO(project));
    }

    /**
     * 分页浏览已发布项目（公开）
     */
    @Operation(summary = "分页浏览模板", description = "公开查询已上架的免费授权模板")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<ProjectVO>> listProjectVOByPage(@RequestBody ProjectQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个项目");
        long pageNum = queryRequest.getPageNum();
        QueryWrapper queryWrapper = projectService.getPublicTemplateQuery(queryRequest);
        Page<Project> projectPage = projectService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ProjectVO> projectVOPage = new Page<>(pageNum, pageSize, projectPage.getTotalRow());
        List<ProjectVO> projectVOList = projectService.getProjectVOList(projectPage.getRecords());
        projectVOList.forEach(template -> template.setSourceUrl(null));
        projectVOPage.setRecords(projectVOList);
        return ResultUtils.success(projectVOPage);
    }

    /**
     * 我发布的项目列表
     */
    @Operation(summary = "我发布的项目列表", description = "登录用户查看自己发布的全部项目（含草稿、已发布、已下架）")
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<ProjectVO>> listMyProjectVOByPage(@RequestBody ProjectQueryRequest queryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个项目");
        long pageNum = queryRequest.getPageNum();
        queryRequest.setAuthorId(loginUser.getId());
        QueryWrapper queryWrapper = projectService.getQueryWrapper(queryRequest);
        Page<Project> projectPage = projectService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ProjectVO> projectVOPage = new Page<>(pageNum, pageSize, projectPage.getTotalRow());
        List<ProjectVO> projectVOList = projectService.getProjectVOList(projectPage.getRecords());
        projectVOPage.setRecords(projectVOList);
        return ResultUtils.success(projectVOPage);
    }

    /**
     * 管理员分页获取所有项目
     */
    @Operation(summary = "管理员查看所有项目", description = "管理员分页查看全部项目（含所有状态），不受发布状态限制")
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ProjectVO>> listProjectVOByPageByAdmin(@RequestBody ProjectQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        QueryWrapper queryWrapper = projectService.getQueryWrapper(queryRequest);
        Page<Project> projectPage = projectService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ProjectVO> projectVOPage = new Page<>(pageNum, pageSize, projectPage.getTotalRow());
        List<ProjectVO> projectVOList = projectService.getProjectVOList(projectPage.getRecords());
        projectVOPage.setRecords(projectVOList);
        return ResultUtils.success(projectVOPage);
    }

    /**
     * 管理员更新项目（审核/上下架）
     */
    @Operation(summary = "管理员更新项目", description = "管理员编辑任意项目信息，可用于审核、上下架等操作")
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateProjectByAdmin(@RequestBody ProjectUpdateRequest updateRequest, HttpServletRequest request) {
        if (updateRequest == null || updateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(projectService.updateProject(updateRequest, loginUser));
    }

    /**
     * 管理员根据 ID 获取项目详情
     */
    @Operation(summary = "管理员查看项目详情", description = "管理员根据ID查看任意项目的完整信息")
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<ProjectVO> getProjectVOByIdByAdmin(@RequestParam long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Project project = projectService.getById(id);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(projectService.getProjectVO(project));
    }
}
