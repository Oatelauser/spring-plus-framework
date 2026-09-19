# Changelog

本项目的所有显著变更记录于此。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [1.1.0] — 未发布（安全加固版）

### 模块级异常 advice 机制

- **多 advice 共存**：`GlobalExceptionAdvice` 显式 `@Order(Ordered.LOWEST_PRECEDENCE)` 成为全局兜底；模块可自带 `@RestControllerAdvice`（`@Order` 更小即优先，段位约定 0~900）接管自身异常域，Spring 原生"按 order 逐个咨询、第一个匹配者赢"语义
- **security 首个落地**：新增 `SecurityExceptionAdvice`（`@Order(100)`，SERVLET 应用且 web 错误引擎就位时自动装配）——`AccessDeniedException` 家族透传给 `ExceptionTranslationFilter`（403 语义保留）；认证异常家族按子类型映射 web 错误码（A0210/A0202/A0203/A0212/A0213/A0230/A0301）经 `ExceptionOutputEngine` 统一渲染为 401
- `ClientStatus` 新增 `A0212`（用户密码已过期）/ `A0213`（用户账户已过期）
- web 删除 `SECURITY_DENIED_CLASSES` 按类名透传的妥协（denied 让路由由 security 模块 advice 类型安全接管）

### ⚠️ 模块重组（破坏性，[ADR 0003](./docs/adr/0003-boot-as-base-redis-split.md) / [ADR 0004](./docs/adr/0004-starter-naming.md)）

- **全家族 starter 化重命名**：5 个发布模块坐标统一加 `-starter` 后缀——`spring-plus-boot` → `spring-plus-boot-starter`，`spring-plus-web` / `spring-plus-redis` / `spring-plus-governor` / `spring-plus-security` 同理；模块目录与 skills/ 技能目录随坐标同步改名。Java 包名、自动装配机制、依赖结构均不变；旧坐标不设迁移桥，直接不兼容替换（`spring-plus-calcite-memory` 不参与，见 ADR 0004 冻结说明）
- **依赖倒置**：`spring-plus-boot-starter` 不再依赖 `spring-plus-web-starter`，改为 `web → boot`、`security → boot`（governor 经 web 传递）——boot 成为家族基础模块；仅需配置加密/优雅停机的应用不再被迫传递引入 web
- **utils 包迁移**：`AnnotationUtils` / `ApplicationContextHolder` / `ApplicationContextUtils` / `BeanUtils` / `FileResources` / `InsecureTlsHelper` / `LogSanitizer` 由 `io.github.oatelauser.springplus.web.utils` 迁至 `io.github.oatelauser.springplus.boot.utils`（业务 import 需改；`AssertUtils`、`JsonUtils` 留守 web）；`spring.factories` 的 `ApplicationContextInitializer` 注册随迁 boot
- **Redis 独立成模块**：最终坐标 `spring-plus-redis-starter`（ADR 0003 拆分时定名 `spring-plus-redis`，随 ADR 0004 重命名）——`RedisStringOperation` / `CacheUtils` / `KeyValue` + Lua 脚本 + 自动配置，包名 `springplus.boot.redis` → `springplus.redis`；boot 不再传递 Redis 工具，使用方坐标替换
- **Jackson 化 RedisTemplate**（`spring-plus-redis-starter`）：新增 `jacksonRedisTemplate` Bean（`RedisTemplate<String, Object>`，按名注入、不接管 Boot 默认）——JSON 内嵌 `@class`（Object 值 round-trip 安全）、容器 `JsonMapper` 取副本配置不被 default typing 污染、String key + Jackson value 一把装配；classpath 无 Jackson 时整体退避

### 安全增强

- **V16**：请求追踪响应侧改有界旁录（直写透传 + 超限占位 + 二进制/multipart 跳过），大响应误标 `@RecordHttp` 不再缓冲整包
- **V10/V21**：日志脱敏——`LogSanitizer` 掩码 JSON/form 载荷中的敏感键值（password/token/phone 等，BODY 级客户端日志与请求追踪旁录均接入）；异常日志消息 CRLF 单行化 + 512 字符截断（防日志伪造）
- **V19**：`@RepeatSubmit` 新增 `releaseOnFailure`（默认 true 兼容）——防刷场景设 false 后业务异常不释放防重 key
- **V24**：ClassValidatorPostProcessor 启动期断言 hibernate-validator 大版本（当前 9.x，漂移即失败）+ 两个静默分支补 warn 日志
- **V22**：内存 SQL 白名单追加 XML 函数黑名单（EXISTS_NODE/EXTRACT_XML/XML_TRANSFORM/EXTRACT_VALUE）
- **V11**：ENC 解密属性源的 `toString` 脱敏（actuator/env 呈现只暴露键名不泄露明文）
- **V12**：`InsecureTlsHelper` 标记 `@Deprecated`（仅测试联调，生产使用视同漏洞；业务代码禁止直接引用）
- **V18**：示例净化——异常消息不再携带用户名原值（遵守自家脱敏规范）、SSE 演示复用共享线程池（修每请求新建不关闭的泄漏）、示例日志级别 debug→info
- **V15**：幂等注解 SpEL 求值收紧——SimpleEvaluationContext 只读数据绑定（`T()` 类型引用 / `new` 构造 / 方法调用一律拒绝）+ 表达式解析缓存
- **V13**：Redis 批量通配护栏——pattern 必须含实质前缀（拒绝 `*` 全库匹配），`batchGet` 返回上限默认 1000 条超限 fail-fast
- **V09/SEC-004**：ApiClient SSRF 防护——`spring-plus.client.ssrf.*`（enabled / allowed-hosts / deny-private-network，默认关闭保持兼容），请求发出前校验目标主机（绝对 URI 覆盖 baseUrl 的注入路径覆盖），解析失败 fail-closed；跨主机重定向自动剥离 Authorization/Cookie（`strip-credentials-on-cross-host-redirect` 默认开启，仅 HTTP_COMPONENTS 引擎）

