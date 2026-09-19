package io.github.oatelauser.springplus.boot.process;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;

/**
 * 全量单例 Bean 扫描处理设施（启动期，fail-closed）
 * <p>
 * 全部非懒加载单例实例化完毕后（{@link SmartInitializingSingleton} 回调），遍历容器中
 * <b>已就绪的单例</b>，交给容器内全部 {@link HandlerBean} 逐个处理。典型用途是启动期静态校验：
 * security 模块借此扫描 {@code @RequiresRole}/{@code @RequiresPermission} 的空值配置错误。
 * <p>
 * 契约与边界：
 * <ul>
 *   <li><b>fail-closed</b>：{@link HandlerBean#handleBean} 抛出的异常直接冒泡、中断启动——
 *       启动校验的语义就是"配置错误宁可启动失败，不可运行期静默放行"，本设施不做任何吞异常</li>
 *   <li><b>只扫已就绪单例</b>：懒加载 / prototype Bean 不强制实例化（保持其惰性语义），
 *       对应的校验职责仍由各处运行期断言兜底</li>
 *   <li>容器引用经 {@link BeanFactoryAware} 取得（而非 {@code BeanFactoryPostProcessor}）：
 *       BFPP 时机过早，其构造依赖（各 {@link HandlerBean}）会被连带提前实例化，
 *       绕过部分 BeanPostProcessor（Spring 会输出 ineligible 告警）</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-19
 * @since 1.1
 */
@RequiredArgsConstructor
public class HandleBeanPostProcessor implements BeanFactoryAware, SmartInitializingSingleton {

    private final List<HandlerBean> handlerBeans;
    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // getSingletonNames()：只含已创建的单例，懒加载/prototype 定义不在其中，getBean 仅取缓存
        for (String beanName : this.beanFactory.getSingletonNames()) {
            Class<?> beanType = this.beanFactory.getType(beanName);
            if (beanType == null || HandlerBean.class.isAssignableFrom(beanType)) {
                continue;
            }
            Object bean = this.beanFactory.getBean(beanName);
            for (HandlerBean handlerBean : this.handlerBeans) {
                if (handlerBean.supportsBean(beanType, bean)) {
                    handlerBean.handleBean(beanType, bean);
                }
            }
        }
    }

    /**
     * 单例 Bean 的启动期处理器 SPI（实现类注册为 Spring Bean 即被收集，按 Ordered 排序）
     */
    public interface HandlerBean {
        /**
         * 是否处理该 Bean（默认全部处理）
         */
        default boolean supportsBean(Class<?> beanType, Object bean) {
            return true;
        }

        /**
         * 处理单个 Bean。抛出异常将中断启动（fail-closed 契约，见类 javadoc）
         */
        void handleBean(Class<?> beanType, Object bean);
    }
}
