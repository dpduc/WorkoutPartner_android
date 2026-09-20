package com.workoutpartner.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * A thin wrapper over Android's [TextToSpeech], introduced for the Position
 * Check's spoken guidance (`workout-partner-v3` ticket 12) — no queue
 * priorities, no Settings toggle; ticket 13's `SessionAnnouncer` work is
 * what layers those on. Not unit-tested: a framework adapter, like
 * `CameraPoseTracker`. Lines requested before the engine finishes
 * initializing are held and spoken once it's ready; [shutdown] must be
 * called when the owning screen goes away, after which everything is
 * ignored.
 */
class PromptSpeaker(context: Context) {
    private var ready = false
    private var isShutDown = false
    private val pending = mutableListOf<String>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (isShutDown) return@TextToSpeech
        ready = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
        if (ready) pending.forEach(::speakNow)
        pending.clear()
    }

    fun speak(text: String) {
        if (isShutDown) return
        if (ready) speakNow(text) else pending += text
    }

    fun shutdown() {
        isShutDown = true
        pending.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, text)
    }
}
