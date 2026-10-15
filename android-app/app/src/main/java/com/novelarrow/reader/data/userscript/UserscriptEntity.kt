package com.novelarrow.reader.data.userscript

import kotlinx.serialization.Serializable

/**
 * Metadata record for a userscript.
 * The raw JavaScript content is stored on disk in the physical filePath.
 */
@Serializable
data class UserscriptEntity(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val filePath: String,
    val isEnabled: Boolean = true,
    val matchPatterns: List<String> = listOf("*"),
    val runAt: String = "document-end",
    val description: String = "",
    val author: String = "",
    val grantList: List<String> = emptyList(),
    val lastModified: Long = System.currentTimeMillis()
)
