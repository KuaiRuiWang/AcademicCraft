package com.wkr.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.core.model.ModelEndpoints;
import com.wkr.aicode.core.model.ModelKeyCipher;
import com.wkr.aicode.core.model.ModelProtocol;
import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.PlatformModelBindingMapper;
import com.wkr.aicode.mapper.PlatformModelMapper;
import com.wkr.aicode.model.entity.PlatformModel;
import com.wkr.aicode.model.entity.PlatformModelBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformModelService {
    private final PlatformModelMapper models;
    private final PlatformModelBindingMapper bindings;
    private final ModelEndpoints endpoints;
    private final ModelKeyCipher keys;
    private final Environment environment;

    public record Input(String name, ModelProtocol protocol, String baseUrl, String modelId, String apiKey,
                        Integer maxTokens, BigDecimal temperature) {
        @Override public String toString() { return "ModelInput[name=" + name + ", protocol=" + protocol + "]"; }
    }
    public record ModelView(Long id, String name, ModelProtocol protocol, String baseUrl, String modelId,
                            boolean hasApiKey, Integer maxTokens, BigDecimal temperature) { }
    public record ModelOption(Long id, String name, ModelProtocol protocol) { }
    public record StepView(ModelStep step, String label, String group, ModelProtocol protocol, Long modelConfigId, String defaultModel) { }
    public record BindingsView(List<StepView> steps, List<ModelOption> models) { }

    public List<ModelView> list() {
        return models.selectListByQuery(QueryWrapper.create().orderBy("createTime", false)).stream().map(this::view).toList();
    }

    @Transactional
    public ModelView save(Long id, Input input) {
        // 1. 校验地址、接口协议和模型参数。
        ThrowUtils.throwIf(input.protocol() == null, ErrorCode.PARAMS_ERROR, "请选择模型接口协议");
        URI endpoint = endpoints.validate(input.baseUrl(), input.protocol());
        String name = text(input.name(), 100, "显示名称");
        String modelId = text(input.modelId(), 180, "模型 ID");
        validateParameters(input);

        // 2. 校验已绑定步骤和凭据，只在明确提供时更换密钥。
        PlatformModel model = id == null ? new PlatformModel() : lockedModel(id);
        if (id != null && !input.protocol().name().equals(model.getProtocol())) {
            requireUnbound(id, "请先解除步骤绑定，再更改接口协议");
        }
        String apiKey = input.apiKey() == null ? "" : input.apiKey().strip();
        if (apiKey.isEmpty()) {
            ThrowUtils.throwIf(id == null || !endpoint.getHost().equalsIgnoreCase(URI.create(model.getBaseUrl()).getHost()),
                    ErrorCode.PARAMS_ERROR, "新增模型或更换服务域名时请填写 API Key");
        } else {
            ThrowUtils.throwIf(apiKey.length() > 4096 || apiKey.chars().anyMatch(Character::isISOControl),
                    ErrorCode.PARAMS_ERROR, "API Key 格式不正确");
            model.setApiKeyCiphertext(keys.encrypt(apiKey));
        }
        model.setName(name);
        model.setProtocol(input.protocol().name());
        model.setBaseUrl(endpoint.toString());
        model.setModelId(modelId);
        model.setMaxTokens(input.maxTokens());
        model.setTemperature(input.temperature());
        try {
            if (id == null) models.insert(model, true);
            else models.update(model, false);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型显示名称已存在");
        }
        return view(model);
    }

    @Transactional
    public void delete(long id) {
        lockedModel(id);
        requireUnbound(id, "请先解除步骤绑定，再删除模型");
        models.deleteById(id);
    }

    public BindingsView listBindings() {
        Map<String, Long> assignments = bindings.selectAll().stream()
                .collect(Collectors.toMap(PlatformModelBinding::getStepKey, PlatformModelBinding::getModelConfigId));
        List<StepView> steps = Arrays.stream(ModelStep.values()).map(step -> new StepView(step,
                step.getLabel(), step.getGroup(), step.getProtocol(), assignments.get(step.name()),
                environment.getProperty(step.getDefaultProperty(), step == ModelStep.LOGO_IMAGE ? "wan2.2-t2i-flash" : ""))).toList();
        List<ModelOption> options = models.selectListByQuery(QueryWrapper.create().orderBy("name", true)).stream()
                .map(model -> new ModelOption(model.getId(), model.getName(), ModelProtocol.valueOf(model.getProtocol()))).toList();
        return new BindingsView(steps, options);
    }

    @Transactional
    public void bind(ModelStep step, Long modelId) {
        if (modelId == null) {
            bindings.deleteById(step.name());
            return;
        }
        PlatformModel model = lockedModel(modelId);
        ThrowUtils.throwIf(!step.getProtocol().name().equals(model.getProtocol()), ErrorCode.PARAMS_ERROR,
                "所选模型的接口协议与此步骤不匹配");
        bindings.bind(step.name(), modelId);
    }

    private PlatformModel lockedModel(long id) {
        PlatformModel model = models.selectOneByQuery(QueryWrapper.create().eq("id", id).forUpdate());
        ThrowUtils.throwIf(model == null, ErrorCode.NOT_FOUND_ERROR, "模型配置不存在");
        return model;
    }

    private void requireUnbound(long id, String message) {
        ThrowUtils.throwIf(bindings.selectCountByQuery(QueryWrapper.create().eq("modelConfigId", id)) > 0,
                ErrorCode.OPERATION_ERROR, message);
    }

    private ModelView view(PlatformModel model) {
        return new ModelView(model.getId(), model.getName(), ModelProtocol.valueOf(model.getProtocol()), model.getBaseUrl(),
                model.getModelId(), model.getApiKeyCiphertext() != null, model.getMaxTokens(), model.getTemperature());
    }

    private String text(String value, int max, String field) {
        String trimmed = value == null ? "" : value.strip();
        ThrowUtils.throwIf(trimmed.isEmpty() || trimmed.length() > max || trimmed.chars().anyMatch(Character::isISOControl),
                ErrorCode.PARAMS_ERROR, field + "应为 1 至 " + max + " 个字符");
        return trimmed;
    }

    private void validateParameters(Input input) {
        ThrowUtils.throwIf(input.maxTokens() != null && input.maxTokens() <= 0, ErrorCode.PARAMS_ERROR, "输出上限应大于 0");
        ThrowUtils.throwIf(input.temperature() != null && (input.temperature().signum() < 0
                        || input.temperature().compareTo(BigDecimal.valueOf(2)) > 0 || input.temperature().stripTrailingZeros().scale() > 3),
                ErrorCode.PARAMS_ERROR, "温度应介于 0 和 2 之间，最多三位小数");
        ThrowUtils.throwIf(input.protocol() == ModelProtocol.DASHSCOPE_IMAGE && (input.maxTokens() != null || input.temperature() != null),
                ErrorCode.PARAMS_ERROR, "图像模型无需填写文本输出参数");
    }
}
