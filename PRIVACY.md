# Nova Dialer Privacy Policy

**Last updated: 21 October 2026**

## 1. Introduction

Nova Dialer is a phone dialer and call-management application published by **Samuvel K**, an individual developer.

This Privacy Policy explains how Nova Dialer accesses, uses, stores, and shares information when you use its features, including calling, contacts, call history, notifications, and local backup.

Nova Dialer is designed to be **offline-first**. The application does not declare the Android `INTERNET` permission and does not include advertising, analytics, crash-reporting, or attribution SDKs. It has no remote endpoint and no code path that transmits your data to the developer or to a third party. The packaged runtime libraries are open-source components — AndroidX/Jetpack, Kotlin/Coroutines, Room, SQLCipher, Coil (including its OkHttp/Okio transport) and the bundled Inter font; none of them is configured with a Nova Dialer endpoint or telemetry.

**Privacy contact:** samuvel072@gmail.com

## 2. Information Nova Dialer Accesses or Processes

### Contacts

When you grant contact permission and use contact-related features, Nova Dialer reads names, phone numbers, email addresses, postal addresses, and related contact fields made available by Android's Contacts Provider.

Nova Dialer uses this information for contact display, contact management, favorites, caller identification, and related dialer features. When you add or edit a contact, Nova Dialer also writes that information to Android's Contacts Provider. Permission to read and write contacts is required for these features.

### Phone calls and call history

As a dialer application, Nova Dialer processes phone numbers, call state, call duration, SIM slot, and call-history information to provide calling, call management, and related functionality.

With the call-log permission and the default Phone role, Nova Dialer reads and updates the system call log and also maintains its own call-history list inside its private, encrypted database.

### Analytics and diagnostics

Nova Dialer contains **no analytics, telemetry, crash-reporting, or advertising SDKs**, and it does not transmit any data off the device. The Android `INTERNET` permission is explicitly removed from the app manifest, so Android blocks network access. The packaged libraries include Coil's OkHttp/Okio transport for local image decoding, but Nova Dialer supplies only local `content://` image URIs, defines no remote endpoint, and contains no WebView, Firebase, or Google Play Services dependency.

Nova Dialer does not log passwords, backup contents, decrypted data, encryption keys, or full phone numbers. Limited diagnostic events (for example call-timing marks with a masked number, audio-route changes, and non-sensitive error categories) may be written to Android's system log, which is local to the device. Release builds additionally strip these log calls through R8, but the app does not rely on that: sensitive values are redacted before logging.

### Notifications and call interface

With the notification and full-screen-intent permissions, Nova Dialer displays incoming-call notifications, full-screen call interfaces, ongoing-call controls, and missed-call notifications. Notifications may reveal a caller's name or number depending on your notification settings and Android lock-screen configuration. The call UI uses `FLAG_SECURE`, so call details do not appear in the recent-apps screenshot, and the lock-screen call activity is excluded from Recents.

If you enable the app lock, a live call does **not** unlock Nova Dialer. While the app is locked, the call screen exposes only the caller's name or number and the controls needed for that call (answer, decline, mute, speaker, end). The dialer, recents, contacts, notes, recordings, quick responses, and settings remain behind the device screen-lock credential. Nova Dialer does not dismiss the device keyguard or use a call notification token to open the full app.

## 3. How Information Is Used

Nova Dialer processes information only to:

- Provide phone dialing and call management.
- Display and manage contacts and favorites.
- Show and maintain call history.
- Support dialing actions you initiate (speed dial, quick replies).
- Display notifications and call-related interfaces.
- Create or restore a local backup that you initiate.

Nova Dialer does not use your information for advertising, profiling, or any purpose beyond the features described here.

## 4. Storage and Security

Nova Dialer stores its data locally on your device:

- **Database:** contacts, call history, call notes, speed dials, quick responses, blocked and spam numbers, legacy callback-reminder rows, metadata for any call recordings created by earlier builds, and settings are stored in a SQLCipher-encrypted Room database. The database passphrase is random, generated on first use, and protected by an AES key that resides in the Android Keystore.
- **Legacy recording files:** recording files created by an earlier build may remain in app-private storage (for example, `filesDir/CallRecordings` and app-specific external directories). This simplified build does not create, play, manage, or surface them, and it does not delete them. They are excluded from automatic Android backup by the app's backup rules.
- **Backups:** the JSON payload is encrypted and authenticated with AES-256-GCM under a key derived from your password using PBKDF2-HMAC-SHA256 (250,000 iterations). Each backup uses a fresh random salt and IV, and the file carries an authenticated `NOVABACKUP1` header.
- **Copies you choose to export or share:** when you export data (such as vCard contacts or a backup), the resulting file is written wherever you choose and leaves Nova Dialer's private storage. Nova Dialer cannot delete copies once they are outside its private storage.

