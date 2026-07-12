---
description: Show the last N slash commands you invoked (Teach CLAUDE.md commands), for easy reuse
argument-hint: [number — default 100, max 1000]
---

The user invoked `/clp` (command "clipboard") to see the history of slash commands they used recently, so they can copy-paste one to reuse without typing it again.

**Limit**: this captures only commands that flow through this extension (`/l`, `/lp`, `/ps`, `/log`, `/t`, `/m`, `/td`, `/tcm-*`, `/clp`). Claude Code built-ins (`/help`, `/clear`, etc.) and regular chat messages are NOT in the history — they're outside our reach.

## Read the history

File: `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`

Format: one JSON object per line, like:
```json
{"ts":"2026-05-15T14:23:11Z","cmd":"/m","arg":"mockup pentru section editor"}
{"ts":"2026-05-15T13:50:02Z","cmd":"/lp","arg":"cum gestionăm token tokens"}
```

If the file doesn't exist or is empty:

> Istoricul de comenzi e gol. Pe măsură ce folosești comenzi `/l`, `/lp`, `/m`, `/td`, etc., vor apărea aici.

Stop. Don't proceed.

## Parse the argument

- `/clp` (no arg) → show last 100.
- `/clp <number>` → show last `<number>` (clamp to range 1-1000).

## Format

Number entries 1-N (1 = most recent), with relative timestamp + command + argument (truncated to 80 chars if longer):

```
Ultimele <N> comenzi:

 1.  acum 2 min   /m mockup pentru section editor cu sidebar și badge
 2.  acum 15 min  /lp cum gestionăm token-urile de sesiune
 3.  acum 1 oră   /log activation flow
 4.  acum 3 ore   /tcm-decide adoptam tcm- prefix peste l-
 5.  ieri 14:23   /td watch
 ...
```

**Relative timestamp rules**:
- < 60s → `acum X sec`
- < 60min → `acum X min`
- < 24h → `acum X ore`
- yesterday → `ieri HH:MM`
- > 1 day → `acum X zile` or `YYYY-MM-DD HH:MM`

## After listing

End with one line:

> Copiază linia dorită și lipește-o ca prompt nou.

Do NOT execute any of the listed commands. `/clp` is read-only — just shows.

## If user asks to re-run #N

If after the listing the user says "rulează #3" or "fă din nou pe asta", the user will likely re-paste the command themselves. Don't auto-execute — that would bypass their intent.

## Logging

Do NOT log `/clp` invocations to the history file. Otherwise the list becomes polluted with read operations.
