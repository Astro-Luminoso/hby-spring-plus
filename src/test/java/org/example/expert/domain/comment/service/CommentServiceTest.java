package org.example.expert.domain.comment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.example.expert.domain.comment.dto.response.CommentResponse;
import org.example.expert.domain.comment.entity.Comment;
import org.example.expert.domain.comment.repository.CommentRepository;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void getComments_댓글_작성자들을_join_fetch로_한번에_조회한다() {
        // given
        User todoOwner = userRepository.save(new User("owner@example.com", "owner", "password", UserRole.USER));
        User firstCommenter = userRepository.save(new User("first@example.com", "first", "password", UserRole.USER));
        User secondCommenter = userRepository.save(new User("second@example.com", "second", "password", UserRole.USER));
        User thirdCommenter = userRepository.save(new User("third@example.com", "third", "password", UserRole.USER));

        Todo todo = todoRepository.save(new Todo("title", "contents", "Sunny", todoOwner));
        commentRepository.saveAll(List.of(
                new Comment("first comment", firstCommenter, todo),
                new Comment("second comment", secondCommenter, todo),
                new Comment("third comment", thirdCommenter, todo)
        ));

        commentRepository.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        List<CommentResponse> responses = commentService.getComments(todo.getId());

        // then
        assertThat(responses)
                .hasSize(3)
                .extracting(
                        CommentResponse::getContents,
                        response -> response.getUser().getEmail()
                )
                .containsExactlyInAnyOrder(
                        tuple("first comment", "first@example.com"),
                        tuple("second comment", "second@example.com"),
                        tuple("third comment", "third@example.com")
                );
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private void cleanUp() {
        commentRepository.deleteAll();
        managerRepository.deleteAll();
        todoRepository.deleteAll();
        userRepository.deleteAll();
    }
}
