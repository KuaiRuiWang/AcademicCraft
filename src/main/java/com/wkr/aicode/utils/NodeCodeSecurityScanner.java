package com.wkr.aicode.utils;

import cn.hutool.core.io.FileUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Node.js 代码静态扫描器：部署前扫描后端源码，命中高危模式则拒绝部署（安全设计第 2 层）。
 * 定位为兜底提示而非强依赖（易被编码绕过），以容器隔离为准。
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class NodeCodeSecurityScanner {

    /**
     * 高危模式清单（设计文档 7.2 第 2 层，process.env 收窄为整体读取/输出，避免误伤合法的配置读取）
     */
    private static final List<Rule> RULES = List.of(
            new Rule("进程执行模块 child_process", Pattern.compile("child_process")),
            new Rule("同步进程执行 execSync/spawnSync", Pattern.compile("\\b(execSync|spawnSync)\\b")),
            new Rule("动态代码执行 eval()", Pattern.compile("\\beval\\s*\\(")),
            new Rule("动态代码执行 Function()", Pattern.compile("\\bFunction\\s*\\(")),
            new Rule("危险文件操作 fs.unlink/rm/rmdir/chmod/chown", Pattern.compile("fs\\s*\\.\\s*(unlink|rm|rmdir|chmod|chown)")),
            new Rule("读取系统敏感文件 /etc/passwd|shadow", Pattern.compile("/etc/(passwd|shadow)")),
            new Rule("整体读取/输出环境变量 process.env", Pattern.compile("process\\.env\\s*[),}\\]]")),
            new Rule("底层网络模块 net.connect/dgram", Pattern.compile("\\bnet\\.connect\\b|\\bdgram\\b")),
            new Rule("多进程模块 cluster", Pattern.compile("['\"](node:)?cluster['\"]|\\bcluster\\.fork\\b"))
    );

    private static final int MAX_REPORTED_VIOLATIONS = 5;

    private record Rule(String description, Pattern pattern) {
    }

    public record Violation(String file, int line, String rule) {
    }

    /**
     * 扫描源码目录下所有 .js 文件，命中则抛 BusinessException 拒绝部署（目录不存在视为无代码，交由部署流程报错）
     */
    public static void assertSafe(File sourceDir) {
        List<Violation> violations = scan(sourceDir);
        if (violations.isEmpty()) {
            return;
        }
        String detail = violations.stream()
                .limit(MAX_REPORTED_VIOLATIONS)
                .map(v -> v.file() + ":" + v.line() + " " + v.rule())
                .collect(Collectors.joining("；"));
        if (violations.size() > MAX_REPORTED_VIOLATIONS) {
            detail += "；等共 " + violations.size() + " 处";
        }
        log.warn("代码安全扫描未通过，拒绝部署: {}", detail);
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "代码安全扫描未通过，已拒绝部署：" + detail);
    }

    /**
     * 扫描 .js 源码，返回全部命中项（供单测与日志）
     */
    public static List<Violation> scan(File sourceDir) {
        List<Violation> violations = new ArrayList<>();
        if (sourceDir == null || !sourceDir.isDirectory()) {
            return violations;
        }
        List<File> jsFiles = FileUtil.loopFiles(sourceDir,
                file -> file.isFile() && file.getName().endsWith(".js")
                        && !FileUtil.getAbsolutePath(file).contains("node_modules"));
        for (File jsFile : jsFiles) {
            List<String> lines = FileUtil.readLines(jsFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Rule rule : RULES) {
                    Matcher matcher = rule.pattern().matcher(line);
                    if (matcher.find()) {
                        violations.add(new Violation(jsFile.getName(), i + 1, rule.description()));
                    }
                }
            }
        }
        log.info("代码安全扫描完成，共扫描 {} 个 js 文件，命中 {} 处", jsFiles.size(), violations.size());
        return violations;
    }
}
