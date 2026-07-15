package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.dto.project.ProjectReviewAddRequest;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectReviewVO;
import com.wkr.aicode.service.ProjectReviewService;
import com.wkr.aicode.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目评价 控制层。
 *
 * @author <a>百夜</a>
 */
@Tag(name = "projectReviewController", description = "项目评价的发表和查看接口")
@RestController
@RequestMapping("/projectReview")
public class ProjectReviewController {

    @Resource
    private ProjectReviewService projectReviewService;

    @Resource
    private UserService userService;

    /**
     * 发表评价
     */
    @Operation(summary = "发表项目评价", description = "购买者对已购项目发表1-5星评价，需提供有效订单ID，同一订单只能评价一次")
    @PostMapping("/add")
    public BaseResponse<Long> addReview(@RequestBody ProjectReviewAddRequest addRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long reviewId = projectReviewService.addReview(addRequest, loginUser);
        return ResultUtils.success(reviewId);
    }

    /**
     * 获取项目评价列表（公开）
     */
    @Operation(summary = "查看项目评价列表", description = "公开接口，获取指定项目的所有评价，按创建时间倒序排列")
    @GetMapping("/list/vo")
    public BaseResponse<List<ProjectReviewVO>> listReviews(@RequestParam long projectId) {
        ThrowUtils.throwIf(projectId <= 0, ErrorCode.PARAMS_ERROR);
        List<ProjectReviewVO> voList = projectReviewService.getReviewVOList(projectId);
        return ResultUtils.success(voList);
    }
}
