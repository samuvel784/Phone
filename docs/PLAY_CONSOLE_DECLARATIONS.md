# Nova Dialer — Google Play Console declaration worksheet

**Status: PREPARATION ONLY — not submitted to Play Console, not reviewed, and not approved.**
This worksheet separates:

1. **Facts established from the current source/manifest/artifact.**
2. **Information only the developer can provide or decide.**
3. **Policy/eligibility questions that must be checked against the current official Google Play
   documentation before answering.**

Do not copy these notes into Play Console as final answers without checking the live forms. Do not
invent Data safety answers, content-rating answers, contact details, or approvals.

## 0. Official policy references (verified 21 October 2026)

These links are evidence for the questions in this worksheet, not a claim that Nova Dialer is
eligible or approved. The live Play Console forms and the current policy pages remain authoritative.

| Topic | Official Google Play page | Effective status |
|---|---|---|
| Call Log / default Phone handler | https://support.google.com/googleplay/android-developer/answer/10208820 | **Current.** Apps must be the default Phone handler before prompting for Call Log/SMS permissions. |
| Full-screen intent | https://support.google.com/googleplay/android-developer/answer/13392821 | **Current.** For apps targeting Android 14+, default FSI availability is limited to calling/alarm functionality; the declaration/review is still required. |
| Privacy policy | https://support.google.com/googleplay/android-developer/answer/9859455 | **Current.** A privacy policy is required for the store listing and the Data safety form. |
| Data safety | https://support.google.com/googleplay/android-developer/answer/10787469 | **Current.** The form must match the final artifact and the current definitions. |
| Sensitive permissions policy index | https://support.google.com/googleplay/android-developer/answer/9888170 | **Current policy index.** Check the individual permission sections at submission time. |
| Contacts permissions preview | https://support.google.com/googleplay/android-developer/answer/16909972 | **Future preview, not a current requirement for this artifact.** Enforcement is stated to begin **27 January 2027** for apps targeting Android 17+ (API 37+) that request `READ_CONTACTS`; default contacts-handler or exception rules apply. This artifact targets SDK 36. |

The contacts page is a preview: do not describe Nova Dialer as compliant with it, and do not claim
that the current default Phone role makes it a default contacts handler. Re-check the page before
raising `targetSdk` to 37 or later.

## 1. Release identity and submission basics

| Item | Established fact | Developer action / unresolved question |
|---|---|---|
| Application ID | `com.aistudio.novadial.kpxm` in the manifest and current AAB. | Decide whether to keep it or replace it **before the first upload**. The README recommends replacing it before a public release. This worksheet does not change it. |
| Version | 2.1.0, versionCode 4. | Confirm the Play release track and version code. |
| Min/target SDK | minSdk 24; targetSdk 36. | Check the current Play target-API requirement at submission time. |
| Signing | Current AAB is signed by the upload certificate with SHA-256 `AF:42:B7:27:BA:4A:94:CF:C3:21:C5:B7:BE:2F:00:82:66:D9:3F:92:3B:75:99:0F:0E:9E:70:17:6D:5A:4B:40`; `jarsigner` exit 0 and `bundletool validate` exit 0 on 21 Oct 2026. | Decide whether to enroll in Play App Signing. Do not upload or change signing during this handoff. |
| AAB | `app/build/outputs/bundle/release/app-release.aab`, 10,570,437 bytes, SHA-256 `676F996047A82F80C9E7E059ED1D8FEAF7C96C423FAE4E27BBA017D4A01F28F8`. | Re-check the hash before upload. Upload only if the source/resources have not changed, or rebuild and update this record. |
| Account/login | No account or login path found. | Provide reviewer instructions; see §11. |
| Internet permission | Absent from the merged release manifest (`INTERNET` is explicitly removed). | Confirm the final AAB/manifest before answering Data safety. |
| Ads | No ad SDK, ad code, or billing dependency found. | Confirm the Ads declaration. |

## 2. Default Phone role and Call Log permissions

### What the app actually does

- Nova Dialer declares the standard dialer contracts (`DIAL`, `VIEW` for `tel:`, and
  `CALL_BUTTON`) and a Telecom `InCallService` with `BIND_INCALL_SERVICE`.
