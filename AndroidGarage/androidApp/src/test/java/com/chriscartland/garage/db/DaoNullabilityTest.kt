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

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Scans Room DAO source files for non-nullable single-row return types.
 *
 * Room throws IllegalStateException at runtime when a query returns no rows
 * but the return type is non-nullable. This is a common bug that only
 * manifests on empty databases (fresh install, cleared data).
 *
 * Rule: Any @Query DAO method returning a single object (not List, not
 * aggregate like Long/Int) must use a nullable return type (T? or Flow<T?>).
 */
class DaoNullabilityTest {
    private val daoDir = File("src/main/java/com/chriscartland/garage")

    @Test
    fun daoQueriesReturningSingleObjectsMustBeNullable() {
        val daoFiles = daoDir
            .walkTopDown()
            .filter { it.name.endsWith("Dao.kt") }
            .toList()

        if (daoFiles.isEmpty()) {
            fail("No DAO files found in $daoDir")
        }

        val violations = daoFiles.flatMap { findNullabilityViolations(it) }

        if (violations.isNotEmpty()) {
            fail(
                "Found ${violations.size} DAO method(s) with non-nullable single-object return types:\n\n" +
                    violations.joinToString("\n\n"),
            )
        }
    }

    private fun findNullabilityViolations(file: File): List<String> {
        val lines = file.readLines()
        return lines.indices
            .filter { lines[it].trim().startsWith("@Query(") }
            .mapNotNull { i ->
                val funLine = findFunLine(lines, i) ?: return@mapNotNull null
                val returnType = extractReturnType(funLine) ?: return@mapNotNull null
                if (isSingleObjectReturn(returnType) && !isNullable(returnType)) {
                    "${file.name}: $funLine\n" +
                        "  Return type '$returnType' should be nullable. " +
                        "Room throws IllegalStateException on empty result with non-nullable types."
                } else {
                    null
                }
            }
    }

    private fun findFunLine(
        lines: List<String>,
        queryLineIndex: Int,
    ): String? {
        // The fun declaration may be on the same line or within the next few lines
        for (j in queryLineIndex..minOf(queryLineIndex + 3, lines.size - 1)) {
            val line = lines[j].trim()
            if (line.startsWith("fun ") || line.contains(" fun ")) {
                return line
            }
        }
        return null
    }

    private fun extractReturnType(funLine: String): String? {
        // Match ): ReturnType or ): ReturnType<Generic>
        val match = Regex("""\):\s*(.+)$""").find(funLine) ?: return null
        return match.groupValues[1].trim()
    }

    private fun isSingleObjectReturn(returnType: String): Boolean {
        // Skip List returns — they return empty list, not null
        if (returnType.contains("List<")) return false
        // Skip aggregate returns (COUNT, SUM, etc. always return a value)
        if (returnType.startsWith("Int") || returnType.startsWith("Long")) return false
        if (returnType.startsWith("Flow<Int") || returnType.startsWith("Flow<Long")) return false
        // Skip Unit/void
        if (returnType == "Unit" || returnType.isEmpty()) return false
        // Everything else is a single-object return
        return true
    }

    private fun isNullable(returnType: String): Boolean {
        // Flow<T?> or T?
        return returnType.endsWith("?>") || returnType.endsWith("?")
    }
}
