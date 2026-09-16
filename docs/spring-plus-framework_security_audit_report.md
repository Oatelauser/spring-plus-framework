# spring-plus-framework 现网发布前安全审计报告

> 项目：`Oatelauser/spring-plus-framework`  
> 审计基线：GitHub `main` 分支当前代码（仓库公开代码）  
> 项目版本：`1.0.0`  
> 审计日期：2026-09-16  
> 审计目标：面向现网发布，对框架自身及其作为公共 Starter / Library 被业务系统依赖后的安全风险进行源码级排查。  
> 结论状态：**当前不建议直接作为现网安全基线发布。**

---

## 1. 执行摘要

本次审计以“公共 Java / Spring Framework 发布前安全审计”为标准，从依赖安全、认证授权、HTTP Client、配置加密、服务治理、流式响应、SQL 引擎、异常处理、CI/CD 供应链等方向开展检查。

截至本报告生成时，已经确认以下问题：

| 编号 | 风险 | 级别 | 状态 |
|---|---|---:|---|
| SEC-001 | Spring Boot 4.1.0 对应 Spring Framework 7.0.8，存在已公开安全更新窗口 | **P0** | 已确认 |
| SEC-002 | 授权器对“空权限集合”采用 fail-open（直接 ALLOW） | **P1** | 已确认 |
| SEC-003 | Security Authorizer 同时 `@Component` + `@Bean` 注册，存在重复处理器/启动失败风险 | **P1** | 已确认 |
| SEC-004 | HTTP Client 默认 `followRedirect=true`，且支持统一敏感 Header / AuthProvider，存在 SSRF/凭据转发攻击面 | **P1** | 高风险设计项，需继续验证利用链 |
| SEC-005 | GitHub Actions 第三方 Action 使用浮动 tag，发布链未 pin 到 commit SHA | **P1** | 已确认的供应链加固缺陷 |
| SEC-006 | `@Authorize(beanName)` 直接从 ApplicationContext 获取 Bean，属于高敏感扩展点 | **P1** | 暂未证明可利用 |
| SEC-007 | Calcite Memory SQL 执行域存在 SQL / UDF / Schema / CodeGen 等高敏感攻击面 | **P1** | 需继续源码与 PoC 验证 |

同时，当前检查中也存在已经初步排除的风险：

- 配置加密核心实现使用 AES-256-GCM、随机 12-byte IV、128-bit GCM Tag，**目前未发现明显密码学实现错误**。
- `ApiRequest.toString()` 当前不会直接输出请求 Header、Authorization、Cookie 或 Body，**当前源码未发现异常日志直接泄漏 Authorization 的证据**。
- 当前未发现可以直接证明的 RCE、反序列化 RCE、任意文件读取；这些仍需继续做完整源码追踪和 PoC 验证。

**综合判断：项目具备较好的基础安全设计，但仍存在必须先整改的依赖基线问题、授权 fail-open 语义，以及多个高价值攻击面。现阶段不应出具“可直接上线”的安全结论。**

---

# 2. 项目与技术栈基线

根 `pom.xml` 当前配置：

```xml
<version>1.0.0</version>
<spring-boot.version>4.1.0</spring-boot.version>
<lombok.version>1.18.46</lombok.version>
<mybatis-flex.version>1.11.8</mybatis-flex.version>
<calcite.version>1.42.0</calcite.version>
<swagger.version>2.2.53</swagger.version>
<hibernate-validator.version>9.1.0.Final</hibernate-validator.version>
```

模块：

```text
spring-plus-web
spring-plus-boot
spring-plus-governor
spring-plus-security
spring-plus-calcite-memory
examples/spring-boot-web-example
```

项目属于“公共框架/Starter”性质，因此漏洞影响面明显高于普通单体业务：

```text
框架漏洞
   ↓
Maven Central
   ↓
多个业务系统依赖
   ↓
同一缺陷被批量复制到生产环境
```

源码基线证据：

- `pom.xml`
- `spring-plus-security`
- `spring-plus-boot`
- GitHub Actions release workflow

---

# 3. P0：依赖安全基线不满足现网发布要求

