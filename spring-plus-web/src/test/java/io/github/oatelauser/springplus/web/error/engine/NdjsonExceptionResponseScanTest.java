package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.annotation.JsonExceptionResponse;
import io.github.oatelauser.springplus.web.error.annotation.NdjsonExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code @NdjsonExceptionResponse}（v3.0）的引擎接线断言：
 * 协议探测规则 1 命中 NDJSON、P0 注解解析带上 bodyCustomizer / statusIntent、协议过滤器排除
 * 其他协议、多派生注解冲突回退。
 * <p>
 * 断言原则（防「哑巴注解」）：所有断言都以「注解的专属值生效」为准——code/msg 是注解值而非
 * P2 兜底值、bodyCustomizer 是注册的 Bean 实例、状态码经协议无关的 {@code statusIntent} 携带。
 * <p>
 * 另含两条 v3.0 守护测试（设计文档 Q14a / Q7a）：
 * <ul>
 *   <li>{@code protocols} 直设必须启动失败（防静默哑巴注解）；</li>
 *   <li>业务自定义派生注解（派生-of-派生）零改动进入协议探测与规则扫描。</li>
 * </ul>
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 3.0
 */
class NdjsonExceptionResponseScanTest {

    /**
     * 自定义 output Bean：微信 errcode/errmsg 风格。必须是 Bean——
     * {@code AnnotationToTemplateConverter.resolveBodyCustomizer} 启动期 {@code getBean} 实例化。
     */
    static class StubWechatOutput implements ExceptionBodyCustomizer {

        @Override
        public Object transform(ExceptionContext ctx) {
            Map<String, Object> body = new LinkedHashMap<>(2);
            body.put("errcode", -1);
            body.put("errmsg", ctx.message());
            return body;
        }
    }

    /**
     * 测试夹具控制器：仅静态方法 + 注解，不需要真实路由。
     */
    static class Demo {

        @NdjsonExceptionResponse(value = ServiceException.class, code = "B0001", msg = "签名校验失败",
                output = StubWechatOutput.class, httpStatus = HttpStatus.NOT_FOUND)
        public void ndjsonAnnotated() {
        }

        @JsonExceptionResponse(value = ServiceException.class, code = "A0001")
        @NdjsonExceptionResponse(value = ServiceException.class, code = "B0002")
        public void conflict() {
        }

        /**
         * Q14a 守护夹具：业务在父注解上<b>直设</b> protocols（派生注解专用字段）——v2.x 静默跳过，
         * v3.0 必须启动失败。
         */
        @ExceptionResponse(value = ServiceException.class, protocols = OutputProtocol.NDJSON)
        public void plainProtocols() {
        }

