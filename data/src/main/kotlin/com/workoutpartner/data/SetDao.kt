package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SetDao {
    @Insert
    suspend fun insert(set: SetEntity)

    @Query("SELECT * FROM sets WHERE id = :setId")
    suspend fun getById(setId: String): SetEntity?

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: String): List<SetEntity>

    /**
     * Every Set across every Session belonging to [accountId] — the raw
     * material the repository layer (ticket 06) folds into Active Days
     * (distinct calendar dates) before handing them to
     * `core-streaks.StreakCalculator`. Deliberately not pre-aggregated into
     * Active Days here: that needs a timezone/"today" policy, which is
     * application logic, not this ticket's schema/DAO scope.
     */
    @Query(
        """
        SELECT sets.* FROM sets
        INNER JOIN sessions ON sets.sessionId = sessions.id
        WHERE sessions.accountId = :accountId
        ORDER BY sets.timestamp ASC
        """,
    )
    suspend fun getForAccount(accountId: String): List<SetEntity>
}
