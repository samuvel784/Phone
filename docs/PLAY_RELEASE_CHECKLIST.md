# Nova Dialer Google Play release checklist

This is the remaining work that cannot be completed from the source tree alone. The app code
now avoids the main authentication, permission, recording and backup hazards, but a Play release
still needs the declarations and assets below.

## 1. Build and signing

- [ ] Create a local, untracked `key.properties` with `storeFile`, `storePassword`, `keyAlias`
      and `keyPassword`. Exact release packaging tasks depend on `validateReleaseSigning` and
      deliberately fail before packaging without valid credentials.
- [ ] Build a signed AAB with `bundleRelease`; build the universal direct-install APK with
      `assembleRelease`.
- [ ] Confirm the AAB's application ID is the one registered in Play Console
      (`com.aistudio.novadial.kpxm`) or update it before the first upload.
- [ ] Keep the upload key backed up. Losing it requires Play App Signing key reset.

**Local candidate (25 October 2026, not uploaded):** `bundleRelease` produced a signed
`app/build/outputs/bundle/release/app-release.aab` (10,626,879 bytes, SHA-256
`E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`). `jarsigner -verify` returned
exit 0 and reported `jar verified`, with self-signed/no-timestamp and JarFile/JarInputStream
consistency warnings; the AAB contains no `INTERNET` permission. `assembleRelease` produced the
universal `app/build/outputs/apk/release/app-release.apk` (11,209,221 bytes, SHA-256
`D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`), verified with
`apksigner` (APK Signature Scheme v2) and `zipalign -c -P 16 -v 4` (16 KB page alignment).
The packaged identity is `com.aistudio.novadial.kpxm`, versionCode 5, versionName 2.1.1, minSdk 24,
targetSdk 36. The upload signer certificate SHA-256 is
`af42b727ba4a94cfc321c5b7be2f008266d93f923b75990f0e9e70176d5a4b40`. The R8 mapping is
51,771,588 bytes, SHA-256 `A0859243F8B760B2748D306344AF4A3AFDB89154A6EBA14328C423BAD361025D`.
The unchecked Play-side items below still require access to Play Console.

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

## 6. Release-gate audit follow-up (23 October 2026)

The code-side blockers from the earlier release-gate audits remain resolved, and the 23 October
audit removed unreachable features, added the shared loading system, and tightened responsive and
background-work behavior. The remaining blockers are Play-side declarations, assets and
physical-device validation.

- **Automated verification (25 October 2026, final source).** `testDebugUnitTest --rerun-tasks
  --no-build-cache`: **422 tests in 62 classes, 0 failures, 0 errors, 0 skipped**.
  `connectedDebugAndroidTest` is **NOT RUN** for this build because no emulator or device was
  attached; the 22 October 2026 run recorded 8 tests on API 37 `emulator-5554` for the previous
  build. `lintDebug` completed with **0 errors and 39 warnings**; `lintRelease` completed with
  **0 errors and 40 warnings**. `assembleDebug`, `assembleRelease` and `bundleRelease` completed
  successfully. The APK (11,209,221 bytes, SHA-256
  `D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`) is a single universal
  signed APK; `apksigner` reports a valid APK Signature Scheme v2 signature and
  `zipalign -c -P 16 -v 4` reports `Verification successful`. The AAB
  (10,626,879 bytes, SHA-256
  `E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`) was checked with
  `jarsigner -verify` (exit 0, `jar verified`, with self-signed/no-timestamp and
  JarFile/JarInputStream consistency warnings); its manifest contains no `INTERNET` permission.
  The signer SHA-256 matches the intended upload certificate, and the packaged manifest reports
  package `com.aistudio.novadial.kpxm`, version `2.1.1`/`5`, minSdk `24`, targetSdk `36`. The mapping is
  51,771,588 bytes with SHA-256
  `A0859243F8B760B2748D306344AF4A3AFDB89154A6EBA14328C423BAD361025D`.
