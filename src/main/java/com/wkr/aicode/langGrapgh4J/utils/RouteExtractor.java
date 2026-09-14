package com.wkr.aicode.langGrapgh4J.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 后端 Express 路由提取器
 * 从 backend/ 源码中解析 app.get / router.get 等路由定义，并沿 require + use 挂载链还原完整路径，
 * 如 app.js 的 app.use('/api', routes) + routes/posts.js 的 router.get('/:id') → GET /api/posts/:id
 *
 * @author <a>百夜</a>
 */
@Slf4j
public final class RouteExtractor {

    /**
     * 路由定义：xxx.get('/path', ...)
     */
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
            "(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*['\"`](/[^'\"`]*)['\"`]",
            Pattern.CASE_INSENSITIVE);

    /**
     * 变量形式挂载：app.use('/api', routes)
     */
    private static final Pattern USE_PATTERN = Pattern.compile(
            "(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\.\\s*use\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*([A-Za-z_$][\\w$]*)");

    /**
     * 内联 require 挂载：app.use('/api', require('./routes'))
     */
    private static final Pattern INLINE_USE_PATTERN = Pattern.compile(
            "\\.\\s*use\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    /**
     * 模块引入：const routes = require('./routes')
     */
    private static final Pattern REQUIRE_PATTERN = Pattern.compile(
            "(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    /**
     * 任意形式的模块引入（含 app.use('/x', require('./x')) 的内联写法），仅用于判定「该文件已被引入」
     */
    private static final Pattern BARE_REQUIRE_PATTERN = Pattern.compile(
            "require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private RouteExtractor() {
    }

    /**
     * 提取后端全部路由
     *
     * @param backendDir 后端产物目录
     * @return 归一化后的 "METHOD /path" 集合
     */
    public static Set<String> extractRoutes(Path backendDir) {
        Map<Path, String> fileContents = readSourceFiles(backendDir);
        if (fileContents.isEmpty()) {
            log.warn("后端目录中未找到任何 js 源码，目录：{}", backendDir);
            return Set.of();
        }
        Map<Path, Map<String, Path>> requireMap = new HashMap<>();
        Set<Path> requiredFiles = new HashSet<>();
        for (Map.Entry<Path, String> entry : fileContents.entrySet()) {
            Path file = entry.getKey();
            String content = entry.getValue();
            Map<String, Path> requires = parseRequires(file, content);
            requireMap.put(file, requires);
            requiredFiles.addAll(requires.values());
            // 内联 require（app.use('/api', require('./routes'))）也要计入「已被引入」，
            // 否则被挂载的文件会被当成入口，路由前缀丢失
            Matcher bareMatcher = BARE_REQUIRE_PATTERN.matcher(content);
            while (bareMatcher.find()) {
                Path target = resolveModule(file, bareMatcher.group(1));
                if (target != null) {
                    requiredFiles.add(target);
                }
            }
        }
        Set<String> routes = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (Path file : fileContents.keySet()) {
            // 未被任何文件引入的视为入口（通常是 app.js），从入口沿挂载链向下解析
            if (!requiredFiles.contains(file)) {
                collectRoutes(file, "", fileContents, requireMap, routes, visited);
            }
        }
        log.info("后端路由提取完成，共 {} 条：{}", routes.size(), routes);
        return routes;
    }

    /**
     * 递归解析文件中的路由定义与子路由挂载
     *
     * @param file         当前文件
     * @param basePrefix   当前文件被挂载的前缀
     * @param fileContents 文件内容缓存
     * @param requireMap   文件内的模块引入关系
     * @param routes       结果集合
     * @param visited      已访问的「文件 + 前缀」组合，防循环挂载
     */
    private static void collectRoutes(Path file, String basePrefix, Map<Path, String> fileContents,
                                      Map<Path, Map<String, Path>> requireMap, Set<String> routes, Set<String> visited) {
        if (!visited.add(file + "|" + basePrefix)) {
            return;
        }
        String content = fileContents.get(file);
        if (content == null) {
            return;
        }
        Matcher routeMatcher = ROUTE_PATTERN.matcher(content);
        while (routeMatcher.find()) {
            String method = routeMatcher.group(2);
            String rawPath = routeMatcher.group(3);
            routes.add(ApiPathUtil.normalizeRoute(method, ApiPathUtil.joinPath(basePrefix, rawPath)));
        }
        Map<String, Path> requires = requireMap.getOrDefault(file, Map.of());
        Matcher useMatcher = USE_PATTERN.matcher(content);
        while (useMatcher.find()) {
            String prefix = useMatcher.group(2);
            Path child = requires.get(useMatcher.group(3));
            if (child == null || !fileContents.containsKey(child)) {
                continue;
            }
            collectRoutes(child, ApiPathUtil.joinPath(basePrefix, prefix), fileContents, requireMap, routes, visited);
        }
        Matcher inlineMatcher = INLINE_USE_PATTERN.matcher(content);
        while (inlineMatcher.find()) {
            String prefix = inlineMatcher.group(1);
            Path child = resolveModule(file, inlineMatcher.group(2));
            if (child == null || !fileContents.containsKey(child)) {
                continue;
            }
            collectRoutes(child, ApiPathUtil.joinPath(basePrefix, prefix), fileContents, requireMap, routes, visited);
        }
    }

    /**
     * 读取后端目录下的 js 源码（跳过 node_modules）
     */
    private static Map<Path, String> readSourceFiles(Path backendDir) {
        Map<Path, String> fileContents = new HashMap<>();
        if (backendDir == null || !Files.isDirectory(backendDir)) {
            return fileContents;
        }
        try (Stream<Path> stream = Files.walk(backendDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().replace('\\', '/').contains("node_modules/"))
                    .filter(RouteExtractor::isSourceFile)
                    .forEach(path -> {
                        try {
                            fileContents.put(path.normalize(), Files.readString(path, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            log.warn("读取后端源码失败，文件：{}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("遍历后端目录失败，目录：{}", backendDir, e);
        }
        return fileContents;
    }

    /**
     * 解析文件内的模块引入关系：变量名 → 被引入文件
     */
    private static Map<String, Path> parseRequires(Path file, String content) {
        Map<String, Path> requires = new HashMap<>();
        Matcher matcher = REQUIRE_PATTERN.matcher(content);
        while (matcher.find()) {
            Path target = resolveModule(file, matcher.group(2));
            if (target != null) {
                requires.put(matcher.group(1), target);
            }
        }
        return requires;
    }

    /**
     * 解析 require 路径：只处理相对路径，依次尝试 原路径 / 加 .js / 目录下 index.js
     */
    private static Path resolveModule(Path fromFile, String spec) {
        if (spec == null || !(spec.startsWith("./") || spec.startsWith("../"))) {
            return null;
        }
        Path base = fromFile.getParent().resolve(spec).normalize();
        if (Files.isRegularFile(base)) {
            return base;
        }
        Path withExt = base.resolveSibling(base.getFileName() + ".js");
        if (Files.isRegularFile(withExt)) {
            return withExt;
        }
        Path indexFile = base.resolve("index.js");
        if (Files.isRegularFile(indexFile)) {
            return indexFile.normalize();
        }
        return null;
    }

    private static boolean isSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs");
    }
}
