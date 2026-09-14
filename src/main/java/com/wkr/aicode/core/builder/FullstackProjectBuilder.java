package com.wkr.aicode.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 全栈项目构建器：前端构建 → 产物复制到 backend/public → 镜像预构建
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Component
public class FullstackProjectBuilder {

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
     * @param projectDir 项目根目录（Dockerfile 所在目录，即构建上下文）
     * @param imageTag   镜像名（不含 :latest）
     */
    public boolean buildImage(File projectDir, String imageTag) {
        File dockerfile = new File(projectDir, "Dockerfile");
        if (!dockerfile.isFile()) {
            log.error("Dockerfile 不存在: {}", dockerfile.getAbsolutePath());
            return false;
        }
        return executeCommand(projectDir, new String[]{"docker", "build", "-t", imageTag + ":latest", "."}, 600);
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