## SEC-001：Spring Boot 4.1.0 / Spring Framework 7.0.8 安全窗口

### 3.1 当前版本

根 `pom.xml` 锁定：

```xml
<spring-boot.version>4.1.0</spring-boot.version>
```

Spring Boot 4.1.0 的依赖管理对应 Spring Framework 7.0.8。

### 3.2 已公开安全问题

截至 2026-09-16，Spring 官方已针对 Spring Framework 7.0.x 发布多条安全公告，受影响范围包含 7.0.0 - 7.0.8，修复版本至少为 7.0.9。

其中与本项目攻击面高度相关的包括：

- CVE-2026-47884：XsltView 特定条件下 SSRF / RCE
- CVE-2026-47885：WebFlux multipart 资源限制绕过导致 DoS
- CVE-2026-47888：RSocket memory leak
- CVE-2026-47889：WebFlux Cookie SameSite 属性丢失
- CVE-2026-47890：SSE stream corruption
- CVE-2026-47892：WebFlux functional endpoint header predicate bypass
- CVE-2026-47893：WebSocket exception reason 暴露 request header
- CVE-2026-59280：FreeMarker path traversal
- CVE-2026-59282：DataBinder / BeanWrapper 相关 DoS

### 3.3 与本项目的相关性

尤其值得关注：

#### SSE

项目本身提供 SSE、NDJSON、流式响应能力，因此 Spring Framework SSE 相关安全问题必须纳入测试。

#### Web / WebFlux

项目为 Web 公共库，最终会被业务服务作为基础 Starter 使用，因此框架安全问题具有供应链放大效应。

### 3.4 修复建议

最低要求：

```text
Spring Boot 4.1.1
Spring Framework 7.0.9
Spring Security 7.1.1
Spring Session 4.1.1
Tomcat 11.0.24
```

升级后必须重新检查最终解析依赖：

```bash
mvn dependency:tree -Dscope=runtime
mvn dependency:tree -Dverbose
mvn help:effective-pom
```

重点确认没有残留：

```text
spring-web 7.0.8
spring-webmvc 7.0.8
spring-webflux 7.0.8
spring-security-* 7.1.0
```

### 3.5 参考

- Spring Boot 4.1 Release Notes
- Spring Boot 4.1.1 Release Notes
- Spring Security Advisories
- Spring Framework Security Advisories

---

# 4. P1：Security 授权 fail-open

## SEC-002：空权限集合直接 ALLOW

文件：

```text
spring-plus-security/
src/main/java/
io/github/oatelauser/springplus/security/authorization/
GrantedAuthorityAuthorizer.java
```

核心实现：

```java
protected AuthorizationResult verify(
        Set<String> requireAuthorities,
        Collection<GrantedAuthority> grantedAuthorities) {

    if (CollectionUtils.isEmpty(requireAuthorities)) {
        return ALLOW;
    }

    for (GrantedAuthority grantedAuthority : grantedAuthorities) {
        if (requireAuthorities.contains(grantedAuthority.getAuthority())) {
            return ALLOW;
        }
    }

    return AnnotationAuthorizationDecision.deny(requireAuthorities);
}
```

### 4.1 安全含义

当前模型：

```text
annotation absent
        ↓
skip authorization
```

这是正常的。

但：

```text
annotation present
        ↓
解析出空权限
        ↓
ALLOW
```

就属于 fail-open。

### 4.2 潜在触发路径

包括：

- 注解误配置
- 自定义 `AnnotationAuthorizer`
- 动态元注解组合
- 权限解析代码回归
- 未来增加新的授权器时返回空集合

### 4.3 风险

一旦某个应受保护的方法被错误解析成空权限集合，就会：

```text
本应 DENY
   ↓
实际 ALLOW
   ↓
形成权限绕过
```

### 4.4 建议

安全模型应区分：

```text
未发现授权注解
    => 不参与该授权器

发现授权注解但权限要求为空
    => 配置错误 / DENY / 启动时失败
```

不要统一处理成：

```text
empty requirement => ALLOW
```

推荐：

```java
if (annotationPresent && requireAuthorities.isEmpty()) {
    throw new IllegalStateException(...)
}
```

