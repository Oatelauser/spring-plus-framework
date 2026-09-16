package io.github.oatelauser.springplus.example.controller;

import io.github.oatelauser.springplus.security.annotation.RequiresAdminRole;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 声明式鉴权演示：细粒度角色控制由注解表达，认证（谁在请求）由
 * {@code SecurityExampleConfig} 的 FilterChain + HTTP Basic 提供。
 *
 * <pre>
 * curl -u admin:admin123 http://localhost:8080/secure/admin   → 00000（超管短路）
 * curl -u user:user123   http://localhost:8080/secure/admin   → 403（无 SUPER_ADMIN）
 * curl                    http://localhost:8080/secure/admin   → 401（未认证）
 * curl -u user:user123   http://localhost:8080/secure/profile → 00000（ROLE_USER 命中）
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
@RestController
@RequestMapping("/secure")
public class SecureController {

    /**
     * 超管端点：@RequiresAdminRole（经 @RequiresRole 元注解归并为 ROLE_SUPER_ADMIN）
     */
    @RequiresAdminRole
    @GetMapping("/admin")
    public SimpleResponse<String> admin() {
        return SimpleResponse.ok("super admin only");
    }

    /**
     * 普通角色端点
     */
    @RequiresRole(role = "USER")
    @GetMapping("/profile")
    public SimpleResponse<String> profile() {
        return SimpleResponse.ok("any authenticated USER");
    }

}
