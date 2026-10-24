package com.novelarrow.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppMetaDao {

    @Query("SELECT value FROM app_meta WHERE key = :key LIMIT 1")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMeta(entry: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE key = :key")
    suspend fun deleteMeta(key: String)
}
