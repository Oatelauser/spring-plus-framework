package io.github.oatelauser.springplus.web.error.advice;

import io.github.oatelauser.springplus.web.autoconfigure.GlobalExceptionAdvice;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * advice 契约校验器行为回归：规则一（Exception/Throwable 兜底）/ 规则二（无显式
 * order）/ 全局兜底豁免。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
class ModuleAdviceContractValidatorTest {

    /** 违反规则一：Exception 兜底（即使有 order 也要告警） */
    @RestControllerAdvice
    @Order(100)
    static class CatchAllModuleAdvice {

        @ExceptionHandler(Exception.class)
        public Object catchAll(Exception ex) {
            return null;
        }
    }

    /** 违反规则二：窄类型但无显式 order */
    @RestControllerAdvice
    static class UnorderedModuleAdvice {

        @ExceptionHandler(IllegalStateException.class)
        public Object narrow(IllegalStateException ex) {
            return null;
        }
    }

    /** 守约形态：窄类型 + 显式 order，不应产生任何告警 */
    @RestControllerAdvice
    @Order(300)
    static class WellFormedModuleAdvice {

        @ExceptionHandler(IllegalArgumentException.class)
        public Object narrow(IllegalArgumentException ex) {
            return null;
        }
    }

    @Test
    void warnsOnCatchAllAndMissingOrderButNotWellFormed() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("catchAll", new CatchAllModuleAdvice());
        beanFactory.registerSingleton("unordered", new UnorderedModuleAdvice());
        beanFactory.registerSingleton("wellFormed", new WellFormedModuleAdvice());

        List<String> warnings = new ModuleAdviceContractValidator(beanFactory).validate();

        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("catchAll") && warnings.get(0).contains("Exception"));
        assertTrue(warnings.get(1).contains("unordered") && warnings.get(1).contains("@Order"));
    }

    @Test
    void globalAdviceAndItsSubclassesAreExempt() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        // 类型豁免只看类型：engine 不参与校验，传 null 不触碰任何 handler
        beanFactory.registerSingleton("globalExceptionAdvice",
                new GlobalExceptionAdvice((ExceptionOutputEngine) null));

        List<String> warnings = new ModuleAdviceContractValidator(beanFactory).validate();

        assertTrue(warnings.isEmpty());
    }

}
