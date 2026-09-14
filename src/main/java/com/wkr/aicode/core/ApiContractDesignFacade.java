package com.wkr.aicode.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.ApiContractDesignService;
import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * API 契约设计外观类
 * 调用架构师 Agent 产出契约，负责 JSON 解析、结构校验、失败重试与落盘
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ApiContractDesignFacade {

    /**
     * 契约文件名（写入产物根目录）
     */
    public static final String CONTRACT_FILE_NAME = "api-contract.json";

    /**
     * 最大尝试次数（首次 + 1 次重试）
     */
    private static final int MAX_ATTEMPTS = 2;

    private static final int MAX_MODELS = 8;

    private static final int MAX_APIS = 40;

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    @Resource
    private ApiContractDesignService apiContractDesignService;

    /**
     * 设计并保存 API 契约
     *
     * @param requirement 用户需求描述
     * @param projectDir  产物根目录
     * @return 契约对象
     */
    public ApiContract designAndSaveContract(String requirement, String projectDir) {
        ApiContract contract = designContract(requirement);
        saveContract(contract, projectDir);
        return contract;
    }

    /**
     * 设计 API 契约（含解析校验与失败重试）
     *
     * @param requirement 用户需求描述
     * @return 契约对象
     */
    public ApiContract designContract(String requirement) {
        ThrowUtils.throwIf(StrUtil.isBlank(requirement), ErrorCode.PARAMS_ERROR, "需求描述不能为空");
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = requirement;
            // 重试时把上次的问题回传给模型，避免盲目重试
            if (attempt > 1) {
                prompt = requirement + "\n\n【重要】上一次输出不符合规范，问题如下：\n" + lastError
                        + "\n请严格按照要求的 JSON 结构重新输出，且只输出 JSON 本身。";
            }
            String rawOutput;
            try {
                rawOutput = apiContractDesignService.designApiContract(prompt);
            } catch (Exception e) {
                lastError = "调用模型失败：" + e.getMessage();
                log.warn("API 契约设计第 {}/{} 次调用失败：{}", attempt, MAX_ATTEMPTS, e.getMessage());
                continue;
            }
            ApiContract contract = parseContract(rawOutput);
            List<String> problems = contract == null
                    ? List.of("输出内容不是合法的 JSON 对象")
                    : validateContract(contract);
            if (problems.isEmpty()) {
                log.info("API 契约设计成功，数据模型 {} 个，接口 {} 个",
                        contract.getModels().size(), contract.getApis().size());
                return contract;
            }
            lastError = String.join("；", problems);
            log.warn("API 契约第 {}/{} 次校验未通过：{}", attempt, MAX_ATTEMPTS, lastError);
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "API 契约生成失败：" + lastError);
    }

    /**
     * 将契约写入产物根目录
     *
     * @param contract   契约对象
     * @param projectDir 产物根目录
     * @return 契约文件
     */
    public File saveContract(ApiContract contract, String projectDir) {
        ThrowUtils.throwIf(contract == null, ErrorCode.PARAMS_ERROR, "契约内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(projectDir), ErrorCode.PARAMS_ERROR, "产物目录不能为空");
        File contractFile = new File(projectDir, CONTRACT_FILE_NAME);
        try {
            FileUtil.mkParentDirs(contractFile);
            FileUtil.writeString(JSONUtil.toJsonPrettyStr(contract), contractFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "写入 API 契约文件失败：" + e.getMessage());
        }
        log.info("API 契约已写入：{}", contractFile.getAbsolutePath());
        return contractFile;
    }

    /**
     * 从模型输出中提取 JSON 并解析为契约对象
     */
    private ApiContract parseContract(String rawOutput) {
        if (StrUtil.isBlank(rawOutput)) {
            return null;
        }
        int start = rawOutput.indexOf('{');
        int end = rawOutput.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String json = rawOutput.substring(start, end + 1);
        try {
            return JSONUtil.toBean(json, ApiContract.class);
        } catch (Exception e) {
            log.warn("API 契约 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 契约结构校验，返回全部问题（空列表表示通过）
     */
    private List<String> validateContract(ApiContract contract) {
        List<String> problems = new ArrayList<>();
        validateModels(contract, problems);
        validateApis(contract, problems);
        if (contract.getResponseFormat() == null) {
            problems.add("缺少 responseFormat 统一响应格式");
        }
        return problems;
    }

    private void validateModels(ApiContract contract, List<String> problems) {
        List<ApiContract.ApiModel> models = contract.getModels();
        if (CollUtil.isEmpty(models)) {
            problems.add("models 数据模型列表不能为空");
            return;
        }
        if (models.size() > MAX_MODELS) {
            problems.add("数据模型数量不能超过 " + MAX_MODELS + " 个");
            return;
        }
        boolean hasPkField = false;
        for (ApiContract.ApiModel model : models) {
            if (model == null || StrUtil.isBlank(model.getTable())) {
                problems.add("存在缺少表名的数据模型");
                continue;
            }
            List<ApiContract.ApiModel.Field> fields = model.getFields();
            if (CollUtil.isEmpty(fields)) {
                problems.add("表 " + model.getTable() + " 缺少字段定义");
                continue;
            }
            for (ApiContract.ApiModel.Field field : fields) {
                if (field == null || StrUtil.isBlank(field.getName()) || StrUtil.isBlank(field.getType())) {
                    problems.add("表 " + model.getTable() + " 存在缺少字段名或字段类型的字段");
                    break;
                }
                if (Boolean.TRUE.equals(field.getPk())) {
                    hasPkField = true;
                }
            }
        }
        if (!hasPkField) {
            problems.add("数据模型必须包含主键字段（pk 为 true）");
        }
    }

    private void validateApis(ApiContract contract, List<String> problems) {
        List<ApiContract.ApiEndpoint> apis = contract.getApis();
        if (CollUtil.isEmpty(apis)) {
            problems.add("apis 接口清单不能为空");
            return;
        }
        if (apis.size() > MAX_APIS) {
            problems.add("接口数量不能超过 " + MAX_APIS + " 个");
            return;
        }
        boolean hasHealthApi = false;
        for (ApiContract.ApiEndpoint api : apis) {
            if (api == null || StrUtil.isBlank(api.getMethod()) || StrUtil.isBlank(api.getPath())) {
                problems.add("存在缺少 method 或 path 的接口");
                continue;
            }
            String method = api.getMethod().trim().toUpperCase();
            String path = api.getPath().trim();
            if (!ALLOWED_METHODS.contains(method)) {
                problems.add("接口 " + api.getMethod() + " " + path + " 的 HTTP 方法不合法");
            }
            if (!path.startsWith("/api")) {
                problems.add("接口路径 " + path + " 必须以 /api 开头");
            }
            if ("GET".equals(method) && "/api/health".equals(path)) {
                hasHealthApi = true;
            }
        }
        if (!hasHealthApi) {
            problems.add("必须包含健康检查接口 GET /api/health");
        }
    }
}
