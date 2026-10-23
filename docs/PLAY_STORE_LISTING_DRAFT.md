# Nova Dialer — Google Play store listing draft

**Status: DRAFT ONLY — not submitted, not approved, and not a claim of Play eligibility.**
This draft describes only functionality that is present in the current simplified 2.1.0 source
and shipped in the local release candidate. Anything marked **MISSING** or **DECISION REQUIRED**
must be supplied by the developer before submission. Do not copy final marketing claims from this
file without checking them against the tested build.

## 1. Store identity

| Field | Proposed value | Status / evidence |
|---|---|---|
| App name | **Nova Dialer** | Matches `app_name` in `strings.xml`; 11 characters, within the usual 30-character app-name limit. |
| Package / application ID | `com.aistudio.novadial.kpxm` | Current manifest and AAB. The README recommends replacing it before the first public release; **decision required** before upload. |
| Version | 2.1.0 (versionCode 4) | Current release candidate. |
| Category | Communication (phone/dialer) | Proposed; confirm in Play Console. |
| Price | Free | README describes the project as free/open source; confirm the Play price and any future monetisation. No billing, ads, analytics, or crash-reporting dependency was found in the source. |
| Contains ads | No | No ad SDK or ad code found in the source. Confirm in Play Console. |
| In-app purchases | None found | No billing dependency found. Confirm before completing the declaration. |
| Requires Android | 7.0 (API 24) or later | `minSdk 24`; `targetSdk 36`. |
| Default Phone role | Optional at first launch, required for full dialer/call-log functionality | Source requests `RoleManager.ROLE_DIALER` (or the legacy default-dialer intent). |
| Account required | No | No account/login path found. |
| Internet permission | **None declared** in the current release artifact | `INTERNET` is explicitly removed in the merged manifest. This is not a claim that the device itself is offline. |

## 2. Store listing text

### App title (proposed)

```text
Nova Dialer
```

### Short description (proposed, 62 characters)

```text
A local-first phone app for calls, contacts, and call history.
```

The wording is deliberately conservative and is 62 characters. Re-check the current Play character
limit before submission.

### Full description (proposed)

```text
Nova Dialer is a local-first phone app for Android. It can be set as your default Phone app and
uses your phone's own contacts, SIM, and call log. There is no account to create and no cloud
service.

CALLING
• Dial numbers on the keypad and place calls through your carrier.
• Answer, decline, mute, switch to speaker, hold, and hang up from the call screen.
• Handle call waiting and multi-call controls when your device and carrier support them.
• See incoming calls on the lock screen, subject to Android's full-screen-notification permission.
• Use the notification actions for incoming and active calls.

CONTACTS AND HISTORY
• Browse, search, and call your Android contacts.
• Add or edit contacts where you have granted contacts permission.
• See incoming, outgoing, and missed calls in Recents after granting the default Phone role and
  call-log permission.
• Block and unblock numbers with the shipped call-blocking controls.
• Add call notes, speed dials, favorites, and quick responses.

PRIVACY AND LOCAL DATA
• Nova Dialer does not declare the Android INTERNET permission and does not include advertising,
  analytics, crash-reporting, or attribution SDKs.
• Contacts, call history, notes, settings, and other app data are kept in a local encrypted
  database. The app does not operate a server and does not send this data to the developer.
• Calls still travel through your mobile carrier, and information leaves the app when you choose
  to share or export a file.

BACKUP AND DATA CONTROL
• Create a manual, password-protected encrypted backup and restore it on a clean install.
• Export and import vCard contacts through Android's document picker.
• Restore merges with existing data and is designed to avoid duplicates when the same backup is
  imported again.
• Automatic Android cloud backup and device-to-device transfer are disabled for the app's private
  database and legacy recording files.

PERSONALISATION
• Light, dark, and AMOLED themes.
• A choice of supported languages and layouts, including right-to-left Arabic.
• Optional app lock using your device screen-lock credential.

REQUIREMENTS
• Android 7.0 or later.
• A device with telephony and a compatible SIM/carrier for calls. The app may install on a device
  without telephony, but calling features depend on the device and carrier.
• The default Phone role is required for full call management and the system call log.
• Full-screen incoming-call UI on Android 14+ depends on the user granting full-screen-notification
  access. If it is denied, the app uses the notification path.

NOT INCLUDED IN THIS RELEASE
• Call recording or microphone recording.
• Emergency SOS or location sharing.
• Overlay/Dynamic Island features.
• Call screening, caller announcements, spam labelling, or duplicate-contact finding.
• Callback reminders or home-screen widgets.

Nova Dialer is open-source software. See the privacy policy for the full data-handling description.
```

**Before using this text:** replace the final sentence with the live policy URL only after the
policy is published. Do not claim "no data leaves the device" without the carrier/export caveats.
Do not add ratings, awards, user counts, "Google-approved," "military-grade," or "100% secure."

### Feature summary for internal review

