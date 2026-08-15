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

package com.chriscartland.garage.domain.graph

import com.chriscartland.garage.domain.graph.DataGraph.Cadence
import com.chriscartland.garage.domain.graph.DataGraph.Derived
import com.chriscartland.garage.domain.graph.DataGraph.Input
import com.chriscartland.garage.domain.graph.DataGraph.Sharing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coherence checks for the real registry, plus a positive control per
 * check run against a doctored graph — a checker that cannot fail is a
 * checker that verifies nothing (the repo's vacuous-pass rule; see
 * "Testing Philosophy" in CLAUDE.md). The honesty of the entries
 * themselves (owners and transforms exist in sources) is covered by
 * `DataGraphHonestyKonsistTest` in `:androidApp`.
 */
class DataGraphTest {
    // ---- the real graph is coherent ----

    @Test
    fun everyEdgeNamesANodeThatExists() {
        assertEquals(emptyList(), DataGraph.danglingEdges())
    }

    @Test
    fun nodeIdsAreUnique() {
        assertEquals(emptyList(), DataGraph.duplicateIds())
    }

    @Test
    fun graphIsAcyclic() {
        assertEquals(emptyList(), DataGraph.cycleMembers())
    }

    @Test
    fun everyGateIsJustifiedByAPollUpstream() {
        assertEquals(emptyList(), DataGraph.unjustifiedGates())
    }

    @Test
    fun noScreenReadsTwoDerivedNodesOverASharedNonClockRoot() {
        assertEquals(emptyList(), DataGraph.sharedRootViolations())
    }

    @Test
    fun theRegistryIsNotEmpty() {
        // Scope sanity: every check above passes vacuously on an empty
        // node list, so pin that the real registry has substance.
        assertTrue(DataGraph.nodes.filterIsInstance<Input>().isNotEmpty())
        assertTrue(DataGraph.nodes.filterIsInstance<Derived>().isNotEmpty())
    }

    // ---- positive controls: each check can actually fail ----

    private val clock = Input("clock", owner = "Clock", cadence = Cadence.CLOCK)
    private val push = Input("push", owner = "PushRepo", cadence = Cadence.PUSH)
    private val poll = Input("poll", owner = "PollRepo", cadence = Cadence.POLL)

    @Test
    fun danglingEdgeCheckCanFail() {
        val doctored = listOf(
            push,
            Derived("d", from = listOf("missing"), transform = "X.f", sharing = Sharing.EAGER),
        )
        assertEquals(listOf("d <- missing"), DataGraph.danglingEdges(doctored))
    }

    @Test
    fun duplicateIdCheckCanFail() {
        val doctored = listOf(push, push.copy(owner = "OtherRepo"))
        assertEquals(listOf("push"), DataGraph.duplicateIds(doctored))
    }

    @Test
    fun cycleCheckCanFail() {
        val doctored = listOf(
            Derived("a", from = listOf("b"), transform = "X.f", sharing = Sharing.EAGER),
            Derived("b", from = listOf("a"), transform = "X.g", sharing = Sharing.EAGER),
        )
        assertEquals(listOf("a", "b"), DataGraph.cycleMembers(doctored))
    }

    @Test
    fun gatingCheckCanFail() {
        // A GATED node over a push-driven input alone: nothing polls,
        // so the gate is unjustified and must be detected.
        val doctored = listOf(
            push,
            Derived("d", from = listOf("push"), transform = "X.f", sharing = Sharing.GATED),
        )
        assertEquals(listOf("d"), DataGraph.unjustifiedGates(doctored))
    }

    @Test
    fun sharedRootCheckCanFail() {
        val doctored = listOf(
            push,
            Derived("a", listOf("push"), "X.f", Sharing.EAGER, readBy = listOf("Screen")),
            Derived("b", listOf("push"), "X.g", Sharing.EAGER, readBy = listOf("Screen")),
        )
        assertEquals(
            listOf("Screen reads a + b over a shared non-clock root"),
            DataGraph.sharedRootViolations(doctored),
        )
    }

    // ---- semantics the checks depend on ----

    @Test
    fun aClockOnlySharedRootIsExempt() {
        val doctored = listOf(
            clock,
            push,
            poll,
            Derived("a", listOf("push", "clock"), "X.f", Sharing.EAGER, readBy = listOf("S")),
            Derived("b", listOf("poll", "clock"), "X.g", Sharing.GATED, readBy = listOf("S")),
        )
        assertEquals(emptyList(), DataGraph.sharedRootViolations(doctored))
    }

    @Test
    fun sourcesWalkThroughDerivedNodes() {
        // Depth > 1: a derived-over-derived chain resolves to leaf
        // inputs, so a GATED node two hops above a poll is justified.
        val doctored = listOf(
            poll,
            Derived("mid", from = listOf("poll"), transform = "X.f", sharing = Sharing.EAGER),
            Derived("top", from = listOf("mid"), transform = "X.g", sharing = Sharing.GATED),
        )
        val top = DataGraph.find("top", doctored)!!
        assertEquals(setOf(poll), DataGraph.sourcesOf(top, doctored))
        assertEquals(emptyList(), DataGraph.unjustifiedGates(doctored))
    }

    @Test
    fun readByMirrorsTheRealConsumers() {
        // Pins the readBy entries the G7 check keys on. If a ViewModel
        // starts or stops observing a derived node, update BOTH the
        // registry and this test (they drift apart silently otherwise).
        val readers = DataGraph.nodes
            .filterIsInstance<Derived>()
            .associate { it.id to it.readBy }
        assertEquals(
            mapOf(
                "buttonHealthDisplay" to listOf("HomeViewModel"),
                "effectiveSnoozeState" to listOf("ProfileViewModel"),
                "watchAppStatus" to listOf("ProfileViewModel"),
                "homeDoorState" to listOf("HomeViewModel"),
            ),
            readers,
        )
    }
}
