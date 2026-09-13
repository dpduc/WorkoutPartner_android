package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getById(accountId: String): AccountEntity?
}
