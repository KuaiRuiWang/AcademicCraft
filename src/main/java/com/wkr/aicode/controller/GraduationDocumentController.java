package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.core.thesis.GraduationDocumentKind;
import com.wkr.aicode.ratelimter.annotation.RateLimit;
import com.wkr.aicode.ratelimter.enums.RateLimitType;
import com.wkr.aicode.service.GraduationDocumentService;
import com.wkr.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/{appId}/graduation-documents/{kind}")
public class GraduationDocumentController {
    private final GraduationDocumentService documents;
    private final UserService users;

    @GetMapping
    public BaseResponse<GraduationDocumentService.View> get(@PathVariable Long appId, @PathVariable GraduationDocumentKind kind,
                                                            HttpServletRequest request) {
        return ResultUtils.success(documents.get(appId, kind, users.getLoginUser(request)));
    }

    @PostMapping(value = "/template", consumes = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public BaseResponse<Boolean> importTemplate(@PathVariable Long appId, @PathVariable GraduationDocumentKind kind,
                                                @RequestParam String filename, HttpServletRequest request) throws IOException {
        documents.importTemplate(appId, kind, users.getLoginUser(request), filename, request.getInputStream());
        return ResultUtils.success(true);
    }

    @PostMapping("/generate")
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "材料生成请求过于频繁，请稍后再试")
    public BaseResponse<Boolean> generate(@PathVariable Long appId, @PathVariable GraduationDocumentKind kind,
                                          @RequestParam String templateId, HttpServletRequest request) {
        documents.generate(appId, kind, users.getLoginUser(request), templateId);
        return ResultUtils.success(true);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long appId, @PathVariable GraduationDocumentKind kind,
                                         HttpServletRequest request) {
        byte[] file = documents.download(appId, kind, users.getLoginUser(request));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(kind.title + ".docx", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(file);
    }
}
