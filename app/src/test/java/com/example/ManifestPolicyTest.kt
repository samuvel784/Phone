/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the Play Store contract of the source manifest. These assertions fail fast when a
 * future change silently adds network access, a broadly-scoped permission, a new exported
 * component, an orientation lock or a backup path that would leak call history.
 */
class ManifestPolicyTest {

    private val manifest: Element by lazy {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        document.documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttr(name: String): String? =
        getAttribute("android:$name").takeIf { it.isNotBlank() }

    @Test
    fun internetIsNeverGranted() {
        val internet = elements("uses-permission").filter {
            it.androidAttr("name") == "android.permission.INTERNET"
        }
        assertTrue("INTERNET must only appear as a tools:node=remove declaration", internet.isNotEmpty())
        internet.forEach { element ->
            assertEquals(
                "INTERNET must never be granted",
                "remove",
                element.getAttribute("tools:node")
            )
        }
    }

    @Test
    fun onlyExpectedDangerousPermissionsAreDeclared() {
        val declared = elements("uses-permission")
            .filter { it.getAttribute("tools:node") != "remove" }
            .mapNotNull { it.androidAttr("name") }
            .toSet()

        val expected = setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_PHONE_STATE",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT"
        )
        assertEquals("unexpected permission change", expected, declared)

        val forbidden = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECORD_AUDIO",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_PHONE_NUMBERS"
        )
        assertTrue(
            "forbidden permissions present: ${declared intersect forbidden}",
            (declared intersect forbidden).isEmpty()
        )
    }

    @Test
    fun applicationIsNotBackedUpAndIsResizeable() {
        val application = elements("application").single()
        assertEquals("false", application.androidAttr("allowBackup"))
        assertEquals("true", application.androidAttr("resizeableActivity"))
        assertEquals("true", application.androidAttr("supportsRtl"))
        assertNotNull(application.androidAttr("dataExtractionRules"))
        assertNotNull(application.androidAttr("fullBackupContent"))
    }

    @Test
    fun telephonyIsOptionalSoTabletsAndChromebooksCanInstall() {
        val telephony = elements("uses-feature")
            .firstOrNull { it.androidAttr("name") == "android.hardware.telephony" }
        assertNotNull(telephony)
        assertEquals("false", telephony!!.androidAttr("required"))
    }

    @Test
    fun onlyLauncherAndCallServiceAreExported() {
        val exported = listOf("activity", "service", "receiver", "provider")
            .flatMap { elements(it) }
            .filter { it.androidAttr("exported") == "true" }
            .mapNotNull { it.androidAttr("name") }
            .toSet()

        assertEquals(
            setOf(".MainActivity", ".MyInCallService"),
            exported
        )

        val inCallService = elements("service")
            .first { it.androidAttr("name") == ".MyInCallService" }
        assertEquals("android.permission.BIND_INCALL_SERVICE", inCallService.androidAttr("permission"))

        val incoming = elements("activity")
            .first { it.androidAttr("name") == ".IncomingCallActivity" }
        assertEquals("false", incoming.androidAttr("exported"))
    }

    @Test
    fun activitiesDoNotLockOrientation() {
        elements("activity").forEach { activity ->
            assertFalse(
                "${activity.androidAttr("name")} must stay resizeable in every orientation",
                activity.hasAttribute("android:screenOrientation")
            )
        }
    }

    @Test
    fun oemSettingsLookupIsScopedByQueries() {
        val packages = elements("package")
            .mapNotNull { it.androidAttr("name") }
            .toSet()
        assertTrue(packages.contains("com.miui.securitycenter"))
        assertTrue(packages.contains("com.miui.permcenter"))
        assertEquals("package visibility must stay narrowly scoped", 2, packages.size)
    }

    @Test
    fun mainActivityKeepsTheStandardDialerContracts() {
        val main = elements("activity")
            .first { it.androidAttr("name") == ".MainActivity" }
        val actions = mutableSetOf<String>()
        val intentFilters = main.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val actionNodes = filter.getElementsByTagName("action")
            for (j in 0 until actionNodes.length) {
                val action = (actionNodes.item(j) as Element).androidAttr("name")
                if (action != null) actions += action
            }
        }
        assertTrue(actions.contains("android.intent.action.MAIN"))
        assertTrue(actions.contains("android.intent.action.DIAL"))
        assertTrue(actions.contains("android.intent.action.VIEW"))
        assertTrue(actions.contains("android.intent.action.CALL_BUTTON"))
    }
}
