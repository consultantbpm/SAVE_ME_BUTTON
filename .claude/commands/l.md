---
description: Capture a knowledge entry into the GLOBAL Teach CLAUDE.md library (cross-project)
argument-hint: [optional hint about what to capture]
---

The user invoked `/l` to save knowledge to the **global** library — insights that apply across projects (tooling tricks, language quirks, personal conventions, cross-cutting patterns).

If you decide this insight is project-specific, suggest the user use `/lp` instead — but proceed with global capture if they invoked `/l` deliberately.

## Decide first: save or clarify?

Examine the recent conversation. Decide:

- **Clear insight present** → write the pending file (see below).
- **Ambiguous** (multiple plausible captures, no clear signal, generic context) → do NOT write a file. Ask 1–2 short clarifying questions. Wait for the user's answer.

## How to save

Write a JSON file at: `C:\Users\drago\.teachcm\pending/<YYYYMMDD-HHMMSS>-<slug>.json`

Where `<slug>` is a kebab-case short slug derived from the title (max 40 chars).

JSON schema:

```json
{
  "version": 1,
  "title": "Short, specific title (max 80 chars; noun phrase or imperative; not a question)",
  "summary": "1–3 sentence summary",
  "content": "Full markdown body. Include code blocks, file paths, examples, anti-patterns. Be specific.",
  "tags": ["tag1", "tag2"],
  "scope": "global",
  "source": {
    "trigger": "/l",
    "hint": "<user's argument, if any>",
    "capturedAt": "<ISO8601 timestamp>"
  }
}
```

## Tagging rules

- 2–5 tags. Lowercase, kebab-case, singular where possible.
- Read `C:\Users\drago\.teachcm\.meta\tags.json` if it exists (JSON array of known tags) and prefer existing tags when one fits.
- Tag the **topic** (`postgres`, `git`, `ts-types`), never the act (`learned`, `note`).

## After writing

Tell the user one line:

> Saved global: "<title>" — tags: [tag1, tag2]. Review in the Teach CLAUDE.md panel.

Do NOT paste the file path or JSON — the extension picks it up automatically.

## Revisions

If the user follows up with "add tag X" or "rename to Y" before reviewing, update the same pending file in place. Do not create a new one.

## Logging to command history

After successfully writing the pending file, append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/l","arg":"<user argument verbatim or empty string>"}

Create the file and parent directory if missing. Skip logging if you only asked clarification questions and didn't write a pending file.
