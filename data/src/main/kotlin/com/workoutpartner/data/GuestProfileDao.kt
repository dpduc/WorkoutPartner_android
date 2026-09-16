package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GuestProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: GuestProfileEntity)

    @Query("SELECT * FROM guest_profile WHERE id = ${GuestProfileEntity.SINGLETON_ID}")
    suspend fun get(): GuestProfileEntity?

    @Query("DELETE FROM guest_profile")
    suspend fun clear()
}
