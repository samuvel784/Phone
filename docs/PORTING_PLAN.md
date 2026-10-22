# Nova Dialer 2.1 — Porting Plan, Release Scope & Change Log

**Current status (22 October 2026):** release candidate for the simplified v1 build,
`2.1.0` (versionCode 4). The automated build, unit-test, lint, AAB-validation and signing
results are recorded in §5. No physical-device test has been performed in this environment.

This document started as the porting log for Nova Dialer 2.0. It now also records what the
2.1 release actually ships and which older, deferred features remain in the source tree but
are deliberately unreachable. Historical 2.0 material is kept in §5 so the derivation and the
decisions behind it remain auditable.

- **Foundation / upstream:** Secure Dialer (`com.example`, GPL-3.0-or-later)
- **Target:** Nova Dialer (`com.aistudio.novadial.kpxm`), GPL-3.0-or-later
- **Release:** 2.1.0 (versionCode 4), minSdk 24, targetSdk 36
- **Repository:** <https://github.com/samuvel784/NovaDialer>

> Both projects use the `com.example` namespace, so the upstream source tree dropped into
> place path-for-path. Copyright headers are preserved; new Nova Dialer files carry the
> `Copyright (C) 2026 Nova Dialer contributors` / GPL-3.0-or-later header. See
> [`NOTICE.md`](../NOTICE.md).

---

## 1. Why a port instead of a patch

The original Nova Dialer had defects that prevented it from building or from working reliably
on a device. Nova Dialer 2.0 was rebuilt on the mature Secure Dialer codebase, then Nova
Dialer-specific features were added or deferred deliberately. The original audit is retained
below; the "current status" column reflects the simplified 2.1 release, not the 2.0 plan.

| # | Original Nova Dialer defect | Current status in 2.1 | Resolution / note |
|---|-----------------------------|-----------------------|-------------------|
| 1 | `BorderStroke.copy(...)` compile errors in `EmergencyAlertActivity.kt` | **Removed** | The broken SOS screen is not compiled; the remaining SOS-era classes were deleted in the 2.1 scope cleanup. |
| 2 | Invalid manifest attribute `android:showOnLockScreen` | **Fixed** | The invalid attribute was removed. The incoming-call activity uses the valid `showWhenLocked` / `turnScreenOn` attributes. |
| 3 | `FloatingCallWidgetService` started a foreground service without `startForeground()` | **Removed** | The Dynamic Island/overlay implementation was deleted in 2.1; no overlay permission or overlay UI remains. |
| 4 | Dead `GestureControlManager` / partially wired `FlashAlertManager` | **Not in the release path** | The in-call gesture helper used by `ActiveCall` is the upstream implementation. The standalone flashlight helper is retained but not referenced. Neither is a deferred feature with a hidden trigger. |
| 5 | `verifyPin` / `verifyPattern` always returned `false` | **Replaced** | App lock is enforced with the device screen-lock credential (`KeyguardManager`). The biometric-only pre-prompt helper was removed; this build does not declare `USE_BIOMETRIC`. |
| 6 | Fake "Google Sync" | **Removed** | Contacts come from Android's Contacts Provider. |
| 7 | Invented spam database with made-up numbers | **Removed** | No seed data or fake threat feed. The local spam table remains only for legacy backup compatibility; spam labelling is hidden and no screening service is registered. |
| 8 | Destructive / data-losing Room migrations | **Fixed** | SQLCipher Room database, non-destructive migrations, fail-closed key handling. |
| 9 | Unused Firebase / OkHttp / Retrofit dependencies | **Fixed** | No Firebase or Retrofit dependency is packaged. Coil contributes its own OkHttp/Okio transport as a transitive library; the app has no `INTERNET` permission and defines no remote endpoint. |
| 10 | Call blocking / Focus mode / protection rules did nothing | **Blocking: implemented; Focus/protection: deferred** | Android system blocking plus a local blocked-number list are reachable. Focus Mode, call protection and the screening service are retained as deferred code and are **not** registered in the manifest. |
| 11 | Caller announcer was not present in a working form | **Deferred** | `CallerAnnouncerManager` and `AnnouncerScript` remain in the tree, but nothing instantiates the announcer in the 2.1 `MyInCallService`. |
| 12 | Emergency SOS (Nova Dialer-only) | **Removed** | The five `EmergencySos*` classes, settings card and triggers were deleted in the 2.1 scope cleanup. |
| 13 | Analytics / call-stats dashboard | **Deferred** | The upstream `CallLogSummaryDashboard` remains. A separate analytics settings screen exists only as unreachable code. |

