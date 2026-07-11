package com.wkr.aicode.service.impl;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.manager.MinIOManager;
import com.wkr.aicode.service.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    /**
     * 允许的图片类型
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp"
    ));

    @Resource
    private MinIOManager minIOManager;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Override
    public String uploadImage(MultipartFile file) {
        // 校验文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 校验文件类型是否为图片
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持上传图片文件（jpg、png、gif、bmp、webp）");
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String key = "/images/" + UUID.randomUUID() + suffix;

        // 上传到 MinIO
        try {
            String bucketName = "ai-code-public";
            minIOManager.uploadInput(bucketName, key, file.getInputStream());
            return endpoint + "/" + bucketName + key;
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        }
    }
}
