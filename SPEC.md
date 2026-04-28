# SPEC.md — Save Me Button

Single source of truth for behavior. Update before commit, not after.

**Last updated:** 2026-04-28

---

## Goal

Wear OS panic-button app + companion phone app. A long-press on the watch upper button kicks off an escalation sequence (SMS+call to 3 contacts, repeating until someone answers) that runs locally on the watch when the watch has working cellular, otherwise on the phone.

---

## Configuration model

Persisted on phone (SharedPreferences `savemebutton_prefs`) and mirrored to watch via `DataClient` `/savemebutton/config`:

| Field | Type | Default | Range | Notes |
|---|---|---|---|---|
| `contacts[0..2].name` | String | `""` | — | Display label only. |
| `contacts[0..2].number` | String | `""` | — | E.164 preferred. Plain digits accepted. |
| `smsBody` | String | `"I need help. My location:"` | — | Coordinates appended at send time. |
| `holdSeconds` | Int | `3` | 3..5 | Upper-button hold needed to trigger. |
| `cancelTaps` | Int | `3` | 3..5 | Screen taps within 1.2 s to cancel/stop. |
| `countdownSeconds` | Int | `5` | 5..10 | Pre-sequence countdown (only before first contact). |
| `perContactWaitSeconds` | Int | `30` | 15..60 | Per-call wait for offhook before escalating. |
| `sirenTarget` | Enum | `BOTH` | NONE / WATCH / PHONE / BOTH | Which device(s) play the alert siren during a sequence. |
| `sirenSound` | Enum | `TWO_TONE` | TWO_TONE / KLAXON / WHOOP / PULSE | Synthesized waveform. |
| `sirenVolume` | Float | `1.0` | 0..1 | AudioTrack track volume. |
| `loudMinutePulse` | Bool | `false` | — | If on: 2-s siren burst every 60 s during sequence. |

If any contact's `number` is blank when SOS triggers, that slot is skipped (in every cycle).

Settings on the phone use a draft/saved model: edits stay in draft until **SAVE** is tapped (which persists + pushes to watch). **TEST** does Save + runs the real SOS sequence locally on the phone (real SMS, real call).

---

## Trigger

- Hardware: `KeyEvent.KEYCODE_STEM_PRIMARY` on watch.
- Activation: `onKeyDown` schedules a `Handler.postDelayed` for `holdSeconds * 1000 ms`. If the user is still holding when the timer fires, the SOS sequence starts immediately — the user does **not** need to release the button. Release before threshold cancels the pending fire.
- `onKeyLongPress` is consumed (returns `true`) so the system long-press-power menu does not appear.
- Trigger is ignored while `SosState != Idle` (no re-entry).

The phone's **TEST** button calls `PhoneSosHandler.triggerLocal(currentConfig)`. It runs its own countdown first.

---

## Capability resolution (`SosRoute`)

Probed at trigger time on the watch:

```
WATCH_SELF iff:
  pm.hasSystemFeature(FEATURE_TELEPHONY) AND
  TelephonyManager.simState == SIM_STATE_READY AND
  TelephonyManager.serviceState.state == STATE_IN_SERVICE
PHONE otherwise (including any SecurityException reading serviceState).
```

The resolved route is included in the `SOS_TRIGGER` payload sent to the phone.

---

## State machine (`SosState`)

```
Idle
  └─ trigger(route) ──→ Countdown(secondsRemaining=countdownSeconds)
      │
      ├─ cancelTaps taps in ≤ 1200 ms ──→ Canceled ──→ (after 1500 ms) Idle
      │
      └─ secondsRemaining=0 ──→ AcquiringLocation
          └─ resolved (≤ 4 s budget total) ──→ SendingSms(i=0)
              └─ smsResult ──→ Calling(i=0, cycle=0)
                  ├─ offhook detected ──→ CallActive(i) ──→ Done(answered=true)
                  ├─ perContactWaitSeconds elapsed without offhook ──→
                  │     i < 2 → SendingSms(i+1) (skip SMS if already sent in cycle 0)
                  │     i == 2 → cycle++; goto Calling(0, cycle) — SMS skipped (already sent)
                  └─ user gesture (cancelTaps screen taps) ──→ Stopped ──→ Idle
```

SMS is sent **once per contact, on the first cycle only**. Subsequent cycles re-call without re-sending SMS. The loop continues indefinitely until any contact answers (CallActive → Done) or the user stops it.

