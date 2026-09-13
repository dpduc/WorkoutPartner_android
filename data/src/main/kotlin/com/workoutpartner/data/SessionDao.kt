package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): SessionEntity?

    /** All Sessions owned by a signed-up Account, most recent first. */
    @Query("SELECT * FROM sessions WHERE accountId = :accountId ORDER BY timestamp DESC")
    suspend fun getForAccount(accountId: String): List<SessionEntity>

    /** A Guest's local Sessions — those with no owning Account yet (ADR-0004). */
    @Query("SELECT * FROM sessions WHERE accountId IS NULL")
    suspend fun getUnowned(): List<SessionEntity>

    /**
     * Re-points every unowned (Guest) Session at [accountId] in one write —
     * the SQL primitive the Guest -> Account migration transaction (ticket
     * 08, ADR-0004) is built on, not the migration itself (that also needs
     * to touch Firestore and is this schema's caller's job).
     */
    @Query("UPDATE sessions SET accountId = :accountId WHERE accountId IS NULL")
    suspend fun claimUnowned(accountId: String)
}
