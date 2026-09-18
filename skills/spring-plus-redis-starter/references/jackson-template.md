# jacksonRedisTemplate（Jackson 化 RedisTemplate）

Spring Boot 只自动装配 JDK 序列化的 `redisTemplate` 与 `stringRedisTemplate`——"Jackson 化模板"是 Spring 生态被复制粘贴最多的配置段，本模块补齐这个空档。

## 用法

```java
// 按名注入（不接管 Boot 默认 redisTemplate，存量 JDK 序列化数据零影响）
private final RedisTemplate<String, Object> jacksonRedisTemplate;

jacksonRedisTemplate.opsForValue().set("user:1", user);        // 序列化为 JSON，内嵌 @class
User back = (User) jacksonRedisTemplate.opsForValue().get("user:1");   // 读回即对象
```

- String key + Jackson value（hash 同构）一把装配；自动配置在 `SpringPlusRedisAutoConfiguration`
- 容器存在 `JsonMapper` Bean 时自动继承其约定（与 web 响应同源：日期格式等家族一致），无则回退自建
- classpath 无 Jackson 时该 Bean 整体退避

## 消掉的三类经典手写错误

1. **忘嵌类型信息**：Object 值读回 `LinkedHashMap` 抛 `ClassCastException` → 本模板以 `DefaultTyping.NON_FINAL` 在 JSON 内嵌 `@class`，round-trip 安全
2. **共享 mapper 被污染**：把容器 `JsonMapper` 直接交给序列化器会激活 default typing，连 HTTP 响应都开始输出 `@class` → 本模板一律 `rebuild()` 副本配置，容器 mapper 分毫不动（有测试钉死）
3. **序列化器拼装错漏**：key/value/hash 的序列化器组合容易漏配 → 工厂一把装配

## 安全红线

家族缺省的 `PolymorphicTypeValidator` 信任域与 Spring Data Redis 缺省一致 = **本应用写入的 Redis**。

- Redis 可被不可信方写入（共用实例、外部可写）时，必须自行收紧：

```java
@Bean
public RedisTemplate<String, Object> myJacksonTemplate(RedisConnectionFactory factory) {
    GenericJacksonJsonRedisSerializer serializer =
            RedisJacksonTemplates.jacksonValueSerializer(null);   // 或传入自建 JsonMapper
    // 在 serializer 上配置收紧的 PolymorphicTypeValidator 后，用
    // RedisJacksonTemplates.jacksonRedisTemplate(connectionFactory, ...) 组装
}
```

- 无约束多态反序列化（`@class` 任意类）面对不可信数据是**任意代码执行**风险，不是理论问题

## 与 StringRedisTemplate 的分工

| | jacksonRedisTemplate | StringRedisTemplate + RedisStringOperation |
|---|---|---|
| 值 | JSON 对象（@class 内嵌） | 纯 String |
| 场景 | 对象缓存、跨服务共享 | 计数器、标记、跨语言消费、省体积 |
| 原子操作 | 常规 opsForXxx | Lua 门面（批量/自增过期） |

两个模板各司其职，不要互相替代。
