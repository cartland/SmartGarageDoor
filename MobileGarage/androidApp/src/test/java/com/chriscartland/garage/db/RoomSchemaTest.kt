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

    private val appDatabaseSource = File(
        "../data-local/src/commonMain/kotlin/com/chriscartland/garage/datalocal/AppDatabase.kt",
    )

    private val databaseFactorySource = File(
        "../data-local/src/androidMain/kotlin/com/chriscartland/garage/datalocal/DatabaseFactory.android.kt",
    )

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

    /**
     * Every consecutive schema version pair must have a migration path —
     * either an `@AutoMigration` declared in `AppDatabase.kt` or an
     * explicit `Migration` registered on the database builder.
     *
     * Why this matters: `DatabaseFactory.android.kt` uses
     * `fallbackToDestructiveMigration(false)`, which **drops every
     * Room-managed table** on any version mismatch with no declared
     * migration — not just the changed one. Adding a column with a
     * default value to `AppEvent` without declaring an `@AutoMigration`
     * would silently wipe the user's `DoorEvent` history too. PR #660
     * caught this in review; this test catches it in CI.
     *
     * This is a structural check, not a runtime migration test. It
     * verifies the developer remembered to declare the migration; it
     * does NOT execute the migration. For non-trivial migrations
     * (column renames, type changes), Room rejects auto-migration at
     * build time and forces an explicit `Migration` class — which this
     * test also accepts.
     */
    @Test
    fun everyVersionPairHasDeclaredMigration() {
        require(appDatabaseSource.exists()) {
            "Cannot find AppDatabase.kt at ${appDatabaseSource.absolutePath}. " +
                "If the file moved, update RoomSchemaTest.appDatabaseSource."
        }
        require(databaseFactorySource.exists()) {
            "Cannot find DatabaseFactory.android.kt at ${databaseFactorySource.absolutePath}. " +
                "If the file moved, update RoomSchemaTest.databaseFactorySource."
        }

        val declaredAutoMigrations = parseDeclaredAutoMigrations(appDatabaseSource.readText())
        val declaredCustomMigrations =
            parseCustomMigrationPairs(databaseFactorySource.readText())
        val declared = declaredAutoMigrations + declaredCustomMigrations

        val versions = schemaDir
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            ?: emptyList()
        assertTrue("Need at least one schema file in $schemaDir", versions.isNotEmpty())

        // Verify the chain is continuous from the lowest known schema
        // to the latest. Gaps mean a schema version was deleted in the
        // wrong order (e.g., removed v10 before all users upgraded past
        // it) — the chain would never reach `version = 12` from a
        // device still on v10.
        val gaps = (versions.first()..versions.last()).filterNot { it in versions }
        assertTrue(
            "Schema files form a non-contiguous chain. Missing version JSONs: $gaps. " +
                "Schema files present: $versions. Either restore the missing schemas " +
                "or update the floor (delete older schemas in order from lowest).",
            gaps.isEmpty(),
        )

        // For every consecutive pair, require a declared migration.
        val missing = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until versions.size - 1) {
            val from = versions[i]
            val to = versions[i + 1]
            if ((from to to) !in declared) {
                missing += from to to
            }
        }
        assertTrue(
            buildString {
                appendLine("No migration declared for version pair(s): $missing.")
                appendLine()
                appendLine(
                    "DatabaseFactory.android.kt uses fallbackToDestructiveMigration(false), " +
                        "which drops every table on any version mismatch with no migration. " +
                        "Add one of:",
                )
                appendLine("  1. @AutoMigration(from = N, to = N+1) to AppDatabase.autoMigrations")
                appendLine("     (works for additive, schema-preserving changes — adding columns")
                appendLine("     with defaults, adding tables, adding/removing indexes).")
                appendLine("  2. A Migration class registered via .addMigrations(...) in")
                appendLine("     DatabaseFactory.android.kt (needed for renames, type changes).")
                appendLine()
                appendLine("Schemas found: $versions")
                appendLine("Declared auto-migrations: $declaredAutoMigrations")
                appendLine("Declared custom migrations: $declaredCustomMigrations")
            },
            missing.isEmpty(),
        )
    }

    /**
     * Parses `autoMigrations = [AutoMigration(from = X, to = Y), ...]`
     * out of `AppDatabase.kt`. Tolerant of whitespace and trailing
     * commas; rejects spec-class variants (those carry user-written
     * code, which this test cannot inspect).
     */
    internal fun parseDeclaredAutoMigrations(source: String): Set<Pair<Int, Int>> {
        // Match `AutoMigration(from = N, to = M)` — order-sensitive
        // for now. Room also accepts `AutoMigration(from = N, to = M, spec = SomeSpec::class)`;
        // the regex covers that case by allowing extra args.
        val regex = Regex(
            """AutoMigration\s*\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)""",
        )
        return regex
            .findAll(stripKotlinComments(source))
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toSet()
    }

    /**
     * Parses pairs from `.addMigrations(MIGRATION_X_Y, ...)` calls in
     * `DatabaseFactory.android.kt`. By convention, custom Migrations
     * use the `MIGRATION_<from>_<to>` name pattern.
     */
    internal fun parseCustomMigrationPairs(source: String): Set<Pair<Int, Int>> {
        val regex = Regex("""MIGRATION_(\d+)_(\d+)""")
        return regex
            .findAll(stripKotlinComments(source))
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toSet()
    }

    // Removes Kotlin block and line comments from [source]. Required
    // because a commented-out `AutoMigration(...)` declaration must
    // not be treated as a real declaration — that's the exact bug
    // class this test exists to catch.
    //
    // Block comments use a non-greedy match. Kotlin block comments
    // don't nest — the first close terminates — and this stripper
    // matches that behavior.
    internal fun stripKotlinComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun parserIgnoresCommentedOutAutoMigrations() {
        // Lock down the comment-stripping behavior. Without this,
        // a future "simplify the parser" refactor could silently
        // reintroduce the bug where `// AutoMigration(from = X, to = Y)`
        // counts as a declaration.
        val source =
            """
            autoMigrations = [
                AutoMigration(from = 1, to = 2),
                // AutoMigration(from = 2, to = 3), // commented out!
                /* AutoMigration(from = 3, to = 4), */
            ],
            """.trimIndent()
        val parsed = parseDeclaredAutoMigrations(source)
        assertEquals(setOf(1 to 2), parsed)
    }

    @Test
    fun parserHandlesAutoMigrationWithSpec() {
        // Room supports `AutoMigration(from = X, to = Y, spec = MySpec::class)`.
        // The pair (X, Y) should still parse correctly.
        val source = "AutoMigration(from = 5, to = 6, spec = MyAutoMigrationSpec::class)"
        assertEquals(setOf(5 to 6), parseDeclaredAutoMigrations(source))
    }

    @Test
    fun parserHandlesCustomMigrationConstantNames() {
        val source =
            """
            .addMigrations(
                MIGRATION_3_4,
                MIGRATION_4_5,
                // MIGRATION_5_6 disabled while we figure out the column type
            )
            """.trimIndent()
        val parsed = parseCustomMigrationPairs(source)
        assertEquals(setOf(3 to 4, 4 to 5), parsed)
    }
}
