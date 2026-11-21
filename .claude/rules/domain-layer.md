---
paths:
  - "src-dotnet/src/TaskManager.Domain/**/*.cs"
  - "src-springboot/taskmanager-domain/**/*.java"
---

# Domain Layer Rules

- Value objects expose a static parse method: `FromName(string)` in C#, `fromString(String)` in Java.
- Invalid input throws `ArgumentException` (C#) / `IllegalArgumentException` (Java) with a message that lists the valid values, e.g. `Invalid status 'Done'. Valid values: PENDING, IN_PROGRESS, COMPLETED, CANCELLED`.
- The domain layer never references logging, HTTP, or persistence types (no `ILogger`/SLF4J, no HTTP clients or web types, no EF Core/JPA).
