# Nova Dialer 2.0 — Porting Plan & Change Log

Nova Dialer 2.0 is a **rebuild**: instead of patching the original Nova Dialer code (which contained
compile errors, dead features and fake functionality), the mature **Secure Dialer** codebase was
used as the foundation, rebranded to Nova Dialer, and Nova Dialer's genuinely useful features were added
back on top.

- **Foundation / upstream:** Secure Dialer (`com.example`, GPL-3.0-or-later)
- **Target:** Nova Dialer (`com.aistudio.novadial.kpxm`), GPL-3.0-or-later
- **Version:** 2.0.0 (versionCode 3), minSdk 24, targetSdk 36

> Both projects used the same `com.example` namespace, so the upstream source tree dropped into
> place path-for-path. Copyright headers are preserved; every new file added by this port carries
> the same `Copyright (C) 2026 MovStore / SPDX-License-Identifier: GPL-3.0-or-later` header so the
> derivation stays license-clean.

---

## 1. Why a port instead of a patch

An audit of the original Nova Dialer found defects that would prevent it from building or from working
on a device. Rebuilding on Secure Dialer eliminates them by construction and keeps the parts of
Nova Dialer users actually value.

| # | Original Nova Dialer defect | Status after port | How the port resolves it |
|---|--------------------------|-------------------|--------------------------|
| 1 | `BorderStroke.copy(...)` compile errors in `EmergencyAlertActivity.kt` (lines ~209/320) | **Fixed** | File removed; the broken Emergency SOS screen is not compiled (see §5). |
| 2 | Invalid manifest attribute `android:showOnLockScreen` | **Fixed** | Manifest rebuilt from upstream; only valid attributes (`showWhenLocked`, `turnScreenOn`) are used. |
| 3 | `FloatingCallWidgetService` called `startForegroundService()` without a matching `startForeground()` → crash on Android 8+ | **Fixed** | Upstream `DynamicIslandOverlayManager` provides the floating call surface correctly. |
| 4 | Dead code: `GestureControlManager`, `FlashAlertManager` partially wired | **Fixed** | Upstream `CallGestureSensorManager` / `FlashLightManager` are the real implementations. |
| 5 | `verifyPin` / `verifyPattern` always returned `false` (app lock could never succeed) | **Fixed** | Upstream uses `VaultBiometricAuthHelper` + device credential (`BiometricPrompt`). |
| 6 | Fake "Google Sync" that did nothing | **Fixed** | Removed; contacts come from the real system provider. |
| 7 | Invented spam database with made-up numbers + fake "threat feed" refresh | **Fixed** | Upstream `SpamDatabaseSettings` only stores rules the user adds; CSV import is local. |
| 8 | Destructive / data-losing Room migrations | **Fixed** | Upstream Room + SQLCipher schema with non-destructive migrations. |
| 9 | Unused dependencies (Firebase, OkHttp, Retrofit) | **Fixed** | `app/build.gradle.kts` only keeps what is used; commented placeholders remain. |
| 10 | Call blocking / Focus mode / protection rules did nothing | **Fixed** | Real `CallScreeningService` now rejects blocked/spam numbers and silences by policy (see §3). |
| 11 | Caller announcer not present in a working form | **Fixed** | Real TTS `CallerAnnouncerManager` + settings screen (see §4). |
| 12 | Emergency SOS feature (Nova Dialer-unique) | **Deferred** | Reference sources archived under `reference/novadial-original/` (see §5). |
| 13 | Analytics / call-stats dashboard (Nova Dialer-unique) | **Deferred** | Reference source archived; upstream `CallLogSummaryDashboard` already covers most of it (see §5). |

---

## 2. Rebrand

| Item | Value |
|------|-------|
| `applicationId` | `com.aistudio.novadial.kpxm` |
| App name (`app_name`) | `Nova Dialer` |
| `rootProject.name` | `Nova Dialer` |
| `versionName` / `versionCode` | `2.0.0` / `3` |
| Launcher icon | Nova Dialer's own adaptive icon (`ic_launcher_background`/`_foreground`, density PNGs) |
| Namespace | unchanged (`com.example`) |

All strings/URLs referencing "Secure Dialer" were replaced with "Nova Dialer" across the resources.

---

