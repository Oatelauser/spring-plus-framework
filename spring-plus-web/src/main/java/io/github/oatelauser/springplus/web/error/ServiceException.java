package io.github.oatelauser.springplus.web.error;

import io.github.oatelauser.springplus.web.response.ServerStatus;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

/**
 * 业务逻辑异常
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2024-03-18
 * @since 1.0
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class ServiceException extends RuntimeException implements ServerStatus {

    final String code;
    final String message;

    /**
     * 响应码
     */
    HttpStatus responseStatus = HttpStatus.OK;

    /**
     * 业务方是否通过 {@link #withStack()} 显式要求日志打印堆栈（v2.1 语义修复）。
     * <p>
     * 默认 {@code false}：业务异常按静态分类表走 WARN 不打堆栈（栈顶就是 {@code throw} 那行，
     * 价值不大还污染日志）；线上偶发需要定位根因时在抛出处链式开启。
     * <p>
     * 「构造时是否捕获堆栈」<b>不是</b>字段，而是类型多态——{@link Signal} 子类覆盖
     * {@code fillInStackTrace()} 返回自身（零捕获成本），普通实例走 {@link Throwable}
     * 默认捕获。不用布尔字段做捕获开关的原因：{@code fillInStackTrace()} 在
     * {@code Throwable} 构造器内被调用，彼时子类字段初始化器尚未执行，字段值恒为默认
     * {@code false}——v2.0 的 {@code recordStack} 字段正是踩了这个构造时序坑，导致
     * 所有实例实际都未捕获堆栈，同时 {@code DefaultExceptionLogger} 又把该字段（构造完成后
     * 为 true）误读成「显式要求打堆栈」，双重架空了日志决策链（v2.1 一并修复）。
     * <p>
     * 用 {@link AccessLevel#NONE} 排除 Lombok 的默认 getter，避免生成 {@code isStackRequested()}
     * 这种 {@code is} 前缀访问器（阿里规约：布尔属性禁止 is 前缀）。
     */
    @Getter(AccessLevel.NONE)
    boolean stackRequested = false;

    public ServiceException(ServerStatus status) {
        this(status.getCode(), status.getMessage());
    }

    public ServiceException(ServerStatus status, Throwable cause) {
        this(status.getCode(), status.getMessage(), cause);
    }

    public ServiceException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ServiceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    public ServiceException(String message, Throwable cause) {
        this(SERVER_INTERNAL_CODE, message, cause);
    }

    public ServiceException(String message) {
        this(SERVER_INTERNAL_CODE, message);
    }

    public ServiceException status(HttpStatus status) {
        this.responseStatus = status;
        return this;
    }

    /**
     * 链式开启堆栈打印（设计文档 4.3）。
     * <p>
     * 业务异常默认不打堆栈；线上偶发需要定位根因时，抛出处临时改写：
     * <pre>{@code
     * throw new ServiceException(BusinessStatus.DATA_DUPLICATE).withStack();
     * }</pre>
     * 该开关在日志决策里优先级仅次于 {@code ErrorDescriptor.logPolicy}（注解 / Mapper 显式声明），
     * 高于全局配置和静态分类表（设计 10.3）。
     *
     * @return this，便于链式调用
     */
    public ServiceException withStack() {
        this.stackRequested = true;
        if (this instanceof Signal signal) {
            return new ServiceException(signal.code, signal.message)
                    .status(signal.responseStatus)
                    .withStack();
        }
        return this;
    }

    /**
     * 仅仅是信号通知，中断程序的ServiceException异常，避免异常堆栈的性能损耗。
     * <p>
     * 返回的 {@link Signal} 实例不捕获堆栈（构造零成本）；后续若又需要堆栈，
     * 链式 {@link #withStack()} 会换回普通实例并保留 code / message / 响应码。
     */
    public ServiceException signal() {
        if (this instanceof Signal signal) {
            return signal;
        }
        return new Signal(code, message, responseStatus);
    }

    /**
     * 是否应该打印堆栈（设计文档 4.3）。
     * <p>
     * 由 {@code DefaultExceptionLogger} 在日志决策时读取，优先级高于全局配置与静态分类表。
     * 只反映业务方的显式要求（{@link #withStack()}），与「构造时是否捕获堆栈」无关——
     * 捕获了不打是常态（静态分类表默认不打），显式要求但没捕获（对 {@code signal()} 链式
     * {@code withStack()} 会自动换回普通异常）不会出现。
     *
     * @return {@code true} 表示业务方通过 {@link #withStack()} 显式要求打堆栈
     */
    public boolean shouldRecordStack() {
        return this.stackRequested;
    }

    private static final class Signal extends ServiceException {
        private Signal(String code, String message, HttpStatus responseStatus) {
            super(code, message);
            this.responseStatus = responseStatus;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

}