- It requests the Android default Phone role through `RoleManager.ROLE_DIALER` on Android 10+ and
  the legacy `ACTION_CHANGE_DEFAULT_DIALER` path on older versions.
- It requests `READ_CALL_LOG` and `WRITE_CALL_LOG` **only after** the default Phone role is held.
- Recents uses the system call log and a local encrypted call-history copy. Call-log access is used
  for the core dialer/recents experience, not for advertising or profiling.
- The app does not upload call history; it has no server endpoint and no `INTERNET` permission.

### Source / manifest evidence

- `app/src/main/AndroidManifest.xml`: `READ_CALL_LOG`, `WRITE_CALL_LOG`, `CALL_PHONE`,
  `READ_CONTACTS`, `WRITE_CONTACTS`, the dialer intent filters, and `MyInCallService` with
  `android.telecom.InCallService` + `BIND_INCALL_SERVICE`.
- `app/src/main/java/com/example/ui/MainScreen.kt` (`buildPermissionRequest`): Call Log is added
  only when `includeCallLog` is true; the caller supplies that after the role.
- `app/src/main/java/com/example/util/CallScreenPermissionHelper.kt`: default-dialer role request
  and `ROLE_DIALER` handling.
- `app/src/main/java/com/example/DialerRepository.kt`: the call-log synchronization comment notes
  the default-Phone-role restriction.
- `app/src/main/java/com/example/MyInCallService.kt`: Telecom in-call service and call
  notifications.

### Information still needed from the developer

- [ ] Confirm the app is intended to be the **default Phone handler** and that call log/recents is
      core functionality, not an optional analytics feature.
- [ ] Complete the exact Play Console Call Log permission declaration and default-handler
      declaration for the current form.
- [ ] Decide whether the `READ_CALL_LOG`/`WRITE_CALL_LOG` request timing and user explanation are
      sufficient for policy review. The source requests them after the role, but the reviewer may
      still ask for an in-app rationale.
- [ ] Provide the reviewer with a test path: grant the Phone role, then open Recents and grant Call
      Log.

### Policy / eligibility questions to check against current official Play documentation

- Does the current Call Log permission policy permit this use case as a default Phone handler?
- What exact default-handler declaration and justification text does the current Play Console
  require?
- Are there additional prominent-disclosure or core-functionality requirements for Call Log on the
  current target SDK?
- Does the app need to handle the case where the user grants the Phone role but denies Call Log
  without losing default-handler eligibility?

**Official current policy:** https://support.google.com/googleplay/android-developer/answer/10208820
(Use of SMS or Call Log permission groups). This worksheet does not claim approval or guaranteed
eligibility; only the current Play policy/review can determine acceptance.

## 3. Contacts permissions

### What the app actually does

- Reads contacts for display, search, favorites, caller identification, and contact details.
- Writes contacts only when the user adds or edits a contact; it uses Android's Contacts Provider.
- Stores its own local contact-related data in the encrypted database and includes contacts in the
  user-initiated backup.
- Does not upload contacts or send them to the developer.

### Source / manifest evidence

- `AndroidManifest.xml`: `READ_CONTACTS`, `WRITE_CONTACTS`.
- `DialerRepositorySystemSync.kt` / `DialerRepository.kt`: Contacts Provider reads.
- `ContactEditorScreen.kt` / contact editor paths: contact writes.
- `BackupRestoreManager.kt`: contacts in the backup payload and vCard import/export.

### Information still needed from the developer

- [ ] Complete the Contacts permission declaration, if the current Play Console requests one for
      this app.
- [ ] Confirm the in-app explanation clearly states why write access is needed (contact editing),
      not merely why read access is needed.
- [ ] Decide whether the contact editor is essential enough to justify `WRITE_CONTACTS`; if not,
      remove the feature in a future source change rather than keeping an unnecessary sensitive
      permission. Do not change it in this documentation-only handoff.

### Policy / eligibility questions

- Does the current Contacts permission policy require a specific disclosure or declaration for
  read/write contacts in a dialer?
- Are there restrictions on storing contacts in a local database/backup in addition to reading
  them from the system provider?
- Does the user-initiated vCard export count as "sharing" in the current Data safety definitions?

