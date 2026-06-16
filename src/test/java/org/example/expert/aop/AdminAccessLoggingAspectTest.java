package org.example.expert.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.example.expert.domain.user.dto.request.UserRoleChangeRequest;
import org.example.expert.domain.user.controller.UserAdminController;
import org.example.expert.domain.user.service.UserAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(AdminAccessLoggingAspect.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAccessLoggingAspectTest {

    private static final long ADMIN_ID = 1L;
    private static final long TARGET_USER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(AdminAccessLoggingAspect.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        aspectLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void changeUserRole_호출_전_관리자_접근_로그를_남긴다() throws Exception {
        doAnswer(invocation -> {
            assertThat(listAppender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains(
                                    "Admin Access Log",
                                    "User ID: " + ADMIN_ID,
                                    "Request URL: /admin/users/" + TARGET_USER_ID,
                                    "Method: changeUserRole"
                            ));
            return null;
        }).when(userAdminService).changeUserRole(eq(TARGET_USER_ID), any(UserRoleChangeRequest.class));

        mockMvc.perform(patch("/admin/users/{userId}", TARGET_USER_ID)
                        .requestAttr("userId", ADMIN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        verify(userAdminService).changeUserRole(eq(TARGET_USER_ID), any(UserRoleChangeRequest.class));
    }
}
