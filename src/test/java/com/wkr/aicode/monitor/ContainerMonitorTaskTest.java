package com.wkr.aicode.monitor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 容器监控任务单元测试：docker stats 输出解析、百分比解析与容错
 * （阈值处置与落库逻辑依赖真实 Docker 与数据库，另行服务器验证）
 *
 * @author <a>百夜</a>
 */
class ContainerMonitorTaskTest {

    @Test
    void parseStatsReadsRealDockerStatsJsonLines() {
        String output = """
                {"BlockIO":"0B / 0B","CPUPerc":"0.15%","Container":"a1b2c3","ID":"a1b2c3","MemPerc":"12.34%","MemUsage":"123.4MiB / 1GiB","Name":"fullstack_pqet89","NetIO":"1.2kB / 0B","PIDs":"23"}
                {"BlockIO":"0B / 0B","CPUPerc":"2.50%","Container":"d4e5f6","ID":"d4e5f6","MemPerc":"80.00%","MemUsage":"800MiB / 1GiB","Name":"fullstack_pqet89_mysql","NetIO":"0B / 0B","PIDs":"40"}
                """;
        Map<String, ContainerMonitorTask.ContainerStat> stats = ContainerMonitorTask.parseStats(output);
        assertEquals(2, stats.size());
        ContainerMonitorTask.ContainerStat app = stats.get("fullstack_pqet89");
        assertEquals(0.15, app.cpuPercent(), 0.001);
        assertEquals(12.34, app.memoryPercent(), 0.001);
        ContainerMonitorTask.ContainerStat mysql = stats.get("fullstack_pqet89_mysql");
        assertEquals(2.50, mysql.cpuPercent(), 0.001);
        assertEquals(80.00, mysql.memoryPercent(), 0.001);
    }

    @Test
    void parseStatsSkipsMalformedAndBlankLines() {
        String output = "\n" + "not a json line\n" +
                "{\"CPUPerc\":\"1.00%\",\"MemPerc\":\"5.00%\",\"Name\":\"fullstack_x\"}\n" +
                "{\"CPUPerc\":\"1.00%\"}\n" +
                "   \n";
        Map<String, ContainerMonitorTask.ContainerStat> stats = ContainerMonitorTask.parseStats(output);
        assertEquals(1, stats.size());
        assertTrue(stats.containsKey("fullstack_x"));
    }

    @Test
    void parseStatsReturnsEmptyForEmptyOutput() {
        assertTrue(ContainerMonitorTask.parseStats("").isEmpty());
    }

    @Test
    void parsePercentHandlesPercentSuffixAndInvalidValues() {
        assertEquals(85.3, ContainerMonitorTask.parsePercent("85.3%"), 0.001);
        assertEquals(0.0, ContainerMonitorTask.parsePercent("0.00%"), 0.001);
        assertEquals(-1.0, ContainerMonitorTask.parsePercent(""), 0.001);
        assertEquals(-1.0, ContainerMonitorTask.parsePercent("  "), 0.001);
        assertEquals(-1.0, ContainerMonitorTask.parsePercent("abc%"), 0.001);
        assertEquals(-1.0, ContainerMonitorTask.parsePercent(null), 0.001);
    }

    @Test
    void parsePercentKeepsValuesWithoutSuffixUsable() {
        assertEquals(90.0, ContainerMonitorTask.parsePercent("90.0"), 0.001);
    }

    @Test
    void statsWithoutNameAreIgnored() {
        Map<String, ContainerMonitorTask.ContainerStat> stats =
                ContainerMonitorTask.parseStats("{\"CPUPerc\":\"10%\",\"MemPerc\":\"20%\"}");
        assertFalse(stats.containsKey(""));
        assertTrue(stats.isEmpty());
    }
}
