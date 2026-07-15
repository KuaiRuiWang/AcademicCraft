package com.wkr.aicode.controller;

import cn.hutool.core.util.StrUtil;
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
@Tag(name = "projectOrderController", description = "项目下单、查看订单、下载源码接口")
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
    @Operation(summary = "购买项目", description = "登录用户购买指定项目，不能购买自己的项目，同一项目不可重复购买。免费项目直接完成，付费项目待支付接入")
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
    @Operation(summary = "我的购买记录", description = "登录用户查看自己的所有购买订单")
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<ProjectOrderVO>> listMyOrders(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        QueryWrapper queryWrapper = projectOrderService.getQueryWrapper(loginUser.getId());
        long pageSize = 20;
        long pageNum = 1;
        Page<ProjectOrder> orderPage = projectOrderService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ProjectOrderVO> voPage = new Page<>(pageNum, pageSize, orderPage.getTotalRow());
        List<ProjectOrderVO> voList = projectOrderService.getOrderVOList(orderPage.getRecords());
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 获取订单详情（含源码地址）
     */
    @Operation(summary = "获取订单详情", description = "买家查看自己的订单详情，包含项目源码下载地址，仅买家本人可查看")
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
    @Operation(summary = "下载已购项目源码", description = "买家下载已支付订单对应的项目源码，根据交付方式重定向到下载地址")
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
                ErrorCode.OPERATION_ERROR, "订单未支付，无法下载");

        Project project = projectService.getById(order.getProjectId());
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");

        String sourceUrl = project.getSourceUrl();
        ThrowUtils.throwIf(StrUtil.isBlank(sourceUrl), ErrorCode.NOT_FOUND_ERROR, "暂无源码下载地址");

        try {
            response.sendRedirect(sourceUrl);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "跳转失败");
        }
    }
}
