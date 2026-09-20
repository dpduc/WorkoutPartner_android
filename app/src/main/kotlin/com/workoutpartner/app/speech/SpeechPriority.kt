package com.workoutpartner.app.speech

/**
 * How urgently a spoken line needs to be heard (`workout-partner-v3` ticket
 * 13). [PromptSpeaker] interrupts whatever's queued for [CRITICAL] and drops
 * [LOW] if it's already speaking; the levels between just queue in order.
 */
enum class SpeechPriority { CRITICAL, HIGH, NORMAL, LOW }

/** One line to speak, and how urgently — what `SessionAnnouncer` produces and [PromptSpeaker] speaks. */
data class Announcement(val text: String, val priority: SpeechPriority)
