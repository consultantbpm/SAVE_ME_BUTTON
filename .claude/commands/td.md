---
description: Build, install, and FUNCTIONALLY verify new/modified functions in the installed build on a connected external device (Android phone / Wear OS watch, via adb)
argument-hint: [feature name; or --since=<git-ref> to set diff baseline; empty = HEAD~1..HEAD]
---

The user invoked `/td` to install the app on a connected external device and **functionally verify the new/modified functions** in the build. Default platform: Android via adb. iOS, web, and other platforms come later.

**Provisional implementation** — diff-aware logcat + optional instrumented tests + precise manual checklist tied to changed functions.

## Step 1 — Detect environment

1. `adb version` — verify adb on PATH. If missing, stop: "adb nu e în PATH. Instalează Android Platform Tools."
2. `adb devices -l` — list connected devices.
   - 0 devices → stop: "Niciun device conectat / authorized."
   - 1 device → use it.
   - Multiple → ask which serial; use `-s <serial>`.
3. Capture device info: `adb shell getprop ro.product.model`, `ro.build.version.release`, `ro.product.cpu.abi`.
4. Detect Wear OS: `adb shell pm list features | grep -i wear`. Mark as **watch** if matches, else **phone**.

## Step 2 — Identify project + module

1. Look for `build.gradle.kts` / `build.gradle` at root.
2. Read modules from `settings.gradle.kts` / `settings.gradle`.
3. Read app package id from `<module>/src/main/AndroidManifest.xml`.

If not Android, stop: "Nu e un proiect Android. `/td` momentan suportă doar Android."

Pick module:
- Argument explicit (`/td watch` → wear module, `/td phone` → mobile).
- Active git branch hint.
- Device type detected (watch → wear module).
- If still ambiguous → ask.

## Step 3 — Build

- Linux/macOS: `./gradlew :<module>:assembleDebug`
- Windows: `.\gradlew.bat :<module>:assembleDebug`

On failure: parse Kotlin/Java compile errors (`e:` lines, `error:`, `> Task ... FAILED`), report file:line. Stop.

On success: locate APK at `<module>/build/outputs/apk/debug/<module>-debug.apk` (verify mtime > build start).

## Step 4 — Install

`adb install -r <apk-path>` (reinstall over existing).

Parse common failures:
- `INSTALL_FAILED_VERSION_DOWNGRADE` → suggest `adb uninstall <package>` then retry.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → signature mismatch; suggest uninstall + reinstall.
- `INSTALL_FAILED_INSUFFICIENT_STORAGE` → free space.
- `INSTALL_PARSE_FAILED_*` → APK corrupt; rebuild.

## Step 5 — Launch

- Read launcher activity from `AndroidManifest.xml` (`MAIN` + `LAUNCHER` intent filter).
- `adb shell am start -n <package>/<activity>`
- Wait 3s.
- Verify foreground: `adb shell dumpsys window windows | grep mCurrentFocus`.

If not foreground, capture last 50 lines of logcat for diagnostic.

## Step 6 — Identify new/modified functions (diff-aware)

Determine the diff baseline:
- If user passed `--since=<ref>` in argument, use that ref.
- Else default to `HEAD~1..HEAD`.

List modified source files in the chosen module:
```
git diff <baseline> --name-only -- '<module>/src/main/**/*.kt' '<module>/src/main/**/*.java'
```

For each modified file:
- Read diff with context: `git diff <baseline> -U10 -- <file>`.
- Extract names of functions/methods whose body has changed lines. Patterns:
  - Kotlin: `fun <name>(`, `private fun`, `internal fun`, `suspend fun`, `override fun`, `protected fun`.
  - Java: signatures of form `<modifiers> <returnType> <name>(...) {`.
- Walk back from each diff hunk to find the enclosing function declaration.
- Record `(file:line, package.Class.function, signature)`.

Build `modifiedFunctions[]`. If empty, note: "No source-level changes detected vs <baseline>; will rely on smoke only."

For each function, also extract its log statements:
- Search the function body for `Log\.[vdiwe]\(\s*"<tag>"\s*,\s*"<msg>"`, `Timber\.[a-z]+\("<msg>"`, `println\("<msg>"`, etc.
- Record log tags + message keywords as `expectedLogs` for that function.

## Step 7 — Run instrumented tests (if available)

Check for `<module>/src/androidTest/` containing at least one `*Test.kt` or `*Test.java`.

If yes:
- Run: `./gradlew :<module>:connectedDebugAndroidTest`
- Parse JUnit XML at `<module>/build/outputs/androidTest-results/connected/TEST-*.xml`
- Count `passed` / `failed` / `skipped`.
- For each failure: extract test class + method + first line of stack trace.

If no:
- Skip; mark "no instrumented tests configured". Fall back entirely to Step 8 (logcat) + Step 9 (manual).

## Step 8 — Logcat capture, targeted at modified functions

Build a logcat filter that includes:
- App's package (always).
- All `<tag>` values extracted from `expectedLogs[]` in modifiedFunctions.
- `AndroidRuntime:E` (uncaught exceptions).
- `*:F` (FATAL).

