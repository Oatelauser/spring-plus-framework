package io.github.oatelauser.springplus.boot.client.interceptor;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 认证拦截器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
public class AuthInterceptor implements ClientHttpRequestInterceptor {

    private final AuthProvider authProvider;

    public AuthInterceptor(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        authProvider.applyAuth(request.getHeaders());
        return execution.execute(request, body);
    }

}
