# Nova Dialer

A real Android default dialer. It uses your phone's own contacts, call log, SIM cards and
voicemail — no sample data, no cloud, and no internet permission.

Package: `com.aistudio.novadial.kpxm` — minSdk 24 — targetSdk 36 — version 2.1.0 (versionCode 4)

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

### Deferred in this build (not implemented in the current simplified v1)
- Focus mode / call protection (no call-screening service is registered).
- Caller announcer.
- Offline spam block / blocklists (no screening service is registered; the spam-labelling
  action and spam settings screen are hidden).
- Call recording (no microphone permission and no reachable recording UI, including playback of
  legacy recordings; automatic recording is disabled even if an older install or restored backup
  carries the preference; existing recording files and rows are left untouched).
- Callback reminders (no reachable reminder entry point; the alarm receiver is not registered;
  existing reminder rows are only preserved for backup compatibility).
- Duplicate-contact finder (no settings entry point and no startup scan in this build).
- Dynamic Island floating capsule (the overlay permission and all overlay UI were removed).
- Emergency SOS (deferred code and its settings/triggers were removed in this build).
- Home-screen widget (the provider and widget-only activity are not declared in this build).
- Biometric app-lock pre-prompt (app lock uses the device screen-lock credential instead).

## Privacy
- `INTERNET` permission is explicitly **removed** from the manifest — the app cannot use the network.
- Contacts, call history and notes are stored locally; backups are AES-256 encrypted and require
  a password.
- Data leaves the device only when the user explicitly exports, shares or restores it.
- `allowBackup=false`, so Android cloud backup does not copy app data.
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
shown to users. The former name remains only in non-user-facing machine identifiers that must
not change without a migration: the Kotlin class `NovaDialCallScreeningService`, the Android
Keystore alias `NovaDialRecordingKey`, the backup envelope identifier `novadial.backup`, the
`novadial_call_protection` / `novadial_intent_guard` preference files, the widget action strings
`com.aistudio.novadial.widget.*`, and the `applicationId`.

An unrelated open-source project also uses the older name "NovaDial". This app is **not**
affiliated with it. If you distribute builds, keep the `applicationId` distinct (see
below) so the two packages never collide in a store or on a device.

### Application id

The current id `com.aistudio.novadial.kpxm` was generated by an AI Studio template and
should be replaced (for example `com.novadial.app`) **before the first public release**.
Changing it means: users must uninstall the old build (different package = different app
data), and any hard-coded widget action strings (`com.aistudio.novadial.widget.*`) must be
renamed to match. The id has intentionally **not** been changed yet.

## Security and recovery invariants (2.1.0 audit)

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
- **Pages:** soft glow page background, tonal contact and call cards, gradient call button on the dialpad, keys that
  light up in the accent colour, solid on/off tiles on the call screen, gradient ring around the caller photo,
  gradient lock badge, matching settings cards.
- **Motion:** one shared motion language (`NovaMotion`): springs for touch, short fades for content, a depth effect
  while swiping between pages, smooth card expansion and list-item movement.

**Performance**
- Recents: every row used to collect the app-wide recordings and spam lists and filter them again, and asked the phone
  for the SIM count. They now share ONE precomputed index (`RecentsLookup`), so a row does a single map lookup.
- Contacts grid: one `items(count)` call instead of registering every contact in a `forEach`.
- Press animations (dialpad keys, call buttons, cards, tabs) are read in the graphics layer, so they redraw instead of
  recomposing on every frame.
- The call screen's breathing glow runs only while the call is ringing or connecting, not for the whole conversation.
- Colour schemes are built once per setting change, not on every recomposition.
- A handwritten **baseline profile** (`app/src/main/baseline-prof.txt`) with the profile installer: faster first launch and
  first scroll.
- The deferred duplicate-contact scan is disabled at startup in this build; the legacy scanner is
  retained behind `ReleaseScope.DUPLICATE_FINDER_ENABLED`. This removes the old once-a-week prompt
  and its contact scan from the shipped flow.
- No more white/grey flash at start: the window background matches the theme.

**Removed**
- Unused libraries (Navigation, Compose preview tooling, duplicate icon core) and unused code
  (`GeneralSettingsSupportCard`, three unused view-model functions, the old unreachable palettes).
- The `reference/` archive and the separate design-files copy. `PORTING_PLAN.md` moved to `docs/`.
  Local IDE metadata under `.idea/` is git-ignored and is not part of the repository.

> Automated verification on 22 October 2026: **323 JVM unit tests** (0 failures, 0 errors,
> 0 skipped), **8 instrumentation tests** on an API 37 emulator (0 failures), `lintDebug`/
> `lintRelease` with **0 errors and 36 warnings**, and successful `assembleRelease` and
> `bundleRelease` runs. The signed local APK is a single universal
> `app/build/outputs/apk/release/app-release.apk` (11,239,878 bytes, SHA-256
> `75CE428767D72488E26B163E5F11716CFC46AE92AC63464E8E0F2FC12E82CD22`); the AAB is
> `app/build/outputs/bundle/release/app-release.aab` (10,598,691 bytes, SHA-256
> `27EC416A1D5C1536CAB03585CABE9731E229CF372228D181D4062628B40405DE`). `apksigner` verified
> the APK with APK Signature Scheme v2. `jarsigner -verify -verbose -certs` returned exit 0 and
> reported `jar verified` for the AAB, with self-signed/no-timestamp and JarFile/JarInputStream
> consistency warnings; `bundletool 1.18.3 validate` returned exit 0. The mapping is
> `app/build/outputs/mapping/release/mapping.txt` (51,347,886 bytes, SHA-256
> `D0589AFB85131709922FE36EDAECF630C3350747AECA13BD495548A371A24098`). Nothing has been
> uploaded to Play.
> The app has not been tested on a physical phone in this environment, so Telecom,
> full-screen intent handling, multi-call transitions and OEM background restrictions still need
> the checklist below and the full matrix in `docs/DEVICE_TEST_MATRIX.md`.

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
