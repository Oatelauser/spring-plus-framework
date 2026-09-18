# 优雅停机与启动序

包：`io.github.oatelauser.springplus.boot.lifecycle`。

## 三个角色

| 类型 | 职责 | 触发时机 |
|---|---|---|
| `StartupProcess`（接口） | 业务启动钩子（预热缓存、订阅就绪等） | `WebServerPostProcessor` 在 **Web 容器就绪前**触发容器中全部实现 |
| `ShutdownHook`（接口，extends Ordered） | 业务停机钩子，`void shutdown() throws Exception` | `SmartGracefulShutdownHandler` 停机时**逆序**执行（先摘流量后关资源） |
| `SmartGracefulShutdownHandler` | 停机编排 | Spring 容器 stop 阶段（SmartLifecycle 回调） |

## 用法

```java
@Component
public class CacheWarmup implements StartupProcess {
    @Override
    public void run() {          // Web 容器就绪前执行；抛异常阻断启动
        dictionaryCache.reloadAll();
    }
}

@Component
public class MqConsumerShutdown implements ShutdownHook {
    @Override
    public int getOrder() { return 100; }      // 数值大 = 先关（逆序：后启动的先停）

    @Override
    public void shutdown() {
        consumer.pause();      // 先停止拉取，等 in-flight 消息处理完再关连接
    }
}
```

## 顺序语义

- 启动：`WebServerPostProcessor` 保证业务 `StartupProcess` 跑完才放行 Web 容器——端口未开就不会接到流量
- 停机：`SmartGracefulShutdownHandler` 逆序执行 `ShutdownHook`——先摘流量（网关注销/暂停消费），后关资源（连接池/线程池）；依赖 Spring 容器的销毁回调兜底

## 红线与陷阱

- **不要**把 `ShutdownHook` 逻辑注册成 `@PostConstruct` / `Runtime.addShutdownHook`——绕过 handler 就失去逆序保证，资源先于流量关闭会产生半失败请求
- `StartupProcess` 抛异常会阻断启动（fail-fast）：可重试的预热放后台线程，不可失败的才同步抛
- 需要精细控制停机超时/宽限期时用 Spring Boot 官方 `server.shutdown: graceful` 配合本机制，不重造轮子