## [1.0.1] — 未发布（安全修复版）

完整对照表见 [docs/security-remediation.md](./docs/security-remediation.md)。

### 安全修复

- **SEC-001**：Spring Boot 4.1.0 → 4.1.1（Spring Framework 7.0.9，脱离公开 CVE 窗口）
- **V01**：`showError` 默认值 `true` → `false`，未映射异常原文不再默认透出客户端（排障经 `spring-plus.web.error-response.show-error=true` 显式开启）
- **V02**：SSE 写入器 `event`/`id` 换行校验（防 SSE 响应拆分/事件伪造）；异常渲染路径换行事件名回落 `app-error`
- **V03**：`MemoryQuerySession` 新增 `query(sql, Object... params)` / `query(sql, List)` 绑参 API——含外部输入的内存 SQL 查询不再依赖字符串拼接
- **V04**：参数类型错误消息不再回显入参原值（只保留参数名 + 期望类型）
- **V05**：`InMemoryIdempotentStore` 容量上限（默认 100,000，构造器可调），满载 fail-closed；操作计数改 AtomicLong
- **V06**：`FileResources.getResource` 路径穿越防护（拒绝 `..`/绝对路径/盘符）
- **V07/SEC-002**：鉴权 fail-closed——`@RequiresRole(role = {})`、`@RequiresPermission` 空 source/action **启动期失败**（新增 `RequiresAnnotationValidator` 扫描全部 Bean）；运行期注解存在但权限解析为空时**拒绝**而非放行
- **V08**：security README/skill 增安全红线（必须自配 `SecurityFilterChain` 且默认 `denyAll`）；examples 新增 `SecurityExampleConfig` 基线与 `/secure/**` 鉴权演示端点
- **V14**：超管短路修复——`instanceof List` + 首元素判断对 Spring Security 常规 `Set` authorities 恒为 false，改为按元素匹配（Set/List/乱序均正确）
- **V17**：`NdjsonStreamWriter.writeRawLine` 换行 fail-fast
- **V25**：`.gitignore` 增密钥文件防误提交规则
- **SEC-003**：移除 Authorizer 的 `@Component`（统一自动配置 `@Bean` 注册，消除双注册隐患）
- **SEC-005/V26**：CI actions pin commit SHA；新增 Dependabot 与 OWASP dependency-check（观察期）

### ⚠️ 行为变更（升级注意）

- **`showError` 默认 false**：升级后未映射异常统一返回"系统内部错误"文案；本地排障请显式开启
- **空权限配置启动失败**：`@RequiresRole(role = {})` / `@RequiresPermission` 空 source/action 的应用**将无法启动**（此前分别表现为"放行所有已认证用户"与"运行期 500"）——请修正注解配置
- **运行期空权限拒绝**：自定义 `AnnotationAuthorizer` 解析结果为空集合时，从放行改为拒绝
- **包移动（破坏性）**：`StartupProcess` 由 `io.github.oatelauser.springplus.web.lifecycle` 移至 `springplus.boot.lifecycle`（实现该接口的业务代码需改 import）；`AssertUtils` 由 `web.error` 移至 `web.utils`
- **API 签名调整**：`Page.of` / `PageResponse.ok` 参数顺序调整为 total 前置（`ok(request, total, item)`）

### 其他

- **文档勘误**（代码即契约，以下文档表述已修正为与实现一致）：`Page` 总数字段 JSON 键为 `total`（此前 README/javadoc/CONTEXT 误写 `totalCount`，1.0.0 起实际序列化即为 `total`）；`PageResponse.ok` 参数序为 `(request, total, records)`；优雅停机 `ShutdownHook` 按 `Ordered` **升序**执行（此前 README 误写"逆序"，摘流量由 SmartLifecycle phase 机制保证）；`ApiClientSettings` 连接池配置前缀为 `apache-hc5.*`
- **发布范围缩减**：`spring-plus-calcite-memory` 自本版本起不再发布到 Maven Central（根 POM `excludeArtifacts` 排除；模块保留在源码仓与 Reactor 中正常构建/测试，需要方请源码或私仓引入。1.0.0 已发布版本不受影响）
- spring.factories 注册文件修复（web ApplicationContextInitializer 与 boot EnvironmentPostProcessor 的行续接损坏，此前实际未生效）
- 新增安全回归测试 22 个（全仓 238 → 预计 245+）

## [1.0.0] — 2026-09-16

首个正式版（已发布 Maven Central）。

- 五模块：spring-plus-web / boot / governor / security / calcite-memory
- 统一响应 `SimpleResponse`（ok/fail）、状态码体系（00000/A0/B0/C0）、分页四件套（Page/PageResponse/BasePageRequest/FieldErrorInfo）
- 全局异常处理三协议（JSON/SSE/NDJSON）、流式写入器四件套、`AssertUtils` 运行时断言
- 幂等/防重治理、声明式鉴权、Calcite 内存 SQL
- examples：21 端点对照样例
