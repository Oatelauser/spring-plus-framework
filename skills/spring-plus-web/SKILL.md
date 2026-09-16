---
name: spring-plus-web
description: 在已引入 io.github.oatelauser:spring-plus-web 的项目中进行 Controller、响应体、异常处理、参数校验、流式响应相关代码的编写、修改或排障时优先使用。本 skill 定义统一响应对象 SimpleResponse、状态码体系、AssertUtils 运行时断言、三协议异常注解、流式写入器、校验注解的使用约定与已知陷阱。
---

# spring-plus-web

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-web`
- Boot 应用零配置接入（自动配置经 `AutoConfiguration.imports` 注册）

## 模块定位

Spring Web 层能力拓展。提供：统一响应、状态码、全局异常（JSON/SSE/NDJSON）、流式写入器、分页、校验注解、请求追踪、运行时断言、JSON 门面。

注意以下能力**不在**本模块（别找错地方）：

- HTTP 客户端 / Redis 工具 / 配置加密 / 优雅停机 → `spring-plus-boot`
- 幂等 / 防重 → `spring-plus-governor`
- 鉴权注解 → `spring-plus-security`

## 优先复用的公开类型

响应与状态码（`io.github.oatelauser.springplus.web.response`）：

- `SimpleResponse<T>`（工厂 `ok()` / `fail()`）
- `PageResponse<T>` / `Page` / `BasePageRequest` / `FieldErrorInfo`
- `ServerStatus`（接口）/ `ClientStatus` / `BusinessStatus` / `SystemStatus` / `CommonStatus` / `ServerStatusProvider`

异常与断言（`web.error`）：

- `ServiceException`（`.withStack()` / `.signal()` 链式开关）
- `AssertUtils`（notNull / isTrue / state / hasText / notEmpty×3 / noNullElements）
- `@JsonExceptionResponse` / `@SseExceptionResponse` / `@NdjsonExceptionResponse` / `@ExceptionResponse`
- `ExceptionMapper`（SPI）/ `LogStackPolicy`
- `SseConnection` / `SseConnectionFactory` / `SseExceptionEmitter`（`error.sse`）

流式与工具：

- `HttpWriterFactory` + `ChunkStreamWriter` / `NdjsonStreamWriter` / `FileDownloadWriter`（`web.stream`）
- `@Phone` / `@EnumValue` / `@ListValues` / `@NoNullElement` / `@UniqueElement`（`web.validation`）
- `JsonUtils` / `ApplicationContextHolder` / `BeanUtils`（`web.utils`）
- `@RecordHttp` / `@EnableRecordHttp`（`web.trace`）

## 决策规则

1. Controller 返回值一律 `SimpleResponse<T>` / `PageResponse<T>`；Void 用 `SimpleResponse<Void>`
2. 分页入参继承 `BasePageRequest`，响应用 `PageResponse.ok(request, list, total)`
3. 需要声明"不成立即抛业务异常"的判断，用 `AssertUtils`，不写 if + throw
4. 错误响应不要手工构造：抛类型化异常，让全局体系渲染（注解 P0 > handler 默认 > Mapper 链 > 兜底）
5. JSON 一律走 `JsonUtils`（容器 JsonMapper 同源），禁止业务代码自建 mapper

## 返回体规则

```java
return SimpleResponse.ok(vo);
return SimpleResponse.ok();
return PageResponse.ok(request, records, total);
return SimpleResponse.fail(BusinessStatus.DATA_NOT_EXIST, id);   // 占位符格式化
```

强约束：

- 不要裸返回 `String` / `Long` / `Boolean` / `List<T>`
- 不要自建 `Result` / `ApiResponse` / `PageResult` 平行封装
- `Page` 字段固定为 `item / totalCount / pageNum / pageSize / totalPage`（单数 `item` 是项目约定）

## 异常与错误码规则

- 业务自定义状态码：业务项目里实现 `ServerStatus` 接口的枚举，或 `ServerStatus.of(code, msg)` 临时构造；不要把业务私有码加回框架
- 异常映射声明位置优先级：异常类上贴注解 > 独立 `ExceptionMapper` Bean > Controller 方法注解
- 错误消息脱敏：不含请求值、ID、堆栈、SQL、类名
- SSE / NDJSON 接口的异常处理与 JSON 同构，只需换派生注解，不要为流式接口另写 try/catch

## 校验规则

- `@Phone` / `@EnumValue` 空值默认通过；必填字段叠加 `@NotBlank` / `@NotNull`
- 集合元素约束用 `@NoNullElement` / `@UniqueElement`，取值范围用 `@ListValues`

## 不要这样做

- 不要修改 `SimpleResponse` / `Page` 的字段名（线上契约）
- 不要在业务代码里解析 `details` 的内部结构之外再造错误清单
- 不要绕过 `HttpWriterFactory` 手写响应头去实现流式输出
- 不要假设 `long-to-string` 已开启（默认关，需要时配 `spring.jackson.long-to-string=true`）

## 已知注意事项

- 成功码唯一（`00000`），`getSuccess()` 不存在"CREATED 二档"歧义——这是与双成功码体系的有意差异
- `spring.jackson.datetime-format` / `time-format` / `long-to-string` 是框架扩展键，Boot 官方文档查不到
- `ClassValidatorPostProcessor` 反射 hibernate-validator 内部 API，HV 版本锁定 9.1.0.Final
- 非 Boot 环境（无自动配置）时 `JsonUtils.shared()` 回落自建实例，行为与容器实例一致（时间格式走默认值）
