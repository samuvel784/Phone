# Nova Dialer Google Play release checklist

This is the remaining work that cannot be completed from the source tree alone. The app code
now avoids the main authentication, permission, recording and backup hazards, but a Play release
still needs the declarations and assets below.

## 1. Build and signing

- [ ] Provide `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` (or place the
      upload keystore where `app/build.gradle.kts` expects it). The release build deliberately
      fails fast without them.
- [ ] Build a signed AAB with `bundleRelease`.
- [ ] Confirm the AAB's application ID is the one registered in Play Console
      (`com.aistudio.novadial.kpxm`) or update it before the first upload.
- [ ] Keep the upload key backed up. Losing it requires Play App Signing key reset.

**Local candidate (21 October 2026, not uploaded):** `bundleRelease` produced a signed
`app/build/outputs/bundle/release/app-release.aab` (10,570,437 bytes, SHA-256
`676F996047A82F80C9E7E059ED1D8FEAF7C96C423FAE4E27BBA017D4A01F28F8`). `jarsigner -verify`
reported `jar verified`; `bundletool validate` returned 0. The packaged identity is
`com.aistudio.novadial.kpxm`, versionCode 4, versionName 2.1.0, minSdk 24, targetSdk 36.
The upload signer certificate SHA-256 is
`af42b727ba4a94cfc321c5b7be2f008266d93f923b75990f0e9e70176d5a4b40`. The unchecked Play-side
items below still require access to Play Console.

## 2. Policy declarations

- [ ] **Call Log permission.** Declare the default-phone-handler use case in Play Console. The app
      requests Call Log access only after the user grants the Phone role.
- [ ] **SMS.** Nova Dialer holds no SMS permission and does not send SMS itself. Quick-reply
      declines either use Telecom's decline-with-message or hand a pre-filled draft to the user's
      SMS app. The listing must not claim silent SMS sending.
- [ ] **Full-screen intents.** Confirm the FSI permission is granted for the default dialer and
      test the fallback notification path on Android 14/15/16.
- [ ] **Contacts.** Declare why contacts are read and written (dialer, contact editing).
- [ ] **Target audience / content rating.** Complete the questionnaires. The app is not directed
      to children.

## 3. Privacy policy and Data safety

- [ ] Publish `PRIVACY.md` at a public HTTPS URL and put that URL in Play Console.
- [ ] Confirm the developer name and privacy email in `PRIVACY.md` are current, and decide whether
      a postal address must be published for your jurisdiction (the policy currently states that
      none is published).
- [ ] Complete the Data safety form using the draft below.
- [ ] Check the store listing for claims that are no longer true (no simulated calls, all data
      never leaves the device, etc.).

### Data safety draft

