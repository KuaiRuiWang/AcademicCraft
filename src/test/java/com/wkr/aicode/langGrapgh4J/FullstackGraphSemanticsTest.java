package com.wkr.aicode.langGrapgh4J;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LangGraph4j 并行语义探针：确认「双分支汇聚是否等待全部完成」与「汇聚节点回环后可再次触发」，
 * 用于确定 T-07 契约校验节点的图拓扑是否可行（不依赖 Spring 与真实模型）
 *
 * @author <a>百夜</a>
 */
class FullstackGraphSemanticsTest {

    private final Queue<String> events = new ConcurrentLinkedQueue<>();

    private void record(String event) {
        events.add(event);
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorService newPool() {
        return ExecutorBuilder.create()
                .setCorePoolSize(4)
                .setMaxPoolSize(8)
                .setWorkQueue(new LinkedBlockingQueue<>(100))
                .setThreadFactory(ThreadFactoryBuilder.create()
                        .setNamePrefix("Semantics-Probe-")
                        .setDaemon(true)
                        .build())
                .build();
    }

    /**
     * 汇聚节点必须等到两个并行分支都结束后仅执行一次
     */
    @Test
    @Timeout(30)
    void fanInWaitsForAllParallelBranches() throws Exception {
        ExecutorService pool = newPool();
        try {
            CompiledGraph<MessagesState<String>> workflow = new MessagesStateGraph<String>()
                    .addNode("fork", node_async(state -> {
                        record("fork");
                        return Map.of();
                    }))
                    .addNode("left", node_async(state -> {
                        sleepQuietly(300);
                        record("left_end");
                        return Map.of();
                    }))
                    .addNode("right", node_async(state -> {
                        sleepQuietly(50);
                        record("right_end");
                        return Map.of();
                    }))
                    .addNode("join", node_async(state -> {
                        record("join");
                        return Map.of();
                    }))
                    .addEdge(START, "fork")
                    .addEdge("fork", "left")
                    .addEdge("fork", "right")
                    .addEdge("left", "join")
                    .addEdge("right", "join")
                    .addEdge("join", END)
                    .compile();
            RunnableConfig config = RunnableConfig.builder()
                    .addParallelNodeExecutor("fork", pool)
                    .build();
            for (NodeOutput<MessagesState<String>> ignored : workflow.stream(Map.of(), config)) {
                // 仅驱动执行
            }
        } finally {
            pool.shutdown();
        }
        List<String> eventList = List.copyOf(events);
        assertEquals(List.of("fork", "right_end", "left_end", "join"), eventList,
                "join 必须等待 left/right 都结束且只执行一次，实际事件序列：" + eventList);
    }

    /**
     * 汇聚节点在回环后（仅上游单分支重跑）能否被再次触发，还是会等待另一分支而卡死
     */
    @Test
    @Timeout(30)
    void fanInNodeCanReTriggerAfterLoopBack() throws Exception {
        AtomicInteger checkRuns = new AtomicInteger();
        ExecutorService pool = newPool();
        try {
            CompiledGraph<MessagesState<String>> workflow = new MessagesStateGraph<String>()
                    .addNode("fork", node_async(state -> {
                        record("fork");
                        return Map.of();
                    }))
                    .addNode("left", node_async(state -> {
                        sleepQuietly(100);
                        record("left_end");
                        return Map.of();
                    }))
                    .addNode("right", node_async(state -> {
                        sleepQuietly(50);
                        record("right_end");
                        return Map.of();
                    }))
                    .addNode("check", node_async(state -> {
                        int round = checkRuns.incrementAndGet();
                        record("check_" + round);
                        return Map.of();
                    }))
                    .addEdge(START, "fork")
                    .addEdge("fork", "left")
                    .addEdge("fork", "right")
                    .addEdge("left", "check")
                    .addEdge("right", "check")
                    // 第一轮回到 left 修复，第二轮结束
                    .addConditionalEdges("check",
                            edge_async(state -> checkRuns.get() < 2 ? "retry" : "done"),
                            Map.of("retry", "left", "done", END))
                    .compile();
            RunnableConfig config = RunnableConfig.builder()
                    .addParallelNodeExecutor("fork", pool)
                    .build();
            for (NodeOutput<MessagesState<String>> ignored : workflow.stream(Map.of(), config)) {
                // 仅驱动执行
            }
        } finally {
            pool.shutdown();
        }
        List<String> eventList = List.copyOf(events);
        assertEquals(2, checkRuns.get(),
                "汇聚节点应在回环后再次执行（共 2 次），实际事件序列：" + eventList);
    }
}
