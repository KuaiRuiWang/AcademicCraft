package com.wkr.aicode.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RuntimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 全栈项目构建器：前端构建 → 产物复制到 backend/public → 镜像预构建
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Component
public class FullstackProjectBuilder {

    /**
     * 与 ContainerDeployServiceImpl.DOCKERFILE_TEMPLATE 指向同一份模板，
     * 改动任一处都必须同步，否则预构建的镜像层无法被部署阶段复用。
     */
    private static final String DOCKERFILE_TEMPLATE = "deploy/Dockerfile.template";

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 构建前端：frontend/ 目录执行 npm install && npm run build，产出 dist/
     */
    public boolean buildFrontend(File projectDir) {
        File frontendDir = new File(projectDir, "frontend");
        if (!frontendDir.isDirectory()) {
            log.error("前端目录不存在: {}", frontendDir.getAbsolutePath());
            return false;
        }
        return vueProjectBuilder.buildProject(frontendDir.getAbsolutePath());
    }

    /**
     * 复制前端构建产物到 backend/public（先清空旧产物，避免残留过期文件）
     */
    public boolean copyDistToPublic(File projectDir) {
        File distDir = new File(projectDir, "frontend/dist");
        if (!distDir.isDirectory()) {
            log.error("前端 dist 目录不存在: {}", distDir.getAbsolutePath());
            return false;
        }
        File publicDir = new File(projectDir, "backend/public");
        try {
            FileUtil.del(publicDir);
            FileUtil.copyContent(distDir, publicDir, true);
        } catch (Exception e) {
            log.error("复制前端产物到 backend/public 失败: {}", e.getMessage(), e);
            return false;
        }
        if (!new File(publicDir, "index.html").isFile()) {
            log.error("复制完成但 backend/public/index.html 不存在: {}", publicDir.getAbsolutePath());
            return false;
        }
        log.info("前端产物已复制到 {}", publicDir.getAbsolutePath());
        return true;
    }

    /**
     * 预构建运行镜像（不启动容器），部署阶段由 compose 重建或复用缓存
     *
     * Dockerfile 是平台模板注入的：前后端 Agent 都被明确要求不得生成它
     * （见 prompt/codegen-nodejs-backend-system-prompt.txt 第 7 条），
     * 部署阶段才由 ContainerDeployServiceImpl 落盘。
     * 所以预构建必须先按同一模板渲染出 Dockerfile，否则必然以「Dockerfile 不存在」失败。
     * 与部署共用同一模板与构建上下文（compose 的 build: .），镜像层缓存才能真正复用。
     *
     * @param projectDir 项目根目录（Dockerfile 所在目录，即构建上下文）
     * @param imageTag   镜像名（不含 :latest）
     */
    public boolean buildImage(File projectDir, String imageTag) {
        if (!writeDockerfile(projectDir)) {
            return false;
        }
        return executeCommand(projectDir, new String[]{"docker", "build", "-t", imageTag + ":latest", "."}, 600);
    }

    /**
     * 按平台模板渲染 Dockerfile 到项目根目录。
     *
     * 模板为静态内容、无占位符，无需变量替换；部署阶段会以同一模板覆盖写入，两者结果一致。
     */
    private boolean writeDockerfile(File projectDir) {
        try (InputStream inputStream = new ClassPathResource(DOCKERFILE_TEMPLATE).getInputStream()) {
            String content = IoUtil.read(inputStream, StandardCharsets.UTF_8);
            File dockerfile = new File(projectDir, "Dockerfile");
            FileUtil.writeUtf8String(content, dockerfile);
            log.info("已按平台模板写入 Dockerfile: {}", dockerfile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("渲染 Dockerfile 模板失败: {}", DOCKERFILE_TEMPLATE, e);
            return false;
        }
    }

    /**
     * Docker 是否可用（本机未安装 Docker 时跳过镜像预构建，部署阶段在服务器构建）
     */
    public boolean isDockerAvailable() {
        return executeCommand(null, new String[]{"docker", "version", "--format", "{{.Server.Version}}"}, 30);
    }

    private boolean executeCommand(File workingDir, String[] command, int timeoutSeconds) {
        try {
            log.info("执行命令: {}", String.join(" ", command));
            Process process = RuntimeUtil.exec(null, workingDir, command);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止: {}", timeoutSeconds, String.join(" ", command));
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() == 0) {
                log.info("命令执行成功: {}", String.join(" ", command));
                return true;
            }
            log.error("命令执行失败，退出码 {}: {}", process.exitValue(), String.join(" ", command));
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误: {}", String.join(" ", command), e.getMessage());
            return false;
        }
    }
}
