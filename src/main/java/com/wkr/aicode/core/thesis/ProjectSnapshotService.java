package com.wkr.aicode.core.thesis;

import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.model.entity.App;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 冻结本轮论文实际使用的源码材料，不读取依赖、构建产物和环境凭据。 */
@Service
public class ProjectSnapshotService {
    private static final Set<String> SKIP_DIRS = Set.of("node_modules", ".git", "dist", "build", "target", "public");
    private static final Set<String> EXTENSIONS = Set.of("vue", "js", "ts", "jsx", "tsx", "java", "sql", "html", "css", "json", "md");
    private static final int MAX_CONTEXT = 90_000;

    public record Snapshot(String version, String context) { }

    public Snapshot capture(App app) {
        return capture(app, Path.of(AppConstant.codeOutputDir(app.getCodeGenType(), app.getId())));
    }

    Snapshot capture(App app, Path root) {
        if (!Files.isDirectory(root)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先成功生成应用代码，再生成论文");
        }
        try (var walk = Files.walk(root)) {
            List<Path> files = walk.filter(path -> include(root, path)).sorted().toList();
            if (files.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有可用于论文的源码文件");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder context = new StringBuilder("应用需求：\n").append(app.getInitPrompt())
                    .append("\n生成类型：").append(app.getCodeGenType())
                    .append("\n证据边界：以下是源码静态材料。构建、运行、页面截图和功能/性能测试结果未作为证据提供，禁止声称已通过测试。\n");
            digest.update(context.toString().getBytes(StandardCharsets.UTF_8));
            for (Path file : files) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                // 大型导入数据不作为论文上下文，仍以流方式计算摘要以识别文件变更。
                if (Files.size(file) > 1_000_000) {
                    try (var input = new java.security.DigestInputStream(Files.newInputStream(file), digest)) {
                        input.transferTo(java.io.OutputStream.nullOutputStream());
                    }
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                digest.update(bytes);
                if (context.length() >= MAX_CONTEXT) continue;
                String source = new String(bytes, StandardCharsets.UTF_8);
                int limit = Math.min(source.length(), Math.min(12_000, MAX_CONTEXT - context.length()));
                context.append("\n【来源文件：").append(relative).append("】\n")
                        .append(source, 0, limit);
                if (limit < source.length()) context.append("\n[该文件仅提供前部片段，不能推断剩余实现]\n");
            }
            String version = HexFormat.of().formatHex(digest.digest());
            Path snapshot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).getParent()
                    .resolve("thesis").resolve(app.getId().toString()).resolve(version);
            Files.createDirectories(snapshot);
            // 保存完整的本轮输入材料，后续写作与重试均使用同一版本。
            Files.writeString(snapshot.resolve("source-context.txt"), context, StandardCharsets.UTF_8);
            return new Snapshot(version, context.toString());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取论文项目资料失败");
        }
    }

    private boolean include(Path root, Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        Path relative = root.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (SKIP_DIRS.contains(name) || name.startsWith(".") || Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml").contains(name)) return false;
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }
}
