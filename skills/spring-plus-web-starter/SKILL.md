---
name: spring-plus-web-starter
description: spring-plus-framework 的 Web 层能力使用约定（坐标 io.github.oatelauser:spring-plus-web-starter）。覆盖：SimpleResponse/PageResponse 统一响应与分页、00000/A0/B0/C0 状态码体系、ServiceException 与 AssertUtils 业务断言、@JsonExceptionResponse/@SseExceptionResponse/@NdjsonExceptionResponse 三协议全局异常处理、模块级 @RestControllerAdvice 与 @Order 段位、HttpWriterFactory 流式响应（SSE/NDJSON/Chunk/文件下载）、@Phone/@EnumValue/@ClassValidator 校验注解、@RecordHttp 请求追踪、JsonUtils。编写或修改 Controller/响应体/错误码/异常映射，排查接口返回结构、参数校验失败、SSE/NDJSON 流式输出问题时使用。鉴权注解在 spring-plus-security-starter；幂等防重在 spring-plus-governor-starter；HTTP 客户端/配置加密在 spring-plus-boot-starter。
---

# spring-plus-web-starter

这个 skill 直接给 AI 使用。本文件是导航与全模块规则；各能力域的 API 细节、可抄示例与陷阱在 `references/`，按任务加载。

## 按需加载参考文档

| 任务涉及 | 加载 |
|---|---|
| 统一响应、分页、状态码/错误码设计、业务枚举实现 ServerStatus | [references/response.md](references/response.md) |
| 异常体系：ServiceException、异常映射注解、ExceptionMapper、模块级 advice、错误渲染定制、`spring-plus.web.error-response.*` | [references/exception-handling.md](references/exception-handling.md) |
| 流式响应：SSE 三档、NDJSON/Chunk/文件下载写入器、流内异常补写、@RecordHttp 请求追踪 | [references/streaming.md](references/streaming.md) |
| 参数校验：@Phone/@EnumValue/@ListValues/集合元素/类级 @ClassValidator | [references/validation.md](references/validation.md) |
| JsonUtils、AssertUtils 全部重载、spring.jackson.* 扩展键 | [references/json-and-assert.md](references/json-and-assert.md) |

只做小改动且明确知道规则时可不加载；涉及具体 API 用法/配置键/多协议输出时必须加载对应文件。

## 能力归属（防找错模块）

本模块：统一响应、状态码、分页、全局异常（JSON/SSE/NDJSON）、流式写入器、校验注解、请求追踪、AssertUtils、JsonUtils。

不在此模块：

- HTTP 客户端 / 配置加密 / 优雅停机 / `ApplicationContextHolder` 等通用工具 → `spring-plus-boot-starter`（`boot.utils`，ADR 0003 迁出）
- 幂等 / 防重 → `spring-plus-governor-starter`
- 鉴权注解 / @Principal → `spring-plus-security-starter`
- Redis 工具 → `spring-plus-redis-starter`

## 核心决策规则（几乎所有任务适用）

1. Controller 返回值一律 `SimpleResponse<T>` / `PageResponse<T>`，无数据用 `SimpleResponse<Void>`；不裸返 String/Long/Boolean/List，不自建 `Result`/`ApiResponse` 平行封装
2. 分页：入参继承 `BasePageRequest`，返回 `PageResponse.ok(request, total, records)`（total 在前）
3. "不成立即抛业务异常"的判断用 `AssertUtils` 收敛，不写 if + throw
4. 错误响应不手工构造：抛类型化异常（自带映射或注解声明），让全局体系渲染
5. JSON 一律走 `JsonUtils`，禁止业务代码自建 `JsonMapper`/`ObjectMapper`
6. 业务/模块自带 `@RestControllerAdvice` 必须满足：只声明窄异常类型 + 显式 `@Order`（0~900）；要渲染就构造 `ErrorDescriptor` 交 `ExceptionOutputEngine.dispatch(...)`，不自己写响应体
7. 业务状态码定义在业务项目（实现 `ServerStatus` 的枚举），不回加进框架

## 红线

- `SimpleResponse` / `Page` 的字段名与顺序是线上契约，禁止修改
- `Page.item` 是单数（不是 items/records/list）；总数字段 JSON 键是 `total`（javadoc 曾误注 `totalCount`，见 references/response.md）
- 流式输出必须经 `HttpWriterFactory` 取写入器，不手写响应头绕过
- 错误消息脱敏：不含请求值、ID、堆栈、SQL、内部类名
- SSE/NDJSON 接口的异常处理与 JSON 同构（换派生注解即可），不为流式接口另写 try/catch

## 已知陷阱（详见对应 references）

- `@Valid @RequestBody` 在 Spring 7 是 fail-fast，violations 只有单条；非 body 参数校验聚合全部结果（`references/validation.md`）
- `@Phone`/`@EnumValue` 空值默认通过，必填须叠加 `@NotBlank`/`@NotNull`（`references/validation.md`）
- `spring.jackson.datetime-format`/`time-format`/`long-to-string` 是框架扩展键，Boot 官方文档查不到；`long-to-string` 默认关（`references/json-and-assert.md`）
- 模块 advice 声明 `Exception`/`Throwable` 兜底或无显式 `@Order` 会被启动期 `ModuleAdviceContractValidator` 告警（`references/exception-handling.md`）
- 敏感键日志掩码只覆盖 `LogSanitizer` 默认键集，自定义业务敏感字段需评估扩展（`boot-starter` 的 utils）