---

## 2. Release identity and configuration

| Item | Value |
|------|-------|
| `applicationId` | `com.aistudio.novadial.kpxm` (intentionally unchanged for this candidate; review before the first Play upload) |
| App name (`app_name`) | `Nova Dialer` |
| `rootProject.name` | `Nova Dialer` |
| `versionName` / `versionCode` | `2.1.0` / `4` |
| `minSdk` / `targetSdk` | 24 / 36 |
| Namespace | `com.example` |
| UI | Compose, Day / Default / Night themes with the accent picker |
| Signing | Local upload signing via `key.properties`; the release task fails fast if credentials are absent |

The old 2.0 values (`2.0.0` / versionCode 3) are historical and no longer describe the
release candidate.

---

## 3. Current release scope

Status vocabulary: **Implemented** means present and wired into the shipped UI.
**Deferred** means code/data may remain for compatibility, but the feature has no ordinary
user entry point in this build. **Removed** means the implementation was deleted.

### 3.1 Dialer and Telecom features — implemented

| Area | Files / evidence | Status |
|------|------------------|--------|
| Default-dialer contracts (`DIAL`, `VIEW tel`, `CALL_BUTTON`) | `app/src/main/AndroidManifest.xml` | Implemented — physical verification outstanding |
| Telecom `InCallService` and lock-screen incoming-call surface | `MyInCallService`, `IncomingCallActivity` | Implemented — physical verification outstanding |
| Shared call state, answer/decline/hold/mute/speaker/audio routes | `CallManager`, `CallAudioRouteState`, `ActiveCall` | Implemented — physical verification outstanding |
| Multi-call answer/decline/swap/merge | `CallManager`, `MultiCallStateMachine` | Implemented — physical verification outstanding |
| SIM selection, call log, contacts, recents, favorites, dialpad, voicemail | `DialerRepository`, `MainScreen`, UI components | Implemented — physical verification outstanding |
| Speed dial, quick replies, call notes | repository/DAO + settings screens | Implemented — physical verification outstanding |
| Block / unblock numbers | `RecentCallRow`, `CallHistoryDetailsScreen`, `ContactProfileSheet`, `BlockedNumberContractManager` | Implemented — physical verification outstanding |
| Encrypted local backup / restore | `BackupRestoreManager`, `AppDatabase`, `DatabaseKeyManager` | Implemented — automated tests pass; physical verification outstanding |
| vCard import/export | `ContactsAndDataSettings`, `ContactShareUtils` | Implemented — physical verification outstanding |
| Device-credential app lock | `MainActivity`, `PrivacySecurityAboutSettings` | Implemented — physical verification outstanding |
| Themes, localization, baseline profile | `ui/theme`, `res/values*`, `baseline-prof.txt` | Implemented — automated build verification only |

### 3.2 Deferred features — retained code, no ordinary user flow

