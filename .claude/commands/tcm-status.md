---
description: Quick dashboard — counts, recent activity, license, token budget for the current project
argument-hint: (no arguments)
---

The user invoked `/tcm-status` to get a 5-7 line snapshot of the state of the Teach CLAUDE.md library + extension state for this project.

## What to read

1. **Project notes**: count files in `c:\AN\CROWN\save-me-button-app-android\.teachcm/notes/` (workspace-scoped).
2. **Global notes**: count files in `~/.teachcm/notes/` (cross-project).
3. **Pending captures**: count files in `c:\AN\CROWN\save-me-button-app-android\.teachcm\pending` + `~/.teachcm/pending/`.
4. **Tags**: count entries in `c:\AN\CROWN\save-me-button-app-android\.teachcm\.meta\tags.json` if it exists.
5. **Decisions** (ADRs): count files in `c:\AN\CROWN\save-me-button-app-android\.teachcm/decisions/`.
6. **Mockups**: count `.html` files in `<workspace>/.tcm-mockups/`.
7. **Test runs**: count `.log` files in `c:\AN\CROWN\save-me-button-app-android\.teachcm/test-runs/`.
8. **Active feature** (best guess): from active git branch name or latest mockup/breakdown mtime.
9. **CLAUDE.md tokens**: count tokens of the managed block in `<workspace>/CLAUDE.md`. Approximate via word count × 1.3 if exact tokenizer unavailable.
10. **License/trial**: read from VSCode storage if exposed; otherwise note as "n/a from slash command".

## Output format

```
Teach CLAUDE.md — status
Project:    <workspace folder name>
Branch:     <git branch>

Library
  Notes:        <P> project, <G> global
  Pending:      <N> awaiting review
  Tags:         <T> unique
  Decisions:    <D> ADRs
  Mockups:      <M> in .tcm-mockups/
  Test runs:    <R> logs

CLAUDE.md
  Managed block: ~<TOK> tokens
  Last updated:  <relative time, mtime of CLAUDE.md>

Active feature (best guess): <name>
Last activity: <most recent mtime across notes/pending/mockups/decisions> — <which file>

Run: <ISO8601 now>
```

If a section is empty (e.g., 0 mockups), still show it with `0`. Empty signals are useful.

## When to skip a check

- If a directory doesn't exist, treat as 0 (don't crash, don't ask).
- If git is not initialized, omit the Branch line.
- If `js-tiktoken` is unavailable, use approximate token count and prefix with `~`.

## After output

End with **no** prompt, just the report. The user reads, decides what to do next.

## Logging

Append to command-history.jsonl as usual:
```json
{"ts":"<ISO8601>","cmd":"/tcm-status","arg":""}
```