`Stopped` is a new explicit state for user-initiated abort during the calling phase. Distinct from `Canceled` (countdown abort).

Watch UI rendering:
- `Idle` — "SAVE ME" centered, **bold red 38 sp**, with "HOLD MIN $holdSeconds SECONDS" in 18 sp white below.
- `Countdown(n)` — huge countdown number (78 sp), red background, vibration loop, siren tone, "TAP $cancelTaps TIMES TO ABORT" in 18 sp white extra-bold.
- `Canceled` — green "Canceled" caption.
- `AcquiringLocation` — "Locating…".
- `SendingSms(i)` — "SMS → contact[i].name".
- `Calling(i)` — "Calling contact[i].name".
- `CallActive(i)` — "Connected" green.
- `Stopped` — "Stopped" amber.
- `Done` — final summary.

In `PHONE` route: watch UI mirrors states received via `SOS_PROGRESS`. Watch's local orchestrator skips `runLocally` and only renders mirrored progress.

---

## Cancel / Stop gesture

Triple-tap on the **screen** (any position), `cancelTaps` taps within **1200 ms**.

- During `Countdown` → `Canceled` → back to `Idle`. Sequence aborts before SMS is sent.
- During `AcquiringLocation`, `SendingSms`, `Calling` → `Stopped` → back to `Idle`. SMS (if already sent) is **not** recalled; only the call loop ends. The watch also broadcasts `SOS_STOP` to the phone in case the phone is the orchestrator.
- Ignored during `CallActive` (user can hang up the call directly).

---

## Location

Both watch and phone use `FusedLocationProviderClient`.

**Watch (in WATCH_SELF route):**
1. Read `lastLocation` (instant).
2. If null, request a single-shot fix at `PRIORITY_HIGH_ACCURACY` with a **2 s budget**.
3. If still null, send `LOC_REQUEST` to the phone via `MessageClient` and `withTimeoutOrNull(2000)` for a `LOC_REPLY` carrying `SosCoords`. Total fallback budget: 2 s.
4. If neither produces a result, SMS uses `(location unavailable)`.

**Phone (in PHONE route or as responder):**
1. `lastLocation` then 2 s `currentLocation` budget. Replies (or sends, in PHONE route) whatever it has.

---

## Wearable Data Layer paths

| Path | Direction | Payload |
|---|---|---|
| `/savemebutton/config` | phone → watch (DataItem) | serialized `SosConfig` |
| `/savemebutton/sos_trigger` | watch → phone (Message) | serialized `SosTriggerPayload` |
| `/savemebutton/sos_progress` | phone → watch (Message) | serialized `SosState` |
| `/savemebutton/sos_cancel` | reserved (unused) | — |
| `/savemebutton/sos_stop` | watch → phone or phone → watch (Message) | empty — orchestrator stops + transitions to `Stopped` |
| `/savemebutton/loc_request` | watch → phone (Message) | empty — phone fetches and replies |
| `/savemebutton/loc_reply` | phone → watch (Message) | serialized `SosCoords` (empty bytes if no fix) |
| `/savemebutton/siren` | bidirectional (Message) | serialized `SirenCommand { start, sound, volume }` |

Constants in `shared/.../WearPaths.kt`.

---

## Alert siren

Programmatically synthesized via `AudioTrack` (`USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`). No bundled audio asset.

| Sound | Waveform |
|---|---|
| `TWO_TONE` | Square-wave alternating 800 Hz / 1200 Hz, 250 ms each. |
| `KLAXON` | 600→1500 Hz sweep over 350 ms, repeated. |
| `WHOOP` | 400→1400 Hz linear sweep over 1.5 s, repeated. |
| `PULSE` | 1000 Hz on/off 300 ms / 300 ms. |

When SOS triggers (real or test), each side decides whether to play locally / push to peer based on `sirenTarget`:

- `NONE` — neither device plays.
- `WATCH` — only the watch plays (the orchestrator pushes start to peer if peer is the watch; otherwise plays locally).
- `PHONE` — only the phone plays (mirror of above).
- `BOTH` — both play in parallel.

