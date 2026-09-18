package io.github.oatelauser.springplus.boot.utils;

import io.github.oatelauser.springplus.boot.utils.ControllerAdviceScanUtils.AdviceBeanDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * advice 扫描助手行为回归：元注解发现 / 生效异常类型收集 / 显式 order 判定。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
class ControllerAdviceScanUtilsTest {

    /** 模拟模块级 advice：窄类型 + 显式 order + 参数式声明混合 */
    @RestControllerAdvice
    @Order(100)
    static class ModuleAdvice {

        @ExceptionHandler({IllegalFormatException.class, NullPointerException.class})
        public Object narrow(Exception ex) {
            return null;
        }

        /** value() 为空：生效类型取方法参数中的 Throwable 子类 */
        @ExceptionHandler
        public Object byParam(IllegalArgumentException ex) {
            return null;
        }
    }

    /** 无序 + Exception 兜底：两条契约都违反的形态 */
    @ControllerAdvice
    static class CatchAllAdvice {

        @ExceptionHandler(Exception.class)
        public Object catchAll(Exception ex) {
            return null;
        }
    }

    /** 实现 Ordered：同样算显式排序意图 */
    @ControllerAdvice
    static class OrderedAdvice implements Ordered {

        @ExceptionHandler(IllegalStateException.class)
        public Object narrow(IllegalStateException ex) {
            return null;
        }

        @Override
        public int getOrder() {
            return 200;
        }
    }

    @Test
    void findsAdviceBeansIncludingRestControllerAdvice() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("moduleAdvice", new ModuleAdvice());
        beanFactory.registerSingleton("catchAllAdvice", new CatchAllAdvice());
        beanFactory.registerSingleton("plainBean", new Object());

        List<AdviceBeanDescriptor> advices = ControllerAdviceScanUtils.findAdviceBeans(beanFactory);
        assertEquals(2, advices.size());
        assertEquals(ModuleAdvice.class, advices.get(0).beanType());
        assertEquals("moduleAdvice", advices.get(0).beanName());
    }

    @Test
    void collectsValueAndParamBasedHandlerTypes() {
        Set<Class<? extends Throwable>> types = ControllerAdviceScanUtils
                .exceptionHandlerExceptionTypes(ModuleAdvice.class);
        assertEquals(Set.of(IllegalFormatException.class, NullPointerException.class,
                IllegalArgumentException.class), types);
    }

    @Test
    void detectsExplicitOrderByAnnotationOrInterface() {
        assertTrue(ControllerAdviceScanUtils.hasExplicitOrder(ModuleAdvice.class));
        assertTrue(ControllerAdviceScanUtils.hasExplicitOrder(OrderedAdvice.class));
        assertFalse(ControllerAdviceScanUtils.hasExplicitOrder(CatchAllAdvice.class));
    }

}
