# Nova Dialer 2.1 — Porting Plan, Release Scope & Change Log

**Current status (23 October 2026):** release candidate for the simplified v1 build,
`2.1.1` (versionCode 5). The automated build, unit-test, lint, manifest/signing and packaging
results are recorded in §5. No physical-device test or instrumentation run has been performed
for this build in this environment.

This document started as the porting log for Nova Dialer 2.0. It now also records what the
2.1 release actually ships and which older, unreachable features were removed in the 23 October
source audit, together with the data/backup compatibility retained for them. Historical 2.0
material is kept in §5 so the derivation and the decisions behind it remain auditable.

- **Foundation / upstream:** Secure Dialer (`com.example`, GPL-3.0-or-later)
- **Target:** Nova Dialer (`com.aistudio.novadial.kpxm`), GPL-3.0-or-later
- **Release:** 2.1.1 (versionCode 5), minSdk 24, targetSdk 36
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
| 4 | Dead `GestureControlManager` / partially wired `FlashAlertManager` | **Removed from the release path** | The in-call gesture helper used by `ActiveCall` is the upstream implementation. The standalone flashlight helper was removed in the 23 October source audit. Neither is a deferred feature with a hidden trigger. |
| 5 | `verifyPin` / `verifyPattern` always returned `false` | **Replaced** | App lock is enforced with the device screen-lock credential (`KeyguardManager`). The biometric-only pre-prompt helper was removed; this build does not declare `USE_BIOMETRIC`. |
| 6 | Fake "Google Sync" | **Removed** | Contacts come from Android's Contacts Provider. |
| 7 | Invented spam database with made-up numbers | **Removed** | No seed data or fake threat feed. The local spam table remains only for legacy backup compatibility; spam labelling is hidden and no screening service is registered. |
| 8 | Destructive / data-losing Room migrations | **Fixed** | SQLCipher Room database, non-destructive migrations, fail-closed key handling. |
| 9 | Unused Firebase / OkHttp / Retrofit dependencies | **Fixed** | No Firebase or Retrofit dependency is packaged. Coil contributes its own OkHttp/Okio transport as a transitive library; the app has no `INTERNET` permission and defines no remote endpoint. |
| 10 | Call blocking / Focus mode / protection rules did nothing | **Blocking: implemented; Focus/protection: removed** | Android system blocking plus a local blocked-number list are reachable. Focus Mode, call-protection policy/preferences and the screening service were removed in the 23 October source audit; no screening component is registered. |
| 11 | Caller announcer was not present in a working form | **Removed** | `CallerAnnouncerManager`, `AnnouncerScript` and the unreachable announcer settings path were removed in the 23 October source audit. |
| 12 | Emergency SOS (Nova Dialer-only) | **Removed** | The five `EmergencySos*` classes, settings card and triggers were deleted in the 2.1 scope cleanup. |
| 13 | Analytics / call-stats dashboard | **Dashboard kept; settings removed** | The upstream `CallLogSummaryDashboard` remains. The separate analytics settings screen was removed in the 23 October source audit. |

---

## 2. Release identity and configuration

| Item | Value |
|------|-------|
| `applicationId` | `com.aistudio.novadial.kpxm` (intentionally unchanged for this candidate; review before the first Play upload) |
| App name (`app_name`) | `Nova Dialer` |
| `rootProject.name` | `Nova Dialer` |
| `versionName` / `versionCode` | `2.1.1` / `5` |
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

### 3.2 Compatibility code and data retained after the source audit

The 23 October 2026 audit removed reachable-entry-point code that could never run. These
compatibility surfaces remain deliberately so older installs, backups and recording files keep
working; none of them exposes a shipped screen or starts a deferred feature.

| Area | Retained compatibility | Current state |
|---------|-------------------------|------------------|
| Call recording | `CallAudioRecorder`, `RecordingCrypto`, recording entity/DAO and backup/recovery fields | No `RECORD_AUDIO` permission and no recording UI/start path. Existing recording files and rows are untouched; automatic recording cannot start even if an older preference is restored. |
| Callback reminders | `call_reminders` table and backup merge fields; `ReleaseScope.CALLBACK_REMINDERS_ENABLED = false` | Scheduler, alarm receiver, dialog and settings screen were removed. No alarm can be scheduled or delivered. |
| Focus/screening | Legacy `CallDecisionStore` state and `ReleaseScope` flags | Policy, preference, screening-service and settings code was removed; no screening service is declared or registered. |
| Caller announcer | None | `CallerAnnouncerManager`, `AnnouncerScript` and its settings entry point were removed. |
| Offline spam | `spam_numbers` table and backup/restore handling | Spam settings, labelling action and screening hook were removed; existing rows survive backup/restore but there is no management flow. |
| Duplicate-contact finder | `ReleaseScope.DUPLICATE_FINDER_ENABLED = false` | Scanner, settings screen and startup scan were removed. |
| Home-screen widget | Legacy `com.aistudio.novadial.widget.*` action strings | Provider, widget activity, layouts and drawables were removed; no component is declared. |
| Analytics | None | Settings screen was removed; the supported Recents dashboard remains. |
| Biometric-only recording vault / app-lock pre-prompt | None | Removed. App lock uses the device screen-lock credential and declares no biometric permission. |

