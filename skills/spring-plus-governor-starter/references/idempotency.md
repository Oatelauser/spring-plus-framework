# 幂等与防重复提交注解

包：`io.github.oatelauser.springplus.governor.annotation`（机制在 `governor.idempotent`）。

## @Idempotent（幂等提交）

同一业务键在时间窗内**仅首次生效**（创建订单、发起支付、触发对账——重复请求返回首次语义的拒绝）：

```java
@Idempotent                                                // 默认窗口 60s，参数指纹键
@PostMapping("/order/create")
public SimpleResponse<Void> create(@Valid @RequestBody CreateOrderCmd cmd) { ... }

@Idempotent(window = 5, unit = TimeUnit.MINUTES,
            express = "#cmd.orderNo")                       // SpEL 取业务单号作键
@PostMapping("/order/settle")
public SimpleResponse<Void> settle(@Valid @RequestBody SettleCmd cmd) { ... }
```

属性：

| 属性 | 默认 | 说明 |
|---|---|---|
| `window` / `unit` | `60` / `SECONDS` | 幂等窗口 |
| `strategy` | `FingerprintKeyStrategy.class` | 键策略（见 storage-and-strategy.md） |
| `express` | `""` | SpEL 参与键提取，如 `#orderId`、`#userId + ':' + #deviceId`；对 `TokenKeyStrategy` 必填，对指纹策略可选（不填按全部参数生成指纹） |
| `maxCacheBytes` | `16384` | 指纹计算的请求体上限（防超大 body 拖慢哈希） |

## @RepeatSubmit（防重复提交）

同一键在短窗内的重复请求**直接拒绝**（表单防抖、防连点）：

```java
@RepeatSubmit                                          // 默认窗口 5s
@PostMapping("/pay")
public SimpleResponse<Void> pay(@Valid @RequestBody PayCmd cmd) { ... }

@RepeatSubmit(window = 10, releaseOnFailure = false, spel = "#cmd.token()")
@PostMapping("/sms/send")
public SimpleResponse<Void> send(@Valid @RequestBody SmsCmd cmd) { ... }
```

| 属性 | 默认 | 说明 |
|---|---|---|
| `window` / `unit` | `5` / `SECONDS` | 防重窗口 |
| `releaseOnFailure` | `true` | 业务**异常**时是否释放防重 key。默认 true（兼容历史）：失败后允许立刻重试。**防刷场景设 false**：验证码发送失败也不许短窗内重发（V19） |
| `strategy` / `spel` | 指纹 / `""` | 同 @Idempotent |

## 两者怎么选

| | @Idempotent | @RepeatSubmit |
|---|---|---|
| 语义 | 首次生效，重复请求按幂等冲突处理 | 重复请求直接拒绝 |
| 典型场景 | 下单/支付/结账（钱和货） | 表单防抖/防刷（体验） |
| 窗口 | 分钟级 | 秒级 |
| 失败释放 | — | 可配 `releaseOnFailure` |

## 拒绝时的响应

幂等/防重命中拒绝时抛 `ServiceException`（`ClientStatus.REPEAT_SUBMIT` 语义），由 web 模块全局异常体系统一渲染（JSON/SSE/NDJSON 同构）。要自定义响应文案/错误码：按 web 模块的异常映射体系声明（注解贴异常类 / ExceptionMapper），**不要**在业务方法里 try/catch 拦截器行为。

## SpEL 求值约束（V15）

- 只读 `SimpleEvaluationContext`：`T()` 类型引用、`new` 构造、方法调用**一律拒绝**——表达式只做属性读取与字符串拼接
- 表达式解析结果有缓存，高频路径无重复解析开销
- SpEL 求值为空/空白时按策略处理：`TokenKeyStrategy` 直接抛 `ServiceException`（fail-closed），不静默回落
