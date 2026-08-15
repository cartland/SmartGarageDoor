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
 * Positive controls for the [DataGraph] check functions — one per
 * check, each run against a doctored graph, because a checker that
 * cannot fail is a checker that verifies nothing (the repo's
 * vacuous-pass rule; see "Testing Philosophy" in CLAUDE.md).
 *
 * The REAL graph is not here: since DATA_GRAPH_PLAN.md §6 it is
 * extracted from sources by `DataGraphExtractionKonsistTest`
 * (`:androidApp`), which runs these same checks over the extracted
 * node list and pins the generated `docs/DATA_GRAPH.md` rendering.
 * This suite proves the checks themselves have teeth.
 */
class DataGraphTest {
    // ---- positive controls: each check can actually fail ----

    private val clock = Input(NodeId.NOW_EPOCH_SECONDS, owner = "Clock", cadence = Cadence.CLOCK)
    private val push = Input(NodeId.CURRENT_DOOR_EVENT, owner = "PushRepo", cadence = Cadence.PUSH)
    private val poll = Input(NodeId.WATCH_COMPANION, owner = "PollRepo", cadence = Cadence.POLL)

    private fun derived(
        id: NodeId,
        from: List<NodeId>,
        sharing: Sharing = Sharing.Eager,
        readBy: List<String> = emptyList(),
    ) = Derived(id = id, from = from, shell = "X", sharing = sharing, readBy = readBy)

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
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.EFFECTIVE_SNOOZE_STATE)),
            derived(NodeId.EFFECTIVE_SNOOZE_STATE, from = listOf(NodeId.BUTTON_HEALTH_DISPLAY)),
        )
        assertEquals(
            listOf(NodeId.BUTTON_HEALTH_DISPLAY, NodeId.EFFECTIVE_SNOOZE_STATE),
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
            derived(NodeId.EFFECTIVE_SNOOZE_STATE, from = listOf(NodeId.CURRENT_DOOR_EVENT), readBy = listOf("Screen")),
        )
        val reads = listOf(
            DataGraph.ScreenRead("Screen", "buttonHealthDisplay", NodeId.BUTTON_HEALTH_DISPLAY),
            DataGraph.ScreenRead("Screen", "effectiveSnoozeState", NodeId.EFFECTIVE_SNOOZE_STATE),
        )
        val finding = DataGraph.sharedRootFindings(doctored, reads).single()
        assertEquals("Screen | currentDoorEvent", finding.key)
        assertEquals(listOf("buttonHealthDisplay", "effectiveSnoozeState"), finding.routes)
    }

    @Test
    fun sharedRootCheckSeesADerivedNextToItsOwnInput() {
        // The pre-C4 blind spot: a screen reading a derived node AND one
        // of that node's inputs is the same one-frame hazard as two
        // derived siblings — the diamond must fire.
        val doctored = listOf(
            push,
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.CURRENT_DOOR_EVENT), readBy = listOf("Screen")),
        )
        val reads = listOf(
            DataGraph.ScreenRead("Screen", "buttonHealthDisplay", NodeId.BUTTON_HEALTH_DISPLAY),
            DataGraph.ScreenRead("Screen", "ObserveDoorEventsUseCase.current", NodeId.CURRENT_DOOR_EVENT),
        )
        assertEquals(
            "Screen | currentDoorEvent",
            DataGraph.sharedRootFindings(doctored, reads).single().key,
        )
    }

    @Test
    fun sharedRootCheckSeesTheSameInputReadTwice() {
        // Two independent flows of one id (a StateFlow pass-through and a
        // mapped projection) emit separately — two routes, one root.
        val doctored = listOf<DataGraph.Node>(push)
        val reads = listOf(
            DataGraph.ScreenRead("Screen", "ObserveDoorEventsUseCase.current", NodeId.CURRENT_DOOR_EVENT),
            DataGraph.ScreenRead("Screen", "ObserveDoorEventsUseCase.position", NodeId.CURRENT_DOOR_EVENT),
        )
        assertEquals(
            listOf("ObserveDoorEventsUseCase.current", "ObserveDoorEventsUseCase.position"),
            DataGraph.sharedRootFindings(doctored, reads).single().routes,
        )
        // One route only — no finding.
        assertEquals(emptyList(), DataGraph.sharedRootFindings(doctored, reads.take(1)))
    }

    @Test
    fun aManagerInputCannotLaunderItsUpstreamPastTheClockExemption() {
        // isCheckInStale's shape: a CLOCK-cadence manager input whose
        // impl collects a PUSH input. The CLOCK exemption is per ROOT —
        // the manager's own tick is exempt, its upstream is not.
        val manager = Input(
            NodeId.IS_CHECK_IN_STALE,
            owner = "CheckInStalenessManager",
            cadence = Cadence.CLOCK,
            from = listOf(NodeId.CURRENT_DOOR_EVENT),
        )
        val doctored = listOf(manager, push)
        val reads = listOf(
            DataGraph.ScreenRead("Screen", "isCheckInStale", NodeId.IS_CHECK_IN_STALE),
            DataGraph.ScreenRead("Screen", "ObserveDoorEventsUseCase.current", NodeId.CURRENT_DOOR_EVENT),
        )
        val finding = DataGraph.sharedRootFindings(doctored, reads).single()
        assertEquals("Screen | currentDoorEvent", finding.key)
        // And sourcesOf counts the manager itself plus its upstream.
        assertEquals(setOf(manager, push), DataGraph.sourcesOf(manager, doctored))
    }

    @Test
    fun eagerOverPollCheckCanFail() {
        // An Eager derived node over a POLL source would keep the poll
        // running for the whole process.
        val doctored = listOf(
            poll,
            derived(NodeId.WATCH_APP_STATUS, from = listOf(NodeId.WATCH_COMPANION)),
        )
        assertEquals(
            listOf("watchAppStatus: Eager over poll watchCompanion"),
            DataGraph.eagerOverPolls(doctored),
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
        val reads = listOf(
            DataGraph.ScreenRead("S", "buttonHealthDisplay", NodeId.BUTTON_HEALTH_DISPLAY),
            DataGraph.ScreenRead("S", "watchAppStatus", NodeId.WATCH_APP_STATUS),
            DataGraph.ScreenRead("S", "nowEpochSeconds", NodeId.NOW_EPOCH_SECONDS),
        )
        assertEquals(emptyList(), DataGraph.sharedRootFindings(doctored, reads))
    }

    @Test
    fun aManagerFromEdgeParticipatesInCycleDetection() {
        val manager = Input(
            NodeId.IS_CHECK_IN_STALE,
            owner = "M",
            cadence = Cadence.CLOCK,
            from = listOf(NodeId.BUTTON_HEALTH_DISPLAY),
        )
        val doctored = listOf(
            manager,
            derived(NodeId.BUTTON_HEALTH_DISPLAY, from = listOf(NodeId.IS_CHECK_IN_STALE)),
        )
        assertEquals(
            listOf(NodeId.BUTTON_HEALTH_DISPLAY, NodeId.IS_CHECK_IN_STALE),
            DataGraph.cycleMembers(doctored),
        )
    }

    @Test
    fun sourcesWalkThroughDerivedNodes() {
        // Depth > 1: a derived-over-derived chain resolves to leaf
        // inputs, so a gate two hops above its poll is still valid.
        val doctored = listOf(
            poll,
            derived(NodeId.WATCH_APP_STATUS, from = listOf(NodeId.WATCH_COMPANION)),
            derived(
                NodeId.EFFECTIVE_SNOOZE_STATE,
                from = listOf(NodeId.WATCH_APP_STATUS),
                sharing = Sharing.Gated(poll = NodeId.WATCH_COMPANION),
            ),
        )
        val top = DataGraph.find(NodeId.EFFECTIVE_SNOOZE_STATE, doctored)!!
        assertEquals(setOf(poll), DataGraph.sourcesOf(top, doctored))
        assertEquals(emptyList(), DataGraph.invalidGates(doctored))
    }
}
