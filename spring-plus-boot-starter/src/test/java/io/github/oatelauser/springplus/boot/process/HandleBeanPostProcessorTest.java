package io.github.oatelauser.springplus.boot.process;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HandleBeanPostProcessor} 契约测试：扫描范围（已就绪单例、跳过处理器自身）、
 * fail-closed（异常冒泡中断）、懒加载不强制实例化。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-19
 * @since 1.1
 */
class HandleBeanPostProcessorTest {

    static class SampleBean {
    }

    static class OtherBean {
    }

    static class RecordingHandler implements HandleBeanPostProcessor.HandlerBean {
        final List<String> handled = new ArrayList<>();

        @Override
        public void handleBean(Class<?> beanType, Object bean) {
            handled.add(beanType.getSimpleName());
        }
    }

    private static HandleBeanPostProcessor processor(DefaultListableBeanFactory beanFactory,
            List<? extends HandleBeanPostProcessor.HandlerBean> handlers) {
        HandleBeanPostProcessor processor = new HandleBeanPostProcessor(new ArrayList<>(handlers));
        processor.setBeanFactory(beanFactory);
        return processor;
    }

    @Test
    void 扫描全部已就绪单例并跳过处理器自身() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RecordingHandler handler = new RecordingHandler();
        beanFactory.registerSingleton("handler", handler);
        beanFactory.registerSingleton("sample", new SampleBean());
        beanFactory.registerSingleton("other", new OtherBean());

        processor(beanFactory, List.of(handler)).afterSingletonsInstantiated();

        assertTrue(handler.handled.contains("SampleBean"));
        assertTrue(handler.handled.contains("OtherBean"));
        assertFalse(handler.handled.contains("RecordingHandler"), "处理器自身必须被跳过");
    }

    @Test
    void 处理器抛异常直接冒泡_启动失败语义() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("sample", new SampleBean());
        HandleBeanPostProcessor.HandlerBean failing = (beanType, bean) -> {
            throw new IllegalStateException("boom");
        };

        HandleBeanPostProcessor processor = processor(beanFactory, List.of(failing));
        assertThrows(IllegalStateException.class, processor::afterSingletonsInstantiated,
                "fail-closed 契约：校验异常必须中断启动，不允许吞掉");
    }

    @Test
    void supportsBean为false时跳过该Bean() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("sample", new SampleBean());
        RecordingHandler handler = new RecordingHandler() {
            @Override
            public boolean supportsBean(Class<?> beanType, Object bean) {
                return beanType != SampleBean.class;
            }
        };

        processor(beanFactory, List.of(handler)).afterSingletonsInstantiated();

        assertFalse(handler.handled.contains("SampleBean"));
    }

    @Test
    void 懒加载Bean不被强制实例化() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition lazy = new RootBeanDefinition(SampleBean.class);
        lazy.setLazyInit(true);
        beanFactory.registerBeanDefinition("lazySample", lazy);
        beanFactory.registerSingleton("eager", new OtherBean());
        RecordingHandler handler = new RecordingHandler();

        assertDoesNotThrow(() ->
                processor(beanFactory, List.of(handler)).afterSingletonsInstantiated());

        assertFalse(beanFactory.containsSingleton("lazySample"), "懒加载 Bean 不允许被启动期扫描强制实例化");
        assertFalse(handler.handled.contains("SampleBean"));
        assertTrue(handler.handled.contains("OtherBean"));
    }

}
