# LegacyTaskProcessor Refactor Plan (Spring Boot)

**Target:** `src-springboot/taskmanager-infrastructure/src/main/java/com/example/taskmanager/infrastructure/legacy/LegacyTaskProcessor.java`
**Only caller:** `LegacyBatchImportService.java` (same package)
**Goal:** Follow `src-springboot/CLAUDE.md` conventions (guard clauses, no abbreviations, SLF4J logging for swallowed exceptions, constructor injection) **without changing observable behavior**.
**Starting point:** No tests cover either class today.

## Behavior to preserve

| Area | Behavior to keep |
|---|---|
| `processTask` with `null` or `""` | Returns `""`. Does not increment the count or update the last result. |
| Type 1, `flag=true` | Space → `_`. Swaps the case of each character (`Character.isUpperCase` → lower, else → `Character.toUpperCase`). Truncates to 50 chars *after* the transform. Sleeps 100 ms, then writes `task_<id>.txt` to the **working directory**. Returns the result even when the write fails. |
| Type 1, `flag=false` | `data.toUpperCase()` (default locale). No truncation, no sleep, no file. |
| Type 2 | `split(" ")`. The first word stays as it is; the rest are lowercased. A leading space gives `" hello world"`. Input of only spaces gives `""` but **still counts**. |
| Any other type | Returns the input unchanged and still counts. |
| `chk` | Returns `false` if not ready, if the input is null or empty, or if the type isn't 1 or 2. Length exactly 500 → `false`, 499 → `true`. |
| State | `processTask` ignores the ready flag. `init` resets the count but **not** the last result. Starts with `""` and `0`. |
| Interrupts | If the thread is already interrupted, the sleep returns **immediately**, the flag is cleared, and the file is still written (verified on JDK 17). **Don't** add `Thread.currentThread().interrupt()` during the refactor: the caller would be left interrupted, and every later sleep in a batch would be skipped. |
| Whitespace | `isEmpty()`/`equals("")`, not `isBlank()`: `"   "` counts as content (type 1 → `"___"`, `chk("   ", 1)` → `true`). Only `' '` is special: tabs and newlines are case-swapped and kept (type 1), and are not word separators (type 2: `"A\tB"` returned unchanged). |
| Type 2 trailing spaces | `split(" ")` drops trailing empty tokens: `"Hello World "` → `"Hello world"`. |
| Type 2 and `flag` | Type 2 ignores `flag`: no sleep, no file. |
| Case conversion | Type 1 swap is **per `char`** (`'ß'` → `'ß'`, `'İ'` → `'i'`; supplementary characters such as `𐐀` and emoji are unchanged). Type 1 `flag=false` and type 2 use **`String`** methods (`"ß".toUpperCase()` → `"SS"`). Both `String` paths use the default locale (Turkish: `"i"` → `"İ"`, `"I"` → `"ı"`). |
| File write | `Files.writeString` overwrites an existing file. If truncation splits a surrogate pair, it throws `UnmappableCharacterException` (an `IOException`, verified on JDK 17): no file, result still returned and counted. |
| Batch service | `importBatch` calls `init(true)` on every batch, so the count is per batch. Rejected records aren't counted. Every accepted record is processed with `flag=true`. |

## Phases

### Phase 0: Baseline
- **Touches:** nothing.
- **Checkpoint:** `mvn clean test` passes on the personal branch. The `pom.xml` targets Java 21; confirm the environment you'll work in has a JDK 21 (a machine with only JDK 17 can't run the safety net).