- **Current source audit fixes.** Database recovery now inspects the full SQLCipher file family,
  restores interrupted swaps (including stale-sidecar quarantine), validates plaintext-to-SQLCipher
  migration snapshots by integrity and row count, and fails closed instead of replacing a database.
  The plaintext-to-SQLCipher exporter now opens the target with the same key API used by Room and
  copies the schema/rows; this fixed the raw-key `ATTACH`/code-26 export failure found by the new
  real-file instrumentation test.
  Notification actions are bound to an exact call id with no generic fallback. With app lock
  enabled, a live call shows only a restricted call surface and never authenticates the full app;
  the incoming-call activity no longer dismisses the keyguard. Backup, vCard and text imports have
  hard read limits. `IntentGuard` persists its token synchronously and validation never mints one.
  Release packaging now depends on `validateReleaseSigning` by exact task name, and lint fails the
  release on errors, missing translations or extra translations.
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
- **Source-audit cleanup (23 October 2026).** After tracing every manifest, Compose and settings
  route, 31 unreachable main-source files and 8 dead test files were removed along with the
  caller announcer, call-screening service, callback reminders, duplicate finder, spam CSV settings,
  flash alerts, analytics, focus/VIP, block-list management, call-log exporters, recording UI/start
  paths and the home-screen widget. Data models, DAOs, backup fields and recording/recovery paths
  needed for legacy compatibility were retained. 206 orphaned strings were removed from all 10
  locales; `LocalizationIntegrityTest` still enforces identical key order and format specifiers.
- **Lint is clean of errors.** `lintDebug` reports **0 errors and 27 warnings**; `lintRelease`
  reports **0 errors and 28 warnings**. The remaining warnings are Gradle/dependency version
  housekeeping, target/compile-SDK notices and two plural-candidate suggestions. No lint rule was
  disabled to reach this count.
- **Deferred-feature gates.** `ReleaseScope` keeps its compatibility flags, but the unreachable
  reminder, duplicate-finder, announcer, screening and recording UI entry points are gone. The
  invalid `android:showOnLockScreen` attribute remains removed from the incoming-call activity.
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

## 8. Lint warning disposition (25 October 2026)

`lintDebug` reported **0 errors and 39 warnings**; `lintRelease` reported **0 errors and 40
warnings**. No lint rule was disabled, and the source audit removed orphaned strings rather
than suppressing `UnusedResources`.

| Group | Count | Severity / relevance | Disposition |
|---|---:|---|---|
| `PluralsCandidate` — `%d` followed by words in `calls_count` and `call_notes_count_badge` | 2 | Localization correctness; not a crash or release blocker | Not fixed. Proper plural resources require a coordinated change across all 10 locales and UI call sites; track for a dedicated localization pass rather than a risky release-week edit. |
| Version/toolchain: `GradleDependency` (14), `NewerVersionAvailable` (9), `AndroidGradlePluginVersion` (1), `OldTargetApi` (1) | 25 | Maintenance/upgrade suggestions; regression risk if upgraded without full testing | Not fixed. Upgrades are outside this audit and require a separate dependency/test cycle. |
| `UnusedResources` | 11 debug / 12 release | Harmless; includes `record` (referenced by `CallOnlyModeTest`) and release-only resource analysis | Not removed. Removing `record` would break the restricted-call test; keep until the test is refactored. |
| `UseKtx` | 1 | Optional KTX convenience suggestion | Not fixed; behaviour-neutral cleanup for a later pass. |
| **Total** | **39 debug / 40 release** | | **0 errors; no lint rules disabled.** |

## 9. Responsive and Play-safety pass (25 October 2026)

This pass fixed existing behaviour only; it added no product features.

### Responsive behaviour

- Shared breakpoints and helpers live in `app/src/main/java/com/example/ui/Responsive.kt`
  (`Compact`/`Medium`/`Expanded`, compact-height detection, adaptive padding, centred content
  frame, responsive column count).
- Main content, settings, the in-call surface and the dialpad are centred and width-capped instead
  of stretching across tablets/Chromebooks. Favourites flow into as many columns as fit; the
  bottom navigation stays a readable 720 dp strip.
- The dialpad, in-call controls, lock screen, SIM picker and note dialog grow with the user's font
  scale instead of clipping; the dialpad is scrollable in landscape and applies IME insets.
- Display-cutout `shortEdges` mode is declared, both activities keep fold/resize/font-density
  changes, and the manifest stays `resizeableActivity=true` with no orientation lock.
- `ResponsiveLayoutTest` (8 tests) locks the breakpoints; `ResponsiveUiMatrixTest` (6 tests)
  renders at 320×480, 640×360, 1280×800 and 800×1280, including a 2.0 font scale, and checks every
  dialpad key, the call action, all five bottom tabs and all core in-call controls are reachable.

### Play-safety hardening

- Prominent disclosure and call-log disclosure are localized strings and explicitly cover editing
  contacts, phone state for multi-SIM routing, notifications, and deleting/clearing call history.
- Notification channel names/descriptions are localized.
- Android 11+ package visibility is narrowly scoped to the two Xiaomi settings packages instead of
  requesting broad visibility; `resizeableActivity` is explicit.
- `DialerRepository.deleteCallLog` now deletes a provider row only when `_ID`, `NUMBER` and `DATE`
  match the local row, so a restored local id cannot delete an unrelated system call.
  `CallLogDeleteIdentityTest` proves both the matching and colliding-id cases.
