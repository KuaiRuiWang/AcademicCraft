package com.wkr.aicode.core.model;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ModelEndpoints {
    private static final Set<String> OFFICIAL_HOSTS = Set.of("api.openai.com", "api.deepseek.com",
            "dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com", "dashscope-us.aliyuncs.com",
            "cn-hongkong.dashscope.aliyuncs.com", "generativelanguage.googleapis.com", "api.moonshot.cn",
            "open.bigmodel.cn", "openrouter.ai", "api.minimaxi.com", "api.minimax.io");
    private final Set<String> extraHosts;

    public ModelEndpoints(@Value("${platform.models.extra-hosts:}") String extraHosts) {
        this.extraHosts = Arrays.stream(extraHosts.split(",")).map(String::strip)
                .map(host -> host.toLowerCase(Locale.ROOT)).filter(host -> !host.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public URI validate(String url, ModelProtocol protocol) {
        ThrowUtils.throwIf(url == null || url.length() > 512, ErrorCode.PARAMS_ERROR, "请输入有效的 Base URL");
        URI endpoint;
        try {
            endpoint = URI.create(url.strip().replaceFirst("/+$", ""));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Base URL 格式不正确");
        }
        String host = endpoint.getHost();
        ThrowUtils.throwIf(!"https".equalsIgnoreCase(endpoint.getScheme()) || host == null || endpoint.getUserInfo() != null
                        || endpoint.getQuery() != null || endpoint.getFragment() != null
                        || endpoint.getPort() != -1 && endpoint.getPort() != 443,
                ErrorCode.PARAMS_ERROR, "Base URL 需为 HTTPS API 根地址");
        host = host.toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!OFFICIAL_HOSTS.contains(host) && !host.endsWith(".maas.aliyuncs.com") && !extraHosts.contains(host),
                ErrorCode.PARAMS_ERROR, "Base URL 域名尚未列入平台允许的模型服务域名");
        String path = endpoint.getPath();
        ThrowUtils.throwIf(path.endsWith("/chat/completions") || path.endsWith("/responses") || path.endsWith("/messages"),
                ErrorCode.PARAMS_ERROR, "请填写 API 根地址，例如 https://api.openai.com/v1");
        ThrowUtils.throwIf(protocol == ModelProtocol.DASHSCOPE_IMAGE && !path.endsWith("/api/v1"),
                ErrorCode.PARAMS_ERROR, "DashScope 图像接口的 Base URL 应以 /api/v1 结尾");
        return endpoint;
    }
}
