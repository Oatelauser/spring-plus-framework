package io.github.oatelauser.springplus.example.output;

import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义响应体定制器：模拟「微信回调要求 errcode/errmsg」格式（v3.0 UC-6 验证）。
 * <p>
 * 验证 {@link ExceptionBodyCustomizer#transform(ExceptionContext)} 的能力——业务方对接第三方时，
 * 必须按对方协议返回 {@code {"errcode":..., "errmsg":...}}（而非框架默认的 {@code SimpleResponse}）。
 * 启动期由 {@code AnnotationToTemplateConverter.resolveBodyCustomizer()} 通过容器
 * {@code getBean(...)} 取本 Bean 并固化进 {@code ErrorDescriptorTemplate}，运行期纯方法调用、
 * 零反射（设计 5.6 / 5.7 / 12.3.1）。
 *
 * <h3>关键点</h3>
 * <ul>
 *   <li><b>必须是 Bean</b>：注解 {@code output = WechatStyleOutput.class} 启动期通过
 *       {@code ApplicationContext.getBean} 取实例；找不到 fail-fast。</li>
 *   <li>使用 {@link LinkedHashMap} 保留 errcode/errmsg 的输出顺序，保证序列化稳定。</li>
 *   <li>返回 null 表示「我不接管」，框架回退默认 {@code SimpleResponse} 渲染。</li>
 * </ul>
 *
 * <h3>预期响应（UC-6）</h3>
 * <pre>
 *   HTTP 200 + JSON
 *   {"errcode":-1, "errmsg":"签名校验失败"}
 *   ↑ 注意：完全没有 SimpleResponse 结构（code/message/data/details）
 * </pre>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@Component
public class WechatStyleOutput implements ExceptionBodyCustomizer {

    @Nullable
    @Override
    public Object transform(ExceptionContext ctx) {
        // 把 v2.0 业务码（A0xxx/B0xxx）转换为微信约定的负数 errcode。
        // 这里简化为：所有错误统一返回 errcode=-1，业务可按需细分。
        Map<String, Object> wechat = new LinkedHashMap<>(2);
        wechat.put("errcode", mapToWechatCode(ctx.code()));
        wechat.put("errmsg", ctx.message());
        return wechat;
    }

    /** v2.0 业务码 → 微信 errcode 的简化映射（演示用）。 */
    private static int mapToWechatCode(String v2Code) {
        if (v2Code == null) {
            return -1;
        }
        // 实际项目里应做完整的码表映射；这里只演示「能转换」即可。
        return switch (v2Code) {
            case "00000" -> 0;
            default -> -1;
        };
    }
}
