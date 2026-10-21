# Nova Dialer

A real Android default dialer. It uses your phone's own contacts, call log, SIM cards and
voicemail — no sample data, no cloud, and no internet permission.

Package: `com.aistudio.novadial.kpxm` — minSdk 24 — targetSdk 36 — version 2.1.0 (versionCode 4)

> **Rebuilt, not patched.** Nova Dialer 2.0 uses the mature **Secure Dialer** codebase
> (`com.example`, GPL-3.0-or-later) as its foundation, rebranded to Nova Dialer, with Nova Dialer's
> unique features re-added on top. See **[docs/PORTING_PLAN.md](docs/PORTING_PLAN.md)** for the full audit of
> the original issues and how each one is resolved, plus the status of the deferred features
> (Emergency SOS, Analytics).

## Build
1. Open the folder in **Android Studio** and let Gradle sync.
2. Build Variants -> `debug` -> Build > Build APK(s). Install it on a real phone.
3. On the phone: Settings > Apps > Default apps > Phone app > Nova Dialer. Accept every permission prompt.
4. Release build: set `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (or put
   `my-upload-key.jks` in the project folder). The release build deliberately fails fast without
   these credentials; it never falls back to the debug key.
5. Unit tests: `./gradlew testDebugUnitTest`; lint: `./gradlew lintDebug`.

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
- A lock-screen incoming-call surface launched by the notification's full-screen intent.

### Everything else
- Contacts, recents, dialpad, favorites, voicemail, speed dial, quick replies.
- Call notes attached to a number.
- App lock backed by the device's screen-lock credential (enable it in Privacy settings; unlocking
  Nova Dialer after it leaves the foreground then requires your device PIN/pattern/biometric).
- Appearance/theming, password-encrypted local backup (contacts, call history, notes and settings),
  vCard import/export.

### Deferred in this build (not implemented in the current simplified v1)
- Focus mode / call protection (no call-screening service is registered).
- Caller announcer.
- Offline spam block / blocklists.
- Call recording (no microphone permission; no recording UI).
- Callback reminders.
- Duplicate-contact finder (no settings entry point ships in this build).
- Dynamic Island floating capsule (the overlay permission and all overlay UI were removed).
- Emergency SOS (deferred code and its settings/triggers were removed in this build).

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
shown to users. The former name is retained only in non-user-facing machine identifiers that must
not change (the Kotlin class `NovaDialCallScreeningService` and the Android Keystore alias
`NovaDialRecordingKey`), plus the `applicationId`.

An unrelated open-source project also uses the older name "NovaDial". This app is **not**
affiliated with it. If you distribute builds, keep the `applicationId` distinct (see
below) so the two packages never collide in a store or on a device.

### Application id

The current id `com.aistudio.novadial.kpxm` was generated by an AI Studio template and
should be replaced (for example `com.novadial.app`) **before the first public release**.
Changing it means: users must uninstall the old build (different package = different app
data), and any hard-coded widget action strings (`com.aistudio.novadial.widget.*`) must be
renamed to match. The id has intentionally **not** been changed yet.

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
- The duplicate-contacts scan waits until the first screen is drawn, and asks at most once a week (it used to prompt on
  every launch).
- No more white/grey flash at start: the window background matches the theme.

**Removed**
- Unused libraries (Navigation, Compose preview tooling, duplicate icon core) and unused code
  (`GeneralSettingsSupportCard`, three unused view-model functions, the old unreachable palettes).
- The `reference/` archive, the `.idea/` folder and the design-files copy. `PORTING_PLAN.md` moved to `docs/`.

> The debug build, 284 JVM unit tests and lint pass locally: `lintDebug`/`lintRelease` now report
> **0 errors** (37 warnings remain, in deferred-feature code, Gradle/dependency housekeeping and
> style suggestions — see `docs/PLAY_RELEASE_CHECKLIST.md`).
> The app has not been tested on a physical phone in this environment, so Telecom,
> full-screen intent handling, multi-call transitions and OEM background restrictions still need
> the checklist below.

## Phone test checklist
1. Install, set as default phone app, accept permissions.
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

## License
GPL-3.0-or-later — see [`LICENSE`](LICENSE) and the **License and credits** section above
and [`NOTICE.md`](NOTICE.md).
