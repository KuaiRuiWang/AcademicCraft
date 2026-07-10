package com.wkr.aicode.config;

import com.wkr.aicode.monitor.MonitorContext;
import com.wkr.aicode.monitor.MonitorContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * Reactor 线程上下文传播配置。
 * <p>
 * 在响应式/WebFlux 架构下，{@code ThreadLocal} 无法自动在异步线程间传递。
 * 通过 {@link Hooks#onEachOperator} 机制，在每个操作符组装时捕获当前线程的
 * {@link MonitorContext} 快照，并在操作符执行时恢复，
 * 从而保证监控上下文在 Reactor 链的所有操作符中可用。
 * </p>
 */
@Configuration
@Slf4j
public class ReactorThreadLocalConfig {

    private static final String HOOK_KEY = MonitorContextHolder.class.getName();

    @PostConstruct
    public void setupThreadLocalPropagation() {
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((scannable, subscriber) -> {
            // 在操作符组装时（此时处于请求线程），捕获 ThreadLocal 快照
            MonitorContext contextSnapshot = MonitorContextHolder.getContext();
            // 如果当前线程没有设置上下文，则不包装（避免无意义的包装开销）
            if (contextSnapshot == null) {
                return subscriber;
            }
            return new ThreadLocalPropagatingSubscriber(subscriber, contextSnapshot);
        }));
        log.info("Reactor ThreadLocal 传播已配置，Hook key: {}", HOOK_KEY);
    }

    /**
     * 包装 {@link CoreSubscriber}，在每次信号传递前恢复 ThreadLocal 快照，
     * 信号传递完成后恢复原值。
     */
    private static class ThreadLocalPropagatingSubscriber implements CoreSubscriber<Object> {

        private final CoreSubscriber<? super Object> delegate;
        private final MonitorContext contextSnapshot;

        ThreadLocalPropagatingSubscriber(CoreSubscriber<? super Object> delegate,
                                         MonitorContext contextSnapshot) {
            this.delegate = delegate;
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public void onSubscribe(Subscription s) {
            MonitorContext previous = MonitorContextHolder.getContext();
            MonitorContextHolder.setContext(contextSnapshot);
            try {
                delegate.onSubscribe(s);
            } finally {
                MonitorContextHolder.setContext(previous);
            }
        }

        @Override
        public void onNext(Object o) {
            MonitorContext previous = MonitorContextHolder.getContext();
            MonitorContextHolder.setContext(contextSnapshot);
            try {
                delegate.onNext(o);
            } finally {
                MonitorContextHolder.setContext(previous);
            }
        }

        @Override
        public void onError(Throwable t) {
            MonitorContext previous = MonitorContextHolder.getContext();
            MonitorContextHolder.setContext(contextSnapshot);
            try {
                delegate.onError(t);
            } finally {
                MonitorContextHolder.setContext(previous);
            }
        }

        @Override
        public void onComplete() {
            MonitorContext previous = MonitorContextHolder.getContext();
            MonitorContextHolder.setContext(contextSnapshot);
            try {
                delegate.onComplete();
            } finally {
                MonitorContextHolder.setContext(previous);
            }
        }

        @Override
        public Context currentContext() {
            return delegate.currentContext();
        }
    }
}
