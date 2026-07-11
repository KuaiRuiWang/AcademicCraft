package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.service.FileService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/common")
public class CommonController {

    @Resource
    private FileService fileService;

    /**
     * 图片上传
     *
     * @param file 图片文件
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    public BaseResponse<String> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.uploadImage(file);
        return ResultUtils.success(url);
    }

}
