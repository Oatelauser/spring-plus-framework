---
name: spring-plus-security-starter
description: spring-plus-framework 的声明式鉴权能力使用约定（坐标 io.github.oatelauser:spring-plus-security-starter）。覆盖：@RequiresRole/@RequiresAdminRole 角色控制、@RequiresPermission(source,action) 权限键自动拼接、@Authorize/@PostAuthorize 指定 Authorizer、@RequiresNonLogin 匿名放行、@Principal 登录主体注入、Authorizer/AnnotationAuthorizer 权限数据源 SPI、超管 SUPER_ADMIN 短路、AnonymousRequestMatcher 白名单匹配器、SecurityFilterChain 必须 denyAll 自配的红线、SecurityExceptionAdvice 401/403 错误码渲染。给接口加角色/权限校验、替代 @PreAuthorize SpEL 写法、对接权限数据源、排查 401 403 无权限问题时使用。统一响应/异常在 spring-plus-web-starter。
---

# spring-plus-security-starter

这个 skill 直接给 AI 使用。本文件是导航与全局规则；API 细节与示例在 `references/`。

**模块边界**：本模块只做**授权**（注解声明"需要什么角色/权限"+ 校验桥接）；**认证**（登录、token 解析、会话）由业务项目的 Security Filter 链提供。

## 按需加载参考文档

| 任务涉及 | 加载 |
|---|---|
| 注解用法：角色/权限/通用授权/主体注入/匿名放行、权限键拼接、AND 语义 | [references/annotations.md](references/annotations.md) |
| 对接权限数据源：实现 Authorizer SPI、@Authorize 指定、超管短路机制 | [references/authorizer.md](references/authorizer.md) |
| SecurityFilterChain 基线、白名单匹配器、401/403 渲染（SecurityExceptionAdvice） | [references/filter-chain-and-advice.md](references/filter-chain-and-advice.md) |

## 安全红线（最高优先级，先于一切规则）

1. **必须自配 `SecurityFilterChain` 且默认 `denyAll`，放行走显式白名单**——本模块不替你关大门；没配 FilterChain 的应用所有接口裸奔（贴几个注解 ≠ 安全）
2. `@RequiresRole(role = {})` 与 `@RequiresPermission` 空 source/action 是配置错误：**启动期直接失败**（fail-closed），不要捕获或绕过
3. 授权语义 fail-closed：注解存在但解析为空权限 → 拒绝（不是放行）
4. 基线参照：examples 的 `SecurityExampleConfig`（denyAll + 白名单 + 演示账号）

## 核心决策规则

1. 角色控制 → `@RequiresRole(role = "USER")`；超管端点 → `@RequiresAdminRole`（元注解，归并为 `ROLE_SUPER_ADMIN`）
2. 权限控制 → `@RequiresPermission(source = "user", action = "delete")`，权限键自动拼接 `user:delete`
3. 登录主体 → `@Principal` 注入方法参数，不从 `SecurityContextHolder` 手动取
4. 权限数据源对接 → 实现自己的 `Authorizer`（或用默认的 `GrantedAuthorityAuthorizer`），不改框架授权器
5. 放行路径 → `AnonymousRequestMatcher` 声明白名单，不在注解上留空绕过

## 不要这样做

- 不混用本模块注解与 `@PreAuthorize` SpEL（两套体系，排查困难；存量 SpEL 迁移时整体替换）
- 不把权限校验逻辑写进 Controller 方法体
- 不在 `@RequiresPermission` 里留空 `source`/`action`
- 已认证但无权限是 403、未认证是 401——排查时先分清这两类，别急着改注解（见 filter-chain-and-advice.md 渲染表）
