package org.example.expert.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.expert.domain.user.enums.UserRole;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

final class SecurityPolicy {

    static final String[] PUBLIC_AUTH_MATCHERS = {"/auth", "/auth/**"};
    static final String ADMIN_MATCHER = "/admin/**";

    private static final String ROLE_PREFIX = "ROLE_";
    private static final RequestMatcher[] PUBLIC_AUTH_REQUEST_MATCHERS = Arrays.stream(PUBLIC_AUTH_MATCHERS)
            .map(AntPathRequestMatcher::new)
            .toArray(RequestMatcher[]::new);

    private SecurityPolicy() {
    }

    static boolean isPublicAuthRequest(HttpServletRequest request) {
        return Arrays.stream(PUBLIC_AUTH_REQUEST_MATCHERS)
                .anyMatch(matcher -> matcher.matches(request));
    }

    static String authority(UserRole userRole) {
        return ROLE_PREFIX + userRole.name();
    }
}
