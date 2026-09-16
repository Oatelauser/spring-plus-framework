package io.github.oatelauser.springplus.boot.client.adapt;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 跨主机重定向剥离凭据（SSRF 纵深，CWE-918）：跟随重定向把请求带到其他主机时，
 * {@code Authorization} / {@code Cookie} 头不再随行（防凭据经 30x 泄露给内网/第三方）。
 * <p>
 * 实现为请求拦截器（HC 公开 API，兼容 5.x 全线）：首跳把原始 host 记入
 * {@link HttpContext}，后续每跳发送前比对——目标 host 变化即剥除凭据头。
 * 默认启用（{@code spring-plus.client.strip-credentials-on-cross-host-redirect}），
 * 仅 HTTP_COMPONENTS 引擎生效；JDK / SIMPLE 引擎的重定向在 JDK HttpClient 内部处理，
 * 无法逐跳干预——启用 SSRF 防护的部署建议使用 HTTP_COMPONENTS 引擎。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
public final class CrossHostCredentialStrippingInterceptor implements HttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CrossHostCredentialStrippingInterceptor.class);

    /** 复用单例（无状态，host 记在 HttpContext 而非拦截器） */
    public static final CrossHostCredentialStrippingInterceptor INSTANCE =
            new CrossHostCredentialStrippingInterceptor();

    private static final String ORIGINAL_HOST_ATTRIBUTE =
            CrossHostCredentialStrippingInterceptor.class.getName() + ".originalHost";

    private static final String[] CREDENTIAL_HEADERS = {"Authorization", "Cookie"};

    private CrossHostCredentialStrippingInterceptor() {
    }

    @Override
    public void process(HttpRequest request, EntityDetails entity, HttpContext context) {
        String current = hostOf(request);
        if (current == null) {
            return;
        }
        String original = (String) context.getAttribute(ORIGINAL_HOST_ATTRIBUTE);
        if (original == null) {
            // 首跳：记录原始 host
            context.setAttribute(ORIGINAL_HOST_ATTRIBUTE, current);
            return;
        }
        if (original.equalsIgnoreCase(current)) {
            return;
        }
        for (String header : CREDENTIAL_HEADERS) {
            if (request.containsHeader(header)) {
                request.removeHeaders(header);
                log.warn("HTTP client: 跨主机重定向剥离凭据头 [{}] ({} -> {})", header, original, current);
            }
        }
    }

    private static String hostOf(HttpRequest request) {
        return request.getAuthority() != null ? request.getAuthority().getHostName() : null;
    }

}