Capture for ~10 seconds after launch:
```
adb logcat -d -t '10s ago' <package>:* <tag1>:* <tag2>:* ... AndroidRuntime:E *:F
```

(If `-t '10s ago'` not supported, fall back to `adb logcat -d` and filter timestamps manually.)

For each `modifiedFunction`:
- Scan captured logcat for any of its `expectedLogs[]` patterns.
- If matched → mark **OBSERVED** (function was exercised during smoke).
- If not matched → mark **NOT_OBSERVED** (function needs manual trigger).

Also categorize issues:
- `FATAL EXCEPTION` → CRITICAL.
- App-package stack traces → CRITICAL.
- `E/` from app package → ERROR.
- `W/` from app package → WARN.

## Step 9 — Generate functional manual checklist (diff-aware)

For each `modifiedFunction` that is NOT_OBSERVED, generate a PRECISE manual instruction tying back to the changed function.

Heuristics:
- Activity/Fragment lifecycle (`onCreate`, `onResume`, `onStart`): "Re-open the app (or navigate to screen `<X>`) — function should fire."
- Click handler (`onClick`, name contains `Click`/`Tap`/`Press`/`Submit`): "Tap the button bound to `<function>`. Expected: log `<expectedTag>: <expectedMsg>`."
- ViewModel methods: "Trigger the user action that calls `<function>` (likely button → ViewModel)."
- Service / WorkManager / BroadcastReceiver: "Trigger via app action that schedules this; or fire intent: `adb shell am broadcast -a <action>`."
- Coroutine / Flow collectors: "Trigger the upstream source (network response, DB write, user input)."
- Generic fallback: "Exercite manually `<package.Class.function>`. Expected log: `<expectedTag>: <expectedMsg>` (if log present), else verify UI/behavior described in the function."

For OBSERVED functions, include in output but mark as auto-verified — no manual step needed.

Always also include 1-2 generic checks for the feature (from argument or context) covering visual / UX aspects not tied to a specific function.

## Step 10 — Output

```
/td report — feature: <name>
Device: <model> · Android <version> · <phone|watch>
Module: <module>
Diff baseline: <range>

Build:       ✅ OK / ❌ FAIL: <reason>
Install:     ✅ OK / ❌ FAIL: <reason>
Launch:      ✅ OK / ❌ FAIL: <reason>
Instr tests: ✅ X/Y passed / ⚠️ not configured / ❌ <reason>
Logcat 10s:  <N critical>, <M errors>, <K warnings>

Modified functions (<count> detected):
✅ <pkg.Class.fn1>             OBSERVED (log seen)
⚠️  <pkg.Class.fn2>             NOT_OBSERVED — needs manual trigger
⚠️  <pkg.Class.fn3>             NOT_OBSERVED — needs manual trigger

Top runtime issues:
- <verbatim log line with tag/PID>
- <verbatim log line with tag/PID>

Manual functional checks for NOT_OBSERVED:
1. <precise step tied to fn2> — expect: <expected outcome>
2. <precise step tied to fn3> — expect: <expected outcome>

Generic feature checks:
A. <UX-level instruction tied to feature name>

Status: build+install+launch X/3 · instr Y/Z · modified A observed, B pending manual.
Log: .teachcm/test-runs/<filename>
```

## Step 11 — Persist log

Append the full output **plus** the raw logcat capture (truncated to 300 lines if larger) **plus** the diff summary (modifiedFunctions list) to `.teachcm/test-runs/<YYYYMMDD-HHMMSS>-<feature-slug>.log`. Create the directory if missing.

## Limits

- **Android only** for now.
- **Diff baseline default = `HEAD~1..HEAD`** — if user hasn't committed yet, may show 0 modified functions. Suggest staging + commit first, or pass `--since=<earlier-ref>`.
- **OBSERVED requires the function to have log statements** that fire during smoke. Functions with no logs default to NOT_OBSERVED even if they ran.
- **UI gestures still manual** — no automated taps/swipes in provisional version (UI Automator setup deferred).
- **Screenshots not captured** in v1.
- **Heuristic-based manual instructions** — they're educated guesses from function names + types. User should sanity-check before executing.

## If anything fails

- Report verbatim with file:line / package / serial as relevant.
- Do NOT auto-fix code. Wait for explicit instruction.
- If multiple checks fail, list them all.

## If everything passes automated + observed

End with:
> Build + install + launch OK pe <device>. <N> functii modificate auto-verificate prin logcat, <M> necesită trigger manual (vezi lista). Spune-mi după ce execuți manual ce vezi sau dacă apare orice neașteptat.

Wait for user input.

## Logging to command history

After completing the test run (whatever the outcome), append a JSONL line to `c:\AN\CROWN\save-me-button-app-android\.teachcm/.meta/command-history.jsonl`:

    {"ts":"<ISO8601 now>","cmd":"/td","arg":"<feature or argument verbatim>"}

Create the file and parent directory if missing.
