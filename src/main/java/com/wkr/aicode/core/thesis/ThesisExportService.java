package com.wkr.aicode.core.thesis;

import cn.hutool.crypto.digest.DigestUtil;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.vo.app.ThesisVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 文档排版复用固定版本的 lunwen-skill，Java 只负责材料与进程边界。 */
@Service
@Slf4j
public class ThesisExportService {
    @Value("${thesis.python-command:}")
    private String pythonCommand;
    @Value("${thesis.mermaid-cli:tools/thesis/node_modules/@mermaid-js/mermaid-cli/src/cli.js}")
    private String mermaidCli;

    public synchronized Path export(ThesisVO thesis) {
        ThrowUtils.throwIf(!"ready".equals(thesis.getStatus()), ErrorCode.PARAMS_ERROR, "请等待论文生成完成；旧版本请先更新后导出");
        String markdown = markdown(thesis);
        Path root = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).getParent().resolve("thesis");
        Path output = root.resolve(thesis.getAppId().toString()).resolve("exports").resolve(DigestUtil.sha256Hex(markdown));
        Path docx = output.resolve("thesis.docx");
        if (Files.isRegularFile(docx)) return docx;
        try {
            // 1. 从应用包提取已许可的固定版本脚本，部署 jar 时不依赖源码目录。
            Path tools = root.resolve("export-tools");
            for (String name : List.of("export_thesis.py", "vendor/lunwen/generate_thesis_docx.py", "vendor/lunwen/markdown_utils.py", "vendor/lunwen/LICENSE")) {
                Path target = tools.resolve(name);
                Files.createDirectories(target.getParent());
                try (var input = new ClassPathResource("thesis/" + name).getInputStream()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.createDirectories(output);
            Path source = output.resolve("thesis.md");
            Path logs = output.resolve("export.log");
            Files.writeString(source, markdown, StandardCharsets.UTF_8);
            // 2. 参数逐项传递，用户内容仅写入文件，不能成为 shell 命令。
            ProcessBuilder builder = new ProcessBuilder(resolvePython(), tools.resolve("export_thesis.py").toAbsolutePath().toString(),
                    source.toAbsolutePath().toString(), docx.toAbsolutePath().toString());
            builder.environment().put("THESIS_MERMAID_CLI", Path.of(mermaidCli).toAbsolutePath().toString());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = builder.redirectErrorStream(true).redirectOutput(logs.toFile()).start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出超时，请重试或简化图表");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(docx)) {
                log.error("论文 Word 导出失败，appId={}，日志文件={}", thesis.getAppId(), logs);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出失败，请检查 Python、Mermaid 依赖及章节中的图表语法");
            }
            return docx;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法启动 Word 导出工具，请配置 thesis.python-command 并安装导出依赖");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出已中断，请重试");
        }
    }

    String markdown(ThesisVO thesis) {
        StringBuilder result = new StringBuilder("# ").append(thesis.getTitle().replace('\n', ' ')).append("\n\n");
        for (var chapter : thesis.getChapters()) {
            result.append("## ").append(chapter.getTitle()).append("\n\n").append(chapter.getContent()).append("\n\n");
        }
        return result.toString();
    }

    private String resolvePython() {
        if (pythonCommand != null && !pythonCommand.isBlank()) return pythonCommand;
        for (String local : List.of("tools/thesis/.venv/Scripts/python.exe", "tools/thesis/.venv/bin/python")) {
            if (Files.isRegularFile(Path.of(local))) return Path.of(local).toAbsolutePath().toString();
        }
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "python" : "python3";
    }
}
