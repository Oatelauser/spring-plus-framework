package io.github.oatelauser.springplus.boot.client.interceptor;

import org.springframework.http.HttpHeaders;

/**
 * 认证提供者
 * <p>用户实现此接口并注册为 Bean</p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@FunctionalInterface
public interface AuthProvider {

    /**
     * 将认证信息写入请求头
     */
    void applyAuth(HttpHeaders headers);

}
