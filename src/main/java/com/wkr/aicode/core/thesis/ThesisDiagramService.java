package com.wkr.aicode.core.thesis;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用 Diagram Design 保留图的语义，单独生成并校验 SVG。 */
@Service
@Slf4j
public class ThesisDiagramService {
    private static final Pattern MERMAID = Pattern.compile("(?m)^```mermaid[ \\t]*\\R(.*?)^```[ \\t]*(?=\\R|$)(?:\\R[ \\t]*)*(图\\s*\\d[^\\r\\n]*)?", Pattern.DOTALL);
    @Resource private ThesisTools tools;
    @Resource private ThesisWriter writer;

    public String renderChapter(Long appId, String revision, Integer chapterNo, String content) {
        if (content.contains("```diagram-design")) validateChapter(appId, content);
        Matcher matches = MERMAID.matcher(content);
        if (!matches.find()) return content;
        Path work = tools.root().resolve(appId.toString()).resolve("diagrams").resolve(revision).resolve(chapterNo.toString());
        StringBuilder result = new StringBuilder();
        int index = 0;
        do {
            // 1. 使用上游提取器冻结图的节点、关系和字段。
            if (++index > 8) throw new BusinessException(ErrorCode.PARAMS_ERROR, "章节配图过多，请拆分章节");
            Path directory = work.resolve(Integer.toString(index));
            String rawSource = matches.group(1).strip();
            Path sourceFile = write(directory.resolve("source.mmd"), rawSource);
            var extracted = JSONUtil.parseObj(tools.run("diagram_design.py", directory, 20, "extract", sourceFile.toAbsolutePath().toString()));
            String source = extracted.getStr("mermaidSource", rawSource);
            String embeddedCaption = extracted.getStr("embeddedCaption");
            extracted.remove("mermaidSource");
            extracted.remove("embeddedCaption");
            String type = extracted.getStr("type");
            String digest = extracted.toString();
            String reference = resource("thesis/vendor/diagram-design/references/type-" + type + ".md")
                    + "\n模板仅参考结构和线框语法，输出时改写为论文的 SVG 展示属性：\n"
                    + resource("thesis/vendor/diagram-design/assets/template.html");
            String caption = matches.group(2);
            if (caption == null) caption = embeddedCaption;
            if (caption == null) caption = "图" + Math.max(1, chapterNo - 2) + "." + index + " 系统设计图";
            caption = caption.strip();
            String feedback = "";
            String figure = null;
            // 2. 模型只重排源图；不完整或错位时带校验反馈修正一次。
            for (int attempt = 0; attempt < 2; attempt++) {
                String svg = writer.drawDiagram(digest, reference, caption, feedback);
                String candidate = JSONUtil.toJsonStr(Map.of("version", 1, "source", source, "svg", svg, "caption", caption));
                Path payload = write(directory.resolve("figure.json"), candidate);
                try {
                    tools.run("diagram_design.py", directory, 60, "validate", payload.toAbsolutePath().toString());
                    figure = candidate;
                    break;
                } catch (BusinessException error) {
                    if (attempt == 1) {
                        // 排版优化失败时保留语义草图，避免阻断整篇论文。
                        log.warn("配图排版未通过校验，保留原图，appId={}，chapterNo={}，figure={}，原因={}",
                                appId, chapterNo, index, error.getMessage());
                        break;
                    }
                    feedback = "上一版 SVG：\n" + svg + "\n校验反馈（仅用于修正布局，不可改变源图语义）：\n" + error.getMessage();
                }
            }
            String block = figure == null
                    ? "```mermaid\n" + source + "\n```\n\n" + caption + "\n\n"
                    : "```diagram-design\n" + figure + "\n```\n\n";
            matches.appendReplacement(result, Matcher.quoteReplacement(block));
        } while (matches.find());
        matches.appendTail(result);
        if (result.length() > 100_000) throw new BusinessException(ErrorCode.PARAMS_ERROR, "配图后的章节超过编辑长度限制，请拆分复杂图表");
        return result.toString();
    }

    public synchronized Path previewMermaid(Long appId, String content, int figureNo) {
        if (figureNo < 1 || figureNo > 8) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "配图不存在");
        Matcher matches = MERMAID.matcher(content);
        for (int number = 1; number <= figureNo; number++) {
            if (!matches.find()) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "配图不存在");
        }

        // 按源图缓存，编辑过的章节会请求并绘制新版本。
        String source = matches.group(1).strip();
        Path directory = tools.root().resolve(appId.toString()).resolve("mermaid-previews").resolve(DigestUtil.sha256Hex(source));
        Path image = directory.resolve("diagram.png");
        if (Files.isRegularFile(image)) return image;
        try {
            Path input = write(directory.resolve("source.mmd"), source);
            tools.run("diagram_design.py", directory, 60, "render-mermaid", input.toAbsolutePath().toString(),
                    "--target", image.toAbsolutePath().toString());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配图暂时无法预览，可查看图中结构");
        }
        if (!Files.isRegularFile(image)) throw new BusinessException(ErrorCode.OPERATION_ERROR, "配图暂时无法预览，可查看图中结构");
        return image;
    }

    public void validateChapter(Long appId, String content) {
        if (!content.contains("```diagram-design")) return;
        try {
            Path parent = tools.root().resolve(appId.toString()).resolve("edit-checks");
            Files.createDirectories(parent);
            Path work = Files.createTempDirectory(parent, "chapter-");
            Path input = write(work.resolve("chapter.md"), content);
            tools.run("diagram_design.py", work, 180, "validate-chapter", input.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法校验配图，请重试");
        }
    }

    private Path write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法写入论文配图资料");
        }
    }

    private String resource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图表设计规范加载失败");
        }
    }
}
