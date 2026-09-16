# spring-plus-boot

Spring Boot 生态能力拓展：依赖 Boot 装配生态才工作的工具集——HTTP 客户端、Redis 工具、配置文件加密、优雅停机。

## 能力清单

| 能力 | 入口 |
|---|---|
| HTTP 客户端 | `ApiClient` / `ApiClient.builder()`：拦截器链、重试、GZIP 压缩、认证注入、Micrometer 打点 |
| 客户端适配 | `ClientHttpRequestFactoryProvider`（httpclient5 可选引擎）、`AuthProvider` 认证扩展点 |
| Redis 工具 | `CacheUtils` / `RedisStringOperation`（Lua 脚本原子操作）/ `KeyValue` |
| 配置加密 | `ConfigCipher` / `ConfigEncryptor` / `EncryptedPropertyEnvironmentPostProcessor`（`ENC(...)` 密文自动解密） |
| 优雅停机 | `SmartGracefulShutdownHandler` / `ShutdownHook` / `WebServerPostProcessor` |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-boot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖 `spring-plus-web`（传递引入）。

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

## Redis 工具

```java
// RedisStringOperation 由自动配置注入（容器存在 StringRedisTemplate 时生效）
redisStringOperation.incrementAndExpire("counter:login:" + userId, Duration.ofMinutes(30));
```

附带 3 个 Lua 脚本（`bdel` / `bget` / `expire_increment`）保证原子性。Redis 依赖为 provided——纯 Web 应用不受影响。

## 配置加密

application.yml 中的敏感配置以 `ENC(密文)` 书写，`EncryptedPropertyEnvironmentPostProcessor` 在环境准备阶段解密（注册于 `spring.factories` 的 `EnvironmentPostProcessor`）。密钥管理见 `ConfigCipher`。

## 优雅停机

`WebServerPostProcessor` 在 Web 容器就绪前触发容器中所有 `StartupProcess`（接口在本模块 `boot.lifecycle` 包）；`SmartGracefulShutdownHandler` 在停机时逆序执行 `ShutdownHook`，先摘流量后关资源。

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
- 本模块定位是"依赖 Boot 装配生态的工具域"，不要把它当作全框架的统一装配层
