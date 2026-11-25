package com.wkr.aicode.controller;

import com.wkr.aicode.admin.AdminAccountService;
import com.wkr.aicode.admin.AdminPermission;
import com.wkr.aicode.admin.AdminSessionService;
import com.wkr.aicode.annotation.AuthCheck;
import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.model.entity.PlatformAdmin;
import com.wkr.aicode.ratelimter.annotation.RateLimit;
import com.wkr.aicode.ratelimter.enums.RateLimitType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.wkr.aicode.admin.AdminPermission.SUPER_ADMIN;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PlatformAdminController {
    private final AdminSessionService sessions;
    private final AdminAccountService accounts;

    public record LoginInput(String username, String password) {
        @Override public String toString() { return "LoginInput[username=" + username + "]"; }
    }

    @PostMapping("/auth/login")
    @RateLimit(limitType = RateLimitType.IP, rate = 10, rateInterval = 60, message = "登录尝试过于频繁，请稍后重试")
    public BaseResponse<AdminSessionService.Login> login(@RequestBody LoginInput input) {
        return ResultUtils.success(sessions.login(input.username(), input.password()));
    }

    @GetMapping("/auth/session")
    public BaseResponse<AdminAccountService.Identity> current(HttpServletRequest request) {
        return ResultUtils.success(sessions.current(request));
    }

    @PostMapping("/auth/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        sessions.logout(request);
        return ResultUtils.success(true);
    }

    @GetMapping("/accounts")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<List<PlatformAdmin>> accounts() {
        return ResultUtils.success(accounts.accounts());
    }

    @PostMapping("/accounts")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<PlatformAdmin> createAccount(@RequestBody AdminAccountService.AccountInput input) {
        return ResultUtils.success(accounts.createAccount(input));
    }

    @PutMapping("/accounts/{id}")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<PlatformAdmin> updateAccount(@PathVariable long id, @RequestBody AdminAccountService.AccountInput input) {
        return ResultUtils.success(accounts.saveAccount(id, input));
    }

    @GetMapping("/roles")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<List<AdminAccountService.RoleView>> roles() {
        return ResultUtils.success(accounts.roles());
    }

    @PostMapping("/roles")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<AdminAccountService.RoleView> createRole(@RequestBody AdminAccountService.RoleInput input) {
        return ResultUtils.success(accounts.saveRole(null, input));
    }

    @PutMapping("/roles/{id}")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<AdminAccountService.RoleView> updateRole(@PathVariable long id, @RequestBody AdminAccountService.RoleInput input) {
        return ResultUtils.success(accounts.saveRole(id, input));
    }

    @DeleteMapping("/roles/{id}")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<Boolean> deleteRole(@PathVariable long id) {
        accounts.deleteRole(id);
        return ResultUtils.success(true);
    }

    @GetMapping("/permissions")
    @AuthCheck(SUPER_ADMIN)
    public BaseResponse<List<Map<String, String>>> permissions() {
        return ResultUtils.success(Arrays.stream(AdminPermission.values()).filter(permission -> permission != SUPER_ADMIN)
                .map(permission -> Map.of("code", permission.name(), "label", permission.getLabel())).toList());
    }
}
