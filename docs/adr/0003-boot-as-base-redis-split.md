# boot 作为基础模块（依赖倒置）、utils 归位与 Redis 能力域拆分

修订 [ADR 0001](./0001-capability-domain-module-split.md) 确立的 `boot → web` 单向依赖与模块职责划分。

## Context

- `spring-plus-web` 的 `utils` 包里沉淀了一批与 Web 无关的通用工具（反射 / 容器访问 / 断言 / 资源 / 日志脱敏 / TLS），放在"Web 能力域"名不正：security / governor / boot 都在消费它们。
- 依赖方向（boot → web）与工具的归属诉求相反：工具想下沉到 boot，但 web 自己也用它们，web 不能反过来依赖 boot（成环）。
- boot 内的 Redis 工具域（`RedisStringOperation` / `CacheUtils` + Lua 脚本 + V13 通配护栏）按 ADR 0001 自己的能力域原则是独立能力，与 `spring-plus-calcite-memory` 同级，却寄生在"Boot 生态拓展"里；governor 的幂等存储已在自行引入 spring-data-redis，Redis 能力的消费已经是模块级行为。

## Considered Options

- **依赖倒置 + utils 归位 + Redis 拆出（已选）**：web 通用工具迁 `springplus.boot.utils`，boot 删除对 web 的依赖，web / security 改为依赖 boot；Redis 域拆出为 `spring-plus-redis`。
- **新建 spring-plus-core 基础模块（弃）**：层次最纯粹，但多一个发布坐标，web / boot 两侧都要动 pom，改动量不小于倒置；收益只在"utils 完全脱离 Boot 语境"。留作 boot 将来膨胀时的再拆分选项。
- **维持现状（弃）**：工具放错域、Redis 域无法按能力域演进。

## Decision

1. **依赖方向**：`spring-plus-boot` 为家族基础模块，框架内不再依赖任何 spring-plus 模块；`spring-plus-web` / `spring-plus-security` 依赖 boot（governor 经 web 传递）。
2. **utils 归位**：`AnnotationUtils`、`ApplicationContextHolder`、`ApplicationContextUtils`、`BeanUtils`、`FileResources`、`InsecureTlsHelper`、`LogSanitizer` 七类迁至 `io.github.oatelauser.springplus.boot.utils`；`spring.factories` 的 `ApplicationContextInitializer` 注册随迁。
   - **留在 web**：`AssertUtils`（深度绑定 `ServiceException` / `ServerStatus`，错误域 DSL）、`JsonUtils`（回退实例复刻 web 的 JacksonConfiguration 定制，与错误渲染 / 流式输出同源）。
3. **Redis 拆出**：`spring-plus-redis` 独立模块（自带 `SpringPlusRedisAutoConfiguration` + imports 注册 + Lua 资源），`spring-data-redis` 保持 `provided`、缺席退避语义随迁；boot 不再传递 Redis 工具。

## Consequences

- boot 的重依赖（httpclient5 / spring-data-redis→已移除 / micrometer）均为 `provided`，倒置后 web 消费方仅额外传递 `spring-boot-starter` 与 `spring-boot-http-client`，自动配置全带条件注解，无对应类即静默跳过。
- **破坏性变更**：七类工具包名变更（仓库内 import 已同步）；仅依赖 boot 且此前经传递获得 web 异常体系的应用需显式加 spring-plus-web；使用 `RedisStringOperation` Bean 的应用需把坐标换成 spring-plus-redis。
- 只需配置加密 / 优雅停机的用户不再被迫传递引入 web（ADR 0001 当年接受的后果就此反转）。
- 客户端域（ApiClient）与配置加密域继续留在 boot：前者是 Boot http-client 抽象的拓展、后者深绑启动序列，均属"Boot 生态拓展"本职；各自膨胀出独立诉求时再立 ADR 拆分。