| Feature | Available in the current scope? | Notes / limits |
|---|---|---|
| Dialpad and carrier calls | Yes, source present | Requires Phone role/permissions for full functionality. |
| Answer, decline, hang up | Yes, source present | Test on a physical device. |
| Mute, speaker, hold | Yes, source present | Route/state depends on Telecom/device. |
| Call waiting, swap, merge/notification transitions | Source present | Carrier and device support required; untested physically. |
| Lock-screen incoming UI | Source present | Android 14+ FSI access; fallback notification. |
| Contacts read/edit | Yes, source present | Contacts permission; edits write to Android Contacts Provider. |
| Recents/system call log | Yes, source present | Default Phone role + call-log permission. |
| Block/unblock | Yes, source present | Uses the shipped blocking path; spam labelling is not shipped. |
| Call notes, speed dial, quick responses | Yes, source present | Stored locally. |
| Encrypted backup/restore | Yes, source present | User-initiated, password-protected; not cloud sync. |
| vCard import/export | Yes, source present | Uses the document picker. |
| Dual-SIM selection | Source present | Device/carrier support required. `READ_PHONE_STATE` is requested only when the device reports two or more modems; denial falls back to the platform/default SIM. Verify on a physical dual-SIM device. |
| Themes, RTL, localization | Source present | Ten locales; RTL layout supported. |
| App lock | Source present | Uses device credential; no biometric permission. |
| Call recording | **No** | Recording UI/start paths removed; no `RECORD_AUDIO`; legacy files are not surfaced. |
| SOS/location | **No** | Removed from the reachable flow and source audit. |
| Overlay/Dynamic Island | **No** | Removed; no reachable feature or overlay permission. |
| Call screening / announcements | **No** | Screening service and announcer removed; no reachable entry point. |
| Duplicate finder | **No** | Scanner, settings entry point and startup scan removed. |
| Callback reminders | **No** | Scheduler, receiver, dialog and settings removed. |
| Home-screen widgets | **No** | Provider, widget activity, layouts and drawables removed. |

## 3. Permission explanations (draft for the listing/privacy review)

These are user-facing explanations, not a substitute for the Play Console permission declarations
or the Data safety form. The exact prompts and timing must be confirmed on a physical device.

| Permission | When it is used | Plain-language explanation | Current source note |
|---|---|---|---|
| `READ_CONTACTS` | When contact features are enabled | Shows your contacts and identifies callers. | Requested in the main permission flow; denial hides contacts but must not crash the app. |
| `WRITE_CONTACTS` | When you add or edit a contact | Saves the change you make to Android's Contacts Provider. | Requested with contacts. |
| `CALL_PHONE` | When you place a call | Lets Nova Dialer place the call you tapped. | Requested in the main permission flow. |
| `READ_CALL_LOG` | After Nova Dialer holds the default Phone role and you enable Recents | Shows incoming, outgoing, and missed calls. | Deliberately requested **after** the Phone role. |
| `WRITE_CALL_LOG` | After the default Phone role, when a call is recorded in the system log | Updates the system call log for calls handled by Nova Dialer. | Deliberately requested **after** the Phone role. |
| `READ_PHONE_STATE` | Only when the device reports two or more modems | Helps identify and route a call to the SIM you choose. | Requested with the permission flow on multi-SIM hardware. If denied, the chooser is unavailable and the call uses the platform/default SIM; no crash. |
| `MODIFY_AUDIO_SETTINGS` | During calls and audio-route changes | Switches audio between earpiece, speaker, and connected headsets. | Normal permission needed by the audio route controls. |
| `VIBRATE` | Incoming calls/notifications when the ringer is in vibrate mode | Vibrates for an incoming call. | Normal permission. |
| `WAKE_LOCK` | Incoming-call screen/notification | Wakes the device so an incoming call can be presented. | Normal permission. |
| `POST_NOTIFICATIONS` | Android 13+ when notifications are enabled | Shows incoming-call, ongoing-call, and missed-call notifications. | Requested alongside the main permission flow on API 33+. |
| `USE_FULL_SCREEN_INTENT` | Android 14+ when the user grants full-screen-notification access | Shows the incoming-call screen over the lock screen for a default dialer. | Special app access, not a normal runtime prompt; fallback is the notification. |
| Generated dynamic-receiver permission | Internal AndroidX use | Protects dynamically registered receivers; not a user-facing permission. | Automatically added by AndroidX; not a feature claim. |

**Permissions deliberately absent:** `RECORD_AUDIO`, `INTERNET`, location, SMS
(`SEND_SMS`/`READ_SMS`/`RECEIVE_SMS`), `SYSTEM_ALERT_WINDOW`, and `USE_BIOMETRIC`. The optional
`uses-feature` entries for telephony, microphone, and Bluetooth do not themselves grant access.

## 4. Support and contact fields

