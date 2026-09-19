package io.github.oatelauser.springplus.web.process;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

/**
 * 请求方法处理器
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-02-08
 * @since 1.0
 */
public interface HandlerMethodProcessor {

    /**
     * 是否支持请求方法
     *
     * @param urls          请求地址
     * @param handlerMethod 请求方法
     * @return yes or no
     */
    boolean supports(Set<String> urls, HandlerMethod handlerMethod);

    /**
     * 处理控制器请求方法
     *
     * @param servletContentPath 统一前缀地址
     * @param urls               请求地址（不带统一前缀地址的地址）
     * @param requestMappingInfo 请求映射信息
     * @param handlerMethod      请求方法
     */
    default void handleMethod(String servletContentPath, Set<String> urls,
            RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        this.handleMethod(urls, requestMappingInfo, handlerMethod);
    }

    /**
     * 处理控制器请求方法
     *
     * @param urls               请求地址（不带统一前缀地址的地址）
     * @param requestMappingInfo 请求映射信息
     * @param handlerMethod      请求方法
     */
    default void handleMethod(Set<String> urls, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
    }

}
