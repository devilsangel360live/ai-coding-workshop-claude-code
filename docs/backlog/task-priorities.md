# Backlog: Task Priorities and Due Dates (Spring Boot)

## User Story

> As a user, I want to manage tasks with priorities and due dates
> so that I can focus on what's important and meet deadlines.

**Story acceptance criteria**

- Tasks have a priority: Low, Medium, High, or Critical (default: Medium)
- Tasks can have an optional due date, which must be in the future when set
- Tasks can be created with a title, description, priority, and due date via the API
- Invalid priorities and past due dates are rejected with a clear error
- Tasks can be listed, filtered by priority, and sorted by due date

## Summary

| # | Item | Layers | Depends on |
|---|---|---|---|
| 1 | `Priority` value object | Domain | — |
| 2 | Priority and due date on `Task` | Domain, Infrastructure | 1 |
| 3 | Create a task with priority and due date via the API | Application, API | 2 |
| 4 | Validation and clear error responses | API | 3 |
| 5 | List tasks filtered by priority, sorted by due date | Application, API | 2 (4 for the error format) |

Suggested order: 1 → 2 → 3 → 4 → 5. Each item is one small PR and leaves `mvn clean test` green.

---

## Item 1: `Priority` value object

**Description.** Add a `Priority` enum to `com.example.taskmanager.domain.tasks` with the four values the business uses, each carrying a numeric level so priorities can be compared and ordered. Following `.claude/rules/domain-layer.md`, it exposes a static `fromString(String)` that parses user input case-insensitively and rejects anything else with a message listing the valid values. It is pure Java and has no knowledge of `Task`, persistence or HTTP, so it can ship on its own.

**Acceptance criteria**

- [ ] Values `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` with `level` 1, 2, 3, 4
- [ ] `Priority.fromString("high")` returns `HIGH` (case-insensitive, surrounding whitespace ignored)
- [ ] `fromString` with `null`, blank, or an unknown value throws `IllegalArgumentException` whose message lists the valid values, e.g. `Invalid priority 'Urgent'. Valid values: LOW, MEDIUM, HIGH, CRITICAL`
- [ ] No Spring, JPA, Jackson, validation or logging types in the class
- [ ] Unit tests in `taskmanager-domain/src/test/java/com/example/taskmanager/domain/tasks/PriorityTest.java`

**Layers.** Domain (`taskmanager-domain`).

**Dependencies.** None.

---

## Item 2: Priority and due date on `Task`

**Description.** Give the `Task` aggregate a `priority` (defaults to `MEDIUM`) and an optional `dueDate` (`LocalDateTime`). A new factory `create(title, description, priority, dueDate)` enforces that a due date, when set, is in the future; the existing `create(title)` and `create(title, description)` overloads keep working and use the defaults. `reconstitute(...)` gains both fields **without** the future-date check, because a persisted task can legitimately become overdue. Changing `reconstitute` breaks `TaskMapper.toDomain`, so the persistence mapping changes in the same PR: `TaskEntity` gets `priority` and `due_date` columns and `TaskMapper` round-trips them. Dev and test profiles use H2 with `create-drop`, so no migration is needed.

**Acceptance criteria**

- [ ] `Task.create(title)` and `Task.create(title, description)` produce `priority = MEDIUM`, `dueDate = null`
- [ ] `Task.create(title, description, Priority.HIGH, futureDate)` sets both fields
- [ ] `Task.create(..., null priority, ...)` defaults to `MEDIUM`; a `null` due date is allowed
- [ ] A due date that is not after the current time throws `IllegalArgumentException` with a clear message (e.g. `Due date must be in the future`)
- [ ] `Task.reconstitute(...)` accepts `priority` and `dueDate`, including a past due date
- [ ] `TaskEntity` has `priority` (String, not null, length 20) and `due_date` (nullable) columns
- [ ] `TaskMapper.toEntity` / `toDomain` round-trip priority and due date (`TaskMapperTest` updated)
- [ ] Existing domain, mapper and repository integration tests still pass

**Layers.** Domain (`Task`, `TaskTest`), Infrastructure (`TaskEntity`, `TaskMapper`, `TaskMapperTest`).

