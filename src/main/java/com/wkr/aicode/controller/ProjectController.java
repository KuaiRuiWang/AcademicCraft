package com.wkr.aicode.controller;

import cn.hutool.core.bean.BeanUtil;
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
import com.wkr.aicode.model.enums.ProjectStatusEnum;
import com.wkr.aicode.model.vo.project.ProjectVO;
import com.wkr.aicode.service.ProjectService;
import com.wkr.aicode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目商品 控制层。
 *
 * @author <a>百夜</a>
 */
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
    @PostMapping("/add")
    public BaseResponse<Long> addProject(@RequestBody ProjectAddRequest addRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long projectId = projectService.createProject(addRequest, loginUser);
        return ResultUtils.success(projectId);
    }

    /**
     * 更新项目（仅本人可编辑）
     */
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
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteProject(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        Project oldProject = projectService.getById(id);
        ThrowUtils.throwIf(oldProject == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldProject.getAuthorId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = projectService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 获取项目详情（公开）,同时增加浏览量
     */
    @GetMapping("/get/vo")
    public BaseResponse<ProjectVO> getProjectVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Project project = projectService.getById(id);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR);
        // 增加浏览量
        projectService.incrementViewCount(id);
        return ResultUtils.success(projectService.getProjectVO(project));
    }

    /**
     * 分页浏览已发布项目（公开）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<ProjectVO>> listProjectVOByPage(@RequestBody ProjectQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个项目");
        long pageNum = queryRequest.getPageNum();
        // 公开列表只查已发布的项目
        if (queryRequest.getStatus() == null) {
            queryRequest.setStatus(ProjectStatusEnum.PUBLISHED.getValue());
        }
        QueryWrapper queryWrapper = projectService.getQueryWrapper(queryRequest);
        Page<Project> projectPage = projectService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<ProjectVO> projectVOPage = new Page<>(pageNum, pageSize, projectPage.getTotalRow());
        List<ProjectVO> projectVOList = projectService.getProjectVOList(projectPage.getRecords());
        projectVOPage.setRecords(projectVOList);
        return ResultUtils.success(projectVOPage);
    }

    /**
     * 我发布的项目列表
     */
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
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateProjectByAdmin(@RequestBody ProjectUpdateRequest updateRequest) {
        if (updateRequest == null || updateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Project oldProject = projectService.getById(updateRequest.getId());
        ThrowUtils.throwIf(oldProject == null, ErrorCode.NOT_FOUND_ERROR);
        Project project = new Project();
        BeanUtil.copyProperties(updateRequest, project);
        project.setEditTime(LocalDateTime.now());
        // 不覆盖敏感字段
        project.setAuthorId(null);
        project.setSalesCount(null);
        project.setViewCount(null);
        project.setAvgRating(null);
        boolean result = projectService.updateById(project);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员根据 ID 获取项目详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<ProjectVO> getProjectVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Project project = projectService.getById(id);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(projectService.getProjectVO(project));
    }
}
