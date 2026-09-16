package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresPermission;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 启动期校验：空配置注解在启动期失败（fail-closed），合法配置零打扰。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class RequiresAnnotationValidatorTest {

    private final RequiresAnnotationValidator validator = new RequiresAnnotationValidator();

    @Test
    void emptyRoleFailsAtStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.validate(EmptyRoleSample.class));
        assertTrue(ex.getMessage().contains("@RequiresRole"), ex.getMessage());
    }

    @Test
    void blankPermissionFailsAtStartup() {
        IllegalStateException noSource = assertThrows(IllegalStateException.class,
                () -> validator.validate(BlankSourceSample.class));
        assertTrue(noSource.getMessage().contains("source"), noSource.getMessage());

        IllegalStateException noAction = assertThrows(IllegalStateException.class,
                () -> validator.validate(BlankActionSample.class));
        assertTrue(noAction.getMessage().contains("action"), noAction.getMessage());
    }

    @Test
    void validAnnotationsPassSilently() {
        assertDoesNotThrow(() -> validator.validate(ValidSample.class));
        assertDoesNotThrow(() -> validator.validate(PlainSample.class));
    }

    static class EmptyRoleSample {

        @RequiresRole(role = {})
        public void guarded() {
        }
    }

    static class BlankSourceSample {

        @RequiresPermission(source = "", action = "delete")
        public void guarded() {
        }
    }

    static class BlankActionSample {

        @RequiresPermission(source = "user")
        public void guarded() {
        }
    }

    static class ValidSample {

        @RequiresRole(role = "ADMIN")
        public void methodLevel() {
        }

        @RequiresPermission(source = "user", action = "delete")
        public void permissioned() {
        }
    }

    static class PlainSample {

        public void plain() {
        }
    }

}
