/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.widget.FavoritesWidgetProvider

/**
 * Places a call for the home-screen favourites widget.
 *
 * This activity is **not exported**, so only the widget's own PendingIntent can
 * start it. It never marks the app as authenticated: a widget tap must not unlock
 * Nova Dialer. It only places the call when CALL_PHONE is granted; otherwise it opens
 * MainActivity with an ACTION_DIAL tel: intent so the number is merely shown in the
 * dialpad and the user decides whether to call.
 */
class WidgetCallActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent?.getStringExtra(FavoritesWidgetProvider.EXTRA_WIDGET_CALL_NUMBER)
        if (number.isNullOrBlank()) {
            finish()
            return
        }

        val canPlaceCall = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (canPlaceCall) {
            val preferredSim = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("preferred_sim", "Ask") ?: "Ask"
            CallManager.placeCall(this, number, preferredSim)
        } else {
            // No call permission yet: reveal the number in the dialpad only.
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_DIAL
                    data = Uri.fromParts("tel", number, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                })
            } catch (_: Exception) {}
        }

        finish()
    }

    private companion object {
        const val PREFS = "dialer_prefs"
    }
}
