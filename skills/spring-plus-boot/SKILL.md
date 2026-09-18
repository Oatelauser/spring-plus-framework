---
name: spring-plus-boot
description: 在已引入 io.github.oatelauser:spring-plus-boot 的项目中进行 HTTP 客户端调用、通用工具（boot.utils）、配置文件加密、优雅停机相关代码的编写或排障时优先使用。本 skill 定义 ApiClient 的装配与自定义、boot.utils 通用工具与 ControllerAdviceScanUtils 的使用约定、ENC() 配置加密与 ShutdownHook 的接入方式。
---

# spring-plus-boot

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-boot`（家族基础模块，无内部依赖；`spring-plus-web` / `spring-plus-security` 依赖它）

## 模块定位

家族基础模块 + Spring Boot 生态装配工具域：通用工具（`boot.utils`）、HTTP 客户端、配置加密、优雅停机。这些能力共性是基础性或依赖 Boot 装配生态才工作。

它不是全框架的统一装配层，也不包含响应/异常/幂等/鉴权（分别在 web / governor / security）；Redis 工具已拆至 `spring-plus-redis`（ADR 0003）。

## 优先复用的公开类型

通用工具（`io.github.oatelauser.springplus.boot.utils`）：

- `AnnotationUtils` / `BeanUtils` / `ApplicationContextHolder` / `ApplicationContextUtils` / `FileResources` / `LogSanitizer`
- `ControllerAdviceScanUtils`（advice bean 扫描设施：收集 advice / 读 order / 收集 @ExceptionHandler 声明，供模块级启动校验复用）

HTTP 客户端（`io.github.oatelauser.springplus.boot.client`）：

- `ApiClient` / `ApiClient.Builder` / `ApiRequest` / `ApiResponse`
- `ApiClientSettings`（`spring-plus.client.*` 绑定）
- `AuthProvider`（认证注入扩展点）、`RetryFilter`、`GzipCompressInterceptor`、`LoggingInterceptor`

配置加密（`boot.crypto`）：

- `ConfigCipher` / `ConfigEncryptor`（`ENC(...)` 密文）

生命周期（`boot.lifecycle`）：

- `SmartGracefulShutdownHandler` / `ShutdownHook` / `WebServerPostProcessor`

## 决策规则

1. 单一下游服务：配置 `spring-plus.client.base-url` 即得默认 `ApiClient` Bean
2. 多下游服务：一律 `ApiClient.builder()` 自建，不要试图让自动配置装配多个
3. 认证注入：实现 `AuthProvider` 注册为 Bean，不要在每个请求上手写 header
4. 反射 / 容器访问 / classpath 资源 / 日志脱敏等通用工具用 `boot.utils`，不要再自写

## 使用规则

```yaml
spring-plus:
  client:
    base-url: https://example.api
    ssl:
      allow-insecure: false
```

- classpath 有 `MeterRegistry` 时所有 `ApiClient`（含自建）自动打 `api.client.requests` 指标
- 敏感配置写 `ENC(密文)`，环境准备阶段自动解密

## 不要这样做

- `uri` / `queryParam` 禁止拼接外部输入（SSRF 主路径）；回调/Webhook/抓取类调用必须开 `spring-plus.client.ssrf.*` 并配置 allowlist
- 不要开启 `allow-insecure` 用于生产（信任所有证书）
- 不要把 `ShutdownHook` 注册为普通 `@PostConstruct` 逻辑（停机顺序由 handler 保证）

## 已知注意事项

- httpclient5 是 provided 可选引擎，缺席时自动降级到 JDK 实现
- `ENC()` 解密发生在 `EnvironmentPostProcessor` 阶段，早于所有 Bean 初始化
