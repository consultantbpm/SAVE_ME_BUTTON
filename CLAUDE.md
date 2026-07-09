# CLAUDE.md — Save Me Button

Project-specific guidance for Claude Code. The global procedure at `c:\AN\CLAUDE.md` also applies and takes precedence on conflicts — this file adds project-specific context only.

---

## What this is

Wear OS panic-button app + companion phone app.

**Trigger.** Long-press upper watch button (`KEYCODE_STEM_PRIMARY`) for ≥ 3 s.

**Sequence.**
1. 5 s countdown on watch — intense vibration + loud alarm-tone siren.
2. Triple-tap watch screen during countdown (3 taps within 1.2 s) → cancel.
3. After countdown: SMS to contact #1 (default body + GPS coords) → place call to #1.
4. If call to #1 not answered within 30 s → SMS+call #2.
5. If #2 not answered → SMS+call #3.
6. After #3 attempt regardless of outcome → STOP. (Countdown only fires before #1; escalations are immediate.)

**Capability-aware routing.** On trigger the app resolves a `SosRoute`:
- `WATCH_SELF` if the watch has `FEATURE_TELEPHONY` + `SIM_STATE_READY` + `ServiceState.STATE_IN_SERVICE`. Watch sends SMS, places call, monitors call state, uses watch GPS.
- `PHONE` otherwise. Watch forwards `SOS_TRIGGER` to phone via `MessageClient`. Phone runs the full sequence using phone telephony + phone GPS, streams progress back to watch over `SOS_PROGRESS`.

**Answer-on-watch.** In WATCH_SELF the call originates on watch so answer is naturally on wrist. In PHONE the system's BT pairing routes the in-call audio to the watch.

---

## Collaboration Workflow

Follow this cycle for every task **without skipping steps**. Step 6 must finish (and the user must confirm) before step 8 starts.

0. **Project setup (once per project)** — `C:\Users\drago\.claude\settings.json` must grant permissions for local commands (`gradlew`, `adb`). If empty, Claude prompts on every call. Reload with `/config` after editing.
1. **User states the goal.**
2. **Ask clarifying questions** — only if genuinely ambiguous. Keep it short.
3. **User clarifies.**
4. **Research & propose** — present 2–3 implementation variants with advantages and limitations.
5. **User picks a variant.**
6. **Implement** — write the code, then **STOP** and summarize. Do not proceed to build without user confirmation.
7. **Iterate** — restart from step 4 if scope changes.
8. **Build & test** — only after user confirms step 6, run `.\gradlew.bat :wear:installDebug` (or `:phone:installDebug`).
9. **Publish** — when user is happy, commit and push. `git init` first if not yet a repo.
10. **Update [SPEC.md](SPEC.md)** — after every functional change. Bump the "Last updated" line. Before commit, not after.
11. **Memorize** — save corrections and validated approaches.
12. **Clean up before publish** — strip debug logs, temp files, commented-out code; ensure `.gitignore` covers build artifacts.
13. **Never skip hooks** — no `--no-verify`, no `--no-gpg-sign`. Fix the underlying cause.
14. **Communication tone** — terse; state results, not deliberation; no trailing summaries.

---

## Resources (project-scoped)

- **Watch** (OnePlus `OPWWE234`, serial `H631105000017EBS077Z`) — LAN IP `192.168.100.115`. **BT-tethered, no SIM** → capability probe will resolve to `PHONE` route on this device. Testing `WATCH_SELF` requires a cellular Wear OS device. Port changes per reboot; discover via `adb mdns services` (match `_adb-tls-connect._tcp` on that IP), then `adb connect 192.168.100.115:<port>`.
- **Phone** — LAN IP `192.168.100.113`. Same mDNS pattern.
- **Screenshots / screen captures** — `C:\Users\drago\CrossDevice\Nokia XR20`.
- **adb.exe** — `C:\Users\drago\AppData\Local\Android\Sdk\platform-tools\adb.exe` (or `/c/Users/drago/AppData/Local/Android/Sdk/platform-tools/adb.exe` from bash).
- **This PC** — full administrator rights granted. Do not ask before running local commands.

---

## Project

