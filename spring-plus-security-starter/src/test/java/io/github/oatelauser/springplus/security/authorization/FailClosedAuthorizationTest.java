package io.github.oatelauser.springplus.security.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static io.github.oatelauser.springplus.security.annotation.RequiresRole.ROLE_SUPER_ADMIN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V14 + V07 运行期 fail-closed 回归：超管短路不依赖集合类型/顺序；注解存在但权限为空必须拒绝。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class FailClosedAuthorizationTest {

    // ───────────── V14：超管短路（Set / List 乱序三态） ─────────────

    @Test
    void superAdminMatchedRegardlessOfCollectionTypeOrOrder() {
        // Set 形态（Spring Security 常规 User 的 authorities）——原 instanceof List 实现恒为 false 的 bug 场景
        Set<GrantedAuthority> asSet = Set.of(new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(ROLE_SUPER_ADMIN));
        assertTrue(Authorizer.determineAdminRole(asSet));

        // List 乱序：超管不在首位
        List<GrantedAuthority> unordered = List.of(new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(ROLE_SUPER_ADMIN));
        assertTrue(Authorizer.determineAdminRole(unordered));

        // 无超管 / 空
        assertFalse(Authorizer.determineAdminRole(Set.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertFalse(Authorizer.determineAdminRole(List.of()));
    }

    // ───────────── V07 运行期：注解存在但权限为空 → DENY ─────────────

    /** 桩授权器：永远返回空权限集合，模拟配置错误或自定义授权器解析失败 */
    static class EmptyAuthorityAuthorizer extends GrantedAuthorityAuthorizer {

        @Override
        protected Set<String> processAuthorizedAnnotation(Annotation annotation, Method specificMethod) {
            return Set.of();
        }

        @Override
        public Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass) {
            return null;
        }

        @Override
        public Class<? extends Annotation>[] getAnnotationClass() {
            return new Class[]{ io.github.oatelauser.springplus.security.annotation.RequiresRole.class };
        }
    }

    @Test
    void annotationPresentButEmptyRequirementsDenied() throws Exception {
        EmptyAuthorityAuthorizer authorizer = new EmptyAuthorityAuthorizer();
        Authentication authentication = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");
        Method method = Sample.class.getDeclaredMethod("guarded");

        // 注解存在（任意非 null）+ 解析为空 → 拒绝
        AnnotationMethodInvocation present = new AnnotationMethodInvocation(Sample.class, method,
                method.getAnnotation(io.github.oatelauser.springplus.security.annotation.RequiresRole.class),
                null, null);
        AuthorizationResult denied = authorizer.verify(authentication, present);
        assertFalse(denied.isGranted(), "空权限配置必须 fail-closed（CWE-862）");

        // 注解不存在（本授权器不处理）→ 放行语义保持
        AnnotationMethodInvocation absent = new AnnotationMethodInvocation(Sample.class, method,
                null, null, null);
        assertTrue(authorizer.verify(authentication, absent).isGranted());
    }

    static class Sample {

        @io.github.oatelauser.springplus.security.annotation.RequiresRole(role = "ADMIN")
        public void guarded() {
        }
    }

}