## 2.1 Aurora design language (2026 redesign)

Nova Dialer no longer ships the upstream Secure Dialer look. It has its own **"Aurora"** design
language — a luminous **indigo → violet → orchid** identity on deep indigo-black glass surfaces,
soft (26–36 dp) squircle geometry, pill chips/buttons and an expressive modern type scale.

> **License note:** a visual redesign does **not** remove the GPL. The source remains a derivative
> of Secure Dialer and stays **GPL-3.0-or-later** with attribution intact. The redesign gives
> Nova Dialer a distinct brand identity (avoiding trade-dress/brand confusion); it is not a way to
> relicense the code.

Changed by the redesign:

| Area | File | What changed |
|------|------|--------------|
| Color tokens | `ui/theme/Color.kt` | New Aurora palette + material tokens; avatar pairs kept |
| Playlists | `ui/theme/Theme.kt` | Replaced the 12 upstream palettes with 6 Nova Dialer themes (`nova_aurora` default, `nova_nebula`, `nova_glacier`, `nova_ember`, `nova_forest`, `nova_mono`); any legacy stored key falls back to Aurora; added glassy `surfaceTint`/`outlineVariant`/`scrim` treatment |
| Typography | `ui/theme/Type.kt` | New Aurora scale (bolder, tighter tracking, tabular digits) |
| Shapes | `ui/theme/AppShapes.kt` | Softer/rounder geometry; pill chips |
| Theme picker | `ui/components/ThemeColorPicker.kt` | New palette swatches + default seed `#5A4BE0` |
| Defaults | `ui/viewmodel/DialerViewModel.kt` | `theme_color` → `nova_aurora`, custom hex → `#5A4BE0` |
| Search bar | `ui/components/CommonComponents.kt` | Pill shape, glassy container, soft outline border |
| Settings cards/rows | `ui/components/Settings.kt`, `CommonComponents.kt` | Aurora card shape + pill icon chips |
| Nav bar | `ui/components/CommonComponents.kt` | Stronger active indicator |
| Recording folder | `CallAudioRecorder.kt`, all `strings.xml` | `Downloads/SecureDialer` → `Downloads/Nova Dialer` |
| Wake-lock tags | `MyInCallService.kt`, `ui/components/ActiveCall.kt`, test | `SecureDialer:*` → `Nova Dialer:*` |

The old settings/preferences file name `secure_dialer_prefs` is still *read* as an upgrade fallback
only (writes always target `dialer_prefs`); this keeps existing users' settings from resetting.

---

## 3. Focus Mode + Call Protection (ported — complete)

New package `com.example.telecom`:

| File | Purpose |
|------|---------|
| `CallProtectionPolicy.kt` | Pure rule engine. Given saved/VIP/favorite/repeat flags, focus mode and protection level, returns whether a call is silenced. Emergency numbers are never silenced. |
| `CallProtectionPrefs.kt` | `SharedPreferences` (`novadial_call_protection`) for focus mode, protection level, repeat-caller policy, VIP numbers and announcer options. VIP matching uses `PhoneNumberUtils.compare`. |
| `CallDecisionStore.kt` | In-memory (2-minute) record, keyed by last 8 digits, of numbers the screening service silenced so the announcer stays quiet for them. |
| `NovaDialCallScreeningService.kt` | `CallScreeningService`. Rejects blocked numbers (`BlockedNumberContractManager` + Room blocklist) and numbers in the offline spam DB; otherwise applies Focus Mode / protection and silences the ring (Android 10+). Declared in the manifest with `BIND_SCREENING_SERVICE`. |

> **Adaptation note:** the screening service was rewritten to use Secure Dialer's
> `DialerDao` (`isBlocked*`, `isSpamNumber`) and `BlockedNumberContractManager` instead of the
> original `RealtimeSpamDetector` / `BlockAction` types, which do not exist in this codebase.

### Settings UI

`com.example.ui.components.FocusAndProtectionSettings` is a self-contained Compose screen (tab 9 of
Settings), reachable from a new "Focus & Protection" card and from the settings search index. It
covers: Focus mode, protection level, repeat-caller toggle, VIP number add/remove, and the full
announcer configuration with a live preview button.

---

## 4. Caller Announcer (ported — complete)

