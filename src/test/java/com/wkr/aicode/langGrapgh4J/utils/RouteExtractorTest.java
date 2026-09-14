package com.wkr.aicode.langGrapgh4J.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端路由提取测试：覆盖 app.js 挂载链、多级子路由、直接定义与噪声代码
 *
 * @author <a>百夜</a>
 */
class RouteExtractorTest {

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    @Test
    void extractRoutesFollowsMountChain(@TempDir Path backendDir) throws IOException {
        writeFile(backendDir.resolve("src/app.js"), """
                const express = require('express');
                const routes = require('./routes');
                const app = express();
                app.use(express.json());
                app.get('/api/health', (req, res) => res.json({ code: 0 }));
                app.use('/api', routes);
                app.use('/api', (req, res) => res.status(200).json({ code: 40400 }));
                app.use(express.static(path.join(__dirname, '../public')));
                app.get('*', (req, res) => res.sendFile('index.html'));
                app.listen(3000, '0.0.0.0');
                """);
        writeFile(backendDir.resolve("src/routes/index.js"), """
                const router = require('express').Router();
                const postRoutes = require('./posts');
                const commentRoutes = require('./comments');
                router.use('/posts', postRoutes);
                router.use('/comments', commentRoutes);
                module.exports = router;
                """);
        writeFile(backendDir.resolve("src/routes/posts.js"), """
                const router = require('express').Router();
                const controller = require('../controllers/postController');
                router.get('/', controller.list);
                router.get('/:id', controller.detail);
                router.post('/', controller.create);
                router.put('/:id', controller.update);
                router.delete('/:id', controller.remove);
                module.exports = router;
                """);
        writeFile(backendDir.resolve("src/routes/comments.js"), """
                const router = require('express').Router();
                const { list, create } = require('../controllers/commentController');
                router.get('/', list);
                router.post('/', create);
                module.exports = router;
                """);
        writeFile(backendDir.resolve("src/controllers/postController.js"), """
                const Post = require('../models/Post');
                exports.list = async (req, res) => {
                  const page = Number(req.query.page || 1);
                  const headers = req.get('Content-Type');
                  res.json({ code: 0, data: [], message: 'ok' });
                };
                exports.detail = async (req, res) => res.json({ code: 0, data: null, message: 'ok' });
                """);
        writeFile(backendDir.resolve("src/models/Post.js"), """
                const { DataTypes } = require('sequelize');
                const sequelize = require('../config/database');
                const Post = sequelize.define('Post', { title: DataTypes.STRING }, { tableName: 'posts' });
                module.exports = Post;
                """);

        Set<String> routes = RouteExtractor.extractRoutes(backendDir);

        assertTrue(routes.contains("GET /api/health"), "应提取到 app 直接定义的健康检查：" + routes);
        assertTrue(routes.contains("GET /api/posts"), "应还原 /api + /posts + /：" + routes);
        assertTrue(routes.contains("GET /api/posts/*"), "路径参数应归一化为通配符：" + routes);
        assertTrue(routes.contains("POST /api/posts"), routes.toString());
        assertTrue(routes.contains("PUT /api/posts/*"), routes.toString());
        assertTrue(routes.contains("DELETE /api/posts/*"), routes.toString());
        assertTrue(routes.contains("GET /api/comments"), routes.toString());
        assertTrue(routes.contains("POST /api/comments"), routes.toString());
        // SPA 兜底与中间件不应被误判为接口
        assertFalse(routes.contains("GET /*"), "app.get('*') 不应被识别为接口：" + routes);
        assertEquals(8, routes.size(), "路由数量应与定义一致：" + routes);
    }

    @Test
    void extractRoutesSupportsInlineRequireMount(@TempDir Path backendDir) throws IOException {
        writeFile(backendDir.resolve("app.js"), """
                const express = require('express');
                const app = express();
                app.use('/api/users', require('./routes/users'));
                app.listen(3000);
                """);
        writeFile(backendDir.resolve("routes/users.js"), """
                const router = require('express').Router();
                router.get('/', (req, res) => res.json({}));
                module.exports = router;
                """);

        Set<String> routes = RouteExtractor.extractRoutes(backendDir);

        assertTrue(routes.contains("GET /api/users"), routes.toString());
        assertEquals(1, routes.size(), routes.toString());
    }

    @Test
    void extractRoutesReturnsEmptyForMissingDir(@TempDir Path tempDir) {
        Set<String> routes = RouteExtractor.extractRoutes(tempDir.resolve("not-exist"));

        assertTrue(routes.isEmpty());
    }

    @Test
    void normalizePathHandlesAllForms() {
        assertEquals("/api/posts", ApiPathUtil.normalizePath("./api/posts/"));
        assertEquals("/api/posts", ApiPathUtil.normalizePath("/api//posts"));
        assertEquals("/api/posts/*", ApiPathUtil.normalizePath("/api/posts/:id"));
        assertEquals("/api/posts/*", ApiPathUtil.normalizePath("/api/posts/${id}"));
        assertEquals("/api/posts", ApiPathUtil.normalizePath("/api/posts?page=1&pageSize=10"));
        assertEquals("/api/posts/*", ApiPathUtil.normalizePath("../api/posts/{postId}"));
        // joinPath 只做拼接与斜杠整理，参数占位由 normalizePath 统一处理
        assertEquals("/api/posts/:id", ApiPathUtil.joinPath("/api", "/posts/:id/"));
    }
}
