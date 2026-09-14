package com.wkr.aicode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.mybatisflex.core.update.UpdateChain;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.builder.FullstackProjectBuilder;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.service.ContainerDeployService;
import com.wkr.aicode.service.NginxConfigService;
import com.wkr.aicode.service.PortManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 全栈应用容器部署器实现
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Service
public class ContainerDeployServiceImpl implements ContainerDeployService {

    private static final String DOCKERFILE_TEMPLATE = "deploy/Dockerfile.template";
    private static final String COMPOSE_TEMPLATE = "deploy/docker-compose.yml.template";
    private static final String APP_HEALTH_PATH = "/api/health";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_STOPPED = "stopped";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_DELETED = "deleted";
    private static final int HEALTH_TIMEOUT_SECONDS = 60;
    private static final int COMPOSE_UP_TIMEOUT_SECONDS = 900;
    private static final int COMPOSE_DOWN_TIMEOUT_SECONDS = 120;
    private static final int IMAGE_REMOVE_TIMEOUT_SECONDS = 60;

    @Resource
    private PortManager portManager;

    @Resource
    private FullstackProjectBuilder fullstackProjectBuilder;

    @Resource
    private NginxConfigService nginxConfigService;

    @Resource
    private AppMapper appMapper;

    @Override
    public String deploy(App app, String deployKey) {
        Long appId = app.getId();
        File projectDir = new File(AppConstant.fullstackProjectDir(appId));
        ThrowUtils.throwIf(!new File(projectDir, "backend").isDirectory(),
                ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        if (!fullstackProjectBuilder.isDockerAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前环境未安装 Docker，无法部署全栈应用");
        }
        if (!nginxConfigService.isAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前环境未配置 Nginx 动态目录，无法部署全栈应用");
        }
        // 1. 部署时重建前端，保证容器内产物与最新代码一致
        ThrowUtils.throwIf(!fullstackProjectBuilder.buildFrontend(projectDir),
                ErrorCode.SYSTEM_ERROR, "前端构建失败，请检查代码与依赖");
        ThrowUtils.throwIf(!fullstackProjectBuilder.copyDistToPublic(projectDir),
                ErrorCode.SYSTEM_ERROR, "前端产物复制到 backend/public 失败");
        // 2. 分配/复用宿主端口
        int port = portManager.allocatePort(appId);
        // compose 工程名必须全小写
        String projectName = "fullstack_" + deployKey.toLowerCase();
        // 3. 生成运行时文件（.env 已存在则保留：MySQL 数据卷中的密码只在首次初始化时生效）
        writeDockerfile(projectDir);
        writeComposeFile(projectDir, projectName, port);
        writeEnvFileIfAbsent(projectDir);
        // 4. 启动容器
        if (!composeUp(projectDir, projectName)) {
            rollback(projectDir, projectName, appId, deployKey);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用容器启动失败，请查看服务端日志");
        }
        // 5. 健康检查，失败则回滚
        if (!waitForHealthy(port, HEALTH_TIMEOUT_SECONDS)) {
            rollback(projectDir, projectName, appId, deployKey);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "应用启动后健康检查未通过（" + HEALTH_TIMEOUT_SECONDS + " 秒超时），已回滚容器");
        }
        // 6. 写入 /apps/{deployKey}/ 反代配置（入口机全量透传，无需逐应用操作）
        try {
            nginxConfigService.applyConfig(deployKey, port);
        } catch (Exception e) {
            rollback(projectDir, projectName, appId, deployKey);
            throw e;
        }
        // 7. 更新部署记录
        updateAppRecord(appId, projectName, deployKey, true);
        String appDeployUrl = AppConstant.CODE_DEPLOY_HOST + "/apps/" + deployKey + "/";
        log.info("全栈应用部署成功，appId={}，工程={}，端口={}，地址={}", appId, projectName, port, appDeployUrl);
        return appDeployUrl;
    }

    @Override
    public void stop(App app) {
        String projectName = requireDeployedProjectName(app);
        File projectDir = new File(AppConstant.fullstackProjectDir(app.getId()));
        ThrowUtils.throwIf(!composeDown(projectDir, projectName),
                ErrorCode.OPERATION_ERROR, "停止容器失败，请查看服务端日志");
        nginxConfigService.removeConfig(app.getDeployKey());
        updateContainerStatus(app.getId(), STATUS_STOPPED);
        log.info("应用容器已停止，appId={}，工程={}（数据卷保留）", app.getId(), projectName);
    }

