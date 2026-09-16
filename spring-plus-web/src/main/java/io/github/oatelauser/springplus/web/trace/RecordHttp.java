package io.github.oatelauser.springplus.web.trace;

import io.github.oatelauser.springplus.web.trace.LoggingHttpTraceFilter;

import java.lang.annotation.*;

/**
 * 启用 HTTP 请求与响应报文的日志记录。
 *
 * <p>可标注在以下位置：
 * <ul>
 *   <li><b>Controller 类</b> — 对该控制器下所有接口方法生效</li>
 *   <li><b>接口方法</b> — 仅对该方法生效（方法级优先于类级）</li>
 *   <li><b>其他注解</b> — 作为元注解组合使用，实现语义化的派生注解</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <p><b>1. 标注在类上（类下所有方法均记录）</b>
 * <pre>{@code
 * @RestController
 * @RecordHttp
 * public class OrderController {
 *     // ...
 * }
 * }</pre>
 *
 * <p><b>2. 标注在方法上（仅该方法记录）</b>
 * <pre>{@code
 * @RestController
 * public class OrderController {
 *
 *     @RecordHttp
 *     @PostMapping("/orders")
 *     public Order create(@RequestBody OrderRequest request) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p><b>3. 作为元注解组合使用</b>
 * <pre><code>
 * &#64;Target({ElementType.METHOD, ElementType.TYPE})
 * &#64;Retention(RetentionPolicy.RUNTIME)
 * &#64;RecordHttp
 * public &#64;interface AuditApi {
 * }
 * </code></pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-24
 * @see LoggingHttpTraceFilter
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
public @interface RecordHttp {
}
