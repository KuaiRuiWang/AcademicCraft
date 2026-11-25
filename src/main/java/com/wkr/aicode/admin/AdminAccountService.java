package com.wkr.aicode.admin;

import cn.hutool.crypto.digest.BCrypt;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.PlatformAdminMapper;
import com.wkr.aicode.mapper.PlatformAdminRoleMapper;
import com.wkr.aicode.model.entity.PlatformAdmin;
import com.wkr.aicode.model.entity.PlatformAdminRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
    public static final long ROOT_ROLE_ID = 1L;
    private final PlatformAdminMapper adminMapper;
    private final PlatformAdminRoleMapper roleMapper;

    @Value("${platform.admin.bootstrap.username:}")
    private String bootstrapUsername;
    @Value("${platform.admin.bootstrap.password:}")
    private String bootstrapPassword;

    public record AccountInput(String username, String displayName, String password, Long roleId, Boolean enabled) {
        @Override public String toString() { return "AccountInput[username=" + username + "]"; }
    }
    public record RoleInput(String name, Set<AdminPermission> permissions) { }
    public record RoleView(Long id, String name, Set<AdminPermission> permissions, boolean systemRole) { }
    public record Identity(Long id, String displayName, String role, Set<AdminPermission> permissions) { }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (bootstrapUsername.isBlank() && bootstrapPassword.isBlank()) return;
        // 共享角色行串行初始化，重复启动沿用已经创建的根账号。
        role(ROOT_ROLE_ID, true);
        if (adminMapper.selectCountByQuery(QueryWrapper.create().eq("roleId", ROOT_ROLE_ID)) > 0) return;
        saveAccount(null, new AccountInput(bootstrapUsername, "超级管理员", bootstrapPassword, ROOT_ROLE_ID, true));
    }

    public PlatformAdmin authenticate(String username, String password) {
        ThrowUtils.throwIf(username == null || password == null || username.length() > 64
                        || password.getBytes(StandardCharsets.UTF_8).length > 72,
                ErrorCode.PARAMS_ERROR, "请输入有效的管理员账号与密码");
        PlatformAdmin admin = adminMapper.selectOneByQuery(QueryWrapper.create().eq("username", username.strip()));
        ThrowUtils.throwIf(admin == null || !Boolean.TRUE.equals(admin.getEnabled())
                        || !BCrypt.checkpw(password, admin.getPasswordHash()),
                ErrorCode.NO_AUTH_ERROR, "管理员账号或密码不正确");
        return admin;
    }

    public Identity identity(long id, int sessionVersion) {
        PlatformAdmin admin = adminMapper.selectOneById(id);
        ThrowUtils.throwIf(admin == null || !Boolean.TRUE.equals(admin.getEnabled())
                        || !Objects.equals(admin.getSessionVersion(), sessionVersion),
                ErrorCode.NOT_LOGIN_ERROR, "管理端会话已失效");
        PlatformAdminRole role = role(admin.getRoleId(), false);
        return new Identity(admin.getId(), admin.getDisplayName(), role.getName(), permissions(role));
    }

    public List<PlatformAdmin> accounts() {
        return adminMapper.selectListByQuery(QueryWrapper.create().orderBy("createTime", false));
    }

    @Transactional
    public PlatformAdmin createAccount(AccountInput input) {
        ThrowUtils.throwIf(Objects.equals(input.roleId(), ROOT_ROLE_ID), ErrorCode.NO_AUTH_ERROR,
                "超级管理员由初始化配置创建");
        return saveAccount(null, input);
    }

    @Transactional
    public PlatformAdmin saveAccount(Long id, AccountInput input) {
        // 1. 锁定目标角色，避免同时删除角色或变更根管理员权限。
        Long roleId = input.roleId();
        ThrowUtils.throwIf(roleId == null || roleId <= 0, ErrorCode.PARAMS_ERROR, "请选择管理员角色");
        role(roleId, true);
        PlatformAdmin admin = id == null ? new PlatformAdmin() : adminMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", id).forUpdate());
        ThrowUtils.throwIf(admin == null, ErrorCode.NOT_FOUND_ERROR, "管理员账号不存在");
        boolean root = id != null && Objects.equals(admin.getRoleId(), ROOT_ROLE_ID);
        ThrowUtils.throwIf(id != null && (root != Objects.equals(roleId, ROOT_ROLE_ID)
                        || root && !Boolean.TRUE.equals(input.enabled())),
                ErrorCode.NO_AUTH_ERROR, "超级管理员角色和启用状态受保护");
        if (id == null && Objects.equals(roleId, ROOT_ROLE_ID)) {
            ThrowUtils.throwIf(adminMapper.selectCountByQuery(QueryWrapper.create().eq("roleId", ROOT_ROLE_ID)) > 0,
                    ErrorCode.NO_AUTH_ERROR, "超级管理员由初始化配置创建");
        }
        // 2. 校验并保存账号，密码或启用状态变更时撤销旧会话。
        String username = input.username() == null ? "" : input.username().strip();
        ThrowUtils.throwIf(!username.matches("[A-Za-z0-9._-]{3,64}"), ErrorCode.PARAMS_ERROR,
                "管理员账号应为 3 至 64 位英文字母、数字或 ._-");
        String displayName = requiredText(input.displayName(), 80, "管理员显示名称");
        boolean changePassword = input.password() != null && !input.password().isEmpty();
        if (id == null || changePassword) validatePassword(input.password());
        boolean enabled = !Boolean.FALSE.equals(input.enabled());
        if (id == null) admin.setSessionVersion(1);
        else if (changePassword || !Objects.equals(admin.getEnabled(), enabled)) {
            admin.setSessionVersion(admin.getSessionVersion() + 1);
        }
        if (id == null || changePassword) admin.setPasswordHash(BCrypt.hashpw(input.password(), BCrypt.gensalt()));
        admin.setUsername(username);
        admin.setDisplayName(displayName);
        admin.setRoleId(roleId);
        admin.setEnabled(enabled);
        try {
            if (id == null) adminMapper.insert(admin, true);
            else adminMapper.update(admin);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "管理员账号已存在");
        }
        return admin;
    }

    public List<RoleView> roles() {
        return roleMapper.selectListByQuery(QueryWrapper.create().orderBy("id", true)).stream().map(this::view).toList();
    }

    @Transactional
    public RoleView saveRole(Long id, RoleInput input) {
        ThrowUtils.throwIf(Objects.equals(id, ROOT_ROLE_ID) || input.permissions() != null
                        && input.permissions().contains(AdminPermission.SUPER_ADMIN),
                ErrorCode.NO_AUTH_ERROR, "超级管理员角色由系统维护");
        PlatformAdminRole role = id == null ? new PlatformAdminRole() : role(id, true);
        role.setName(requiredText(input.name(), 80, "角色名称"));
        Set<AdminPermission> permissions = input.permissions() == null ? Set.of() : input.permissions();
        role.setPermissions(permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        try {
            if (id == null) roleMapper.insert(role, true);
            else roleMapper.update(role);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色名称已存在");
        }
        return view(role);
    }

    @Transactional
    public void deleteRole(long id) {
        ThrowUtils.throwIf(id == ROOT_ROLE_ID, ErrorCode.NO_AUTH_ERROR, "超级管理员角色由系统维护");
        role(id, true);
        ThrowUtils.throwIf(adminMapper.selectCountByQuery(QueryWrapper.create().eq("roleId", id)) > 0,
                ErrorCode.OPERATION_ERROR, "还有管理员使用此角色，请先调整其角色");
        roleMapper.deleteById(id);
    }

    private PlatformAdminRole role(Long id, boolean lock) {
        PlatformAdminRole role = lock ? roleMapper.selectOneByQuery(QueryWrapper.create().eq("id", id).forUpdate())
                : roleMapper.selectOneById(id);
        ThrowUtils.throwIf(role == null, ErrorCode.NOT_FOUND_ERROR, "管理员角色不存在");
        return role;
    }

    private RoleView view(PlatformAdminRole role) {
        return new RoleView(role.getId(), role.getName(), permissions(role), role.getId() == ROOT_ROLE_ID);
    }

    private Set<AdminPermission> permissions(PlatformAdminRole role) {
        if (role.getId() == ROOT_ROLE_ID) return EnumSet.allOf(AdminPermission.class);
        List<String> assigned = List.of(role.getPermissions().split(","));
        return Arrays.stream(AdminPermission.values()).filter(permission -> permission != AdminPermission.SUPER_ADMIN)
                .filter(permission -> assigned.contains(permission.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validatePassword(String password) {
        ThrowUtils.throwIf(password == null || password.isBlank() || password.length() < 8
                        || password.getBytes(StandardCharsets.UTF_8).length > 72,
                ErrorCode.PARAMS_ERROR, "密码至少 8 个字符，最长 72 字节");
    }

    private String requiredText(String input, int maxLength, String label) {
        String value = input == null ? "" : input.strip();
        ThrowUtils.throwIf(value.isEmpty() || value.length() > maxLength, ErrorCode.PARAMS_ERROR,
                label + "应为 1 至 " + maxLength + " 字");
        return value;
    }
}
