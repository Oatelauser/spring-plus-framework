package io.github.oatelauser.springplus.web.validation.clazz;

import io.github.oatelauser.springplus.web.validation.clazz.ClassValidator.OrderPolicy;
import jakarta.validation.Configuration;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.hibernate.validator.internal.engine.ValidatorFactoryImpl;
import org.hibernate.validator.internal.metadata.BeanMetaDataManager;
import org.hibernate.validator.internal.metadata.DefaultBeanMetaDataClassNormalizer;
import org.hibernate.validator.internal.metadata.aggregated.BeanMetaData;
import org.hibernate.validator.internal.metadata.core.MetaConstraint;
import org.hibernate.validator.internal.util.ConcurrentReferenceHashMap;
import org.hibernate.validator.metadata.BeanMetaDataClassNormalizer;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static org.hibernate.validator.BaseHibernateValidatorConfiguration.FAIL_FAST;

/**
 * 类级别的校验器执行顺序拓展支持类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-28
 * @see ClassValidator#policy()
 * @since 1.0
 */
public class ClassValidatorPostProcessor implements SmartInitializingSingleton, ApplicationContextAware, ValidationConfigurationCustomizer {

    private ApplicationContext applicationContext;

    /**
     * 是否开启短路校验（fail-fast）：true 时全应用所有 @Valid 校验只报告第一个错误。
     * <p>
     * 类级别后置校验器依赖短路语义保证执行安全，默认 true；
     * 需要一次返回全部校验错误的应用可通过
     * {@code spring-plus.web.validation.fail-fast=false} 关闭。
     */
    private final boolean failFast;

    public ClassValidatorPostProcessor() {
        this(true);
    }

    public ClassValidatorPostProcessor(boolean failFast) {
        this.failFast = failFast;
    }