        /**
         * Q7a 守护夹具：业务自定义派生注解（派生-of-派生，未做任何框架注册）。
         * 框架靠家族反查识别它——协议探测与规则扫描零改动接入。
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @NdjsonExceptionResponse(value = ServiceException.class, code = "B0003")
        @interface MyNdjson {
        }

        @MyNdjson
        public void customDerived() {
        }
    }

    private final StaticApplicationContext context = new StaticApplicationContext();

    @BeforeEach
    void setUp() {
        this.context.registerSingleton("stubWechatOutput", StubWechatOutput.class);
        this.context.refresh();
    }

    private static HandlerMethod handlerMethod(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Demo(), Demo.class.getMethod(name));
    }

    @Test
    void soleDerivedAnnotationHintsNdjsonProtocol() throws NoSuchMethodException {
        OutputProtocolResolver detector = new OutputProtocolResolver();
        HandlerMethod hm = handlerMethod("ndjsonAnnotated");

        detector.handleMethod(Set.of(), null, hm);

        assertEquals(OutputProtocol.NDJSON, detector.resolve(hm, null),
                "规则 1：方法仅贴一个派生注解 → 协议提示 NDJSON（无 produces 也成立）");
    }

    @Test
    void conflictingDerivedAnnotationsFallBackToJson() throws NoSuchMethodException {
        OutputProtocolResolver detector = new OutputProtocolResolver();
        HandlerMethod hm = handlerMethod("conflict");

        detector.handleMethod(Set.of(), null, hm);

        assertEquals(OutputProtocol.HTTP_JSON, detector.resolve(hm, null),
                "Json + Ndjson 同贴 → 规则 1 冲突回退 → 无 produces → 兜底 JSON");
    }

    @Test
    void annotatedMethodHitsP0WithCustomOutputAndStatusIntent() throws Exception {
        HandlerExceptionAnnotationProcessor scanner = new HandlerExceptionAnnotationProcessor(this.context);
        HandlerMethod hm = handlerMethod("ndjsonAnnotated");
        scanner.handleMethod(Set.of(), null, hm);

        ErrorDescriptor descriptor = scanner.resolve(new ServiceException("B0001", "boom"), hm, OutputProtocol.NDJSON);

        assertNotNull(descriptor, "P0 必须命中（防哑巴注解——扫描漏登记时此处最先红）");
        assertEquals("B0001", descriptor.getCode(), "code 来自注解而非 P2 兜底");
        assertEquals("签名校验失败", descriptor.getMessage(), "msg 来自注解而非异常自身文案");
        assertInstanceOf(StubWechatOutput.class, descriptor.getBodyCustomizer(),
                "output 指向的 Bean 必须被实例化并固化进 descriptor");
        assertEquals(HttpStatus.NOT_FOUND, descriptor.getStatusIntent(),
                "v3.0：注解 httpStatus 升维流入协议无关的 statusIntent");
        assertNull(descriptor.getHint(),
                "v3.0：NDJSON 无协议专属 hint（状态码已升维，sealed 家族只剩 SSE）");
    }

    @Test
    void annotatedMethodDoesNotMatchOtherProtocols() throws Exception {
        HandlerExceptionAnnotationProcessor scanner = new HandlerExceptionAnnotationProcessor(this.context);
        HandlerMethod hm = handlerMethod("ndjsonAnnotated");
        scanner.handleMethod(Set.of(), null, hm);

        assertNull(scanner.resolve(new ServiceException("B0001", "boom"), hm, OutputProtocol.HTTP_JSON),
                "协议过滤器：注解锁死 NDJSON，JSON 协议下不得命中");
        assertNull(scanner.resolve(new ServiceException("B0001", "boom"), hm, OutputProtocol.HTTP_SSE),
                "协议过滤器：SSE 协议下同样不得命中");
    }

    // ─────────────────────────────────────────────────────────────
    // v3.0 守护测试
    // ─────────────────────────────────────────────────────────────

    /**
     * Q14a 守护：业务在 {@code @ExceptionResponse} 上直设 {@code protocols} 必须让启动失败。
     * <p>
     * v2.x 对这种误用静默跳过（哑巴注解——业务以为锁了协议，实际从未生效）。v3.0 改为
     * {@code IllegalStateException} 快速失败；本测试防止未来重构时悄悄退化回静默行为。
     */
    @Test
    void plainProtocolsDirectSetFailsFastAtStartup() throws NoSuchMethodException {
        HandlerExceptionAnnotationProcessor scanner = new HandlerExceptionAnnotationProcessor(this.context);
        HandlerMethod hm = handlerMethod("plainProtocols");

        assertThrows(IllegalStateException.class,
                () -> scanner.handleMethod(Set.of(), null, hm),
                "protocols 直设是派生注解专用字段的误用，必须启动期失败而非静默跳过");
    }

    /**
     * Q7a 守护：业务自定义派生注解（{@code @MyNdjson} ← {@code @NdjsonExceptionResponse} ←
     * {@code @ExceptionResponse}，派生-of-派生）零改动进入协议探测与规则扫描。
     * <p>
     * 这是 v3.0 家族反查（元注解发现）的核心承诺：新增派生注解不需要改框架任何一处扫描代码。
     * v2.x 的按类型枚举扫描（hasJson/hasSse/hasNdjson 三布尔）不可能通过本测试。
     */
    @Test
    void customDerivedAnnotationJoinsFamilyWithZeroFrameworkChange() throws Exception {
        // 1. 协议探测：家族反查识别业务自定义派生注解的锁死协议。
        OutputProtocolResolver detector = new OutputProtocolResolver();
        HandlerMethod hm = handlerMethod("customDerived");
        detector.handleMethod(Set.of(), null, hm);
        assertEquals(OutputProtocol.NDJSON, detector.resolve(hm, null),
                "自定义派生注解必须能提示协议（元注解链反查）");

        // 2. 规则扫描：注解值（code）经合并视图正确流入 descriptor。
        HandlerExceptionAnnotationProcessor scanner = new HandlerExceptionAnnotationProcessor(this.context);
        scanner.handleMethod(Set.of(), null, hm);
        ErrorDescriptor descriptor = scanner.resolve(new ServiceException("B0003", "boom"), hm, OutputProtocol.NDJSON);

        assertNotNull(descriptor, "自定义派生注解必须进入规则扫描（防哑巴派生）");
        assertEquals("B0003", descriptor.getCode(),
                "派生链上声明的 code 必须经 @AliasFor/元注解合并正确解析");
    }

}