- The dead system-call-log writer was replaced with a local-only insert, so non-Telecom paths cannot
  duplicate or overwrite provider history.
- Icon-only controls have content descriptions (overflow menu, remove-field buttons, dismiss, speed
  dial). The dialpad grabber keeps its small visual pill but now has a 48 dp touch target.
- `ManifestPolicyTest` (8 tests) fails on `INTERNET`, an unexpected permission, a new exported
  component, backup enabled, a missing telephony-optional feature, an orientation lock, unscoped
  package visibility, or removal of the standard dialer intent actions.
- Version bumped to `versionCode 5` / `versionName 2.1.1`.

### Verification results (responsive-pass snapshot; final numbers in sections 6 and 10)

- `:app:testDebugUnitTest`: **56 suites, 387 tests, 0 failures, 0 errors, 0 skipped**.
- `:app:lintRelease`: **0 errors, 42 warnings** (dependency/toolchain housekeeping, unused
  resources, two plural candidates, `UseKtx`, `ApplySharedPref`, one `OldTargetApi`). No lint rule
  was disabled; the previous `QueryPermissionsNeeded` warning disappeared because package
  visibility is now scoped.
- `:app:assembleRelease` and `:app:bundleRelease`: successful.
- `zipalign -c -P 16 -v 4 app-release.apk`: **Verification successful**.
- Every packaged `.so` (SQLCipher and `androidx.graphics.path`, all four ABIs) was parsed from the
  APK: every `PT_LOAD` segment has `p_align >= 16384` and matching file/virtual offsets.
- `apksigner verify`: v2 scheme true, signer SHA-256
  `af42b727ba4a94cfc321c5b7be2f008266d93f923b75990f0e9e70176d5a4b40`.
- `jarsigner -verify`: exit 0, `jar verified`.
- Merged release manifest: no `INTERNET`, no `RECORD_AUDIO`, no `debuggable`, no `testOnly`,
  `allowBackup=false`, `extractNativeLibs=false`; exported components are `MainActivity`,
  the `BIND_INCALL_SERVICE`-protected `MyInCallService`, and the androidx `DUMP`-protected
  `ProfileInstallReceiver`. Room's service, `InitializationProvider` and the FileProvider are
  `exported=false`.
- **Device tests remain NOT RUN:** `adb devices` returned no attached device. Telecom, lock-screen,
  notification, audio-route, multi-SIM, keystore/SQLCipher and visual responsive checks still need
  the physical-device matrix in [`DEVICE_TEST_MATRIX.md`](DEVICE_TEST_MATRIX.md).

## 10. Contact identity, reset and recovery pass (25 October 2026)

Source defects fixed in this pass (no new product features, no new runtime dependencies):

- **Contact identity authority.** Number identity is now the full, region-aware E.164 subscriber
  identity (SIM/network/locale region, exact digits only when the region cannot resolve the
  number). There is no 7- or 10-digit suffix matching anywhere. Cache entries are keyed by stable
  provider `CONTACT_ID`/raw-contact/local ids; the same number on two ids is ambiguous and
  resolves to no name. Provider lookups return a name only when every matching row shares one
  contact id; permission denial returns no name. Recents grouping uses the normalized subscriber
  identity alone, and rows derive name/avatar/Edit-or-Add from the **current** saved contact, so a
  deleted contact no longer keeps a stale name. CNAP/conference names use the same normalization.
- **Fresh-install reset.** `AppResetManager` closes Room, refuses to run during a live call, moves
  the active database, sidecars, interrupted-swap/plaintext/unreadable artifacts, legacy recovery
  archives and stale `reset_pending` staging into one directory, verifies KeyStore alias removal
  and key-metadata erasure, deletes the staged files and the staging shell, clears every
  SharedPreferences file in credential and device-protected storage, and deletes private
  `files`/`cache` content. Failures are returned to the caller and displayed on the recovery
  screen. Files exported through the document picker live outside the private directories and are
  preserved; the reset never creates a private archive the user could not open again.
- **Startup recovery proof.** Failure handling moved to `StartupRecovery.resolveStartupViewModel`
  and is tested by constructing throwing `AndroidViewModel` subclasses through a real
  `androidx.lifecycle.ViewModelProvider`. Only `DatabaseRecoveryRequiredException` and key-related
  `IllegalStateException`s open the recovery screen; unrelated failures are rethrown. The screen
  requires an explicit confirmation before deleting private data and now shows a reset failure
  instead of silently staying put.
- **No reversible protections were removed.** SQLCipher fail-closed inspection, the application ID,
  the upload key/signing configuration, `allowBackup=false`, `FLAG_SECURE`, and the document-picker
  export path are unchanged.

