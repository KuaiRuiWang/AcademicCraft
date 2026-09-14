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
 * 前端接口调用提取测试：覆盖请求封装短路径、api 字面量、路径参数与噪声代码
 *
 * @author <a>百夜</a>
 */
class ApiCallExtractorTest {

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    @Test
    void extractApiCallsFromWrapperAndLiterals(@TempDir Path frontendDir) throws IOException {
        writeFile(frontendDir.resolve("src/utils/request.js"), """
                const BASE_URL = './api'

                async function request(path, options = {}) {
                  const response = await fetch(BASE_URL + path, {
                    headers: { 'Content-Type': 'application/json' },
                    ...options
                  })
                  const result = await response.json()
                  if (result.code !== 0) {
                    throw new Error(result.message || '请求失败')
                  }
                  return result.data
                }

                export default request
                """);
        writeFile(frontendDir.resolve("src/pages/PostList.vue"), """
                <script setup>
                import { ref, onMounted } from 'vue'
                import request from '@/utils/request'

                const posts = ref([])
                onMounted(async () => {
                  const data = await request('/posts?page=1&pageSize=10')
                  posts.value = data.list
                })
                </script>
                <template><div class="post-list"><img src="https://picsum.photos/200" /></div></template>
                """);
        writeFile(frontendDir.resolve("src/pages/PostDetail.vue"), """
                <script setup>
                import { ref, onMounted } from 'vue'
                import { useRoute } from 'vue-router'
                import request from '@/utils/request'

                const route = useRoute()
                const post = ref(null)
                onMounted(async () => {
                  post.value = await request(`/posts/${route.params.id}`)
                })
                </script>
                """);
        writeFile(frontendDir.resolve("src/pages/CommentForm.vue"), """
                <script setup>
                import { ref } from 'vue'

                const content = ref('')
                async function submit() {
                  const response = await fetch('./api/comments', {
                    method: 'POST',
                    body: JSON.stringify({ content: content.value })
                  })
                  const result = await response.json()
                  if (result.code === 0) {
                    await fetch(`/api/comments/${result.data.id}`, { method: 'DELETE' })
                  }
                }
                </script>
                """);
        writeFile(frontendDir.resolve("src/router/index.js"), """
                import { createRouter, createWebHashHistory } from 'vue-router'

                const router = createRouter({
                  history: createWebHashHistory(),
                  routes: [
                    { path: '/', component: () => import('@/pages/PostList.vue') },
                    { path: '/posts/:id', component: () => import('@/pages/PostDetail.vue') }
                  ]
                })

                export default router
                """);
        writeFile(frontendDir.resolve("vite.config.js"), """
                import { defineConfig } from 'vite'
                import vue from '@vitejs/plugin-vue'

                export default defineConfig({
                  base: './',
                  plugins: [vue()],
                  server: {
                    proxy: { '/api': 'http://localhost:3000' }
                  }
                })
                """);

        Set<String> calls = ApiCallExtractor.extractApiCalls(frontendDir);

        assertTrue(calls.contains("/api/posts"), "封装函数短路径应补全为 /api 前缀：" + calls);
        assertTrue(calls.contains("/api/posts/*"), "模板字面量路径参数应归一化：" + calls);
        assertTrue(calls.contains("/api/comments"), "fetch('./api/comments') 应被提取：" + calls);
        assertTrue(calls.contains("/api/comments/*"), calls.toString());
        // 路由配置、vite 代理、常量定义不应被识别为接口调用
        assertFalse(calls.contains("/posts/*"), "前端路由配置不应被识别为接口调用：" + calls);
        assertFalse(calls.contains("/api"), "BASE_URL 常量不应被识别为接口调用：" + calls);
        assertEquals(4, calls.size(), "调用数量应与实际一致：" + calls);
    }

    @Test
    void extractApiCallsReturnsEmptyForMissingDir(@TempDir Path tempDir) {
        Set<String> calls = ApiCallExtractor.extractApiCalls(tempDir.resolve("not-exist"));

        assertTrue(calls.isEmpty());
    }
}