或者明确拒绝。

---

# 5. P1：Security Authorizer 双注册

## SEC-003：`@Component` + `@Bean` 重复注册

### 5.1 当前状态

`RequiresRoleAuthorizer`：

```java
@Component
public class RequiresRoleAuthorizer ...
```

同时自动配置：

```java
@Bean
public RequiresRoleAuthorizer requiresRoleAuthorizer(...)
```

`RequiresPermissionAuthorizer` 同样存在该模式。

### 5.2 为什么存在问题

在正常自动配置环境下可能因包扫描路径不同而表现不同，但从框架设计上：

```text
@Component
      +
@Bean
      ↓
潜在两个实例
```

而 `CompositeAuthorizationManager` 会根据 annotation 类型建立唯一集合：

```java
if (!annotationSet.add(type)) {
    throw new IllegalStateException(...)
}
```

因此极端情况下会出现：

```text
重复 AnnotationAuthorizer
       ↓
duplicate annotation type
       ↓
IllegalStateException
       ↓
应用启动失败
```

### 5.3 风险性质

这不是直接权限绕过，更偏：

- 可用性风险
- 配置相关 DoS
- Starter 集成稳定性问题

### 5.4 建议

公共 Starter 建议只使用一种注册模式。

推荐：

```text
AutoConfiguration + @Bean
```

然后删除这些基础 Authorizer 上的：

```java
@Component
```

这样可以明确由 Starter 生命周期统一管理。

---

# 6. P1：HTTP Client 重定向 / SSRF 攻击面

## SEC-004：默认允许跟随 Redirect

`ApiClientSettings` 当前：

```java
private boolean followRedirect = true;
```

并同时支持：

```text
defaultHeaders
AuthProvider
Authorization
Cookie / 自定义 Header
Proxy
```

### 6.1 安全场景

典型攻击：

```text
https://trusted.example/api
          ↓
302 Location:
http://127.0.0.1:8080/admin
```

或者：

```text
https://trusted.example
          ↓
http://169.254.169.254/
```

如果上游请求还携带：

```text
Authorization
X-API-Key
Cookie
```

那么风险会从：

```text
SSRF
```

升级为：

```text
SSRF + Credential Forwarding
```

### 6.2 必须验证的内容

需要继续进行动态 PoC：

1. 是否自动跟随 301 / 302 / 303 / 307 / 308
2. HTTPS -> HTTP 是否允许
3. Host 改变后 Authorization 是否继续发送
4. Cookie 是否继续发送
5. 自定义 Header 是否继续发送
6. 是否解析 DNS 后直接连接 private address
7. 是否防止：
   - `127.0.0.1`
   - `localhost`
   - `0.0.0.0`
   - `::1`
   - RFC1918
   - `169.254.169.254`
   - IPv6 link-local
8. 是否防止 DNS rebinding

### 6.3 建议

默认应考虑：

```text
followRedirect = false
```

如果业务必须开启：

```text
允许 redirect
+
禁止跨 host 携带敏感 Header
+
禁止 HTTPS -> HTTP
+
IP / DNS 安全策略
```

并提供可选：

```text
Host allowlist
Scheme allowlist
Redirect allowlist
Private-IP blocklist
```

---

# 7. 当前 HTTP Client 的一项正面结果：异常日志未直接发现 Header 泄漏

`BaseApiClient` 在异常时记录：

```java
log.error("Request failed: {}", prepared, e);
```

而 `ApiRequest.toString()` 当前输出：

```text
HTTP method
URI
contentType
query param names
```

不会输出：

```text
Authorization
Cookie
request headers
request body
```

因此：

> 当前源码没有直接发现“请求失败后日志打印 Authorization/Cookie”的问题。

但仍应继续检查 `LoggingInterceptor`，尤其是：

```text
HEADERS
BODY
sensitiveHeaders
```

这一层。

---

# 8. 配置加密：当前核心实现未发现明显密码学错误

## SEC-005：ConfigCipher 当前实现检查

当前使用：

```text
AES/GCM/NoPadding
256-bit key
12-byte random IV
128-bit GCM tag
SecureRandom
Base64
```