| File | Purpose |
|------|---------|
| `AnnouncerScript.kt` | Pure, unit-testable template renderer + script builder (`{name}`, `{user}`, `{type}`), capped at 15 s / 3 repeats every 5 s. |
| `CallerAnnouncerManager.kt` | TextToSpeech engine. Speaks on the ringtone stream while `STATE_RINGING`, stops on answer/reject/end, stays silent in silent/vibrate/DND, honours "headphones only", and skips calls that were silenced by policy. |

Hooked into `MyInCallService`: created in `onCreate`, announced from the `STATE_RINGING` branch of
`handleCallState`, stopped on `STATE_ACTIVE`/`STATE_DISCONNECTED`, and shut down in `onDestroy`.

> **Adaptation note:** caller lookup uses Nova Dialer's `getContactNameFromNumber(context, number)`
> instead of the original `DeviceContactsHelper.lookupCallerInfo`.

### Tests

- `app/src/test/java/com/example/telecom/AnnouncerScriptTest.kt`
- `app/src/test/java/com/example/telecom/CallProtectionPolicyTest.kt`

Both exercise pure logic only (no Android framework), so they run on the JVM.

---

## 5. Deferred features (Emergency SOS & Analytics)

These two Nova Dialer-only features were **not** merged in this pass. Their original sources are kept
for reference (not compiled) under `reference/novadial-original/`:

```
reference/novadial-original/
  telecom/       EmergencyAlertActivity.kt, EmergencySettings.kt,
                 EmergencyTriggerManager.kt, EmergencyTriggerService.kt,
                 RealtimeSpamDetector.kt, FloatingCallWidgetService.kt,
                 GestureControlManager.kt, FlashAlertManager.kt
  ui-screens/    EmergencyScreen.kt, AnalyticsScreen.kt, CallProtectionSection.kt
  data-model/    SpamBlocklistModels.kt, BlockedNumber.kt, DeviceContactsHelper.kt
  README.novadial-original.md, metadata.novadial-original.json
```

### 5.1 Emergency SOS — deferred

- **Why deferred:** the original contained a compile error (`BorderStroke.copy`), and the trigger
  service / floating widget were unreliable. It needs careful re-implementation on top of the
  upstream `CallManager` and a foreground service with a proper `startForeground`.
- **To finish:** port `EmergencyTriggerManager` to drive the upstream `CallManager`, build the alert
  activity fresh (do **not** copy the broken `EmergencyAlertActivity`), and route the SOS call
  through the same `ACTION_CALL` / telecom path the rest of the app uses. Follow the upstream
  foreground-service patterns used by `DynamicIslandOverlayManager`.

### 5.2 Analytics — deferred

- **Why deferred:** the upstream `CallLogSummaryDashboard` already provides call counts, talk time
  and quick filters, so a separate analytics screen is largely redundant.
- **To finish:** either delete `AnalyticsScreen.kt` as duplicate, or re-add any Nova Dialer-only metrics
  (e.g. per-contact trends) as an extra section inside `CallLogSummaryDashboard`.

### 5.3 Offline spam DB / community reports

The genuine, local parts of Nova Dialer's spam handling (CSV blocklist import, manual entries, offline
matching) are already covered by upstream `SpamDatabaseSettings` + `NovaDialCallScreeningService`.
The original `RealtimeSpamDetector`, `CommunitySpamManager` and the made-up seed data were
deliberately **not** ported.

---

## 6. Verification status

This port was authored **without a JDK or Android Studio** on the build machine, so it has **not
been compiled**. What was done instead:

- Every new/changed reference was cross-checked against the real upstream API surface
  (`DialerDao`, `AppDatabase.getDatabase`, `BlockedNumberContractManager`, the Compose helper
  components, and the `stringResource`/icon imports).
- The pure logic is covered by the two JVM unit tests above.

**Before shipping:** open the project in Android Studio, `./gradlew testDebugUnitTest`, then
`./gradlew assembleDebug`, and install on a real phone. Use the checklist in `README.md`.

---

## 7. License

Secure Dialer is licensed **GPL-3.0-or-later**; Nova Dialer 2.0 is a derivative work and stays
GPL-3.0-or-later. Existing copyright headers are preserved and new files carry the same header.