    @Override
    public void customize(@NonNull Configuration<?> configuration) {
        if (failFast) {
            configuration.addProperty(FAIL_FAST, Boolean.TRUE.toString());
        }
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void afterSingletonsInstantiated() {
        Validator validatorBean = applicationContext.getBean(Validator.class);
        if (!(validatorBean instanceof LocalValidatorFactoryBean validatorFactoryBean)) {
            return;
        }

        // 代理BeanMetaDataManager
        ValidatorFactory validatorFactory = validatorFactoryBean.unwrap(ValidatorFactory.class);
        if (validatorFactory instanceof ValidatorFactoryImpl validatorFactoryImpl) {
            ReflectionUtils.doWithFields(validatorFactoryImpl.getClass(), field -> {
                ReflectionUtils.makeAccessible(field);
                ConcurrentMap beanMetaDataManagers = (ConcurrentMap) ReflectionUtils.getField(field, validatorFactoryImpl);
                assert beanMetaDataManagers != null;
                beanMetaDataManagers.replaceAll((k, v) -> new OrderedBeanMetaDataManager((BeanMetaDataManager) v));
            }, field -> "beanMetaDataManagers".equals(field.getName()));
        }
        // 重新创建新的Validator，使用代理后的BeanMetaDataManager
        Validator repliceValidator = validatorFactory.getValidator();
        ReflectionUtils.doWithMethods(validatorFactoryBean.getClass(), method -> {
            ReflectionUtils.makeAccessible(method);
            ReflectionUtils.invokeMethod(method, validatorFactoryBean, repliceValidator);
        }, method -> "setTargetValidator".equals(method.getName())
                && method.getParameterCount() == 1 && Validator.class.isAssignableFrom(method.getParameterTypes()[0]));
    }

    @RequiredArgsConstructor
    static class OrderedBeanMetaDataManager implements BeanMetaDataManager {
        private final BeanMetaDataManager target;
        private final BeanMetaDataClassNormalizer classNormalizer = new DefaultBeanMetaDataClassNormalizer();
        private final Map<Class<?>, BeanMetaData<?>> beanMetaDataCache = new ConcurrentReferenceHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> BeanMetaData<T> getBeanMetaData(Class<T> beanClass) {
            Class<? super T> normalizeClass = classNormalizer.normalize(beanClass);
            return ((BeanMetaData<T>) beanMetaDataCache.computeIfAbsent(normalizeClass,
                    key -> new OrderedBeanMetaData<>(target.getBeanMetaData(beanClass))));
        }

        @Override
        public void clear() {
            target.clear();
        }
    }

    /**
     * 有序的 BeanMetaData 包装器
     *
     * <p>根据 {@link ClassValidator.OrderPolicy} 控制类级别约束和字段级别约束的执行顺序：</p>
     * <ul>
     *   <li>{@link ClassValidator.OrderPolicy#BEFORE_FIELD}: 类级别约束在字段约束之前执行</li>
     *   <li>{@link ClassValidator.OrderPolicy#AFTER_FIELD}: 类级别约束在字段约束之后执行</li>
     *   <li>{@link ClassValidator.OrderPolicy#PARALLEL}: 保持原始顺序，并行执行</li>
     * </ul>
     *
     * @param <T> Bean 类型
     * @author Oatelauser
     * @since 1.0
     */
    static class OrderedBeanMetaData<T> implements BeanMetaData<T> {

        @Delegate
        private final BeanMetaData<T> target;
        private final Set<MetaConstraint<?>> allMetaConstraints;
        private final Set<MetaConstraint<?>> classMetaConstraints;
        private final Set<MetaConstraint<?>> allDirectMetaConstraints;

        OrderedBeanMetaData(BeanMetaData<T> target) {
            this.target = target;
            this.allMetaConstraints = MetaConstraintSorter.sort(target.getAllMetaConstraints());
            this.classMetaConstraints = MetaConstraintSorter.sort(target.getClassMetaConstraints());
            this.allDirectMetaConstraints = MetaConstraintSorter.sort(target.getAllDirectMetaConstraints());
        }

        @Override
        public Set<MetaConstraint<?>> getAllMetaConstraints() {
            return this.allMetaConstraints;
        }

        @Override
        public Set<MetaConstraint<?>> getClassMetaConstraints() {
            return this.classMetaConstraints;
        }

        @Override
        public Set<MetaConstraint<?>> getAllDirectMetaConstraints() {
            return this.allDirectMetaConstraints;
        }

    }

    /**
     * 约束排序器
     */
    static class MetaConstraintSorter {

        /**
         * Policy 优先级映射
         */
        static final Map<OrderPolicy, Integer> POLICY_PRIORITY = Map.of(
                OrderPolicy.BEFORE_FIELD, 0,
                OrderPolicy.PARALLEL, 1,
                OrderPolicy.AFTER_FIELD, 2
        );

        /**
         * 普通约束的默认 order
         */
        static final int DEFAULT_ORDER = 0;

        /**
         * 普通约束的 Policy 优先级
         */
        static final int NORMAL_POLICY_PRIORITY = 1;

        /**
         * 对约束集合排序
         *
         * @param constraints 原始约束集合
         * @return 排序后的约束集合
         */
        static Set<MetaConstraint<?>> sort(Set<MetaConstraint<?>> constraints) {
            if (constraints == null || constraints.size() <= 1) {
                return constraints;
            }

            // 转为 List 并提取排序 key
            List<SortableConstraint> sortableList = new ArrayList<>(constraints.size());
            boolean needsSort = false;

            for (MetaConstraint<?> constraint : constraints) {
                SortKey sortKey = extractSortKey(constraint);
                sortableList.add(new SortableConstraint(constraint, sortKey));

                if (sortKey.isClassValidator) {
                    needsSort = true;
                }
            }

            // 没有 ClassValidator，直接返回
            if (!needsSort) {
                return constraints;
            }

            // 排序
            sortableList.sort(Comparator.comparing(SortableConstraint::sortKey));

            // 构建结果
            Set<MetaConstraint<?>> result = new LinkedHashSet<>(constraints.size());
            for (SortableConstraint sc : sortableList) {
                result.add(sc.constraint);
            }

            return Collections.unmodifiableSet(result);
        }

        /**
         * 提取排序 key
         */
        private static SortKey extractSortKey(MetaConstraint<?> constraint) {
            Annotation annotation = constraint.getDescriptor()
                    .getAnnotationDescriptor()
                    .getAnnotation();

            // 直接是 @ClassValidator
            if (annotation instanceof ClassValidator cv) {
                return new SortKey(true, cv.policy(), cv.order());
            }

            // 检查组合注解
            Class<? extends Annotation> annotationType = annotation.annotationType();
            ClassValidator meta = annotationType.getAnnotation(ClassValidator.class);

            if (meta != null) {
                OrderPolicy policy = getAttribute(annotation, "policy", OrderPolicy.class, meta.policy());
                int order = getAttribute(annotation, "order", Integer.class, meta.order());
                return new SortKey(true, policy, order);
            }

            // 普通约束
            return new SortKey(false, null, DEFAULT_ORDER);
        }

        /**
         * 获取注解属性
         */
        @SuppressWarnings("unchecked")
        private static <T> T getAttribute(Annotation annotation, String name, Class<T> type, T defaultValue) {
            try {
                Method method = annotation.annotationType().getDeclaredMethod(name);
                Object value = method.invoke(annotation);
                return type.isInstance(value) ? (T) value : defaultValue;
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    /**
     * 排序 Key
     */
    record SortKey(boolean isClassValidator, OrderPolicy policy, int order) implements Comparable<SortKey> {
        @Override
        public int compareTo(SortKey other) {
            // 1. 按 policy 优先级排序
            int p1 = policy != null ? MetaConstraintSorter.POLICY_PRIORITY.get(policy) :
                    MetaConstraintSorter.NORMAL_POLICY_PRIORITY;
            int p2 = other.policy != null ? MetaConstraintSorter.POLICY_PRIORITY.get(other.policy) :
                    MetaConstraintSorter.NORMAL_POLICY_PRIORITY;

            int policyCompare = Integer.compare(p1, p2);
            if (policyCompare != 0) {
                return policyCompare;
            }

            // 2. 同 policy 内按 order 升序
            return Integer.compare(this.order, other.order);
        }
    }

    /**
     * 可排序的约束
     */
    record SortableConstraint(MetaConstraint<?> constraint, SortKey sortKey) {
    }

}
