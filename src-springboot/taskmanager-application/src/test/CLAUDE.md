# Application Layer Tests

Unit tests for application services. Frameworks, Arrange/Act/Assert layout and the base naming convention are in `src-springboot/CLAUDE.md`; TDD and layering rules are in `.claude/rules/springboot.md`.

Domain-layer rules: @../../../../.claude/rules/domain-layer.md

## Shape of a test class

- One test class per service, in the service's package (`TaskService` → `services/TaskServiceTest`).
- Plain JUnit 5 + Mockito: `@ExtendWith(MockitoExtension.class)`, `@Mock` the domain repository port, `@InjectMocks` the real service. No Spring context here; `@SpringBootTest` belongs in `taskmanager-api`.
- One `@Nested` class per service operation, each with a `@DisplayName` ("Start Task Operations").
- Each operation covers three cases: success, task not found (`TaskNotFoundException`), and invalid state change (`IllegalStateException` raised by the domain).

## Naming

- New test classes: `methodName_stateUnderTest_expectedBehavior`, as in `src-springboot/CLAUDE.md`.
- Adding to an existing class: match its style. `TaskServiceTest` uses `shouldXxx()` method names plus a `@DisplayName("should ...")` sentence, and JUnit `Assertions` rather than AssertJ.

## Rules

- Build real aggregates with `Task.create(...)` and reach the starting state through business methods (`task.start()`, `task.complete()`, `task.cancel()`).
- Test what the service owns: which repository calls happen. Success paths `verify(taskRepository).save(task)`; every failure path also asserts `verify(taskRepository, never()).save(any())`.
- Domain rules (title validation, status transitions) get their detailed tests in `taskmanager-domain` (`TaskTest`). Here, one test per operation proves the domain exception propagates.
- One behaviour per test. Use `@ParameterizedTest` for input variations, such as null and blank titles.
