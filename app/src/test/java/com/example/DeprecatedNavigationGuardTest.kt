/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Source-level regression guard for the simplified v1 release scope.
 *
 * Guards against:
 *  1. Reachable UI navigating to settings tabs that no longer have a handler
 *     (the historical bug: the Duplicate Finder card navigated to a removed tab).
 *  2. Leftover references to deferred/removed features (Dynamic Island overlay,
 *     Emergency SOS, recording vault biometrics, draw-overlays prompts) that would
 *     either crash at runtime or contradict the shipped permission set.
 */
class DeprecatedNavigationGuardTest {

    /** Tabs with a real handler in SettingsPanel: 1 Appearance, 2 Sound, 3 SIM, 4 Speed dial, 6 Contacts, 8 Privacy. */
    private val supportedTabs = setOf(1, 2, 3, 4, 6, 8)

    /** Class names of deferred/removed features that must stay out of production sources. */
    private val forbiddenSymbols = listOf(
        "DynamicIslandOverlayManager",
        "DynamicIslandPill",
        "EmergencySosService",
        "EmergencySosBootReceiver",
        "EmergencySosManager",
        "EmergencySosPrefs",
        "EmergencySosSettings",
        "VaultBiometricAuthHelper",
        "requestOverlayPermission",
        "canDrawOverlays"
    )

    private fun sourceFiles(): List<File> {
        val root = File("src/main/java")
        assertTrue("Production source root not found relative to test working directory", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `all settings navigation targets are supported tabs`() {
        val pattern = Regex("""onNavigateToTab\(\s*(\d+)""")
        for (file in sourceFiles()) {
            val text = file.readText()
            for (match in pattern.findAll(text)) {
                val tab = match.groupValues[1].toInt()
                if (tab !in supportedTabs) {
                    fail("${file.path}: navigation targets unsupported tab $tab (${supportedTabs.sorted()} are supported). " +
                            "Update the entry point or add a real handler for the tab.")
                }
            }
        }
    }

    @Test
    fun `settings search index only offers supported destinations and no deferred features`() {
        val itemPattern = Regex("""SettingsSearchItem\(\s*"([^"]*)"\s*,\s*"[^"]*"\s*,\s*"[^"]*"\s*,\s*(\d+)""")
        val seenTabs = mutableMapOf<String, Int>()
        for (file in sourceFiles()) {
            val text = file.readText()
            for (match in itemPattern.findAll(text)) {
                val title = match.groupValues[1]
                val tab = match.groupValues[2].toInt()
                if (tab !in supportedTabs) {
                    fail("${file.path}: settings search entry \"$title\" targets unsupported tab $tab")
                }
                seenTabs[title] = tab
                for (deferred in listOf("Dynamic Island", "Duplicate Finder", "Emergency SOS", "Screening", "Recording Vault")) {
                    if (title.contains(deferred, ignoreCase = true)) {
                        fail("${file.path}: settings search entry \"$title\" advertises a deferred feature")
                    }
                }
            }
        }
        assertTrue("Settings search index scan found no entries", seenTabs.isNotEmpty())
    }

    @Test
    fun `no production source references removed deferred-feature classes or overlay prompts`() {
        for (file in sourceFiles()) {
            val text = file.readText()
            for (symbol in forbiddenSymbols) {
                if (text.contains(symbol)) {
                    fail("${file.path}: references removed deferred-feature symbol $symbol")
                }
            }
        }
    }

    @Test
    fun `manifest does not declare removed deferred-feature permissions`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Manifest not found relative to test working directory", manifest.isFile)
        val text = manifest.readText()
        // <uses-permission android:name="..." tools:node="remove" /> entries are intentional
        // hardening against merged permissions from libraries; only real declarations fail.
        val declaredPermissions = Regex("""<uses-permission\s+(?:tools:node="remove"\s+)?android:name="([^"]+)"(?:[^>]*tools:node="remove")?""")
            .findAll(text)
            .filter { !it.value.contains("""tools:node="remove"""") }
            .map { it.groupValues[1] }
            .toList()
        val removedOrDeferred = listOf(
            "SYSTEM_ALERT_WINDOW",
            "RECORD_AUDIO",
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "INTERNET",
            "SCHEDULE_EXACT_ALARM",
            "RECEIVE_BOOT_COMPLETED",
            "USE_BIOMETRIC",
            "FOREGROUND_SERVICE"
        )
        for (permission in declaredPermissions) {
            for (removed in removedOrDeferred) {
                if (permission.endsWith(removed)) {
                    fail("Manifest declares removed/deferred permission $removed")
                }
            }
        }
        // Components the supported dialer must keep.
        assertTrue("Manifest must declare MyInCallService (InCallService)", text.contains("InCallService"))
        assertTrue("Manifest must declare IncomingCallActivity", text.contains("IncomingCallActivity"))
        assertTrue("Manifest must declare MainActivity with launcher intent", text.contains("MainActivity"))
        assertTrue("Manifest must declare FileProvider for exports", text.contains("FileProvider"))
    }

    @Test
    fun `deferred feature switches are off in the release scope`() {
        assertFalse("Call recording must stay deferred", ReleaseScope.CALL_RECORDING_ENABLED)
        assertFalse("Callback reminders must stay deferred", ReleaseScope.CALLBACK_REMINDERS_ENABLED)
        assertFalse("Duplicate Finder must stay deferred", ReleaseScope.DUPLICATE_FINDER_ENABLED)
        assertFalse("Spam labelling must stay deferred", ReleaseScope.SPAM_LABELS_ENABLED)
    }

    @Test
    fun `manifest does not register deferred feature components`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Manifest not found relative to test working directory", manifest.isFile)
        val text = manifest.readText()
        val deferredComponents = listOf(
            "ReminderAlarmReceiver",
            "NovaDialCallScreeningService",
            "FavoritesWidgetProvider",
            "WidgetCallActivity",
            "RecordingDisclosureActivity"
        )
        for (component in deferredComponents) {
            assertFalse("Manifest must not register deferred component $component", text.contains(component))
        }
    }
}
