package org.example.expert.domain.todo.service;

import org.example.expert.client.WeatherClient;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.dto.request.TodoSaveRequest;
import org.example.expert.domain.todo.dto.response.TodoResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class TodoServiceTest {

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private WeatherClient weatherClient;

    @BeforeEach
    void setUp() {
        managerRepository.deleteAll();
        todoRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveTodo_스프링_프록시를_통해_todo를_저장한다() {
        // given
        User user = userRepository.save(new User("user@example.com", "nickname", "password", UserRole.USER));
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

        assertThat(managerRepository.findByTodoIdWithUser(response.getId()))
                .singleElement()
                .satisfies(manager -> {
                    assertThat(manager.getUser().getId()).isEqualTo(user.getId());
                    assertThat(manager.getUser().getEmail()).isEqualTo(user.getEmail());
                });
    }

    @Test
    void getTodos_weather와_수정일_기간으로_검색한다() {
        // given
        User user = userRepository.save(new User("user@example.com", "nickname", "password", UserRole.USER));
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 16, 12, 0);

        Todo oldSunnyTodo = todoRepository.save(new Todo("old sunny", "contents", "Sunny", user));
        Todo recentSunnyTodo = todoRepository.save(new Todo("recent sunny", "contents", "Sunny", user));
        Todo recentRainyTodo = todoRepository.save(new Todo("recent rainy", "contents", "Rainy", user));

        updateModifiedAt(oldSunnyTodo.getId(), baseTime.minusDays(3));
        updateModifiedAt(recentSunnyTodo.getId(), baseTime.minusDays(1));
        updateModifiedAt(recentRainyTodo.getId(), baseTime.minusHours(1));

        // when
        Page<TodoResponse> result = todoService.getTodos(
                1,
                10,
                "Sunny",
                baseTime.minusDays(2),
                baseTime
        );

        // then
        assertThat(result.getContent())
                .extracting(TodoResponse::getTitle)
                .containsExactly("recent sunny");
    }

    @Test
    void getTodos_weather와_수정일_조건이_없으면_전체를_수정일_내림차순으로_조회한다() {
        // given
        User user = userRepository.save(new User("user@example.com", "nickname", "password", UserRole.USER));
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 16, 12, 0);

        Todo oldTodo = todoRepository.save(new Todo("old", "contents", "Sunny", user));
        Todo recentTodo = todoRepository.save(new Todo("recent", "contents", "Rainy", user));

        updateModifiedAt(oldTodo.getId(), baseTime.minusDays(1));
        updateModifiedAt(recentTodo.getId(), baseTime);

        // when
        Page<TodoResponse> result = todoService.getTodos(1, 10, null, null, null);

        // then
        assertThat(result.getContent())
                .extracting(TodoResponse::getTitle)
                .containsExactly("recent", "old");
    }

    @Test
    void getTodos_수정일_시작과_끝_조건은_각각_생략할_수_있다() {
        // given
        User user = userRepository.save(new User("user@example.com", "nickname", "password", UserRole.USER));
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 16, 12, 0);

        Todo oldTodo = todoRepository.save(new Todo("old", "contents", "Sunny", user));
        Todo middleTodo = todoRepository.save(new Todo("middle", "contents", "Sunny", user));
        Todo recentTodo = todoRepository.save(new Todo("recent", "contents", "Sunny", user));

        updateModifiedAt(oldTodo.getId(), baseTime.minusDays(3));
        updateModifiedAt(middleTodo.getId(), baseTime.minusDays(2));
        updateModifiedAt(recentTodo.getId(), baseTime.minusDays(1));

        // when
        Page<TodoResponse> fromOnlyResult = todoService.getTodos(1, 10, null, baseTime.minusDays(2), null);
        Page<TodoResponse> toOnlyResult = todoService.getTodos(1, 10, null, null, baseTime.minusDays(2));

        // then
        assertThat(fromOnlyResult.getContent())
                .extracting(TodoResponse::getTitle)
                .containsExactly("recent", "middle");
        assertThat(toOnlyResult.getContent())
                .extracting(TodoResponse::getTitle)
                .containsExactly("middle", "old");
    }

    private void updateModifiedAt(Long todoId, LocalDateTime modifiedAt) {
        jdbcTemplate.update(
                "UPDATE todos SET modified_at = ? WHERE id = ?",
                Timestamp.valueOf(modifiedAt),
                todoId
        );
    }
}
