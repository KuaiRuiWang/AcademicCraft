package com.wkr.aicode.controller;

import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
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
}
