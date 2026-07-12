---
description: Capture a knowledge entry into the PROJECT Teach CLAUDE.md library (this workspace only)
argument-hint: [optional hint about what to capture]
---

The user invoked `/lp` to save knowledge specific to **this project** — architecture decisions, gotchas in this codebase, conventions enforced here, build/test quirks, module-specific patterns.

If you decide this insight is cross-project (general tooling, language behavior), suggest the user use `/l` instead — but proceed with project capture if they invoked `/lp` deliberately.

## Decide first: save or clarify?

Examine the recent conversation. Decide:

- **Clear, project-specific insight** → write the pending file (see below).
- **Ambiguous** → do NOT write. Ask 1–2 short clarifying questions. Wait.

## How to save

Write a JSON file at: `c:\AN\CROWN\save-me-button-app-android\.teachcm\pending/<YYYYMMDD-HHMMSS>-<slug>.json`

Where `<slug>` is a kebab-case short slug derived from the title (max 40 chars).

JSON schema:

```json
{
  "version": 1,
  "title": "Short, specific title (max 80 chars; noun phrase or imperative)",
  "summary": "1–3 sentence summary anchored to this project",
  "content": "Full markdown body. Include file paths (relative to project root), module names, code refs like `src/foo.ts:42`. Be specific to this codebase.",
  "tags": ["tag1", "tag2"],
  "scope": "workspace",
  "source": {
    "trigger": "/lp",
    "hint": "<user's argument, if any>",
    "capturedAt": "<ISO8601 timestamp>",
    "references": ["relative/path.ext:42", "..."]
  }
}
```

## Tagging rules

- 2–5 tags. Lowercase, kebab-case, singular where possible.
- Read `c:\AN\CROWN\save-me-button-app-android\.teachcm\.meta\tags.json` if it exists; prefer existing tags.
- Encouraged tag families for this project: `area:<auth|build|ui|ipc|...>`, `module:<phone|wear|shared|...>`, `kind:<gotcha|decision|convention|fix>`. Use them when they fit.

## References

When the capture concerns specific files, include `source.references` with `relative/path:line` entries. The review panel renders them as clickable links.

## After writing

Tell the user one line:

> Saved (project): "<title>" — tags: [tag1, tag2]. Review in the Teach CLAUDE.md panel.

Do NOT paste the file path or JSON.

## Revisions

If the user follows up before reviewing, update the same pending file in place.

## Logging to command history

After successfully writing the pending file, append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/lp","arg":"<user argument verbatim or empty string>"}

Create the file and parent directory if missing. Skip logging if you only asked clarification questions and didn't write a pending file.
