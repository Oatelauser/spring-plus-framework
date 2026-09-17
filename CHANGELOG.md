# Changelog

本项目的所有显著变更记录于此。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [1.1.0] — 未发布（安全加固版）

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

- spring.factories 注册文件修复（web ApplicationContextInitializer 与 boot EnvironmentPostProcessor 的行续接损坏，此前实际未生效）
- 新增安全回归测试 22 个（全仓 238 → 预计 245+）

## [1.0.0] — 2026-09-16

首个正式版（已发布 Maven Central）。

- 五模块：spring-plus-web / boot / governor / security / calcite-memory
- 统一响应 `SimpleResponse`（ok/fail）、状态码体系（00000/A0/B0/C0）、分页四件套（Page/PageResponse/BasePageRequest/FieldErrorInfo）
- 全局异常处理三协议（JSON/SSE/NDJSON）、流式写入器四件套、`AssertUtils` 运行时断言
- 幂等/防重治理、声明式鉴权、Calcite 内存 SQL
- examples：21 端点对照样例
