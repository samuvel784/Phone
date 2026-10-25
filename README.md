# Nova Dialer

A real Android default dialer. It uses your phone's own contacts, call log, SIM cards and
voicemail — no sample data, no cloud, and no internet permission.

Package: `com.aistudio.novadial.kpxm` - minSdk 24 - targetSdk 36 - version 2.1.1 (versionCode 5)

> **Rebuilt, not patched.** Nova Dialer 2.0 uses the mature **Secure Dialer** codebase
> (`com.example`, GPL-3.0-or-later) as its foundation, rebranded to Nova Dialer, with Nova Dialer's
> unique features re-added on top. See **[docs/PORTING_PLAN.md](docs/PORTING_PLAN.md)** for the release
> scope: what ships, what is deferred or removed, and the historical 2.0 porting decisions.

## Build
1. Open the folder in **Android Studio** and let Gradle sync.
2. Build Variants -> `debug` -> Build > Build APK(s). Install it on a real phone.
3. On the phone: Settings > Apps > Default apps > Phone app > Nova Dialer. Grant the
   permissions the app asks for only as you use the related feature (contacts, phone, call log,
   notifications, and phone state only on multi-SIM devices). Call-log access is requested after
   Nova Dialer holds the default Phone
   role; grant it then if you want Recents to show calls.
4. Release build: create a local, untracked `key.properties` containing `storeFile`,
   `storePassword`, `keyAlias`, and `keyPassword`, then run `.\gradlew.bat assembleRelease` for
   the universal signed APK or `.\gradlew.bat bundleRelease` for the Play upload bundle. Exact
   release packaging tasks depend on `validateReleaseSigning`; they fail before packaging when
   the credentials are missing or invalid. They never fall back to the debug key. App Bundle
   ABI splitting is handled by the AAB; the APK path is intentionally a single universal APK so
   the output does not change with the Gradle invocation.
5. Unit tests: `.\gradlew.bat testDebugUnitTest`; lint: `.\gradlew.bat lintDebug` (or
   `lintRelease`). Release builds also check lint, and missing/extra translations are errors.

> The debug build and JVM unit tests are verified locally. Telecom, full-screen intents,
> multi-call transitions and OEM-specific background restrictions still require
> physical-device testing before release.

## Features

### Calling (from the Secure Dialer core)
- One `CallManager` shared across the InCallService, notifications and the call screen, so the
  call screen always reflects the real call state.
- Mute / speaker / hold / answer reflect Android's confirmed state; the app does not change the
  phone's audio settings by itself.
- Call timer from Android's own connect time; correct call direction on every API level.
- Real SIM names, real default SIM, exact subscription match when choosing a SIM.
- An **audio** button cycles the outputs the phone really offers (earpiece, wired headset,
  Bluetooth, speaker).
- Signal bars and dBm come from Android (nothing estimated); network type says "4G LTE" / "5G".
- Multi-call support (swap, merge, targeted answer/decline of the exact ringing call).
- A lock-screen incoming-call surface launched by the notification's full-screen intent. When the
  app lock is enabled and the app is not authenticated, it exposes only caller identity and
  answer/decline/mute/end; a live call never unlocks the full dialer.

### Everything else
- Contacts, recents, dialpad, favorites, voicemail, speed dial, quick replies.
- Block and unblock numbers through Android's system blocklist (and a local fallback list).
- Call notes attached to a number.
- App lock backed by the device's screen-lock credential (enable it in Privacy settings; unlocking
  Nova Dialer after it leaves the foreground then requires your device PIN/pattern/biometric).
- Appearance/theming, password-encrypted local backup (contacts, call history, notes and settings),
  vCard import/export.

### Not in this build (deferred or removed in the source audit)
- Focus mode / call protection (no call-screening service is registered).
- Caller announcer.
- Offline spam block / blocklists (the screening hook, spam-labelling action and spam settings
  were removed; existing spam rows are preserved for backup compatibility).
