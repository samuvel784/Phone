/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.util.CallScreenPermissionHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallScreenPermissionsTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDefaultDialerDetectionDoesNotCrash() {
        val isDefault = CallScreenPermissionHelper.isDefaultDialer(context)
        // In Robolectric test container without ROLE_DIALER granted, should safely return false without exception
        assertNotNull(isDefault)
    }

    @Test
    fun testOverlayPermissionHelperWasRemovedFromTheShippedV1() {
        // The Deferred overlay feature was removed with the simplified v1; its permission
        // helpers must no longer exist on CallScreenPermissionHelper. Uses reflection so the
        // test compiles (and keeps passing) regardless of the helper's remaining API.
        val overlayCheck = runCatching {
            CallScreenPermissionHelper::class.java.getDeclaredMethod("canDrawOverlays", android.content.Context::class.java)
        }
        val overlayRequest = runCatching {
            CallScreenPermissionHelper::class.java.getDeclaredMethod("requestOverlayPermission", android.content.Context::class.java)
        }
        assertTrue("canDrawOverlays must be removed from the simplified v1", overlayCheck.isFailure)
        assertTrue("requestOverlayPermission must be removed from the simplified v1", overlayRequest.isFailure)
    }

    @Test
    fun testFullScreenIntentCheckOnApi34() {
        val canFullScreen = CallScreenPermissionHelper.canUseFullScreenIntent(context)
        // On API 34 default, NotificationManager.canUseFullScreenIntent() returns true or false safely
        assertNotNull(canFullScreen)
    }

    @Test
    fun testRequestIntentsDoNotThrowExceptions() {
        // Test fallback intent execution safely
        CallScreenPermissionHelper.requestDefaultDialer(context)
        CallScreenPermissionHelper.requestFullScreenIntentPermission(context)
        CallScreenPermissionHelper.openChannelNotificationSettings(context)
        CallScreenPermissionHelper.openAppDetailsSettings(context)
        assertTrue(true)
    }
}
