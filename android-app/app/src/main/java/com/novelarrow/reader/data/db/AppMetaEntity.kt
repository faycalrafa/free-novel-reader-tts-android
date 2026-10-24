package com.novelarrow.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String?
)
