# NOTICE — Nova Dialer

Nova Dialer (applicationId `com.aistudio.novadial.kpxm`, package namespace `com.example`)
is a real Android default dialer built on the **Secure Dialer** codebase.

- **Upstream:** Secure Dialer — <https://github.com/Secure-Phone-apps/Secure-Dialer>
- **Upstream copyright:** Copyright (C) 2026 MovStore
- **License:** GNU General Public License, version 3.0 or later (GPL-3.0-or-later).
  The full license text is in [`LICENSE`](LICENSE).

Nova Dialer is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version. Nova Dialer is distributed
in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the
implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

## Attribution

Every file derived from Secure Dialer keeps its original
`Copyright (C) 2026 MovStore` / `SPDX-License-Identifier: GPL-3.0-or-later` header
unchanged. Those files were rebranded for Nova Dialer (strings, colours, icon, package
id) but their license and copyright notices were **not** altered.

## Files written for Nova Dialer

The following files are original Nova Dialer work and carry the header
`Copyright (C) 2026 Nova Dialer contributors / SPDX-License-Identifier: GPL-3.0-or-later`.
None of them is copied from Secure Dialer.

### Telecom / protection / announcer
- `app/src/main/java/com/example/telecom/AnnouncerScript.kt`
- `app/src/main/java/com/example/telecom/CallerAnnouncerManager.kt`
- `app/src/main/java/com/example/telecom/CallProtectionPolicy.kt`
- `app/src/main/java/com/example/telecom/CallProtectionPrefs.kt`
- `app/src/main/java/com/example/telecom/CallDecisionStore.kt`
- `app/src/main/java/com/example/telecom/DoNotDisturbPrefs.kt`
- `app/src/main/java/com/example/telecom/NovaDialCallScreeningService.kt`
- `app/src/main/java/com/example/telecom/RingingPolicy.kt`

### Utilities
- `app/src/main/java/com/example/util/ContactRingtonePrefs.kt`
- `app/src/main/java/com/example/util/CallLogExport.kt`
- `app/src/main/java/com/example/util/DuplicateFinder.kt`

### Security
- `app/src/main/java/com/example/security/IntentGuard.kt`
- `app/src/main/java/com/example/WidgetCallActivity.kt`

### UI
- `app/src/main/java/com/example/ui/components/CallAnalyticsSettings.kt`
- `app/src/main/java/com/example/ui/components/ContactRingtonesSettings.kt`
- `app/src/main/java/com/example/ui/components/DuplicateFinderSettings.kt`
- `app/src/main/java/com/example/ui/components/FocusAndProtectionSettings.kt`
- `app/src/main/java/com/example/ui/components/HomeTab.kt`
- `app/src/main/java/com/example/widget/FavoritesWidgetProvider.kt`

### Tests
- `app/src/test/java/com/example/telecom/AnnouncerScriptTest.kt`
- `app/src/test/java/com/example/telecom/CallProtectionPolicyTest.kt`
- `app/src/test/java/com/example/telecom/RingingPolicyTest.kt`
- `app/src/test/java/com/example/security/IntentGuardTest.kt`
- `app/src/test/java/com/example/util/ContactRingtonePrefsTest.kt`

> New files added in future must carry the same
> `Copyright (C) 2026 Nova Dialer contributors` header. If any file listed above is found
> to contain code copied from Secure Dialer, restore the `Copyright (C) 2026 MovStore`
> line in that file.

## No third-party binaries or services

Nova Dialer uses only Android platform APIs and the open-source libraries declared in
`gradle/libs.versions.toml` (AndroidX, Room, SQLCipher, Coil, Robolectric/Roborazzi —
all Apache-2.0 or MIT). There are no ads, no analytics, no proprietary SDKs, no API keys
and **no `INTERNET` permission**.

## Fonts

The app bundles **Inter** (https://github.com/rsms/inter), Copyright (c) The Inter Project Authors, licensed under the
SIL Open Font License 1.1. The four weights in `app/src/main/res/font/` are subsets (Latin, Greek and Cyrillic) of the
original files. The licence text is in `app/src/main/assets/licenses/Inter-OFL.txt`.

## Design system

The premium design system (`AccentPalette.kt`, `NovaColors.kt`, `NovaMotion.kt`, `NovaComponents.kt`, `RecentsLookup.kt`,
`Theme.kt`, `Type.kt` and the restyled components) was written for Nova Dialer. `Theme.kt` and `Type.kt` were rewritten
from scratch; they still carry the original header because they replace files that came from Secure Dialer.