并且：

```java
RANDOM.nextBytes(iv);
```

### 当前评价

这属于正确的 AEAD 使用方式。

目前没有发现：

```text
AES/ECB
固定 IV
固定 nonce
明文 XOR
弱随机数
无认证标签
```

等明显问题。

### 继续检查

仍需检查：

- 密钥来源
- 密钥是否写入日志
- 解密结果是否进入 Actuator
- 异常是否包含 plaintext
- 是否存在默认 key
- 测试代码是否泄露 sample key

---

# 9. `@Authorize(beanName)` 高敏感扩展点

## SEC-006

当前支持：

```java
@Authorize(beanName = "xxx")
```

实现：

```java
Object bean = applicationContext.getBean(beanName);

if (bean instanceof Authorizer authorizer) {
    return authorizer.check(authentication, mi);
}
```

另一路支持：

```java
@Authorize(beanClass = ...)
```

### 当前判断

目前没有证据证明：

```text
HTTP 输入
  ↓
beanName
  ↓
ApplicationContext.getBean()
  ↓
RCE
```

因此不能直接定性为 RCE。

但是：

```text
ApplicationContext.getBean(beanName)
```

本身是高敏感动态 Bean resolution。

### 安全要求

应确保 `beanName` / `beanClass`：

- 只能来自编译期注解
- 不能由数据库动态配置直接映射
- 不能来自用户输入
- 不能与表达式引擎混用

---

# 10. Calcite Memory：当前高风险攻击面，尚未完成最终利用链确认

模块：

```text
spring-plus-calcite-memory
```

该模块需要重点审计：

```text
SQL Parser
Schema
Table
Function
UDF
Table Function
JDBC Adapter
CSV Adapter
Reflection
Enumerable
Code Generation
Dynamic Function
```

尤其要测试用户可控 SQL 是否能够：

```sql
TABLE(...)
```

或者触发：

```text
Java reflection
custom UDF
filesystem adapter
JDBC access
code generation
```

### 安全目标

理想模型应为：

```text
User SQL
   ↓
SQL Parser
   ↓
Whitelist Schema
   ↓
Whitelist Table
   ↓
Whitelist Column
   ↓
Read-only relational execution
   ↓
Result
```

而不是：

```text
User SQL
   ↓
Calcite defaults
   ↓
全部 functions / schemas / adapters / reflection
```

### 当前结论

这是整个仓库中最值得继续做动态安全测试的模块之一。

---

# 11. Governor / 幂等治理：重点验证失败策略

模块：

```text
spring-plus-governor
```

依赖：

```text
Redis
Memory Store
AOP
MVC Interceptor
```

必须继续验证：

### Redis 故障时

```text
Redis unavailable
```

是：

```text
FAIL_OPEN
```

还是：

```text
FAIL_CLOSED
```

### 幂等 key

必须确认 key 至少考虑：

```text
tenant
user
API / resource
idempotency key
```

不能单纯：

```text
idempotency-key
```

否则会出现跨租户 / 跨用户碰撞。

### Race Condition

必须测试：

```text
Request A
Request B
    ↓
同时进入
    ↓
SETNX / lock
```

确保并发下只有一个成功。

### 业务异常

还需检查：

```text
锁成功
  ↓
业务异常
  ↓
key 是否释放
```

以及 TTL 是否能覆盖业务执行时间。

---

# 12. CI/CD 供应链安全

## SEC-007

当前 release workflow：

```yaml
on:
  push:
    tags: ['v*']
```

并使用：

```yaml
uses: actions/checkout@v4
uses: actions/setup-java@v4
uses: actions/upload-artifact@v4
```

### 问题

第三方 Action 使用 floating major tag：

```text
@v4
```

而不是 commit SHA。

推荐：

```text
uses:
  actions/checkout@<40-char-commit-sha>
```

### 原因

发布 workflow 具有：

```text
GPG_PRIVATE_KEY
CENTRAL_USERNAME
CENTRAL_PASSWORD
```

权限价值非常高。

如果 workflow 依赖的 Action 发生供应链劫持，则攻击者可能影响：

```text
Maven artifact
GPG signing
Maven Central publishing
release credentials
```

