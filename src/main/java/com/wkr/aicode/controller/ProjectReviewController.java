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
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目评价 控制层。
 *
 * @author <a>百夜</a>
 */
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
    @GetMapping("/list/vo")
    public BaseResponse<List<ProjectReviewVO>> listReviews(@RequestParam long projectId) {
        ThrowUtils.throwIf(projectId <= 0, ErrorCode.PARAMS_ERROR);
        List<ProjectReviewVO> voList = projectReviewService.getReviewVOList(projectId);
        return ResultUtils.success(voList);
    }
}
