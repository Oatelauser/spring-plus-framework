package io.github.oatelauser.springplus.boot.process;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;

/**
 * 启动期全量 Bean 扫描处理设施（fail-closed）
 * <p>
 * 全部非懒加载单例实例化完毕后（{@link SmartInitializingSingleton} 回调），遍历容器中
 * <b>全部具体 Bean 定义</b>（含懒加载 / prototype），交给容器内全部 {@link HandlerBean} 处理。
 * 典型用途是启动期静态校验：security 模块借此扫描 {@code @RequiresRole}/{@code @RequiresPermission}
 * 的空值配置错误——lazy / prototype Bean 上的错误同样在启动期暴露，无须等首次使用。
 * <p>
 * 契约与边界：
 * <ul>
 *   <li><b>一个方法，两档输入</b>：{@code beanKind} 标注实例化形态、{@code beanType} 恒非 null
 *       （元数据档，全定义覆盖）；{@code bean} 仅在 {@link BeanKind#SINGLETON} 时非 null（实例档）
 *       ——<b>本设施绝不因扫描而实例化任何 Bean</b>（构造副作用 / lazy 语义零破坏），
 *       需要实例的 {@link HandlerBean} 以 {@code beanKind == SINGLETON} 为门槛自行取用</li>
 *   <li><b>跳过</b>：abstract 父定义（永不成 Bean，报错属误报）与 {@link HandlerBean} 实现类自身</li>
 *   <li><b>fail-closed</b>：{@link HandlerBean#handleBean} 抛出的异常直接冒泡、中断启动——
 *       启动校验的语义就是"配置错误宁可启动失败，不可运行期静默放行"，本设施不做任何吞异常</li>
 *   <li>容器引用经 {@link BeanFactoryAware} 取得（而非 {@code BeanFactoryPostProcessor}）：
 *       BFPP 时机过早，其构造依赖（各 {@link HandlerBean}）会被连带提前实例化，
 *       绕过部分 BeanPostProcessor（Spring 会输出 ineligible 告警）</li>
 *   <li>边缘语义：FactoryBean 定义的 {@code getType(name)} 可能触发其工厂初始化（Spring 自身
 *       解析类型即如此），属类型解析成本而非 Bean 实例化</li>
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
        // 实例仅对已就绪的单例提供，绝不因扫描触发创建
        for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = this.beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.isAbstract()) {
                continue;
            }
            Class<?> beanType = this.beanFactory.getType(beanName);
            if (beanType == null || HandlerBean.class.isAssignableFrom(beanType)) {
                continue;
            }
            BeanKind beanKind = getBeanKind(beanDefinition);
            Object bean = beanKind == BeanKind.SINGLETON && this.beanFactory.containsSingleton(beanName)
                    ? this.beanFactory.getBean(beanName) : null;
            for (HandlerBean handlerBean : this.handlerBeans) {
                handlerBean.handleBean(beanKind, beanType, bean);
            }
        }
    }

    private static @NonNull BeanKind getBeanKind(BeanDefinition beanDefinition) {
        return beanDefinition.isSingleton()
                ? (beanDefinition.isLazyInit() ? BeanKind.LAZY : BeanKind.SINGLETON)
                : BeanKind.PROTOTYPE;
    }

    /**
     * 启动期 Bean 处理 SPI（实现类注册为 Spring Bean 即被收集，按 Ordered 排序）
     * <p>
     * 一个方法覆盖两类需求：只要元数据的实现用 {@code beanKind}/{@code beanType}
     * （全定义覆盖，含 lazy/prototype）；需要实例的实现以 {@code beanKind == SINGLETON}
     * 为门槛使用 {@code bean}（其余形态为 null，本设施不代为创建）。
     */
    public interface HandlerBean {

        /**
         * 处理单个 Bean 定义。抛出异常将中断启动（fail-closed 契约，见类 javadoc）。
         *
         * @param beanKind 实例化形态（单例 / 懒加载 / 每次创建），决定 {@code bean} 的提供方式
         * @param beanType Bean 类型（恒非 null）
         * @param bean     已就绪的单例实例；懒加载 / prototype 等其余形态为 null
         */
        void handleBean(BeanKind beanKind, Class<?> beanType, @Nullable Object bean);

    }
}
