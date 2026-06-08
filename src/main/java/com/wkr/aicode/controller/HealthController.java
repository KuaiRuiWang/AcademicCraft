package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @GetMapping("/redis")
    public BaseResponse<String> redis() {
        redisTemplate.opsForValue().set("test", "test");
        return ResultUtils.success("ok");
    }


}
