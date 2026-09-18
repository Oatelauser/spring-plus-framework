# spring-plus-security-starter

> ⚠️ **安全红线（引入本模块前必读）**
>
> 1. 本模块**只做授权**（注解声明"需要什么角色/权限"），**不做认证**（登录、token 解析、会话）。认证链路必须由业务项目自建。
> 2. **必须自配 `SecurityFilterChain` 且默认 `denyAll`，放行走显式白名单**——没有这条，所有接口对公网裸奔（引几个注解 ≠ 安全）。
> 3. 最小可用配置与演示账号见 [examples 的 SecurityExampleConfig](../examples/spring-boot-web-example/src/main/java/io/github/oatelauser/springplus/example/config/SecurityExampleConfig.java)。
> 4. `@RequiresRole(role = {})` / `@RequiresPermission` 空 source/action 属配置错误：**启动期直接失败**（fail-closed），不要试图绕过。

声明式鉴权域：以注解表达角色与权限要求，替代 Spring Security 的 SpEL 配置（`@PreAuthorize("@ss.hasPermission('x')")` 风格）。

## 能力清单

| 能力 | 入口 |
|---|---|
| 角色要求 | `@RequiresRole` / `@RequiresAdminRole` |
| 权限要求 | `@RequiresPermission`（source + action 自动拼权限键） |
| 通用授权 | `@Authorize` / `@PostAuthorize` |
| 主体注入 | `@Principal`（Controller 方法参数直接注入登录主体） |
| 动作类型 | `ActionType` / `Relation`（权限键组合语义） |
| 授权器 SPI | `Authorizer` / `AnnotationAuthorizer` / `CompositeAuthorizationManager` |
| 匹配器 | `AnonymousRequestMatcher` / `AbstractAntRequestMatcher`（放行路径声明） |
| 模块异常 advice | `SecurityExceptionAdvice`（`@Order(100)`，1.1.0+） |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-security-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖 `spring-plus-web-starter`、`spring-plus-boot-starter` 与 `spring-boot-starter-security`。

## 用法

```java
// 角色：替代 hasRole('ADMIN')
@RequiresAdminRole
@DeleteMapping("/user/{id}")
public SimpleResponse<Void> delete(@PathVariable Long id) { ... }

// 权限：source + action 自动拼接权限键 "user:delete"
@RequiresPermission(source = "user", action = "delete")
@DeleteMapping("/user/{id}")
public SimpleResponse<Void> delete(@PathVariable Long id) { ... }

// 主体注入
@GetMapping("/me")
public SimpleResponse<UserVO> me(@Principal LoginUser user) {
    return SimpleResponse.ok(convert(user));
}
```

## 机制

- 注解由 `AuthorizationManager` 体系消费（`CompositeAuthorizationManager` 聚合各 `Authorizer`），与 Spring Security 6/7 的授权模型原生集成
- 权限键约定：`source + ":" + action`；`source` 以 `:*` 结尾表示通配前缀
- 业务侧提供权限数据源（实现自己的 `Authorizer` 或用户详情服务），本模块只负责声明与校验的桥接

## 模块级异常 advice（1.1.0+）

`SecurityExceptionAdvice`（`@Order(100)`）是框架多 advice 共存机制的首个落地：SERVLET 应用且 web 错误引擎就位时自动装配，先于全局兜底 `GlobalExceptionAdvice` 被咨询。

- **denied 透传**：`AccessDeniedException` 家族（含 `AuthorizationDeniedException`）原样抛出——403 语义由 Security 的 `ExceptionTranslationFilter` / 方法级 denied handler 翻译。web 侧原 `SECURITY_DENIED_CLASSES` 按类名让路的妥协已删除
- **认证异常翻译**：`AuthenticationException` 家族按子类型映射 web 错误码，经 `ExceptionOutputEngine` 统一渲染为 401（协议探测 / 日志策略 / 已提交补写全部保留）：

| 异常 | 错误码 |
|---|---|
| `BadCredentialsException` | `A0210` 用户密码错误 |
| `LockedException` | `A0202` 用户账户被冻结 |
| `DisabledException` | `A0203` 用户账户已作废 |
| `CredentialsExpiredException` | `A0212` 用户密码已过期 |
| `AccountExpiredException` | `A0213` 用户账户已过期 |
| `SessionAuthenticationException` | `A0230` 用户登录已过期 |
| 其余（含 `InsufficientAuthenticationException`） | `A0301` 访问未授权，请先登录 |

## 已知注意事项

- 注解可以叠加，全部条件为 AND 语义
- `@RequiresPermission` 的 `source` / `action` 均不可为空（运行期断言，空值直接失败）
- 本模块不做认证（登录/token 解析），只做授权声明；认证链路由业务项目的 Security Filter 提供
