# spring-boot-web-example

spring-plus-framework 的 Spring Boot 接入示例：完整展示统一响应、全局异常三协议（JSON/SSE/NDJSON）、流式响应四件套、运行时断言、幂等防重的标准用法。**业务方接入时可参考本模块作为对照样例。**

## 运行

```bash
cd examples/spring-boot-web-example
mvn spring-boot:run
# http://localhost:8080
```

## 端点总表

`TestV2Controller`（`/v2-test`，21 个端点）：

| 端点 | 演示内容 |
|---|---|
| `POST /v2-test/json/validate` | JSON `@Valid` 失败 → details.violations |
| `POST /v2-test/sse/validate-handshake` | SSE 握手期校验失败 → app-error 事件 |
| `GET /v2-test/param-validate` | 非 body 参数约束（方法级校验） |
| `GET /v2-test/exception-class-annotation` | 异常类上贴注解 + 占位符 `{exception}` |
| `GET /v2-test/sse/b-tier` | SSE 流内异常 + completeWithError |
| `GET /v2-test/sse/c-tier` | SSE 连接封装零样板（SseConnection.execute） |
| `POST /v2-test/wechat-style` | 自定义 bodyCustomizer（微信 errcode/errmsg 风格） |
| `GET /v2-test/rate-limit` · `/rate-limit2` | 业务自定义 ExceptionMapper（独立 Mapper Bean / 异常自带映射） |
| `GET /v2-test/log/always` · `/log/never` | 日志堆栈策略 ALWAYS / NEVER |
| `POST /v2-test/protocol-conflict` | JSON/SSE 双协议注解（启动 warn + 协议过滤） |
| `GET /v2-test/priority` | 注解 P0 覆盖 handler 默认 descriptor |
| `GET /v2-test/json/not-found-status` | 真实 HTTP 404 状态码 |
| `GET /v2-test/service-exception/with-stack` · `/with-signal` | withStack() / signal() 链式开关 |
| `GET /v2-test/placeholder` | 消息模板占位符 `{exceptionClass}` |
| `GET /v2-test/chunk` · `/ndjson` · `/ndjson/error` · `/ndjson/custom-error` | 流式四件套（Chunk 按块 / NDJSON 逐行 / 流内异常补错误行 / 自定义错误行） |
| `GET /v2-test/download` | 文件下载（中文文件名 RFC 5987） |

`IdempotentController`：`@Idempotent` / `@RepeatSubmit` 幂等防重演示。

`SecureController`（`/secure`，声明式鉴权演示，认证走 HTTP Basic）：

| 端点 | 演示 | 验证 |
|---|---|---|
| `GET /secure/admin` | `@RequiresAdminRole` 超管短路 | `curl -u admin:admin123 .../secure/admin` → 00000；`-u user:user123` → 403；无凭据 → 401 |
| `GET /secure/profile` | `@RequiresRole("USER")` | `curl -u user:user123 ...` → 00000 |

安全基线：`SecurityExampleConfig`——默认 `denyAll` + 显式白名单（`/v2-test/**`、`/idempotent-test/**` 免认证，`/secure/**` 需认证），是 V08 红线的对照样例。

## 辅助类索引

| 类 | 演示点 |
|---|---|
| `EchoCmd` | 校验失败的 DTO 载体 |
| `RateLimitException` + `RateLimitExceptionMapper` | 独立 Mapper Bean 路径 |
| `RateLimit2Exception` | 异常类自身实现 `ExceptionMapper`（免独立 Bean） |
| `UsernameDuplicatedException` | 异常类上贴 `@JsonExceptionResponse` |
| `ResourceNotFoundException` | 真实 HTTP 状态码映射 |
| `WechatStyleOutput` | 自定义响应渲染（`output` 属性） |

## application.yml

示例全量列出了 spring-plus-framework 的可配置项（jackson 时间格式 / long-to-string / error-response / validation / client），并注释标注了"官方键"与"框架扩展键"（⚑）的区别。
