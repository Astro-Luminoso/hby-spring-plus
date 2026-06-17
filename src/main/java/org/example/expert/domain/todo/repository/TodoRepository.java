package org.example.expert.domain.todo.repository;

import org.example.expert.domain.todo.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TodoRepository extends JpaRepository<Todo, Long>, TodoRepositoryCustom {

    @Query(
            value = "SELECT t FROM Todo t " +
                    "LEFT JOIN FETCH t.user u " +
                    "WHERE (:weather IS NULL OR t.weather = :weather) " +
                    "AND (:modifiedAtFrom IS NULL OR t.modifiedAt >= :modifiedAtFrom) " +
                    "AND (:modifiedAtTo IS NULL OR t.modifiedAt <= :modifiedAtTo) " +
                    "ORDER BY t.modifiedAt DESC",
            countQuery = "SELECT COUNT(t) FROM Todo t " +
                    "WHERE (:weather IS NULL OR t.weather = :weather) " +
                    "AND (:modifiedAtFrom IS NULL OR t.modifiedAt >= :modifiedAtFrom) " +
                    "AND (:modifiedAtTo IS NULL OR t.modifiedAt <= :modifiedAtTo)"
    )
    Page<Todo> searchTodos(
            @Param("weather") String weather,
            @Param("modifiedAtFrom") LocalDateTime modifiedAtFrom,
            @Param("modifiedAtTo") LocalDateTime modifiedAtTo,
            Pageable pageable
    );

}
