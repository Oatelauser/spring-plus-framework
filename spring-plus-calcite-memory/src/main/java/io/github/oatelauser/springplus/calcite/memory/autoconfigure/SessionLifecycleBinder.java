package io.github.oatelauser.springplus.calcite.memory.autoconfigure;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.engine.SessionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 请求级 {@link MemoryQuerySession} 生命周期绑定：仅当 spring-web 在类路径时激活。
 *
 * <p>Session 作用域为 request，{@link MemoryQuerySession} 实现 {@link AutoCloseable}，
 * Spring 在请求销毁（{@code ServletRequestAttributes.requestCompleted()}）时自动调用 {@code close()}，
 * 释放底层 CalciteConnection，无需业务方手工关闭。</p>
 *
 * <p>不使用作用域代理（proxyMode=NO）：请求级 Session 由处理线程在请求上下文内直接获取使用，
 * 避免被误注入到 singleton 造成作用域歧义。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RequestContextHolder.class)
public class SessionLifecycleBinder {

    @Bean
    @Scope(scopeName = WebApplicationContext.SCOPE_REQUEST)
    @ConditionalOnMissingBean(MemoryQuerySession.class)
    public MemoryQuerySession memoryQuerySession(MemoryQueryEngine engine, SessionConfig sessionConfig) {
        return engine.openSession(sessionConfig);
    }
}
