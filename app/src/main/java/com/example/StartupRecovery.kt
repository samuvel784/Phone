/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import com.example.data.DatabaseRecoveryRequiredException

/**
 * Outcome of creating the startup ViewModel.
 *
 * Kept separate from the activity so the exact failure handling can be exercised with a real
 * AndroidX `ViewModelProvider` creation instead of only by reading the activity source.
 */
internal sealed interface StartupResult<out T> {
    /** The ViewModel was created and the dialer can start. */
    data class Ready<T>(val viewModel: T) : StartupResult<T>

    /** The database/key failure is recoverable only through the explicit reset screen. */
    data class RecoveryRequired(val reason: String) : StartupResult<Nothing>
}

/**
 * Creates the startup ViewModel and classifies a failure.
 *
 * AndroidX instantiates ViewModels reflectively, so the original exception normally arrives
 * wrapped in an `InvocationTargetException` (or a provider/runtime wrapper). The whole cause chain
 * is inspected; an unrelated failure is rethrown so real bugs are not silently relabelled as
 * "database recovery".
 */
internal fun <T> resolveStartupViewModel(create: () -> T): StartupResult<T> {
    return try {
        StartupResult.Ready(create())
    } catch (failure: Throwable) {
        val reason = databaseRecoveryReason(failure) ?: throw failure
        StartupResult.RecoveryRequired(reason)
    }
}

private const val DEFAULT_DATABASE_RECOVERY_REASON =
    "The dialer database needs recovery before it can be opened."
private const val DEFAULT_KEY_RECOVERY_REASON =
    "The dialer database key could not be unlocked."

private fun databaseRecoveryReason(failure: Throwable): String? {
    val recovery = failure.findCause<DatabaseRecoveryRequiredException>()
    if (recovery != null) {
        return recovery.message ?: DEFAULT_DATABASE_RECOVERY_REASON
    }

    val keyFailure = failure.findCause<IllegalStateException>()
    if (keyFailure != null && isDatabaseKeyFailure(keyFailure)) {
        return keyFailure.message ?: DEFAULT_KEY_RECOVERY_REASON
    }

    return null
}

/** Finds a throwable of type [T] anywhere in the cause chain. */
internal inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

/**
 * Only the key-material failures reported by [com.example.data.DatabaseKeyManager] should open the
 * recovery screen. An unrelated IllegalStateException must still crash loudly instead of being
 * silently converted into "your database is corrupted".
 */
private fun isDatabaseKeyFailure(failure: IllegalStateException): Boolean {
    val message = failure.message.orEmpty()
    return message.contains("key", ignoreCase = true) ||
        message.contains("keystore", ignoreCase = true)
}
