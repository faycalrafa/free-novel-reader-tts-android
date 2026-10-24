package com.novelarrow.reader.data.util

class ProgressDebouncer(
    private val intervalMs: Long = 80L,
    private val onProgress: (current: Int, total: Int, currentItemName: String) -> Unit
) {
    private var lastProgressTime = 0L

    fun report(current: Int, total: Int, name: String) {
        val now = System.currentTimeMillis()
        if (now - lastProgressTime >= intervalMs || current == total || current <= 1) {
            lastProgressTime = now
            onProgress(current, total, name)
        }
    }
}
