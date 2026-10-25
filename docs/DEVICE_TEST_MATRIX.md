# Nova Dialer physical-device test matrix

**Physical-device status: NOT RUN.** No physical Android device or SIM is available in this
environment. **No emulator or device is attached for the 25 October 2026 final-source rebuild, so
instrumentation is NOT RUN for the current artifact.** The 22 October 2026 emulator results are
kept below as historical evidence only; they do not substitute for the physical-device matrix.
Every test card below remains **NOT RUN** until a human records a result on a real phone.

Target build: **Nova Dialer 2.1.1** (versionCode 5), `com.aistudio.novadial.kpxm`,
minSdk 24, targetSdk 36. The current local release candidate is
`app/build/outputs/bundle/release/app-release.aab` (10,626,879 bytes, SHA-256
`E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`). For direct sideload
testing use the universal APK
`app/build/outputs/apk/release/app-release.apk` (11,209,221 bytes, SHA-256
`D5B813645F6C4A39B8C59604518C0110AEBF9193CE22C845622445DF32D16217`). Re-check the relevant hash
before installing so the report and the tested artifact cannot drift apart.

## Automated verification (25 October 2026)

These are the only automated results recorded in this file. They are not physical-device results.

> **Post-verification layout change:** the bottom navigation bar was moved from the tab content
> `Column` into `Scaffold(bottomBar = …)`, with the bar height delivered to the content through
> `PaddingValues`. That change landed after the results below were recorded, so the JVM suite,
> release build and device orientation/inset checks must be repeated before upload.

- **JVM:** `:app:testDebugUnitTest --rerun-tasks --no-build-cache` completed with **422 tests in
  62 classes, 0 failures, 0 errors, 0 skipped**. This includes the new contact-identity,
  recent-row, reset, number-normalizer and startup-recovery suites.
- **Header menu policy:** `HeaderTabMenuPolicyTest` asserts the settings menu is exposed only on the
  Home tab, and `HeaderSearchBarTest` asserts the search field remains present, the menu is
  clickable when enabled, absent when disabled, and keeps the same x-position in both states.
- **Transactional reset journal:** `AppResetTest` writes and reads the reset journal, checks the
  stage/timestamp/manifest round trip, and verifies a structured `restorePending` result restores
  a staged file and reports failure instead of silently dropping it.
- **Recovery-screen proof:** `StartupRecoveryTest` creates throwing `AndroidViewModel` subclasses
  through a real `androidx.lifecycle.ViewModelProvider` (including AndroidX's reflective
  `RuntimeException`/`InvocationTargetException` wrapper) and verifies that only database/key
  failures reach `StartupResult.RecoveryRequired`; an unrelated `IllegalStateException` is
  rethrown. This is execution evidence, not source inspection.
- **Instrumentation:** **NOT RUN for the current artifact** — `adb devices` reported no attached
  emulator or phone. The historical 22 October 2026 run used `Medium_Phone_API_37.0(AVD)`
  (`emulator-5554`, API 37) and recorded **8 tests, 0 failures, 0 errors, 0 skipped**; that result
  belongs to the earlier build, not this one.
- **SQLCipher/recovery:** the current JVM suite still exercises the recovery/import planners. The
  real-file SQLCipher/recovery instrumentation coverage from 22 October 2026 (fresh encrypted
  create/reopen, wrong-key and unreadable candidates, fail-closed legacy evidence, interrupted
  swap recovery with sidecars, and plaintext-to-SQLCipher migration) has **not been rerun** on a
  device for this build.
- **App lock/restricted UI:** JVM Compose assertions confirm locked/restricted surfaces expose no
  Contacts, Recents, Favourites, Dialpad, or Settings destinations. The historical instrumentation
  check that inspected the installed `IncomingCallActivity` manifest/window contract was not
  rerun.
- **Telecom/imports:** JVM tests cover stale call ids, call-waiting races, ambiguous hang-up
  routing, and per-format over-limit import rejection with bounded reads.
- **Contact identity/reset:** JVM tests cover unique/ambiguous provider ids, permission denial,
  rename/deletion, national/international identity, no suffix matching, recents grouping, and a
  destructive reset that removes the database family, stale staging directories, all shared
  preferences and private storage while keeping a document-picker export. Source review also
  confirms live call, lock-screen and notification names come only from saved contacts; carrier
  CNAP is no longer substituted for a caller name.
- **Lint:** `:app:lintDebug` completed with **0 errors and 39 warnings**; `:app:lintRelease`
  completed with **0 errors and 40 warnings**. Remaining warnings are Gradle/dependency version
  housekeeping (24), unused resources (11 debug / 12 release), two plural-candidate suggestions,
  one `UseKtx` suggestion and one `OldTargetApi` notice. No lint rule was disabled.
- **Release build:** `:app:assembleDebug`, `:app:assembleRelease` and `:app:bundleRelease`
  completed successfully (release packaging depends on `validateReleaseSigning`). `apksigner`
  verified the APK with APK Signature Scheme v2, and `zipalign -c -P 16 -v 4` reported
  `Verification successful`. `jarsigner -verify` returned exit 0 and reported `jar verified` for
  the AAB, with self-signed/no-timestamp and JarFile/JarInputStream consistency warnings. The
  merged release manifest contains no `INTERNET` permission, `allowBackup=false`,
  `extractNativeLibs=false`, minSdk 24 and targetSdk 36.
- **Release APK smoke check:** **NOT RUN for this build.** The 22 October 2026 emulator install and
  `MainActivity` cold start are historical evidence only.
- **Still NOT RUN:** a real locked-device incoming call, full-screen-intent grant/denial,
  multi-SIM/call-waiting on a carrier, Bluetooth/wired audio, upgrade/recovery from a real previous
  install, OEM background restrictions, and all Play Console checks.

## How to use this matrix

- **Do not mark a test PASS because the app merely opened or did not crash.** PASS means the
  complete expected result was observed. Otherwise use **FAIL**, or keep **NOT RUN** if the
  prerequisite was unavailable.
- Use **test contacts and test numbers only**. Do not copy a personal address book or real call
  history into screenshots, logs, backups, or the bug report.
- **Do not test emergency numbers.** Use ordinary test numbers and a second phone/landline.
- Run the main matrix on a physical phone, not only an emulator. Emulator results may be useful
  for a quick check but are not a substitute for the physical-device gate.