### Phase 1: Characterization tests
- **Touches:** new `LegacyTaskProcessorTest` and `LegacyBatchImportServiceTest` in `taskmanager-infrastructure/src/test/java/com/example/taskmanager/infrastructure/legacy/`.
- The tests cover every row of the behavior table and every case in the Phase 3 risk table below. Use `@ParameterizedTest` for the type and input combinations.
- To test the file write, use a unique id and delete the file in `@AfterEach`. To force the `IOException`, create a *directory* named `task_<id>.txt`.
- Also cover:
  - `init(false)` resets the count.
  - The 100 ms sleep: assert elapsed time ≥ 100 ms for type 1 `flag=true` (lower bound only, so it isn't flaky).
  - A pre-interrupted thread: after the call, `isInterrupted()` is `false` and the file is written.
  - An existing `task_<id>.txt` is overwritten, not appended to.
  - `LegacyBatchImportServiceTest` with a real processor: count is per batch, rejected records aren't counted, type 1 records write files.
- **Checkpoint:** all tests pass against the **unchanged** code. JaCoCo shows 100% line and branch coverage of the class.
- **Commit:** `test: add characterization tests for LegacyTaskProcessor`

### Phase 2: Remove abbreviations and magic numbers
- **Touches:** the processor and `LegacyBatchImportService`.
  - Rename fields: `rdy` → `ready`, `cnt` → `processedCount`, `last` → `lastResult`.
  - Rename `chk(d, tp)` → `canProcess(data, type)`, and update the call in `LegacyBatchImportService`.
  - Rename `init(mode)` → `init(ready)` and `flag` → `formatForExport`. Not `writeToFile`: the flag also controls the case swap, the truncation and the sleep, and a narrower name invites Phase 3 to move those out of the branch.
  - Keep `type` as an `int`. Don't convert it to an enum or add `Locale.ROOT` while tidying.
  - Replace the numbers with constants: `TYPE_FORMAT = 1`, `TYPE_SENTENCE = 2`, `MAX_RESULT_LENGTH = 50`, `MAX_INPUT_LENGTH = 500`, `WRITE_DELAY_MS = 100`.
- **Checkpoint:** the same tests pass. The only test change allowed is the method rename.
- **Commit:** `refactor: rename abbreviated identifiers in LegacyTaskProcessor`

### Phase 3: Guard clauses and extracted methods (riskiest phase)
- **Touches:** the processor only.
  - Return early for null or empty input.
  - Pull each type into its own private method, for example `swapCaseAndUnderscore` and `lowercaseTrailingWords`.
  - Turn `canProcess` into guard returns and remove the `== true` comparison.
  - Use `StringBuilder` only if the result stays character-for-character identical.
- **Checkpoint:** **no test edits at all**. Any red test means the behavior changed. See the risk section below for the full checkpoint.
- **Commit:** `refactor: flatten LegacyTaskProcessor with guard clauses`

### Phase 4: Constructor injection
- **Touches:** the processor, a new `TaskResultFileWriter` (`@Component` with `write(int id, String content) throws IOException`, keeping the same working-directory path), and the processor tests.
  - The processor receives the writer through its constructor.
  - Tests switch from the real file system to a Mockito mock, using `verify(writer, times(1))`.
  - One small test checks the real writer.
  - The real writer must keep `Files.writeString` (overwrite, and fail on a lone surrogate instead of writing `?`), and must throw the checked `IOException` rather than wrapping it in `UncheckedIOException`, which would escape the processor's `catch` and skip the count.
  - Real-writer tests: relative path, overwrite, lone-surrogate content throws `IOException`, unwritable path throws `IOException`.
- **Checkpoint:** unit tests pass, plus the full `mvn clean test`, so the Spring context still wires the beans. Both `JpaTaskRepositoryAdapterIntegrationTest` (infrastructure `TestApplication`) and `TaskApiIntegrationTest` (`scanBasePackages = "com.example.taskmanager"`) load the legacy beans on H2, without Docker.
- **Commit:** `refactor: inject file writer into LegacyTaskProcessor`

### Phase 5: SLF4J logging
- **Touches:** the processor and its tests.
  - Add a `private static final Logger` (`LoggerFactory.getLogger(LegacyTaskProcessor.class)`).
  - `warn` on `IOException`, including the id and the exception.
  - `warn` on `InterruptedException`, without restoring the interrupt flag.
- **Checkpoint:** a mock writer throws `IOException`, and a test asserts the log line using Spring Boot's `OutputCaptureExtension` (already on the test classpath, so no new dependency). It also asserts the result is still returned. The pre-interrupted-thread test from Phase 1 still passes (flag cleared, file written). All earlier tests still pass.
- **Commit:** `refactor: log swallowed exceptions in LegacyTaskProcessor`

### Phase 6 (optional): Bug fix, separate from the refactor
Phases 1–5 change no behavior. A bug fix gets its own phase and a `fix:` commit. Which bug to fix is still open:
- **Interrupt flag:** call `Thread.currentThread().interrupt()` after the sleep, and decide how `LegacyBatchImportService` should react to an interrupted thread (today it keeps going; with the flag restored, every later sleep is skipped). Check on JDK 21 whether the file write still succeeds with the flag set (it does on JDK 17).
- **Validation mismatch:** `processTask` applies the same rules as `canProcess` (type 1 or 2, length under 500, ready). Recommended handling for invalid input: return `""` without counting.
- **Touches:** the processor, plus new tests written first (TDD) that describe the new behavior.
- **Checkpoint:** the new tests fail before the change and pass after. The characterization tests that pinned the old behavior get updated *in this commit only*, so the behavior change is visible in the diff.
- **Commit:** `fix: ...`

**Why this order:** tests before any change. Renames come next because they're mechanical and make later diffs readable. Restructuring is the riskiest step, so it runs with the tests left untouched. Injection comes before logging so the failure-path test can use a mock instead of the file system trick. Behavior changes come last and on their own.

## Phase 3 risks and detection

Phase 3 is the only phase that restructures the control flow, so it carries the most risk. It is only as safe as the Phase 1 tests are complete.

| Likely mistake | Breaks | Test case that catches it |
|---|---|---|
| Moving the count increment and last-result update into the per-type methods, or above the empty-input guard | Count drifts for some types, or empty input starts counting | Assert count and last result for **every** type, and for `null` and `""` |
| Rewriting type 2 with `trim()`, `split("\\s+")` or streams | Leading, double and trailing spaces behave differently | `" Hello World"` → `" hello world"`, `"a  b"` → `"a  b"`, `"   "` → `""` but counted |
| Rewriting the swap with `isLowerCase` instead of the `isUpperCase`/else logic | Characters that are neither upper nor lower case flip the wrong way | `'ǅ'` (titlecase) → `'Ǆ'` |
| Using `String.toUpperCase` on chunks instead of `Character.toUpperCase` per character | `ß` → `SS`, which changes the length and the truncation point | `"ß"` stays `"ß"` |
| Truncating before the transform, or applying it to `flag=false` | Wrong length or wrong content | 60-char input: `flag=true` → 50 chars, transformed. `flag=false` → 60 chars, uppercase |
| Writing the file with the untruncated value, or skipping the write on one path | File content no longer matches the returned value | Assert the file content equals the returned value for a 60-char input |
| Copying the `canProcess` checks into `processTask` "for consistency" | `processTask` starts rejecting input it accepted before | Call `processTask` **before** `init`, with type 3 and with 600 chars. It must still process all three |
| Off-by-one at the 500 limit (`<` becomes `<=`) | 500-char input is accepted | Boundary cases: 499 → `true`, 500 → `false` |
| "Fixing" the case conversion with `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)` | Different output under some locales, such as Turkish | Optional: `tr` locale tests, `"i"` → `"İ"` (type 1 `flag=false`) and `"I"` → `"ı"` (type 2) |
| `return data;` in the null/empty guard | `null` input returns `null` instead of `""` | `processTask(1, null, 1, true)` → `""` |
| Tidying `isEmpty()`/`equals("")` into `isBlank()` | `"   "` stops counting, `canProcess("   ", 1)` becomes `false` | Type 1 `"   "` → `"___"` and counted; `canProcess("   ", 1)` → `true` |
| Using `Character.isWhitespace` or `split("\\s+")` | Tabs and newlines become `_` or word separators | Type 1 `"a\tb"` → `"A\tB"`; type 2 `"A\tB"` → `"A\tB"`; type 2 `"Hello World "` → `"Hello world"` |
| Switching the swap to `codePoints()` | Supplementary characters change case; `sb.append(Character.toLowerCase(cp))` appends numbers | `"𐐀"` stays `"𐐀"`; `"İ"` → `"i"` (one char) |
| Sharing one case helper between the swap and the `String` paths | `"ß"` behaves the same everywhere | Type 1 `flag=true` `"ß"` → `"ß"`; `flag=false` `"ß"` → `"SS"` |
| Hoisting "if flag: sleep and write" out of the type 1 branch | Type 2 starts writing files | Type 2 with `flag=true` writes no file |
| Deleting the "pointless" sleep | 100 ms delay disappears | Elapsed time ≥ 100 ms for type 1 `flag=true` |

### Phase 3 checkpoint
1. **Tests untouched.** `git diff --stat` for the phase must show only `LegacyTaskProcessor.java`.
2. **All tests pass, and JaCoCo branch coverage stays at 100%.** A drop means a branch disappeared or a new one has no test.
3. **Old and new produce the same results (optional, no new dependency).** Temporarily copy the pre-refactor class into the test folder. Run both versions over the same inputs (every case above plus random strings) and assert identical return values, count and last result. Delete the copy before committing.
4. **Mutation testing (optional).** The PIT Maven plugin would show whether the Phase 1 tests catch small code changes. It is a new build plugin, so it needs approval before adding.

## Open decisions (recommended defaults)
1. **Rename the public methods (`chk`, `getCount`, `getLast`)?** Default: yes, and update the one in-repo caller.
2. **Inject a writer, or only a logger?** Default: inject `TaskResultFileWriter`. The class has no collaborators today, so injection has nothing to apply to without one, and the writer also makes the file path testable.
3. **Which bug does Phase 6 fix: the interrupt flag or the validation mismatch?** Default: neither during the refactor. Decide before starting Phase 6.
