# spring-plus-boot-starter

家族基础模块 + Spring Boot 生态能力拓展：通用工具（反射 / 容器访问 / 资源 / 日志脱敏 / TLS）、HTTP 客户端、配置文件加密、优雅停机。

> 1.1.0 起 Redis 工具拆出为独立模块 [`spring-plus-redis-starter`](../spring-plus-redis-starter/README.md)（ADR 0003）。

## 能力清单

| 能力 | 入口 |
|---|---|
| 通用工具 | `boot.utils`：`AnnotationUtils` / `BeanUtils` / `ApplicationContextHolder` / `ApplicationContextUtils` / `FileResources` / `LogSanitizer` / `InsecureTlsHelper`（@Deprecated） |
| HTTP 客户端 | `ApiClient` / `ApiClient.builder()`：拦截器链、重试、GZIP 压缩、认证注入、Micrometer 打点 |
| 客户端适配 | `ClientHttpRequestFactoryProvider`（httpclient5 可选引擎）、`AuthProvider` 认证扩展点 |
| 配置加密 | `ConfigCipher` / `ConfigEncryptor` / `EncryptedPropertyEnvironmentPostProcessor`（`ENC(...)` 密文自动解密） |
| 优雅停机 | `SmartGracefulShutdownHandler` / `ShutdownHook` / `WebServerPostProcessor` |
| 启动期 Bean 扫描设施 | `HandleBeanPostProcessor` + `HandlerBean` SPI（单例就绪后扫描全部 Bean 定义：BeanKind 标注单例/懒加载/每次创建形态，lazy/prototype 以元数据参与、就绪单例附实例，绝不强制实例化；fail-closed。security 的注解启动校验基于此） |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-boot-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

无内部依赖（家族基础模块）；`spring-plus-web-starter` / `spring-plus-security-starter` 依赖本模块（ADR 0003 依赖倒置）。

## HTTP 客户端

```yaml
spring-plus:
  client:
    base-url: https://example.api    # 配置后自动装配默认 ApiClient（@ConditionalOnMissingBean）
    ssl:
      allow-insecure: false          # 信任自签证书（显式确认才启用）
```

```java
// 注入默认客户端
private final ApiClient apiClient;
ApiResponse<String> resp = apiClient.execute(ApiRequest.get("/users/1").build());

// 多客户端场景：builder 自行装配
ApiClient github = ApiClient.builder()
        .properties(ApiClientSettings.of("https://api.github.com"))
        .clientHttpRequestFactoryProvider(factoryProvider)
        .build();
```

- classpath 存在 `MeterRegistry` 时自动为**所有** ApiClient Bean（含业务自建）注入 `api.client.requests` 指标
- httpclient5 为 provided 可选引擎，缺席时自动降级

## 配置加密

application.yml 中的敏感配置以 `ENC(密文)` 书写，`EncryptedPropertyEnvironmentPostProcessor` 在环境准备阶段解密（注册于 `spring.factories` 的 `EnvironmentPostProcessor`）。密钥管理见 `ConfigCipher`。

## 优雅停机

`WebServerPostProcessor` 在 Web 容器接受请求前触发容器中所有 `StartupProcess`（接口在本模块 `boot.lifecycle` 包，方法 `start()`）；`SmartGracefulShutdownHandler`（SmartLifecycle，phase 低于 Web 容器）在 Web 容器优雅停机**之后**按 `Ordered` 升序执行 `ShutdownHook`——摘流量由容器 phase 机制先完成，业务 hook 只管关资源，单个 hook 异常记日志不中断。

## SSRF 防护（1.1.0+）

外部输入参与构造 `uri` 的调用（回调、Webhook、抓取）建议开启：

```yaml
spring-plus:
  client:
    base-url: https://api.example.com
    ssrf:
      enabled: true                    # 默认 false（兼容）
      allowed-hosts: [api.example.com] # 后缀匹配；为空仅私网拦截
      deny-private-network: true       # 环回/私网/链路本地/保留地址，解析失败 fail-closed
```

- 校验挂在过滤器链首：绝对 URI 覆盖 baseUrl 的注入路径与相对路径（校验 baseUrl 主机）都覆盖
- 已知局限：DNS rebinding 未做 IP pinning（浅防护）；跨主机重定向凭据剥离仅 HTTP_COMPONENTS 引擎支持（`strip-credentials-on-cross-host-redirect` 默认 true），启用 SSRF 的部署建议该引擎
- **红线**：`uri`/`queryParam` 禁止拼接外部输入；外部 URL 必须经 allowlist 校验

## 已知注意事项

- 不配置 `spring-plus.client.base-url` 时不装配默认 ApiClient，多客户端场景一律 `builder()` 自建
- `allow-insecure` 默认关闭，开启即信任所有证书，仅限内网调试
- 本模块是家族基础模块 + "依赖 Boot 装配生态的工具域"，不是全框架的统一装配层
