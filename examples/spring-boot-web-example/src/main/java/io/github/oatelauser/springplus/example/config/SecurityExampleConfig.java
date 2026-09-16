package io.github.oatelauser.springplus.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全基线示例（V08 红线的对照样例）。
 * <p>
 * <b>红线：spring-plus-security 只做授权（注解声明），认证与 FilterChain 必须由业务方自配，
 * 且默认方向 denyAll——放行走白名单。</b>未配置本类的应用所有接口裸奔。
 * <p>
 * 演示账号（仅示例，内存态）：
 * <ul>
 *   <li>{@code admin / admin123} —— ROLE_SUPER_ADMIN（走超管短路）</li>
 *   <li>{@code user / user123} —— ROLE_USER</li>
 * </ul>
 * 认证方式用 HTTP Basic 便于 curl 验证：{@code curl -u admin:admin123 ...}
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
@Configuration
@EnableWebSecurity
public class SecurityExampleConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 授权基线：默认全部拒绝，显式白名单放行（fail-closed）
                .authorizeHttpRequests(auth -> auth
                        // 框架功能演示端点保持免认证（行为与历史版本一致）
                        .requestMatchers("/v2-test/**", "/idempotent-test/**").permitAll()
                        // 鉴权演示端点：要求已认证，细粒度角色交给 @RequiresRole 族注解
                        .requestMatchers("/secure/**").authenticated()
                        // 其余一切未声明路径：拒绝
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withDefaultPasswordEncoder()
                        .username("admin")
                        .password("admin123")
                        .roles("SUPER_ADMIN")
                        .build(),
                User.withDefaultPasswordEncoder()
                        .username("user")
                        .password("user123")
                        .roles("USER")
                        .build());
    }

}