    @Override
    public void restart(App app) {
        String projectName = requireDeployedProjectName(app);
        Integer port = app.getNodePort();
        ThrowUtils.throwIf(port == null, ErrorCode.OPERATION_ERROR, "应用缺少端口分配记录，请重新部署");
        File projectDir = new File(AppConstant.fullstackProjectDir(app.getId()));
        ThrowUtils.throwIf(!composeUp(projectDir, projectName),
                ErrorCode.OPERATION_ERROR, "重启容器失败，请查看服务端日志");
        if (!waitForHealthy(port, HEALTH_TIMEOUT_SECONDS)) {
            updateContainerStatus(app.getId(), STATUS_FAILED);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "重启后健康检查未通过，请查看容器日志");
        }
        nginxConfigService.applyConfig(app.getDeployKey(), port);
        updateContainerStatus(app.getId(), STATUS_RUNNING);
        log.info("应用容器已重启，appId={}，工程={}，端口={}", app.getId(), projectName, port);
    }

    @Override
    public void remove(App app) {
        String projectName = requireDeployedProjectName(app);
        File projectDir = new File(AppConstant.fullstackProjectDir(app.getId()));
        // 1. 停容器并删除数据卷
        ThrowUtils.throwIf(!composeDown(projectDir, projectName, true),
                ErrorCode.OPERATION_ERROR, "删除容器或数据卷失败，请查看服务端日志");
        // 2. 清理应用镜像（mysql 为共享基础镜像，不清理；镜像已不存在时命令失败可忽略）
        executeCommand(projectDir, new String[]{"docker", "rmi", projectName + "-app"},
                IMAGE_REMOVE_TIMEOUT_SECONDS);
        // 3. 清理反代配置并释放宿主端口
        nginxConfigService.removeConfig(app.getDeployKey());
        portManager.releasePort(app.getId());
        // 4. 更新应用记录（deployKey 保留，重新部署复用同一访问地址）
        UpdateChain.of(appMapper)
                .set("containerStatus", STATUS_DELETED)
                .set("containerName", null)
                .where("id = ?", app.getId())
                .update();
        log.info("应用容器已删除，appId={}，工程={}（容器/卷/镜像/反代/端口已清理）", app.getId(), projectName);
    }

    /**
     * 生命周期操作前置校验：应用必须已部署过，返回 compose 工程名
     */
    private String requireDeployedProjectName(App app) {
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDeployKey()) || StrUtil.isBlank(app.getContainerName()),
                ErrorCode.OPERATION_ERROR, "应用尚未部署，无容器可操作");
        return app.getContainerName();
    }

    private void updateContainerStatus(Long appId, String status) {
        App update = new App();
        update.setId(appId);
        update.setContainerStatus(status);
        appMapper.update(update);
    }

    private void writeDockerfile(File projectDir) {
        FileUtil.writeUtf8String(renderTemplate(DOCKERFILE_TEMPLATE), new File(projectDir, "Dockerfile"));
    }

    private void writeComposeFile(File projectDir, String projectName, int port) {
        FileUtil.writeUtf8String(renderCompose(projectName, port), new File(projectDir, "docker-compose.yml"));
    }

    /**
     * 渲染 compose：替换容器名与宿主端口，保留 ${DB_PASSWORD} 由 compose 读 .env 注入
     */
    String renderCompose(String projectName, int port) {
        return renderTemplate(COMPOSE_TEMPLATE)
                .replace("__APP_CONTAINER_NAME__", projectName)
                .replace("__MYSQL_CONTAINER_NAME__", projectName + "_mysql")
                .replace("__HOST_PORT__", String.valueOf(port));
    }

    private void writeEnvFileIfAbsent(File projectDir) {
        File envFile = new File(projectDir, ".env");
        if (envFile.isFile()) {
            return;
        }
        String env = "DB_PASSWORD=" + RandomUtil.randomString(16) + "\n"
                + "MYSQL_ROOT_PASSWORD=" + RandomUtil.randomString(16) + "\n";
        FileUtil.writeUtf8String(env, envFile);
    }

    private String renderTemplate(String classpathPath) {
        try (InputStream inputStream = new ClassPathResource(classpathPath).getInputStream()) {
            return IoUtil.read(inputStream, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("部署模板读取失败: {}", classpathPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署模板读取失败: " + classpathPath);
        }
    }

    private boolean composeUp(File projectDir, String projectName) {
        return executeCommand(projectDir,
                new String[]{"docker", "compose", "-p", projectName, "up", "-d", "--build"},
                COMPOSE_UP_TIMEOUT_SECONDS);
    }

    private boolean composeDown(File projectDir, String projectName) {
        return composeDown(projectDir, projectName, false);
    }

    private boolean composeDown(File projectDir, String projectName, boolean removeVolumes) {
        String[] command = removeVolumes
                ? new String[]{"docker", "compose", "-p", projectName, "down", "-v"}
                : new String[]{"docker", "compose", "-p", projectName, "down"};
        boolean down = executeCommand(projectDir, command, COMPOSE_DOWN_TIMEOUT_SECONDS);
        if (!down) {
            log.error("容器停止失败，请手动清理 compose 工程: {}", projectName);
        }
        return down;
    }

    /**
     * 部署失败回滚：停容器（保留数据卷）+ 清理反代配置 + 标记失败，保证「配置只在部署成功时存在」
     */
    private void rollback(File projectDir, String projectName, Long appId, String deployKey) {
        composeDown(projectDir, projectName);
        nginxConfigService.removeConfig(deployKey);
        updateAppRecord(appId, projectName, null, false);
    }

    /**
     * 轮询健康检查接口直至 200 或超时
     */
    boolean waitForHealthy(int port, int timeoutSeconds) {
        String healthUrl = "http://127.0.0.1:" + port + APP_HEALTH_PATH;
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse response = HttpRequest.get(healthUrl).timeout(3000).execute();
                if (response.isOk()) {
                    log.info("健康检查通过: {}", healthUrl);
                    return true;
                }
            } catch (Exception e) {
                // 容器尚未就绪，继续轮询
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void updateAppRecord(Long appId, String projectName, String deployKey, boolean success) {
        App update = new App();
        update.setId(appId);
        update.setContainerName(projectName);
        update.setContainerStatus(success ? STATUS_RUNNING : STATUS_FAILED);
        if (success) {
            update.setDeployKey(deployKey);
            update.setDeployedTime(LocalDateTime.now());
        }
        appMapper.update(update);
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
