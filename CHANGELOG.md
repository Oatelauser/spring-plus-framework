# Changelog

本项目的所有显著变更记录于此。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [SemVer](https://semver.org/lang/zh-CN/)。

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
