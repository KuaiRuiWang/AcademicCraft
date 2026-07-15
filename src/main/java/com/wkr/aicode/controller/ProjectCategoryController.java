package com.wkr.aicode.controller;

import com.mybatisflex.core.query.QueryWrapper;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目分类 控制层。
 *
 * @author <a>百夜</a>
 */
@Tag(name = "projectCategoryController", description = "项目分类的增删改查接口")
@RestController
@RequestMapping("/projectCategory")
public class ProjectCategoryController {

    @Resource
    private ProjectCategoryService projectCategoryService;

    /**
     * 获取全部分类列表（公开）
     */
    @Operation(summary = "获取全部分类列表", description = "按排序权重升序返回所有未删除的项目分类")
    @GetMapping("/list")
    public BaseResponse<List<ProjectCategory>> listCategories() {
        List<ProjectCategory> list = projectCategoryService.list(
                QueryWrapper.create().orderBy("sortOrder", true)
        );
        return ResultUtils.success(list);
    }

    /**
     * 管理员添加分类
     */
    @Operation(summary = "添加项目分类（管理员）", description = "管理员新增一个项目分类")
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
    @Operation(summary = "删除项目分类（管理员）", description = "管理员根据ID逻辑删除一个项目分类")
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
    @Operation(summary = "更新项目分类（管理员）", description = "管理员更新一个项目分类的名称、图标或排序")
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
