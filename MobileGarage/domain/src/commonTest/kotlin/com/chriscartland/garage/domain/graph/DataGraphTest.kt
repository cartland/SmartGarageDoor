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
import com.chriscartland.garage.domain.graph.DataGraph.NodeId
import com.chriscartland.garage.domain.graph.DataGraph.Sharing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coherence checks for the real registry, plus a positive control per
 * check run against a doctored graph — a checker that cannot fail is a
 * checker that verifies nothing (the repo's vacuous-pass rule; see
 * "Testing Philosophy" in CLAUDE.md).
 *
 * Two former checks are gone because the type system now carries them:
 * a dangling edge does not compile ([NodeId] enum references), and a
 * gate without a justification does not compile ([Sharing.Gated]
 * requires its poll). What remains runtime-checkable is checked here;
 * the honesty of entries against SOURCES (owners, transforms, shells,
 * edges) is `DataGraphHonestyKonsistTest` in `:androidApp`.
 */
class DataGraphTest {
    // ---- the real graph is coherent ----

    @Test
    fun everyNodeIdHasExactlyOneEntry() {
        // Bijection with the enum: with no missing and no duplicate
        // ids, every compile-checked edge reference resolves to exactly
        // one entry — closing (and strengthening) what the old
        // dangling-edge check covered.
        assertEquals(emptyList(), DataGraph.missingNodes())
        assertEquals(emptyList(), DataGraph.duplicateIds())
    }

    @Test
    fun graphIsAcyclic() {
        assertEquals(emptyList(), DataGraph.cycleMembers())
    }

    @Test
    fun everyGateNamesARealUpstreamPoll() {
        assertEquals(emptyList(), DataGraph.invalidGates())
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

    private val clock = Input(NodeId.NOW_EPOCH_SECONDS, owner = "Clock", cadence = Cadence.CLOCK)
    private val push = Input(NodeId.CURRENT_DOOR_EVENT, owner = "PushRepo", cadence = Cadence.PUSH)
    private val poll = Input(NodeId.WATCH_COMPANION, owner = "PollRepo", cadence = Cadence.POLL)

    private fun derived(
        id: NodeId,
        from: List<NodeId>,
        sharing: Sharing = Sharing.Eager,
        readBy: List<String> = emptyList(),
    ) = Derived(id = id, from = from, transform = "X.f", shell = "X", sharing = sharing, readBy = readBy)

    @Test
    fun missingNodeCheckCanFail() {
        // A one-entry list leaves every other NodeId missing.
        val doctored = listOf<DataGraph.Node>(push)
        assertTrue(NodeId.WATCH_COMPANION in DataGraph.missingNodes(doctored))
        assertTrue(NodeId.CURRENT_DOOR_EVENT !in DataGraph.missingNodes(doctored))
    }

    @Test
    fun duplicateIdCheckCanFail() {
        val doctored = listOf(push, push.copy(owner = "OtherRepo"))
        assertEquals(listOf(NodeId.CURRENT_DOOR_EVENT), DataGraph.duplicateIds(doctored))
    }

    @Test
    fun cycleCheckCanFail() {
        val doctored = listOf(
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.HOME_DOOR_STATE)),
            derived(NodeId.HOME_DOOR_STATE, from = listOf(NodeId.BUTTON_HEALTH_DISPLAY)),
        )
        assertEquals(
            listOf(NodeId.BUTTON_HEALTH_DISPLAY, NodeId.HOME_DOOR_STATE),
            DataGraph.cycleMembers(doctored),
        )
    }

    @Test
    fun gateCheckCanFailBothWays() {
        // Declared poll not upstream at all:
        val notUpstream = listOf(
            push,
            poll,
            derived(
                NodeId.WATCH_APP_STATUS,
                from = listOf(NodeId.CURRENT_DOOR_EVENT),
                sharing = Sharing.Gated(poll = NodeId.WATCH_COMPANION),
            ),
        )
        assertEquals(
            listOf("watchAppStatus: declared poll watchCompanion is not upstream"),
            DataGraph.invalidGates(notUpstream),
        )
        // Declared poll upstream but not POLL-cadence:
        val notAPoll = listOf(
            push,
            derived(
                NodeId.WATCH_APP_STATUS,
                from = listOf(NodeId.CURRENT_DOOR_EVENT),
                sharing = Sharing.Gated(poll = NodeId.CURRENT_DOOR_EVENT),
            ),
        )
        assertEquals(
            listOf("watchAppStatus: declared poll currentDoorEvent is not POLL-cadence"),
            DataGraph.invalidGates(notAPoll),
        )
    }

    @Test
    fun sharedRootCheckCanFail() {
        val doctored = listOf(
            push,
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.CURRENT_DOOR_EVENT), readBy = listOf("Screen")),
            derived(NodeId.HOME_DOOR_STATE, from = listOf(NodeId.CURRENT_DOOR_EVENT), readBy = listOf("Screen")),
        )
        assertEquals(
            listOf("Screen reads buttonHealthDisplay + homeDoorState over a shared non-clock root"),
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
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.CURRENT_DOOR_EVENT, NodeId.NOW_EPOCH_SECONDS), readBy = listOf("S")),
            derived(
                NodeId.WATCH_APP_STATUS,
                from = listOf(NodeId.WATCH_COMPANION, NodeId.NOW_EPOCH_SECONDS),
                sharing = Sharing.Gated(poll = NodeId.WATCH_COMPANION),
                readBy = listOf("S"),
            ),
        )
        assertEquals(emptyList(), DataGraph.sharedRootViolations(doctored))
    }

    @Test
    fun sourcesWalkThroughDerivedNodes() {
        // Depth > 1: a derived-over-derived chain resolves to leaf
        // inputs, so a gate two hops above its poll is still valid.
        val doctored = listOf(
            poll,
            derived(NodeId.WATCH_APP_STATUS, from = listOf(NodeId.WATCH_COMPANION)),
            derived(
                NodeId.HOME_DOOR_STATE,
                from = listOf(NodeId.WATCH_APP_STATUS),
                sharing = Sharing.Gated(poll = NodeId.WATCH_COMPANION),
            ),
        )
        val top = DataGraph.find(NodeId.HOME_DOOR_STATE, doctored)!!
        assertEquals(setOf(poll), DataGraph.sourcesOf(top, doctored))
        assertEquals(emptyList(), DataGraph.invalidGates(doctored))
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
                NodeId.BUTTON_HEALTH_DISPLAY to listOf("HomeViewModel"),
                NodeId.EFFECTIVE_SNOOZE_STATE to listOf("ProfileViewModel"),
                NodeId.WATCH_APP_STATUS to listOf("ProfileViewModel"),
                NodeId.HOME_DOOR_STATE to listOf("HomeViewModel"),
            ),
            readers,
        )
    }
}
