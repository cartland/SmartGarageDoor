package com.chriscartland.garage.datalocal

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS `actual` for [DatabaseFactory]. Resolves the SQLite file path
 * via `NSDocumentDirectory`. The kotlin-inject `@Singleton` provider
 * in the iOS `NativeComponent` is the singleton scope — no internal
 * caching needed here.
 */
actual class DatabaseFactory {
    actual fun createDatabase(): AppDatabase {
        val dbPath = "${iosDocumentsDirectory()}/garage.db"
        return Room
            .databaseBuilder<AppDatabase>(
                name = dbPath,
                factory = { AppDatabaseConstructor.initialize() },
            ).setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun iosDocumentsDirectory(): String {
        val documentDirectory: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )!!
        return requireNotNull(documentDirectory.path)
    }
}