| Feature | Retained implementation | Why it cannot be reached in 2.1 |
|---------|-------------------------|----------------------------------|
| Call recording | `CallAudioRecorder`, `RecordingCrypto`, `RecordingDisclosureActivity`, `CallRecordingsSettings`, record controls in `ActiveCall`/`InCallControlGrid` | No `RECORD_AUDIO` permission is declared. The only `ActiveCallScreen` call sites pass `recordingEnabled = false`; the advanced settings screen that contains the recording card is not referenced by `SettingsPanel`. `ReleaseScope.CALL_RECORDING_ENABLED` is `false`, so manual and automatic start paths return early. |
| Callback reminders | `ReminderScheduler`, `ReminderAlarmReceiver`, `CallbackReminderDialog`, `ScheduledRemindersSettings` | The call-detail reminder card is gated by `ReleaseScope.CALLBACK_REMINDERS_ENABLED` (`false`). `ReminderAlarmReceiver` is not declared in the manifest and scheduling is disabled, so no alarm can fire. Existing reminder rows are still preserved/merged by backup restore. |
| Focus Mode / call protection | `CallProtectionPolicy`, `CallProtectionPrefs`, `CallDecisionStore`, `NovaDialCallScreeningService`, `FocusAndProtectionSettings` | The screening service has no manifest declaration. The settings composable has no navigation entry point. `MyInCallService` only consults the generic silent-ringing flag/decision store; it does not register a screening service. |
| Caller announcer | `CallerAnnouncerManager`, `AnnouncerScript` | Nothing in `MyInCallService` creates or calls the announcer. The only UI reference is inside the unreachable Focus/Protection settings screen. |
| Offline spam database / spam labelling | `spam_numbers` table, `SpamDatabaseSettings`, `CallBlockingSettings`, spam action in `RecentCallRow` | No screening service is registered. The spam action is gated by `ReleaseScope.SPAM_LABELS_ENABLED` (`false`); the spam settings screen is unreachable. Existing rows are retained and restored with backups, but there is no management or screening flow in this build. |
| Duplicate-contact finder | `DuplicateFinder`, `DuplicateFinderSettings` | No navigation entry point. The startup scan is gated by `ReleaseScope.DUPLICATE_FINDER_ENABLED` (`false`), so it no longer runs invisibly. |
| Home-screen widget | `FavoritesWidgetProvider`, `WidgetCallActivity` | Neither component is declared in the manifest. Favourites updates call a refresh helper, but Android has no registered provider to update. |
| Analytics settings screen | `CallAnalyticsSettings` | No navigation entry point; the supported Recents dashboard remains. |
| Biometric-only recording vault / app-lock pre-prompt | Former `VaultBiometricAuthHelper` and related UI | Removed. App lock uses the device screen-lock credential and declares no biometric permission. |

### 3.3 Removed features / code

- Emergency SOS: `EmergencySosService`, `EmergencySosBootReceiver`, `EmergencySosManager`,
  `EmergencySosPrefs`, `EmergencySosSettings` and all live triggers.
- Dynamic Island / overlay: `DynamicIslandOverlayManager`, `DynamicIslandPill`, overlay
  permission helpers and the Appearance settings card.
- Duplicate Finder settings card and the removed settings tab it targeted.
- Overlay/recording/SOS permission requests that previously appeared at startup.
- The `reference/novadial-original/` archive and the separate design-files copy. The historical
  notes remain in this document; the archive itself is no longer part of the repository.

### 3.4 Unresolved / requires device investigation

These are not code-scope defects, but they remain unverified until a physical device is used:

- Telecom role transitions, full-screen-intent grant/deny behaviour and lock-screen launch on
  Android 14/15/16.
- Multi-call transitions on a real carrier.
- Bluetooth/wired audio routing and OEM background restrictions.
- Encrypted database opening after key invalidation and backup/restore across a real upgrade.
- Play Console policy declarations and the final application ID decision.

The device matrix is in [`DEVICE_TEST_MATRIX.md`](DEVICE_TEST_MATRIX.md).

---

## 4. Historical 2.0 porting work

The 2.0 rebuild resolved the original compile/runtime defects by replacing the broken code
with the upstream Secure Dialer implementation. Some of that work is still shipped; some was
later deferred to keep the 2.1 release small.

### 4.1 Rebrand and Aurora (2.0 history)

- `applicationId` changed to `com.aistudio.novadial.kpxm`; app name, launcher icon and user
  strings rebranded to Nova Dialer.
- A 2.0 "Aurora" visual language (indigo → violet → orchid, glassy surfaces, pill controls)
  replaced the upstream look. The 2.1 redesign supersedes it with the Day / Default / Night
  themes and the measured-contrast accent palette described in `README.md`.
