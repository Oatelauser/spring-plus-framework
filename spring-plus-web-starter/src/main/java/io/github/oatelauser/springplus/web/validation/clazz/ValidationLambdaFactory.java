package io.github.oatelauser.springplus.web.validation.clazz;

import io.github.oatelauser.springplus.web.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ReflectionUtils;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 校验方法执行工厂类
 * <p>
 * 使用 {@link LambdaMetafactory} 将校验方法转换为高效的函数式接口调用，
 * 避免反射调用的性能开销。</p>
 *
 * <h2>性能对比</h2>
 * <ul>
 *   <li>反射调用：每次调用都需要进行安全检查和参数包装</li>
 *   <li>LambdaMetafactory：首次生成 Lambda 后，后续调用与直接方法调用性能相近</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-27
 * @since 1.0
 */
@Slf4j
class ValidationLambdaFactory {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<MethodCacheKey, Optional<ValidationInvoker>> INVOKER_CACHE = new ConcurrentHashMap<>();

    static Optional<ValidationInvoker> getOrCreateInvoker(String methodName, Class<?> targetClass) {
        MethodCacheKey cacheKey = new MethodCacheKey(targetClass, methodName);
        return INVOKER_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                return createInvoker(methodName, targetClass);
            } catch (Throwable e) {
                log.warn("Failed to create lambda invoker for {}.{}, falling back to reflection",
                        targetClass.getName(), methodName, e);
                return Optional.empty();
            }
        });
    }

    private static Optional<ValidationInvoker> createInvoker(String methodName, Class<?> targetClass) throws Throwable {
        Method method = findValidationMethod(methodName, targetClass);
        if (method == null) {
            log.debug("Validation method [{}] not found in class [{}]", methodName, targetClass.getName());
            return Optional.empty();
        }

        Class<?> returnType = method.getReturnType();
        // 根据返回类型创建不同的调用器
        if (ValidationResult.class.isAssignableFrom(returnType)) {
            return Optional.of(createValidationResultInvoker(method, targetClass));
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return Optional.of(createBooleanInvoker(methodName, method, targetClass));
        }

        log.warn("Unsupported return type [{}] for method [{}.{}]",
                returnType.getName(), targetClass.getName(), methodName);
        return Optional.empty();
    }

    @SuppressWarnings("all")
    private static ValidationInvoker createValidationResultInvoker(Method method,
            Class<?> targetClass) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(targetClass, LOOKUP);
        MethodHandle methodHandle = privateLookup.unreflect(method);
        CallSite callSite = LambdaMetafactory.metafactory(
                privateLookup,
                "apply",                                          // 函数式接口方法名
                MethodType.methodType(ValidationFunction.class),  // 工厂方法类型
                MethodType.methodType(ValidationResult.class, Object.class),  // 擦除后的方法类型
                methodHandle,                                     // 实际方法句柄
                MethodType.methodType(ValidationResult.class, targetClass)    // 实际方法类型
        );
        ValidationFunction<Object> function = (ValidationFunction<Object>) callSite.getTarget().invokeExact();
        return new ValidationResultInvoker(function);
    }

    @SuppressWarnings("all")
    private static ValidationInvoker createBooleanInvoker(String methodName,
            Method method, Class<?> targetClass) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(targetClass, LOOKUP);
        MethodHandle methodHandle = privateLookup.unreflect(method);

        // 处理 boolean 和 Boolean 的差异
        MethodType samMethodType;
        if (method.getReturnType() == boolean.class) {
            samMethodType = MethodType.methodType(boolean.class, Object.class);
        } else {
            // Boolean 类型需要特殊处理，转换为 boolean
            methodHandle = methodHandle.asType(
                    MethodType.methodType(boolean.class, targetClass));
            samMethodType = MethodType.methodType(boolean.class, Object.class);
        }

        CallSite callSite = LambdaMetafactory.metafactory(
                privateLookup,
                "test",                               // 函数式接口方法名
                MethodType.methodType(BooleanValidationFunction.class), // 工厂方法类型
                samMethodType,                                          // 擦除后的方法类型
                methodHandle,                                           // 实际方法句柄
                MethodType.methodType(boolean.class, targetClass)       // 实际方法类型
        );

        BooleanValidationFunction<Object> function =
                (BooleanValidationFunction<Object>) callSite.getTarget().invokeExact();
        return new BooleanValidationInvoker(methodName, function);
    }

    @Nullable
    static Method findValidationMethod(String methodName, Class<?> targetClass) {
        Method method = ReflectionUtils.findMethod(targetClass, methodName);
        if (method != null && isValidValidationMethod(method)) {
            return method;
        }
        return null;
    }

    /**
     * 检查方法是否是有效的校验方法
     */
    static boolean isValidValidationMethod(Method method) {
        // 必须是无参方法
        if (method.getParameterCount() != 0) {
            return false;
        }
        // 不能是静态方法
        if (Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        // 返回类型必须是 ValidationResult 或 boolean
        Class<?> returnType = method.getReturnType();
        return ValidationResult.class.isAssignableFrom(returnType) ||
                returnType == boolean.class ||
                returnType == Boolean.class;
    }


    private record MethodCacheKey(Class<?> clazz, String methodName) {
    }

    protected interface ValidationInvoker {

        /**
         * 执行校验
         *
         * @param target 目标对象
         * @return 校验结果
         */
        ValidationResult invoke(Object target);

    }

    /**
     * ValidationResult 类型的调用器
     */
    private record ValidationResultInvoker(ValidationFunction<Object> function) implements ValidationInvoker {

        @Override
        public ValidationResult invoke(Object target) {
            ValidationResult result = function.apply(target);
            return result != null ? result : ValidationResult.success();
        }
    }

    /**
     * Boolean 类型的调用器
     */
    private record BooleanValidationInvoker(String methodName,
            BooleanValidationFunction<Object> function) implements ValidationInvoker {
        @Override
        public ValidationResult invoke(Object target) {
            boolean result = function.test(target);
            return result ? ValidationResult.success() :
                    ValidationResult.failure("Validation failed in method: " + methodName);
        }
    }

    /**
     * 校验方法函数式接口 - 返回 ValidationResult
     *
     * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
     * @date 2026-01-27
     * @see ValidationLambdaFactory
     * @since 1.0
     */
    protected interface ValidationFunction<T> {

        /**
         * 执行校验
         *
         * @param target 目标对象
         * @return 校验结果
         */
        ValidationResult apply(T target);

    }

    /**
     * 校验方法函数式接口 - 返回 boolean
     *
     * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
     * @date 2026-01-27
     * @see ValidationLambdaFactory
     * @since 1.0
     */
    protected interface BooleanValidationFunction<T> {

        /**
         * 执行校验
         *
         * @param target 目标对象
         * @return true 表示校验通过
         */
        boolean test(T target);

    }

}
