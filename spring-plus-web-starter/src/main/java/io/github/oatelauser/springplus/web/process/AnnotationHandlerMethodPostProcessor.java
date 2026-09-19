package io.github.oatelauser.springplus.web.process;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理Controller中的请求方法后置处理器
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-02-08
 * @since 1.0
 */
public class AnnotationHandlerMethodPostProcessor implements BeanFactoryPostProcessor, SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationHandlerMethodPostProcessor.class);

    /**
     * Spring基础包名
     */
    public static final String SPRING_BASE_PACKAGE = "org.springframework";

    private String servletContextPath = "";
    private ConfigurableListableBeanFactory beanFactory;

    public void postProcessHandleMethods(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo requestMappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            if (!this.shouldIgnoreHandleMethod(handlerMethod)) {
                this.postProcessHandleMethod(requestMappingInfo, handlerMethod);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void postProcessHandleMethod(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        Set<String> urls = requestMappingInfo.getPathPatternsCondition().getPatterns()
                .stream().map(PathPattern::getPatternString)
                .collect(Collectors.toSet());

        Map<String, HandlerMethodProcessor> processors = beanFactory.getBeansOfType(HandlerMethodProcessor.class);
        for (HandlerMethodProcessor processor : processors.values()) {
            if (processor.supports(urls, handlerMethod)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("processor [{}] handle url {} and method [{}}]",
                            processor, urls, handlerMethod);
                }

                try {
                    processor.handleMethod(servletContextPath, urls, requestMappingInfo, handlerMethod);
                } catch (Exception e) {
                    LOG.warn("processor [{}] handle method [{}}] has error: {}",
                            processor, handlerMethod, e.getLocalizedMessage());
                }
            }
        }
    }

    /**
     * 过滤Spring内部的请求方法
     *
     * @param handlerMethod 请求方法
     * @return yes or no
     */
    private boolean shouldIgnoreHandleMethod(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getName().contains(SPRING_BASE_PACKAGE);
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.loadServletContextPath(beanFactory.getBean(Environment.class));
        RequestMappingHandlerMapping handlerMapping = beanFactory.getBean("requestMappingHandlerMapping",
                RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        if (!CollectionUtils.isEmpty(handlerMethods)) {
            this.postProcessHandleMethods(handlerMethods);
        }
    }

    private void loadServletContextPath(Environment environment) {
        this.servletContextPath = environment.getProperty("server.servlet.context-path", "");
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

}
