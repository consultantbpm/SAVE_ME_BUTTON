---
description: Generate or iterate a UI mockup with Tailwind CSS in .tcm-mockups/
argument-hint: [what to mock, or describe the change to iterate the latest]
---

The user invoked `/m` to create or iterate on a UI mockup for this project (typically the Teach CLAUDE.md extension or plugin). Output is a self-contained HTML file the user previews in VSCode or a browser.

## Decide: new mockup or iteration?

- **New mockup** if the user named a UI surface ("section editor", "alias picker", "review panel") or there is no existing file in `.tcm-mockups/` matching the topic.
- **Iteration** if the user says "change X", "move Y", "smaller font", or otherwise refers to something they're already looking at. In that case, find the latest file in `.tcm-mockups/` for that topic (highest `-v<n>` for the slug, or most recent mtime if ambiguous), and **edit it in place** — do not create a new version unless the user asks for "v2".

If unsure which file the user means, ask one short question and stop.

## Context to load before generating

- `REZUMAT-DISCUTIE.md` at workspace root — product decisions, brand (Teach CLAUDE.md / `tcm` prefix / `teachcm:` plugin namespace), agreed UX patterns.
- `CLAUDE.md` — project conventions.
- Recent chat context — what feature is currently being discussed.

## File location & naming

All mockups live under `<workspace>/.tcm-mockups/`. Create the directory if missing.

Naming: `<slug>-v<n>.html`
- `<slug>`: kebab-case description of the UI surface (e.g. `section-editor`, `alias-picker`, `review-panel`, `library-tree`).
- `<n>`: starts at 1; for a fresh iteration on the same slug, increment only if the user asks for a new version side-by-side.

## File template

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Mockup: <descriptive title></title>
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-zinc-900 text-zinc-100 antialiased font-sans">
  <!-- Mockup content goes here. Use semantic Tailwind classes. -->
</body>
</html>
```

## Style guidelines

- **Theme**: dark by default (matches VSCode default). `bg-zinc-900` for body, `bg-zinc-800` for panels, `bg-zinc-700` for inputs/cards, `border-zinc-700` for separators.
- **Type scale**: `text-xs` (10-12px) for hints, `text-sm` (13-14px) for body, `text-base` (16px) for emphasis, `text-lg` for titles. Avoid huge sizes.
- **Spacing**: stay on Tailwind's standard scale (1, 2, 3, 4, 6, 8, 12).
- **Color semantics**: `text-emerald-400` for OK / safe, `text-amber-400` for warn / pending, `text-rose-400` for error / conflict, `text-sky-400` for info / links.
- **Icons**: prefer inline SVG copied from Lucide (`https://lucide.dev/icons/`). Do NOT add external JS icon libs — embed the SVG markup.
- **States stacked**: if a UI has multiple states (empty, loaded, error, expanded), show them all in the same file separated by `<h3 class="text-zinc-500 text-xs uppercase tracking-wider mt-12 mb-4">State: empty</h3>` so the user sees variations in one preview.
- **Annotations**: use HTML comments `<!-- behavior: ... -->` for non-obvious interactions that aren't visible in the static render.
- **Realistic data**: use plausible fake content (real-sounding tag names, real-sounding section titles like "Conventions", "Architecture") — placeholder "Lorem ipsum" makes it harder for the user to judge layout.

## After write/edit

Reply with **one short line**, in the language the user used (Romanian if they wrote in Romanian):

> Mockup salvat la `.tcm-mockups/<filename>`. Deschide cu Ctrl+Shift+V sau în browser. Spune-mi ce ajustez.

Do NOT explain the design choices. Let the mockup speak. Wait for the user's feedback.

## Iteration cues — common phrases and what to do

- "smaller font" / "font mai mic" → drop one Tailwind size (text-base → text-sm → text-xs).
- "move X to the right" / "muta X la dreapta" → `ml-auto`, `justify-end`, or `flex-row-reverse` depending on layout.
- "more spacing" / "mai mult spațiu" → bump padding/gap (p-2 → p-4, gap-2 → gap-4).
- "different color for X state" / "altă culoare pe X" → use semantic color classes from the palette above.
- "add a Y" / "adaugă un Y" → insert a new HTML block in the right place; copy the styling of nearby siblings for consistency.
- "remove X" / "scoate X" → delete the block.
- "show on hover" / "doar la hover" → `opacity-0 group-hover:opacity-100 transition-opacity` (and add `group` to parent).
- "compact" / "compactează" → reduce padding, line-height (`leading-tight`), and font size by one step.
- "wider" / "mai lat" → `w-64` → `w-80` → `w-96` → `w-full`.

## When to ask

Only ask **one** clarifying question if the change is truly ambiguous (e.g. "make it better", "schimbă-l"). Otherwise just do the change and reply with the one-line confirmation.

## Future brand-aligned name

The user has agreed on the `tcm-` prefix convention; this command will also be available as `/tcm-mockup` after the brand-safe install pass. For now, `/m` is the active alias.

## Logging to command history

After writing or editing the mockup file, append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/m","arg":"<user description or argument verbatim>"}

Create the file and parent directory if missing.