- Call recording (no microphone permission and no recording UI/start path; automatic recording
  cannot start even if an older install or restored backup carries the preference. The recorder
  and crypto helpers are retained only so existing recording files and rows stay readable).
- Callback reminders (the scheduler, alarm receiver, dialog and settings were removed; existing
  reminder rows are preserved for backup compatibility).
- Duplicate-contact finder (the scanner, settings entry point and startup scan were removed).
- Dynamic Island floating capsule (the overlay permission and all overlay UI were removed).
- Emergency SOS (the deferred code and its settings/triggers were removed).
- Home-screen widget (the provider, widget activity, layouts and drawables were removed).
- Biometric app-lock pre-prompt (app lock uses the device screen-lock credential instead).

## Privacy
- `INTERNET` permission is explicitly **removed** from the manifest — the app cannot use the network.
- Contacts, call history and notes are stored locally; backups are AES-256 encrypted and require
  a password.
- Data leaves the device only when the user explicitly exports, shares or restores it.
- `allowBackup=false`, so Android cloud backup does not copy app data.
- App Reset: Explicitly clearing app data erases Nova Dialer's local encrypted database, encryption keys, preferences, and private caches while preserving user-selected exports in document-picker locations.
- Nova Dialer holds **no SMS permission** and never sends SMS itself. A decline reply uses
  Android's decline-with-message, or hands a pre-filled draft to your own messaging app once the
  call is no longer ringing. Call Log access is requested only after Nova Dialer holds the default
  Phone role.
- The packaged runtime libraries are open-source: AndroidX/Jetpack, Kotlin/Coroutines, Room,
  SQLCipher and Coil (which brings its OkHttp/Okio transport), plus the bundled Inter font. There
  are no analytics, advertising or crash-reporting SDKs, no remote endpoint, and no code that loads
  a remote image. The missing `INTERNET` permission also makes network access impossible at the
  Android level.
- Full policy text: [`PRIVACY.md`](PRIVACY.md). A public HTTPS URL is still required for the Play
  Console listing.
- Remaining publishing tasks and Data safety draft:
  [`docs/PLAY_RELEASE_CHECKLIST.md`](docs/PLAY_RELEASE_CHECKLIST.md).

## License and credits

Nova Dialer is licensed **GPL-3.0-or-later**. The full license text is in [`LICENSE`](LICENSE).

