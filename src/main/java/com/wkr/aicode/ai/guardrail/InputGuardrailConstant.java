package com.wkr.aicode.ai.guardrail;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public interface InputGuardrailConstant {

    // 注入攻击模式
    // 设计原则：只匹配明确的注入攻击特征，避免误伤代码生成场景的正常 prompt
    // 以下句式已排除（在代码生成场景中太常见）：
    //   - "reply only with the code" / "只回复代码" → 会被 req.#6 误杀
    //   - "you must generate" / "你必须生成"       → 会被 req.#5 误杀
    //   - "act as a React expert" / "作为XXX"     → 会被 req.#2 误杀
    //   - "from now on you are" / "从现在开始"    → 会被 req.#2 误杀
    List<Pattern> INJECTION_PATTERNS = Arrays.asList(

            // ======================================================
            // 1. 指令忽略/覆盖 —— 攻击者试图让模型无视系统提示
            // ======================================================
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all|prior)\\s+(?:instructions?|commands?|prompts?|rules?|context)"),
            Pattern.compile("(?i)(?:forget|disregard|discard)\\s+(?:everything|all)\\s+(?:above|before|earlier|you\\s+(?:were|have)\\s+been\\s+told)"),
            Pattern.compile("(?i)override\\s+(?:all|previous|prior|the)\\s+(?:instructions?|commands?|prompts?|rules?|safeguards?)"),
            Pattern.compile("(?i)(?:overwrite|replace|reset)\\s+(?:your|the)\\s+(?:instructions?|commands?|prompts?|rules?)"),
            Pattern.compile("(?i)this\\s+(?:supersedes|overrides|takes\\s+priority\\s+over)\\s+(?:all|previous|earlier)"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?|directives?|rules?)\\s*[：:]"),

            // ======================================================
            // 2. 角色/身份劫持 —— 攻击者试图篡改 AI 的身份定义
            //    已移除: "act as / from now on you are / you are now" 等常见角色设定句式
            // ======================================================
            Pattern.compile("(?i)your\\s+(?:new|real|true|actual|hidden)\\s+(?:role|identity|persona|character|job|purpose)\\s+(?:is|should\\s+be)"),
            Pattern.compile("(?i)you\\s+have\\s+been\\s+(?:transformed|reprogrammed|reconfigured)\\s+into"),

            // ======================================================
            // 3. 系统级分隔符注入 —— 伪造模型内部标记
            // ======================================================
            Pattern.compile("(?i)(?:<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>)"),
            Pattern.compile("(?i)\\[system\\s*(?:prompt|message|instruction)\\]"),
            Pattern.compile("(?i)</?system[^>]*>"),
            Pattern.compile("(?i)</?instruction[^>]*>"),

            // ======================================================
            // 4. DAN / 越狱 / 非受限模式
            // ======================================================
            Pattern.compile("(?i)\\bDAN\\s*(?:mode|prompt|jailbreak)?\\b"),
            Pattern.compile("(?i)\\b(?:developer|god|admin|root|debug|unrestricted|unfiltered)\\s*mode\\b"),
            Pattern.compile("(?i)\\b(?:jailbreak|jail\\s*break)\\b"),
            Pattern.compile("(?i)(?:do\\s+anything\\s+now|no\\s+restrictions?|no\\s+limits?|no\\s+rules?)"),
            Pattern.compile("(?i)remove\\s+(?:all\\s+)?(?:restrictions?|limits?|safeguards?|filters?|protections?)"),
            Pattern.compile("(?i)(?:you\\s+are\\s+(?:free|unrestricted|unbound|liberated)\\s*(?:now|to\\s+do\\s+anything))"),

            // ======================================================
            // 5. 指令层级破坏 —— 宣称自己的话比系统指令更优先
            //    已移除: "you must/will/shall do/output/write" 等正常代码生成指令句式
            // ======================================================
            Pattern.compile("(?i)(?:absolute|top|highest|overriding)\\s+priority\\s+(?:instruction|command|order)"),
            Pattern.compile("(?i)I\\s+(?:command|order|demand|insist)\\s+you\\s+(?:to\\s+)?(?:ignore|disobey|forget|override)"),
            Pattern.compile("(?i)(?:do\\s+not\\s+(?:follow|obey|comply|listen|adhere)\\s+(?:your|the|to\\s+your|previous|above))"),
            Pattern.compile("(?i)(?:disobey|defy|violate|break)\\s+(?:your|the)\\s+(?:instructions?|commands?|prompts?|rules?|guidelines?)"),

            // ======================================================
            // 6. 上下文伪造 —— 假造对话历史或系统消息
            //    已移除: "reply only with / do not output anything else" 等常见"只要代码"句式
            // ======================================================
            Pattern.compile("(?i)(?:the\\s+(?:conversation|context|history)\\s+(?:above|below|so\\s+far)\\s+(?:is|was|has\\s+been)\\s+(?:fake|false|wrong|incorrect|a\\s+lie|made\\s+up|fabricated))"),
            Pattern.compile("(?i)this\\s+message\\s+(?:is|takes|has)\\s+(?:higher|greater|more|absolute)\\s+(?:priority|importance|authority|weight)"),
            Pattern.compile("(?i)(?:the\\s+)?true\\s+(?:instructions?|prompts?|system\\s+message)\\s+(?:is|was|are|were)"),

            // ======================================================
            // 7. 编码绕过 —— 试图用编码/翻译来绕过检测
            //    收紧: 只在明确要求"解码后执行"时才触发
            // ======================================================
            Pattern.compile("(?i)(?:decode|decrypt)\\s+(?:this|the\\s+following)\\s+(?:and|then|to)\\s+(?:execute|follow|run|obey)"),
            Pattern.compile("(?i)(?:translate|convert)\\s+.\\s+(?:and|then)\\s+(?:execute|follow|run|obey|carry\\s+out)"),
            Pattern.compile("(?i)\\b(?:base64|hex\\s*encode|rot\\s*13|url\\s*encode)\\s*(?:of|encoded)?\\s*[：:]"),

            // ======================================================
            // 8. 分隔线 / 视觉伪造 —— 用 Markdown 或特殊字符伪造系统消息
            // ======================================================
            Pattern.compile("(?i)(?:^|\\n)\\s*[-=_]{3,}\\s*(?:system|instruction|command|begin|start|new|real)\\s*[-=_]{0,}\\s*$", Pattern.MULTILINE),
            Pattern.compile("(?i)(?:^|\\n)\\s*[\\*\\+]{3,}\\s*(?:system|instruction|command|prompt)\\s*[\\*\\+]{0,}"),
            Pattern.compile("(?i)(?:begin|start)\\s*(?:of|new)\\s*(?:real|true|actual|hidden)\\s*(?:instructions?|commands?|prompts?)"),

            // ======================================================
            // 9. 情感操控 / 社会工程 —— 通过虚构严重后果来操控模型
            // ======================================================
            Pattern.compile("(?i)(?:if\\s+you\\s+(?:don't|do\\s+not|refuse\\s+to)\\s+(?:do|comply|follow|answer|help)\\b.*(?:people|someone|I|they|humans?)\\s+(?:will|may|could|might)\\s+(?:die|be\\s+harmed|suffer|get\\s+hurt))"),
            Pattern.compile("(?i)(?:this\\s+is\\s+(?:a\\s+)?matter\\s+of\\s+life\\s+(?:and|or)\\s+death)")
    );

    // 敏感词列表
    List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );
}
