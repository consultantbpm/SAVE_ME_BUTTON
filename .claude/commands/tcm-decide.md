---
description: Record an Architecture Decision Record (ADR) — captures WHY a choice was made, distinct from reusable knowledge (/lp)
argument-hint: [decision title or short description]
---

The user invoked `/tcm-decide` to record an architectural decision. This is **NOT** a reusable knowledge capture (use `/lp` for that). This is a **decision log** — a snapshot of "why we chose X over Y, what we considered, what the consequences are".

## Decide first: clear decision or clarify?

Examine the recent conversation. Decide:

- **Clear decision present**: there's a concrete choice (architecture, library, approach, scope cut, naming, etc.) with at least one alternative considered. Write the ADR.
- **Ambiguous** (vague, no alternatives, just an opinion): ask 1-2 clarifying questions. Do NOT write the file.

Good triggers: "we agreed X over Y because Z", "decision: use approach A", "we'll go with B not C".
Bad triggers: "I like X" (preference), "use Y" (instruction without context).

## Where to save

Path: `c:\AN\CROWN\save-me-button-app-android\.teachcm/decisions/<YYYYMMDD>-<slug>.md`

Where `<slug>` is a kebab-case short slug derived from the title (max 40 chars).

If the directory doesn't exist, create it.

## File template

```markdown
---
id: <UUID-v4 or short hash>
title: <Concise decision title — noun phrase, max 80 chars>
date: <ISO8601 date YYYY-MM-DD>
status: accepted
tags: [<2-5 tags, lowercase kebab>]
---

## Context
<2-4 sentences: what forces, constraints, or requirements drove the need for this decision. Include the relevant pieces of state (current architecture, time pressure, team size, etc.).>

## Decision
<1-3 sentences stating the chosen approach in plain language.>

## Alternatives considered
- **<Option A>**: <why rejected, in 1 sentence>
- **<Option B>**: <why rejected, in 1 sentence>
- (List 1-3 alternatives the user actually considered. If only one was discussed, write only the chosen one.)

## Consequences
- **Positive**: <what gets easier or better>
- **Negative**: <what gets harder or costs more>
- **Risks to monitor**: <what could go wrong if assumptions are wrong>

## References
<Optional. File paths, links, or other artifacts that informed this decision.>
```

## After writing

Update the index file `c:\AN\CROWN\save-me-button-app-android\.teachcm/decisions/INDEX.md`:

- If it doesn't exist, create with header:
  ```markdown
  # Architecture Decision Records
  
  Chronological list of decisions taken for this project.
  ```
- Append a line: `- [<YYYYMMDD> — <title>](<filename>)` ordered by date descending (newest first).

## After writing, reply with one line

> Decizia salvată: "<title>". Status: accepted. Vezi `.teachcm/decisions/<filename>`.

Do NOT explain the decision back to the user — they just made it; they don't need an echo.

## Logging to command history

After writing, also append a JSONL entry to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:
```json
{"ts":"<ISO8601>","cmd":"/tcm-decide","arg":"<original user argument>"}
```

Create the file if missing.

## Tagging rules

- 2-5 tags, lowercase kebab-case.
- Read `c:\AN\CROWN\save-me-button-app-android\.teachcm\.meta\tags.json` if it exists and prefer matching tags.
- Tag the **topic of the decision** (`auth`, `database`, `naming`, `pricing`), not the act (`decision`, `chose`).

## Status field

Default `accepted` for new ADRs. Other values for later updates:
- `superseded` — replaced by a later ADR (link to it in References).
- `deprecated` — no longer applies but kept for history.

Users can update status later via `/lp` or by editing the file directly.