**Official references:** the current sensitive-permissions policy index is
https://support.google.com/googleplay/android-developer/answer/9888170. The contacts-permissions
page at https://support.google.com/googleplay/android-developer/answer/16909972 is a **future
preview**: enforcement is stated to begin **27 January 2027** for apps targeting Android 17+ (API
37+) that request `READ_CONTACTS`, with default contacts-handler/exception rules. The current
artifact targets SDK 36, so that enforcement is not yet triggered for this artifact. This is not a
compliance claim, and the app is not currently the default contacts handler.

## 4. Phone, SIM, audio, and notification permissions

| Permission / access | Established source fact | Developer action / question |
|---|---|---|
| `CALL_PHONE` | Declared; used for placing calls through the dialer flow. | Confirm the in-app rationale and that calls are user-initiated only. |
| `READ_PHONE_STATE` | Declared and now requested only when the device reports two or more modems. It is used to enumerate call-capable SIM accounts and map call-log slots. If denied or unavailable, `getCallableSims()` returns empty and the call falls back to the platform/default SIM; no crash or wrong-SIM selection is expected. | Verify the multi-SIM request, grant, and denial paths on a physical dual-SIM device. No separate Play Console declaration is known to be required, but confirm the live form. |
| `MODIFY_AUDIO_SETTINGS` | Declared; normal permission used for call audio route control. | No separate Play declaration expected, but confirm the current form. |
| `VIBRATE` | Declared; normal permission for ring vibration. | No separate declaration expected. |
| `WAKE_LOCK` | Declared; normal permission for waking/presenting incoming calls. | No separate declaration expected. |
| `FOREGROUND_SERVICE` | **Not declared** in the source or merged release manifest after the 21 October 2026 audit. No app-managed foreground service, `startForeground()` call, or `foregroundServiceType` was found. `MyInCallService` is Telecom-bound, not an app-created foreground service. | No action; do not add the permission back without a real foreground service. |
| `POST_NOTIFICATIONS` | Runtime permission on Android 13+, requested with the permission flow; used for incoming/ongoing/missed call notifications. | Confirm the notification rationale and test the denial path. |
| `USE_FULL_SCREEN_INTENT` | Declared; used only for the incoming-call screen; user can grant/deny the special access on Android 14+. | Complete the full-screen-intent declaration/questionnaire and confirm current eligibility. |
| Generated dynamic-receiver permission | Automatically added by AndroidX for dynamic receivers. | No user-facing declaration expected; do not advertise it as a feature. |

The conditional `READ_PHONE_STATE` request is in
`app/src/main/java/com/example/ui/MainScreen.kt` (`buildPermissionRequest`), and the callable-SIM
list is keyed on the permission state so it refreshes after the user grants access. A one-time
upgrade prompt covers installs where contacts/call log were already granted. Denial keeps
`getCallableSims()` empty and falls back to the platform/default SIM.

## 5. Full-screen intent (FSI) declaration

### What the app actually does

- On Android 14+, `CallScreenPermissionHelper.canUseFullScreenIntent()` checks
  `NotificationManager.canUseFullScreenIntent()`.
- The incoming-call notification uses the full-screen intent path to launch
  `IncomingCallActivity`; if FSI access is unavailable, the notification remains the fallback.
- `IncomingCallActivity` is `exported=false`, `excludeFromRecents=true`, `showWhenLocked=true`,
  `turnScreenOn=true`, and uses `FLAG_SECURE` in release builds.
- The app does not use FSI for ads, alarms, promotions, or unrelated full-screen content.

### Source / manifest evidence

- `AndroidManifest.xml`: `USE_FULL_SCREEN_INTENT`, `IncomingCallActivity` attributes.
- `MyInCallService.kt`: incoming notification + full-screen intent construction.
- `CallScreenPermissionHelper.kt`: permission check and settings intent.
- `IncomingCallActivity.kt`: lock-screen activity behavior.

### Information still needed from the developer

- [ ] Confirm the app is eligible under the current Play policy for `USE_FULL_SCREEN_INTENT`
      (calling/alarm use cases).
- [ ] Complete the Play Console declaration or questionnaire exactly as shown at submission time.
- [ ] Test both granted and denied paths on Android 14/15/16; the declaration should not claim a
      fallback works until it is tested.
