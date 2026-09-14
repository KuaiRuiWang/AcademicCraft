package com.wkr.aicode.langGrapgh4J.utils;

/**
 * API 路径归一化工具
 * 契约、后端路由、前端调用三方比对前必须归一化到同一形态，
 * 否则 `./api/posts`、`/api/posts/`、`/api/posts/:id`、`/api/posts/${id}` 会被误判为不同路径
 *
 * @author <a>百夜</a>
 */
public final class ApiPathUtil {

    private ApiPathUtil() {
    }

    /**
     * 归一化路径：去查询串/哈希、去相对路径前缀、合并重复斜杠、去末尾斜杠、参数占位统一为 *
     */
    public static String normalizePath(String rawPath) {
        if (rawPath == null) {
            return "/";
        }
        String path = rawPath.trim();
        int cutIndex = path.indexOf('?');
        if (cutIndex >= 0) {
            path = path.substring(0, cutIndex);
        }
        cutIndex = path.indexOf('#');
        if (cutIndex >= 0) {
            path = path.substring(0, cutIndex);
        }
        while (path.startsWith("./") || path.startsWith("../")) {
            path = path.startsWith("./") ? path.substring(2) : path.substring(3);
        }
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        path = path.replaceAll("/{2,}", "/");
        path = path.replaceAll(":\\w+", "*");
        path = path.replaceAll("\\$\\{[^}]*}", "*");
        path = path.replaceAll("\\{[^}]*}", "*");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 拼接挂载前缀与子路径，如 /api + /posts/:id → /api/posts/:id
     */
    public static String joinPath(String basePath, String subPath) {
        if (basePath == null || basePath.isEmpty()) {
            return normalizePath(subPath);
        }
        if (subPath == null || subPath.isEmpty()) {
            return normalizePath(basePath);
        }
        String joined = ("/" + basePath + "/" + subPath).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined;
    }

    /**
     * 归一化为 "METHOD /path" 形式，便于集合比对
     */
    public static String normalizeRoute(String method, String path) {
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase();
        return normalizedMethod + " " + normalizePath(path);
    }
}
