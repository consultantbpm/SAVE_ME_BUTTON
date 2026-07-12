---
description: Propose a test plan; implement and run tests only after the user agrees
argument-hint: <feature, module, or scenario to test>
---

The user invoked `/t` to receive a structured test plan and then have it executed. **Two phases: propose, then run — never skip the agreement step.**

## Phase 1 — Propose

Identify the scope:

- If the user gave an argument, treat it as the target.
- Otherwise, ask: *"What feature/module should this test plan cover?"*

Read the project's testing setup before proposing:

- `CLAUDE.md` for testing conventions
- `build.gradle*`, `settings.gradle*`, `package.json` test scripts, etc. — to identify the actual test framework
- `src/test/`, `app/src/test/`, `*Test.kt`, `*.test.ts` etc. for existing patterns
- `.teachcm/notes/` for prior capture about testing conventions

Then produce a **test plan** in this format:

### Test plan for `<scope>`

**Goal:** one-sentence statement of what success looks like.

**Test cases**, grouped by category:

#### Happy path
1. **<short test name>** — preconditions; action; expected outcome.
2. ...

#### Edge cases
3. **<short test name>** — boundary condition; expected behavior.
4. ...

#### Failure modes
5. **<short test name>** — error trigger; expected error handling.
6. ...

#### Integration (if applicable)
7. **<short test name>** — cross-module interaction; expected coordination.

**Test type per case** — for each, indicate: unit / instrumentation / integration / manual / UI.

**Test framework & runner** — name them explicitly (JUnit/Robolectric/Espresso/Jest/Vitest/…), with the exact command to run them.

**Out of scope** — what this plan does NOT cover, so the user can confirm or expand.

End the proposal with:

> Approve this plan? (yes / edit / add cases / cancel)

## Phase 2 — Implement & run (only after approval)

1. **Write the tests** following the existing project's testing patterns. Don't invent new conventions.
2. **Run them**. Use the exact command identified in the plan.
3. **Report results**:
   - Pass/fail count
   - For each failure: test name, failure reason from the runner, file:line, suggested fix or hypothesis
4. **If failures point to a real bug** (not test setup issues), STOP and ask the user how to proceed before fixing. The bug fix is a separate decision.

## Do not

- Do not implement tests before the user agrees to the plan.
- Do not modify production code during `/t` unless explicitly approved — `/t` is about adding *tests*, not refactoring.
- Do not write to `.teachcm/`. If the test work yields a reusable insight (e.g., "this module is fragile around X"), suggest `/lp` after.
- Do not skip running the tests — a plan that's only written but never executed is not a `/t` deliverable.

## Logging to command history

After proposing the plan (and after implementation if user accepted), append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/t","arg":"<feature or argument verbatim>"}

Create the file and parent directory if missing.
