package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 异常响应注解<b>家族</b>的统一发现器（v3.0，Q7a 元注解反查）。
 * <p>
 * v2.x 的问题：扫描方必须逐个枚举家族成员——{@code ExceptionResponseAnnotationProcessor} 里
 * {@code addPlainRules / addJsonRules / addSseRules / addNdjsonRules} 四套重复方法，
 * {@code HandlerExceptionProtocolProcessor} 里 {@code hasJson / hasSse / hasNdjson} 三布尔计数。
 * 每加一个派生注解要改 N 处（v2.2 的 NDJSON 就是漏改 {@code ExceptionClassAnnotationMapper}
 * 的实证），违反开闭。
 * <p>
 * v3.0 机制（对齐 Spring 发现 {@code @Component} 派生注解的做法）：家族成员 = 类型自身是
 * {@link ExceptionResponse}，或其元注解链上存在 {@link ExceptionResponse}
 * （{@link #isFamilyMember}）。业务自定义派生注解只要挂上
 * {@code @JsonExceptionResponse}（或直接 {@code @ExceptionResponse(protocols=...)}）+ {@code @AliasFor}
 * 桥接，即可被零改动识别。
 *
 * <h3>本类只做「发现」，不做「合并」</h3>
 * <p>
 * {@link #findFamilyAnnotations} 返回<b>原始注解实例</b>（保住派生注解的专属属性如 event/retry），
 * 通用字段合并（{@code @AliasFor} 桥接）由 {@link #mergedView} 按需执行——协议专属字段的二次
 * 合并读取由 {@code AnnotationToTemplateConverter} 的 hint 工厂完成。分工单一。
 *
 * <h3>以启动期为主</h3>
 * <p>
 * 家族发现涉及反射 + 元注解链遍历，主要调用点（规则扫描器、协议探测）都在启动期回调里；
 * 运行期只消费固化的 {@code ErrorDescriptorTemplate}，零反射（设计 1.2）。唯一例外是
 * {@code ExceptionClassAnnotationMapper}（异常类注解无法启动期穷举，首次命中懒扫描 + 缓存）。
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 3.0
 */
public final class ExceptionAnnotationUtils {

    private ExceptionAnnotationUtils() {
    }

    /**
     * 判定注解类型是否是 {@link ExceptionResponse} 家族成员。
     * <p>
     * 成员判定：类型自身是父注解，或元注解链上能反查到父注解（支持派生-of-派生，如业务
     * {@code @MyJsonExceptionResponse} ← {@code @JsonExceptionResponse} ← {@code @ExceptionResponse}）。
     * 借助 Spring {@code AnnotationUtils.findAnnotation} 的元注解递归（带访问环保护）。
     */
    public static boolean isFamilyMember(Class<? extends Annotation> type) {
        return type == ExceptionResponse.class
                || AnnotationUtils.findAnnotation(type, ExceptionResponse.class) != null;
    }

    /**
     * 收集方法上直接声明的全部家族注解（含 {@code @Repeatable} 容器解包）。
     * <p>
     * 方法注解不继承，无需层级遍历。
     *
     * @param method 控制器方法
     * @return 家族注解原始实例列表（可能为空，永不为 null）；同注解多条按声明顺序保留
     */
    public static List<Annotation> findFamilyAnnotations(Method method) {
        return collect(method.getAnnotations());
    }

    /**
     * 收集类上声明的全部家族注解（含容器解包 + 类层级遍历）。
     * <p>
     * 遍历顺序：本类 → 父类链（Object 以下）→ 接口（含超类携带的接口，递归）。{@code LinkedHashSet}
     * 按结构相等去重——{@code @Inherited} 注解会同时出现在子类与父类视图里，值相同即折叠。
     *
     * @param clazz 控制器类或异常类
     * @return 家族注解原始实例列表（可能为空，永不为 null）
     */
    public static List<Annotation> findFamilyAnnotations(Class<?> clazz) {
        Set<Annotation> found = new LinkedHashSet<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            found.addAll(collect(current.getAnnotations()));
        }
        for (Class<?> iface : allInterfaces(clazz)) {
            found.addAll(collect(iface.getAnnotations()));
        }
        return List.copyOf(found);
    }

    /**
     * 把任意家族注解实例合并为 {@link ExceptionResponse} 视图（{@code @AliasFor} 桥接生效）。
     * <p>
     * 普通注解传入即返回自身（幂等）；派生注解经 Spring 合并工具取桥接后的通用字段值。
     *
     * @throws IllegalStateException 注解不在家族内（调用方传错对象，编程错误）
     */
    public static ExceptionResponse mergedView(Annotation annotation) {
        if (annotation instanceof ExceptionResponse plain) {
            return plain;
        }
        ExceptionResponse merged = AnnotatedElementUtils.getMergedAnnotation(
                asElement(annotation), ExceptionResponse.class);
        if (merged == null) {
            throw new IllegalStateException("注解 " + annotation.annotationType().getName()
                    + " 不在 @ExceptionResponse 家族内（未挂元注解），无法合并通用字段");
        }
        return merged;
    }

    /**
     * 注解锁死的协议：合并视图 {@code protocols()} 恰好一个元素时返回它；其余（普通注解空协议 /
     * 多协议）返回 null。
     * <p>
     * 用于 {@code OutputProtocolResolver} 规则 1（方法派生注解协议提示）。
     */
    @Nullable
    public static OutputProtocol boundProtocol(Annotation annotation) {
        OutputProtocol[] protocols = mergedView(annotation).protocols();
        return protocols.length == 1 ? protocols[0] : null;
    }

    /**
     * 把单个注解实例包装成 {@link AnnotatedElement}——Spring 的 {@code AnnotatedElementUtils}
     * 系列 API 都接受 element 而非 annotation。包内共享（converter 的 hint 工厂二次合并读取也用它）。
     */
    static AnnotatedElement asElement(Annotation annotation) {
        return new SingleAnnotationElement(annotation);
    }

    /**
     * 遍历一批直接注解：家族成员直接收集；{@code @Repeatable} 容器（{@code Xxx.List}）解包后收集内层。
     */
    private static List<Annotation> collect(Annotation[] directAnnotations) {
        List<Annotation> found = new ArrayList<>();
        for (Annotation annotation : directAnnotations) {
            if (isFamilyMember(annotation.annotationType())) {
                found.add(annotation);
                continue;
            }
            collectFromContainer(annotation, found);
        }
        return found;
    }

    /**
     * 解包 {@code @Repeatable} 容器：反射读 {@code value()}，若返回家族注解数组则收集内层元素。
     * <p>
     * 判据只看结构（存在返回注解数组的 {@code value()} 且组件类型是家族成员），不限定容器类型名，
     * 业务自定义派生注解的 {@code Xxx.List} 容器同样被解包。
     */
    private static void collectFromContainer(Annotation container, List<Annotation> sink) {
        Method value;
        try {
            value = container.annotationType().getDeclaredMethod("value");
        } catch (NoSuchMethodException e) {
            return;
        }
        Class<?> returnType = value.getReturnType();
        if (!returnType.isArray() || !Annotation.class.isAssignableFrom(returnType.getComponentType())) {
            return;
        }
        try {
            Annotation[] elements = (Annotation[]) value.invoke(container);
            for (Annotation element : elements) {
                if (isFamilyMember(element.annotationType())) {
                    sink.add(element);
                }
            }
        } catch (ReflectiveOperationException e) {
            // 注解成员访问异常属容器定义问题：静默跳过比炸掉启动更稳妥，且下方 isFamilyMember 已保证只收家族成员。
        }
    }

    /**
     * 收集类层级上的全部接口（含接口的超接口），父类链上每一层携带的接口都算。
     */
    private static Set<Class<?>> allInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            collectInterfaces(current, interfaces);
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> sink) {
        for (Class<?> iface : type.getInterfaces()) {
            if (sink.add(iface)) {
                collectInterfaces(iface, sink);
            }
        }
    }

    /**
     * 单注解实例的 {@link AnnotatedElement} 适配器（v2.0 从 converter 迁入，随家族发现共享）。
     */
    private static final class SingleAnnotationElement implements AnnotatedElement {
        private final Annotation annotation;
        private final Annotation[] annotations;

        SingleAnnotationElement(Annotation annotation) {
            this.annotation = annotation;
            this.annotations = new Annotation[]{ annotation };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return annotationClass.isInstance(annotation) ? (T) annotation : null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotations.clone();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return annotations.clone();
        }
    }

}