Nova Dialer is a GPLv3 port built on the **Secure Dialer** codebase
(<https://github.com/Secure-Phone-apps/Secure-Dialer>), **Copyright (C) 2026 MovStore**,
GPL-3.0-or-later. Every file derived from Secure Dialer keeps its original
`Copyright (C) 2026 MovStore` / SPDX header unchanged; the files written for Nova Dialer are
listed in [`NOTICE.md`](NOTICE.md) and carry a `Copyright (C) 2026 Nova Dialer contributors`
header. See [`docs/PORTING_PLAN.md`](docs/PORTING_PLAN.md) for the full derivation log.

Third-party components are open-source only (AndroidX, Room, SQLCipher, Coil — Apache-2.0
or MIT). There are no ads, no analytics, no server and no `INTERNET` permission.

### The name "Nova Dialer"

This app was previously branded "NovaDial" and is now named **"Nova Dialer"** everywhere it is
shown to users. The former name remains only in non-user-facing legacy identifiers that must
not change without a migration: the Android Keystore alias `NovaDialRecordingKey`, the backup
envelope identifier `novadial.backup`, the `novadial_intent_guard` preference file, and the
`applicationId`. The old screening-service class and widget action strings were removed with
their unreachable features and are no longer part of the app.

An unrelated open-source project also uses the older name "NovaDial". This app is **not**
affiliated with it. If you distribute builds, keep the `applicationId` distinct (see
below) so the two packages never collide in a store or on a device.

### Application id

The current id `com.aistudio.novadial.kpxm` was generated by an AI Studio template and
should be replaced (for example `com.novadial.app`) **before the first public release**.
Changing it means users must uninstall the old build (different package = different app
data) and any external integrations keyed to the old package must be updated. The id has
intentionally **not** been changed in this audit because it is a release-identity invariant.

## Security and recovery invariants (2.1.0/2.1.1 audits)

- **Database files fail closed.** Before asking for the SQLCipher key, the app inspects the whole
  database family (active file, `-wal`/`-shm`/`-journal`, interrupted swap, migration temp,
  legacy plaintext backup, and key material). A missing active file is a fresh install only when
  no recovery evidence and no key material exist. A zero-byte or unreadable active database is
  preserved and blocks startup; the app never creates an empty, plaintext, or in-memory
  replacement. Interrupted swaps are restored, including quarantining stale sidecars.
- **Plaintext migration is a validated swap.** Legacy plaintext SQLite is exported to SQLCipher,
  the exported snapshot must pass integrity checking and match every table row count, and only
  then is it renamed into place. Failed installs restore the original; failed rollback raises an
  explicit recovery error with both files preserved. No permanent plaintext backup is created.
- **Notification actions target exactly one call.** Answer, decline, and hang-up resolve the
  call id from authoritative Telecom state. A stale id is a no-op; an id-less legacy action is
  accepted only when exactly one eligible call exists. Ringtone and incoming notifications are
  recomputed from live calls, so ending one call cannot silence another ringing call.
- **A live call never authenticates the app.** With app lock enabled, an unauthenticated call
  shows only caller identity plus answer/decline/mute/speaker/end. The full dialer, recents,
  contacts, notes, recordings, quick responses, and settings stay locked. Neither the lock-screen
  activity nor MainActivity dismisses the keyguard; the private intent token only authorises the
  specific internal action and is durably persisted before use.
- **Imports are bounded while reading.** Backup (20 million characters), vCard (5 million), and
  text-list (2 million) imports stop at one character over the format limit. They never call
  `readText()` or allocate from a provider-reported length, and return typed empty/too-large/
  failure results to localised UI messages.
- **Release packaging is gated.** Exact release packaging tasks validate `key.properties` before
  packaging and cannot fall back to the debug key. Lint runs with `abortOnError` and missing or
  extra translations enabled. The APK output is a single universal APK; the AAB provides Play's
  ABI splits.

The file-state recovery coordinator and the call-routing/presentation planners are covered by JVM
tests. These tests do **not** claim native SQLCipher coverage (Robolectric uses the app's in-memory
Room test path), and no physical-device, multi-SIM, lock-screen, or Play Console verification was
performed for this audit. Those checks remain in
[`docs/DEVICE_TEST_MATRIX.md`](docs/DEVICE_TEST_MATRIX.md).

## Premium redesign (2.1)

A new design system, applied to every page, plus a round of performance work.

**Look and feel**
- **Three themes, new palettes:** *Day* (soft porcelain), *Default* (midnight) and *Night* (true black for OLED).
  Surfaces are layered by colour (page, card, sheet) with hairline borders instead of heavy shadows.
- **Accent picker restored and improved:** Aurora, Nebula, Glacier, Ember, Forest, Mono or any custom colour. A full,
  balanced palette is generated from one seed colour, and every tone is chosen by **measured contrast**
  (WCAG AA: accent text on the page >= 4.5:1, labels on buttons >= 4.8:1, white on the brand gradient >= 3:1).
  A unit test checks this for every accent in both modes.
- **Typography:** Inter, bundled with the app (OFL licence in `assets/licenses/`), with tabular digits for the dialpad and
  call timer. Hindi, Chinese, Japanese, Arabic and other scripts use the system font automatically.
- **New chrome:** pill search bar with an animated focus ring; bottom navigation with a sliding spring pill and
  outline-to-filled icons, flush at the bottom edge; gradient keypad button with a soft glow that pops in on a spring.
  The search field is shared by all three tabs, while the settings menu appears on Home only and reserves its
  leading slot elsewhere, so the field does not jump when tabs change.
- **Pages:** soft glow page background, tonal contact and call cards, gradient call button on the dialpad, keys that
  light up in the accent colour, solid on/off tiles on the call screen, gradient ring around the caller photo,
  gradient lock badge, matching settings cards.
- **Motion:** one shared motion language (`NovaMotion`): springs for touch, short fades for content, a depth effect
  while swiping between pages, smooth card expansion and list-item movement.
- **Loading and resilience:** one shared **Nova Orbit** system represents loading, content, empty,
  permission-denied, error-with-retry and refreshing as distinct states. Contacts/favorites and
  Recents use shape-matched skeletons; a provider/DB failure shows an explicit retry card instead
  of a spinner; an existing list refreshes under a thin progress bar. Backup, restore and vCard
  import/export use a blocking progress overlay with the actual operation name. Orbit, shimmer and
  refresh animations become static when Android's animator scale is disabled.
- **Responsive layout:** the active-call screen pins answer/end controls while the middle content
  scrolls; the dialpad sheet, SIM picker and add-to-contact sheet cap their height and scroll in
  landscape and with the keyboard open. Small icon actions now have 48 dp touch targets, and the
  layouts tolerate compact windows, tablets, 200% font scale, RTL and long localized labels.

**Performance**
- Recents: every row used to collect the app-wide recordings and spam lists and filter them again, and asked the phone
  for the SIM count. They now share ONE precomputed index (`RecentsLookup`), so a row does a single map lookup.
- Contacts grid: one `items(count)` call instead of registering every contact in a `forEach`.
- Press animations (dialpad keys, call buttons, cards, tabs) are read in the graphics layer, so they redraw instead of
  recomposing on every frame.
- The call screen's breathing glow runs only while the call is ringing or connecting, not for the whole conversation.
- Colour schemes are built once per setting change, not on every recomposition.
- Dialpad normalization compiles its combining-mark regex once per process instead of once per
  contact/call record, and the dashboard clock is stable across recompositions.
- Recents hoists one blocked-number set for the whole list instead of opening a collector in every
  row; deleting all history for a number is one bulk query instead of one delete per row.
- Provider/DB mutations, spam-list parsing and backup/vCard export work run on
  `Dispatchers.IO`; dialpad matching is only subscribed while the dialpad sheet is composed.
- A handwritten **baseline profile** (`app/src/main/baseline-prof.txt`) with the profile installer: faster first launch and
  first scroll.
- The duplicate-contact scan and its settings entry point were removed in the 23 October source
  audit, so the old once-a-week prompt and contact scan no longer exist in the shipped flow.
- No more white/grey flash at start: the window background matches the theme.

**Removed**
- Unused libraries (Navigation, Compose preview tooling, duplicate icon core, Paging Compose) and
  unused code (`GeneralSettingsSupportCard`, three unused view-model functions, the old unreachable
  palettes).
- **Source audit cleanup:** unreachable recording UI/start paths, caller announcer, call screening,
  callback reminders, duplicate finder, spam CSV settings, flash alerts, analytics, focus/VIP,
  block-list management, call-log exporters and the home-screen widget were removed after tracing
  every manifest/Compose/settings route. Data models, DAOs, backup fields and recording/recovery
  paths were retained where needed for legacy compatibility. Dead tests and 206 orphaned strings
  were removed from all 10 locales; key-order parity still passes `LocalizationIntegrityTest`.
- The `reference/` archive and the separate design-files copy. `PORTING_PLAN.md` moved to `docs/`.
  Local IDE metadata under `.idea/` is git-ignored and is not part of the repository.

> Automated verification on 25 October 2026: **422 JVM unit tests in 62 classes** (0 failures,
> 0 errors, 0 skipped), `lintDebug` with **0 errors and 39 warnings**, `lintRelease` with
> **0 errors and 40 warnings**, and successful `assembleDebug`, `assembleRelease` and
> `bundleRelease` runs. Instrumentation was **NOT RUN** for this build
> because no emulator or device was attached; the earlier 22 October emulator run is historical
> evidence only. The signed local APK is a single universal
> `app/build/outputs/apk/release/app-release.apk` (11,209,221 bytes, SHA-256
> `D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`); the AAB is
> `app/build/outputs/bundle/release/app-release.aab` (10,626,879 bytes, SHA-256
> `E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`). `apksigner` verified
> the APK with APK Signature Scheme v2. `jarsigner -verify` returned exit 0 and
> reported `jar verified` for the AAB, with self-signed/no-timestamp and JarFile/JarInputStream
> consistency warnings; the AAB manifest contains no `INTERNET` permission. The mapping is
> `app/build/outputs/mapping/release/mapping.txt` (51,771,588 bytes, SHA-256
> `A0859243F8B760B2748D306344AF4A3AFDB89154A6EBA14328C423BAD361025D`). Nothing has been
> uploaded to Play.
> **After these runs,** the bottom navigation bar was moved from the tab content `Column` into
> `Scaffold(bottomBar = ...)`, with the bar height now delivered through `PaddingValues`. Re-run the
> JVM suite, rebuild the AAB/APK and repeat the portrait/landscape device checks before upload.
> The app has not been tested on a physical phone in this environment, so Telecom,
> full-screen intent handling, multi-call transitions and OEM background restrictions still need
> the checklist below and the full matrix in `docs/DEVICE_TEST_MATRIX.md`.
>
> **Known limitation:** database initialization and SQLCipher key/recovery checks still run on the
> application-start path. Moving that bootstrap to a dedicated background initializer is deferred
> because it would reorder the fail-closed recovery decisions; all other disk, provider, export
> and backup work described above runs off the main thread.

## Phone test checklist
1. Install, set Nova Dialer as the default Phone app, and grant the permissions required for
   the feature you are testing (contacts for contacts, phone/call log after the role is granted,
   notifications for incoming-call alerts). Do not grant permissions you do not intend to use.
2. Close Nova Dialer. Have someone call you: the call screen opens with the caller's name (or the
   notification, if the phone is locked).
