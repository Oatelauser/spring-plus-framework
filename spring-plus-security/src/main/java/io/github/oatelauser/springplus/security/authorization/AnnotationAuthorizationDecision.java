package io.github.oatelauser.springplus.security.authorization;

import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 保留必须权限（缺少的权限）的授权结果对象
 * <p>
 * 在处理授权拒接的时候，返回未分配的权限
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-20
 * @see AuthorityAuthorizationDecision
 * @since 1.0
 */
@SuppressWarnings("all")
public class AnnotationAuthorizationDecision extends AuthorizationDecision {

    public static final AnnotationAuthorizationDecision ALLOW
            = new AnnotationAuthorizationDecision(true, List.of());

    private final Collection<GrantedAuthority> authorities;

    public AnnotationAuthorizationDecision(boolean granted, Collection<GrantedAuthority> authorities) {
        super(granted);
        this.authorities = authorities;
    }

    public Collection<GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "granted=" + isGranted() + ", authorities=" + this.authorities + ']';
    }

    public static AnnotationAuthorizationDecision deny(Collection<GrantedAuthority> authorities) {
        return new AnnotationAuthorizationDecision(false, authorities);
    }

    public static AnnotationAuthorizationDecision deny(Set<String> authorities) {
        return new AnnotationAuthorizationDecision(false, authorities.stream()
                .map(authority -> new GrantedAuthority() {
                    @Override
                    public String getAuthority() {
                        return authority;
                    }
                }).collect(Collectors.toSet()));
    }

}
