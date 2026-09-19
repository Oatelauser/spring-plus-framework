package io.github.oatelauser.springplus.boot.process;

/**
 * Bean 的实例化形态（{@link HandleBeanPostProcessor} 扫描时刻的语义标签，
 * 告知 {@link HandleBeanPostProcessor.HandlerBean} 实例的提供方式）
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-19
 * @since 1.1
 */
public enum BeanKind {

    /**
     * 单例（非懒加载）：扫描时已就绪，{@code handleBean} 的 {@code bean} 参数即该实例
     */
    SINGLETON,

    /**
     * 懒加载单例：首次使用时才创建，{@code bean} 参数恒为 null（扫描绝不触发创建）
     */
    LAZY,

    /**
     * 每次获取都新建（prototype / request / session 等非单例 scope）：{@code bean} 参数恒为 null
     */
    PROTOTYPE
}