- Some tests need a second phone, a second SIM, a carrier feature, Bluetooth/wired audio, an OEM
  device, or a specific Android version. Those tests are grouped in
  [Special-equipment runs](#special-equipment-runs) as well as in each card.
- If a test is **not applicable** to the device (for example full-screen-intent special access
  below Android 14), keep the status **NOT RUN** and write `N/A — reason` in the actual-result
  field. Do not silently omit it.
- Do not clear app data, uninstall, or restore a backup while investigating a data-related
  failure. Preserve the state first.

### Session record (fill once per device/run)

| Field | Value |
|---|---|
| Tester and date | |
| Device make/model | |
| Android version / build fingerprint | |
| App version and AAB/APK SHA-256 | |
| SIM(s), carrier, and SIM slots | |
| Default Phone role state | |
| Permission state (contacts / phone / call log / notifications / FSI) | |
| Bluetooth or wired accessory | |
| Evidence folder or ticket reference | |

### Evidence and privacy rules

- Give every item an evidence ID such as `E-B3-01`. Record the ID in the test card.
- Prefer screenshots of test data. If a real number or name is unavoidable, mask it before saving
  the image (solid box or crop). Do not rely on a blur that can be reversed.
- A screenshot of a `FLAG_SECURE` screen may be blank/black. That can itself be evidence; record
  that Android blocked the capture rather than treating it as a UI bug.
- If you collect `adb logcat`, filter to the app and `AndroidRuntime`, and mask phone numbers,
  names, and file paths before attaching it. Never upload a full unfiltered device log.
- Keep evidence out of Git. Do not commit personal contacts, call history, backups, or passwords.
- Do not write the test device's screen-lock PIN/password, backup password, or signing passwords
  into this file or a ticket.

### Failure and recovery protocol

1. Stop the test. Record the exact step, observed result, status, and evidence ID.
2. Retry once after restoring the documented prerequisite (role, permission, network/carrier,
   accessory, or setting). Do not change unrelated settings.
3. If a call is stuck, end it from the in-call notification, the system call UI, or by rebooting.
   Do not pull the SIM out while a call is active.
4. If a permission or role is wrong, fix it in **Settings → Apps → Nova Dialer → Permissions** or
   **Settings → Apps → Default apps → Phone app**. Re-run the test from the beginning.
5. If data is missing or the database will not open, **do not clear data and do not uninstall**.
   Export/copy evidence and a backup if the app allows it. Restore only from a known-good backup
   on a disposable test device.
6. If the full-screen UI or lock-screen behaviour is wrong, restore the special-access setting,
   then test the heads-up notification path again.
7. If the app crashes, capture the crash time, device state, and a filtered log; leave the app
   installed until the evidence is copied.

## Devices and accessories

| Ref | What is needed | Why |
|---|---|---|
| P1 | Primary phone, Android 14/15/16, SIM, default-Phone role | Main target behaviour and Android 14+ full-screen-intent rules |
| P2 | Second phone (Android/iOS/landline) | Incoming/outgoing calls, caller audio, remote hang-up, call waiting |
| SIM2 | Dual-SIM phone with a second active SIM, or a carrier conference/call-waiting service | SIM selection and multi-call tests |
| P3 | Phone on Android 7/8 (API 24–27), if available | Legacy lock-screen flags and old notification behaviour |
| BT | Bluetooth headset/speaker | Bluetooth audio route and headset button |
| WIRE | Wired headset | Wired audio route and headset button |
| OEM | Samsung/Xiaomi/Pixel-class device with aggressive background management | Battery optimisation and background-kill behaviour |
| OLD | The **intended previous Nova Dialer build** with the same application ID | Upgrade test only; this artifact is not supplied in the repository |
| ART | Signed release APK built from the current AAB, or an internal-testing install of the same AAB | The artifact actually under test |
| FILE | File manager/document provider | Export/share and backup/restore tests |

Use a test carrier/SIM where possible. Record the carrier and whether call waiting, conference
calling, voicemail, and Wi-Fi calling are provisioned; many failures in the multi-call section are
carrier limitations rather than app defects.

## Status summary

| Group | Test IDs | Current status |
|---|---|---|
| Install, role, permissions | A1–A5 | NOT RUN |
| Incoming calls and lock screen | B1–B7 | NOT RUN |
| Call actions and notifications | C1–C7 | NOT RUN |
| Multi-call | D1–D5 | NOT RUN |
| Audio routes and hardware | E1–E5 | NOT RUN |
| Reboot, background, OEM | F1–F4 | NOT RUN |
| Upgrade, database, backup | G1–G9 | NOT RUN |
| Deferred-feature absence | H1–H9 | NOT RUN |
| Privacy and store-facing checks | I1–I4 | NOT RUN |
| Accessibility, RTL, large fonts | J1–J4 | NOT RUN |
| Contacts and call history | K1–K5 | NOT RUN |

## A. Install, default Phone role, and permissions

### A1 — Fresh install and first launch

- **Purpose:** prove the current release artifact installs, launches, and identifies itself
  correctly.
- **Prerequisites:** P1; signed APK derived from the current AAB; previous Nova Dialer test build
  uninstalled; test contacts available; at least 100 MB free storage.
- **Steps:**
  1. Uninstall any previous Nova Dialer build from the test phone. Do not uninstall a build that
     contains data you still need.
  2. Install the release APK and open it.
  3. Open **Settings → Apps → Nova Dialer → App info** and read the version and package name.
  4. Rotate the phone and return to the main screen.
- **Expected:** Installation succeeds without a signature error. App opens to a usable main
  screen; package is `com.aistudio.novadial.kpxm`, version 2.1.1 (versionCode 5). No crash, ANR,
  or blank screen. Any permission prompt is tied to a visible feature, not an unexplained
  launch-time request.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-A1-01` — screenshot of App info/version; `E-A1-02` — first screen with test
  data only.
- **Recovery if it fails:** Re-download/copy the artifact and re-check its SHA-256. If install
  reports a signature mismatch, uninstall the old test build (after exporting anything you need)
  and install again. If it crashes, capture a filtered log and stop.

### A2 — Grant the default Phone role

- **Purpose:** confirm Nova Dialer can become the default Phone app and reflects the role.
- **Prerequisites:** A1 complete; P1; no other default-dialer pinning that blocks the change.
- **Steps:**
  1. From the app's permission/setup card, choose the action to become the default Phone app.
  2. If no prompt appears, open **Settings → Apps → Default apps → Phone app** and select
     **Nova Dialer**.
  3. Return to Nova Dialer and reopen the permission/setup card.
  4. Place one ordinary test call.
- **Expected:** The role request opens the Android role/default-app screen. After selecting Nova
  Dialer, the app reports that it is the default Phone app. A test call can be placed from Nova
  Dialer. No crash or loop back to the role prompt.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-A2-01` — default-app screen showing Nova Dialer; `E-A2-02` — app state after
  returning.
- **Recovery if it fails:** Re-open **Settings → Apps → Default apps → Phone app** and select Nova
  Dialer manually. On restricted OEM builds, also check **Settings → Apps → Nova Dialer → Open by
  default**. If the role is granted but the app does not notice it, force-stop and reopen, then
  record the OEM and Android version.

### A3 — Call Log permission timing

- **Purpose:** verify Call Log is requested only after the default Phone role, not on a fresh
  install.
- **Prerequisites:** A1 complete; default Phone role **not** granted; Android permission dialogs
  reset for Nova Dialer.
- **Steps:**
  1. Clear Nova Dialer app data (test device only), or install fresh.
  2. Open the app and try to open **Recents** without granting the Phone role.
  3. Watch for any Android runtime prompt for Call Log.
  4. Grant the Phone role, then open **Recents** again.
- **Expected:** Before the role, the app explains that call history needs the default Phone role
  and does **not** show a `READ_CALL_LOG`/`WRITE_CALL_LOG` runtime prompt. After the role is
  granted, the app requests Call Log permission when Recents/related functionality needs it.
  Denying it leaves the rest of the app usable without a crash.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-A3-01` — pre-role Recents explanation; `E-A3-02` — post-role permission
  prompt.
- **Recovery if it fails:** Revoke Call Log in **Settings → Apps → Nova Dialer → Permissions**,
  remove the role, and repeat. If Call Log is requested before the role, record it as a policy
  failure and stop; do not grant it to work around the problem.

### A4 — Permission prompts, grants, and denials

- **Purpose:** check that contacts/phone/notification permissions are requested at the right
  time and that denial degrades only the related feature.
- **Prerequisites:** A2 complete; test contacts; notification permission reset; Android 13+ for
  the notification portion.
- **Steps:**
  1. Open Contacts and note when the contacts/phone prompt appears.
  2. Grant the requested permissions. Confirm contacts load.
  3. Deny the contacts permission on a second run and confirm the app does not crash and explains
     the limitation.
  4. On Android 13+, deny notifications and confirm the app still opens and calls can be placed.
  5. Re-grant each permission from Android settings and confirm the feature recovers.
- **Expected:** Each permission is requested in context, with a clear explanation. Denial does not
  crash the app or block unrelated features. Call Log is not part of the initial pre-role request.
  Re-granting restores the feature after the app is reopened/refreshed.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-A4-01`–`E-A4-04` — prompt, denied state, recovery state.
- **Recovery if it fails:** Use **Settings → Apps → Nova Dialer → Permissions** to reset one
  permission at a time. If a prompt never appears, check the app's setup card and Android's
  "Don't ask again" state. Do not grant every permission merely to hide a prompt bug.

### A5 — No sensitive permissions in the installed app

- **Purpose:** confirm the shipped artifact does not request microphone, location, SMS, overlay,
  or biometric permissions.
- **Prerequisites:** A1 complete; access to App info → Permissions.
- **Steps:**
  1. Open **Settings → Apps → Nova Dialer → Permissions** and inspect every listed permission.
  2. Check **Special app access** for overlay and full-screen notifications.
  3. Compare the list with the expected permissions in section I1.
- **Expected:** The list contains contacts, phone, call log, phone state (if shown), notifications,
  and full-screen-intent access. It contains no microphone/`RECORD_AUDIO`, location, SMS, overlay
  (`SYSTEM_ALERT_WINDOW`), or biometric permission. The optional microphone `uses-feature` entry in
  the manifest is not a runtime microphone permission and must not cause a microphone prompt.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-A5-01` — permission list; `E-A5-02` — special access list.
- **Recovery if it fails:** Do not change the APK. Record the extra permission and its Android
  label, stop the release, and report it. This is a release-gate failure until explained.

## B. Incoming calls and the lock screen

### B1 — Incoming call while the app is open

- **Purpose:** verify the foreground incoming-call experience.
- **Prerequisites:** P1 + P2; default Phone role; contacts/phone permissions; screen unlocked; app
  open.
- **Steps:**
  1. From P2, call P1 using an ordinary test number.
  2. Observe the incoming screen: caller name/number, answer, decline, and any quick-reply action.
  3. Answer, speak both ways, then end the call from P1.
- **Expected:** The incoming UI appears promptly with the correct caller information. Answer and
  decline are usable. After answering, the active-call timer/state appears. Ending the call closes
  the call UI without leaving a stale incoming notification.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B1-01` — incoming screen with test caller; `E-B1-02` — active call.
- **Recovery if it fails:** Check that Nova Dialer still holds the Phone role. End any stuck call
  from the notification or system call UI, force-stop Nova Dialer, and retry once. Record whether
  the failure reproduces with the app open and with it backgrounded.

### B2 — Incoming call while Nova Dialer is backgrounded

- **Purpose:** verify incoming calls when the app is not foregrounded.
- **Prerequisites:** P1 + P2; default role; app opened once, then sent to Home/Recents.
- **Steps:**
  1. Press Home so Nova Dialer is in the background.
  2. Call P1 from P2.
  3. Answer from the full-screen UI or the notification; end the call.
- **Expected:** A full-screen call UI or a high-priority heads-up notification appears. Answer and
  decline actions work. The call is not silently dropped because the app is in the background.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B2-01` — background incoming UI/notification.
- **Recovery if it fails:** Confirm the role and notification permission. Disable battery
  optimisation temporarily and retry; record the OEM and whether the failure is timing-dependent.

### B3 — Incoming call while the screen is locked

- **Purpose:** verify the lock-screen incoming-call surface and screen wake-up.
- **Prerequisites:** P1 + P2; default role; a device screen lock set (PIN/pattern/password);
  notification permission; full-screen-intent access if Android 14+.
- **Steps:**
  1. Lock P1 and turn the screen off.
  2. Call P1 from P2.
  3. Observe whether the screen turns on and whether the call UI appears over the keyguard.
  4. Answer or decline without first unlocking the device, if the platform permits it.
  5. After the call, unlock normally and check Recents.
- **Expected:** The screen turns on. The incoming-call UI appears over the lock screen (or a
  heads-up notification if full-screen UI is unavailable). Answer/decline actions work; the app
  does not expose the rest of the app behind the keyguard. The lock-screen activity is not left in
  Recents after the call.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B3-01` — locked-screen incoming UI; `E-B3-02` — post-call Recents.
- **Recovery if it fails:** Check **Settings → Apps → Special app access → Full-screen
  notifications** (name varies by OEM), then retry B4/B5. If the device requires unlock before
  answering, record that explicitly; do not mark it a pass unless it matches the design.

### B4 — Full-screen-intent access granted (Android 14+)

- **Purpose:** verify the primary lock-screen path when Android allows full-screen intents.
- **Prerequisites:** P1 + P2 on Android 14/15/16; B3; full-screen-intent access set to **Allowed**
  for Nova Dialer.
- **Steps:**
  1. Confirm the special-access setting shows Nova Dialer is allowed.
  2. Lock and sleep P1.
  3. Call from P2.
  4. Answer/decline and end the call.
- **Expected:** The full-screen incoming-call activity appears over the keyguard, with caller
  information and usable answer/decline controls. No heads-up-only fallback is required while the
  permission is granted.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B4-01` — special-access setting; `E-B4-02` — full-screen incoming UI.
- **Recovery if it fails:** Re-open the special-access setting from the app's setup card, toggle it
  off/on, and retry. On Android <14, record `N/A — no special access`; do not use this card as a
  failure on those versions.

### B5 — Full-screen-intent access denied (Android 14+)

- **Purpose:** verify the required fallback when the user denies or revokes full-screen access.
- **Prerequisites:** P1 + P2 on Android 14/15/16; B3; full-screen-intent access set to **Not
  allowed** (use the app's Configure button or Settings → Apps → Special app access).
- **Steps:**
  1. Confirm the special-access setting is denied.
  2. Lock and sleep P1.
  3. Call from P2.
  4. Use the heads-up/high-priority notification to answer or decline.
  5. End the call and restore the setting afterwards.
- **Expected:** A heads-up/high-priority incoming notification appears with working answer and
  decline actions. The call is not silently dropped, the app does not crash, and the user is not
  trapped on a blank screen. The app may explain how to enable full-screen access, but must still
  provide the fallback.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B5-01` — denied setting; `E-B5-02` — fallback notification with actions.
- **Recovery if it fails:** Restore full-screen access, confirm B4 still works, and record the
  fallback failure separately. Do not leave the device with full-screen access denied after this
  test.

### B6 — Call privacy, screenshots, and Recents

- **Purpose:** verify that call details do not leak into screenshots or the recent-apps list.
- **Prerequisites:** P1 + P2; incoming or active call; screenshot/Recents access.
- **Steps:**
  1. While the incoming or active call UI is visible, open Recents and look for the call details.
  2. Attempt a screenshot of the call UI.
  3. End the call and inspect Recents again.
  4. Check whether any lock-screen call activity remains in Recents.
- **Expected:** The call screen is protected by `FLAG_SECURE`; a screenshot is blank/blocked or
  does not contain caller details. The lock-screen activity is excluded from Recents. If a Recent
  thumbnail exists, it must not reveal the caller name/number.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B6-01` — screenshot attempt result; `E-B6-02` — Recents after call. Mask any
  unavoidable caller data.
- **Recovery if it fails:** Do not publish the screenshot. Record the OEM/Android version and the
  exact surface that leaked. This is a privacy failure; stop and report it.

### B7 — Do Not Disturb, silent, and vibrate

- **Purpose:** verify the app follows the system ringer/DND mode and does not crash or ring
  unexpectedly.
- **Prerequisites:** P1 + P2; default role; control of **Settings → Sound & vibration** and DND.
- **Steps:**
  1. Set **Silent**, call from P2, record whether there is an audible ringtone, vibration, or
     full-screen UI.
  2. Set **Vibrate**, call again, and record the result.
  3. Set **Do Not Disturb** (total silence or priority-only as available), call again, and record
     the result.
  4. Restore the normal ringer mode and verify a normal call rings.
- **Expected:** Silent mode produces no audible ringtone; vibrate mode vibrates without an audible
  ringtone; DND follows the system policy (no audible alert unless the user has granted an explicit
  exemption). The app does not crash, and the call remains answerable from the UI/notification.
  Record any observed DND bypass as a failure or a documented product decision.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-B7-01`–`E-B7-03` — each mode and observed alert type.
- **Recovery if it fails:** Restore the normal sound mode, check the app's notification channel
  settings, and retry once. Do not disable DND globally to hide the issue.

## C. Call actions, notifications, and missed calls

### C1 — Answer and two-way audio

- **Purpose:** verify that answering establishes a real call with working audio.
- **Prerequisites:** P1 + P2; incoming call ringing.
- **Steps:** Answer on P1, speak in both directions, watch the timer, then end from P1.
- **Expected:** Audio works both ways, the timer starts, the active-call UI is shown, and the call
  ends cleanly when either side hangs up.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C1-01` — active call; `E-C1-02` — post-call state.
- **Recovery if it fails:** Check the audio route (earpiece/speaker), then retry with a wired or
  Bluetooth headset. End any stuck call and record whether the failure is audio-only or call-state.

### C2 — Decline an incoming call

- **Purpose:** verify decline ends the incoming call and returns to the previous screen.
- **Prerequisites:** P1 + P2; incoming call; app in foreground/background.
- **Steps:** Decline from the full-screen UI and, separately, from the notification. Ask P2 what
  the caller hears. Return to Nova Dialer.
- **Expected:** The call is rejected, P2 hears the carrier's normal decline/busy response, the
  incoming UI/notification disappears, and Nova Dialer returns to its previous screen.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C2-01` — post-decline screen; `E-C2-02` — caller-side report.
- **Recovery if it fails:** Check the default role and Telecom binding. Try the notification action
  instead of the in-app button to isolate the path. End any stuck call and retry once.

### C3 — Missed-call notification and Recents entry

- **Purpose:** verify an unanswered call produces the correct missed-call state.
- **Prerequisites:** P1 + P2; default role; Call Log permission granted; notification permission.
- **Steps:** Call P1 from P2 and let it ring out. Open the notification shade and Recents. Tap the
  missed-call notification.
- **Expected:** A missed-call notification appears with the test caller. Recents shows a missed
  entry with the correct direction and time. Tapping the notification opens the relevant call
  details or callback surface without crashing.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C3-01` — missed notification; `E-C3-02` — Recents entry.
- **Recovery if it fails:** Check notification and Call Log permissions, then retry. If the
  notification is missing but Recents is correct, record it as a notification failure rather than a
  call failure.

### C4 — Declining with a quick-reply message

- **Purpose:** verify quick replies do not send SMS from Nova Dialer itself.
- **Prerequisites:** P1 + P2; incoming call; at least one configured quick response; an SMS app
  available for the handoff path.
- **Steps:** Decline with a quick-reply message. Observe whether Android's decline-with-message
  path sends it or whether Nova Dialer hands a pre-filled draft to the user's SMS app. Check the
  app's permission list for SMS.
- **Expected:** The chosen message is handled by Android/Telecom or by the user's messaging app;
  Nova Dialer does not hold or request SMS permission and does not silently send an SMS. The call
  is declined and the app returns to its previous state.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C4-01` — quick-reply chooser; `E-C4-02` — handoff/result; `E-C4-03` — no SMS
  permission.
- **Recovery if it fails:** Remove the quick-reply action from the test and use a plain decline.
  Record whether the failure is the chooser, the handoff, or an unexpected SMS send. If the app
  appears to send SMS itself, stop and report it as a policy failure.

### C5 — Hang up from every path

- **Purpose:** verify call termination from the app, notification, and remote side leaves no stale
  state.
- **Prerequisites:** P1 + P2; active call.
- **Steps:** End one call from the in-app red button, another from the notification's hang-up
  action, and another by having P2 hang up. After each, inspect the notification shade and Recents.
- **Expected:** Every path ends the call, closes the active-call UI, and removes the ongoing-call
  notification. No call remains active in the system/Telecom UI. Recents records the correct
  duration/direction.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C5-01`–`E-C5-03` — each hang-up path.
- **Recovery if it fails:** End any stuck call from the system call UI, then force-stop Nova
  Dialer. Record which path failed; do not test all paths again on the same stuck call.

### C6 — Mute, speaker, hold, and unhold

- **Purpose:** verify in-call controls match the actual Telecom/audio state.
- **Prerequisites:** P1 + P2; active call with two-way audio.
- **Steps:** Toggle mute and ask P2 whether they can hear you. Toggle speaker and confirm the audio
  route changes. Toggle hold and ask P2 whether they hear hold audio/music. Unhold and confirm
  two-way audio returns. Repeat each control twice.
- **Expected:** Each button reflects the confirmed system state, not just the tap. Mute actually
  silences the microphone; speaker changes the route; hold is reflected on both sides; unhold
  restores audio. No button gets stuck or shows the opposite state after a rotation/background.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C6-01` — control states; `E-C6-02` — caller-side confirmation.
- **Recovery if it fails:** Return to the earpiece route, end the call, and retry once with a wired
  headset. Record whether the failure is UI-only or audio-state. Do not repeatedly toggle a stuck
  hold on a live call.

### C7 — Outgoing calls from keypad, contact, and Recents

- **Purpose:** verify all ordinary outgoing-call entry points.
- **Prerequisites:** P1 + P2; default role; contacts, phone, and Call Log permissions; one test
  contact and one recent test call.
- **Steps:** Dial a test number on the keypad; call the test contact; call back from a Recents
  entry. End each call.
- **Expected:** Each path places the call to the correct number. The active-call screen appears;
  the call ends cleanly; Recents records the correct direction and time. No duplicate call is
  placed by a double tap.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-C7-01`–`E-C7-03` — each entry point.
- **Recovery if it fails:** Check the Phone role and `CALL_PHONE` permission. Try a number typed
  directly before concluding that contact/Recents lookup is broken. End any stuck call.

## D. Multi-call: waiting, swap, hold, merge, and notification transitions

> These tests need carrier call waiting and, for merge, carrier conference support. A dual-SIM
> phone is not automatically a conference-capable phone. Record the carrier feature state before
> marking a failure.

### D1 — Answer a waiting call

- **Purpose:** verify a second incoming call is answered without dropping the first.
- **Prerequisites:** P1 with an active call to P2; SIM2/third party able to call P1; carrier call
  waiting enabled.
- **Steps:** While active with P2, call P1 from the second number. Answer the waiting call. Speak
  to each party and observe the UI.
- **Expected:** The first call is held, the second becomes active, and the UI shows both calls.
  Audio switches to the answered call. The first call is not ended.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-D1-01` — two-call UI.
- **Recovery if it fails:** End both calls, verify call waiting with the carrier's own phone app
  if possible, and retry. Record whether the carrier rejects the second call before the app sees it.

### D2 — Decline a waiting call

- **Purpose:** verify declining a waiting call leaves the active call connected.
- **Prerequisites:** D1 setup; active call plus waiting call.
- **Steps:** Decline the waiting call from the in-app control and, separately, from the
  notification. Continue speaking to the first party.
- **Expected:** Only the waiting call is rejected; the active call remains connected with audio.
  The notification/UI returns to the single active call. The active call is not ended by the
  decline action.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-D2-01` — post-decline active call.
- **Recovery if it fails:** If the active call drops, stop and report it as a high-severity
  multi-call failure. Do not retry repeatedly on a real call; use a test call.

### D3 — Swap between calls

- **Purpose:** verify call swapping updates both UI and audio.
- **Prerequisites:** D1 (two connected calls); carrier support.
- **Steps:** Use the swap control. Identify which party is active; speak to both. Swap back.
- **Expected:** The selected call becomes active and the other is held. The UI labels the active
  and held calls correctly. Audio follows the swap; no call is dropped.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-D3-01` — swapped state.
- **Recovery if it fails:** Return to the original call, end both, and retry once. Record whether
  the UI or the audio is wrong.

### D4 — Merge calls into a conference

- **Purpose:** verify conference merge where the carrier supports it.
- **Prerequisites:** two connected calls; carrier conference/merge provisioned; P1 + P2 + a third
  party or SIM2.
- **Steps:** With one call active and one held, choose merge/conference. Confirm all parties can
  hear each other. End the conference.
- **Expected:** The two calls merge into one conference entry/participant list. Audio works for all
  parties. Ending the conference ends the merged call; if the carrier does not support merge, the
  app reports that rather than crashing or dropping calls.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-D4-01` — conference UI; `E-D4-02` — carrier feature state.
- **Recovery if it fails:** End all calls, confirm conference support with the carrier, and record
  `BLOCKED BY CARRIER` in the actual-result field if it is not provisioned. Do not mark a carrier
  limitation as an app pass.

### D5 — Notification transition after ending one call

- **Purpose:** verify the remaining live call survives when one of two calls ends.
- **Prerequisites:** D1/D3; two live calls; notifications enabled.
- **Steps:** End one call (remote side and in-app, if possible). Inspect the notification and UI.
  Continue the remaining call.
- **Expected:** The remaining call stays active; the ongoing-call notification updates to the
  remaining call instead of disappearing or ending it. Tapping the notification opens the correct
  call screen.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-D5-01` — notification after one call ends.
- **Recovery if it fails:** Use the system call UI to keep the remaining call alive, then end it.
  Record whether the failure is notification-only or the live call was actually dropped.

## E. Audio routes and hardware

### E1 — Earpiece, speaker, wired, and Bluetooth routes

- **Purpose:** verify the route control matches the actual audio path.
- **Prerequisites:** P1 + P2; active call; BT headset paired; wired headset available.
- **Steps:** During one call, cycle the audio button through every route the phone offers. Speak
  and listen on each route. Note which routes are shown versus actually available.
- **Expected:** The app reports only routes the phone/Telecom says are available; selecting a route
  changes the audio path; the label matches what you hear. No route selection drops the call.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-E1-01` — route chooser; `E-E1-02` — audible confirmation per route.
- **Recovery if it fails:** Return to earpiece, disconnect accessories, and retry one route at a
  time. Record whether the failure is a missing route or a wrong audio path.

### E2 — Bluetooth connect/disconnect during a call

- **Purpose:** verify Bluetooth route changes without dropping the call.
- **Prerequisites:** E1; paired BT headset; active call.
- **Steps:** Start on earpiece, connect the BT headset, switch audio to it, then disconnect/power
  off the headset during the call.
- **Expected:** Audio follows the headset when connected; on disconnect it returns to a safe route
  (earpiece/speaker) and the call remains active. No crash or silent call.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-E2-01` / `E-E2-02` — connected and disconnected states.
- **Recovery if it fails:** Reconnect the headset, switch back to earpiece manually, and end the
  call. Record the headset model and whether the failure is route switching or call loss.

### E3 — Wired headset plug/unplug

- **Purpose:** verify wired-headset detection and fallback.
- **Prerequisites:** E1; wired headset with microphone; active call.
- **Steps:** Plug in the headset during a call, speak/listen, then unplug it.
- **Expected:** Audio routes to the headset when plugged in and back to the phone when unplugged.
  The call stays active; no audio is stuck on a disconnected route.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-E3-01` — wired route; `E-E3-02` — fallback.
- **Recovery if it fails:** Unplug/replug once, switch speaker on/off, and end the call if audio is
  stuck. Record whether the phone has a 3.5 mm jack or uses a USB-C adapter.

### E4 — Headset call button

- **Purpose:** verify the headset/Bluetooth call button performs the expected answer/end action.
- **Prerequisites:** E1/E2; headset button that the phone supports; incoming and active call
  scenarios.
- **Steps:** With an incoming call, press the headset button once. With an active call, press it
  once (or long-press, if that is the device convention). Observe the action.
- **Expected:** A single press answers an incoming call and ends an active call where Android/OEM
  supports that behavior; the app does not crash or leave a ghost call. If the accessory/OEM does
  not map the button, record it as unsupported rather than forcing a pass.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-E4-01` — button behavior.
- **Recovery if it fails:** Use the on-screen controls to end the call and record the headset model.
  Do not repeatedly press a stuck button.

### E5 — Dual-SIM selection and call-log slot

- **Purpose:** verify a call can be placed on the chosen SIM and is recorded with the right slot
  where the platform exposes it.
- **Prerequisites:** dual-SIM P1 with both SIMs active; SIM2; Call Log permission; default role;
  `READ_PHONE_STATE` not yet granted (fresh install, clear app data, or revoke it in Android
  settings).
- **Steps:** On first launch, confirm the permission disclosure mentions phone-state access and
  grant it. Then place a call and choose SIM 2 when prompted. Check the active-call UI and the
  Recents entry. Repeat with SIM 1. Also deny the phone-state permission once and verify that the
  app does not crash and falls back to the platform/default SIM. If the app never prompts, record
  whether the permission was already granted or the one-time prompt flag was set.
- **Expected:** The call uses the selected SIM (confirm with the carrier/phone UI). The active call
  and Recents show the correct SIM/slot where Android exposes it. No call is placed on the wrong
  SIM without user choice. On a multi-SIM device, `READ_PHONE_STATE` should be requested with the
  permission flow and the SIM chooser should appear after the grant. If the permission is denied,
  record the chooser as unavailable and verify that the call still uses the platform/default SIM
  rather than treating the call itself as failed.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-E5-01` — SIM chooser; `E-E5-02` — Recents slot; `E-E5-03` — permission state.
- **Recovery if it fails:** Set the default calling SIM in Android settings, retry one call per
  SIM, and record whether the failure is SIM selection or only the recorded slot metadata.

## F. Reboot, background survival, and OEM restrictions

### F1 — Reboot and call before opening the app

- **Purpose:** verify Telecom can bind the InCallService after a reboot without the user launching
  Nova Dialer first.
- **Prerequisites:** P1 + P2; default role; app has been opened at least once; device lock known.
- **Steps:** Reboot the phone. Do not open Nova Dialer. Call P1 from P2 and answer/decline. Then
  place an outgoing call.
- **Expected:** Incoming and outgoing calls work after reboot without manually launching Nova
  Dialer. The call UI appears and the call can be controlled.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-F1-01` — post-reboot incoming call.
- **Recovery if it fails:** Open Nova Dialer once and retry. Record whether the role survived the
  reboot and whether the OEM blocks auto-start/Telecom binding.

### F2 — App swiped away from Recents

- **Purpose:** verify receiving calls when the task is removed.
- **Prerequisites:** P1 + P2; default role; app opened, then swiped out of Recents.
- **Steps:** Swipe Nova Dialer out of Recents. Call P1 from P2. Answer/decline.
- **Expected:** The call still arrives with the call UI/notification; the InCallService is not
  killed merely because the task was removed.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-F2-01` — incoming after swipe-away.
- **Recovery if it fails:** Reopen the app, confirm the role, and retry. Record the OEM and whether
  the app appears in a "recently killed" list.

### F3 — Battery optimisation / background restriction

- **Purpose:** document OEM background-kill behavior and any required user allowlist.
- **Prerequisites:** P1 + P2 + OEM; default role; ability to change battery settings.
- **Steps:** Set Nova Dialer to **Restricted** or the OEM's equivalent. Lock the phone and call from
  P2. Record the delay, missed call, or fallback. Restore the setting and retry.
- **Expected:** The app either delivers the call within a reasonable time or documents the OEM
  restriction. No crash or silent data loss. A delayed/missed call under an explicit restriction
  must be recorded as an observed limitation, not marked pass.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-F3-01` — battery setting; `E-F3-02` — observed call timing.
- **Recovery if it fails:** Restore **Optimised/Unrestricted** and retry. If the OEM requires an
  allowlist, record the exact menu path; do not claim it is unnecessary.

### F4 — Repeat on a second OEM device

- **Purpose:** ensure F1–F3 are not Pixel-only behaviour.
- **Prerequisites:** Samsung/Xiaomi/other OEM device with a SIM and a second phone.
- **Steps:** Repeat F1–F3 on the OEM device. Record OEM-specific prompts and battery menus.
- **Expected:** Same functional result as F1–F3, with any OEM-specific restriction documented.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-F4-01` — OEM model/build and results.
- **Recovery if it fails:** Revert OEM-specific changes and record the exact model/software
  version. A failure on one OEM does not prove the app is broken on all devices, but it blocks a
  broad "works everywhere" claim.

## G. Upgrade, encrypted database, and backup/restore

### G1 — Upgrade from the intended previous build

- **Purpose:** verify an in-place upgrade preserves user data.
- **Prerequisites:** `OLD` — the **intended previous Nova Dialer build/version** with the same
  application ID and signing lineage. **This artifact/version is not identified in the repository.**
  If the developer cannot supply it, this test is blocked and must remain NOT RUN.
- **Steps:**
  1. Install `OLD` on the test phone and create synthetic data: a test contact, a call, a note, a
     speed dial, a blocked test number, and a setting.
  2. Record the old version and confirm Nova Dialer is the default Phone app.
  3. Upgrade to the current 2.1.1 candidate using the same signing key (for example, `adb install
     -r` or an internal-testing track), without clearing data.
  4. Open the app and inspect contacts, Recents, notes, blocked numbers, settings, and any legacy
     reminder rows.
- **Expected:** Upgrade completes without a signature mismatch or database-reset prompt. Existing
  data survives. No deferred feature becomes reachable. The database remains encrypted and opens
  without an extra password.
- **Actual result:** _Not recorded. Previous build/version: ______________________._
- **Status:** `NOT RUN` — blocked until the intended previous build is identified.
- **Evidence:** `E-G1-01` — old version; `E-G1-02` — post-upgrade data; `E-G1-03` — upgrade
  log/error if any.
- **Recovery if it fails:** Do not uninstall or clear data. Copy the error and, if possible, the
  app's backup. Reinstall the old build only if you have a known-good path and a backup; otherwise
  keep the device unchanged for diagnosis.

### G2 — Reopen the encrypted database

- **Purpose:** verify the encrypted database reopens without prompting or data loss.
- **Prerequisites:** P1; app used at least once with data; device rebooted or app force-stopped.
- **Steps:** Force-stop Nova Dialer, reopen it, browse contacts/Recents/settings. Reboot the phone
  and repeat.
- **Expected:** The database opens without a password prompt or reset. Existing data is visible.
  No plaintext database or fallback file is created.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G2-01` — data after force-stop; `E-G2-02` — data after reboot.
- **Recovery if it fails:** Do not clear data. Capture the error and stop. A database-open failure
  is a release blocker until diagnosed on a disposable test device.

### G3 — Keystore key unavailable (fail-closed test)

- **Purpose:** verify the app fails closed if the database key cannot be unwrapped.
- **Prerequisites:** **Disposable test device/profile only**; no irreplaceable data; a test build or
  method for simulating key unavailability; current app data backed up by an approved test method.
- **Steps:** Simulate the key being unavailable (for example, restore app data without the Keystore
  key, or use a test build hook). Open the app. Do not attempt to "fix" it by deleting files.
- **Expected:** The app reports that the local data cannot be opened and leaves the database and
  key files untouched. It must not create a plaintext fallback, silently reset the database, or
  overwrite the encrypted file.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G3-01` — fail-closed message; `E-G3-02` — file state before/after (no secrets).
- **Recovery if it fails:** Stop using the test device; do not delete the database. Reinstall the
  known-good build and restore from a valid encrypted backup if one exists. Report the path that
  created a fallback/reset.

### G4 — Export and restore an encrypted backup

- **Purpose:** verify the user-initiated encrypted backup round-trips the supported data.
- **Prerequisites:** P1; synthetic test data (contact, call, note, speed dial, blocked number,
  setting); a document provider; a backup password of at least eight characters; a clean install
  target.
- **Steps:**
  1. Export a backup and save it to a local document provider. Record the password only in a
     password manager/test note, not in this file.
  2. Uninstall Nova Dialer, reinstall the same build, and restore the backup.
  3. Inspect every data category. Repeat the restore with the same file.
- **Expected:** The backup is created with the `NOVABACKUP1` encrypted format. Restore succeeds
  with the correct password, preserves the supported data, and merges without duplicates when the
  same file is restored twice. No data is sent off the device by the app.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G4-01` — export success; `E-G4-02` — restored data; `E-G4-03` — second-restore
  duplicate check.
- **Recovery if it fails:** Keep the original backup file and the current app data. Do not retry
  with a different password or a modified file. If restore partially applies, stop and report it;
  restore the original backup only after recording the failure.

### G5 — Duplicate restore and completed reminder state

- **Purpose:** verify restoring the same backup twice does not duplicate rows or reopen completed
  reminders.
- **Prerequisites:** G4; a backup containing at least one completed legacy reminder row (if
  available).
- **Steps:** Restore the same file twice. Compare counts of contacts, calls, notes, speed dials,
  blocked numbers, quick responses, and reminders.
- **Expected:** No duplicates are created. A completed reminder stays completed. The merge is
  deterministic and does not reset user data.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G5-01` — before/after counts; `E-G5-02` — reminder state.
- **Recovery if it fails:** Stop; do not run a third restore. Export the current data and report
  the duplicate category. Keep the backup file.

### G6 — Wrong password and damaged backup

- **Purpose:** verify bad input is rejected without modifying existing data.
- **Prerequisites:** G4 backup; a copy of the file; a way to truncate/modify the copy.
- **Steps:** Restore with a wrong password. Restore a truncated/corrupted copy. Restore a valid
  backup afterwards.
- **Expected:** Both invalid inputs are rejected with a clear explanation. Existing data is
  unchanged. A later valid restore still works. No plaintext/debug dump is produced.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G6-01` — wrong-password error; `E-G6-02` — damaged-file error; `E-G6-03` —
  post-error data.
- **Recovery if it fails:** Do not overwrite the valid backup. Copy the damaged file for diagnosis
  (it contains encrypted data, not plaintext) and restore from the known-good backup.

### G7 — Legacy plaintext JSON backup

- **Purpose:** verify old plaintext backups are imported only with an explicit warning.
- **Prerequisites:** a legacy plaintext backup fixture; a clean test install. **The fixture is not
  supplied in the repository.**
- **Steps:** Import the plaintext fixture. Read the warning before confirming. Check the imported
  data.
- **Expected:** Nova Dialer detects the plaintext format, warns that it is not encrypted, and does
  not describe it as encrypted. Only after confirmation does it import. The app does not create a
  plaintext copy of the current encrypted database.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN` — fixture and exact legacy format must be supplied.
- **Evidence:** `E-G7-01` — warning; `E-G7-02` — imported test data.
- **Recovery if it fails:** Cancel the import and keep the current data. Do not use a real
  plaintext backup for diagnosis; create a synthetic fixture.

### G8 — Export/share through the document picker and FileProvider

- **Purpose:** verify vCard/backup sharing exposes only the chosen file.
- **Prerequisites:** P1; test contact and test backup; a file manager and a receiving app.
- **Steps:** Export a vCard and a backup through the document picker. Share one file via the
  Android share sheet. In the receiving app, verify what it can read.
- **Expected:** The user chooses the destination. The receiving app can read only the shared file,
  not Nova Dialer's private database or unrelated files. No path traversal or broad directory
  grant occurs.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G8-01` — picker; `E-G8-02` — receiving app's accessible file list.
- **Recovery if it fails:** Revoke the share, delete the test copy, and report the exact receiving
  app. Do not share a real contact/backup while investigating.

### G9 — Automatic Android backup exclusion

- **Purpose:** verify the encrypted database and legacy recordings are not included in Android
  automatic backup/device transfer.
- **Prerequisites:** P1; app with test data; access to Android backup/restore settings (or a test
  device where backups can be inspected safely).
- **Steps:** Confirm `android:allowBackup="false"` in App info if exposed. Trigger/observe an
  automatic backup or device-transfer preview if the OEM allows it. Check that Nova Dialer's
  database and `CallRecordings` are absent.
- **Expected:** Automatic cloud backup and device-to-device transfer do not contain the encrypted
  database, master-key preferences, or legacy recordings. The app's manual encrypted export is the
  supported transfer path.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-G9-01` — backup state; `E-G9-02` — exclusion evidence without secrets.
- **Recovery if it fails:** Stop; do not delete the backup. Record the OEM/Android path and report
  any sensitive file that was included.

## H. Deferred-feature absence (ordinary user flow)

> These are **release-scope** checks, not a request to remove the retained data classes. The app
> must not expose a working entry point for a deferred feature.

### H1 — No recording controls during a call

- **Purpose:** confirm call recording is not reachable.
- **Prerequisites:** P1 + P2; active call; all in-call menus opened.
- **Steps:** During an active call, inspect every visible button and overflow action. Look for a
  record/mic/recording control. Check the notification actions too.
- **Expected:** No record button, recording prompt, or recording-disclosure activity appears. No
  `RECORD_AUDIO` permission prompt is triggered. The in-call UI is limited to the shipped call
  controls.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H1-01` — in-call controls; `E-H1-02` — notification actions.
- **Recovery if it fails:** Do not record a real call. End the call, note the exact entry point,
  and report it as a release-scope regression.

### H2 — No deferred settings entries

- **Purpose:** confirm deferred settings are absent from every reachable Settings category and
  search result.
- **Prerequisites:** P1; app open; Settings and Settings search.
- **Steps:** Open every Settings category. Search for `record`, `SOS`, `spam`, `reminder`,
  `duplicate`, `screening`, `announce`, `island`, and `widget`. Open any result.
- **Expected:** No Call Recording, Callback Reminders, Focus & Protection, Caller Announcer, Spam
  Database, Duplicate Finder, Dynamic Island, SOS, or widget entry is reachable. A search result
  must not open a blank/removed screen or crash. Block/unblock remains available.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H2-01` — settings categories; `E-H2-02` — search results.
- **Recovery if it fails:** Record the exact path and whether it is a visible card or only a search
  result. Do not navigate deeper into a deferred flow on a real call.

### H3 — No callback-reminder action on a call detail

- **Purpose:** confirm the reminder entry point is gone.
- **Prerequisites:** P1; at least one test call in Recents; app lock disabled or known.
- **Steps:** Open a call's detail screen and inspect every action/card.
- **Expected:** No "Schedule callback reminder" or equivalent card/action appears. No reminder
  alarm prompt or permission request is triggered.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H3-01` — call detail screen.
- **Recovery if it fails:** Record the exact card and whether tapping it crashes. Do not schedule a
  real reminder.

### H4 — No spam-labelling action; block/unblock remains

- **Purpose:** confirm deferred spam labelling is absent while core blocking works.
- **Prerequisites:** P1; a test recent call; a test number to block.
- **Steps:** Long-press a recent call and open its actions. Look for "Mark as spam." Then block and
  unblock the test number using the shipped controls.
- **Expected:** No spam-labelling action appears. Block/unblock works for the test number. No
  crash or removed-screen navigation.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H4-01` — recent-call actions; `E-H4-02` — block/unblock result.
- **Recovery if it fails:** Unblock the test number, record the path, and report whether the
  failure is the deferred action or core blocking.

### H5 — No duplicate-contact prompt or scan

- **Purpose:** confirm the duplicate finder is not exposed.
- **Prerequisites:** P1; test contacts, including two with similar names if safe.
- **Steps:** Launch the app and browse contacts for at least 10 seconds. Search settings for
  "duplicate." Look for a startup prompt/card.
- **Expected:** No duplicate-contact prompt, scan result, or merge card appears. Contacts still
  load normally.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H5-01` — contacts screen after launch.
- **Recovery if it fails:** Do not merge/delete contacts. Record the prompt and stop.

### H6 — No home-screen widget

- **Purpose:** confirm no widget provider is exposed.
- **Prerequisites:** P1; home-screen widget picker.
- **Steps:** Open the widget picker and search for Nova Dialer.
- **Expected:** No Nova Dialer widget is listed. The packaged widget resources, if present in the
  bundle, are not a working provider and must not appear as a feature.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H6-01` — widget picker.
- **Recovery if it fails:** Remove the widget, record the provider entry, and report it as a
  release-scope regression.

### H7 — No overlay or call-screening access request

- **Purpose:** confirm the deferred overlay/screening roles are not requested.
- **Prerequisites:** P1; Android Special app access; Settings.
- **Steps:** Check **Special app access → Display over other apps** and the call-screening role.
  Open every reachable Settings screen and look for a request.
- **Expected:** Nova Dialer does not request overlay access or the call-screening role in a
  reachable flow. No `SYSTEM_ALERT_WINDOW` prompt appears.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H7-01` — special-access list; `E-H7-02` — settings search.
- **Recovery if it fails:** Deny the access, record the exact entry point, and stop. Do not grant
  it to continue testing.

### H8 — App lock uses the device credential

- **Purpose:** verify the shipped app lock does not introduce a biometric-only permission or a
  separate bypass.
- **Prerequisites:** P1; device screen lock set; app lock available in Settings.
- **Steps:** Enable app lock. Background the app and reopen it. Try the device credential. Check
  App info for a biometric permission.
- **Expected:** The device screen-lock credential is required to reopen the app. No separate
  biometric-only prompt or `USE_BIOMETRIC` permission is introduced. An incoming call still gets
  the lock-screen call surface without requiring the app lock first.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-H8-01` — app-lock prompt; `E-H8-02` — permission list.
- **Recovery if it fails:** Disable app lock from the device/app settings if possible; if locked
  out, follow the app's documented recovery. Do not clear data.

### H9 — Legacy recording rows are not surfaced

- **Purpose:** confirm old recording metadata/files do not create a reachable playback or
  management feature.
- **Prerequisites:** P1 with legacy Nova Dialer data, if available; otherwise record `N/A` and do
  not fabricate data.
- **Steps:** Open Recents, call details, and Settings. Look for recording playback, file
  management, or a recording disclosure. Inspect the backup/restore UI for a recording category.
- **Expected:** No recording playback/management entry point is visible. Legacy rows may remain in
  the encrypted database for compatibility, but the current build does not surface them. No
  microphone permission is requested.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN` — requires a device with legacy data or a supplied fixture.
- **Evidence:** `E-H9-01` — Recents/details; `E-H9-02` — settings search.
- **Recovery if it fails:** Do not delete or play a real recording. Record the path and stop.

## I. Privacy and store-facing checks

### I1 — Inspect the installed artifact's manifest/permissions

- **Purpose:** verify the shipped package, components, and permissions match the release report.
- **Prerequisites:** ART; Android Studio/`apkanalyzer`/Play pre-launch report or a manifest dump.
- **Steps:** Dump the release manifest. Verify package/version, permissions, activities, services,
  and providers. Check for `INTERNET`, `RECORD_AUDIO`, location, SMS, overlay, and biometric
  permissions.
- **Expected:** Package `com.aistudio.novadial.kpxm`, version 2.1.1 (5), minSdk 24, targetSdk 36.
  Components include `MainActivity`, `IncomingCallActivity`, `MyInCallService`, AndroidX
  `FileProvider`, `MultiInstanceInvalidationService`, `InitializationProvider`, and
  `ProfileInstallReceiver`. Permissions are contacts, phone, call log, phone state, audio settings,
  vibrate, wake lock, notifications, full-screen intent, and the generated dynamic-receiver
  permission. `FOREGROUND_SERVICE` must be absent. No `INTERNET`, `RECORD_AUDIO`, location, SMS,
  overlay, or biometric permission; no deferred reminder/screening/widget provider.
- **Actual result:** _Not recorded. Artifact hash: ______________________._
- **Status:** `NOT RUN` — repeat against the exact artifact under test.
- **Evidence:** `E-I1-01` — manifest/permission dump; `E-I1-02` — component list.
- **Recovery if it fails:** Do not upload. Compare against `app/src/main/AndroidManifest.xml` and
  the merged manifest; report any extra permission/component before changing anything.

### I2 — In-app privacy text matches the policy

- **Purpose:** ensure the user-facing privacy text does not promise deferred features or make
  unsupported security claims.
- **Prerequisites:** P1; Privacy/About screen.
- **Steps:** Open the in-app privacy dialog and compare it with `PRIVACY.md`. Look specifically for
  recording, SOS/location, microphone, or "100% secure" claims.
- **Expected:** The in-app text describes contacts, calls/history, local encrypted storage, backup,
  and the absence of internet access. It does not advertise recording, SOS, overlay/Dynamic Island,
  screening, announcements, duplicate finding, reminders, or widgets. It does not make an absolute
  security guarantee.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-I2-01` — in-app privacy screen.
- **Recovery if it fails:** Record the exact misleading sentence; do not edit production strings
  during a device test.

### I3 — Public privacy-policy URL

- **Purpose:** ensure Play Console has a live, public HTTPS policy, not a 404.
- **Prerequisites:** Browser; current publication state from `docs/PRIVACY_HOSTING.md`.
- **Steps:** Open `https://samuvel784.github.io/NovaDialer/` over HTTPS without signing in. Check
  the heading, date, and sections. Do not enter any URL into Play Console until it returns HTTP
  200 and matches `PRIVACY.md`.
- **Expected:** The policy page is live and readable over HTTPS, dated 21 October 2026, and
  complete. **Current verified state: 404 — NOT PUBLISHED.** This test cannot pass until an
  authorized person enables GitHub Pages and pushes the policy branch.
- **Actual result:** _Not recorded. Current audit: 404, Pages API 404, no `gh-pages` remote branch._
- **Status:** `NOT RUN` — blocked by hosting, not by the app.
- **Evidence:** `E-I3-01` — HTTP status/heading once published. Do not save a 404 as a policy URL.
- **Recovery if it fails:** Follow `docs/PRIVACY_HOSTING.md`; restore the last known-good page if a
  published policy later regresses.

### I4 — AAB signature and Play identity

- **Purpose:** confirm the artifact is signed by the intended upload key and matches the Play
  Console identity.
- **Prerequisites:** ART/AAB; `jarsigner`, `keytool`, `bundletool`; Play Console access for the
  identity comparison.
- **Steps:** Verify the AAB signature, print the signer certificate SHA-256, validate the bundle,
  and compare the package/version with Play Console.
- **Expected:** `jarsigner -verify` exits 0, `bundletool validate` exits 0, signer SHA-256 matches
  the upload certificate, and package/version match the Play Console app. The certificate is the
  expected self-signed upload certificate unless Play App Signing is configured.
- **Actual result:** _Not recorded. Automated preflight on 25 October 2026: `apksigner` verified the
  APK with v2; `jarsigner -verify` returned exit 0 and reported `jar verified` for
  the AAB, with self-signed/no-timestamp and JarFile/JarInputStream consistency warnings. The
  packaged manifest and AAB manifest contain no `INTERNET` permission and report package
  `com.aistudio.novadial.kpxm`, versionName `2.1.1`, versionCode `5`, minSdk `24`, targetSdk `36`,
  and the expected permissions/components; the Play Console identity was not checked. Signer
  SHA-256 `AF:42:B7:27:BA:4A:94:CF:C3:21:C5:B7:BE:2F:00:82:66:D9:3F:92:3B:75:99:0F:0E:9E:70:17:6D:5A:4B:40`;
  AAB SHA-256 `E1B666D7E94AF27662E391A75A5742FD528A166C56BE4C3726B6609BF0187B26`._
- **Status:** `NOT RUN` — repeat before any upload; Play Console check remains outstanding.
- **Evidence:** `E-I4-01` — verification transcript; `E-I4-02` — Play Console identity.
- **Recovery if it fails:** Do not upload. Rebuild only from the verified source and signing
  configuration; never regenerate or overwrite the signing key to "fix" a mismatch.

## J. Accessibility, RTL, and large fonts

### J1 — TalkBack navigation of the dialer and call UI

- **Purpose:** verify screen-reader labels, focus order, and activation of core controls.
- **Prerequisites:** P1; TalkBack enabled; test call; contacts with test names.
- **Steps:** With TalkBack, open the dialpad and activate several digits. Navigate to Contacts,
  Recents, the call button, and an incoming/active call. Use the notification actions with
  TalkBack. Listen for labels.
- **Expected:** Every interactive control has a meaningful label; digits are announced; the call
  button and answer/decline/mute/speaker/hold controls are reachable and activatable; focus order
  is logical; no focus trap or unlabeled icon-only control blocks the flow.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-J1-01` — screen-reader focus order; `E-J1-02` — call controls.
- **Recovery if it fails:** Turn TalkBack off, record the exact screen/control, and continue the
  remaining tests. Do not change production accessibility labels during a device test.

### J2 — Largest font size and display scaling

- **Purpose:** verify layout remains usable with large text and display size.
- **Prerequisites:** P1; **Settings → Display → Font size** largest; optionally display size
  largest; test data.
- **Steps:** Open the dialpad, Contacts, Recents, Settings, and an active-call screen. Scroll every
  page. Check buttons, labels, and dialogs for clipping/overlap.
- **Expected:** Text is readable and not clipped; controls remain reachable by scrolling; no
  overlapping labels; dialogs remain dismissible; the call screen's critical controls are visible.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-J2-01`–`E-J2-04` — each surface.
- **Recovery if it fails:** Restore normal font/display size, record the affected screen and
  Android version, and continue.

### J3 — RTL layout with Arabic

- **Purpose:** verify mirrored layout and text for a supported RTL locale.
- **Prerequisites:** P1; system language Arabic (or Developer options → **Force RTL layout
  direction**); test contacts.
- **Steps:** Switch to Arabic/force RTL. Open dialpad, Contacts, Recents, Settings, and call UI.
  Check back arrows, padding, text alignment, and dialpad layout.
- **Expected:** Layout mirrors correctly; text is right-aligned where appropriate; dialpad digits
  remain correctly ordered; no clipped labels or overlapping controls; navigation still works.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-J3-01`–`E-J3-03` — each screen.
- **Recovery if it fails:** Restore the original language and layout direction, record the screen,
  and note whether the failure is clipping or incorrect mirroring.

### J4 — Dark/AMOLED theme and contrast

- **Purpose:** verify the call UI and critical text remain legible in dark and AMOLED themes.
- **Prerequisites:** P1; app theme set to Dark and, if available, AMOLED; incoming/active call.
- **Steps:** Switch themes. Inspect dialpad, Recents, settings, and the call screen. Check muted
  text, buttons, and notification icons.
- **Expected:** Critical labels and controls are legible; no invisible text/buttons; the call screen
  remains usable; icons remain distinguishable.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-J4-01` / `E-J4-02` — dark and AMOLED screens.
- **Recovery if it fails:** Restore the default theme, record the affected screen, and do not
  change colors during the test.

## K. Contacts and call history

### K1 — Contacts load, search, and caller identification

- **Purpose:** verify contacts are read from Android and used for caller ID.
- **Prerequisites:** P1; READ_CONTACTS granted; at least two test contacts with distinct names and
  numbers; P2.
- **Steps:** Open Contacts; search by name and number; call a test contact; call P1 from a test
  contact's number. Check the displayed name.
- **Expected:** Test contacts appear without sample/fake data. Search returns the right contact.
  Outgoing calls show the contact name; incoming calls show the matching name (or number when the
  contact is unknown). No private contact is uploaded.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-K1-01` — contact list/search; `E-K1-02` — incoming caller ID.
- **Recovery if it fails:** Check READ_CONTACTS and the Android account sync state. Try a phone
  number search before concluding caller ID is broken. Do not edit the contact yet.

### K2 — Add or edit a contact

- **Purpose:** verify contact writing works and is explicit.
- **Prerequisites:** P1; READ/WRITE_CONTACTS granted; a synthetic test contact.
- **Steps:** Create a test contact in Nova Dialer; add a phone number; edit the name; verify the
  result in the app and in Android Contacts; delete the test contact afterwards.
- **Expected:** The app writes the change to Android's Contacts Provider only after the user
  confirms. The change appears in both places; invalid input is rejected; no duplicate contact is
  created by a single save.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-K2-01` — editor; `E-K2-02` — Android Contacts result.
- **Recovery if it fails:** Delete only the synthetic test contact; record whether the failure is
  the editor, the provider write, or a duplicate. Do not test with a real contact.

### K3 — Recents and system call log

- **Purpose:** verify call history is read/updated without corrupting the system log.
- **Prerequisites:** P1 + P2; default role; READ/WRITE_CALL_LOG granted; one incoming, one
  outgoing, and one missed test call.
- **Steps:** Open Recents. Compare each entry's direction, time, and duration with the system call
  log. Place/answer/miss another call and refresh.
- **Expected:** Recents matches the test calls; directions and timestamps are correct; tapping an
  entry opens details/callback. The app does not duplicate or delete unrelated system call-log
  entries.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-K3-01` — Recents; `E-K3-02` — system call log comparison.
- **Recovery if it fails:** Check Call Log permission and the default role. Do not delete the
  system call log to "reset" the test; record the mismatch.

### K4 — Block and unblock a test number

- **Purpose:** verify core call blocking still works without the deferred spam database.
- **Prerequisites:** P1 + P2; a test number that can call P1; Call Log/phone permissions.
- **Steps:** Block the test number from Recents/details. Call P1 from that number. Unblock it and
  call again.
- **Expected:** The blocked call is rejected/blocked according to Android/Telecom behavior; the
  UI reflects the block state. Unblocking restores the call. No spam-labelling feature appears.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-K4-01` — blocked state; `E-K4-02` — post-unblock call.
- **Recovery if it fails:** Unblock the test number, check the system blocklist, and record whether
  the failure is app UI or carrier/Telecom enforcement.

### K5 — Voicemail entry, if the carrier supports it

- **Purpose:** verify the carrier-dependent voicemail entry without claiming universal support.
- **Prerequisites:** P1; carrier visual-voicemail support; voicemail configured; a test message if
  possible.
- **Steps:** Open Recents → Voicemail (if shown). Play a test message. Return to Recents.
- **Expected:** If the carrier exposes visual voicemail, the entry opens and plays only the test
  message; no crash. If the carrier does not support it, the entry is absent or clearly
  unavailable and the test is recorded `N/A`.
- **Actual result:** _Not recorded._
- **Status:** `NOT RUN`
- **Evidence:** `E-K5-01` — voicemail entry; `E-K5-02` — carrier support state.
- **Recovery if it fails:** Do not delete the voicemail. Record the carrier and whether the failure
  is missing support or playback.

## Special-equipment runs

These are the same tests grouped by the extra equipment or service they need. Run the individual
cards; do not create a second set of results.

| Run | Equipment / service | Required test IDs | Notes |
|---|---|---|---|
| S1 | P1 + P2 | B1–B7, C1–C5, C7, F1–F2, K1, K3 | The minimum physical-device run. |
| S2 | Dual-SIM phone + second SIM/third party + carrier call waiting/conference | D1–D5, E5 | Record whether call waiting/conference is provisioned. |
| S3 | Bluetooth headset and wired headset | E1–E4 | Run each route separately; record models. |
| S4 | Samsung/Xiaomi/Pixel-class OEM | F3–F4, plus a repeat of B2/B3 | Record exact battery/auto-start menus. |
| S5 | Android 7/8 (API 24–27) | A1–A5, B1–B3, C1–C3, G2, K1–K3 | Android 14+ FSI cards are `N/A`; record that instead of skipping. |
| S6 | Intended previous build `OLD` | G1 | **Blocked until the previous version/artifact is identified.** |
| S7 | Accessibility/RTL/large-font setup | J1–J4 | Use TalkBack, largest font, and Arabic/forced RTL. |
| S8 | Documents provider + receiving app | G8 | Verify only the selected file is exposed. |
| S9 | Legacy recording/data fixture | H9, G7 | Do not use real recordings; a synthetic fixture is required. |

## Result log and sign-off

- A result is complete only when the **Actual result**, **Status**, and **Evidence** fields are
  filled in each card and the session record above is complete.
- All physical-device test cards in this file are currently **NOT RUN**. The automated emulator
  results at the top of this file are not device passes. Do not change the summary to "all passed"
  without a human tester's evidence for every required card.
- Before upload, the release owner must review every FAIL, every `N/A`, and every blocked test
  (especially G1 and I3) and decide whether the build may proceed. A crash-free run is not a
  substitute for the expected behaviour.
