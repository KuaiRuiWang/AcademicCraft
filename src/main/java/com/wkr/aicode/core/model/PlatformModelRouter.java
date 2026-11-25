package com.wkr.aicode.core.model;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wkr.aicode.mapper.PlatformModelBindingMapper;
import com.wkr.aicode.model.entity.PlatformModel;
import com.wkr.aicode.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlatformModelRouter {
    private final PlatformModelBindingMapper bindings;
    private final ModelKeyCipher keys;
    private final ModelEndpoints endpoints;
    private final AiModelMonitorListener monitor;
    private final Cache<PlatformModel, ChatModel> chatClients = Caffeine.newBuilder().maximumSize(64).build();
    private final Cache<PlatformModel, StreamingChatModel> streamClients = Caffeine.newBuilder().maximumSize(64).build();

    public record ImageSettings(String baseUrl, String modelId, String apiKey) {
        @Override public String toString() { return "ImageSettings[modelId=" + modelId + "]"; }
    }

    public ChatModel chat(ModelStep step, ChatModel fallback) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return PlatformModelRouter.this.chat(configuredModel(step), fallback).chat(request);
            }

            @Override
            public Set<Capability> supportedCapabilities() {
                return fallback.supportedCapabilities();
            }
        };
    }

    public PlatformModel configuredModel(ModelStep step) {
        return bindings.findModel(step.name());
    }

    public ChatModel chat(PlatformModel model, ChatModel fallback) {
        return model == null ? fallback : chatClients.get(model, this::newChat);
    }

    public StreamingChatModel stream(PlatformModel model, StreamingChatModel fallback) {
        return model == null ? fallback : streamClients.get(model, this::newStream);
    }

    public ImageSettings image(ModelStep step, String fallbackModel, String fallbackKey) {
        PlatformModel model = configuredModel(step);
        if (model == null) return new ImageSettings(null, fallbackModel, fallbackKey);
        String baseUrl = endpoints.validate(model.getBaseUrl(), ModelProtocol.DASHSCOPE_IMAGE).toString();
        return new ImageSettings(baseUrl, model.getModelId(), keys.decrypt(model.getApiKeyCiphertext()));
    }

    private ChatModel newChat(PlatformModel model) {
        var endpoint = endpoints.validate(model.getBaseUrl(), ModelProtocol.OPENAI_COMPATIBLE);
        var builder = OpenAiChatModel.builder()
                .baseUrl(endpoint.toString())
                .modelName(model.getModelId()).apiKey(keys.decrypt(model.getApiKeyCiphertext()))
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .timeout(Duration.ofMinutes(3)).customHeaders(Map.of("Accept-Encoding", "identity")).listeners(List.of(monitor));
        if ("api.openai.com".equalsIgnoreCase(endpoint.getHost())) builder.maxCompletionTokens(model.getMaxTokens());
        else builder.maxTokens(model.getMaxTokens());
        return builder.build();
    }

    private StreamingChatModel newStream(PlatformModel model) {
        var endpoint = endpoints.validate(model.getBaseUrl(), ModelProtocol.OPENAI_COMPATIBLE);
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(endpoint.toString())
                .modelName(model.getModelId()).apiKey(keys.decrypt(model.getApiKeyCiphertext()))
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .timeout(Duration.ofMinutes(3)).customHeaders(Map.of("Accept-Encoding", "identity")).listeners(List.of(monitor));
        if ("api.openai.com".equalsIgnoreCase(endpoint.getHost())) builder.maxCompletionTokens(model.getMaxTokens());
        else builder.maxTokens(model.getMaxTokens());
        return builder.build();
    }
}
