package org.example.expert.config;

import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        jwtUtil.init();
    }

    @Test
    void createToken_닉네임_claim을_포함한다() throws Exception {
        // given
        Long userId = 1L;
        String email = "user@example.com";
        String nickname = "nickname";

        // when
        String bearerToken = jwtUtil.createToken(userId, email, nickname, UserRole.USER);
        String token = jwtUtil.substringToken(bearerToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JwtUtil.class
                .getMethod("extractClaims", String.class)
                .invoke(jwtUtil, token);

        // then
        assertThat(claims.get("sub")).isEqualTo(String.valueOf(userId));
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.get("nickname")).isEqualTo(nickname);
        assertThat(claims.get("userRole")).isEqualTo(UserRole.USER.name());
    }
}
