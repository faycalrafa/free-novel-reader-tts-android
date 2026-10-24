package com.novelarrow.reader.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex

internal object LibraryStateManager {

    val dbMutex = Mutex()

    @Volatile
    var cachedDb: LibraryDatabase? = null

    var lastSaveTimeMs = 0L

    @Volatile
    var lastFolderProgressTimeMs = 0L

    private val _libraryUpdateEvents = MutableSharedFlow<LibraryUpdateEvent>(extraBufferCapacity = 64)
    val libraryUpdateEvents: SharedFlow<LibraryUpdateEvent> = _libraryUpdateEvents.asSharedFlow()

    fun notifyUpdate(event: LibraryUpdateEvent) {
        _libraryUpdateEvents.tryEmit(event)
    }

    fun clearCache() {
        cachedDb = null
    }

    fun loadDatabaseLocked(roomDelegate: LibraryRoomDelegate): LibraryDatabase {
        cachedDb?.let { return it }
        val loaded = roomDelegate.loadDatabase()
        cachedDb = loaded
        return loaded
    }

    fun saveDatabaseLocked(roomDelegate: LibraryRoomDelegate, db: LibraryDatabase) {
        cachedDb = db
        roomDelegate.saveDatabase(db)
    }
}