Automatic Android backup and device-to-device transfer are disabled (`android:allowBackup="false"`, with cloud-backup and device-transfer exclusions). Apart from the files you explicitly export or share, no plaintext copy of your database is created outside app-private storage.

No software or storage method can guarantee absolute security. Protect your device with a screen lock, keep Android updated, and do not share exported backups or their passwords with untrusted people.

## 5. Local Backup and Restore

Nova Dialer's backup is a **manual, local, encrypted export** — not cloud synchronization.

When you export a backup you must set a password of at least eight characters. The file starts with a `NOVABACKUP1` marker followed by the encrypted data. The payload is encrypted and authenticated with AES-256-GCM under a key derived from your password with PBKDF2-HMAC-SHA256 (250,000 iterations). A fresh random salt and IV are generated for every export, and the marker is authenticated, so a modified file is rejected. The password is never stored by the app; if you lose it, the backup cannot be restored.

The backup includes: contacts, call history, call notes, speed dials, quick responses, blocked and spam numbers, legacy callback reminders, and application settings. The file is written wherever you choose using Android's document picker (Storage Access Framework), so you control the destination.

Before anything is written, Nova Dialer validates the file's structure and version and decrypts and parses the whole payload in memory. A wrong password, a damaged file, an unsupported version, or a malformed record is rejected with an explanation and leaves your existing data untouched. The restore is then applied in a single database transaction.

Restoring **merges** the backup with your current data. Records that share a stable key (contacts and call history by id, blocked and spam numbers by number, speed dials by slot, settings by key) overwrite the matching existing record; everything else is kept. Call notes, reminders, and quick responses use generated ids, so Nova Dialer matches them by their exact content when merging: a quick response by its message text, a call note by number, text and save timestamp, and a reminder by number, time and note. Importing the same file more than once therefore does not create duplicates, and a reminder you already completed is never reopened. A restored reminder keeps the completed or pending state recorded in the backup. The only theoretical exception is a call-history entry from a legacy file that lacks ids, which cannot be distinguished from a new record without guesswork; such files were never produced by Nova Dialer, so those rows are preserved rather than deduplicated.

Callback reminders are **deferred in this simplified build**: there is no reminder entry point and no reminder alarm receiver is registered, so no new reminder can be scheduled. The reminder rows described above remain in the backup format and are still merged, so data from an older build (or a backup that contains it) is preserved rather than discarded.

Older backups that were saved as **plaintext JSON** are still accepted for compatibility, but Nova Dialer detects them explicitly and shows a warning that the file is not encrypted before importing it. Nova Dialer never describes a plaintext backup as encrypted.

You are responsible for storing exported backups securely and for protecting the password used to encrypt them.

## 6. Sharing and Disclosure

Nova Dialer does not sell or rent your information and does not operate servers. It transmits no data to the developer.

Information can leave your device through Android and the people you contact:

- **Mobile carrier and call recipients:** when you place a call, or when you hand a pre-filled message to your own messaging app.
- **Android system services:** to manage calls, contacts, notifications, and permissions.
- **The destination you choose:** when you export or share a backup or a vCard copy made by you. Once a file is outside Nova Dialer's private storage, the app can no longer protect or delete it.

## 7. Data Retention and Deletion

Information remains on your device until you delete it. You can delete contacts, call-history entries, call notes, and settings inside Nova Dialer, subject to the relevant permissions.

**Reset App:** When you perform an explicit "Reset App" in Nova Dialer, all app-owned local storage — including the encrypted database, encryption keys, preferences, and private application caches (across internal and app-specific external storage) — is durably and permanently erased. Files that you exported through Android's Storage Access Framework or document picker to public directories (such as Downloads) live outside the app's private directories and are preserved.

Uninstalling Nova Dialer removes its private data (the encrypted database, preferences, and caches). It does **not** remove information stored in Android's Contacts Provider, the system call log, or files you previously exported to public storage. Delete those separately if you wish.

## 8. Permissions and User Choices

Nova Dialer requests permissions only when a supported feature needs them, including permissions for contacts, phone calls, call logs (after you grant the default Phone role), notifications, and full-screen incoming-call intents. Call-log permission is requested only once Nova Dialer holds the default Phone role, as required by Google Play. Nova Dialer does not request microphone, location, overlay, SMS, or biometric permissions.

You can review and change app permissions in Android settings. Revoking a permission may cause the related feature to stop working or operate with reduced functionality. The default Phone role is required for Nova Dialer to manage calls and access the call log.

## 9. Children's Privacy

Nova Dialer is a general-purpose phone and call-management application and is not directed at children. It is not designed for, marketed to, or intended for use by children.

## 10. Changes to This Policy

This Privacy Policy may be updated when Nova Dialer's features, data practices, or legal obligations change. The revised policy will display an updated "Last updated" date.

## 11. Contact

For privacy questions, requests, or concerns about Nova Dialer, contact:

**Developer:** Samuvel K
**Email:** samuvel072@gmail.com

No postal address is published in this policy.
