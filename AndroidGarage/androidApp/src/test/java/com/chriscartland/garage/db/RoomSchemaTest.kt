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

import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the Room database schema is consistent.
 *
 * Room schema changes break at runtime (not compile time). These tests
 * catch schema drift before it reaches a device:
 *
 * - Schema file must exist for the declared version
 * - Entity structure must match what the code defines
 * - DoorPosition enum values must match the type converter expectations
 *
 * If a test fails, you likely need to increment the database version
 * in AppDatabase.kt and commit the new schema JSON.
 */
class RoomSchemaTest {
    @Serializable
    data class SchemaFile(
        val database: SchemaDatabase,
    )

    @Serializable
    data class SchemaDatabase(
        val version: Int,
        val entities: List<SchemaEntity>,
    )

    @Serializable
    data class SchemaEntity(
        val tableName: String,
        val fields: List<SchemaField>,
    )

    @Serializable
    data class SchemaField(
        val columnName: String,
        val affinity: String,
    )

    private val schemaDir = File("../data-local/schemas/com.chriscartland.garage.datalocal.AppDatabase")

    private val json = Json { ignoreUnknownKeys = true }

    private fun latestSchemaVersion(): Int {
        val files = schemaDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        return files.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.maxOrNull() ?: -1
    }

    private fun readSchema(version: Int): SchemaFile {
        val file = File(schemaDir, "$version.json")
        assertTrue("Schema file $version.json must exist", file.exists())
        return json.decodeFromString(file.readText())
    }

    @Test
    fun schemaFileExistsForLatestVersion() {
        val version = latestSchemaVersion()
        assertTrue("No schema files found in $schemaDir", version > 0)
        val file = File(schemaDir, "$version.json")
        assertTrue("Schema file ${file.name} must exist", file.exists())
    }

    @Test
    fun doorEventEntityHasExpectedColumns() {
        val schema = readSchema(latestSchemaVersion())
        val doorEvent = schema.database.entities.find { it.tableName == "DoorEvent" }
        assertNotNull("DoorEvent table must exist in schema", doorEvent)

        val columnNames = doorEvent!!.fields.map { it.columnName }.toSet()

        val expectedColumns = setOf(
            "id",
            "doorPosition",
            "message",
            "lastCheckInTimeSeconds",
            "lastChangeTimeSeconds",
        )
        assertEquals(
            "DoorEvent columns changed — increment database version in AppDatabase.kt",
            expectedColumns,
            columnNames,
        )
    }

    @Test
    fun doorPositionEnumValuesMatchSchemaExpectations() {
        // Room stores DoorPosition as TEXT (enum name). If enum values change,
        // existing database rows become unreadable. This test ensures we don't
        // accidentally rename or remove values.
        val expectedValues = setOf(
            "UNKNOWN",
            "CLOSED",
            "OPENING",
            "OPENING_TOO_LONG",
            "OPEN",
            "OPEN_MISALIGNED",
            "CLOSING",
            "CLOSING_TOO_LONG",
            "ERROR_SENSOR_CONFLICT",
        )
        val actualValues = DoorPosition.entries.map { it.name }.toSet()
        assertEquals(
            "DoorPosition enum values changed — this breaks existing Room data. " +
                "If intentional, increment database version.",
            expectedValues,
            actualValues,
        )
    }

    @Test
    fun appEventEntityExistsInSchema() {
        val schema = readSchema(latestSchemaVersion())
        val appEvent = schema.database.entities.find { it.tableName == "AppEvent" }
        assertNotNull("AppEvent table must exist in schema", appEvent)
    }

    @Test
    fun schemaVersionMatchesDatabaseAnnotation() {
        val schema = readSchema(latestSchemaVersion())
        val jsonVersion = schema.database.version
        val latestFile = latestSchemaVersion()
        assertEquals(
            "Schema JSON filename ($latestFile.json) must match the version inside it ($jsonVersion). " +
                "Rebuild to regenerate schema.",
            latestFile,
            jsonVersion,
        )
    }
}
