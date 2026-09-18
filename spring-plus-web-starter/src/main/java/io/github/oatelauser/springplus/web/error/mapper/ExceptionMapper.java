package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import org.jspecify.annotations.Nullable;

/**
 * 可插拔异常翻译器 SPI（设计文档第 6 章）。
 * <p>
 * {@code ExceptionMapper} 的定位是<b>「翻译器，不是渲染器」</b>——它把「未识别的异常」
 * 翻译成「系统认识的错误语义」（{@link ErrorDescriptor}），翻译完之后渲染由协议层
 * （{@code ExceptionRenderer}）接管。Mapper 永远返回协议无关的 {@link ErrorDescriptor}，
 * 不返回 {@code SimpleResponse<?>} 也不返回 {@code Object}（设计文档 3.3）。
 *
 * <h3>三大扩展点里它的位置</h3>
 * <p>
 * v2.0 有四条异常分类路径，按优先级（设计文档 5.2）：
 * <pre>
 *   P0  方法/类级 @ExceptionResponse 注解（ExceptionResponseAnnotationProcessor）
 *   &gt; P1  @ExceptionHandler 调用方传入的 defaultDescriptor
 *   &gt; P2  ExceptionMapper 链          ← 本接口属于这一层
 *         ├─ ExceptionClassAnnotationMapper   （异常类上的 @ExceptionResponse）
 *         ├─ 业务 / 中间件自定义 Mapper        （本接口的业务实现）
 *         ├─ ServerStatusMapper
 *         └─ DefaultExceptionMapper       （兜底）
 * </pre>
 * 只有 P0/P1 都没命中时，Mapper 链才被遍历。
 *
 * <h3>谁来实现</h3>
 * <ul>
 *   <li><b>框架内置</b>：三个 Mapper（见 {@code ExceptionClassAnnotationMapper} 等）。</li>
 *   <li><b>第三方 / 中间件 starter</b>：例如 Sentinel 的 {@code BlockException}、
 *       限流 SDK 的 {@code RateLimitException}，由对应 starter 注册自己的 Mapper，
 *       而不是硬编码进框架。</li>
 *   <li><b>业务模块</b>：模块自定义的异常翻译规则（设计文档 10.1 迁移指引）。</li>
 * </ul>
 *
 * <h3>返回 null 的语义</h3>
 * <p>
 * {@code map(...)} 返回 {@code null} 表示「这个异常我不管」，让 {@code ExceptionMapperChain}
 * 去问链上下一个 Mapper。这是 SPI 的核心协作机制——多个 Mapper 用 {@code null} 串联成责任链。
 *
 * <h3>order 语义</h3>
 * <p>
 * {@link #order()} 数值越小优先级越高，与 {@code @ExceptionResponse} 的 {@code order} 字段语义一致。
 * 约定：内置 Mapper 用负数（{@code ExceptionClassAnnotationMapper} = -1000，
 * {@code ServerStatusMapper} = -900），业务 / 第三方建议用正数（0~999），
 * {@code DefaultExceptionMapper} 用 {@code Integer.MAX_VALUE} 兜底。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 * @see ExceptionMapperContext
 */
public interface ExceptionMapper {

    /**
     * 把异常翻译为 {@link ErrorDescriptor}。
     * <p>
     * 引擎（{@code ExceptionMapperChain}）会把异常因果链上的每个节点依次喂给链上的每个 Mapper，
     * 谁先返回非 null 谁就赢。
     *
     * @param ex  异常（已经是因果链上的某个节点，由引擎遍历传入，本方法不需要自己再爬 cause）
     * @param ctx 包含 request / handlerMethod / protocol 等上下文
     * @return 翻译出的错误描述；{@code null} 表示「我不管」，让下一个 Mapper 处理
     */
    @Nullable
    ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx);

    /**
     * 优先级：数值越小越先被调用。
     * <p>
     * 默认 0，建议业务 Mapper 显式声明一个正数 order 以便与内置 Mapper 区分。
     *
     * @return 顺序值
     */
    default int order() {
        return 0;
    }
}
