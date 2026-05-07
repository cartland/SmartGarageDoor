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
import com.chriscartland.garage.datalocal.RoomAppLoggerRepository
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.usecase.SeedDiagnosticsCountersFromRoomUseCase
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
    fun insertAndPruneKey_keepsMostRecentRowsUpToLimit() {
        // Insert 5 rows; cap to 3. Oldest 2 should be evicted; newest 3 retained.
        for (i in 1..5) {
            db.appLoggerDao().insertAndPruneKey(
                appEvent = AppEvent(eventKey = "k", timestamp = i.toLong()),
                limit = 3,
            )
        }

        val rows = runBlocking { db.appLoggerDao().getAll().first() }
        assertEquals("Should keep at most 3 rows for the key", 3, rows.size)
        assertEquals(
            "Most recent timestamps retained",
            listOf(3L, 4L, 5L),
            rows.map { it.timestamp }.sorted(),
        )
    }

    @Test
    fun insertAndPruneKey_doesNotAffectOtherKeys() {
        // High-volume key + a single low-volume key. Cap each to 2.
        for (i in 1..5) {
            db.appLoggerDao().insertAndPruneKey(
                appEvent = AppEvent(eventKey = "noisy", timestamp = i.toLong()),
                limit = 2,
            )
        }
        db.appLoggerDao().insertAndPruneKey(
            appEvent = AppEvent(eventKey = "quiet", timestamp = 100L),
            limit = 2,
        )

        val all = runBlocking { db.appLoggerDao().getAll().first() }
        val noisy = all.filter { it.eventKey == "noisy" }
        val quiet = all.filter { it.eventKey == "quiet" }
        assertEquals("Noisy key capped at 2", 2, noisy.size)
        assertEquals("Quiet key untouched", 1, quiet.size)
    }

    @Test
    fun pruneAllKeys_trimsLegacyRowsForEveryKey() {
        // Simulate the migration case: many existing rows pre-cap.
        for (i in 1..10) {
            db.appLoggerDao().insert(AppEvent(eventKey = "a", timestamp = i.toLong()))
        }
        for (i in 1..7) {
            db.appLoggerDao().insert(AppEvent(eventKey = "b", timestamp = i.toLong()))
        }

        db.appLoggerDao().pruneAllKeys(limit = 4)

        val all = runBlocking { db.appLoggerDao().getAll().first() }
        assertEquals("Both keys trimmed to limit", 8, all.size)
        assertEquals(4, all.count { it.eventKey == "a" })
        assertEquals(4, all.count { it.eventKey == "b" })
    }

    @Test
    fun deleteAllAppEvents_clearsTheTable() {
        db.appLoggerDao().insert(AppEvent(eventKey = "k", timestamp = 1L))
        db.appLoggerDao().insert(AppEvent(eventKey = "k", timestamp = 2L))

        db.appLoggerDao().deleteAllAppEvents()

        val rows = runBlocking { db.appLoggerDao().getAll().first() }
        assertEquals("Table should be empty", 0, rows.size)
    }

    /**
     * Wires through the actual [RoomAppLoggerRepository.log] (not the DAO
     * directly) to prove `log()` honors the per-write cap. Without this
     * test, a future regression that swapped `insertAndPruneKey` back to
     * `insert` inside the repository would pass every other test.
     */
    @Test
    fun roomAppLoggerRepositoryLog_capsAtPerKeyLimit() {
        val repo = RoomAppLoggerRepository(
            appDatabase = db,
            appVersion = "test",
            perKeyLimit = 3,
        )

        runBlocking {
            // Log 5 events for the same key.
            for (i in 1..5) {
                repo.log("repo_key")
            }
        }

        val rows = runBlocking { db.appLoggerDao().getAll().first() }
        assertEquals("Repo log() should cap at perKeyLimit", 3, rows.size)
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

    /**
     * Wires the real [RoomAppLoggerRepository] through
     * [SeedDiagnosticsCountersFromRoomUseCase] to prove the seed
     * correctly groups Room rows by `eventKey` on a real Room DB
     * (catches regressions in the SQL `getAll()` query, the
     * domain-model conversion, and the `groupingBy` orchestration).
     * The DataStore side stays a fake — that layer is well-covered
     * by the use case's unit tests.
     */
    @Test
    fun seedDiagnosticsFromRoom_groupsRealRoomRowsByEventKey() {
        val repo = RoomAppLoggerRepository(
            appDatabase = db,
            appVersion = "test",
            perKeyLimit = 1000,
        )
        val counters = FakeDiagnosticsCountersRepository()

        runBlocking {
            repeat(7) { repo.log("alpha") }
            repeat(3) { repo.log("beta") }
            repo.log("gamma")

            val seeded = SeedDiagnosticsCountersFromRoomUseCase(repo, counters)()

            assertEquals("first call seeds", true, seeded)
            assertEquals(7L, counters.observeCount("alpha").first())
            assertEquals(3L, counters.observeCount("beta").first())
            assertEquals(1L, counters.observeCount("gamma").first())
        }
    }
}
