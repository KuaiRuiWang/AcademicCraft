package com.wkr.aicode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.service.NginxConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 动态 Nginx 配置服务实现
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Service
public class NginxConfigServiceImpl implements NginxConfigService {

    private static final Pattern DEPLOY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,32}$");
    private static final int COMMAND_TIMEOUT_SECONDS = 60;

    @Value("${app.nginx.conf-dir:/baiye/1panel/www/sites/ai-code/proxy/apps}")
    private String confDir;

    @Value("${app.nginx.container-name:1Panel-openresty-D1L0}")
    private String containerName;

    // 生产 openresty 为 host 网络，127.0.0.1 即宿主机；本地验证（bridge 网络容器）需配 host.docker.internal
    @Value("${app.nginx.upstream-host:127.0.0.1}")
    private String upstreamHost;

    @Override
    public boolean isAvailable() {
        return StrUtil.isNotBlank(confDir) && new File(confDir).isDirectory();
    }

    @Override
    public void applyConfig(String deployKey, int port) {
        validate(deployKey, port);
        ThrowUtils.throwIf(!isAvailable(), ErrorCode.OPERATION_ERROR,
                "当前环境未配置 Nginx 动态目录: " + confDir);
        File confFile = new File(confDir, "apps-" + deployKey + ".conf");
        FileUtil.writeUtf8String(renderLocation(deployKey, port), confFile);
        if (!reloadNginx()) {
            // 配置有误时移除本次写入，避免残留影响后续所有 reload
            FileUtil.del(confFile);
            reloadNginx();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Nginx 配置生效失败，请查看服务端日志");
        }
        log.info("已写入应用反代配置: {} -> 127.0.0.1:{}", confFile.getName(), port);
    }

    @Override
    public void removeConfig(String deployKey) {
        validateDeployKey(deployKey);
        File confFile = new File(confDir, "apps-" + deployKey + ".conf");
        if (!confFile.exists()) {
            return;
        }
        FileUtil.del(confFile);
        if (!reloadNginx()) {
            log.error("删除配置后 Nginx reload 失败: {}", confFile.getName());
        }
    }

    String renderLocation(String deployKey, int port) {
        return "location ^~ /apps/" + deployKey + "/ {\n"
                + "    proxy_pass http://" + upstreamHost + ":" + port + "/;\n"
                + "    proxy_set_header Host $host;\n"
                + "    proxy_set_header X-Real-IP $remote_addr;\n"
                + "    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n"
                + "    proxy_set_header X-Forwarded-Proto $scheme;\n"
                + "    proxy_set_header Upgrade $http_upgrade;\n"
                + "    proxy_set_header Connection $http_connection;\n"
                + "    proxy_http_version 1.1;\n"
                + "    proxy_read_timeout 900s;\n"
                + "}\n";
    }

    private void validate(String deployKey, int port) {
        validateDeployKey(deployKey);
        ThrowUtils.throwIf(port <= 0 || port > 65535, ErrorCode.PARAMS_ERROR, "非法端口: " + port);
    }

    private void validateDeployKey(String deployKey) {
        ThrowUtils.throwIf(StrUtil.isBlank(deployKey) || !DEPLOY_KEY_PATTERN.matcher(deployKey).matches(),
                ErrorCode.PARAMS_ERROR, "非法 deployKey");
    }

    /**
     * 先 nginx -t 校验语法再 reload（reload 自身不校验，语法错误只会静默保留旧配置）
     */
    boolean reloadNginx() {
        if (!executeCommand("docker", "exec", containerName, "nginx", "-t")) {
            return false;
        }
        return executeCommand("docker", "exec", containerName, "nginx", "-s", "reload");
    }

    private boolean executeCommand(String... command) {
        String commandLine = String.join(" ", command);
        try {
            Process process = RuntimeUtil.exec(command);
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("命令执行超时: {}", commandLine);
                return false;
            }
            if (process.exitValue() == 0) {
                return true;
            }
            log.error("命令执行失败，退出码 {}: {}", process.exitValue(), commandLine);
            return false;
        } catch (Exception e) {
            log.error("执行命令异常: {}, 错误: {}", commandLine, e.getMessage());
            return false;
        }
    }
}