On sequence end / cancel / stop, the orchestrator unconditionally sends `SirenCommand(start=false)` to the peer (no-op if peer wasn't playing) and stops its own siren.

If `loudMinutePulse` is on, a parallel `Siren.burst()` plays the same waveform for 2 s every 60 s while the sequence runs, on top of the in-call audio when applicable.

---

## Telephony actions

**Send SMS** — `SmsManager.sendTextMessage`. Sent **only on first cycle** per contact. Tracked via `BooleanArray smsSent[3]`.

**Place call** — `Intent.ACTION_CALL` with `tel:` URI. Requires `CALL_PHONE`.

**Detect answered** — `TelephonyCallback.CallStateListener` (API 31+) or `PhoneStateListener.onCallStateChanged` (≤30). Treat `CALL_STATE_OFFHOOK` after call placement as "answered". Hard ceiling = `perContactWaitSeconds * 1000 ms` (configurable).

**End call** — programmatic `endCall` ducked in MVP (the next `ACTION_CALL` displaces the previous). Documented limitation.

---

## Permissions (declared in both manifests)

Unchanged from MVP: `SEND_SMS`, `CALL_PHONE`, `READ_PHONE_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `VIBRATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`. Phone also has `INTERNET`.

Watch only requests SMS/CALL/READ_PHONE_STATE at runtime when capability probe resolves WATCH_SELF.

---

## Out of scope (still)

- Real-time location updates (single fix only).
- Multi-part SMS.
- Programmatic call hangup.
- Foreground service.
- Premium IAP / paywall.
- Localized strings.
- Adaptive icons.
- Hilt DI.
- Tests.

---

## Module → file map (changes vs. MVP marked ⊕ added, ✎ modified)

```
shared/src/main/java/com/savemebutton/shared/
  Models.kt          ✎ + holdSeconds, cancelTaps, countdownSeconds, perContactWaitSeconds,
                       sirenEnabled, sirenSound, sirenVolume, loudMinutePulse
                     ✎ + SirenSound enum, SirenCommand, SosState.Stopped
  WearPaths.kt       ✎ + SOS_STOP, LOC_REQUEST, LOC_REPLY, SIREN
  Siren.kt           ⊕ programmatic AudioTrack siren, 4 waveforms

wear/src/main/java/com/savemebutton/wear/
  WearApplication.kt              ✎ wires Siren, RemoteLocationBridge
  data/ConfigRepository.kt        (unchanged)
  sos/CapabilityProbe.kt          (unchanged)
  sos/LocationProvider.kt         ✎ 2 s watch budget + 2 s phone fallback
  sos/RemoteLocationBridge.kt     ⊕ LOC_REQUEST sender / LOC_REPLY waiter
  sos/WatchTelephony.kt           ✎ waitForAnswer(timeoutMs)
  sos/SosOrchestrator.kt          ✎ repeat-until-answer loop, Stopped state, dual-device siren,
                                    minute-pulse, configurable timings, triple-tap during Calling
  sos/RemoteSosBridge.kt          ✎ + pushSiren, pushStop
  sync/PhoneListenerService.kt    ✎ handles LOC_REPLY, SIREN, SOS_STOP
  presentation/MainActivity.kt    ✎ Handler-based auto-trigger while held
  presentation/SosViewModel.kt    ✎ stop(), config getters, configurable cancelTaps
  presentation/SosUI.kt           ✎ red bold SAVE ME, dynamic-text Idle and Countdown screens,
                                    Stopped state rendering

phone/src/main/java/com/savemebutton/phone/
  PhoneApplication.kt             ✎ wires Siren, PhoneLocationResponder
  data/ConfigRepository.kt        (unchanged)
  sos/PhoneTelephony.kt           ✎ waitForAnswer(timeoutMs)
  sos/PhoneLocationProvider.kt    ✎ 2 s budget
  sos/PhoneLocationResponder.kt   ⊕ replies to LOC_REQUEST
  sos/PhoneSosHandler.kt          ✎ repeat-until-answer, configurable, minute-pulse, dual siren,
                                    triggerLocal() for TEST button
  sync/WearListenerService.kt     ✎ handles LOC_REQUEST, SIREN, SOS_STOP
  sync/WatchSyncBridge.kt         ✎ + pushSiren
  presentation/MainActivity.kt    (unchanged)
  presentation/MainViewModel.kt   ✎ draft/saved model, SAVE, TEST, all setters
  presentation/MainUI.kt          ✎ SAVE ME header in red, Save+Test buttons, sliders,
                                    sound dropdown + volume + minute-pulse toggle
  presentation/Theme.kt           (unchanged)
```
