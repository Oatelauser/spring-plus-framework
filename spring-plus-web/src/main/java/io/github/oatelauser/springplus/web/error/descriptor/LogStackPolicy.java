package io.github.oatelauser.springplus.web.error.descriptor;

/**
 * 异常日志堆栈打印策略。
 * <p>
 * v2.0 把「这个异常要不要打堆栈」从每个 {@code @ExceptionHandler} 方法里手写日志代码，
 * 收口成一个四级决策（设计文档 5.3）：
 * <pre>
 *   ErrorDescriptor.logPolicyPolicy（注解 / Mapper 设置）
 *      &gt; ServiceException.shouldRecordStack() 显式开关
 *      &gt; 全局配置项 (web.error.log.print-stack-for-*)
 *      &gt; 静态分类表（LogPolicyTable）
 * </pre>
 * 本枚举是第一级、也是优先级最高的一级：业务方在注解上显式声明后，直接覆盖后面三级。
 * <p>
 * <b>设计原则</b>（10.1）：一眼看得出原因的异常 → 简单输出，不打堆栈；难以排查的异常 → 打完整堆栈。
 * 参数校验失败、业务异常这类「栈顶就是 throw 那行」的异常打堆栈价值不大，反而污染日志；
 * 数据库系统错误、兜底 Exception 这类需要堆栈定位根因。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public enum LogStackPolicy {

    /**
     * 默认策略：跟随框架静态分类表（{@code LogPolicyTable}）。
     * <p>
     * 99% 的异常用这个值——业务不显式声明时，框架按异常类型查表自动决定级别和是否打堆栈。
     * 这是 {@code @ExceptionResponse.logPolicy} 的默认值，保证 v1.0 行为可平滑迁移。
     */
    DEFAULT,

    /**
     * 强制打堆栈。无论静态分类表怎么说，都把完整堆栈写入日志。
     * <p>
     * 典型场景：某个业务异常默认不打堆栈，但线上偶发需要定位时，临时用
     * {@code @ExceptionResponse(logPolicy = ALWAYS)} 强制打开。
     */
    ALWAYS,

    /**
     * 强制不打堆栈。无论静态分类表怎么说，都只输出一行摘要日志。
     * <p>
     * 典型场景：SSE 流内的 {@code TimeoutException}——客户端会自动重连，打堆栈噪音大于价值，
     * 用 {@code @SseExceptionResponse(logPolicy = NEVER)} 关掉。
     */
    NEVER
}