- The legacy preferences file `secure_dialer_prefs` is referenced only by the deferred recording
  implementation; the shipped settings use `dialer_prefs`. This is not a general settings migration.

### 4.2 Focus/protection/announcer work (2.0 history, now deferred)

The 2.0 port added a pure `CallProtectionPolicy` rule engine, `CallProtectionPrefs`,
`CallDecisionStore`, a `NovaDialCallScreeningService`, `AnnouncerScript` and
`CallerAnnouncerManager`, plus the `FocusAndProtectionSettings` screen. Those files still
exist and have unit coverage for the pure logic, but the screening service is no longer
registered and the settings screen is not reachable in 2.1. Their presence is **not** evidence
that Focus Mode or caller announcements work in the release candidate.

### 4.3 Recording work (2.0 history, now deferred)

The upstream recording pipeline was adapted for Nova Dialer (voice-communication source,
encrypted-at-rest recordings, disclosure notice). It is retained so existing recording files and
database rows from earlier builds are not deleted, but the simplified 2.1 release neither creates
nor surfaces them: no microphone permission is declared, the reachable UI never enables the
record button, and the Recents playback/management actions are gated by
`ReleaseScope.CALL_RECORDING_ENABLED` (`false`).

### 4.4 Deferred SOS and analytics (2.0 history)

The original SOS and analytics sources were previously kept under
`reference/novadial-original/`. That archive is no longer shipped. SOS was removed completely
in the 2.1 cleanup. The analytics idea was not merged; the Recents dashboard remains the
supported summary.

### 4.5 Spam handling (2.0 history)

The genuine local parts of the old spam feature (CSV import, manual entries, offline matching)
were carried over with the upstream spam table. The invented seed data, community feed and
`RealtimeSpamDetector` were deliberately not ported. In 2.1 the screening service and spam
management UI are deferred, and the spam-labelling action is hidden.

---

## 5. Verification status

Automated verification is recorded in `README.md` and `docs/PLAY_RELEASE_CHECKLIST.md`.
The commands are:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat lintRelease
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
git diff --check
```

Results from the 22 October 2026 verification: **323 JVM unit tests** (0 failures, 0 errors,
0 skipped), **8 instrumentation tests** on an API 37 emulator (0 failures), and
`lintDebug`/`lintRelease` **0 errors / 36 warnings**; successful `assembleRelease` and
`bundleRelease`. The release APK is deliberately a single universal APK
(`app-release.apk`, 11,239,878 bytes, SHA-256
`75CE428767D72488E26B163E5F11716CFC46AE92AC63464E8E0F2FC12E82CD22`) so its output does not
depend on which Gradle task is invoked; Play's ABI splitting comes from the AAB
(`app-release.aab`, 10,598,691 bytes, SHA-256
`27EC416A1D5C1536CAB03585CABE9731E229CF372228D181D4062628B40405DE`). Its mapping is
`mapping.txt` (51,347,886 bytes, SHA-256
`D0589AFB85131709922FE36EDAECF630C3350747AECA13BD495548A371A24098`). `apksigner` verified the
APK with APK Signature Scheme v2; `jarsigner -verify -verbose -certs` returned exit 0 and reported
`jar verified` for the AAB, with self-signed/no-timestamp and JarFile/JarInputStream consistency
warnings; `bundletool 1.18.3 validate` returned exit 0. The local AAB has not been uploaded. This
porting plan does not claim a physical-device pass: Telecom, full-screen intents, multi-call
transitions, OEM background
restrictions and upgrade/backup behaviour still need the matrix in
[`DEVICE_TEST_MATRIX.md`](DEVICE_TEST_MATRIX.md).

---

## 6. License

Secure Dialer is licensed **GPL-3.0-or-later**. Nova Dialer 2.0 and 2.1 are derivative works
and remain **GPL-3.0-or-later**. Existing copyright headers are preserved; new files carry the
same GPL-3.0-or-later header. See [`LICENSE`](../LICENSE) and [`NOTICE.md`](../NOTICE.md).
