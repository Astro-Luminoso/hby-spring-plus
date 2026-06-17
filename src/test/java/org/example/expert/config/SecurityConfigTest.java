package org.example.expert.config;

import org.example.expert.domain.auth.controller.AuthController;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.SigninResponse;
import org.example.expert.domain.auth.dto.response.SignupResponse;
import org.example.expert.domain.auth.service.AuthService;
import org.example.expert.domain.todo.controller.TodoController;
import org.example.expert.domain.todo.dto.response.TodoResponse;
import org.example.expert.domain.todo.service.TodoService;
import org.example.expert.domain.user.controller.UserAdminController;
import org.example.expert.domain.user.dto.request.UserRoleChangeRequest;
import org.example.expert.domain.user.dto.response.UserResponse;
import org.example.expert.domain.user.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.example.expert.support.SecurityMockMvcSupport.authenticatedAdmin;
import static org.example.expert.support.SecurityMockMvcSupport.authenticatedUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, TodoController.class, UserAdminController.class})
@Import({SecurityConfig.class, JwtUtil.class})
class SecurityConfigTest {

    private static final long USER_ID = 1L;
    private static final long ADMIN_ID = 2L;
    private static final long TODO_ID = 10L;
    private static final long TARGET_USER_ID = 20L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private AuthService authService;

    @MockBean
    private TodoService todoService;

    @MockBean
    private UserAdminService userAdminService;

    @Test
    void auth_경로는_JWT_없이_접근_가능하다() throws Exception {
        when(authService.signin(any(SigninRequest.class)))
                .thenReturn(new SigninResponse("Bearer signin-token"));
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(new SignupResponse("Bearer signup-token"));

        mockMvc.perform(post("/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bearerToken").value("Bearer signin-token"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"nickname\":\"new-user\",\"password\":\"password\",\"userRole\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bearerToken").value("Bearer signup-token"));
    }

    @Test
    void 보호된_엔드포인트는_JWT가_없으면_거부된다() throws Exception {
        mockMvc.perform(get("/todos/{todoId}", TODO_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(todoService);
    }

    @Test
    void 유효한_JWT로_보호된_엔드포인트에_접근할_수_있다() throws Exception {
        when(todoService.getTodo(TODO_ID)).thenReturn(todoResponse());

        mockMvc.perform(get("/todos/{todoId}", TODO_ID)
                        .with(authenticatedUser(jwtUtil, USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TODO_ID));

        verify(todoService).getTodo(TODO_ID);
    }

    @Test
    void USER_권한은_admin_경로에_접근할_수_없다() throws Exception {
        mockMvc.perform(patch("/admin/users/{userId}", TARGET_USER_ID)
                        .with(authenticatedUser(jwtUtil, USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void ADMIN_권한은_admin_경로에_접근할_수_있다() throws Exception {
        mockMvc.perform(patch("/admin/users/{userId}", TARGET_USER_ID)
                        .with(authenticatedAdmin(jwtUtil, ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        verify(userAdminService).changeUserRole(eq(TARGET_USER_ID), any(UserRoleChangeRequest.class));
    }

    private TodoResponse todoResponse() {
        return new TodoResponse(
                TODO_ID,
                "title",
                "contents",
                "Sunny",
                new UserResponse(USER_ID, "user@example.com"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
