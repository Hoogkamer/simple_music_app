package com.michael.simplemusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningStateDao {
    @Query("SELECT * FROM listening_states ORDER BY id ASC")
    fun getAllStates(): Flow<List<ListeningState>>

    @Query("SELECT * FROM listening_states WHERE id = :id")
    suspend fun getStateById(id: Int): ListeningState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: ListeningState): Long

    @Update
    suspend fun updateState(state: ListeningState)

    @Delete
    suspend fun deleteState(state: ListeningState)

    @Query("SELECT COUNT(*) FROM listening_states")
    suspend fun getStateCount(): Int
}
