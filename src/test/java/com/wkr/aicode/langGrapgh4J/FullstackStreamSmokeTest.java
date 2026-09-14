package com.wkr.aicode.langGrapgh4J;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.AiCodeGeneratorFacade;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全栈工作流真实模型冒烟测试：驱动完整工作流，校验 SSE 流中的角色标识与产物目录
 * 需要网络与模型额度，避免频繁跑全量测试时可单独执行
 */
@Slf4j
@SpringBootTest
class FullstackStreamSmokeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void realModelStreamCarriesAgentRoles() {
        // T-17 起已生成产物会走修改工作流，本测试验证从零生成，先清理上次产物
        File root = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, AppConstant.FULLSTACK_PROJECT_DIR_PREFIX + 0L);
        FileUtil.del(root);
        String prompt = "做一个极简待办清单网站：单页面，支持新增/勾选完成/删除待办，数据保存到后端。"
                + "请尽量精简：前端一个 Vue3 单页，后端接口不超过 4 个。";
        List<String> chunks = aiCodeGeneratorFacade
                .generateAndSaveCodeStream(prompt, CodeGenTypeEnum.FULLSTACK_VUE_NODEJS, 0L)
                .collectList()
                .block(Duration.ofMinutes(14));
        Assertions.assertNotNull(chunks, "流未产出任何数据");

        Map<String, Integer> roleCount = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        int parseFail = 0;
        for (String chunk : chunks) {
            try {
                JSONObject obj = JSONUtil.parseObj(chunk);
                String role = obj.getStr("agentRole");
                roleCount.merge(role == null ? "(none)" : role, 1, Integer::sum);
                typeCount.merge(String.valueOf(obj.getStr("type")), 1, Integer::sum);
            } catch (Exception e) {
                parseFail++;
            }
        }
        log.info("冒烟结果：分片总数={}，JSON解析失败={}", chunks.size(), parseFail);
        log.info("冒烟结果：角色分布={}", roleCount);
        log.info("冒烟结果：类型分布={}", typeCount);

        Assertions.assertEquals(0, parseFail, "所有分片都应是合法 JSON 信封");
        Assertions.assertTrue(roleCount.getOrDefault("architect", 0) > 0, "缺少 architect 角色消息");
        Assertions.assertTrue(roleCount.getOrDefault("frontend", 0) > 0, "缺少 frontend 角色消息");
        Assertions.assertTrue(roleCount.getOrDefault("backend", 0) > 0, "缺少 backend 角色消息");

        Assertions.assertTrue(new File(root, "api-contract.json").isFile(), "缺少 api-contract.json");
        Assertions.assertTrue(new File(root, "frontend").isDirectory(), "缺少 frontend 目录");
        Assertions.assertTrue(new File(root, "backend").isDirectory(), "缺少 backend 目录");
        Assertions.assertTrue(new File(root, "backend/public/index.html").isFile(),
                "构建节点应产出 backend/public/index.html");
        log.info("冒烟结果：产物目录={}", root.getAbsolutePath());
    }
}
