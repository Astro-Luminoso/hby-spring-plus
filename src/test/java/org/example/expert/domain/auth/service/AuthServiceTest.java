package org.example.expert.domain.auth.service;

import org.example.expert.config.JwtUtil;
import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.SigninResponse;
import org.example.expert.domain.auth.dto.response.SignupResponse;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void signup_닉네임을_저장하고_토큰_생성에_사용한다() {
        // given
        SignupRequest request = new SignupRequest("user@example.com", "nickname", "password", "USER");
        User savedUser = new User(request.getEmail(), request.getNickname(), "encodedPassword", UserRole.USER);

        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtUtil.createToken(savedUser.getId(), savedUser.getEmail(), savedUser.getNickname(), UserRole.USER))
                .willReturn("Bearer signup-token");

        // when
        SignupResponse response = authService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(request.getEmail());
        assertThat(userCaptor.getValue().getNickname()).isEqualTo(request.getNickname());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encodedPassword");
        assertThat(userCaptor.getValue().getUserRole()).isEqualTo(UserRole.USER);
        assertThat(response.getBearerToken()).isEqualTo("Bearer signup-token");
    }

    @Test
    void signin_저장된_사용자_닉네임으로_토큰을_생성한다() {
        // given
        SigninRequest request = new SigninRequest("user@example.com", "password");
        User user = new User(request.getEmail(), "nickname", "encodedPassword", UserRole.USER);

        given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);
        given(jwtUtil.createToken(user.getId(), user.getEmail(), user.getNickname(), user.getUserRole()))
                .willReturn("Bearer signin-token");

        // when
        SigninResponse response = authService.signin(request);

        // then
        verify(jwtUtil).createToken(user.getId(), user.getEmail(), "nickname", UserRole.USER);
        assertThat(response.getBearerToken()).isEqualTo("Bearer signin-token");
    }

    @Test
    void signup_이메일이_다르면_중복_닉네임도_허용한다() {
        // given
        SignupRequest firstRequest = new SignupRequest("first@example.com", "sameNickname", "password", "USER");
        SignupRequest secondRequest = new SignupRequest("second@example.com", "sameNickname", "password", "USER");

        given(userRepository.existsByEmail(firstRequest.getEmail())).willReturn(false);
        given(userRepository.existsByEmail(secondRequest.getEmail())).willReturn(false);
        given(passwordEncoder.encode("password")).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtUtil.createToken(any(), eq(firstRequest.getEmail()), eq("sameNickname"), eq(UserRole.USER)))
                .willReturn("Bearer first-token");
        given(jwtUtil.createToken(any(), eq(secondRequest.getEmail()), eq("sameNickname"), eq(UserRole.USER)))
                .willReturn("Bearer second-token");

        // when
        SignupResponse firstResponse = authService.signup(firstRequest);
        SignupResponse secondResponse = authService.signup(secondRequest);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        List<User> savedUsers = userCaptor.getAllValues();
        assertThat(savedUsers).extracting(User::getNickname)
                .containsExactly("sameNickname", "sameNickname");
        assertThat(firstResponse.getBearerToken()).isEqualTo("Bearer first-token");
        assertThat(secondResponse.getBearerToken()).isEqualTo("Bearer second-token");
    }
}
