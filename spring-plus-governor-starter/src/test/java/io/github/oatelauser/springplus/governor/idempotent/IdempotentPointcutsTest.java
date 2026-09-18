package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.governor.annotation.Idempotent;
import io.github.oatelauser.springplus.governor.annotation.RepeatSubmit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdempotentPointcuts} 切点匹配：方法级、类级注解命中，未标注放行。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class IdempotentPointcutsTest {

    @Test
    void repeatSubmitMatchesAnnotatedMethod() throws Exception {
        Method annotated = Sample.class.getDeclaredMethod("pay");
        Method plain = Sample.class.getDeclaredMethod("query");

        assertTrue(IdempotentPointcuts.repeatSubmit().matches(annotated, Sample.class));
        assertFalse(IdempotentPointcuts.repeatSubmit().matches(plain, Sample.class));
    }

    @Test
    void repeatSubmitMatchesClassLevelAnnotation() throws Exception {
        Method anyMethod = ClassLevelSample.class.getDeclaredMethod("anything");
        assertTrue(IdempotentPointcuts.repeatSubmit().matches(anyMethod, ClassLevelSample.class));
    }

    @Test
    void idempotentMatchesItsOwnAnnotationOnly() throws Exception {
        Method create = Sample.class.getDeclaredMethod("create");
        assertTrue(IdempotentPointcuts.idempotent().matches(create, Sample.class));
        assertFalse(IdempotentPointcuts.repeatSubmit().matches(create, Sample.class),
                "@Idempotent 不应被 repeatSubmit 切点命中");
    }

    static class Sample {

        @RepeatSubmit
        public void pay() {
        }

        @Idempotent
        public Object create() {
            return null;
        }

        public void query() {
        }
    }

    @RepeatSubmit
    static class ClassLevelSample {

        public void anything() {
        }
    }

}