| Field | Value / status |
|---|---|
| Developer / publisher name | **Samuvel K** appears in `PRIVACY.md`. The signing certificate organization is **VURKA Studio**. These are not necessarily the same public Play developer identity; the developer must choose and verify the Play Console name. |
| Privacy contact email | `samuvel072@gmail.com` — currently listed in `PRIVACY.md` as the privacy contact. **Confirm it is suitable as the public support email.** |
| Public support email | **MISSING** — no separate support address was found. Do not invent one. |
| Public website | **MISSING** — no public website was found in the source or repository metadata. Do not invent one. |
| Privacy policy URL | **MISSING / NOT PUBLISHED** — `https://samuvel784.github.io/NovaDialer/` currently returns **404**, and the GitHub Pages API returns 404. Do not enter this URL in Play Console until it is live and checked. |
| Source repository | `https://github.com/samuvel784/NovaDialer` — public. It is a source repository, not automatically a support channel; confirm whether issues/discussions should be advertised. |
| Postal address | **DECISION REQUIRED** — `PRIVACY.md` currently says no postal address is published. Check the current Play Console and local legal requirements. |
| Support response expectation | **MISSING** — no published response-time commitment exists. Do not invent one. |

## 5. Required graphic and screenshot inventory

| Asset | Current status | Requirement / notes |
|---|---|---|
| App icon, 512×512 PNG | **Present** — `store/play_store_icon_512.png`, verified 512×512. | Use the existing icon; do not stretch or add unverified text. |
| Feature graphic, 1024×500 | **MISSING** | Must be created from the real app/approved artwork. No fabricated UI or fake rating. |
| Phone screenshots | **MISSING** | Capture from the tested build with synthetic contacts/calls. Use 2 minimum; 4–8 recommended. Suggested screens: dialpad, contacts with test data, active call, Recents with test data. Redact personal data. |
| 7-inch tablet screenshots | **MISSING / OPTIONAL** | Only if the listing targets tablets. Do not fabricate a tablet layout; the app is phone-first and calling depends on telephony. |
| 10-inch tablet screenshots | **MISSING / OPTIONAL** | Same caveat. |
| Promotional video | **MISSING / OPTIONAL** | If supplied, it must show the real app and must not advertise deferred features. |
| App title / short / full description | Draft in this file | Requires final review and character-count check. |
| Privacy policy page | **NOT PUBLISHED** | See `docs/PRIVACY_HOSTING.md`; 404 is not a valid Play privacy URL. |

**Screenshot rules:** use only synthetic `Nova Test` contacts and test numbers; mask any accidental
personal data; do not create mock screenshots of features that are not shipped; do not add fake
stars, downloads, awards, or "Editor's Choice" badges.

## 6. App access / review instructions (proposed)

- No account or login is required. The app opens without credentials.
- The reviewer should grant the default Phone role and the requested contacts/phone/call-log/
  notification permissions to exercise calling and Recents.
- Full-screen incoming-call UI on Android 14+ requires the full-screen-notification special access;
  if the reviewer does not grant it, the fallback notification path should be used.
- Calls require a SIM/carrier and a second phone for incoming-call testing. There is no fake-call
  or demo-call generator in the app; do not claim one.
- App lock is optional. If enabled for review, the reviewer must use the device screen-lock
  credential; do not put a real PIN in the review notes. Leave app lock off for the review build
  unless the review specifically requires it.
- No test account, password, or private data should be placed in this repository.

## 7. Claims checklist — do not use

Do **not** use any of the following in the listing, screenshots, or promotional text unless a later
build and a new review establish them:

- "Record calls," "call recording," "microphone recording," or a record button.
- "SOS," "emergency," "location sharing," or "shake to alert."
- "Dynamic Island," "overlay," "floating call bubble," or "draw over other apps."
- "Call screening," "spam blocking AI," "caller announcement," or "automatic spam detection."
- "Duplicate finder," "merge duplicates automatically," or "contact cleanup."
- "Callback reminders," "remind me later," or "scheduled callbacks."
- "Home-screen widget," "favorites widget," or "quick-call widget."
- "100% secure," "military-grade encryption," "unhackable," "guaranteed private," or "no data can
  ever leave the device." The app has no internet permission and uses local encryption, but calls
  go through the carrier and user-initiated exports leave the app.
- Any star rating, download count, award, certification, "Google-approved," or review quote that
  has not actually been awarded.
- Any support email, website, address, or response-time promise that has not been supplied by the
  developer.

## 8. Pre-submission decisions for the developer

- [ ] Decide whether to keep or replace `com.aistudio.novadial.kpxm` before the first upload. The
      application ID is effectively permanent for the Play listing.
- [ ] Confirm the Play developer name and public support email. The policy currently lists
      `samuvel072@gmail.com`.
- [ ] Decide whether a postal address is required and update `PRIVACY.md` if so.
- [ ] Publish the privacy policy at a public HTTPS URL and verify it returns 200.
- [x] Resolve the `READ_PHONE_STATE` declaration/request mismatch and remove the unused
      `FOREGROUND_SERVICE` declaration. `READ_PHONE_STATE` is now conditional on multi-SIM
      hardware; verify the grant/denial paths on a physical device.
- [ ] Complete the Play Console declarations in `docs/PLAY_CONSOLE_DECLARATIONS.md`.
- [ ] Capture real screenshots and a feature graphic.
- [ ] Run the physical-device matrix; do not mark it passed until every required result is recorded.
- [ ] Rebuild the AAB only if source/resources or build configuration change; if they do, update
      the SHA-256 and rerun the release checks.
