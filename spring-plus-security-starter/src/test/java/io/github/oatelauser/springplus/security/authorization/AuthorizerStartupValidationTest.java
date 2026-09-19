package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresPermission;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 启动期校验（内聚于授权器自身）：空配置注解在启动期失败（fail-closed），合法配置零打扰。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-17
 * @since 1.1.0
 */
class AuthorizerStartupValidationTest {

    private final RequiresRoleAuthorizer roleAuthorizer = new RequiresRoleAuthorizer();
    private final RequiresPermissionAuthorizer permissionAuthorizer = new RequiresPermissionAuthorizer();

    // ───────────── @RequiresRole（RequiresRoleAuthorizer 自扫） ─────────────

    @Test
    void emptyRoleFailsAtStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> roleAuthorizer.validateRequiresRole(EmptyRoleSample.class));
        assertTrue(ex.getMessage().contains("@RequiresRole"), ex.getMessage());
    }

    @Test
    void validRolePassesSilently() {
        assertDoesNotThrow(() -> roleAuthorizer.validateRequiresRole(ValidRoleSample.class));
        assertDoesNotThrow(() -> roleAuthorizer.validateRequiresRole(PlainSample.class));
    }

    // ───────────── @RequiresPermission（RequiresPermissionAuthorizer 自扫） ─────────────

    @Test
    void blankPermissionFailsAtStartup() {
        IllegalStateException noSource = assertThrows(IllegalStateException.class,
                () -> permissionAuthorizer.validateRequiresPermission(BlankSourceSample.class));
        assertTrue(noSource.getMessage().contains("source"), noSource.getMessage());

        IllegalStateException noAction = assertThrows(IllegalStateException.class,
                () -> permissionAuthorizer.validateRequiresPermission(BlankActionSample.class));
        assertTrue(noAction.getMessage().contains("action"), noAction.getMessage());
    }

    @Test
    void validPermissionPassesSilently() {
        assertDoesNotThrow(() -> permissionAuthorizer.validateRequiresPermission(ValidPermissionSample.class));
        assertDoesNotThrow(() -> permissionAuthorizer.validateRequiresPermission(PermissionOnlySample.class));
        assertDoesNotThrow(() -> permissionAuthorizer.validateRequiresPermission(PlainSample.class));
    }

    // ───────────── 样本 ─────────────

    static class EmptyRoleSample {

        @RequiresRole(role = {})
        public void guarded() {
        }
    }

    static class ValidRoleSample {

        @RequiresRole(role = "ADMIN")
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

    /** permission 完整表达式独立使用：合法，不要求 source/action（与运行期语义对齐） */
    static class PermissionOnlySample {

        @RequiresPermission(permission = "user:delete")
        public void guarded() {
        }
    }

    static class ValidPermissionSample {

        @RequiresPermission(source = "user", action = "delete")
        public void guarded() {
        }
    }

    static class PlainSample {

        public void plain() {
        }
    }

}
