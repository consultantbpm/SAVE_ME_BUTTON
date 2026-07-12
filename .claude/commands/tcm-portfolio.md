---
description: Sync the developer's app portfolio from Google Sheets and generate portfolio.json for Android apps
argument-hint: [sync | preview | init | add-target <path> | list-targets]
---

The user invoked `/tcm-portfolio` to manage their app portfolio (cross-promo list of their published apps). Source of truth: a Google Sheet listing apps with Icon, Name, Package, Type, Description. Output: `portfolio.json` consumed by each Android app's "Portfolio" UI at runtime.

## Subcommands

- `/tcm-portfolio sync` — fetch latest from sheet, regenerate JSON, copy to targets, **auto-commit + push to GitHub** if configured.
- `/tcm-portfolio sync --no-push` — same as sync but skip the GitHub commit/push step.
- `/tcm-portfolio preview` — fetch sheet and show what JSON would be generated, **without writing**.
- `/tcm-portfolio init` — create the config file with defaults if missing.
- `/tcm-portfolio add-target <absolute-path>` — add an Android app's assets folder to the sync list.
- `/tcm-portfolio list-targets` — show configured app paths.
- `/tcm-portfolio push` — re-push the current local portfolio.json to GitHub (without re-fetching the sheet).

If no argument: assume `sync`.

## Config file

Path: `c:\AN\CROWN\save-me-button-app-android\.teachcm/portfolio-config.json`

Default structure:
```json
{
  "sheetUrl": "https://docs.google.com/spreadsheets/d/1FwtM43aTwodrT-3Q24lAHhb4LDoIioBQmLrrwkunkFY/edit?usp=sharing",
  "outputPath": "c:\AN\CROWN\save-me-button-app-android\.teachcm/portfolio.json",
  "targetApps": [],
  "githubRepo": {
    "localPath": "",
    "fileInRepo": "portfolio.json",
    "branch": "main",
    "autoPush": true
  }
}
```

On `init`: create with defaults if missing. Don't overwrite if exists.

`githubRepo` section is **optional** — if `localPath` is empty or section missing, auto-push is skipped silently. If present and `autoPush: true`, every successful `sync` triggers commit+push.

## sync workflow

1. **Read config** from `c:\AN\CROWN\save-me-button-app-android\.teachcm/portfolio-config.json`. If missing → stop with: "Config lipsă. Rulează `/tcm-portfolio init` mai întâi."

2. **Extract sheet ID** from `sheetUrl`. Pattern: `/spreadsheets/d/<ID>/`.

3. **Fetch CSV** from `https://docs.google.com/spreadsheets/d/<ID>/export?format=csv`. Use Bash + `curl -sL` (the `-L` follows redirects — Google uses 307 to a signed URL). If 401: stop with: "Sheet nu e public. Setează Share → Anyone with link → Viewer."

4. **Parse CSV**:
   - First line = header. Expected columns: `Icon`, `Name`, `Package`, `Phone companion/Watch` (or similar type), `Description`.
   - Handle quoted fields (RFC 4180 — fields with commas wrapped in `"..."`, double-quote escape `""`).
   - Skip rows where both `Name` AND `Package` are empty (placeholder rows).
   - Skip rows where `Package` doesn't look like a valid Android package (must contain at least one dot).

5. **Build JSON**:
   ```json
   [
     {
       "title": "<Name verbatim>",
       "packageName": "<Package verbatim>",
       "iconUrl": "<Icon URL if present, else empty>",
       "category": "<column 4 value, e.g. 'Phone companion/Watch'>",
       "shortDescription": "<Description verbatim>",
       "playStoreUrl": "https://play.google.com/store/apps/details?id=<Package>"
     }
   ]
   ```

6. **Write** the JSON (pretty-printed, 2-space indent) to `outputPath` from config.

7. **Distribute** to targets — for each `<path>` in `targetApps[]`:
   - If `<path>` is a directory, copy as `<path>/portfolio.json`.
   - If `<path>` ends in `.json`, copy verbatim to that file.
   - If path doesn't exist, log warning, don't fail the whole sync.

8. **Auto-push to GitHub** (skip if `--no-push` flag passed, OR if `githubRepo.localPath` empty, OR if `autoPush: false`):
   - Copy `outputPath` to `<githubRepo.localPath>/<githubRepo.fileInRepo>`.
   - Run from `githubRepo.localPath`:
     - `git diff --quiet -- <fileInRepo>` — if no changes, skip commit (no-op push).
     - `git add <fileInRepo>`
     - `git commit -m "Update portfolio — <N> apps as of <ISO8601 date>"`
     - `git push origin <branch>`
   - On push failure (network, auth, conflict): report verbatim, don't fail the whole sync. User can retry with `/tcm-portfolio push`.

9. **Report**:
   ```
   Portfolio sync:
   - Sheet: <sheet URL>
   - Entries valid: <N> (skipped <K> empty rows)
   - Written: <outputPath>
   - Distributed to <T> targets:
     - <path 1> ✅
     - <path 2> ⚠️ path missing, skipped
   - GitHub: ✅ pushed <commit-hash-short> to <branch> at <localPath>
              (or: ⏭️  no changes / ⚠️ push failed: <reason> / ⏭️  disabled)
   - Public URL: https://raw.githubusercontent.com/<owner>/<repo>/<branch>/<fileInRepo>
   ```
   (Extract owner/repo from `git remote get-url origin` in the local repo.)

## preview workflow

Same as sync steps 1-5, but instead of writing, **show the JSON inline** with a count summary. No file changes.

## add-target / list-targets

Read config, modify `targetApps[]`, write back. For `add-target`: dedupe (don't add if already in list). Normalize path with forward slashes.

## push (standalone)

`/tcm-portfolio push` — re-push the current local `portfolio.json` to GitHub WITHOUT re-fetching the sheet. Useful when:
- Sync was run with `--no-push` and you want to push later.
- You hand-edited `portfolio.json` locally.
- Last push failed and you want to retry.

Steps: same as the "Auto-push to GitHub" section above (steps 8 + part of 9). Stops with clear error if `githubRepo.localPath` is empty or local path doesn't exist.

## Errors

- Sheet 401 / 403 → "Sheet nu e public. Setează Share → Anyone with link → Viewer."
- Network failure → "Eroare rețea: <message>. Verifică internet sau URL."
- Config malformed → "Config corupt. Rulează `/tcm-portfolio init` să-l regenerezi."
- Target path nu există → warning, continuă cu restul targets.

## Logging to command history

After each invocation (sync, preview, add-target, list-targets, init), append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/tcm-portfolio","arg":"<subcommand + args>"}

Create the file and parent directory if missing.

## Limits & notes

- CSV parsing is naive: handles common cases but assumes RFC-ish formatting. If sheet uses unusual delimiters or encoding, may fail.
- `iconUrl` is taken verbatim from the sheet. If the Icon column contains an image (not URL), it'll be empty in the JSON — user has to host icons separately.
- No GitHub auto-push in v1. User commits & pushes `portfolio.json` manually to wherever apps fetch from.
- For app-side fetching (Kotlin), see `resources/portfolio-template/README.md` after install.
