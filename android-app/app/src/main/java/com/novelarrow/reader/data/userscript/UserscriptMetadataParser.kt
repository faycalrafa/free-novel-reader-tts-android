package com.novelarrow.reader.data.userscript

import java.net.URI
import java.util.UUID

object UserscriptMetadataParser {

    data class ParsedMetadata(
        val name: String,
        val version: String,
        val matchPatterns: List<String>,
        val runAt: String,
        val description: String,
        val author: String,
        val grantList: List<String>
    )

    fun parse(code: String, fallbackName: String = "Untitled Script"): ParsedMetadata {
        var name: String? = null
        var version: String? = null
        val matchPatterns = mutableListOf<String>()
        var runAt: String? = null
        var description: String? = null
        var author: String? = null
        val grantList = mutableListOf<String>()

        var inHeaderBlock = false
        val lines = code.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("==UserScript==", ignoreCase = true)) {
                inHeaderBlock = true
                continue
            }
            if (trimmed.contains("==/UserScript==", ignoreCase = true)) {
                inHeaderBlock = false
                break
            }

            if (inHeaderBlock) {
                val cleanLine = trimmed.removePrefix("//").trim()
                if (cleanLine.startsWith("@")) {
                    val parts = cleanLine.substring(1).trim().split(Regex("\\s+"), limit = 2)
                    val key = parts.getOrNull(0)?.lowercase() ?: continue
                    val value = parts.getOrNull(1)?.trim().orEmpty()

                    when (key) {
                        "name" -> if (name == null && value.isNotBlank()) name = value
                        "version" -> if (version == null && value.isNotBlank()) version = value
                        "match", "include" -> if (value.isNotBlank() && !matchPatterns.contains(value)) matchPatterns.add(value)
                        "run-at" -> if (runAt == null && value.isNotBlank()) runAt = normalizeRunAt(value)
                        "description" -> if (description == null && value.isNotBlank()) description = value
                        "author" -> if (author == null && value.isNotBlank()) author = value
                        "grant" -> if (value.isNotBlank() && !grantList.contains(value)) grantList.add(value)
                    }
                }
            }
        }

        return ParsedMetadata(
            name = name ?: fallbackName.ifBlank { "Untitled Script" },
            version = version ?: "1.0.0",
            matchPatterns = if (matchPatterns.isNotEmpty()) matchPatterns else listOf("*"),
            runAt = runAt ?: "document-end",
            description = description ?: "",
            author = author ?: "",
            grantList = grantList
        )
    }

    private fun normalizeRunAt(raw: String): String {
        return when (raw.trim().lowercase()) {
            "document-start", "document_start" -> "document-start"
            "document-idle", "document_idle" -> "document-idle"
            else -> "document-end"
        }
    }

    fun generateDefaultTemplate(currentUrl: String? = null, scriptName: String = "New Userscript"): String {
        val matchPattern = try {
            if (!currentUrl.isNullOrBlank() && (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))) {
                val host = URI(currentUrl).host
                if (!host.isNullOrBlank()) "*://$host/*" else "*://*/*"
            } else {
                "*://*/*"
            }
        } catch (_: Exception) {
            "*://*/*"
        }

        return """
            // ==UserScript==
            // @name         $scriptName
            // @version      1.0.0
            // @description  Custom userscript for NovelArrow
            // @match        $matchPattern
            // @run-at       document-end
            // @grant        GM_notification
            // ==/UserScript==

            (function() {
                'use strict';
                console.log('NovelArrow userscript [$scriptName] executed on ' + window.location.href);
                if (typeof GM_notification === 'function') {
                    GM_notification('Userscript [$scriptName] loaded successfully!');
                }
            })();
        """.trimIndent()
    }
}
