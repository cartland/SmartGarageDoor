/*
 * Copyright 2021 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chriscartland.garage.applogger.AppLoggerDao
import com.chriscartland.garage.applogger.model.AppEvent
import com.chriscartland.garage.door.DoorEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        DoorEvent::class,
        AppEvent::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLoggerDao(): AppLoggerDao

    abstract fun doorEventDao(): DoorEventDao

    companion object {
        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If the INSTANCE is not null, then return it.
            // If it is null, then create the database, assign INSTANCE, and return it.
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance ->
                    // The also {} block will return the instance for getDatabase().
                    Companion.instance = instance
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase =
            Room
                .databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "database",
                ).fallbackToDestructiveMigration(false)
                .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext appContext: Context,
    ): AppDatabase = AppDatabase.getDatabase(appContext)
}
