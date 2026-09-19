package io.github.oatelauser.springplus.boot.process;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HandleBeanPostProcessor} 契约测试：BeanKind 形态标注、全定义覆盖
 * （lazy/prototype 以元数据参与且不实例化、bean 为 null）、abstract 定义跳过、
 * 就绪单例提供实例、处理器自身跳过、fail-closed 异常冒泡。
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

    record Visit(BeanKind beanKind, Class<?> beanType, Object bean) {
    }

    static class RecordingHandler implements HandleBeanPostProcessor.HandlerBean {
        final List<Visit> visits = new ArrayList<>();

        @Override
        public void handleBean(BeanKind beanKind, Class<?> beanType, Object bean) {
            visits.add(new Visit(beanKind, beanType, bean));
        }
    }

    private static void run(DefaultListableBeanFactory beanFactory,
            List<? extends HandleBeanPostProcessor.HandlerBean> handlers) {
        HandleBeanPostProcessor processor = new HandleBeanPostProcessor(new ArrayList<>(handlers));
        processor.setBeanFactory(beanFactory);
        processor.afterSingletonsInstantiated();
    }

    private static Visit visitOf(List<Visit> visits, Class<?> beanType) {
        return visits.stream().filter(v -> v.beanType() == beanType).findFirst().orElseThrow();
    }

    @Test
    void 就绪单例为SINGLETON并拿到实例_处理器自身跳过() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RecordingHandler handler = new RecordingHandler();
        beanFactory.registerBeanDefinition("handler", new RootBeanDefinition(RecordingHandler.class));
        beanFactory.registerBeanDefinition("sample", new RootBeanDefinition(SampleBean.class));
        beanFactory.preInstantiateSingletons();
        SampleBean created = beanFactory.getBean("sample", SampleBean.class);

        run(beanFactory, List.of(handler));

        assertTrue(handler.visits.stream().noneMatch(v -> v.beanType() == RecordingHandler.class),
                "处理器自身必须被跳过");
        Visit visit = visitOf(handler.visits, SampleBean.class);
        assertSame(BeanKind.SINGLETON, visit.beanKind());
        assertSame(created, visit.bean(), "就绪单例必须返回缓存中的同一实例");
    }

    @Test
    void 懒加载定义为LAZY_bean为null_不强制实例化() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition lazy = new RootBeanDefinition(SampleBean.class);
        lazy.setLazyInit(true);
        beanFactory.registerBeanDefinition("lazySample", lazy);
        beanFactory.registerBeanDefinition("eager", new RootBeanDefinition(OtherBean.class));
        beanFactory.preInstantiateSingletons();
        RecordingHandler handler = new RecordingHandler();

        assertDoesNotThrow(() -> run(beanFactory, List.of(handler)));

        assertFalse(beanFactory.containsSingleton("lazySample"), "懒加载 Bean 不允许被启动期扫描强制实例化");
        Visit lazyVisit = visitOf(handler.visits, SampleBean.class);
        assertSame(BeanKind.LAZY, lazyVisit.beanKind(), "lazy 定义必须以元数据参与扫描并标注 LAZY");
        assertNull(lazyVisit.bean());
        assertSame(BeanKind.SINGLETON, visitOf(handler.visits, OtherBean.class).beanKind());
    }

    @Test
    void prototype定义为PROTOTYPE_bean为null() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition proto = new RootBeanDefinition(SampleBean.class);
        proto.setScope("prototype");
        beanFactory.registerBeanDefinition("proto", proto);
        beanFactory.preInstantiateSingletons();
        RecordingHandler handler = new RecordingHandler();

        run(beanFactory, List.of(handler));

        Visit visit = visitOf(handler.visits, SampleBean.class);
        assertSame(BeanKind.PROTOTYPE, visit.beanKind());
        assertNull(visit.bean(), "prototype 定义的 bean 必须是 null（不代为创建）");
    }

    @Test
    void abstract父定义跳过() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition abstractParent = new RootBeanDefinition(SampleBean.class);
        abstractParent.setAbstract(true);
        beanFactory.registerBeanDefinition("parentDef", abstractParent);
        beanFactory.registerBeanDefinition("concrete", new RootBeanDefinition(OtherBean.class));
        beanFactory.preInstantiateSingletons();
        RecordingHandler handler = new RecordingHandler();

        run(beanFactory, List.of(handler));

        assertTrue(handler.visits.stream().noneMatch(v -> v.beanType() == SampleBean.class),
                "abstract 父定义永不成 Bean，必须跳过（报错属误报）");
        assertTrue(handler.visits.stream().anyMatch(v -> v.beanType() == OtherBean.class));
    }

    @Test
    void 处理器抛异常直接冒泡_启动失败语义() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("sample", new RootBeanDefinition(SampleBean.class));
        beanFactory.preInstantiateSingletons();
        HandleBeanPostProcessor.HandlerBean failing = (beanKind, beanType, bean) -> {
            throw new IllegalStateException("boom");
        };

        assertThrows(IllegalStateException.class, () -> run(beanFactory, List.of(failing)),
                "fail-closed 契约：校验异常必须中断启动，不允许吞掉");
    }

}
