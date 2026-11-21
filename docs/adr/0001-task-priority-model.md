# ADR 0001: Task Priority and Due Date Model

**Status:** Accepted
**Date:** 2026-11-19

## Context

Users need to rank tasks by importance (Low, Medium, High, Critical; default Medium) and set an optional due date that must be in the future (backlog `docs/backlog/task-priorities.md`, Items 1–3). Invalid input must be rejected with a clear error, and tasks will later be filtered by priority and sorted by due date. Domain rules (`.claude/rules/domain-layer.md`) require value objects with a `fromString` parser and `IllegalArgumentException` messages that list the valid values.

## Decision

- **`Priority` is a domain enum** (`LOW`=1 … `CRITICAL`=4) with a `level` for ordering and a case-insensitive `fromString`; invalid input throws `IllegalArgumentException` listing `LOW, MEDIUM, HIGH, CRITICAL`.
- **`Task` holds `priority` and `dueDate` (`LocalDateTime`) as final fields.** `Task.create(title, description, priority, dueDate)` defaults a null priority to `MEDIUM` and rejects a due date that is not after now. The existing `create` overloads use the defaults.
- **`reconstitute` skips the future check**, so persisted tasks can become overdue. Its signature was replaced, not overloaded, so the mapper cannot silently drop the new fields.
- **Persistence stores the enum name** (`priority VARCHAR(20) NOT NULL`, nullable `due_date`). A row with no priority maps to `MEDIUM`.
- **The API accepts priority as a `String`.** `TaskService` parses it with `Priority.fromString`, so an unknown value becomes a 400 Problem Details response through the existing `IllegalArgumentException` handler.

## Consequences

**Positive**
- Every invalid priority, at any layer, gets the same clear message listing the valid values.
- Domain stays pure Java; `level` gives sorting a stable order without relying on enum ordinal.
- Storing names (not ordinals) survives reordering or adding priorities.

**Negative**
- `LocalDateTime` is server-local with no time zone; clients in other zones can mis-set due dates.
- `Task` calls `LocalDateTime.now()` directly, so time rules can't be tested with a fixed clock.
- OpenAPI shows `priority` as a free string, not an enum of allowed values.
- Priority and due date can't be changed after creation yet; production (`ddl-auto: validate`) needs a schema change.

## Alternatives Considered

- **Bind `Priority` directly in the request DTO via Jackson:** an unknown value raises `HttpMessageNotReadableException`, which currently returns 500 with no list of valid values. Rejected.
- **`@Enumerated(ORDINAL)` persistence:** compact, but reordering the enum corrupts data. Rejected.
- **Overload `reconstitute` and keep the 6-argument version:** callers could silently drop priority and due date. Rejected.
- **Inject a `Clock` into the domain:** better testability; deferred to keep Item 2 minimal.
