---
description: Add diagnostic logging at key points, then run and analyze the logs
argument-hint: <area to instrument, e.g., "button-press flow" or specific file>
---

The user invoked `/log` to instrument the code with diagnostic tracing and then analyze the resulting logs. Two-phase: **instrument**, then **analyze**.

## Phase 1 — Plan the instrumentation

Before adding any logs, identify:

1. **The flow under investigation** — what sequence of events is the user trying to understand?
2. **The key points along that flow** — entry/exit of relevant functions, state transitions, decision branches, error paths.
3. **Existing log tags** — read `CLAUDE.md` for the project's logcat tags or logging conventions (this project documents tags like `WearKeys:D`, `WearNav:D`, etc.).

State the plan back to the user in a numbered list of insertion points, **then ask for confirmation** before editing. Format:

> Instrumentation plan for "<area>":
> 1. `<file>:<line>` — log entry of `<function>` with `<vars>` (tag: `<Tag>:D`)
> 2. `<file>:<line>` — log state transition `<from> → <to>` (tag: `<Tag>:D`)
> 3. ...
>
> Add these? (yes / edit / cancel)

## Phase 2 — Instrument

After the user confirms:

- Add log statements following the project's existing logging style. For Android projects in this repo, use `android.util.Log.d("<Tag>", "<message> <vars>")`.
- Tag prefix should match the area (e.g., `WearKeys`, `PhoneMainViewModel`). If a new tag is needed, name it consistently and add it to `CLAUDE.md` under the logcat tags list.
- Keep messages short and grep-friendly. Include the critical variables, not whole objects.
- Mark each insertion with `// teachcm-log:<id>` — a comment that lets us strip them later (`/log clean`).

After editing, list what was added, file-by-file, and remind the user how to run:

> Build & capture logs:
> ```powershell
> winstall -Tail        # installs and streams filtered logcat
> ```
> Reproduce the scenario, then paste the relevant logcat lines back here (or run again with the filter) — I'll analyze them.

## Phase 3 — Analyze

When the user pastes log output (or asks you to read a log file):

1. **Parse** the lines into a timeline.
2. **Highlight** what's missing vs. expected (e.g., a transition that should fire but doesn't).
3. **Diagnose** — point to the file:line where the divergence is, with evidence from the log.
4. **Propose** the next step: either a code fix, more instrumentation, or a hypothesis test.

## Cleanup

If the user invokes `/log clean`, remove all comments and log statements tagged `// teachcm-log:*` from the codebase.

## Do not

- Do not commit logs as a permanent change without the user's explicit ok — they are diagnostic by default.
- Do not log secrets, tokens, or PII even temporarily.
- Do not write to `.teachcm/`. If a finding is worth keeping, suggest `/lp` after the analysis is done.

## Logging to command history

After completing the trace + analysis, append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/log","arg":"<area or argument verbatim>"}

Create the file and parent directory if missing.
