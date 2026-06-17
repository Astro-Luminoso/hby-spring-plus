package org.example.expert.support;

import org.example.expert.config.JwtUtil;
import org.example.expert.domain.user.enums.UserRole;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Locale;

public final class SecurityMockMvcSupport {

    private SecurityMockMvcSupport() {
    }

    public static RequestPostProcessor authenticated(JwtUtil jwtUtil, long userId, UserRole userRole) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, bearerToken(jwtUtil, userId, userRole));
            return request;
        };
    }

    public static RequestPostProcessor authenticatedUser(JwtUtil jwtUtil, long userId) {
        return authenticated(jwtUtil, userId, UserRole.USER);
    }

    public static RequestPostProcessor authenticatedAdmin(JwtUtil jwtUtil, long userId) {
        return authenticated(jwtUtil, userId, UserRole.ADMIN);
    }

    public static String bearerToken(JwtUtil jwtUtil, long userId, UserRole userRole) {
        String roleName = userRole.name().toLowerCase(Locale.ROOT);
        return jwtUtil.createToken(userId, roleName + "@example.com", roleName, userRole);
    }
}
