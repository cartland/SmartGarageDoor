/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chriscartland.garage.datalocal.AppDatabase
import com.chriscartland.garage.datalocal.AppEvent
import com.chriscartland.garage.datalocal.DoorEventEntity
import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Room database works at runtime.
 *
 * These tests catch failures that unit tests miss:
 * - R8 stripping Room type converters or entity definitions
 * - Room schema mismatches between code and compiled schema
 * - DAO query compilation errors that only surface on device
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSanityTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun databaseCreates() {
        assertNotNull("Database should create successfully", db)
    }

    @Test
    fun doorEventDaoAccessible() {
        assertNotNull("DoorEventDao should be accessible", db.doorEventDao())
    }

    @Test
    fun appLoggerDaoAccessible() {
        assertNotNull("AppLoggerDao should be accessible", db.appLoggerDao())
    }

    @Test
    fun insertAndReadDoorEvent() {
        val entity = DoorEventEntity(
            doorPosition = DoorPosition.CLOSED,
            message = "The door is closed.",
            lastCheckInTimeSeconds = 1000L,
            lastChangeTimeSeconds = 900L,
        )
        db.doorEventDao().insert(entity)

        val result = runBlocking { db.doorEventDao().recentDoorEvents().first() }
        assertEquals("Should have 1 door event", 1, result.size)
        assertEquals(DoorPosition.CLOSED, result[0].doorPosition)
        assertEquals("The door is closed.", result[0].message)
        assertEquals(1000L, result[0].lastCheckInTimeSeconds)
        assertEquals(900L, result[0].lastChangeTimeSeconds)
    }

    @Test
    fun insertAndReadAppEvent() {
        val event = AppEvent(
            eventKey = "test_key",
            timestamp = 12345L,
        )
        db.appLoggerDao().insert(event)

        val result = runBlocking { db.appLoggerDao().getAll().first() }
        assertEquals("Should have 1 app event", 1, result.size)
        assertEquals("test_key", result[0].eventKey)
    }

    @Test
    fun doorEventReplaceAll() {
        val events = listOf(
            DoorEventEntity(
                doorPosition = DoorPosition.OPEN,
                message = "Open",
                lastChangeTimeSeconds = 100L,
            ),
            DoorEventEntity(
                doorPosition = DoorPosition.CLOSED,
                message = "Closed",
                lastChangeTimeSeconds = 200L,
            ),
        )
        db.doorEventDao().replaceAll(events)

        val result = runBlocking { db.doorEventDao().recentDoorEvents().first() }
        assertEquals("Should have 2 door events", 2, result.size)
    }
}
