package com.michael.simplemusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 1")
    fun getConfig(): Flow<AppConfig?>

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun getConfigSync(): AppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AppConfig)
}
