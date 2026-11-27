package com.wkr.aicode.core.thesis;

import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 论文排版与配图共用同一套已许可脚本和本地运行环境。 */
@Service
@Slf4j
public class ThesisTools {
    @Value("${thesis.python-command:}") private String pythonCommand;
    @Value("${thesis.mermaid-cli:tools/thesis/node_modules/@mermaid-js/mermaid-cli/src/cli.js}") private String mermaidCli;
    private Path prepared;

    public static final List<String> RESOURCES = List.of("export_thesis.py", "graduation_form.py", "diagram_design.py", "render_svg.mjs",
            "vendor/lunwen/generate_thesis_docx.py", "vendor/lunwen/markdown_utils.py", "vendor/lunwen/LICENSE",
            "vendor/diagram-design/LICENSE", "vendor/diagram-design/scripts/mermaid_extract.py",
            "vendor/diagram-design/scripts/self_check.py", "vendor/diagram-design/scripts/verify_geometry.py");

    public Path root() {
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).getParent().resolve("thesis");
    }

    public synchronized Path prepare() throws IOException {
        if (prepared != null) return prepared;
        Path directory = root().resolve("export-tools");
        for (String name : RESOURCES) {
            Path target = directory.resolve(name);
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".tool-", ".tmp");
            try (var input = new ClassPathResource("thesis/" + name).getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        prepared = directory;
        return directory;
    }

    public String run(String script, Path work, int seconds, String... arguments) {
        try {
            Files.createDirectories(work);
            List<String> command = new ArrayList<>(List.of(resolvePython(), prepare().resolve(script).toAbsolutePath().toString()));
            command.addAll(List.of(arguments));
            Path logs = Files.createTempFile(work, "tool-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("THESIS_MERMAID_CLI", Path.of(mermaidCli).toAbsolutePath().toString());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = builder.redirectErrorStream(true).redirectOutput(logs.toFile()).start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "论文图表或排版处理超时，请重试");
            }
            String output = Files.readString(logs, StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) {
                log.error("论文工具执行失败，script={}，日志文件={}", script, logs);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图表或排版校验失败：" + output.substring(Math.max(0, output.length() - 1800)));
            }
            return output;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法启动论文工具，请检查 Python、Node 和浏览器配置");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "论文工具执行已中断");
        }
    }

    private String resolvePython() {
        if (pythonCommand != null && !pythonCommand.isBlank()) return pythonCommand;
        for (String local : List.of("tools/thesis/.venv/Scripts/python.exe", "tools/thesis/.venv/bin/python")) {
            if (Files.isRegularFile(Path.of(local))) return Path.of(local).toAbsolutePath().toString();
        }
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "python" : "python3";
    }
}
