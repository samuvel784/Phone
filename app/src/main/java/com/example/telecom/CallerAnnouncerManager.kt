/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.getContactNameFromNumber
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Speaks the caller's name while an incoming call is ringing.
 *
 * Rules:
 *  - Settings (message, repeat, speed, pitch, volume, private mode) come from [CallProtectionPrefs].
 *  - Never longer than 15 seconds, and it stops as soon as the call is answered, rejected or ended.
 *  - Silent / vibrate mode and Do Not Disturb stay silent. A call silenced by Focus mode or protection is not announced.
 *  - It never changes the ring volume.
 * All work is posted to the main thread, so no locking is needed.
 */
class CallerAnnouncerManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor()

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** True when the engine provided a gendered voice for the current preference (so pitch is left alone). */
    private var genderVoiceSelected = false

    private var requestId = 0
    private var script: List<AnnouncerScript.Line> = emptyList()
    private val scheduled = mutableListOf<Runnable>()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
    }

    override fun onInit(status: Int) {
        handler.post {
            val engine = tts ?: return@post
            if (status != TextToSpeech.SUCCESS) return@post

            val result = engine.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            genderVoiceSelected = applyVoicePreference(engine)
            isInitialized = true
            // a call may have started while the engine was loading
            if (script.isNotEmpty()) scheduleScript(requestId)
        }
    }

    /** Called by the InCallService when a new incoming call starts ringing. */
    fun announceIncomingCall(number: String, displayName: String) {
        if (!CallProtectionPrefs.isAnnouncerEnabled(appContext)) return
        val myRequest = ++requestId

        // contact lookups use content providers, so they run off the main thread
        background.execute {
            val savedName = getContactNameFromNumber(appContext, number)
            val isSaved = savedName != null
            val isVip = CallProtectionPrefs.isVip(appContext, number)
            val silencedByRules = CallDecisionStore.wasSilenced(number)

            handler.post {
                if (myRequest != requestId) return@post // the call was answered or ended meanwhile
                if (silencedByRules) return@post
                if (!isSaved && !CallProtectionPrefs.isAnnounceUnknown(appContext)) return@post
                if (!isAllowedByDevice(CallProtectionPrefs.isAnnouncerHeadphonesOnly(appContext))) return@post

                val spokenName = savedName ?: displayName.ifBlank { "an unknown number" }.let {
                    // a bare phone number is read digit by digit; say "an unknown number" instead
                    if (it.any { ch -> ch.isLetter() }) it else "an unknown number"
                }
                val type = when {
                    isVip -> "VIP"
                    isSaved -> "saved contact"
                    else -> "unknown number"
                }
                script = AnnouncerScript.buildScript(
                    template = CallProtectionPrefs.getAnnouncerTemplate(appContext),
                    name = spokenName,
                    user = CallProtectionPrefs.getAnnouncerUserName(appContext),
                    type = type,
                    repeats = CallProtectionPrefs.getAnnouncerRepeat(appContext)
                )
                if (isInitialized) scheduleScript(myRequest)
            }
        }
    }

    /** Settings test button: one announcement, no ringer / headphone checks. */
    fun testAnnouncement() {
        val myRequest = ++requestId
        handler.post {
            script = listOf(
                AnnouncerScript.Line(
                    0L,
                    AnnouncerScript.render(
                        CallProtectionPrefs.getAnnouncerTemplate(appContext),
                        "Alex Morgan",
                        CallProtectionPrefs.getAnnouncerUserName(appContext),
                        "saved contact"
                    )
                )
            )
            if (isInitialized) scheduleScript(myRequest)
        }
    }

    private fun scheduleScript(myRequest: Int) {
        val engine = tts ?: return
        cancelScheduled()

        // Re-read the voice each time so a change in Settings takes effect immediately.
        genderVoiceSelected = applyVoicePreference(engine)
        engine.setSpeechRate(CallProtectionPrefs.getAnnouncerSpeedPercent(appContext) / 100f)
        val basePitch = CallProtectionPrefs.getAnnouncerPitchPercent(appContext) / 100f
        engine.setPitch(basePitch * (if (genderVoiceSelected) 1f else genderPitchFactor()))

        script.forEach { line ->
            val runnable = Runnable { if (myRequest == requestId) speak(line.text) }
            scheduled.add(runnable)
            handler.postDelayed(runnable, line.atMs)
        }
        val stopper = Runnable { if (myRequest == requestId) stop() }
        scheduled.add(stopper)
        handler.postDelayed(stopper, AnnouncerScript.MAX_TOTAL_MS)
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        if (!isInitialized || text.isBlank()) return
        requestAudioFocus()
        val params = Bundle().apply {
            putFloat(
                TextToSpeech.Engine.KEY_PARAM_VOLUME,
                CallProtectionPrefs.getAnnouncerVolumePercent(appContext) / 100f
            )
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "novadial_announcement")
    }

    /**
     * Tries to apply the requested gender voice. Returns true when a matching
     * installed voice was found and set (so no extra pitch shift is needed).
     */
    private fun applyVoicePreference(engine: TextToSpeech): Boolean {
        return try {
            when (CallProtectionPrefs.getAnnouncerVoice(appContext)) {
                CallProtectionPrefs.AnnouncerVoice.DEFAULT -> {
                    try { engine.voice = engine.defaultVoice } catch (_: Exception) {}
                    false
                }
                CallProtectionPrefs.AnnouncerVoice.MALE -> selectGenderedVoice(engine, wantFemale = false)
                CallProtectionPrefs.AnnouncerVoice.FEMALE -> selectGenderedVoice(engine, wantFemale = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply announcer voice", e)
            false
        }
    }

    /**
     * Picks the first installed voice that looks like the requested gender.
     * Android exposes no gender field, so this reads the voice name. Many engines
     * include "male"/"female" fragments (e.g. "...-sfg#male_1-local").
     */
    private fun selectGenderedVoice(engine: TextToSpeech, wantFemale: Boolean): Boolean {
        val voices = try { engine.voices } catch (e: Exception) { null } ?: return false
        val chosen = voices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            val hasFemale = name.contains("female")
            val hasMale = name.contains("male") && !hasFemale
            if (wantFemale) hasFemale else hasMale
        } ?: return false
        return try {
            engine.voice = chosen
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not select gendered voice", e)
            false
        }
    }

    /** Pitch offset used to approximate a male/female voice when the engine has no gendered voice. */
    private fun genderPitchFactor(): Float = when (CallProtectionPrefs.getAnnouncerVoice(appContext)) {
        CallProtectionPrefs.AnnouncerVoice.MALE -> 0.80f
        CallProtectionPrefs.AnnouncerVoice.FEMALE -> 1.20f
        CallProtectionPrefs.AnnouncerVoice.DEFAULT -> 1f
    }

    /** Stops speaking right away. Called when the call is answered, rejected or ends. */
    fun stop() {
        requestId++
        handler.post {
            cancelScheduled()
            script = emptyList()
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "tts.stop failed", e)
            }
            abandonAudioFocus()
        }
    }

    fun shutdown() {
        requestId++
        handler.post {
            cancelScheduled()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "tts shutdown failed", e)
            }
            tts = null
            isInitialized = false
            abandonAudioFocus()
        }
        background.shutdown()
    }

    private fun cancelScheduled() {
        scheduled.forEach { handler.removeCallbacks(it) }
        scheduled.clear()
    }

    /** False when the phone is silent or vibrating, Do Not Disturb is on, or private mode is on without headphones. */
    private fun isAllowedByDevice(headphonesOnly: Boolean): Boolean {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false

        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = nm?.currentInterruptionFilter
            if (filter != null &&
                filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) return false
        } catch (e: Exception) {
            Log.w(TAG, "Could not read Do Not Disturb state", e)
        }

        return !headphonesOnly || isHeadphonesConnected(audio)
    }

    private fun isHeadphonesConnected(audio: AudioManager): Boolean {
        return try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun requestAudioFocus() {
        try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon failed", e)
        }
    }

    private companion object {
        const val TAG = "CallerAnnouncer"
    }
}
