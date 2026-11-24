package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.core.thesis.ThesisExportService;
import com.wkr.aicode.model.dto.app.ThesisChapterUpdateRequest;
import com.wkr.aicode.model.dto.app.ThesisSelectionRewriteRequest;
import com.wkr.aicode.model.vo.app.ThesisVO;
import com.wkr.aicode.ratelimter.annotation.RateLimit;
import com.wkr.aicode.ratelimter.enums.RateLimitType;
import com.wkr.aicode.service.ThesisService;
import com.wkr.aicode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/app/{appId}/thesis")
public class ThesisController {
    @Resource private ThesisService thesisService;
    @Resource private UserService userService;
    @Resource private ThesisExportService exportService;

    @GetMapping
    public BaseResponse<ThesisVO> get(@PathVariable Long appId, HttpServletRequest request) {
        return ResultUtils.success(thesisService.getThesis(appId, userService.getLoginUser(request)));
    }

    @PostMapping("/generate")
    public BaseResponse<Boolean> generate(@PathVariable Long appId, HttpServletRequest request) {
        thesisService.generate(appId, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @PutMapping("/chapters/{chapterNo}")
    public BaseResponse<Boolean> updateChapter(@PathVariable Long appId, @PathVariable Integer chapterNo,
                                              @RequestBody ThesisChapterUpdateRequest input, HttpServletRequest request) {
        thesisService.saveChapter(appId, chapterNo, input, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @PostMapping("/chapters/{chapterNo}/rewrite")
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 修改请求过于频繁，请稍后再试")
    public BaseResponse<String> rewriteSelection(@PathVariable Long appId, @PathVariable Integer chapterNo,
                                                 @RequestBody ThesisSelectionRewriteRequest input, HttpServletRequest request) {
        return ResultUtils.success(thesisService.rewriteSelection(appId, chapterNo, input, userService.getLoginUser(request)));
    }

    @GetMapping("/chapters/{chapterNo}/mermaid/{figureNo}")
    public void previewMermaid(@PathVariable Long appId, @PathVariable Integer chapterNo, @PathVariable int figureNo,
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path image = thesisService.previewMermaid(appId, chapterNo, figureNo, userService.getLoginUser(request));
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentLengthLong(Files.size(image));
        Files.copy(image, response.getOutputStream());
    }

    @GetMapping("/export")
    public void export(@PathVariable Long appId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        ThesisVO thesis = thesisService.getThesis(appId, userService.getLoginUser(request));
        Path file = exportService.export(thesis);
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename=\"thesis-" + appId + ".docx\"");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }
}
