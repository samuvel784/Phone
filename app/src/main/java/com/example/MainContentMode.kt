/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

/**
 * The only three surfaces MainActivity may render.
 *
 * A live call does not unlock the app. When the app lock is enabled and the user has not
 * authenticated, a call gets a deliberately small answer/decline/mute/end surface instead of
 * the full dialer, recents, contacts or settings.
 */
internal enum class MainContentMode {
    LOCKED,
    RESTRICTED_CALL,
    FULL_APP
}

internal fun resolveMainContentMode(
    isAppLockEnabled: Boolean,
    isAppAuthenticated: Boolean,
    isCallActive: Boolean,
    hasLiveCall: Boolean
): MainContentMode {
    if (!isAppLockEnabled || isAppAuthenticated) return MainContentMode.FULL_APP
    if (hasLiveCall || isCallActive) return MainContentMode.RESTRICTED_CALL
    return MainContentMode.LOCKED
}
