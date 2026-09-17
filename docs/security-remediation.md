# 安全整改对照表（1.0.1）

> 底稿：[spring-plus-framework-SECURITY-AUDIT.md](./spring-plus-framework-SECURITY-AUDIT.md)（白盒审计，V01-V27）
> 与 [spring-plus-framework_security_audit_report.md](./spring-plus-framework_security_audit_report.md)（SEC-001-007）。
> 本表记录每项的处置结论；发布 1.0.1 前逐项打勾，re-test 以本表为底稿。

## 已修复（进 1.0.1）

| 编号 | 问题 | 修复方式 | 验证 |
|---|---|---|---|
| SEC-001 | Spring Framework 7.0.8 CVE 窗口 | Boot 4.1.0 → **4.1.1**（SF 7.0.9），dependency:tree 确认无 7.0.8 残留 | 构建解析 7.0.9 ✅ |
| V01 🔴 | `showError=true` 异常原文默认透出 | 默认翻转为 **false**；排障经 `spring-plus.web.error-response.show-error=true` 显式开启 | GlobalExceptionPropertiesSecurityTest ✅ |
| V02 🔴 | SSE event/id 换行 → 响应拆分 | 写入器两个入口 `requireSingleLine` fail-fast；异常渲染器换行 event 回落 `app-error` | SseStreamWriterSecurityTest ✅ |
| V03 🔴 | 内存 SQL 无绑参 API | 新增 `query(sql, Object...)` / `query(sql, List)`，注入串作为数据比较 | ParameterizedQueryTest（含注入串用例）✅ |
| V04 🔴 | TypeMismatch 回显入参原值 | 消息只保留参数名 + 期望类型 | 编译期审查 + 代码注释 ✅ |
| V05 🟠 | 匿名主体碰撞 + 内存存储无界 | 容量上限（默认 10 万，构造器可调）满载 fail-closed；AtomicLong；javadoc/skill 红线（多租户必须自定义策略+Redis） | InMemoryIdempotentStoreCapacityTest ✅ |
| V06 🟠 | FileResources 路径穿越 | normalize + 拒绝 `..`/绝对路径/盘符 | SecurityUtilsTest ✅ |
| V07/SEC-002 🟠 | 鉴权 fail-open（空权限放行） | 分层 fail-closed：静态空配置**启动期失败**（校验内聚于两个授权器，自扫全 Bean）；动态空集运行期 **DENY** | FailClosedAuthorizationTest + RequiresAnnotationValidatorTest ✅ |
| V08 🟠 | security 无默认拒绝、消费方易裸奔 | security README/skill 红线（必须自配 FilterChain + denyAll）；example 增 `SecurityExampleConfig`（denyAll+白名单）与 `/secure/**` 演示端点 | 冒烟 401/403/200 ✅ |
| SEC-003 | Authorizer `@Component`+`@Bean` 双注册 | 移除 4 个类的 `@Component`，统一 autoconfig `@Bean` | 编译 + 启动 ✅ |
| V14 🟠 | 超管短路 `instanceof List` 恒 false | 改 `anyMatch`（Set/List/乱序三态测试锁定） | FailClosedAuthorizationTest ✅ |
| V17 🟡 | NDJSON writeRawLine 不校验换行 | 换行 fail-fast | SecurityUtilsTest ✅ |
| V25 🟡 | .gitignore 缺密钥规则 | 补 `*.asc/*.gpg/*.pgp/secring*/.env` | 规则在位 ✅ |
| V26/SEC-005 | CI actions 未 pin SHA + 无依赖告警 | 三个 action pin commit SHA；Dependabot（周检）；OWASP dependency-check 进 CI（观察期不拦截） | workflow 在位 ✅ |

## 明确不做 / 后续版本（backlog）

| 编号 | 问题 | 处置 |
|---|---|---|
| V09/SEC-004 🟠 | ApiClient SSRF 面（自由 uri/跟随重定向/无内网拦截） | 1.1.x：`spring-plus.client.ssrf.*` 可选护栏 + 重定向凭据剥离；当前以 skill/javadoc 红线警示 |
| V10 🟠 | 请求/响应 body 日志无脱敏 | 1.1.x：sensitive-field-names 掩码；示例 yml 日志级别已维持 info |
| V11 🟠 | ENC 明文经 actuator/env 可见 | 文档要求 `show-values=NEVER`（audit §3.4）随 V08 红线发布；属性源脱敏 1.1.x |
| V12 🟠 | InsecureTlsHelper 公开面过大 | 1.1.x：@Deprecated + 迁移包可见性（破坏性，随 1.1.0） |
| V13 🟠 | Redis 通配批量无护栏 | 1.1.x：纯通配拒绝 + bget 上限 |
| V15 🟡 | governor SpEL 用 StandardEvaluationContext | 1.1.x：SimpleEvaluationContext + 表达式缓存 |
| V16 🟠 | 追踪过滤器响应缓存无上限 | 1.1.x：响应侧限大小 + 默认跳过下载类 |
| V18 🟡 | 示例违反自家脱敏规范（用户名拼异常） | 1.1.x 示例净化 |
| V19 🟡 | 业务异常释放防重 key 可被刷 | 1.1.x：`releaseOnFailure` 属性 |
| V20 🟡 | 匿名/IP 主体 key 可预测占用 | 已随 V05 文档红线；策略增强 1.1.x |
| V21 🟡 | 日志 message CRLF 伪造 | 1.1.x：DefaultExceptionLogger 转义+截断 |
| V22 🟡 | Calcite XML 函数黑名单 | 1.1.x 纵深（XXE 已由 1.42 修复） |
| V23 🟡 | ConfigEncryptor CLI 密钥进 shell 历史 | 1.1.x：env 模式主推 + 警告 |
| V24 🟡 | HV 内部 API 反射无版本断言 | 1.1.x：启动期版本断言 + 静默分支 warn |
| V27 🟡 | Accept 头子串协议探测 | 保持现状（仅影响错误渲染形态），记录即可 |
| SEC-006 | `@Authorize(beanName)` 高敏感扩展点 | 保持编译期注解限定；文档红线（禁止动态来源） |
| SEC-007 | Calcite 深度 PoC（TABLE()/UDF/CodeGen） | 1.1.x 动态测试矩阵；当前 RESTRICTED 白名单 + 绑参 API 已收主要面 |
| — | CodeQL/Semgrep/GitHub Environment 审批 | backlog，CI 观察期后评估 |