### Final verification

- `:app:testDebugUnitTest --rerun-tasks --no-build-cache`: **62 suites, 422 tests, 0 failures,
  0 errors, 0 skipped**.
- `:app:lintDebug`: **0 errors, 39 warnings**; `:app:lintRelease`: **0 errors, 40 warnings**.
- `assembleDebug`, `assembleRelease`, `bundleRelease`: successful. AAB 10,626,879 bytes / SHA-256
  `E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`; APK 11,209,221 bytes /
  SHA-256 `D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`; mapping 51,771,588
  bytes / SHA-256 `A0859243F8B760B2748D306344AF4A3AFDB89154A6EBA14328C423BAD361025D`.
- `apksigner` v2 verification, `zipalign -c -P 16` and `jarsigner -verify` all passed; the packaged
  manifest reports 2.1.1/5, minSdk 24, targetSdk 36, no `INTERNET`, `allowBackup=false`,
  `extractNativeLibs=false`.
- **NOT RUN:** instrumentation, real SQLCipher reopen, Telecom/call-waiting, contact-provider
  behaviour on a device, reset-on-device, notification/lock-screen, audio routes and visual
  checks. `adb devices` reported no attached device. Play Console declarations, Data safety,
  content rating, store assets and the public privacy URL remain external blockers.

## 11. Header menu and caller-identity pass (25 October 2026)

Source changes in this pass (no new runtime dependencies, no manifest or signing change):

- **Tab header policy.** The search field stays on Home, Recents and Favourites, but the settings
  menu is now shown only on Home. `headerMenuVisibleForTab` in `MainScreen.kt` owns the policy.
  `HeaderSearchBar` reserves a fixed 48 dp leading slot when the menu is hidden, so the search icon
  and text keep the same x-position instead of jumping between tabs. `HeaderTabMenuPolicyTest` and
  `HeaderSearchBarTest` pin visibility, click, absence and alignment (4 tests).
- **Caller identity.** In-call, locked-screen and notification names are resolved from the saved
  contact record only; a carrier `CallerDisplayName`/CNAP value is no longer substituted for a
  caller name. Carrier CNAP is still persisted for recents labelling, but it cannot impersonate a
  caller during a call. `CallManager.onContactsChanged()` re-resolves the live caller after an
  address-book sync, and the asynchronous resolver pins its result to the live `Call` reference,
  call id and number so a stale lookup cannot overwrite a newer caller.
- **Transactional reset.** `AppResetManager` records each stage and the staged-file manifest in a
  device-protected journal. A failed key erasure rolls the database back only while the key is
  still usable; once the key is gone the reset fails closed with the evidence preserved instead of
  restoring an unreadable database. `DatabaseFileGuard` now treats a non-empty `reset_pending`
  directory as recoverable material, never as a fresh install.
- **Unchanged protections.** Application ID, version 2.1.1/5, the upload keystore, SQLCipher
  fail-closed inspection, `allowBackup=false`, and document-picker exports (which live outside
  app-private storage) are unchanged.

### Final verification

- `:app:testDebugUnitTest --rerun-tasks --no-build-cache`: **62 suites, 422 tests, 0 failures,
  0 errors, 0 skipped**.
- `:app:lintDebug`: **0 errors, 39 warnings**; `:app:lintRelease`: **0 errors, 40 warnings**.
- `assembleDebug`, `assembleRelease`, `bundleRelease`: successful. AAB 10,626,879 bytes / SHA-256
  `E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`; APK 11,209,221 bytes /
  SHA-256 `D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`; mapping 51,771,588
  bytes / SHA-256 `A0859243F8B760B2748D306344AF4A3AFDB89154A6EBA14328C423BAD361025D`.
- `apksigner` v2 verification, `zipalign -c -P 16` and `jarsigner -verify` all passed; the packaged
  manifest reports 2.1.1/5, minSdk 24, targetSdk 36, no `INTERNET`, `allowBackup=false`,
  `extractNativeLibs=false`.
- **NOT RUN:** instrumentation, real SQLCipher reopen, Telecom/call-waiting, contact-provider
  behaviour on a device, reset-on-device, notification/lock-screen, audio routes and visual
  checks. `adb devices` reported no attached device. Play Console declarations, Data safety,
  content rating, store assets and the public privacy URL remain external blockers.

> **Post-verification layout change:** the bottom navigation bar was moved from the tab content
> `Column` into `Scaffold(bottomBar = ...)`, and the content now receives the bar height through
> `PaddingValues`. This landed after the test/build record above, so the JVM suite, release
> build, hashes and landscape/portrait device checks must be repeated before upload.
