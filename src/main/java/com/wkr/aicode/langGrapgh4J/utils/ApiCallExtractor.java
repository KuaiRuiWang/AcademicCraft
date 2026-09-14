package com.wkr.aicode.langGrapgh4J.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 前端接口调用提取器
 * 前端约定以 './api' 为基地址（BASE_URL），页面通过 request('/posts') 形式调用，
 * 因此既提取含 api/ 的字面量，也提取请求封装函数的相对路径参数，统一还原为 /api/xxx
 *
 * @author <a>百夜</a>
 */
@Slf4j
public final class ApiCallExtractor {

    /**
     * 含 api/ 的字面量：'./api/posts'、"/api/posts"、`/api/posts/${id}`
     */
    private static final Pattern API_LITERAL_PATTERN = Pattern.compile(
            "(['\"`])((?:\\.{1,2}/|/)?api/[^'\"`\\s)]*)\\1");

    /**
     * 请求封装调用：request('/posts')、request(`/posts/${id}`)、http.get('/posts')
     */
    private static final Pattern WRAPPER_CALL_PATTERN = Pattern.compile(
            "(?<![\\w$.])(?:request|http|api|service)\\s*(?:\\.\\s*(?:get|post|put|delete|patch))?\\s*\\(\\s*(['\"`])(/[^'\"`]*)\\1",
            Pattern.CASE_INSENSITIVE);

    private ApiCallExtractor() {
    }

    /**
     * 提取前端全部接口调用路径
     *
     * @param frontendDir 前端产物目录
     * @return 归一化后的路径集合（已补全 /api 前缀）
     */
    public static Set<String> extractApiCalls(Path frontendDir) {
        Set<String> calls = new LinkedHashSet<>();
        if (frontendDir == null || !Files.isDirectory(frontendDir)) {
            return calls;
        }
        try (Stream<Path> stream = Files.walk(frontendDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(ApiCallExtractor::isSourceFile)
                    .forEach(path -> collectFromFile(path, calls));
        } catch (IOException e) {
            log.warn("遍历前端目录失败，目录：{}", frontendDir, e);
        }
        log.info("前端接口调用提取完成，共 {} 个：{}", calls.size(), calls);
        return calls;
    }

    private static void collectFromFile(Path file, Set<String> calls) {
        String normalizedFileName = file.toString().replace('\\', '/');
        if (normalizedFileName.contains("node_modules/") || normalizedFileName.contains("/dist/")) {
            return;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取前端源码失败，文件：{}", file, e);
            return;
        }
        Matcher literalMatcher = API_LITERAL_PATTERN.matcher(content);
        while (literalMatcher.find()) {
            calls.add(ApiPathUtil.normalizePath(literalMatcher.group(2)));
        }
        Matcher wrapperMatcher = WRAPPER_CALL_PATTERN.matcher(content);
        while (wrapperMatcher.find()) {
            String path = wrapperMatcher.group(2);
            // 封装函数基地址为 './api'，短路径需补全为 /api/xxx
            if (!path.startsWith("/api/") && !path.equals("/api")) {
                path = "/api" + path;
            }
            calls.add(ApiPathUtil.normalizePath(path));
        }
    }

    private static boolean isSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".mjs")
                || name.endsWith(".vue") || name.endsWith(".html");
    }
}
