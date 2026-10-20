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
      SMS app, and SOS opens the user's own messaging app for confirmation. The listing must not
      claim silent SMS sending.
- [ ] **Foreground service `specialUse`.** Declare the shake-to-SOS watch and provide the
      justification. The service shows an ongoing, cancelable notification and stops if the
      foreground start fails.
- [ ] **Full-screen intents.** Confirm the FSI permission is granted for the default dialer and
      test the fallback notification path on Android 14/15/16.
- [ ] **Contacts.** Declare why contacts are read and written (dialer, contact editing, duplicate
      merge).
- [ ] **Exact alarms.** Declare the reminder scheduling use case if Play Console asks.
- [ ] **Call recording.** Comply with local consent laws and make the in-app recording disclosure
      visible. Store listing text must not imply automatic silent recording.
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
| Call recordings | No (stored on device; encrypted when the Keystore is available, with an explicitly flagged unencrypted fallback) | Only when the user exports/shares | User-initiated recording | Microphone permission |
| SMS content | No (handled by carrier/SMS app; SOS opens the user's own messaging app with a pre-filled draft) | Carrier only when the user sends | Quick replies, SOS | None — the app holds no SMS permission |
| Approximate/precise location | No (read at SOS time only) | Included only in the user's SOS SMS if enabled | Emergency SOS | Location permission |
| App settings/notes | No (stored on device) | Only in encrypted backups | App functionality | None |

The app has no `INTERNET` permission, no advertising, no analytics and no crash-reporting SDK.

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
- [ ] Call recording start/stop, playback, share, export and delete; confirm files are encrypted
      (or explicitly reported as unencrypted when encryption fails) and no phone number appears in
      new filenames.
- [ ] Password-encrypted backup export/import, including contacts and call history.
- [ ] Shake-to-SOS enable, visible notification, cancel, stop, reboot re-arm and FGS restrictions.
- [ ] Full-screen intent fallback on Android 14+.
- [ ] Overlay/Dynamic Island permission and behavior.
- [ ] OEM battery optimization/kill behavior (Samsung, Xiaomi, Pixel).
- [ ] Upgrade from the previous release without losing data.
- [ ] Recovery behavior when the database key is unavailable: the app must not delete data.

## 6. Residual blockers

Until the boxes above are complete, the source tree is **not** a publishable release. In
particular, the signed AAB, public privacy URL, Play Console declarations and store assets cannot
be produced from this repository alone.
