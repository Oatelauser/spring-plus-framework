# 键策略与存储（幂等的两个底层维度）

包：`io.github.oatelauser.springplus.governor.idempotent`。

## 最终键格式

拦截器把策略产物统一封装：

```
idempotent:<strategyName>:<sha256(rawValue)>
```

`rawValue` 由策略的 `extract(...)` 返回（不含前缀与哈希），所以策略实现只关心"提取业务唯一标识"。

## 键策略（IdempotentKeyStrategy）

```java
@FunctionalInterface
public interface IdempotentKeyStrategy {
    String extract(String spelRaw, String spelValue, MethodInvocation invocation);
    default String strategyName() { ... }   // 键前缀分段，默认类名
}
```

内置两个策略（注解 `strategy` 属性选择）：

| 策略 | 行为 | 适用 |
|---|---|---|
| `FingerprintKeyStrategy`（默认） | SpEL 可选：不填按**全部参数**生成指纹；填了指纹 = SpEL 值 + 参数指纹 | 大多数场景 |
| `TokenKeyStrategy` | 直接用 SpEL 值作 rawValue；**SpEL 必填**（空则抛 `ServiceException`，fail-closed） | 按业务单号/令牌幂等 |

### 自定义策略（多租户等）

```java
@Component   // 注册为 Bean，KeyStrategyResolver 自动收集；注解 strategy = TenantKeyStrategy.class 指定
public class TenantKeyStrategy implements IdempotentKeyStrategy {

    @Override
    public String extract(String spelRaw, String spelValue, MethodInvocation invocation) {
        // 例如：租户 ID + SpEL 业务键，隔离不同租户的幂等域
        return TenantContext.currentTenantId() + ":" + spelValue;
    }

    @Override
    public String strategyName() { return "tenant"; }   // 键前缀分段 idempotent:tenant:...
}
```

规则：扩展走实现接口 + 注册 Bean；不改拦截器、不改注解默认值。

## 存储（IdempotentStore）

| 存储 | 生效条件 | 适用 |
|---|---|---|
| `RedisIdempotentStore` | classpath 存在 `StringRedisTemplate` 时自动启用 | **集群部署必选**（跨实例幂等） |
| `InMemoryIdempotentStore` | 无 Redis 时兜底 | 单实例 |

- 探测是运行时按类名反射，模块无 Redis 依赖也能安全加载
- `spring-data-redis` 在本模块是 **provided**——集群部署的项目必须自己引入（传递依赖里没有）
- `InMemoryIdempotentStore` 容量默认 10 万条（构造器可调），满载 **fail-closed**：拒绝新 key（对应请求失败），防内存无界增长——看到"容量满"相关报错先查窗口是否过长/键维度是否过细

## 部署形态对照

| 部署 | 存储 | 键策略底线 |
|---|---|---|
| 单实例 | 内存兜底可接受 | 默认指纹即可 |
| 集群 | 必须 Redis | 默认指纹即可 |
| 集群 + 多租户/共用出口 IP | 必须 Redis | 必须自定义策略（租户/主体维度），否则 NAT 下互相误杀 |
