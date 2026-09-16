package com.workoutpartner.data

import com.google.firebase.Timestamp

/**
 * Maps this schema's entities onto plain Firestore document data for
 * [FirestoreSyncGateway]. [Instant] becomes a Firestore [Timestamp] (its
 * native, query/sort-friendly time type) and [Exercise] becomes its name —
 * mirroring how [Converters] handles the same two types for Room.
 *
 * [accountId] is stamped onto every document (ticket 14) even though
 * neither [SetEntity] nor [TallyEntity] carries it in Room — Room derives
 * ownership through [SessionEntity]/[TrackedProfileEntity] instead, but
 * Firestore's security rules (ticket 14) need it directly on the document
 * they're evaluating, not via a join Firestore rules can't easily express
 * for that access pattern.
 */
fun SetEntity.toFirestoreMap(accountId: String): Map<String, Any?> = mapOf(
    "id" to id,
    "accountId" to accountId,
    "sessionId" to sessionId,
    "exercise" to exercise.name,
    "targetReps" to targetReps,
    "actualReps" to actualReps,
    "formScore" to formScore,
    "goodSet" to goodSet,
    "timestamp" to Timestamp(timestamp.epochSecond, timestamp.nano),
)

fun TallyEntity.toFirestoreMap(accountId: String): Map<String, Any?> = mapOf(
    "id" to id,
    "accountId" to accountId,
    "trackedProfileId" to trackedProfileId,
    "exercise" to exercise.name,
    "repsAchieved" to repsAchieved,
    "target" to target,
    "timestamp" to Timestamp(timestamp.epochSecond, timestamp.nano),
    // formScore/durationSeconds (workout-partner-v2 ticket 03) — null for a
    // Tally recorded before that ticket landed, same nullable shape as
    // `target` above.
    "formScore" to formScore,
    "durationSeconds" to durationSeconds,
)
