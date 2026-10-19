/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the opt-in "shake to trigger SOS" watch after a device reboot, but only when the user
 * has previously enabled it. If Android refuses the background foreground-service start, the watch
 * simply stays off until the app is opened again — nothing crashes.
 */
class EmergencySosBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!EmergencySosPrefs.isShakeToTriggerEnabled(context)) return
        EmergencySosService.start(context)
    }
}
