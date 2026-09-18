package io.github.oatelauser.springplus.security.utils.matcher;

import io.github.oatelauser.springplus.security.annotation.RequiresNonLogin;
import io.github.oatelauser.springplus.web.trace.HandlerMethodProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

import static io.github.oatelauser.springplus.boot.utils.AnnotationUtils.findMethodAnnotation;


/**
 * 权限注解控制器处理器
 * <p>
 * 1.筛选需要跳过登录的接口
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-13
 * @see RequiresNonLogin
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class AnonymousRequestMatcher extends AbstractAntRequestMatcher implements HandlerMethodProcessor {

    @Override
    public boolean supports(Set<String> urls, HandlerMethod handlerMethod) {
        return true;
    }

    @Override
    public void handleMethod(Set<String> urls, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        RequiresNonLogin annotation = findMethodAnnotation(handlerMethod.getMethod(),
                RequiresNonLogin.class, handlerMethod.getBeanType());
        if (annotation == null || CollectionUtils.isEmpty(urls)) {
            return;
        }

        // 处理不需要登录的接口
        for (String url : urls) {
            Set<RequestMethod> methods = requestMappingInfo.getMethodsCondition().getMethods();
            if (CollectionUtils.isEmpty(methods)) {
                this.addRequestMatcher(url);
                continue;
            }

            for (RequestMethod requestMethod : methods) {
                this.addRequestMatcher(requestMethod.asHttpMethod(), url);
            }
        }
    }

}
