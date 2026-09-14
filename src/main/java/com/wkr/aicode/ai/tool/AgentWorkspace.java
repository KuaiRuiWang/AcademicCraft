package com.wkr.aicode.ai.tool;

import cn.hutool.core.util.StrUtil;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 智能体工作区：限定某个 Agent 可操作的文件范围
 * 每个 Agent 持有独立的工作区实例（不使用 ThreadLocal，避免工具执行线程与工作流节点线程不一致导致上下文丢失）
 *
 * @author <a>百夜</a>
 */
@Getter
public class AgentWorkspace {

    /**
     * 根目录部署文件白名单（后端 Agent 可写）
     */
    private static final Set<String> ROOT_DEPLOY_FILES = Set.of(
            "Dockerfile", "docker-compose.yml", ".env.example", "README.md"
    );

    /**
     * 契约文件（架构师 Agent 可写）
     */
    private static final String CONTRACT_FILE_NAME = "api-contract.json";

    /**
     * 前端产物目录前缀
     */
    private static final String FRONTEND_PREFIX = "frontend/";

    /**
     * 后端产物目录前缀
     */
    private static final String BACKEND_PREFIX = "backend/";

    /**
     * 产物根目录（绝对路径，已 normalize）
     */
    private final Path projectRoot;

    /**
     * Agent 角色，为 null 表示不做角色限制（兼容旧模式）
     */
    private final AgentRoleEnum role;

    public AgentWorkspace(String projectRoot, AgentRoleEnum role) {
        this.projectRoot = Paths.get(projectRoot).toAbsolutePath().normalize();
        this.role = role;
    }

    /**
     * 兼容旧逻辑的工作区：Vue 工程模式产物目录，不做角色限制
     */
    public static AgentWorkspace vueProject(Long appId) {
        String projectDir = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
        return new AgentWorkspace(projectDir, null);
    }

    /**
     * 解析写入 / 修改 / 删除的目标路径
     *
     * @param relativeFilePath 相对路径
     * @return 解析后的绝对路径；越权或非法路径返回 null
     */
    public Path resolveForWrite(String relativeFilePath) {
        String normalized = normalizeInput(relativeFilePath);
        if (normalized == null) {
            return null;
        }
        Path resolved = resolveInsideRoot(normalized);
        if (resolved == null) {
            return null;
        }
        if (!isWriteAllowed(resolved)) {
            return null;
        }
        return resolved;
    }

    /**
     * 解析读取的目标路径（仅限制不得逃出产物根目录，允许读取其他 Agent 的产物）
     *
     * @param relativeFilePath 相对路径，为空表示根目录
     * @return 解析后的绝对路径；非法路径返回 null
     */
    public Path resolveForRead(String relativeFilePath) {
        if (StrUtil.isBlank(relativeFilePath)) {
            return projectRoot;
        }
        String normalized = normalizeInput(relativeFilePath);
        if (normalized == null) {
            return null;
        }
        return resolveInsideRoot(normalized);
    }

    /**
     * 越权提示（返回给 AI，引导其改用合法路径）
     */
    public String getWriteDeniedMessage(String relativeFilePath) {
        if (role == AgentRoleEnum.FRONTEND) {
            return "错误：无权限写入 " + relativeFilePath + "，你的产物必须位于 frontend/ 目录下";
        }
        if (role == AgentRoleEnum.BACKEND) {
            return "错误：无权限写入 " + relativeFilePath
                    + "，你的产物必须位于 backend/ 目录下（根目录仅允许 " + ROOT_DEPLOY_FILES + "）";
        }
        if (role == AgentRoleEnum.ARCHITECT) {
            return "错误：无权限写入 " + relativeFilePath + "，架构师仅允许写入 " + CONTRACT_FILE_NAME;
        }
        return "错误：非法的文件路径 " + relativeFilePath;
    }

    /**
     * 规范化输入路径：统一分隔符、剥离 ./ 前缀、拒绝空值与绝对路径
     */
    private String normalizeInput(String relativeFilePath) {
        if (StrUtil.isBlank(relativeFilePath)) {
            return null;
        }
        String path = relativeFilePath.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        // 绝对路径一律拒绝，必须使用产物根目录下的相对路径
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            return null;
        }
        return path;
    }

    /**
     * 解析到产物根目录内，并校验未逃出根目录（防 ../ 穿越）
     */
    private Path resolveInsideRoot(String relativePath) {
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            return null;
        }
        return resolved;
    }

    /**
     * 按角色校验写入范围（基于 normalize 后的相对路径，避免 frontend/../backend 之类的绕过）
     */
    private boolean isWriteAllowed(Path resolvedPath) {
        if (role == null) {
            // 兼容旧模式：产物根目录内均可写
            return true;
        }
        String relativePath = projectRoot.relativize(resolvedPath).toString().replace('\\', '/');
        return switch (role) {
            case ARCHITECT -> CONTRACT_FILE_NAME.equals(relativePath);
            case FRONTEND -> relativePath.startsWith(FRONTEND_PREFIX);
            case BACKEND -> relativePath.startsWith(BACKEND_PREFIX)
                    || ROOT_DEPLOY_FILES.contains(relativePath);
        };
    }
}