| Data type | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|
| Phone numbers / contacts | No (stored on device) | Only when the user exports/shares | Dialer, contact editing | Contacts permission |
| Call history | No (stored on device) | Only when the user exports a backup | Dialer, recents | Call Log permission |
| SMS content | No (handled by carrier/SMS app; decline replies hand a pre-filled draft to the user's own messaging app) | Carrier only when the user sends | Quick replies | None — the app holds no SMS permission |
| App settings/notes | No (stored on device) | Only in encrypted backups | App functionality | None |

The app has no `INTERNET` permission, no microphone, location or overlay permissions, and no
advertising, analytics or crash-reporting SDK.

## 4. Store listing and assets

Draft copy, permission explanations, missing contact fields and the full asset inventory are in
[`PLAY_STORE_LISTING_DRAFT.md`](PLAY_STORE_LISTING_DRAFT.md). The declaration worksheet is
[`PLAY_CONSOLE_DECLARATIONS.md`](PLAY_CONSOLE_DECLARATIONS.md).

- [x] 512×512 high-resolution icon (`store/play_store_icon_512.png`, verified 512×512).
- [ ] 1024×500 feature graphic.
- [ ] At least 2 phone screenshots (Play normally expects 4–8); use real app screens with
      synthetic contacts/calls and redact personal data.
- [ ] Short description and full description that match actual behavior.
- [ ] Support email and website (currently missing; do not invent).
- [ ] Content rating and target audience answers.
- [ ] App access instructions if reviewers need demo data (no fake-call flow exists).

## 5. Device verification (required before calling it publishable)

Execute the full matrix in [`DEVICE_TEST_MATRIX.md`](DEVICE_TEST_MATRIX.md) on a physical
device, not only an emulator. The matrix covers:

- Fresh install, role request, permission flow and deferred-feature absence checks.
- Incoming calls on the lock screen and while the app is backgrounded/stopped.
- Full-screen-intent permission granted and denied on Android 14+.
- Answer, decline, reject-with-SMS, missed-call notification and call termination.
- Multi-call: active + waiting call (answer/decline/swap/merge), and the notification
  transition from one ended call to the next live call.
- Bluetooth, wired, speaker, mute and audio-route changes.
- DND/silent/vibrate behaviour.
- Reboot, background restrictions and OEM battery behaviour.
- Upgrade from the intended previous build without losing data.
- Encrypted database opening, key-unavailable recovery, and backup export/import.
- Privacy/store checks against the installed artifact.

The matrix now contains per-test purpose, prerequisites, steps, expected result, actual-result
field, status, evidence guidance, recovery instructions, and separate accessibility/RTL/large-font
cards. **All device results remain NOT RUN.**

## 6. Release-gate audit follow-up (21 October 2026)

The code-side blockers from the 21 October 2026 release-gate audit have been resolved; the
remaining blockers are Play-side declarations, assets and physical-device validation.

- **Automated verification.** `testDebugUnitTest`: **286 tests, 0 failures, 0 errors, 0 skipped**.
  `lintDebug` and `lintRelease`: **0 errors, 37 warnings**. `assembleDebug`, `assembleRelease`
  and `bundleRelease` completed successfully. The final AAB was verified with
  `jarsigner -verify -verbose -certs` (`jar verified`) and `bundletool validate` (exit 0), not
  with an APK-only checker. `git diff --check` exited 0 with only line-ending notices.
- **Privacy strings.** The in-app privacy dialog copy (`privacy_section_1_desc`, `privacy_section_2_desc`,
  `privacy_section_3_desc`) was rewritten in all 10 locales (`values` + ar, de, es, fr, hi, ja, pl,
  pt, zh) so it no longer mentions call recording, the microphone or SOS location. The 42 orphaned
  `sos_*` strings, the Dynamic Island strings, the overlay-permission warning and the orphaned
  category headers were removed from every locale (key order parity is covered by
  `LocalizationIntegrityTest`, and `DeprecatedNavigationGuardTest` guards the release scope).
- **Obsolete overlay / Dynamic Island UI.** The Dynamic Island card in Appearance settings, the
  `CallSystemPermissionsCard` overlay branch, the startup job in `DialerApplication`, the
  overlay-permission helpers and the `DynamicIslandOverlayManager`/`DynamicIslandPill` classes were
  removed. No reachable UI requests `SYSTEM_ALERT_WINDOW` anymore (the permission was already
  undeclared).
- **SOS crash risk.** `MainActivity` no longer re-arms the shake-to-SOS watch on start (this would
  have crashed on upgrades where the service is no longer declared), the reachable "Set Emergency
  SOS Contacts" card in Contacts is gone, and the five deferred `EmergencySos*` classes were
  deleted.
- **Duplicate Finder dead navigation.** The entry card that navigated to removed settings tab 11
  was removed; the deferred `DuplicateFinder` logic keeps its unit tests.
- **Lint is now clean of errors.** `lintDebug` and `lintRelease` report **0 errors, 37 warnings**
  (deferred-feature code such as `ReminderScheduler`, Gradle/dependency version housekeeping and
  style suggestions). The 5 previous errors (3× `MissingPermission` USE_BIOMETRIC in the deleted
  `VaultBiometricAuthHelper`, 2× `ForegroundServiceType` for the deleted `EmergencySosService`) are
  gone with the deferred code; the biometric app lock that ships in this build is enforced through
  the device screen-lock credential and needs no extra permission.
- **Deferred-feature gates.** The 2.1 audit found a reachable callback-reminder card whose alarm
  receiver was not registered, and an auto-record preference that could still start a recording
  after an upgrade or backup restore. Both paths are now gated by `ReleaseScope`; the
  duplicate-contact startup scan and the spam-labelling action are gated too. The invalid
  `android:showOnLockScreen` attribute was removed from the incoming-call activity.
- **Scope regression guard.** `DeprecatedNavigationGuardTest` now also asserts that the deferred
  feature switches are off and that the manifest does not register the reminder receiver,
  screening service, widget provider or recording-disclosure activity.
- **Permission cleanup (21 October 2026).** The unused `FOREGROUND_SERVICE` declaration was
  removed; no app-managed foreground service exists, and `MyInCallService` remains Telecom-bound.
  `READ_PHONE_STATE` is now requested only when the device reports two or more modems, and the
  callable-SIM list refreshes after the grant. A one-time upgrade prompt covers installs that
  already granted contacts/call log. Denial remains fail-soft: no SIM chooser and the
  platform/default SIM is used. `DeprecatedNavigationGuardTest` also rejects a reintroduced
  `FOREGROUND_SERVICE` declaration.

## 7. Residual blockers

Until the boxes above are complete, the source tree is **not** a publishable release. The signed
AAB exists only as a local candidate; it has not been uploaded. The public privacy URL, Play
Console declarations, physical-device matrix and remaining store assets (feature graphic,
screenshots, descriptions) still have to be completed outside this repository.

Additional housekeeping findings (21 October 2026):

- [ ] `PRIVACY.md` now distinguishes recording metadata in the encrypted database from legacy
      recording files retained in app-private storage. Regenerate the Pages site after this edit;
      it is not published yet.
- [ ] `https://samuvel784.github.io/NovaDialer/` still returns **404**; the Pages API returns 404
      and the remote has only `main`. Do not use that URL in Play Console. See
      [`PRIVACY_HOSTING.md`](PRIVACY_HOSTING.md).
- [ ] `Answer.md` is a tracked **0-byte** file with no project references (last changed
      19 October 2026). It has no build/test purpose. Remove it deliberately in a housekeeping
      commit, or document a real purpose; it has intentionally not been removed automatically.
- [ ] The intended previous build for the G1 upgrade test is not identified in the repository.
      Supply the old version/artifact or record G1 as blocked.
- [x] `READ_PHONE_STATE` is now requested conditionally on multi-SIM hardware; the SIM mapping
      still fails soft when it is denied or unavailable. Verify the prompt, grant, denial, and
      SIM-routing paths on a physical dual-SIM device. `FOREGROUND_SERVICE` is no longer declared;
      no app-managed foreground service exists.

## 8. Lint warning disposition (21 October 2026)

`lintDebug` and `lintRelease` reported **0 errors and 37 warnings** after the permission cleanup.
No warnings were suppressed. No source/resource changes were made solely to reduce the count. The
count is unchanged because the remaining warning is the deferred exact-alarm code plus version
and localization housekeeping; the permission edits did not introduce a new lint issue.

| Group | Count | Severity / relevance | Disposition |
|---|---:|---|---|
| `MissingPermission` — `ReminderScheduler.kt:55` exact-alarm call | 1 | Deferred code; reminder receiver is not registered and the feature is gated off | Not fixed. Adding a suppression would hide a real permission signal; if the feature returns, add `SCHEDULE_EXACT_ALARM`/power-exemption handling or redesign the scheduling. |
| `PluralsCandidate` — `%d` followed by words in 9 strings | 9 | Localization correctness; not a crash or release blocker | Not fixed. Proper plural resources require a coordinated change across all 10 locales and UI call sites; track for a dedicated localization pass rather than a risky release-week edit. |
| Version/toolchain: `GradleDependency` (15), `NewerVersionAvailable` (9), `AndroidGradlePluginVersion` (1), `OldTargetApi` (1) | 26 | Maintenance/upgrade suggestions; regression risk if upgraded without full testing | Not fixed. Upgrades are outside this documentation handoff and require a separate dependency/test cycle. |
| `UnusedResources` — `Theme_MyApplication_Translucent` | 1 | Harmless unused style; no runtime impact | Not fixed. Removing a resource is not necessary for this release and would require a resource rebuild; leave it for planned cleanup or confirm no future use. |
| **Total** | **37** | | **0 errors; no suppressions; 1 deferred exact-alarm warning retained.** |
