package io.github.oatelauser.springplus.boot.client.interceptor;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 内部流式标记头的兜底清除器。
 * <p>
 * {@code BaseApiClient} 的流式请求会向请求头注入
 * {@link LoggingInterceptor#STREAMING_HINT}，供日志拦截器识别"跳过 BODY 缓冲"。
 * 该标记是框架内部协议，<b>绝不发送到服务端</b>。
 * </p>
 * <p>
 * 本拦截器无条件注册在 HTTP 拦截器链的末位（最靠近网络的一端），
 * 保证无论日志拦截器是否存在、日志级别为何，标记头都会在发送前被移除。
 * </p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @since 1.1
 */
@SuppressWarnings("NullableProblems")
public class StreamingHintInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().remove(LoggingInterceptor.STREAMING_HINT);
        return execution.execute(request, body);
    }

}
