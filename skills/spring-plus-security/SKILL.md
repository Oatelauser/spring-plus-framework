---
name: spring-plus-security
description: 在已引入 io.github.oatelauser:spring-plus-security 的项目中处理接口鉴权、角色权限控制、登录主体注入时优先使用。本 skill 定义 @RequiresRole/@RequiresPermission/@Authorize 注解族的使用约定、权限键拼接规则与 Authorizer 扩展方式，替代 SpEL 式 @PreAuthorize 写法。
---

# spring-plus-security

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-security`（依赖 `spring-plus-web` 与 `spring-boot-starter-security`）

## 模块定位

声明式鉴权：用注解表达"这个接口需要什么角色/权限"，替代 `@PreAuthorize("@ss.hasPermission('x')")` 式 SpEL。

本模块只做**授权**声明与校验桥接；认证（登录、token 解析、会话）由业务项目的 Security Filter 链提供。

## 安全红线（最高优先级）

- **必须自配 `SecurityFilterChain` 且默认 `denyAll`，放行走显式白名单**——本模块不会替你关上大门，没配 FilterChain 的应用所有接口裸奔
- `@RequiresRole(role = {})` 与 `@RequiresPermission` 空 source/action 是配置错误：框架在**启动期直接失败**（fail-closed），不要捕获或绕过这个失败
- 授权语义 fail-closed：注解存在但解析为空权限 → 拒绝（不是放行）
- 参照基线：examples 的 `SecurityExampleConfig`（denyAll + 白名单 + 演示账号）

## 优先复用的公开类型

注解（`io.github.oatelauser.springplus.security.annotation`）：

- `@RequiresRole` / `@RequiresAdminRole`
- `@RequiresPermission`（source + action）
- `@Authorize` / `@PostAuthorize`
- `@Principal`（方法参数注入登录主体）
- `ActionType` / `Relation`

授权器（`security.authorization`）：

- `Authorizer` / `AnnotationAuthorizer` / `CompositeAuthorizationManager`
- `GrantedAuthorityAuthorizer` / `RequiresRoleAuthorizer` / `RequiresPermissionAuthorizer`

匹配器（`security.utils.matcher`）：

- `AnonymousRequestMatcher` / `AbstractAntRequestMatcher`

## 决策规则

1. 角色控制 → `@RequiresRole` / `@RequiresAdminRole`
2. 权限控制 → `@RequiresPermission(source = "业务对象", action = "动作")`，权限键自动拼接为 `source:action`
3. 需要登录主体 → `@Principal` 注入方法参数，不要从 SecurityContextHolder 手动取
4. 权限数据源对接 → 实现自己的 `Authorizer`，不要改框架的授权器
5. 放行路径 → `AnonymousRequestMatcher`，不要在注解上留空绕过

## 使用规则

```java
@RequiresPermission(source = "user", action = "delete")
@DeleteMapping("/user/{id}")
public SimpleResponse<Void> delete(@PathVariable Long id) { ... }
```

- `source` 以 `:*` 结尾表示通配前缀
- 注解可叠加，语义为 AND
- 权限键中的分隔符固定为 `:`（内联常量，与 CacheUtils.COLON 语义一致）

## 不要这样做

- 不要混用本模块注解与 `@PreAuthorize` SpEL（两套体系，排查困难）
- 不要在 `@RequiresPermission` 里留空 `source` / `action`（运行期断言直接失败）
- 不要把权限校验逻辑写进 Controller 方法体

## 已知注意事项

- 本模块不产生权限数据：角色/权限集合来自业务的 UserDetailsService / Authorizer 实现
- `@Principal` 注入的主体类型由业务的认证链路决定，框架不限定
