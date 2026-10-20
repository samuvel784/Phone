/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Minimal, dependency-free location access used only by Emergency SOS.
 *
 * Uses the platform [LocationManager] (no Google Play Services) and returns the freshest
 * last-known fix available from GPS, network or passive providers. A last-known fix is
 * intentional: an SOS must be able to attach a location immediately without waiting for a
 * fresh GPS lock, which may not be available indoors in an emergency.
 */
object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Best available last-known location, or null when permission is missing or no fix exists. */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var best: Location? = null
        for (provider in providers) {
            val location = try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (_: Exception) {
                null
            }
            if (location != null && (best == null || location.time > best.time)) {
                best = location
            }
        }
        return best
    }

    /** A compact `geo:` map link that any maps app can open. */
    fun buildGeoUri(location: Location): String {
        val lat = location.latitude
        val lng = location.longitude
        return "geo:$lat,$lng?q=$lat,$lng"
    }
}
