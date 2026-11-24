package com.wkr.aicode.core.thesis;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** 依据冻结资料逐章写作，模型不持有文件工具或其他应用的对话记忆。 */
@Service
public class ThesisWriter {
    public static final List<String> CHAPTERS = List.of("摘要", "Abstract", "第一章 绪论", "第二章 相关技术",
            "第三章 需求分析", "第四章 系统设计", "第五章 系统实现", "第六章 系统测试", "第七章 总结与展望", "参考文献与待补材料");

    @Resource(name = "streamingChatModelPrototype")
    private StreamingChatModel chatModel;

    public String writeChapter(String title, String chapter, String context, String previousSummary) {
        String system;
        try {
            system = new ClassPathResource("prompt/thesis-chapter-system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "论文提示词加载失败");
        }
        String text = complete(system, "论文题目：" + title + "\n本次只写：" + chapter + "\n完整提纲：" + String.join("、", CHAPTERS)
                        + "\n前文摘要（仅用于术语衔接，不是新增证据）：\n" + previousSummary
                        + "\n<project_sources>\n" + context + "\n</project_sources>");
        // 只移除整章的 Markdown 包装，保留正文末尾的配图围栏。
        String unwrapped = text.replaceFirst("^\\s*```(?:markdown|md)?[ \\t]*\\R", "");
        if (!unwrapped.equals(text)) unwrapped = unwrapped.replaceFirst("\\R```\\s*$", "");
        return unwrapped.replaceFirst("^#{1,2} [^\\n]+\\n", "").strip();
    }

    public String drawDiagram(String digest, String reference, String caption, String feedback) {
        try {
            String prompt = new ClassPathResource("prompt/thesis-diagram-system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
            return complete(prompt, "图题：" + caption + "\n<source_diagram>\n" + digest
                    + "\n</source_diagram>\n<layout_reference>\n" + reference + "\n</layout_reference>\n" + feedback)
                    .replaceFirst("(?s)^\\s*```(?:svg|xml)?\\s*\\n", "").replaceFirst("\\n```\\s*$", "").strip();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "配图提示词加载失败");
        }
    }

    public String rewriteSelection(String title, String chapter, String selectedText, String instruction,
                                   String beforeText, String afterText) {
        try {
            String prompt = new ClassPathResource("prompt/thesis-rewrite-system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
            String input = JSONUtil.toJsonStr(Map.of("论文题目", title, "章节", chapter, "选中原文", selectedText,
                    "修改要求", instruction, "前文片段", beforeText, "后文片段", afterText));
            return complete(prompt, input, 45, "AI 修改建议输出不完整，请重试").strip();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "论文局部修改提示词加载失败");
        } catch (CompletionException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 暂未生成修改建议，请稍后重试");
        }
    }

    private String complete(String system, String input) {
        return complete(system, input, 180, "章节输出不完整，请重试该章节");
    }

    private String complete(String system, String input, int timeoutSeconds, String incompleteMessage) {
        // 收齐流式响应后再交给调用方，避免使用截断的结果。
        ChatRequest request = ChatRequest.builder().messages(List.of(SystemMessage.from(system), UserMessage.from(input)))
                .parameters(OpenAiChatRequestParameters.builder().reasoningEffort("none").build()).build();
        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        chatModel.chat(request, new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String text) { }
            @Override public void onCompleteResponse(ChatResponse response) { completed.complete(response); }
            @Override public void onError(Throwable error) { completed.completeExceptionally(error); }
        });
        ChatResponse response = completed.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
        String text = response.aiMessage().text();
        if (response.finishReason() == FinishReason.LENGTH || text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, incompleteMessage);
        }
        return text;
    }
}
