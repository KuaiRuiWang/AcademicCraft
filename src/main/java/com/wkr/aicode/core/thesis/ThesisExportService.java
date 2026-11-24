package com.wkr.aicode.core.thesis;

import cn.hutool.crypto.digest.DigestUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.vo.app.ThesisVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 文档排版复用固定版本的 lunwen-skill，Java 只负责材料与进程边界。 */
@Service
public class ThesisExportService {
    @Resource private ThesisTools tools;

    public synchronized Path export(ThesisVO thesis) {
        ThrowUtils.throwIf(!"ready".equals(thesis.getStatus()), ErrorCode.PARAMS_ERROR, "请等待论文生成完成；旧版本请先更新后导出");
        String markdown = markdown(thesis);
        Path root = tools.root();
        Path output = root.resolve(thesis.getAppId().toString()).resolve("exports").resolve(DigestUtil.sha256Hex(markdown));
        Path docx = output.resolve("thesis.docx");
        if (Files.isRegularFile(docx)) return docx;
        try {
            Files.createDirectories(output);
            Path source = output.resolve("thesis.md");
            Files.writeString(source, markdown, StandardCharsets.UTF_8);
            tools.run("export_thesis.py", output, 180, source.toAbsolutePath().toString(), docx.toAbsolutePath().toString());
            if (!Files.isRegularFile(docx)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出失败，请重试");
            }
            return docx;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出失败，请稍后重试");
        } catch (BusinessException e) {
            // 工具日志保留完整错误，下载接口仅返回能在页面处理的提示。
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Word 导出失败，请检查本篇论文的配图后重试");
        }
    }

    String markdown(ThesisVO thesis) {
        StringBuilder result = new StringBuilder("# ").append(thesis.getTitle().replace('\n', ' ')).append("\n\n");
        for (var chapter : thesis.getChapters()) {
            result.append("## ").append(chapter.getTitle()).append("\n\n").append(chapter.getContent()).append("\n\n");
        }
        return result.toString();
    }

}
