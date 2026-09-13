package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One offline-created Set or Tally still waiting to reach Firestore — the
 * outbox behind "Offline write → local queue → sync-on-reconnect for Sets
 * and Tallies" (ticket 06's scope; per spec.md user stories 42/43).
 *
 * Deliberately scoped to [SyncEntityKind.SET]/[SyncEntityKind.TALLY] only:
 * Account/Session/Roster Firestore sync isn't built by this ticket (see
 * [SyncEngine]'s doc comment for why).
 *
 * [entityId] is the corresponding [SetEntity.id] or [TallyEntity.id] —
 * these are client-generated UUIDs (ticket 05), which is what makes two
 * devices' independently-queued rows reconcile additively once both sync:
 * they're simply different documents, never a same-id collision, so there
 * is nothing to "merge."
 */
@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val entityKind: SyncEntityKind,
    val entityId: String,
    val enqueuedAt: Instant,
)

enum class SyncEntityKind { SET, TALLY }
