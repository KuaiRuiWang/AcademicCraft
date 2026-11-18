package com.wkr.aicode.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.dto.projectorder.ProjectOrderCreateRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.ProjectOrder;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.projectorder.ProjectOrderVO;
import com.wkr.aicode.service.ProjectOrderService;
import com.wkr.aicode.service.ProjectService;
import com.wkr.aicode.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购买记录 控制层。
 *
 * @author <a>百夜</a>
 */
@Tag(name = "projectOrderController", description = "免费模板领取、查看历史记录和下载源码接口")
@RestController
@RequestMapping("/projectOrder")
public class ProjectOrderController {

    @Resource
    private ProjectOrderService projectOrderService;

    @Resource
    private ProjectService projectService;

    @Resource
    private UserService userService;

    /**
     * 创建订单（购买项目）
     */
    @Operation(summary = "领取免费模板", description = "登录用户领取已上架的免费授权模板，重复领取返回同一记录")
    @PostMapping("/create")
    public BaseResponse<Long> createOrder(@RequestBody ProjectOrderCreateRequest createRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(createRequest == null || createRequest.getProjectId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long orderId = projectOrderService.createOrder(createRequest.getProjectId(), loginUser);
        return ResultUtils.success(orderId);
    }

    /**
     * 我的购买记录
     */
    @Operation(summary = "我的领取记录", description = "登录用户分页查看自己的领取记录和历史订单")
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<ProjectOrderVO>> listMyOrders(@RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                           @RequestParam(required = false) Long projectId,
                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(pageNum < 1 || pageSize < 1 || pageSize > 20,
                ErrorCode.PARAMS_ERROR, "分页参数无效");
        QueryWrapper queryWrapper = projectOrderService.getQueryWrapper(loginUser.getId());
        queryWrapper.eq("projectId", projectId);
        Page<ProjectOrder> orderPage = projectOrderService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ProjectOrderVO> voPage = new Page<>(pageNum, pageSize, orderPage.getTotalRow());
        List<ProjectOrderVO> voList = projectOrderService.getOrderVOList(orderPage.getRecords());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 获取领取或历史订单详情
     */
    @Operation(summary = "获取领取记录详情", description = "用户查看自己的领取记录和当前下载状态")
    @GetMapping("/get/vo")
    public BaseResponse<ProjectOrderVO> getOrderVOById(@RequestParam long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ProjectOrder order = projectOrderService.getById(id);
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        if (!order.getBuyerId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return ResultUtils.success(projectOrderService.getOrderVO(order));
    }

    /**
     * 下载已购项目源码
     */
    @Operation(summary = "下载已领取的模板", description = "校验领取归属及模板当前状态后跳转至源码地址")
    @GetMapping("/download/{orderId}")
    public void downloadProject(@PathVariable Long orderId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        ThrowUtils.throwIf(orderId == null || orderId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        ProjectOrder order = projectOrderService.getById(orderId);
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(!order.getBuyerId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权下载");
        ThrowUtils.throwIf(order.getStatus() != 1,
                ErrorCode.OPERATION_ERROR, "领取记录已失效，暂不可下载");

        Project project = projectService.getPublicTemplate(order.getProjectId());

        try {
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(project.getSourceUrl().strip());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "跳转失败");
        }
    }
}
