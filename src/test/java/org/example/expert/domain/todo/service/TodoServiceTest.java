package org.example.expert.domain.todo.service;

import org.example.expert.client.WeatherClient;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.todo.dto.request.TodoSaveRequest;
import org.example.expert.domain.todo.dto.response.TodoSaveResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class TodoServiceTest {

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private WeatherClient weatherClient;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveTodo_스프링_프록시를_통해_todo를_저장한다() {
        // given
        User user = userRepository.save(new User("user@example.com", "password", UserRole.USER));
        AuthUser authUser = new AuthUser(user.getId(), user.getEmail(), user.getUserRole());
        TodoSaveRequest request = new TodoSaveRequest("title", "contents");

        given(weatherClient.getTodayWeather()).willReturn("Sunny");

        // when
        TodoSaveResponse response = todoService.saveTodo(authUser, request);

        // then
        assertThat(AopUtils.isAopProxy(todoService)).isTrue();

        Todo savedTodo = todoRepository.findById(response.getId()).orElseThrow();
        assertThat(savedTodo.getTitle()).isEqualTo("title");
        assertThat(savedTodo.getContents()).isEqualTo("contents");
        assertThat(savedTodo.getWeather()).isEqualTo("Sunny");
        assertThat(savedTodo.getUser().getId()).isEqualTo(user.getId());
    }
}
