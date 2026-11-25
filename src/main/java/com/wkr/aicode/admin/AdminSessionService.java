package com.wkr.aicode.admin;

import cn.hutool.crypto.digest.DigestUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AdminSessionService {
    private static final Duration SESSION_DURATION = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private final AdminAccountService accounts;

    public record Login(String token, AdminAccountService.Identity admin) {
        @Override public String toString() { return "Login[admin=" + admin + "]"; }
    }

    public Login login(String username, String password) {
        var admin = accounts.authenticate(username, password);
        var identity = accounts.identity(admin.getId(), admin.getSessionVersion());
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        redis.opsForValue().set(key(token), admin.getId() + ":" + admin.getSessionVersion(), SESSION_DURATION);
        return new Login(token, identity);
    }

    public AdminAccountService.Identity current(HttpServletRequest request) {
        String session = redis.opsForValue().get(key(token(request)));
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_LOGIN_ERROR, "请登录管理端");
        String[] values = session.split(":", 2);
        try {
            return accounts.identity(Long.parseLong(values[0]), Integer.parseInt(values[1]));
        } catch (NumberFormatException | IndexOutOfBoundsException error) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "管理端会话已失效");
        }
    }

    public void logout(HttpServletRequest request) {
        redis.delete(key(token(request)));
    }

    private String token(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        ThrowUtils.throwIf(authorization == null || !authorization.matches("^Bearer [A-Za-z0-9_-]{43}$"),
                ErrorCode.NOT_LOGIN_ERROR, "请登录管理端");
        return authorization.substring("Bearer ".length());
    }

    private String key(String token) {
        return "platform:admin:session:" + DigestUtil.sha256Hex(token);
    }
}