3. Answer, then ask the caller to hang up: the call screen must close by itself.
4. During a call, tap mute / speaker / hold / audio: the buttons must match what you hear.
5. Make a call on SIM 2 (if you have two SIMs): it must use SIM 2.
6. With a call active, receive a second call: answer it (first call held) and decline it; the
   active call must never be ended by the decline.
7. While the phone is locked, receive a call: the full-screen call screen (or heads-up fallback)
   must appear over the keyguard.
8. Recents > Voicemail (only if your carrier supports visual voicemail): play a message.

## Release preparation

- [Physical-device test matrix](docs/DEVICE_TEST_MATRIX.md) — per-test purpose, prerequisites,
  steps, expected result, actual-result field, status, evidence and recovery guidance. All
  device results are currently **NOT RUN**.
- [Play store listing draft](docs/PLAY_STORE_LISTING_DRAFT.md) — proposed copy, permission
  explanations, missing contact fields and asset inventory.
- [Play Console declaration worksheet](docs/PLAY_CONSOLE_DECLARATIONS.md) — default Phone role,
  Call Log, full-screen intent, Data safety, content rating and privacy-policy questions.
- [Privacy-policy hosting](docs/PRIVACY_HOSTING.md) — GitHub Pages instructions and the current
  publication blocker (the public URL is still 404).

## License
GPL-3.0-or-later — see [`LICENSE`](LICENSE) and the **License and credits** section above
and [`NOTICE.md`](NOTICE.md).