### 3.3 Removed features / code

- **Source audit (23 October 2026):** 31 unreachable main-source files and 8 dead test files,
  including recording UI/start and notification paths, caller announcer, call screening,
  callback reminders, duplicate finder, spam CSV settings, flash alerts, analytics, focus/VIP,
  block-list management, call-log exporters and the home-screen widget. 206 orphaned string
  resources were removed from all 10 locales.
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

### 4.2 Focus/protection/announcer work (2.0 history, removed in the source audit)

The 2.0 port added a pure `CallProtectionPolicy` rule engine, `CallProtectionPrefs`,
`CallDecisionStore`, a `NovaDialCallScreeningService`, `AnnouncerScript` and
`CallerAnnouncerManager`, plus the `FocusAndProtectionSettings` screen. In the 23 October 2026
source audit the policy, preferences, screening service, announcer and settings screen were
removed because no shipped route could reach them. `CallDecisionStore` remains only as legacy
call-decision state. Nothing in the release candidate implements Focus Mode, call screening or
caller announcements.

### 4.3 Recording work (2.0 history, compatibility only)

The upstream recording pipeline was adapted for Nova Dialer (voice-communication source,
encrypted-at-rest recordings, disclosure notice). `CallAudioRecorder` and `RecordingCrypto` are
retained so existing recording files and database rows from earlier builds are not deleted or
made unreadable, but the simplified 2.1 release neither creates nor surfaces them: no microphone
permission is declared, the recording UI/start/notification paths were removed in the 23 October
source audit, and recording backup fields remain for compatibility. The
`ReleaseScope.CALL_RECORDING_ENABLED` flag stays `false`.

### 4.4 Deferred SOS and analytics (2.0 history)

The original SOS and analytics sources were previously kept under
`reference/novadial-original/`. That archive is no longer shipped. SOS was removed completely
in the 2.1 cleanup. The analytics idea was not merged; the Recents dashboard remains the
supported summary.

### 4.5 Spam handling (2.0 history)

The genuine local parts of the old spam feature (CSV import, manual entries, offline matching)
were carried over with the upstream spam table. The invented seed data, community feed and
`RealtimeSpamDetector` were deliberately not ported. In the 23 October 2026 source audit the
unreachable spam settings, labelling action and screening hook were removed; the `spam_numbers`
table and its backup/restore handling remain so older data is not lost.

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

Results from the 25 October 2026 verification: **417 JVM unit tests in 60 classes** (0 failures,
0 errors, 0 skipped); instrumentation was **NOT RUN** because no emulator or device was attached.
`lintDebug` completed with **0 errors / 39 warnings** and `lintRelease` with **0 errors / 40
warnings**; `assembleDebug`, `assembleRelease` and `bundleRelease` completed successfully (release
packaging validates signing as a dependency). The release APK is deliberately a single universal APK
(`app-release.apk`, 11,209,221 bytes, SHA-256
`1237FBCE4BD22845891959229C49F0F6ECB2FB7ECA814315822F3BAF31FD42AD`) so its output does not
depend on which Gradle task is invoked; Play's ABI splitting comes from the AAB
(`app-release.aab`, 10,621,828 bytes, SHA-256
`9679E2E90700033585030AA302DD3AD4164582925537FDF916C16BE8C34F9E33`). Its mapping is
`mapping.txt` (51,738,664 bytes, SHA-256
`3A8E3E3457AB47B027620E17EC450DB8C977AE17E59B1194F3A3CA1126E76D67`). `apksigner` verified the
APK with APK Signature Scheme v2; `jarsigner -verify` returned exit 0 and reported
`jar verified` for the AAB, with self-signed/no-timestamp and JarFile/JarInputStream consistency
warnings; the AAB and merged release manifests contain no `INTERNET` permission. The local AAB has
not been uploaded. This porting plan does not claim a physical-device pass: Telecom, full-screen
intents, multi-call transitions, OEM background restrictions and upgrade/backup behaviour still
need the matrix in [`DEVICE_TEST_MATRIX.md`](DEVICE_TEST_MATRIX.md).

---

## 6. License

Secure Dialer is licensed **GPL-3.0-or-later**. Nova Dialer 2.0 and 2.1 are derivative works
and remain **GPL-3.0-or-later**. Existing copyright headers are preserved; new files carry the
same GPL-3.0-or-later header. See [`LICENSE`](../LICENSE) and [`NOTICE.md`](../NOTICE.md).
