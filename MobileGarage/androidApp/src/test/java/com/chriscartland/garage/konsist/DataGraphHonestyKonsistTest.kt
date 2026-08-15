/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
 */

package com.chriscartland.garage.konsist

import com.chriscartland.garage.domain.graph.DataGraph
import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the DataGraph registry honest against the sources
 * (docs/DATA_GRAPH_PLAN.md §4).
 *
 * `DataGraphTest` (domain commonTest) checks the registry's internal
 * coherence; this test checks it against reality:
 *  - every `Input.owner`, `Derived.transform`, and `Derived.shell`
 *    names a declaration that exists in production code;
 *  - EDGE ACCURACY (registry ⊆ code): each derived node's shell file
 *    — the class holding its `stateIn` — must actually reference every
 *    declared `from` node (by its camelCase id, or by the source
 *    node's owner class where the shell goes through the owner, e.g.
 *    `WearCompanionRepository` for WATCH_COMPANION) and its transform
 *    call. Comments are stripped first, so prose cannot satisfy the
 *    check.
 *
 * The unchecked direction is stated so it isn't mistaken for covered:
 * an edge present in CODE but missing from the registry (code ⊄
 * registry) is not detected here — that requires parsing combine
 * argument lists and is tracked in DATA_GRAPH_PLAN.md §4.
 *
 * Scope-sanity `require`s follow the repo's Konsist pattern: a filter
 * that matches zero declarations must fail loudly, never pass
 * vacuously (the `file.name` trap, CLAUDE.md § Konsist).
 */
class DataGraphHonestyKonsistTest {
    private val scope = Konsist.scopeFromProduction()

    private fun declarationNames(): Set<String> {
        val names = (scope.classes() + scope.interfaces() + scope.objects())
            .map { it.name }
            .toSet()
        require(names.isNotEmpty()) { "Konsist scope returned no declarations — scope misconfigured" }
        return names
    }

    /** The file declaring [name] as a class/interface/object, or null. */
    private fun fileDeclaring(name: String) =
        scope.files.firstOrNull { file ->
            file.classes(includeNested = true).any { it.name == name } ||
                file.interfaces(includeNested = true).any { it.name == name } ||
                file.objects(includeNested = true).any { it.name == name }
        }

    @Test
    fun `every Input owner names a production class or interface`() {
        val declared = declarationNames()
        val inputs = DataGraph.nodes.filterIsInstance<DataGraph.Input>()
        require(inputs.isNotEmpty()) { "registry has no inputs — registry misconfigured" }

        val missing = inputs
            .filterNot { it.owner in declared }
            .map { "${it.id.id}: owner `${it.owner}` not found in production sources" }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every Derived transform names a production object function`() {
        val objects = scope.objects()
        require(objects.isNotEmpty()) { "Konsist scope returned no objects — scope misconfigured" }

        val transforms = DataGraph.nodes
            .filterIsInstance<DataGraph.Derived>()
            .map { it.transform }
            .filterNot { it == DataGraph.IDENTITY }
        require(transforms.isNotEmpty()) { "registry has no named transforms — registry misconfigured" }

        val missing = transforms.filterNot { transform ->
            val objectName = transform.substringBefore('.')
            val functionName = transform.substringAfter('.')
            objects.any { obj ->
                obj.name == objectName && obj.functions().any { it.name == functionName }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every Derived shell exists and references its transform and every edge`() {
        val derived = DataGraph.nodes.filterIsInstance<DataGraph.Derived>()
        require(derived.isNotEmpty()) { "registry has no derived nodes — registry misconfigured" }
        val byId = DataGraph.nodes.associateBy { it.id }

        val problems = derived.flatMap { d ->
            val shellFile = fileDeclaring(d.shell)
                ?: return@flatMap listOf("${d.id.id}: shell `${d.shell}` not found in production sources")
            val text = stripComments(shellFile.text)
            val issues = mutableListOf<String>()
            if (d.transform != DataGraph.IDENTITY && !text.contains(d.transform)) {
                issues.add("${d.id.id}: shell `${d.shell}` does not call `${d.transform}`")
            }
            d.from.forEach { edge ->
                val source = byId.getValue(edge)
                val anchors = listOfNotNull(
                    edge.id,
                    (source as? DataGraph.Input)?.owner,
                    (source as? DataGraph.Derived)?.shell,
                )
                if (anchors.none(text::contains)) {
                    issues.add("${d.id.id}: shell `${d.shell}` never references edge `${edge.id}` (or its owner)")
                }
            }
            issues
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `every readBy consumer exists and references the node`() {
        val derived = DataGraph.nodes.filterIsInstance<DataGraph.Derived>()
        val problems = derived.flatMap { d ->
            d.readBy.mapNotNull { reader ->
                val readerFile = fileDeclaring(reader)
                    ?: return@mapNotNull "${d.id.id}: reader `$reader` not found in production sources"
                val text = stripComments(readerFile.text)
                val anchors = listOf(d.id.id, d.shell, d.transform.substringBefore('.'))
                if (anchors.none(text::contains)) {
                    "${d.id.id}: reader `$reader` never references the node (id, shell, or transform)"
                } else {
                    null
                }
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    // ---- positive controls (the vacuous-pass rule) ----

    @Test
    fun `the honesty lookups can actually fail`() {
        // A real object with a nonexistent function must NOT be found.
        val found = scope.objects().any { obj ->
            obj.name == "ButtonHealthDisplayLogic" &&
                obj.functions().any { it.name == "definitelyNotARealFunction" }
        }
        assertEquals(false, found)
        // A nonexistent shell must not resolve to a file.
        assertEquals(null, fileDeclaring("DefinitelyNotARealShellClass"))
    }

    @Test
    fun `the edge check reads code, not comments`() {
        // stripComments removes both comment forms; an anchor that only
        // ever appears in prose cannot satisfy the edge check.
        val text =
            """
            // watchCompanion mentioned in prose only
            /* watchCompanion again */
            val real = wearCompanionRepository.observeWatchAppStatus()
            """.trimIndent()
        val stripped = stripComments(text)
        assertTrue(!stripped.contains("watchCompanion"))
        assertTrue(stripped.contains("wearCompanionRepository"))
    }

    /** Remove block + line comments so prose cannot satisfy (or hide) an anchor. */
    private fun stripComments(text: String): String =
        text
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
}