### 建议

1. 所有第三方 Action pin SHA。
2. CI / Release 分开权限。
3. GitHub Actions 设置最小 permissions。
4. tag 发布启用 protected tags。
5. Release workflow 不接受 PR 分支触发。
6. 发布账号最小权限。
7. GPG key 最好带口令。
8. 发布产物启用 provenance / attestations。
9. 检查失败 artifact 是否包含：
   - `settings.xml`
   - secrets
   - GPG key
   - token
   - credentials

---

# 13. 当前未发现 / 暂未确认的问题

以下项目目前不能被定性为漏洞：

| 项目 | 当前结论 |
|---|---|
| 直接 RCE | 未发现证据 |
| Java 原生反序列化 RCE | 未发现证据 |
| 配置加密算法错误 | 当前未发现 |
| 请求异常直接输出 Authorization | 当前未发现 |
| 明文 Secret 写入源码 | 当前审查范围内未发现 |
| SpEL 注入 | 当前未发现直接使用 SpEL 的证据 |
| 任意文件读取 | 尚未完成完整源码追踪 |
| Calcite RCE | 尚未完成 PoC |
| SSRF | HTTP Client 存在明确攻击面，利用链待验证 |
| 路径穿越 | 尚未完成完整代码审计 |

注意：

> “未发现”不等于“绝对不存在”。

---

# 14. 上线前必须整改的事项

## P0

### 1. 升级 Spring Boot / Spring Framework 安全基线

至少确保最终依赖达到：

```text
Spring Boot >= 4.1.1
Spring Framework >= 7.0.9
Spring Security >= 7.1.1
```

并重新执行依赖树扫描。

---

## P1

### 2. 修复授权 fail-open

修改：

```text
empty authority -> ALLOW
```

为：

```text
annotation exists + empty authority
    -> deny / configuration error
```

---

### 3. 解决 Authorizer 双注册

不要同时：

```java
@Component
```

以及：

```java
@Bean
```

---

### 4. HTTP Client 默认不要无条件允许 Redirect

建议：

```text
followRedirect = false
```

业务明确要求时再开启。

并加入：

```text
redirect host policy
private IP blocking
scheme downgrade blocking
credential header stripping
DNS rebinding protection
```

---

### 5. Release workflow pin SHA

将：

```text
@v4
```

改成固定 commit SHA。

---

# 15. 推荐继续进行的源码级深度审计

按优先级：

```text
P0  Dependency / CVE
 ↓
P1  Security
 ↓
P1  HTTP Client / SSRF
 ↓
P1  Calcite SQL / Function / CodeGen
 ↓
P1  Governor / Redis / Race
 ↓
P1  SSE / WebSocket / WebFlux
 ↓
P2  Crypto
 ↓
P2  Exception / Logging
 ↓
P2  CI/CD / Maven Central
```

---

# 16. 动态 PoC 测试矩阵

## Security

```text
anonymous
authenticated
wrong role
one of multiple roles
all roles
empty role
meta annotation
interface annotation
class annotation
method annotation
self invocation
private method
final method
CGLIB proxy
JDK proxy
async
scheduled
event listener
```

## HTTP Client

```text
301
302
303
307
308
https -> http
host change
Authorization forwarding
Cookie forwarding
127.0.0.1
localhost
169.254.169.254
RFC1918
IPv6 loopback
DNS rebinding
proxy
```

## Governor

```text
2 concurrent identical requests
10 concurrent identical requests
Redis unavailable
Redis timeout
Redis restart
business exception
business timeout
TTL expiration
same key across users
same key across tenants
```

## Calcite

```text
SELECT
JOIN
UNION
WITH
TABLE()
UDF
custom schema
filesystem
JDBC
reflection
large query
deep nesting
Cartesian product
huge sort
huge aggregation
```

---

# 17. 最终上线判定建议

在完成上述整改和 PoC 之前：

```text
当前状态：NOT READY FOR PRODUCTION
```

完成以下条件后再重新评估：

