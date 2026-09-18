package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ErrorHint;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * <b>框架内部类，业务方请勿直接使用</b>。启动期 / 首次访问时计算的「错误描述半成品」（设计文档 3.4）。
 * <p>
 * {@link ErrorDescriptor} 是<b>运行期</b>对象（每次异常都要构造一个，且依赖具体异常实例做占位符替换）；
 * 而 {@code ErrorDescriptorTemplate} 是<b>启动期</b>对象——它把注解里所有「与具体异常实例无关」的静态信息
 * （code、异常类型清单、order、协议集合、statusIntent、bodyCustomizer Bean 实例、{@link ErrorHint}、
 * logPolicy）预先计算好缓存起来。运行期命中注解时，只需要把异常实例代入 {@link #materialize(Throwable)}
 * 做一次占位符替换（且无占位符时连 replace 都跳过），就得到完整的 {@link ErrorDescriptor}。
 *
 * <h3>v3.0 改造点</h3>
 * <ul>
 *   <li><b>自持 {@code exceptionTypes} / {@code order}</b>：v2 里这两个值存在规则
 *       （{@code MethodExceptionRule}）上，且各扫描方法重复读注解属性。v3 模板成为注解的
 *       完整固化镜像，扫描器一次 {@code convert} 即得全部信息——「注解 → 模板」单一职责。</li>
 *   <li><b>{@code statusIntent}</b>：原协议 hint 里的 httpStatus 升维为协议无关字段
 *       （OK 归一化为 null = 未指定）。</li>
 *   <li><b>{@code bodyCustomizer}</b>：原 {@code outputBase} 更名。</li>
 *   <li>由 descriptor 包迁入 engine 包——它只被引擎消费，不是业务可见模型。</li>
 * </ul>
 *
 * <h3>为什么要这一层（运行期零反射）</h3>
 * <ul>
 *   <li>注解属性读取：启动期一次性，运行期不碰注解。</li>
 *   <li>bodyCustomizer 实例化：启动期 getBean 一次，运行期直接持有实例引用。</li>
 *   <li>ErrorHint 提取：启动期由 hint 工厂解析一次，运行期直接持有 hint。</li>
 *   <li>占位符预判：启动期判断 messageTemplate 是否含 {@code {exception}}，运行期用
 *       {@link #hasPlaceholder} 短路。</li>
 * </ul>
 *
 * <h3>{@link #NONE} 哨兵</h3>
 * <p>
 * 扫描某异常类发现没有任何 {@code @ExceptionResponse} 家族注解时，缓存 {@link #NONE} 而非 null，
 * 避免每次都要用 {@code containsKey} 区分「没扫过」和「扫了但没注解」——查表一次 {@code Map.get} 搞定。
 * 调用方（异常类映射层）必须先判 {@code template == NONE} 再走 {@link #matchesProtocol}：
 * NONE 的协议集合为空（语义「全协议」），不能进入规则匹配。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public final class ErrorDescriptorTemplate {

    /**
     * 「扫描过但没有注解」哨兵：所有字段保持默认值，调用方先于 matchesProtocol 判本值。
     */
    static final ErrorDescriptorTemplate NONE = new ErrorDescriptorTemplate();

    /** 注解声明的异常类型清单（{@code value()}，桥接后的合并视图值）。 */
    Class<? extends Throwable>[] exceptionTypes;

    /** 注解显式 order（0 = 未指定，由扫描层以层级默认值顶替）。 */
    int order;

    /** 错误码（业务码体系 A0xxx / B0xxx / C0xxx）。 */
    String code;

    /** 消息模板，可能含 {@code {exception}} / {@code {exceptionClass}} 占位符。 */
    String messageTemplate;

    /** 启动期预判：messageTemplate 是否含占位符。false 时运行期跳过 {@code String.replace}（性能优化）。 */
    boolean hasPlaceholder;

    /** 是否把异常信息直接作为 message（忽略 messageTemplate）。 */
    boolean showException;

    /** 结构化补充信息（注解层面一般不设，由 Mapper / handler 动态填）。默认空。 */
    Map<String, Object> details = Map.of();

    /**
     * 协议过滤集合。空集合 = 全协议通用（兼容 v1.0）。
     * 来自 {@code @ExceptionResponse.protocols()} 或派生注解锁死的协议。
     */
    Set<OutputProtocol> protocols = Set.of();

    /**
     * 真实 HTTP 状态码意图（v3.0：{@code httpStatus != OK} 的归一化值，OK → null = 未指定）。
     */
    @Nullable HttpStatus statusIntent;

    /** 启动期 getBean 拿到的自定义响应体实例；null 表示走默认 {@code SimpleResponse}。 */
    @Nullable ExceptionBodyCustomizer bodyCustomizer;

    /** 日志堆栈策略，默认 {@link LogStackPolicy#DEFAULT}。 */
    LogStackPolicy logPolicy = LogStackPolicy.DEFAULT;

    /**
     * 协议专属渲染参数（sealed family）。
     * <p>
     * 启动期由 {@code AnnotationToTemplateConverter} 的 hint 工厂表（{@code HINT_FACTORIES}）解析，
     * null 表示「业务方未指定协议专属参数」（如普通 {@code @ExceptionResponse} 通吃所有协议），
     * 运行期由处理器用本协议缺省兜底。
     */
    @Nullable ErrorHint errorHint;

    /**
     * 协议过滤：协议集合为空（全协议通用）或包含当前协议时命中。
     *
     * @param p 当前请求探测出的协议
     * @return 是否在该协议下命中
     */
    public boolean matchesProtocol(OutputProtocol p) {
        return protocols.isEmpty() || protocols.contains(p);
    }

    /**
     * 模板 + 异常实例 → 完整 {@link ErrorDescriptor}。
     * <p>
     * 这是运行期热路径，<b>零反射</b>，只做一次字符串判断（按 {@link #hasPlaceholder} 短路）。
     * <p>
     * 消息解析优先级：
     * <ol>
     *   <li>{@link #showException} 为 true：直接用异常的 localizedMessage（兜底用类名）。</li>
     *   <li>含占位符：把 {@code {exception}} 替换为异常消息、{@code {exceptionClass}} 替换为类名。</li>
     *   <li>否则：原样使用 {@link #messageTemplate}。</li>
     * </ol>
     *
     * @param ex 触发本次错误的异常实例
     * @return 完整的、已规范化的错误描述对象
     */
    public ErrorDescriptor materialize(Throwable ex) {
        String msg;
        if (showException) {
            msg = StringUtils.hasText(ex.getLocalizedMessage()) ?
                    ex.getLocalizedMessage() : ex.getClass().getSimpleName();
        } else if (hasPlaceholder) {
            msg = messageTemplate.replace("{exception}", nullSafe(ex.getLocalizedMessage()))
                    .replace("{exceptionClass}", ex.getClass().getSimpleName());
        } else {
            msg = messageTemplate;
        }
        return ErrorDescriptor.builder()
                .code(code)
                .message(msg)
                .error(ex)
                .details(details)
                .logPolicy(logPolicy)
                .statusIntent(statusIntent)
                .hint(errorHint)
                .bodyCustomizer(bodyCustomizer)
                .build();
    }

    /** null 安全的字符串化，避免 {@code replace} 收到 null 抛 NPE。 */
    private static String nullSafe(@Nullable String s) {
        return s != null ? s : "";
    }
}
