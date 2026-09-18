# spring-plus-framework

[![Maven Central](https://img.shields.io/maven-central/v/io.github.oatelauser/spring-plus-web-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.oatelauser/spring-plus-web-starter)
[![CI](https://github.com/Oatelauser/spring-plus-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/Oatelauser/spring-plus-framework/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Changelog](https://img.shields.io/badge/changelog-1.0.1%20dev-blue)](CHANGELOG.md)

面向 Maven 中央仓库发布的 Spring 公共库：为 Spring Web / Spring Boot 应用提供**统一响应、全局异常处理（JSON/SSE/NDJSON 三协议）、流式响应写入器、分页、服务治理（幂等防重）、声明式鉴权、内存 SQL 查询**能力。

基于 **Java 21 + Spring Boot 4.1**（Jackson 3 / `tools.jackson`）构建，不继承 `spring-boot-starter-parent`，与消费方项目的 parent 零冲突。

## Overview (English)

**spring-plus-framework** is a Maven Central library extending Spring Web / Spring Boot (Java 21, Boot 4.1, Jackson 3) with:

| Module | Provides |
|---|---|
| `spring-plus-boot-starter` | Family base module: shared utilities (reflection / context / JSON-free helpers), HTTP client (interceptor chain / retry / GZIP / metrics), config-file encryption (`ENC(...)`), graceful shutdown |
| `spring-plus-web-starter` | Unified response (`SimpleResponse`, A0/B0/C0 status codes), global exception handling across **JSON / SSE / NDJSON** protocols with **module-level advice precedence** (global fallback last), streaming response writers, pagination, validation annotations, runtime assertions (`AssertUtils`) |
| `spring-plus-redis-starter` | Redis utilities: `StringRedisTemplate` enhancement (Lua batch get/delete, atomic increment-with-expiry, wildcard guard) + ready-made Jackson-serialized `RedisTemplate<String,Object>` (`jacksonRedisTemplate`) |
| `spring-plus-governor-starter` | Idempotency & repeat-submit protection (`@Idempotent` / `@RepeatSubmit`) |
| `spring-plus-security-starter` | Declarative authorization: `@RequiresRole` / `@RequiresPermission` annotations replacing SpEL; module-level exception advice (denied pass-through + auth-exception error-code mapping) |
| `spring-plus-calcite-memory` | In-memory SQL over POJO/Map/List tables, powered by Apache Calcite. *Source-only from 1.0.1 — no longer published to Maven Central; build from source or use a private repository* |

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-web-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
@GetMapping("/user")
public SimpleResponse<User> user(@RequestParam Long id) {
    User user = userService.get(id);
    AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST);   // throws ServiceException on failure
    return SimpleResponse.ok(user);
}
```

Boot applications get everything auto-configured — see [examples/spring-boot-web-example](./examples/spring-boot-web-example) (21 endpoints covering the full exception-handling contract). Full documentation below is in Chinese; the API itself (class/method names, this README's tables) is language-neutral.

## 模块矩阵

| 模块 | 定位 | 依赖 |
|---|---|---|
| [`spring-plus-boot-starter`](./spring-plus-boot-starter/README.md) | 家族基础模块 + Spring Boot 生态能力拓展：通用工具（反射/容器/资源/日志脱敏/TLS）、HTTP 客户端（拦截器链/重试/GZIP/指标）、配置文件加密、优雅停机 | 无内部依赖 |
| [`spring-plus-web-starter`](./spring-plus-web-starter/README.md) | Spring Web 层能力拓展：统一响应（SimpleResponse / 状态码体系）、全局异常体系（JSON/SSE/NDJSON，模块级 advice 优先、全局兜底）、流式响应写入器、分页四件套、校验注解、请求追踪、运行时断言 | boot |
| [`spring-plus-redis-starter`](./spring-plus-redis-starter/README.md) | Redis 能力域：`RedisStringOperation`（Lua 批量读删/原子自增过期/通配护栏）、Jackson 化 `jacksonRedisTemplate`、缓存工具 | 无内部依赖 |
| [`spring-plus-governor-starter`](./spring-plus-governor-starter/README.md) | 服务治理：幂等提交 / 防重复提交（AOP 织入，Redis / 内存双存储）；限流熔断未来归此 | web |
| [`spring-plus-security-starter`](./spring-plus-security-starter/README.md) | 声明式鉴权：`@RequiresRole` / `@RequiresPermission` / `@Authorize` 等注解替代 SpEL；模块级异常 advice（denied 透传 + 认证异常映射错误码） | web、boot |
| [`spring-plus-calcite-memory`](./spring-plus-calcite-memory/README.md) | 内存 SQL 查询：POJO/Map/List 注册为内存表，Apache Calcite 驱动 SQL 查询（**自 1.0.1 起不再发布 Central**，源码保留，需源码/私仓引入） | 无内部依赖（独立） |
| [`examples/spring-boot-web-example`](./examples/spring-boot-web-example) | Spring Boot 接入示例：21 个端点覆盖异常体系全部验收用例 + 流式响应四件套 + 幂等 | web + boot + governor |

依赖方向（ADR 0003 倒置后）：`web → boot`、`security → boot`、`governor → web`；`boot` / `redis` / `calcite-memory` 无内部依赖。

## 快速上手

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-web-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
// 统一响应：成功只有一档 00000
@GetMapping("/user")
public SimpleResponse<User> user(@RequestParam Long id) {
    return SimpleResponse.ok(userService.get(id));
}

// 分页：Page{ item, totalCount, pageNum, pageSize, totalPage }
@GetMapping("/users")
public PageResponse<User> users(UserPageRequest request) {
    return PageResponse.ok(request, total, userService.page(request));
}

// 运行时断言：失败直接抛 ServiceException（替代 if + throw 样板）
AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST, id);

// 声明式异常映射：方法级注解直达错误码 / HTTP 状态 / 日志策略 / 自定义渲染
@PostMapping("/create")
@JsonExceptionResponse(value = UsernameDuplicatedException.class, code = "B0204",
        msg = "用户名已存在: {exception}")
public SimpleResponse<Void> create(@Valid @RequestBody CreateUserCmd cmd) { ... }
```

完整端点样例见 [examples/spring-boot-web-example](./examples/spring-boot-web-example)（迁移自旧项目 v2.0 错误处理验收 Controller，21 个端点逐一覆盖）。

## 配置键

本框架遵循"官方有对应键则用官方键"原则：

**`spring.jackson.*`（蹭官方命名空间，其中两个为框架扩展键）**

| 键 | 默认值 | 说明 |
|---|---|---|
| `spring.jackson.time-zone` | （跟随 JVM） | 时区，与官方键同名同义 |
| `spring.jackson.date-format` | `yyyy-MM-dd` | 与官方键同名同义：`java.util.Date` 与 `LocalDate` 的格式 |
| `spring.jackson.datetime-format` ⚑ | `yyyy-MM-dd HH:mm:ss` | `LocalDateTime` 格式，**框架扩展键**（官方无对应） |
| `spring.jackson.time-format` ⚑ | `HH:mm:ss` | `LocalTime` 格式，**框架扩展键** |
| `spring.jackson.long-to-string` ⚑ | `false` | Long 序列化为字符串（防 JS 精度丢失），**框架扩展键**，默认关闭 |

**`spring-plus.*`（框架自有命名空间）**

| 键 | 模块 | 说明 |
|---|---|---|
| `spring-plus.web.error-response.*` | web | 全局异常处理行为（详见 GlobalExceptionProperties） |
| `spring-plus.web.validation.fail-fast` | web | 类级校验 fail-fast，默认 true |
| `spring-plus.client.*` | boot | HTTP 客户端（配置 `base-url` 后自动装配默认 ApiClient） |
| `spring-plus.client.ssl.allow-insecure` | boot | 信任自签证书开关，默认 false |

其余官方 Jackson 行为（visibility / serialization 开关等）仍按 Spring Boot 官方 `spring.jackson.*` 配置——框架 customizer 注册进官方 `JsonMapperBuilderCustomizer` 链，一次配置全链生效。

## 状态码体系

参考阿里巴巴错误码规范，`SimpleResponse.code` 为五位字符串：

- `00000`：成功（唯一成功码，不存在第二成功档）
- `A0xxx`：客户端错误（参数缺失/格式错误/授权过期等，`ClientStatus`）
- `B0xxx`：业务错误（数据不存在/重复/并发冲突等，`BusinessStatus`）
- `C0xxx`：系统错误（内部异常/依赖不可用等，`SystemStatus`）

业务项目自定义状态码：实现 `ServerStatus` 接口（或 `ServerStatus.of(code, msg)` 临时构造），**业务私有码不要回加到框架**。

## 模块级异常 advice

异常体系支持多 `@RestControllerAdvice` 共存：Spring 按 order 排序后逐个咨询，第一个能匹配的 advice 直接赢。`GlobalExceptionAdvice` 显式 `@Order(Ordered.LOWEST_PRECEDENCE)`，是全局兜底；模块/业务自带 advice 声明更小的 order 即可优先接管自己的异常域。

契约（启动期由 `ModuleAdviceContractValidator` 告警校验）：

1. 模块 advice 只声明**窄异常类型**——`Exception`/`Throwable` 级兜底会遮蔽全局全部具体 handler（validation/DAO/请求解析等）；
2. 模块 advice 必须显式 `@Order`——不标则与全局兜底平局，先后由 bean 注册顺序决定；
3. 要渲染必须构造 `ErrorDescriptor` 后委托 `ExceptionOutputEngine.dispatch(...)`（守住协议探测/日志策略/已提交补写），不允许 advice 自己写响应体；rethrow 透传是唯一例外（security denied 语义）。

advice 段位约定：

| 段位 | 归属 |
|---|---|
| 0~900 | 框架模块与业务模块（security = 100） |
| `LOWEST_PRECEDENCE` | 全局兜底 `GlobalExceptionAdvice`（专属） |

首个落地用例：`spring-plus-security-starter` 的 `SecurityExceptionAdvice`（`@Order(100)`）——`AccessDeniedException` 家族透传给 `ExceptionTranslationFilter`（403 语义保留）；认证异常家族按子类型映射 `ClientStatus` 错误码（A0210/A0202/A0203/A0212/A0213/A0230/A0301）统一渲染为 401。

## 给 AI 的 skills

[`skills/`](./skills) 目录为本项目各模块的 AI 编程规范（skill），供接入本框架的 AI 助手加载使用：主 skill 定义流程闸门与检查清单，六个模块 skill 定义"优先复用的公开类型 / 负面清单 / 已知陷阱"。

## 构建与发布

```bash
mvn clean install          # 本地构建 + 全量测试（224 个测试）
mvn -P release deploy      # 发布到 Maven Central（需 central 账号与 GPG 密钥）
```

- Java 21 基线，Boot 4.1.0 BOM import（不继承 starter-parent）
- 根 POM 已配置 central-publishing / source / javadoc / gpg 插件（`-P release` 激活）
- examples 模块已排除发布（`excludeArtifacts`）
- **打 tag 自动发布**：推送 `v*` 标签触发 GitHub Actions 全自动构建/签名/上传 Central——完整流程（Secrets 配置、CI 脚本、发布 SOP、踩坑记录）见 [docs/release-with-github-actions.md](./docs/release-with-github-actions.md)

## License

[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