| Module | Role |
|---|---|
| `phone` | Android phone app (`com.savemebutton.app`, namespace `com.savemebutton.phone`). Compose UI: edit 3 contacts + default SMS body. Receives `SOS_TRIGGER`, runs SOS sequence in PHONE route. |
| `wear` | Wear OS app (`com.savemebutton.app`, namespace `com.savemebutton.wear`). Detects 3-s upper-button hold, runs countdown UI + triple-tap cancel, runs SOS sequence in WATCH_SELF route or forwards to phone. |
| `shared` | Kotlin/Android library (`com.savemebutton.shared`). Serializable models, `WearPaths` constants. |

**Stack.** Kotlin · Jetpack Compose · Material3 · Kotlinx Serialization · Google Wearable Data Layer (`MessageClient`/`DataClient`) · Google Play Services Location (Fused).

**Toolchain (pinned, known-good).** AGP `9.1.0` · Kotlin `2.1.0` · Gradle `9.3.1` (wrapper present at `gradle/wrapper/`). `compileSdk = 35` · phone `minSdk = 26` · wear `minSdk = 30`.

Premium IAP: not yet defined.

---

## Build & Run

Run from `c:\AN\SAVE ME BUTTON\`:

```bash
.\gradlew.bat :wear:installDebug
.\gradlew.bat :phone:installDebug
.\gradlew.bat :wear:assembleDebug
.\gradlew.bat :phone:assembleDebug
```

Wrapper is already in place (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`), copied from Crown Button. Both APKs build clean: `phone-debug.apk` (~10 MB), `wear-debug.apk` (~25 MB).

**If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**:
```bash
adb -s 192.168.100.115:<port> uninstall com.savemebutton.app.debug
```

**Launch the wear app after install**:
```bash
adb -s 192.168.100.115:<port> shell am start -n com.savemebutton.app.debug/com.savemebutton.wear.presentation.MainActivity
```

**Logcat filters** (clear first with `adb -s ... logcat -c`):
- `SmbKeys:D` — hardware button events on watch
- `SmbSos:D` — orchestrator state transitions
- `SmbCap:D` — capability probe result
- `SmbLoc:D` — location fix budget + result
- `SmbTel:D` — telephony actions (sms sent, call placed, offhook detected)
- `SmbWear:D` — Wearable Data Layer in/out (both sides)

---

## Architecture

```
Watch upper-button hold ≥ 3 s
  → MainActivity.onKeyUp (KEYCODE_STEM_PRIMARY)
     → SosViewModel.trigger()
        → CapabilityProbe.resolveRoute()
           ├─ WATCH_SELF → SosOrchestrator runs locally:
           │    Countdown(5s) → LocationProvider.fetch() →
           │    for each contact: WatchTelephony.sendSms() + callAndWait(30s)
           │
           └─ PHONE → RemoteSosBridge.dispatch():
                MessageClient.sendMessage(SOS_TRIGGER, payload)
                Phone WearListenerService → PhoneSosHandler.handleTrigger():
                  PhoneLocationProvider.fetch() →
                  for each contact: PhoneTelephony.sendSms() + callAndWait(30s) →
                  MessageClient.sendMessage(SOS_PROGRESS, state) → watch UI

Phone settings change (3 contacts, SMS body)
  → ConfigRepository persists to SharedPreferences "savemebutton_prefs"
     → DataClient.putDataItem(CONFIG) → watch ConfigRepository writes its mirror
```

---

## SOS state machine

```
Idle
  └─ trigger() ──→ Countdown(5..1)
                      ├─ tripleTap ──→ Canceled ──→ Idle
                      └─ tick=0 ──→ AcquiringLocation
                                       └─ done/timeout ──→ SendingSms(i=0)
                                                              └─ Calling(i=0)
                                                                   ├─ offhook ──→ CallActive ──→ Done
                                                                   └─ 30s ──→ if i<2 inc i ──→ SendingSms(i+1)
                                                                              else ──→ Done
```

`SosState` is a sealed class; watch UI renders per state. In PHONE route the phone owns the state machine and the watch mirrors progress.

---

## Permissions

Both modules declare:
- `SEND_SMS`, `CALL_PHONE`, `READ_PHONE_STATE`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `VIBRATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`

Runtime permission requests live in `MainActivity` of each module. Watch only requests telephony perms when `CapabilityProbe` resolves WATCH_SELF.

