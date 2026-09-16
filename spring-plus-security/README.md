# spring-plus-security

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

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖 `spring-plus-web` 与 `spring-boot-starter-security`。

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

## 已知注意事项

- 注解可以叠加，全部条件为 AND 语义
- `@RequiresPermission` 的 `source` / `action` 均不可为空（运行期断言，空值直接失败）
- 本模块不做认证（登录/token 解析），只做授权声明；认证链路由业务项目的 Security Filter 提供
