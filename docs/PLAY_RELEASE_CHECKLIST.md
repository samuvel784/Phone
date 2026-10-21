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

- [ ] 512×512 high-resolution icon.
- [ ] 1024×500 feature graphic.
- [ ] At least 2 phone screenshots (Play normally expects 4–8).
- [ ] Short description and full description that match actual behavior.
- [ ] Support email and website.
- [ ] Content rating and target audience answers.
- [ ] App access instructions if reviewers need demo data (no fake-call flow exists).

## 5. Device verification (required before calling it publishable)

Test on a physical device, not only an emulator:

- [ ] Fresh install, role request, contact and Call Log permission flow.
- [ ] Incoming call on lock screen and while the app is backgrounded.
- [ ] Answer, decline, reject-with-SMS, missed-call notification.
- [ ] Password-encrypted backup export/import, including contacts and call history.
- [ ] Full-screen intent fallback on Android 14+.
- [ ] Multi-call: active call + waiting call (answer/decline/swap), and notification transition
      from one ended call to the next live call.
- [ ] OEM battery optimization/kill behavior (Samsung, Xiaomi, Pixel).
- [ ] Upgrade from the previous release without losing data.
- [ ] Recovery behavior when the database key is unavailable: the app must not delete data.

## 6. Release-gate audit follow-up (completed 21 October 2026)

The blockers from the 21 October 2026 release-gate audit have been resolved:

- **Privacy strings.** The in-app privacy dialog copy (`privacy_section_1_desc`, `privacy_section_2_desc`,
  `privacy_section_3_desc`) was rewritten in all 10 locales (`values` + ar, de, es, fr, hi, ja, pl,
  pt, zh) so it no longer mentions call recording, the microphone or SOS location. The 42 orphaned
  `sos_*` strings, the Dynamic Island strings, the overlay-permission warning and the orphaned
  category headers were removed from every locale (key order parity is covered by
  `LocalizationIntegrityTest`, and a new `DeprecatedNavigationGuardTest` guards the release scope).
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

## 7. Residual blockers

Until the boxes above are complete, the source tree is **not** a publishable release. In
particular, the signed AAB, public privacy URL, Play Console declarations and store assets cannot
be produced from this repository alone.
