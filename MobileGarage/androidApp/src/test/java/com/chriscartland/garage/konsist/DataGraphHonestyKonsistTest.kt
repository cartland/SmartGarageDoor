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
import org.junit.Test

/**
 * Keeps the DataGraph registry honest against the sources
 * (docs/DATA_GRAPH_PLAN.md §4).
 *
 * `DataGraphTest` (domain commonTest) checks the registry's internal
 * coherence; this test checks it against reality: every `Input.owner`
 * and every `Derived.transform` must name a declaration that actually
 * exists in production code. Without this, the registry is a second
 * source of truth that nothing compares to the first — strictly worse
 * than no registry.
 *
 * Scope-sanity `require`s follow the repo's Konsist pattern: a filter
 * that matches zero declarations must fail loudly, never pass
 * vacuously (the `file.name` trap, CLAUDE.md § Konsist).
 */
class DataGraphHonestyKonsistTest {
    private val scope = Konsist.scopeFromProduction()

    @Test
    fun `every Input owner names a production class or interface`() {
        val declared = (scope.classes() + scope.interfaces() + scope.objects())
            .map { it.name }
            .toSet()
        require(declared.isNotEmpty()) { "Konsist scope returned no declarations — scope misconfigured" }

        val inputs = DataGraph.nodes.filterIsInstance<DataGraph.Input>()
        require(inputs.isNotEmpty()) { "registry has no inputs — registry misconfigured" }

        val missing = inputs
            .filterNot { it.owner in declared }
            .map { "${it.id}: owner `${it.owner}` not found in production sources" }
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
    fun `the honesty check can actually fail`() {
        // Positive control: a transform string that names a real object
        // but a nonexistent function must NOT be found — otherwise the
        // lookup above is matching on something other than what it
        // claims to (the vacuous-pass rule).
        val found = scope.objects().any { obj ->
            obj.name == "ButtonHealthDisplayLogic" &&
                obj.functions().any { it.name == "definitelyNotARealFunction" }
        }
        assertEquals(false, found)
    }
}
