package com.wkr.aicode.controller;

import com.wkr.aicode.annotation.AuthCheck;
import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.DeleteRequest;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.constant.UserConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.entity.ProjectCategory;
import com.wkr.aicode.service.ProjectCategoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目分类 控制层。
 *
 * @author <a>百夜</a>
 */
@RestController
@RequestMapping("/projectCategory")
public class ProjectCategoryController {

    @Resource
    private ProjectCategoryService projectCategoryService;

    /**
     * 获取全部分类列表（公开）
     */
    @GetMapping("/list")
    public BaseResponse<List<ProjectCategory>> listCategories() {
        List<ProjectCategory> list = projectCategoryService.list(
                com.mybatisflex.core.query.QueryWrapper.create().orderBy("sortOrder", true)
        );
        return ResultUtils.success(list);
    }

    /**
     * 管理员添加分类
     */
    @PostMapping("/admin/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addCategory(@RequestBody ProjectCategory category) {
        ThrowUtils.throwIf(category == null, ErrorCode.PARAMS_ERROR);
        boolean result = projectCategoryService.save(category);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(category.getId());
    }

    /**
     * 管理员删除分类
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteCategory(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ProjectCategory old = projectCategoryService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = projectCategoryService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新分类
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateCategory(@RequestBody ProjectCategory category) {
        if (category == null || category.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ProjectCategory old = projectCategoryService.getById(category.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = projectCategoryService.updateById(category);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
}