- [ ] Confirm the lock-screen activity does not expose the rest of the app behind the keyguard.

### Policy / eligibility questions

- Is a default Phone/dialer app's incoming-call screen an allowed FSI use under the current policy?
- Does the app need a prominent disclosure or default-handler justification in addition to the
  role?
- What happens to FSI eligibility if the user denies the special access but keeps the Phone role?

**Official current policy:** https://support.google.com/googleplay/android-developer/answer/13392821
(foreground service and full-screen intent requirements). The page states that, for apps targeting
Android 14+, only apps with calling or alarm functionality have this permission enabled by default.
Nova Dialer is intended to qualify as a calling app, but this is not an approval or eligibility
claim; complete the live declaration and test both the granted and denied paths.

**Do not claim that FSI access will be granted or that Play will approve the declaration.**

## 6. Data safety

### Established data-flow facts

- **No developer server:** no app-operated backend, no account system, no remote endpoint, and no
  `INTERNET` permission in the current release artifact.
- **No analytics/ads/crash SDKs:** no advertising, analytics, crash-reporting, or attribution SDK
  was found.
- **Contacts:** names, phone numbers, email/postal addresses and related fields are read from
  Android's Contacts Provider when permission is granted; the app also keeps local contact data
  for its own contacts/backup features.
- **Call history:** phone numbers, call state, duration, SIM/slot information where available, and
  call-log entries are read/updated after the default role and Call Log permission; a local
  encrypted copy is maintained.
- **App data:** call notes, speed dials, quick responses, blocked numbers, settings, and legacy
  reminder rows are stored locally.
- **Legacy recording files:** files created by earlier builds may remain in app-private storage.
  This build does not create, play, manage, or surface them. They are excluded from automatic
  backup by the app's backup rules.
- **Exports:** vCard files and encrypted backup files leave the app only when the user chooses a
  destination through Android's document picker/share sheet.
- **Calls:** call audio and signaling necessarily go through the mobile carrier/Telecom; that is
  not a developer collection path.
- **Diagnostics:** limited local log events; release builds strip `android.util.Log` calls through
  R8, and sensitive values are redacted before logging.

### What the Data safety form may require

The current Play Console form is the authority. Do not simply copy this table into it.

| Data type | Local access/processing | Sent to developer? | Possible Data safety treatment / question |
|---|---|---|---|
| Contacts (name, phone, email, address) | Read from Contacts Provider; local app data and user-initiated backup. | No developer server. | Does Play consider local-only access "collection"? Is a user-initiated vCard export "sharing"? Check the current definitions. |
| Call history (phone number, time, duration, direction, SIM) | Read/updated with default role + Call Log permission; local encrypted copy. | No developer server. | Same collection/sharing definitions; Call Log is a restricted permission. |
| Call notes / settings / blocked numbers / speed dials / quick responses | Local encrypted database. | No. | Include only if the form treats locally stored user data as in scope; follow the current form. |
| Legacy recording files | May remain local from an earlier build; not surfaced by this build. | No. | Decide whether to disclose retained legacy files; do not claim they are deleted or newly recorded. |
| Backups / vCards | User-initiated local files. | No automatic transfer. | User-chosen destination; is that "sharing" or a user-initiated export? Check the current definitions. |
| Diagnostics/logs | Local; release logs stripped. | No. | Confirm whether any crash/diagnostic data is actually collected; the source has no crash-reporting SDK. |

### Information still needed from the developer

- [ ] Complete every mandatory Data safety question in Play Console after reading the current
      definitions.
- [ ] Decide how to describe user-initiated exports and carrier call handling.
- [ ] Decide whether legacy recording files need a retention/deletion statement; the policy says
      they may remain and this build does not surface them.
- [ ] Confirm there is no hidden SDK/telemetry in the final artifact, not just the source tree.
- [ ] Provide the data-deletion path: uninstall deletes app-private data; in-app deletions remove
      supported records; system Contacts/Call Log and exported files are separate.
- [ ] Confirm whether any jurisdiction requires a postal address or additional privacy contact.

