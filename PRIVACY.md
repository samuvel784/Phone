# NovaDial Privacy Policy

**Last updated: 2026-10-19**

NovaDial is a local phone dialer. It is designed to work without an internet connection and
without advertising, analytics, tracking or crash-reporting SDKs.

## 1. Who is responsible

NovaDial is published by the NovaDial project. For privacy questions, use the developer contact
address shown on the Google Play listing. Before publishing, replace this paragraph with the
developer's legal name, postal address and privacy email.

## 2. What NovaDial accesses

NovaDial asks for Android permissions only to provide dialer features:

- **Contacts** — to show names, photos, labels and phone numbers, and to let you create or edit
  contacts.
- **Phone / call state** — to place calls, show the in-call screen and react to call state.
- **Call Log** — to show, create and manage call history. NovaDial requests this access only
  after you make it the default Phone app.
- **SMS** — only for user-initiated quick replies and the opt-in Emergency SOS feature. Quick
  replies are sent directly only while NovaDial is the default Phone app; otherwise the message
  is handed to your SMS app.
- **Microphone** — only while you start a call recording.
- **Location** — only if you enable the optional Emergency SOS location attachment. The location
  is read at the moment the alert is sent.
- **Notifications and full-screen intents** — to show incoming calls and ongoing-call controls.
- **Overlay** — only for the optional floating call capsule.
- **Biometric/device credential** — to unlock the app and the recordings screen. NovaDial never
  receives or stores your biometric data.
- **Sensors** — the accelerometer is used only while the opt-in shake-to-SOS watch is enabled.

## 3. Where data is stored

Contacts mappings, call history, notes, reminders, settings and recordings are stored in
NovaDial's private app storage on your device. Call recordings are encrypted at rest with an
AES-256-GCM key held by the Android Keystore. The encrypted database is protected with
SQLCipher. Android cloud backup is disabled (`allowBackup=false`).

NovaDial does not operate a server and does not upload your data. The app removes the
`INTERNET` permission, so it cannot make network connections.

## 4. When data leaves the device

Data leaves the device only when **you** choose to:

- export a backup (AES-256 encrypted, password required);
- export or share a recording;
- export contacts as a vCard;
- share a call note or other content through Android's share sheet; or
- send an SMS through your carrier or place a call through your carrier.

Those actions are handled by Android and the app you choose. Their privacy policies apply.

## 5. Call recording

Recording laws vary. You are responsible for obtaining any consent required in your
jurisdiction. NovaDial does not record calls automatically unless you enable that setting, and
it shows recording controls while a recording is active. Recordings are encrypted at rest in
private app storage. Exporting or sharing a recording creates a copy outside that protection.

## 6. Emergency SOS

Emergency SOS is opt-in. When triggered, NovaDial may place a call to your chosen emergency
number and send an SMS with your custom message and, if enabled, a location link to the
emergency contacts you selected. This uses your carrier's normal call and SMS services. The
optional shake watch runs as a visible foreground service and can be stopped from its
notification.

## 7. Children

NovaDial is not directed to children under 13 and does not knowingly collect personal
information from children.

## 8. Data retention and deletion

Your data remains on your device until you delete it in the app, clear Android's app storage, or
uninstall NovaDial. Backups and exported files remain wherever you saved them. Because NovaDial
does not receive your data, it cannot delete copies you exported.

## 9. Security

NovaDial uses the Android Keystore, AES-256-GCM, SQLCipher and Android's app sandbox. No method
of storage is perfectly secure. Keep your device lock enabled and protect exported backups and
recordings.

## 10. Changes

If this policy changes, the updated version will be published at the same public URL and the
"Last updated" date will change.

## 11. Contact

Replace this section with the developer's privacy email and, where required, postal address
before publishing on Google Play.
