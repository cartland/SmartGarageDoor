package com.chriscartland.garage.datalocal

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android-specific database factory.
 *
 * Uses Android [Context] for database file path.
 * iOS would use NSDocumentDirectory instead.
 */
object DatabaseFactory {
    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder<AppDatabase>(
                    context = context,
                    name = context.getDatabasePath("database").absolutePath,
                ).setDriver(BundledSQLiteDriver())
                .fallbackToDestructiveMigration(false)
                .build()
                .also { instance = it }
        }
}
