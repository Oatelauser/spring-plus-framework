# spring-plus-framework

面向 Maven 中央仓库发布的 Spring 公共库：为 Spring Web / Spring Boot 应用提供**统一响应、全局异常处理（JSON/SSE/NDJSON 三协议）、流式响应写入器、分页、服务治理（幂等防重）、声明式鉴权、内存 SQL 查询**能力。

基于 **Java 21 + Spring Boot 4.1**（Jackson 3 / `tools.jackson`）构建，不继承 `spring-boot-starter-parent`，与消费方项目的 parent 零冲突。

## 模块矩阵

| 模块 | 定位 | 依赖 |
|---|---|---|
| [`spring-plus-web`](./spring-plus-web/README.md) | Spring Web 层能力拓展：统一响应（SimpleResponse / 状态码体系）、全局异常体系、流式响应写入器、分页四件套、校验注解、请求追踪、运行时断言 | 无内部依赖 |
| [`spring-plus-boot`](./spring-plus-boot/README.md) | Spring Boot 生态能力拓展：HTTP 客户端（拦截器链/重试/GZIP/指标）、Redis 工具、配置文件加密、优雅停机 | web |
| [`spring-plus-governor`](./spring-plus-governor/README.md) | 服务治理：幂等提交 / 防重复提交（AOP 织入，Redis / 内存双存储）；限流熔断未来归此 | web |
| [`spring-plus-security`](./spring-plus-security/README.md) | 声明式鉴权：`@RequiresRole` / `@RequiresPermission` / `@Authorize` 等注解替代 SpEL | web |
| [`spring-plus-calcite-memory`](./spring-plus-calcite-memory/README.md) | 内存 SQL 查询：POJO/Map/List 注册为内存表，Apache Calcite 驱动 SQL 查询 | 无内部依赖（独立） |
| [`examples/spring-boot-web-example`](./examples/spring-boot-web-example) | Spring Boot 接入示例：21 个端点覆盖异常体系全部验收用例 + 流式响应四件套 + 幂等 | web + boot + governor |

依赖方向：`boot → web`、`governor → web`、`security → web`，`calcite-memory` 完全独立。

## 快速上手

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-web</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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
    return PageResponse.ok(request, userService.page(request), total);
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

## 给 AI 的 skills

[`skills/`](./skills) 目录为本项目各模块的 AI 编程规范（skill），供接入本框架的 AI 助手加载使用：主 skill 定义流程闸门与检查清单，五个模块 skill 定义"优先复用的公开类型 / 负面清单 / 已知陷阱"。

## 构建与发布

```bash
mvn clean install          # 本地构建 + 全量测试（192 个测试）
mvn -P release deploy      # 发布到 Maven Central（需 central 账号与 GPG 密钥）
```

- Java 21 基线，Boot 4.1.0 BOM import（不继承 starter-parent）
- 根 POM 已配置 central-publishing / source / javadoc / gpg 插件（`-P release` 激活）
- examples 模块已设置 `maven.deploy.skip`，不参与发布

## License

[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
