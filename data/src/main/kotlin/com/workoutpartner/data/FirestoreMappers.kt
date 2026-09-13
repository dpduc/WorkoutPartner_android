package com.workoutpartner.data

import com.google.firebase.Timestamp

/**
 * Maps this schema's entities onto plain Firestore document data for
 * [FirestoreSyncGateway]. [Instant] becomes a Firestore [Timestamp] (its
 * native, query/sort-friendly time type) and [Exercise] becomes its name —
 * mirroring how [Converters] handles the same two types for Room.
 */
fun SetEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "sessionId" to sessionId,
    "exercise" to exercise.name,
    "targetReps" to targetReps,
    "actualReps" to actualReps,
    "formScore" to formScore,
    "goodSet" to goodSet,
    "timestamp" to Timestamp(timestamp.epochSecond, timestamp.nano),
)

fun TallyEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "trackedProfileId" to trackedProfileId,
    "exercise" to exercise.name,
    "repsAchieved" to repsAchieved,
    "target" to target,
    "timestamp" to Timestamp(timestamp.epochSecond, timestamp.nano),
)