**Official current guidance:** https://support.google.com/googleplay/android-developer/answer/10787469
(Data safety) and https://support.google.com/googleplay/android-developer/answer/9859455 (privacy
policy). The Data safety guidance distinguishes on-device-only processing from "collection" and
does not automatically treat a user-initiated export as "sharing"; confirm the live form's current
definitions before answering. A privacy policy is required to complete the form.

**Do not answer "No data collected" or "No data shared" solely from this worksheet.** Verify the
current Play definitions and the final artifact.

## 7. Content rating and target audience

### Established facts

- The app is a general-purpose phone/dialer and call-management app.
- It has no ads, no social feed, no user-to-user content sharing, no gambling, no violence, no
  sexual content, no drugs/alcohol/tobacco content, and no in-app purchases found in the source.
- It handles personal contacts, call history, and call notes locally; it does not publish them.
- It is not designed or marketed for children. `PRIVACY.md` states it is not directed at children.
- The app has no age gate or account system.

### Information still needed from the developer

- [ ] Complete the current content-rating questionnaire truthfully. Do not guess answers.
- [ ] Set the target audience and any "designed for families" status in Play Console. If the app is
      not for children, ensure the listing, screenshots, and privacy policy do not suggest
      otherwise.
- [ ] Confirm whether the communication category triggers any additional review questions.
- [ ] Confirm the app's rating in every target country; ratings are not universal.

### Policy / eligibility questions

- Does the current Play content-rating questionnaire treat private call notes or contacts as
  user-generated content requiring disclosure?
- Is a dialer with a default Phone role subject to additional target-audience or families review?
- Does the app need an age screen? The current source has none; do not add one just to influence
  the rating without a product/legal decision.

## 8. Ads, monetisation, and other declarations

| Declaration / area | Established fact | Developer action |
|---|---|---|
| Contains ads | No ad SDK/code found. | Confirm "No" in Play Console only after checking the final artifact. |
| In-app purchases | No billing dependency found. | Confirm; if monetisation is added later, update the declaration and policy. |
| Payments / financial features | None found. | Confirm not applicable. |
| Health / medical | None found. | Confirm not applicable. |
| Government / politics | None found. | Confirm not applicable. |
| News / COVID / disaster | None found. | Confirm not applicable. |
| Gambling / real-money gaming | None found. | Confirm not applicable. |
| User-generated content / social | No social feed or public content sharing found; contacts/notes are private local data. | Confirm the current definition; vCard/backup export is user-initiated. |
| SMS / call screening / recording | No SMS permission; no `RECORD_AUDIO`; no call-screening service in the merged manifest; no recording UI in the reachable scope. | Confirm the final artifact. Do not declare features that are not shipped. |
| Foreground service | No `FOREGROUND_SERVICE` permission, app-managed foreground service, or `foregroundServiceType` in the source/merged manifest. `MyInCallService` is Telecom-bound. | Do not claim a foreground service. |
| Sensitive permissions not present | No location, SMS, microphone, overlay, or biometric permission in the merged manifest. | Confirm the final artifact and the current Play permission list. |

## 9. Privacy policy

### Current state

- **Policy file:** `PRIVACY.md`, last updated 21 October 2026.
- **Developer:** Samuvel K (individual developer), with privacy email
  `samuvel072@gmail.com`.
- **Coverage:** contacts/call history access, local encrypted storage, backup/export/import,
  logging and diagnostics, legacy recording files, network access/no `INTERNET`, deletion, and
  children's privacy.
- **Absolute guarantees:** the policy explicitly says no software/storage method can guarantee
  absolute security. Do not add stronger claims to the listing.
- **Public URL:** `https://samuvel784.github.io/NovaDialer/` currently returns **404**. The GitHub
  Pages API returns 404, and the remote has only a `main` branch (no `gh-pages` branch).
- **In-app privacy text:** present; compare it with `PRIVACY.md` during device testing.

### Blocker and exact next step

The current blocker is **hosting, not the policy text**:

1. GitHub Pages must be enabled for a public source (the recommended route is a `gh-pages` branch
   containing only the generated policy site).
2. The policy branch must be pushed and Pages must build successfully.
3. The URL must return HTTP 200 over HTTPS and match `PRIVACY.md` before it is entered in Play
   Console.

