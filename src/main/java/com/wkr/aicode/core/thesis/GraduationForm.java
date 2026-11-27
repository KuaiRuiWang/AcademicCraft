package com.wkr.aicode.core.thesis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 校验学校模板，并用同一组已确认的栏目生成 Word。 */
@Component
@RequiredArgsConstructor
public class GraduationForm {
    private final ThesisTools tools;
    private final ObjectMapper json;

    public List<String> inspect(byte[] template) throws IOException {
        Path work = Files.createTempDirectory("graduation-import-");
        try {
            Files.write(work.resolve("template.docx"), template);
            var report = json.readTree(tools.run("graduation_form.py", work, 30, "inspect", work.toString()));
            if (report.has("error")) throw new BusinessException(ErrorCode.PARAMS_ERROR, report.get("error").asText());
            return List.of(json.treeToValue(report.required("fields"), String[].class));
        } finally {
            FileSystemUtils.deleteRecursively(work);
        }
    }

    public byte[] render(GraduationDocumentKind kind, String title, Map<String, String> sections, byte[] template)
            throws IOException {
        Path work = Files.createTempDirectory("graduation-export-");
        try {
            if (template != null) Files.write(work.resolve("template.docx"), template);
            json.writeValue(work.resolve("content.json").toFile(), Map.of("kind", kind.title, "title", title, "sections", sections));
            tools.run("graduation_form.py", work, 60, "render", work.toString());
            return Files.readAllBytes(work.resolve("result.docx"));
        } finally {
            FileSystemUtils.deleteRecursively(work);
        }
    }
}
