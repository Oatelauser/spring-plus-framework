package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.governor.annotation.RepeatSubmit;
import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.response.BusinessStatus;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 防重复提交拦截器。
 */
@SuppressWarnings("SpellCheckingInspection")
public class RepeatSubmitInterceptor implements MethodInterceptor {

    private final IdempotentStore store;
    private final KeyStrategyResolver strategyResolver;

    public RepeatSubmitInterceptor(IdempotentStore store, KeyStrategyResolver strategyResolver) {
        this.store = store;
        this.strategyResolver = strategyResolver;
    }

    @Override
    public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
        RepeatSubmit annotation = annotation(invocation);
        String key = resolveKey(annotation.spel(), annotation.strategy(), invocation);
        Duration ttl = Duration.ofMillis(annotation.unit().toMillis(annotation.window()));
        if (!store.setIfAbsent(key, "1", ttl)) {
            throw new ServiceException(BusinessStatus.REPEAT_SUBMIT);
        }
        try {
            return invocation.proceed();
        } catch (Throwable throwable) {
            store.delete(key);
            throw throwable;
        }
    }

    private String resolveKey(String spel, Class<? extends IdempotentKeyStrategy> strategyClass, MethodInvocation invocation) {
        IdempotentKeyStrategy strategy = strategyResolver.resolve(strategyClass);
        String spelValue = (spel == null || spel.isBlank()) ? null : evalSpelValue(spel, invocation);
        String raw = strategy.extract(spel, spelValue, invocation);
        if (raw == null) {
            throw new ServiceException(BusinessStatus.REPEAT_SUBMIT);
        }
        return "idempotent:" + strategy.strategyName() + ":" + sha256(raw);
    }

    private static String evalSpelValue(String spel, MethodInvocation invocation) {
        EvaluationContext context = new StandardEvaluationContext();
        java.lang.reflect.Parameter[] parameters = invocation.getMethod().getParameters();
        Object[] args = invocation.getArguments();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), i < args.length ? args[i] : null);
        }
        Expression expression = new SpelExpressionParser().parseExpression(spel);
        Object value = expression.getValue(context);
        return value == null ? null : value.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static RepeatSubmit annotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        RepeatSubmit annotation = AnnotatedElementUtils.findMergedAnnotation(method, RepeatSubmit.class);
        if (annotation != null) {
            return annotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(invocation.getThis().getClass(), RepeatSubmit.class);
    }
}
