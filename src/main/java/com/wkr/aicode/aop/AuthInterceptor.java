package com.wkr.aicode.aop;

import com.wkr.aicode.admin.AdminAccountService;
import com.wkr.aicode.admin.AdminSessionService;
import com.wkr.aicode.annotation.AuthCheck;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthInterceptor {
    private final AdminSessionService sessions;
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        AdminAccountService.Identity admin = sessions.current(request);
        ThrowUtils.throwIf(!admin.permissions().contains(authCheck.value()), ErrorCode.NO_AUTH_ERROR, "当前角色无权执行此操作");
        request.setAttribute(AdminAccountService.Identity.class.getName(), admin);
        return joinPoint.proceed();
    }
}
