package io.github.oatelauser.springplus.boot.utils;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code @ControllerAdvice} bean 的通用扫描助手（ADR 0003 通用扫描设施）。
 * <p>
 * 模块级异常 advice 契约（窄类型声明 / 显式 {@code @Order}）的启动期校验需要
 * "按注解收集 advice bean + 读类级 order + 收集 {@code @ExceptionHandler} 声明"
 * 三件事，各模块（web 的 advice 契约校验器、security 的注解校验等）复用本类，
 * 不各自散写扫描逻辑。生命周期（何时触发校验）仍归各模块的校验器自理。
 * <p>
 * 类型解析对齐 Spring 自身 {@code ControllerAdviceBean#findAnnotatedBeans} 的做法
 * （{@code findAnnotationOnBean} 深入元注解，{@code @RestControllerAdvice} 经
 * {@code @ControllerAdvice} 元注解命中）；{@code @ExceptionHandler} 生效类型语义对齐
 * {@code ExceptionHandlerMethodResolver}：{@code value()} 非空取 value，
 * 否则取方法参数中的 {@code Throwable} 子类。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
public final class ControllerAdviceScanUtils {

    private ControllerAdviceScanUtils() {
    }

    /**
     * advice bean 描述：bean 名 + 代理剥离后的用户类。
     */
    public record AdviceBeanDescriptor(String beanName, Class<?> beanType) {
    }

    /**
     * 收集容器中全部 advice bean（含 {@code @RestControllerAdvice}），不实例化目标 bean。
     */
    public static List<AdviceBeanDescriptor> findAdviceBeans(ListableBeanFactory beanFactory) {
        List<AdviceBeanDescriptor> result = new ArrayList<>();
        for (String name : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Object.class)) {
            if (beanFactory.findAnnotationOnBean(name, ControllerAdvice.class) != null) {
                Class<?> type = beanFactory.getType(name);
                if (type != null) {
                    result.add(new AdviceBeanDescriptor(name, ClassUtils.getUserClass(type)));
                }
            }
        }
        return result;
    }

    /**
     * 某个 advice 类上全部 {@code @ExceptionHandler} 的生效异常类型（含父类声明）。
     */
    public static Set<Class<? extends Throwable>> exceptionHandlerExceptionTypes(Class<?> adviceType) {
        Set<Class<? extends Throwable>> types = new HashSet<>();
        ReflectionUtils.doWithMethods(adviceType, method -> {
            ExceptionHandler ann = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
            if (ann == null) {
                return;
            }
            if (ann.value().length > 0) {
                Collections.addAll(types, ann.value());
            } else {
                for (Class<?> parameterType : method.getParameterTypes()) {
                    if (Throwable.class.isAssignableFrom(parameterType)) {
                        types.add(parameterType.asSubclass(Throwable.class));
                    }
                }
            }
        });
        return types;
    }

    /**
     * 类是否表达了显式排序意图：类级 {@code @Order}（含元注解）或实现 {@link Ordered}。
     * <p>
     * 未表达时排序值为缺省 {@link Ordered#LOWEST_PRECEDENCE}——与全局兜底 advice 持平，
     * 先后由 bean 注册顺序决定（模块级 advice 契约要求显式声明，见 web 校验器规则二）。
     */
    public static boolean hasExplicitOrder(Class<?> beanType) {
        return Ordered.class.isAssignableFrom(beanType) || OrderUtils.getOrder(beanType) != null;
    }

    /**
     * bean 是否表达了显式排序意图。对齐 Spring 实际的 advice 排序解析
     * （{@code findAnnotationOnBean(name, Order.class)}）：除类级声明外，
     * {@code @Bean} 工厂方法上的 {@code @Order} 同样生效，漏看会误报"未声明 order"。
     */
    public static boolean hasExplicitOrder(ListableBeanFactory beanFactory, String beanName, Class<?> beanType) {
        return hasExplicitOrder(beanType) || beanFactory.findAnnotationOnBean(beanName, Order.class) != null;
    }

}
