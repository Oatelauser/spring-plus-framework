# spring-plus-web

Spring Web 层能力拓展：不依赖 Spring Boot 也能使用的 Web 协议层能力（自带 Boot 自动配置，Boot 应用零配置接入）。

## 能力清单

| 能力 | 入口 |
|---|---|
| 统一响应 + 状态码体系 | `SimpleResponse` / `ServerStatus` / `ClientStatus` / `BusinessStatus` / `SystemStatus` / `ServerStatusProvider` |
| 分页四件套 | `Page` / `PageResponse` / `BasePageRequest` / `FieldErrorInfo` |
| 运行时业务断言 | `AssertUtils`（失败抛 `ServiceException`） |
| 全局异常体系 | `ServiceException`、`@JsonExceptionResponse` / `@SseExceptionResponse` / `@NdjsonExceptionResponse`、`ExceptionMapper` 链、`GlobalExceptionAdvice`（自动装配） |
| SSE 连接封装 | `SseConnection` / `SseConnectionFactory` / `SseExceptionEmitter` |
| 流式响应写入器 | `HttpWriterFactory` + `ChunkStreamWriter` / `NdjsonStreamWriter` / `FileDownloadWriter` / `SseStreamWriter` |
| 校验注解 | `@Phone` / `@EnumValue` / `@ListValues` / `@NoNullElement` / `@UniqueElement` / `ClassValidator` |
| 请求追踪 | `@RecordHttp` / `@EnableRecordHttp` / `LoggingHttpTraceFilter` |
| 统一 JSON 门面 | `JsonUtils`（容器 `JsonMapper` 优先，非容器兜底自建） |
| ~~启动过程 SPI~~ | 已移至 spring-plus-boot（`boot.lifecycle` 包） |

## 包结构（名实对照）

```
io.github.oatelauser.springplus.web
├── response/    响应与状态码（SimpleResponse / ServerStatus / Page / PageResponse / BasePageRequest / FieldErrorInfo）
├── error/       异常体系（annotation / descriptor / engine / mapper / output / sse 子包 + ServiceException）
├── stream/      流式响应写入器（原 servlet 包，名实修正）
├── trace/       HTTP 请求追踪（原 process 包；@RecordHttp 注解同包）
├── validation/  校验注解（field / collection / clazz 三层）
├── utils/       ApplicationContextHolder / JsonUtils / BeanUtils / FileResources / AssertUtils（运行时断言） 等
└── autoconfigure/ SpringPlusWebAutoConfiguration / ExceptionHandlingAutoConfiguration / GlobalExceptionProperties
```

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-web</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 核心用法

### 统一响应

```java
SimpleResponse.ok()                    // 无数据
SimpleResponse.ok(data)                // 带数据
SimpleResponse.ok(message, data)       // 自定义成功文案
SimpleResponse.fail(BusinessStatus.DATA_NOT_EXIST)          // 枚举直抛
SimpleResponse.fail(BusinessStatus.PARAMETER_MISSING, "userId")  // 占位符格式化
SimpleResponse.fail("B0001", "签名校验失败")                   // 自定义码
```

JSON 结构（字段顺序即线上契约）：

```json
{ "code": "00000", "message": "操作成功", "data": { ... }, "success": true }
```

失败时 `data` 恒为 null，`details` 携带结构化补充信息（`@Valid` 字段错误清单等）：

```json
{ "code": "A0430", "message": "[age] age 不能为空", "data": null,
  "details": { "violations": [ { "field": "age", "msg": "age 不能为空" } ] }, "success": false }
```

### 分页

```java
class UserPageRequest extends BasePageRequest { private String keyword; }

PageResponse.ok(request, records, total)
// data: { "item": [...], "totalCount": 57, "pageNum": 1, "pageSize": 10, "totalPage": 6 }
```

`BasePageRequest` 自带 `pageNum`（默认 1）与 `pageSize`（默认 10，上限 500 防深分页）校验。

### 运行时断言

```java
AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST);
AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST, id);   // 占位符格式化
AssertUtils.isTrue(amount.signum() > 0, "B0102", "金额必须为正数"); // 临时码直抛
```

覆盖 `notNull` / `isTrue` / `state` / `hasText` / `notEmpty`（Collection/数组/Map）/ `noNullElements` 八类断言。

### 声明式异常映射

```java
@JsonExceptionResponse(value = RateLimitException.class, code = "A0429",
        msg = "请求过于频繁", httpStatus = HttpStatus.TOO_MANY_REQUESTS,
        logPolicy = LogStackPolicy.NEVER)
```

三协议派生注解（JSON / SSE / NDJSON）共享同一套属性；SSE 接口失败发 `app-error` 事件而非 JSON，NDJSON 流内异常自动补写错误行。优先级：方法/类级注解（P0）> handler 默认 descriptor（P1）> Mapper 链（P2）> 兜底（P3）。

### 流式响应

```java
@GetMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
public void stream(HttpServletResponse response) throws Exception {
    NdjsonStreamWriter writer = streamWriterFactory.ndjson(response);
    writer.writeLine(Map.of("seq", 0, "token", "delta-0"));
    writer.close();
}
```

`HttpWriterFactory` 由自动配置注入，SSE / NDJSON / Chunk / 文件下载四件套一行取得，协议头已配好。

## 配置键

| 键 | 默认 | 说明 |
|---|---|---|
| `spring.jackson.time-zone` | JVM 默认 | 时区（官方键） |
| `spring.jackson.datetime-format` ⚑ | `yyyy-MM-dd HH:mm:ss` | LocalDateTime 格式（框架扩展键） |
| `spring.jackson.date-format` | `yyyy-MM-dd` | Date 与 LocalDate 格式（官方键，同名同义） |
| `spring.jackson.time-format` ⚑ | `HH:mm:ss` | LocalTime 格式（框架扩展键） |
| `spring.jackson.long-to-string` ⚑ | `false` | Long→String 序列化（框架扩展键，防 JS 精度丢失） |
| `spring-plus.web.error-response.*` | - | 全局异常行为 |
| `spring-plus.web.validation.fail-fast` | `true` | 类级校验 fail-fast |

## 已知注意事项

- `getSuccess()` 仅把 `00000` 视为成功——本框架单一成功码，不存在"CREATED 二档成功"的判定歧义
- `@Phone` / `@EnumValue` 空值默认通过（非必填校验），必填字段须叠加 `@NotBlank` / `@NotNull`
- `JsonUtils` 是唯一 JSON 门面：容器内有 `JsonMapper` Bean 时与其同源，业务代码不要自建 `JsonMapper`
- `ClassValidatorPostProcessor` 反射依赖 hibernate-validator 内部 API，HV 版本由根 POM 锁定 9.1.0.Final，升级需回归 validation 域测试
- `SimpleResponse` 的字段名与顺序是线上契约，改名即破坏性变更
