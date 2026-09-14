package com.wkr.aicode.langGrapgh4J;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.AiModifyIntentRoutingService;
import com.wkr.aicode.ai.AiModifyIntentRoutingServiceFactory;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.AiCodeGeneratorFacade;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.model.enums.ModifyScopeEnum;
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
 * 修改对话意图路由真实模型冒烟测试（T-17）：
 * 1) 三条验收指令的分类结果；2) facade 路由分支 + 修改工作流端到端，仅改动目标目录（沙箱回归）
 * 需要网络与模型额度，依赖已生成的全栈项目产物（appId=456911146833846272）
 */
@Slf4j
@SpringBootTest
class FullstackModifySmokeTest {

    private static final Long APP_ID = 456911146833846272L;

    @Resource
    private AiModifyIntentRoutingServiceFactory aiModifyIntentRoutingServiceFactory;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void routesAcceptancePromptsToExpectedScope() {
        AiModifyIntentRoutingService routingService =
                aiModifyIntentRoutingServiceFactory.createAiModifyIntentRoutingService();
        ModifyScopeEnum frontendScope = routingService.routeModifyScope("把首页标题改成蓝色");
        ModifyScopeEnum backendScope = routingService.routeModifyScope("给 /api/posts 增加分页参数");
        ModifyScopeEnum bothScope = routingService.routeModifyScope("换主题并同步改接口");
        log.info("冒烟结果：分类=前端[{}] 后端[{}] 前后端[{}]", frontendScope, backendScope, bothScope);
        Assertions.assertEquals(ModifyScopeEnum.FRONTEND, frontendScope, "“把首页标题改成蓝色”应分类为仅前端");
        Assertions.assertEquals(ModifyScopeEnum.BACKEND, backendScope, "“给 /api/posts 增加分页参数”应分类为仅后端");
        Assertions.assertEquals(ModifyScopeEnum.BOTH, bothScope, "“换主题并同步改接口”应分类为前后端");
    }

    @Test
    void frontendModifyOnlyTouchesFrontendDir() {
        File root = new File(AppConstant.fullstackProjectDir(APP_ID));
        Assertions.assertTrue(new File(root, "frontend").isDirectory(),
                "前置条件：appId=" + APP_ID + " 的全栈产物需已生成");
        resetFrontendTitleBaseline(root);
        long startMillis = System.currentTimeMillis();
        List<String> chunks = aiCodeGeneratorFacade
                .generateAndSaveCodeStream("把首页的标题文字改成「我的待办清单」，并用蓝色显示",
                        CodeGenTypeEnum.FULLSTACK_VUE_NODEJS, APP_ID)
                .collectList()
                .block(Duration.ofMinutes(14));
        Assertions.assertNotNull(chunks, "流未产出任何数据");

        Map<String, Integer> roleCount = new HashMap<>();
        for (String chunk : chunks) {
            JSONObject obj = JSONUtil.parseObj(chunk);
            String role = obj.getStr("agentRole");
            roleCount.merge(role == null ? "(none)" : role, 1, Integer::sum);
        }
        log.info("冒烟结果：分片总数={}，角色分布={}", chunks.size(), roleCount);
        Assertions.assertTrue(roleCount.getOrDefault("frontend", 0) > 0, "缺少 frontend 角色消息");
        Assertions.assertFalse(roleCount.containsKey("backend"), "仅前端修改不应出现 backend 角色消息");

        long frontendLatest = latestModified(new File(root, "frontend"));
        long backendLatest = latestModified(new File(root, "backend"));
        log.info("冒烟结果：frontend 最新改动={}，backend 最新改动={}，开始时间={}",
                frontendLatest, backendLatest, startMillis);
        Assertions.assertTrue(frontendLatest > startMillis, "前端目录应有文件被修改");
        Assertions.assertTrue(backendLatest < startMillis, "后端目录不应有文件被修改");
    }

    /**
     * 前置：把待改标题重置为基线文本，保证用例可重复执行。
     * 否则首轮执行后标题已等于目标值，模型会判断「无需改动」而不写文件，
     * 导致「前端目录应有文件被修改」断言在后续每一轮都失败（测试自身不可重跑）。
     */
    private void resetFrontendTitleBaseline(File root) {
        File page = new File(root, "frontend/src/pages/TodoPage.vue");
        Assertions.assertTrue(page.isFile(), "前置条件：待修改页面 " + page + " 需存在");
        String content = FileUtil.readUtf8String(page);
        String reset = content.replaceFirst("<h1[^>]*>[^<]*</h1>", "<h1>待办事项</h1>");
        Assertions.assertNotEquals(content, reset,
                "前置条件：未能在 " + page.getName() + " 中定位 <h1> 标题，测试无法构造改动");
        FileUtil.writeUtf8String(reset, page);
        log.info("前置：已将 {} 标题重置为基线，等待模型修改", page.getName());
    }

    private long latestModified(File dir) {
        return FileUtil.loopFiles(dir).stream().mapToLong(File::lastModified).max().orElse(0L);
    }
}
