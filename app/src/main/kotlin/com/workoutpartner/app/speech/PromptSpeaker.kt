package com.workoutpartner.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * A thin wrapper over Android's [TextToSpeech] (`workout-partner-v3` tickets
 * 12/13): every line is spoken only while [prefs]' spoken-prompts setting is
 * on (checked per line, so flipping it takes effect immediately), a
 * [SpeechPriority.CRITICAL] line interrupts whatever is queued, and a
 * [SpeechPriority.LOW] one is dropped if the engine is already speaking.
 * Not unit-tested: a framework adapter, like `CameraPoseTracker`. Lines
 * requested before the engine finishes initializing are held and spoken once
 * it's ready; [shutdown] must be called when the owning screen goes away,
 * after which everything is ignored.
 */
class PromptSpeaker(context: Context, private val prefs: SpeechPrefs = SpeechPrefs(context)) {
    private var ready = false
    private var isShutDown = false
    private val pending = mutableListOf<Announcement>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (isShutDown) return@TextToSpeech
        ready = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
        if (ready) pending.forEach(::speakIfEnabled)
        pending.clear()
    }

    fun speak(announcement: Announcement) {
        if (isShutDown || !prefs.spokenPromptsEnabled) return
        if (ready) speakNow(announcement) else pending += announcement
    }

    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) = speak(Announcement(text, priority))

    fun shutdown() {
        isShutDown = true
        pending.clear()
        tts.stop()
        tts.shutdown()
    }

    /** Lines held before the engine was ready re-check the setting, in case it was switched off in the meantime. */
    private fun speakIfEnabled(announcement: Announcement) {
        if (prefs.spokenPromptsEnabled) speakNow(announcement)
    }

    private fun speakNow(announcement: Announcement) {
        val text = announcement.text
        when (announcement.priority) {
            SpeechPriority.CRITICAL -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
            SpeechPriority.LOW -> if (!tts.isSpeaking) tts.speak(text, TextToSpeech.QUEUE_ADD, null, text)
            SpeechPriority.HIGH, SpeechPriority.NORMAL -> tts.speak(text, TextToSpeech.QUEUE_ADD, null, text)
        }
    }
}