```text
[ ] Spring Framework 升级到安全版本
[ ] dependency tree 完整扫描
[ ] Security fail-open 修复
[ ] Authorizer 双注册修复
[ ] SSRF / Redirect PoC 通过
[ ] Calcite 安全边界通过
[ ] Governor race tests 通过
[ ] SSE/WebFlux 安全测试通过
[ ] Release Actions pin SHA
[ ] Secret / artifact 检查通过
[ ] 安全回归测试加入 CI
[ ] 发布前 SBOM + CVE 扫描
```

---

# 18. 建议的 CI 安全门禁

发布 Maven Central 前建议至少：

```bash
mvn -B clean verify
mvn -B dependency:tree
mvn -B org.owasp:dependency-check-maven:check
```

并结合：

```text
OWASP Dependency-Check
Trivy / Grype（SBOM）
Semgrep
CodeQL
GitHub Secret Scanning
GitHub Dependabot
```

安全门禁建议：

```text
Critical CVE   -> fail
High CVE       -> fail
Secret found   -> fail
CodeQL high    -> fail
Security tests -> fail
```

---

# 19. 主要源码证据

以下为本次审计实际读取的代码：

### 根 POM

`pom.xml`

- Spring Boot 版本
- 模块结构
- dependencyManagement
- release profile

### Security

`GrantedAuthorityAuthorizer.java`

- 空权限直接 ALLOW

`CompositeAuthorizationManager.java`

- annotation authorizer 聚合
- duplicate annotation 检测

`RequiresRoleAuthorizer.java`

- role 权限解析
- default `ROLE_` prefix

`RequiresPermissionAuthorizer.java`

- permission 解析

`CompositeAnnotationAuthorizer.java`

- ApplicationContext Bean resolution

`SpringPlusSecurityAutoConfiguration.java`

- Authorizer Bean 注册
- Pre/Post authorize interceptor

### HTTP Client

`ApiClientSettings.java`

- `followRedirect`
- defaultHeaders
- SSL
- Proxy
- logging

`ApiClient.java`

- ApiClient 构建
- AuthProvider
- runtime filter

`BaseApiClient.java`

- request execution
- headers
- redirect related client behavior入口
- exception logging
- streaming

`ApiRequest.java`

- Authorization Header
- Basic/Bearer Auth
- multipart
- request metadata
- `toString()`

### Crypto

`ConfigCipher.java`

- AES-256-GCM
- random IV
- key validation

### CI/CD

`.github/workflows/release.yml`

- tag trigger
- signing key
- Maven Central credentials
- Maven deploy

---

# 20. 参考资料

Spring Boot 4.1 Release Notes  
https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes

Spring Boot 4.1.1 Release  
https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/

Spring Framework CVE-2026-47884  
https://spring.io/security/cve-2026-47884/

Spring Framework CVE-2026-47885  
https://spring.io/security/cve-2026-47885/

Spring Framework CVE-2026-47888  
https://spring.io/security/cve-2026-47888/

Spring Framework CVE-2026-47889  
https://spring.io/security/cve-2026-47889/

Spring Framework CVE-2026-47890  
https://spring.io/security/cve-2026-47890/

Spring Framework CVE-2026-47892  
https://spring.io/security/cve-2026-47892/

Spring Framework CVE-2026-47893  
https://spring.io/security/cve-2026-47893/

Spring Framework CVE-2026-59280  
https://spring.io/security/cve-2026-59280/

Spring Framework CVE-2026-59282  
https://spring.io/security/cve-2026-59282/

GitHub Repository  
https://github.com/Oatelauser/spring-plus-framework

---

## 附录 A：审计结论说明

本报告严格区分：

1. **已确认问题**：源码或官方安全公告可以直接证明。
2. **高风险设计项**：攻击面明确，但需要 PoC 才能判断实际可利用性。
3. **待验证项**：需要进一步读取源码、构建项目或运行动态测试。
4. **当前未发现**：在已读取代码范围内没有发现证据，不代表绝对不存在。

因此，本报告不把尚未验证的 RCE、SSRF、SQL 注入等问题伪装成已经确认的漏洞。

**现阶段建议：先修复 P0/P1，再进行第二轮动态 PoC 与完整代码扫描，然后再决定 Maven Central 正式发布。**