Do **not** enter the 404 URL as the privacy policy. Do **not** use the raw GitHub file URL as a
substitute. See `docs/PRIVACY_HOSTING.md` for the exact generation/publish/verify steps. The
current Play requirement is documented at
https://support.google.com/googleplay/android-developer/answer/9859455.

### Developer decisions

- [ ] Authorize publication and perform the Pages setup.
- [ ] Confirm the developer name and privacy email.
- [ ] Decide whether a postal address is required and update the policy if so.
- [ ] Confirm the policy's legacy-recording description matches the final build and retention
      behavior.
- [ ] Keep the in-app privacy text and `PRIVACY.md` in sync.

## 10. App access / reviewer instructions

- **No account or login** is required. There is no test username/password to provide.
- **Core UI** can be opened without the default Phone role, but full call management and Recents
  need the role and permissions.
- **Review path:** grant the Phone role → grant contacts/phone/call-log/notifications → open
  Recents → place a call → use a second phone for an incoming call.
- **Android 14+ full-screen intent:** grant the full-screen-notification special access to test
  the lock-screen UI; otherwise test the fallback notification.
- **Calls require a SIM/carrier.** There is no fake-call or demo-call generator. If the review
  device has no telephony, explain which features could not be exercised; do not claim they were
  tested.
- **App lock:** optional. If enabled, the reviewer must use the device screen-lock credential. Do
  not put a real PIN or private data in the review notes.
- **No private data:** do not upload a real contacts database or call history as review material.

## 11. Unresolved questions / submission blockers

- [ ] **Application ID:** keep `com.aistudio.novadial.kpxm` or replace it before first upload.
- [ ] **Public support email:** confirm whether `samuvel072@gmail.com` is the public support address.
- [ ] **Public website:** missing; decide whether to provide one.
- [ ] **Privacy policy URL:** currently 404; hosting must be enabled and verified.
- [ ] **Postal address:** decide whether required; update `PRIVACY.md` if so.
- [ ] **Call Log declaration:** confirm eligibility as a default Phone handler and complete the
      current Play Console declaration.
- [ ] **Full-screen intent:** confirm eligibility and complete the current declaration; test
      granted/denied paths.
- [ ] **Contacts declaration:** confirm the in-app rationale and complete any current declaration.
- [ ] **Contacts policy (future):** the 27 January 2027 preview applies to apps targeting Android
      17+ (API 37+) that request `READ_CONTACTS`; this artifact targets 36 and is not yet covered,
      but a future targetSdk change needs a separate default-handler/exception decision. Do not
      claim compliance.
- [ ] **`READ_PHONE_STATE`:** source now requests it conditionally on multi-SIM hardware and keeps
      a fail-soft denial path; verify the prompt, grant, denial, and SIM-routing behavior on a
      physical dual-SIM device.
- [x] **`FOREGROUND_SERVICE`:** removed from the source/merged manifest; no app-managed foreground
      service exists. Do not add it back without one.
- [ ] **Data safety:** complete the form from the final artifact and current definitions; do not
      guess.
- [ ] **Content rating / target audience:** complete the current questionnaires; do not claim a
      rating in advance.
- [ ] **Ads / IAP:** confirm the final artifact contains none, or update the declarations.
- [ ] **Store assets:** feature graphic and real screenshots are missing.
- [ ] **Physical-device matrix:** all tests are NOT RUN; complete them before calling the build
      publishable.
- [ ] **Upgrade test:** the intended previous build is not identified; supply it or record G1 as
      blocked.
- [ ] **AAB:** do not upload until the identity, privacy URL, declarations, and device tests are
      complete. If source/resources change, rebuild and update the hash.

## 12. Submission gate

The app is **not release-ready** while any of the following remain:

1. A live, matching privacy-policy URL (currently 404).
2. Completed Play Console declarations and Data safety/content-rating answers.
3. A physical-device matrix with recorded results; all cards are currently NOT RUN.
4. Real store screenshots and a feature graphic.
5. A final decision on the application ID, support contact, and any legal address.
6. A final artifact review after any source/resource change (with a new AAB hash, tests, lint, and
   signature verification).

Nothing in this worksheet authorizes uploading, publishing, changing repository settings, or
changing the application ID/signing configuration.
