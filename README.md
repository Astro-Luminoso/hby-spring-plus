# SPRING PLUS

Java 17, Spring Boot 3.3.3 기반 일정 관리 API입니다. 아래 내용은 `git log`의 단계별 커밋과 현재 코드 변경 사항을 기준으로, 각 요구사항에서 무엇을 바꿨고 어떻게 동작하는지 정리한 문서입니다.

## 변경 이력 기준

- `b741ba7 level1 - 1 fix transactional`
- `f0f6152 level 1-2 update jwt and User Entity`
- `a6e9c78 level 1-3 update todo jpa`
- `740f307 level 1-4 fix logging in aop`
- `81ebee5 level 2-1 add cascade`
- `1b72581 level 2-2 solve N+1 issue`
- `d134138 level 2-3 apply QueryDSL`
- `c7808e0 level 2-4 apply spring security`
- 현재 작업 트리: `JwtFilter`의 잘못된 `Authorization` 헤더 처리 보완

## 실행 및 검증

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew test
```

전체 테스트는 위 명령으로 실행합니다.

## 요구사항 1. `@Transactional` read-only 저장 오류 수정

### 요구사항

`POST /todos` 호출 시 read-only 커넥션에서 `insert`가 실행되어 발생하던 저장 오류를 제거해야 합니다.

### 변경된 코드

- `src/main/java/org/example/expert/domain/todo/service/TodoService.java:25`
  - 서비스 클래스 기본값은 `@Transactional(readOnly = true)`로 유지했습니다.
- `src/main/java/org/example/expert/domain/todo/service/TodoService.java:31`
  - 쓰기 작업인 `saveTodo()`에 메서드 레벨 `@Transactional`을 추가했습니다.
- `src/main/java/org/example/expert/domain/todo/controller/TodoController.java:24`
  - `POST /todos` 요청이 `todoService.saveTodo()`로 위임됩니다.

### 동작 방식

조회 메서드는 클래스 레벨의 read-only 트랜잭션을 사용합니다. 반면 `saveTodo()`는 `todoRepository.save(newTodo)`를 호출하는 쓰기 작업이므로, 메서드 레벨 `@Transactional`이 클래스 설정을 덮어써 쓰기 가능한 트랜잭션으로 실행됩니다.

### 검증 포인트

- `POST /todos` 호출 시 read-only connection 오류 없이 Todo가 저장됩니다.
- `TodoServiceTest`의 `saveTodo_스프링_프록시를_통해_todo를_저장한다` 테스트에서 저장 결과와 프록시 적용 여부를 확인합니다.

## 요구사항 2. User nickname 추가 및 JWT nickname claim 포함

### 요구사항

User 정보에 `nickname`을 추가하고, 닉네임 중복은 허용해야 합니다. 프론트엔드가 JWT에서 닉네임을 읽을 수 있도록 토큰에 `nickname` claim도 포함해야 합니다.

### 변경된 코드

- `src/main/java/org/example/expert/domain/auth/dto/request/SignupRequest.java:16`
  - 회원가입 요청 DTO에 `nickname` 필드를 추가했습니다.
- `src/main/java/org/example/expert/domain/user/entity/User.java:20`
  - `nickname` 컬럼을 추가했습니다. `unique` 제약이 없으므로 중복 닉네임을 허용합니다.
- `src/main/java/org/example/expert/domain/auth/service/AuthService.java:38`
  - 회원가입 시 요청 닉네임을 `User`에 저장합니다.
- `src/main/java/org/example/expert/domain/auth/service/AuthService.java:46`
  - 회원가입 토큰 생성 시 저장된 닉네임을 전달합니다.
- `src/main/java/org/example/expert/domain/auth/service/AuthService.java:60`
  - 로그인 토큰 생성 시 사용자 닉네임을 전달합니다.
- `src/main/java/org/example/expert/config/JwtUtil.java:37`
  - `createToken` 파라미터에 `nickname`을 추가했습니다.
- `src/main/java/org/example/expert/config/JwtUtil.java:44`
  - JWT payload에 `nickname` claim을 추가했습니다.

### 동작 방식

회원가입 요청의 `nickname`은 `User` 엔티티에 저장됩니다. 이메일은 기존처럼 중복 검사를 하지만, 닉네임은 중복 검사를 하지 않습니다. 회원가입과 로그인 모두 `JwtUtil.createToken(userId, email, nickname, userRole)`을 호출하며, 생성된 JWT에는 `sub`, `email`, `nickname`, `userRole`이 포함됩니다.

### 검증 포인트

- 서로 다른 이메일과 같은 닉네임으로 회원가입이 가능합니다.
- JWT를 디코딩하면 `nickname` claim이 포함됩니다.
- `JwtUtilTest`의 `createToken_닉네임_claim을_포함한다` 테스트가 claim 포함 여부를 검증합니다.

## 요구사항 3. Todo 검색 조건 추가

### 요구사항

Todo 목록 조회 시 `weather` 조건과 수정일 기간 조건(`modifiedAtFrom`, `modifiedAtTo`)을 선택적으로 적용해야 합니다. 검색 쿼리는 JPQL을 사용해야 합니다.

### 변경된 코드

- `src/main/java/org/example/expert/domain/todo/controller/TodoController.java:32`
  - `GET /todos`에서 `weather`, `modifiedAtFrom`, `modifiedAtTo` 요청 파라미터를 받습니다.
- `src/main/java/org/example/expert/domain/todo/service/TodoService.java:54`
  - 빈 문자열 날씨 조건은 `null`로 처리하고 Repository 검색 메서드를 호출합니다.
- `src/main/java/org/example/expert/domain/todo/repository/TodoRepository.java:14`
  - JPQL `searchTodos`에서 nullable 조건을 사용해 동적 검색을 처리합니다.
- `src/main/java/org/example/expert/domain/common/entity/Timestamped.java:21`
  - 기간 검색 기준인 `modifiedAt`을 관리합니다.

### 동작 방식

`GET /todos`는 다음 파라미터를 선택적으로 받을 수 있습니다.

```http
GET /todos?page=1&size=10&weather=Sunny&modifiedAtFrom=2026-06-14T00:00:00&modifiedAtTo=2026-06-16T23:59:59
```

- `weather`가 없거나 빈 문자열이면 날씨 조건을 적용하지 않습니다.
- `modifiedAtFrom`이 있으면 `modifiedAt >= modifiedAtFrom` 조건을 적용합니다.
- `modifiedAtTo`가 있으면 `modifiedAt <= modifiedAtTo` 조건을 적용합니다.
- 결과는 `modifiedAt DESC`로 정렬됩니다.
- JPQL 본문은 `LEFT JOIN FETCH t.user`로 Todo 작성자 정보를 함께 조회하고, 페이지네이션을 위해 별도 `countQuery`를 사용합니다.

### 검증 포인트

- 날씨와 수정일 시작/끝 조건을 모두 주면 조건에 맞는 Todo만 조회됩니다.
- 조건을 생략하면 전체 Todo가 수정일 내림차순으로 조회됩니다.
- `TodoServiceTest`의 검색 관련 테스트들이 조건 조합을 검증합니다.

## 요구사항 4. TodoController 단건 조회 실패 테스트 수정

### 요구사항

`todo_단건_조회_시_todo가_존재하지_않아_예외가_발생한다()` 테스트가 정상 통과해야 합니다.

### 변경된 코드

- `src/test/java/org/example/expert/domain/todo/controller/TodoControllerTest.java:28`
  - `@WebMvcTest(TodoController.class)`에 `SecurityConfig`, `JwtUtil`을 import해 보안 필터 환경을 포함했습니다.
- `src/test/java/org/example/expert/domain/todo/controller/TodoControllerTest.java:70`
  - `todoService.getTodo(todoId)`가 `InvalidRequestException("Todo not found")`를 던지도록 mock 처리했습니다.
- `src/test/java/org/example/expert/domain/todo/controller/TodoControllerTest.java:80`
  - 테스트 요청에 인증용 JWT 헤더를 추가했습니다.
- `src/test/java/org/example/expert/support/SecurityMockMvcSupport.java:22`
  - 테스트용 USER JWT Authorization 헤더를 생성합니다.

### 동작 방식

테스트는 인증 실패가 아니라 Todo 없음 예외 처리 경로를 검증해야 합니다. 그래서 요청에 유효한 JWT를 붙이고, 서비스 mock이 `InvalidRequestException`을 던지게 합니다. 이 예외는 `GlobalExceptionHandler`에서 `400 Bad Request`와 JSON 에러 응답으로 변환됩니다.

### 검증 포인트

- HTTP 상태가 `400 Bad Request`입니다.
- 응답 JSON의 `status`, `code`, `message`가 각각 `BAD_REQUEST`, `400`, `Todo not found`입니다.

## 요구사항 5. AOP 관리자 접근 로그 수정

### 요구사항

`UserAdminController.changeUserRole()` 실행 전에 관리자 접근 로그가 남아야 합니다.

### 변경된 코드

- `build.gradle:31`
  - `spring-boot-starter-aop` 의존성을 추가했습니다.
- `src/main/java/org/example/expert/aop/AdminAccessLoggingAspect.java:24`
  - `UserAdminController.changeUserRole(..)` 대상 `@Before` 포인트컷을 설정했습니다.
- `src/main/java/org/example/expert/aop/AdminAccessLoggingAspect.java:26`
  - `SecurityContextHolder`의 인증 principal에서 `AuthUser`를 읽어 관리자 ID를 가져옵니다.
- `src/main/java/org/example/expert/aop/AdminAccessLoggingAspect.java:31`
  - `HttpServletRequest`로 요청 URL을 조회합니다.
- `src/test/java/org/example/expert/aop/AdminAccessLoggingAspectTest.java:68`
  - 컨트롤러 호출 전에 로그가 남는지 검증합니다.

### 동작 방식

관리자가 `PATCH /admin/users/{userId}`를 호출하면 Spring AOP가 `changeUserRole` 실행 직전에 `AdminAccessLoggingAspect.logBeforeChangeUserRole`을 실행합니다. Aspect는 관리자 ID, 요청 시각, 요청 URL, 메서드명을 `Admin Access Log` 형식으로 남깁니다. 이후 컨트롤러가 실제 권한 변경 서비스를 호출합니다.

### 검증 포인트

- `AdminAccessLoggingAspectTest`에서 `ListAppender`로 로그를 캡처합니다.
- 서비스 mock 실행 전에 로그 메시지에 관리자 ID, 요청 URL, 메서드명이 들어있는지 확인합니다.

## 요구사항 6. JPA Cascade로 Todo 생성자를 Manager로 자동 등록

### 요구사항

Todo를 새로 저장할 때 Todo 생성자가 담당자 Manager로 자동 등록되어야 합니다. 이 동작은 JPA cascade를 활용해야 합니다.

### 변경된 코드

- `src/main/java/org/example/expert/domain/todo/entity/Todo.java:33`
  - `managers` 연관관계에 `cascade = CascadeType.PERSIST`를 설정했습니다.
- `src/main/java/org/example/expert/domain/todo/entity/Todo.java:36`
  - Todo 생성자에서 작성자 `user`를 Manager로 추가합니다.
- `src/main/java/org/example/expert/domain/todo/service/TodoService.java:37`
  - 서비스는 `new Todo(..., user)`를 생성한 뒤 Todo만 저장합니다.
- `src/test/java/org/example/expert/domain/todo/service/TodoServiceTest.java:88`
  - Todo 저장 후 Manager가 자동 생성되는지 검증합니다.

### 동작 방식

`new Todo(title, contents, weather, user)` 호출 시 생성자 내부에서 `new Manager(user, this)`가 `managers` 컬렉션에 추가됩니다. 이후 `todoRepository.save(newTodo)`가 실행되면 `CascadeType.PERSIST` 때문에 아직 저장되지 않은 Manager도 함께 persist됩니다.

### 검증 포인트

- Todo 저장 후 `managers` 테이블에 작성자 기준 Manager가 자동 생성됩니다.
- 생성된 Manager의 `todo_id`는 저장된 Todo ID와 같고, `user_id`는 Todo 작성자 ID와 같습니다.

## 요구사항 7. Comment 조회 N+1 문제 해결

### 요구사항

`GET /todos/{todoId}/comments` 호출 시 댓글 목록 조회 후 댓글 작성자를 개별 조회하는 N+1 문제를 제거해야 합니다.

### 변경된 코드

- `src/main/java/org/example/expert/domain/comment/controller/CommentController.java:31`
  - `getComments()` 엔드포인트가 `CommentService.getComments(todoId)`를 호출합니다.
- `src/main/java/org/example/expert/domain/comment/service/CommentService.java:50`
  - 댓글 조회 시 `commentRepository.findByTodoIdWithUser(todoId)`를 사용합니다.
- `src/main/java/org/example/expert/domain/comment/repository/CommentRepository.java:12`
  - JPQL을 `JOIN FETCH c.user`로 변경해 댓글과 작성자를 한 번에 조회합니다.

### 동작 방식

`Comment.user`는 `FetchType.LAZY`입니다. 일반 조회 후 DTO 생성 과정에서 `comment.getUser()`를 호출하면 댓글 수만큼 작성자 조회 쿼리가 추가될 수 있습니다. 이를 막기 위해 Repository에서 `JOIN FETCH c.user`로 댓글과 작성자를 함께 로딩합니다. DTO 생성 시에는 이미 로딩된 작성자 정보를 사용하므로 추가 select가 발생하지 않습니다.

### 검증 포인트

- `CommentServiceTest`에서 댓글 3개와 작성자 3명을 저장한 뒤 `getComments()`를 호출합니다.
- Hibernate `prepareStatementCount`가 `1`인지 검증해 단일 쿼리로 처리되는지 확인합니다.

## 요구사항 8. QueryDSL로 Todo 단건 조회 전환

### 요구사항

JPQL로 작성된 `findByIdWithUser`를 QueryDSL로 변경하고, Todo 단건 조회 시 작성자 조회 N+1이 발생하지 않도록 해야 합니다.

### 변경된 코드

- `build.gradle:32`
  - QueryDSL JPA 의존성을 추가했습니다.
- `src/main/java/org/example/expert/config/PersistenceConfig.java:13`
  - `JPAQueryFactory` Bean을 등록했습니다.
- `src/main/java/org/example/expert/domain/todo/repository/TodoRepository.java:12`
  - `TodoRepositoryCustom`을 상속합니다.
- `src/main/java/org/example/expert/domain/todo/repository/TodoRepositoryCustom.java:7`
  - `findByIdWithUser(Long todoId)` 계약을 정의했습니다.
- `src/main/java/org/example/expert/domain/todo/repository/TodoRepositoryCustomImpl.java:20`
  - QueryDSL 기반 구현을 추가했습니다.
- `src/main/java/org/example/expert/domain/todo/service/TodoService.java:71`
  - `getTodo()`가 QueryDSL 커스텀 메서드를 호출합니다.

### 동작 방식

`TodoService.getTodo(todoId)`는 `todoRepository.findByIdWithUser(todoId)`를 호출합니다. 커스텀 구현체는 QueryDSL의 `selectFrom(todo)`와 `leftJoin(todo.user, user).fetchJoin()`을 사용해 Todo와 작성자를 한 번에 조회합니다. 따라서 응답 DTO 생성 중 `todo.getUser()`에 접근해도 작성자 조회 SQL이 추가로 실행되지 않습니다.

### 검증 포인트

- `TodoServiceTest`의 `getTodo_todo와_작성자를_join_fetch로_한번에_조회한다` 테스트가 응답 값과 작성자 정보를 검증합니다.
- 영속성 컨텍스트를 비운 뒤 조회하고, Hibernate `prepareStatementCount`가 `1`인지 확인합니다.

## 요구사항 9. Spring Security 인증/인가 전환

### 요구사항

기존 커스텀 Filter와 Argument Resolver 기반 인증을 Spring Security 기반 인증/인가로 전환해야 합니다. JWT 토큰 방식은 유지하고, 기존 권한 기능도 유지해야 합니다.

### 변경된 코드

- `build.gradle:30`
  - `spring-boot-starter-security` 의존성을 추가했습니다.
- `src/main/java/org/example/expert/config/SecurityConfig.java:27`
  - `SecurityFilterChain`을 구성했습니다.
- `src/main/java/org/example/expert/config/SecurityConfig.java:33`
  - `/auth/**`는 공개, `/admin/**`는 ADMIN 권한, 그 외 요청은 인증 필요로 설정했습니다.
- `src/main/java/org/example/expert/config/SecurityConfig.java:42`
  - `JwtFilter`를 Spring Security 필터 체인에 등록했습니다.
- `src/main/java/org/example/expert/config/SecurityPolicy.java:12`
  - 공개 경로와 관리자 경로 정책을 분리했습니다.
- `src/main/java/org/example/expert/config/JwtFilter.java:50`
  - JWT를 추출하고 검증한 뒤 `SecurityContextHolder`에 인증 객체를 저장합니다.
- `src/main/java/org/example/expert/config/JwtFilter.java:87`
  - `Bearer ` 형식이 아닌 잘못된 `Authorization` 헤더를 `400 Bad Request`로 처리하도록 보완했습니다.
- `src/main/java/org/example/expert/domain/todo/controller/TodoController.java:26`
  - 기존 `@Auth` 대신 `@AuthenticationPrincipal AuthUser`를 사용합니다.
- `src/main/java/org/example/expert/domain/comment/controller/CommentController.java:24`
  - 댓글 생성 API도 `@AuthenticationPrincipal`을 사용합니다.
- `src/main/java/org/example/expert/domain/manager/controller/ManagerController.java:24`
  - 담당자 등록/삭제 API도 Spring Security principal을 사용합니다.
- `src/main/java/org/example/expert/domain/user/controller/UserController.java:25`
  - 비밀번호 변경 API도 Spring Security principal을 사용합니다.

### 동작 방식

요청이 들어오면 Spring Security 필터 체인에서 `JwtFilter`가 실행됩니다. `/auth`, `/auth/**`는 인증 없이 통과하고, 보호된 API는 `Authorization: Bearer {token}` 헤더가 필요합니다. 필터는 JWT claims에서 사용자 ID, 이메일, 권한을 읽어 `AuthUser`를 만들고, `ROLE_USER` 또는 `ROLE_ADMIN` 권한과 함께 `SecurityContext`에 저장합니다. 이후 컨트롤러는 `@AuthenticationPrincipal AuthUser`로 기존 서비스 로직에 필요한 사용자 정보를 받습니다.

`/admin/**` 경로는 `SecurityConfig`의 `.hasAuthority(...)` 설정으로 ADMIN 권한만 접근할 수 있습니다. USER 권한이 접근하면 서비스 호출 전 Spring Security에서 `403 Forbidden`으로 차단됩니다.

### 검증 포인트

- `/auth/signin`, `/auth/signup`은 JWT 없이 접근 가능합니다.
- 보호된 엔드포인트는 JWT가 없으면 거부됩니다.
- 유효한 USER JWT로 일반 보호 API에 접근할 수 있습니다.
- USER 권한은 `/admin/**`에 접근할 수 없습니다.
- ADMIN 권한은 `/admin/**`에 접근할 수 있습니다.
- 잘못된 `Authorization` 헤더는 의도치 않은 `500`이 아니라 `400 Bad Request`로 처리됩니다.
- 관련 테스트는 `SecurityConfigTest`에서 인증/인가 흐름을 검증합니다.
