/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.core.net.toUri
import com.example.R
import com.example.util.LocationHelper

/**
 * Executes the Emergency SOS action:
 *   A. Calls the user's chosen emergency number (defaults to the GSM universal 112).
 *   B. Texts every saved emergency contact with an optional map link of the current location.
 *
 * Nothing here touches the network directly; SMS goes through the user's own carrier, and the
 * location is only read at the moment the action is triggered.
 */
object EmergencySosManager {

    /**
     * Nova Dialer is a dialer, not the default SMS handler, so it never requests the restricted
     * SEND_SMS permission. The alert is handed to the user's own SMS app for confirmation.
     */
    fun canSendSms(context: Context): Boolean = true

    /** Builds the alert text, appending a map link when a location is available. */
    fun buildAlertMessage(context: Context, includeLocation: Boolean, location: Location?): String {
        val base = EmergencySosPrefs.getCustomMessage(context)
            .ifBlank { context.getString(R.string.sos_alert_message) }
        return if (includeLocation && location != null) {
            "$base\n${context.getString(R.string.sos_alert_location_label)} ${LocationHelper.buildGeoUri(location)}"
        } else {
            base
        }
    }

    /**
     * Runs the full SOS action in one shot: texts the saved contacts (when auto-alert is on) and
     * places the emergency call (when a number is set). Used by the background shake trigger.
     * The foreground screen keeps its own copy so it can show per-step feedback.
     */
    fun trigger(context: Context) {
        val includeLocation = EmergencySosPrefs.isIncludeLocation(context)
        val contacts = EmergencySosPrefs.getContacts(context)
        if (EmergencySosPrefs.isAutoAlertEnabled(context) && contacts.isNotEmpty() && canSendSms(context)) {
            val location = if (includeLocation && LocationHelper.hasLocationPermission(context)) {
                LocationHelper.getLastKnownLocation(context)
            } else {
                null
            }
            alertContacts(context, contacts, buildAlertMessage(context, includeLocation, location))
        }
        callEmergencyNumber(context, EmergencySosPrefs.getEmergencyCallNumber(context))
    }

    /**
     * Opens the user's SMS app once with every emergency contact pre-addressed and the alert
     * pre-filled. No restricted SEND_SMS permission is needed; the user confirms the send.
     *
     * @return 1 when the SMS composer was opened, 0 otherwise.
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun alertContacts(
        context: Context,
        contacts: List<EmergencySosPrefs.SosContact>,
        message: String
    ): Int {
        if (contacts.isEmpty()) return 0
        val recipients = contacts
            .map { it.number.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(";")
        if (recipients.isBlank()) return 0

        return try {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, "smsto:$recipients".toUri()).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            1
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Places the emergency call. Uses ACTION_CALL when we hold CALL_PHONE (our app is a dialer),
     * and falls back to opening the dialer prefilled so the user can confirm with one tap.
     */
    fun callEmergencyNumber(context: Context, number: String) {
        val clean = number.trim()
        if (clean.isBlank()) return
        val uri = Uri.fromParts("tel", clean, null)

        try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {
            // Fall through to the dialer.
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Nothing else we can do.
        }
    }
}
