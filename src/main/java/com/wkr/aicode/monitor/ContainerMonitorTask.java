package com.wkr.aicode.monitor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.model.entity.App;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 全栈应用容器监控与告警（安全设计第 4 层）：
 * 定时采集 docker stats 与容器运行状态，内存打满 / CPU 持续超阈值 → 告警并自动重启（带处置冷却），
 * 容器实际状态变化落库刷新 container_status。
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Component
public class ContainerMonitorTask {

    private static final int STATS_TIMEOUT_SECONDS = 30;
    private static final int INSPECT_TIMEOUT_SECONDS = 15;
    private static final int RESTART_TIMEOUT_SECONDS = 60;

    @Value("${app.monitor.enabled:true}")
    private boolean enabled;

    @Value("${app.monitor.memory-threshold-percent:90}")
    private double memoryThresholdPercent;

    /**
     * compose 模板将应用容器 CPU 限制为 0.5 核，docker stats 的 CPUPerc 上限即 50%，
     * 故默认阈值取 45%（≈ 容器 CPU 配额的 90%，与内存阈值语义对齐）
     */
    @Value("${app.monitor.cpu-threshold-percent:45}")
    private double cpuThresholdPercent;

    @Value("${app.monitor.cpu-sustained-samples:3}")
    private int cpuSustainedSamples;

    @Value("${app.monitor.action-cooldown-seconds:600}")
    private long actionCooldownSeconds;

    @Resource
    private AppMapper appMapper;

    /**
     * 容器 → 上次处置时间戳（毫秒），防止重启抖动循环
     */
    private final Map<String, Long> lastActionTime = new ConcurrentHashMap<>();

    /**
     * 容器 → CPU 连续超阈值次数
     */
    private final Map<String, Integer> cpuHighStreak = new ConcurrentHashMap<>();

    @Scheduled(initialDelayString = "${app.monitor.initial-delay-ms:30000}",
            fixedDelayString = "${app.monitor.interval-ms:60000}")
    public void monitorContainers() {
        if (!enabled) {
            return;
        }
        List<App> apps = appMapper.selectListByQuery(QueryWrapper.create()
                .where("containerName IS NOT NULL")
                .and("containerStatus <> 'deleted'"));
        if (apps.isEmpty()) {
            return;
        }
        Map<String, ContainerStat> stats = collectStats();
        for (App app : apps) {
            String containerName = app.getContainerName();
            refreshStatus(app, containerName);
            ContainerStat stat = stats.get(containerName);
            if (stat != null) {
                checkThresholds(containerName, stat);
            }
        }
    }

    /**
     * 采集全部运行中容器的资源统计（docker stats 单次快照）
     */
    private Map<String, ContainerStat> collectStats() {
        String output = execute(new String[]{"docker", "stats", "--no-stream", "--format", "{{json .}}"},
                STATS_TIMEOUT_SECONDS);
        if (output == null) {
            return Collections.emptyMap();
        }
        return parseStats(output);
    }

    static Map<String, ContainerStat> parseStats(String output) {
        Map<String, ContainerStat> stats = new HashMap<>();
        for (String line : output.split("\n")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            try {
                JSONObject statJson = JSONUtil.parseObj(line.trim());
                String name = statJson.getStr("Name");
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                stats.put(name, new ContainerStat(name,
                        parsePercent(statJson.getStr("CPUPerc")),
                        parsePercent(statJson.getStr("MemPerc"))));
            } catch (Exception e) {
                log.warn("docker stats 输出解析失败: {}", line);
            }
        }
        return stats;
    }

    /**
     * 解析 docker stats 百分比字段，无法解析返回 -1（跳过阈值判断）
     */
    static double parsePercent(String raw) {
        if (StrUtil.isBlank(raw)) {
            return -1;
        }
        try {
            return Double.parseDouble(raw.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 容器实际运行状态落库刷新（inspect 失败视为容器不存在）
     */
    private void refreshStatus(App app, String containerName) {
        String actual = inspectStatus(containerName);
        String current = app.getContainerStatus();
        if (actual == null) {
            if ("running".equals(current)) {
                log.warn("容器不存在但状态为 running，标记为 missing: appId={}, container={}", app.getId(), containerName);
                updateStatus(app.getId(), "missing");
            }
            return;
        }
        if (!actual.equals(current)) {
            log.info("容器状态变化: appId={}, container={}, {} -> {}", app.getId(), containerName, current, actual);
            updateStatus(app.getId(), actual);
        }
    }

    private String inspectStatus(String containerName) {
        String output = execute(new String[]{"docker", "inspect", "--format", "{{.State.Status}}", containerName},
                INSPECT_TIMEOUT_SECONDS);
        return output == null ? null : output.trim();
    }

    /**
     * 阈值告警与处置：内存打满立即处置；CPU 连续 N 次超阈值处置；同一容器遵循处置冷却
     */
    private void checkThresholds(String containerName, ContainerStat stat) {
        if (stat.memoryPercent() >= 0 && stat.memoryPercent() >= memoryThresholdPercent) {
            alertAndRestart(containerName,
                    String.format("内存占用 %.1f%% 达到阈值 %.0f%%", stat.memoryPercent(), memoryThresholdPercent));
            return;
        }
        if (stat.cpuPercent() >= 0 && stat.cpuPercent() >= cpuThresholdPercent) {
            int streak = cpuHighStreak.merge(containerName, 1, Integer::sum);
            if (streak >= cpuSustainedSamples) {
                cpuHighStreak.put(containerName, 0);
                alertAndRestart(containerName,
                        String.format("CPU 连续 %d 次超阈值（当前 %.1f%%）", streak, stat.cpuPercent()));
            }
        } else {
            cpuHighStreak.remove(containerName);
        }
    }

    private void alertAndRestart(String containerName, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(containerName);
        if (last != null && now - last < actionCooldownSeconds * 1000) {
            log.warn("【容器告警】{}：{}（处置冷却中，跳过自动重启，上次处置 {} 秒前）",
                    containerName, reason, (now - last) / 1000);
            return;
        }
        log.warn("【容器告警】{}：{}，执行自动重启", containerName, reason);
        boolean restarted = execute(new String[]{"docker", "restart", containerName}, RESTART_TIMEOUT_SECONDS) != null;
        if (restarted) {
            lastActionTime.put(containerName, now);
            log.warn("【容器告警处置】{} 自动重启成功", containerName);
        } else {
            log.error("【容器告警处置】{} 自动重启失败，请人工介入", containerName);
        }
    }

    private void updateStatus(Long appId, String status) {
        App update = new App();
        update.setId(appId);
        update.setContainerStatus(status);
        appMapper.update(update);
    }

    /**
     * 执行命令并返回标准输出；超时或退出码非 0 返回 null
     */
    private String execute(String[] command, int timeoutSeconds) {
        try {
            Process process = RuntimeUtil.exec(command);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("命令执行超时: {}", String.join(" ", command));
                return null;
            }
            if (process.exitValue() != 0) {
                log.warn("命令执行失败，退出码 {}: {}", process.exitValue(), String.join(" ", command));
                return null;
            }
            return IoUtil.read(process.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("执行命令异常: {}, 错误: {}", String.join(" ", command), e.getMessage());
            return null;
        }
    }

    record ContainerStat(String name, double cpuPercent, double memoryPercent) {
    }
}