---

## Gotchas

- **Path with spaces.** `c:\AN\SAVE ME BUTTON\` contains spaces. Gradle handles it but some tools break. If issues, rename to `save-me-button`.
- **OnePlus Watch 2 is BT-tethered.** Capability probe always resolves PHONE on this device. WATCH_SELF requires a cellular Wear OS device.
- **Triple-tap cancel false positives.** Strong vibration during countdown can cause wrist motion to jiggle the screen. Threshold = 3 taps within 1.2 s. Track in `SosViewModel`.
- **`serviceState` permission.** `TelephonyManager.serviceState` requires `READ_PHONE_STATE` on API 30+. `CapabilityProbe` catches `SecurityException` and falls back to PHONE route if it can't read state.
- **30 s timeout vs offhook.** Offhook detection on BT-routed audio can be flaky. 30-s hard timeout is the source of truth; offhook only *cancels* escalation when call is clearly answered.
- **No foreground service in MVP.** Backing out of watch activity cancels. Add a foreground service later if reliability needs it.
- **local.properties.** `sdk.dir` must point to `C:\Users\drago\AppData\Local\Android\Sdk`. Stale path from another user breaks the build.
- **Package id symmetry.** Phone and wear share `applicationId = com.savemebutton.app` (both get `.debug` suffix). Namespaces differ: `com.savemebutton.phone` and `com.savemebutton.wear`. Don't conflate.
- **`android.builtInKotlin=false` is required** in `gradle.properties` for this AGP 9.1.0 + `org.jetbrains.kotlin.android` combo. Without it the build fails at the phone module with `Cannot add extension with name 'kotlin', as there is an extension already registered`. Don't remove the flag even though AGP marks it deprecated — its replacement (built-in Kotlin) is incompatible with the Kotlin Android plugin we use.
- **Kotlin 2.1.0 promotes `StateFlow.distinctUntilChanged()` to `DEPRECATION_ERROR`.** It's a no-op on `StateFlow` anyway; just don't apply it. Same applies to other operator-fusion no-ops on `StateFlow`.

<!-- teachcm:start -->
## Teach CLAUDE.md — managed section

*This block is auto-managed by the Teach CLAUDE.md extension. Edit content outside the markers; this block will be rewritten.*

**Knowledge base layout for this project:**
- `.teachcm/notes/` — project-scoped learnings (saved via `/lp`).
- `.teachcm/pending/` — captures awaiting user review (Claude writes here).
- `.teachcm/.meta/tags.json` — existing tags; prefer these when proposing new captures.
- `~/.teachcm/notes/` — cross-project learnings (saved via `/l`).

**Slash commands installed in `.claude/commands/`:**
- `/lp <hint?>` — learn for this project (writes to project pending dir).
- `/l <hint?>` — learn globally (writes to ~/.teachcm/pending/).
- `/ps <screenshot-path>` — check screenshot against requirements, propose fix prompt.
- `/log <area?>` — instrument tracing at key points, then analyze logs.
- `/t <feature?>` — propose a test plan; implement only after user agrees.
- `/m <description?>` — generate or iterate a UI mockup (HTML + Tailwind) in `.tcm-mockups/`.
- `/td <feature?>` — build + install + smoke-test the current feature on a connected device (Android via adb, provisional).
- `/clp [n?]` — show last N slash commands invoked (history clipboard, default 100).
- `/tcm-decide <decizie>` — record an Architecture Decision Record (ADR) in `.teachcm/decisions/`.
- `/tcm-status` — quick dashboard of library / pending / decisions / mockups / token budget.
- `/tcm-context <topic>` — build a single context bundle (markdown) for a fresh Claude session.
- `/tcm-portfolio [sync|preview|init|add-target|list-targets]` — sync developer app portfolio from Google Sheets, distribute `portfolio.json` to Android apps.

**Behavior contract for `/l` and `/lp`:**
- If the insight is clear, write `<timestamp>-<slug>.json` to the appropriate `pending/` directory and announce a one-line summary.
- If ambiguous, ask 1–2 clarifying questions and do NOT write a file.
- Always prefer existing tags from `tags.json`. Lowercase kebab-case. 2–5 tags.
<!-- teachcm:end -->
