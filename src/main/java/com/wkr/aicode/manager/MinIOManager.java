package com.wkr.aicode.manager;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MinIOManager {

    @Resource
    private MinioClient minioClient;

    private final String PUBLIC_BUCKET="ai-code-public";

    private final String PRIVATE_BUCKET="ai-code-private";

    @Value("${minio.endpoint}")
    private String endpoint;

    public String uploadFilePublic(String key, File file) {
        try (InputStream is = new FileInputStream(file)) {
            uploadInput(PUBLIC_BUCKET,key,is);
            return endpoint + "/" + PUBLIC_BUCKET + key;
        } catch (Exception e) {
            log.error("上传失败", e);
            return null;
        }
    }

    public String uploadFilePrivate(String key, File file) {
        try (InputStream is = new FileInputStream(file)) {
            uploadInput(PRIVATE_BUCKET,key,is);
            return endpoint + "/" + PRIVATE_BUCKET + key;
        } catch (Exception e) {
            log.error("上传失败", e);
            return null;
        }
    }

    /**
     * 简单上传-流式上传
     *
     * @param bucketName  bucket名称
     * @param key         文件名
     * @param inputStream 输入流
     * @return ObjectWriteResponse 上传结果
     */
    public ObjectWriteResponse uploadInput(String bucketName, String key, InputStream inputStream)
            throws Exception {
        //设置权限
        Map<String, String> userMetadata = new HashMap<>();
        //设置为公有读
        userMetadata.put("access-control", "public-read");
        //设置为私有读
        // userMetadata.put("access-control", "private");
        // 准备OSS上传对象请求
        PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                // bucketName
                .bucket(bucketName)
                // 文件名称
                .object(key)
                //设置文件权限
                .userMetadata(userMetadata)
                .stream(inputStream, inputStream.available(), -1)
                .build();
        // 上传文件
        return minioClient.putObject(putObjectArgs);
    }

    /**
     * 获取文件地址
     *
     * @param bucketName bucketName
     * @param fileName        文件名称
     * @param duration   过期时长
     * @param unit       过期时长单位
     * @return 文件地址
     */
    public String getObjectUrl(String bucketName, String fileName, int duration, TimeUnit unit)
            throws Exception {
        // 查看文件地址
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                // bucketName
                .bucket(bucketName)
                // 文件名称
                .object(fileName)
                // 过期参数
                .expiry(duration, unit)
                // 请求方式
                .method(Method.GET)
                // 构建参数
                .build()
        );
    }
    /**
     * 通过bucketName、key删除文件
     *
     * @param bucketName bucket名称
     * @param key        文件名
     */
    public void delObject(String bucketName, String key) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(key)
                        .build());
    }


}
