package com.chriscartland.garage.datalocal

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android `actual` for [DatabaseFactory]. Resolves the SQLite file
 * path via `context.getDatabasePath("database")`. The kotlin-inject
 * `@Singleton` provider in `AppComponent` is the singleton scope —
 * no internal caching needed here.
 */
actual class DatabaseFactory(
    private val context: Context,
) {
    actual fun createDatabase(): AppDatabase =
        Room
            .databaseBuilder<AppDatabase>(
                context = context,
                name = context.getDatabasePath("database").absolutePath,
            ).setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(false)
            .build()
}
