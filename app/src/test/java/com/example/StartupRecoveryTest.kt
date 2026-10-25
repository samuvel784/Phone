/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatabaseRecoveryRequiredException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the recovery startup path with real AndroidX ViewModel creation.
 *
 * Reading the activity source is not enough: the exception the activity catches is the one
 * AndroidX actually raises while instantiating the ViewModel (normally an
 * `InvocationTargetException` from the reflective constructor call). These tests run that exact
 * creation path and feed its failure through the production classifier.
 */
@RunWith(RobolectricTestRunner::class)
class StartupRecoveryTest {

    private lateinit var app: Application
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    private fun <T : ViewModel> create(modelClass: Class<T>): T =
        ViewModelProvider(store, AndroidViewModelFactory.getInstance(app)).get(modelClass)

    @Test
    fun `database recovery failure from real ViewModel creation opens recovery`() {
        val result = resolveStartupViewModel {
            create(DatabaseRecoveryViewModel::class.java)
        }

        assertTrue(
            "Expected the recovery screen, got $result",
            result is StartupResult.RecoveryRequired
        )
        assertEquals(
            "database-file-recovery",
            (result as StartupResult.RecoveryRequired).reason
        )
    }

    @Test
    fun `key failure from real ViewModel creation opens recovery`() {
        val result = resolveStartupViewModel {
            create(KeyFailureViewModel::class.java)
        }

        assertTrue(
            "Expected the recovery screen, got $result",
            result is StartupResult.RecoveryRequired
        )
        assertEquals(
            "The database key could not be unlocked.",
            (result as StartupResult.RecoveryRequired).reason
        )
    }

    @Test
    fun `androidx wrapper chain still contains the recovery cause`() {
        val raw = try {
            create(DatabaseRecoveryViewModel::class.java)
            null
        } catch (failure: Throwable) {
            failure
        }

        assertNotNull("ViewModel creation must have failed", raw)
        assertNotNull(
            "AndroidX threw something that does not reference the recovery exception",
            raw!!.findCause<DatabaseRecoveryRequiredException>()
        )
        assertEquals("database-file-recovery", raw.findCause<DatabaseRecoveryRequiredException>()?.message)
    }

    @Test
    fun `unrelated IllegalStateException is rethrown instead of showing recovery`() {
        // AndroidX wraps constructor failures in a RuntimeException ("Cannot create an instance
        // of ..."), so the production path must rethrow that wrapper rather than convert it into
        // a recovery screen. The unrelated cause must still be visible.
        val thrown = assertThrows(RuntimeException::class.java) {
            resolveStartupViewModel {
                create(UnrelatedFailureViewModel::class.java)
            }
        }

        assertEquals("unrelated-state", thrown.findCause<IllegalStateException>()?.message)
    }

    @Test
    fun `successful ViewModel creation is returned ready`() {
        val result = resolveStartupViewModel {
            create(HealthyViewModel::class.java)
        }

        assertTrue(result is StartupResult.Ready)
    }

    class DatabaseRecoveryViewModel(application: Application) : AndroidViewModel(application) {
        init {
            throw DatabaseRecoveryRequiredException("database-file-recovery")
        }
    }

    class KeyFailureViewModel(application: Application) : AndroidViewModel(application) {
        init {
            throw IllegalStateException("The database key could not be unlocked.")
        }
    }

    class UnrelatedFailureViewModel(application: Application) : AndroidViewModel(application) {
        init {
            throw IllegalStateException("unrelated-state")
        }
    }

    class HealthyViewModel(application: Application) : AndroidViewModel(application)
}
