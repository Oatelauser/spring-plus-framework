package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;

/**
 * 协议专属渲染参数的 sealed family（v3.0：由 descriptor 包迁入 output 包并收窄）。
 * <p>
 * <b>归属变更的理由</b>：hint 的生产者是 {@code AnnotationToTemplateConverter} 的内置
 * hint 工厂表，消费者是各协议 {@code ExceptionOutputProcessor}——两端都在输出层，放
 * descriptor 包（业务可见的纯模型层）名不副实。迁到 output 后，descriptor 只保留一个
 * {@code ErrorHint} 入口引用，协议渲染参数完全内聚在输出层。
 * <p>
 * <b>收窄的理由</b>（v3.0）：原 {@code JsonErrorHint} 携带的 {@code httpStatus} 已升维为
 * 协议无关的 {@code ErrorDescriptor.statusIntent}——JSON/NDJSON 不再有任何协议专属渲染参数，
 * 密封家族只剩 {@link SseErrorHint}（event/retry 是 SSE 事件帧的真实字段）。
 * <p>
 * sealed 的价值：新协议要加专属渲染参数时，必须显式新增 record 加入 permits——编译器强制
 * 全链路（工厂表 + 处理器下钻）同步更新，杜绝「悄悄加了个没人消费的 hint」。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public sealed interface ErrorHint permits SseErrorHint {

    /**
     * 本 hint 服务哪个协议——用于 {@code AnnotationToTemplateConverter} 的工厂表路由
     * （{@code HINT_FACTORIES.get(protocol)}）。
     */
    OutputProtocol protocol();
}
