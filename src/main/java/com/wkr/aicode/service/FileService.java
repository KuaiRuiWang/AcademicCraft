package com.wkr.aicode.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传图片
     *
     * @param file 图片文件
     * @return 文件访问URL
     */
    String uploadImage(MultipartFile file);
}