**Dependencies.** Item 1.

---

## Item 3: Create a task with priority and due date via the API

**Description.** Let clients set priority and due date when creating a task. `CreateTaskRequest` gains optional `priority` (String) and `dueDate` fields; `TaskService` gets a `createTask(title, description, priority, dueDate)` overload that builds the task through `Task.create(...)` and saves it; `TaskResponse` exposes `priority` and `dueDate` on every endpoint that returns a task. The controller stays thin: it parses the priority with `Priority.fromString` (or passes `null` when omitted) and delegates to the service. Requests that omit the new fields behave exactly as today.

**Acceptance criteria**

- [ ] `POST /api/tasks` with `{"title": "...", "description": "...", "priority": "HIGH", "dueDate": "<future ISO-8601>"}` returns 201 with `priority: "HIGH"` and the due date
- [ ] Omitting `priority` and `dueDate` returns 201 with `priority: "MEDIUM"` and `dueDate: null`
- [ ] `GET /api/tasks/{id}` and `GET /api/tasks` include `priority` and `dueDate`
- [ ] `TaskServiceTest` covers the new `createTask` overload (saves once, returns the saved task)
- [ ] `TaskApiIntegrationTest` covers both create paths above

**Layers.** Application (`TaskService`), API (`CreateTaskRequest`, `TaskResponse`, `TaskController`).

**Dependencies.** Item 2.

---

## Item 4: Validation and clear error responses

**Description.** Make every bad input from this story a 400 Problem Details response that tells the client what to fix. `GlobalExceptionHandler` already maps `IllegalArgumentException` to 400 "Invalid Input", which covers an unknown priority and a past due date once Items 1–3 are in place; this item pins that behavior with integration tests. It also closes a gap found during planning: a malformed date string (or other unreadable JSON) raises `HttpMessageNotReadableException`, which today falls through to the generic handler and returns **500**.

**Acceptance criteria**

- [ ] `"priority": "Urgent"` → 400 Problem Details whose `detail` lists `LOW, MEDIUM, HIGH, CRITICAL`
- [ ] A past `dueDate` → 400 Problem Details stating the due date must be in the future
- [ ] `"dueDate": "not-a-date"` → 400 (not 500) via a new `HttpMessageNotReadableException` handler with a clear title and detail
- [ ] No task is persisted for any rejected request
- [ ] Each case has a MockMvc test in `TaskApiIntegrationTest`

**Layers.** API (`GlobalExceptionHandler`, integration tests).

**Dependencies.** Item 3.

---

## Item 5: List tasks filtered by priority, sorted by due date

**Description.** Extend `GET /api/tasks` with optional query parameters: `priority` to return only tasks with that priority, and `sort=dueDate` to order by due date ascending, with tasks that have no due date last. Filtering and sorting live in a `TaskService` query method over `findAll()`, which keeps the domain repository port unchanged; a dedicated repository query is a later optimization if the data grows. Without parameters the endpoint behaves exactly as today.

**Acceptance criteria**

- [ ] `GET /api/tasks?priority=HIGH` returns only HIGH tasks (parameter is case-insensitive)
- [ ] `GET /api/tasks?sort=dueDate` returns tasks by due date ascending; tasks without a due date come last
- [ ] `GET /api/tasks?priority=HIGH&sort=dueDate` combines both
- [ ] `GET /api/tasks` with no parameters returns all tasks, as today
- [ ] An unknown `priority` or `sort` value → 400 Problem Details listing the valid values
- [ ] `TaskServiceTest` covers filter, sort, combined and no-parameter cases; `TaskApiIntegrationTest` covers the endpoint

**Layers.** Application (`TaskService`), API (`TaskController`).

**Dependencies.** Item 2 (fields to filter and sort on); Item 4 for the shared 400 error format.

---

## Out of Scope

- Changing priority or due date on an existing task (`PATCH`): not in the story; a follow-up item
- Filtering by more than one priority at once
- Time zones: `dueDate` is a server-local `LocalDateTime`, matching the rest of `Task`
- Database migrations: dev/test use H2 `create-drop`; production schema changes are a separate concern
