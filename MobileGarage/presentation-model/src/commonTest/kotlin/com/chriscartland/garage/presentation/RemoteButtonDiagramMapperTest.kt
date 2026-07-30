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
 *
 */

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.RemoteButtonState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The send-progress diagram's shape, now that both platforms read it from here.
 *
 * These assert the properties that make the diagram *mean* something, rather
 * than restating the table row by row — a test that just mirrors the mapping
 * would fail for every intentional change and catch none of the interesting
 * mistakes.
 */
class RemoteButtonDiagramMapperTest {
    private fun diagram(state: RemoteButtonState) = RemoteButtonDiagramMapper.forState(state)

    @Test
    fun everyStateProducesThreeNodesAndTwoEdges() {
        // Guards the shape the renderers index into on both platforms.
        for (state in allStates) {
            val d = diagram(state)
            assertEquals(3, d.nodes.size, "nodes for $state")
            assertEquals(2, d.edges.size, "edges for $state")
        }
    }

    @Test
    fun nothingIsInFlightBeforeSending() {
        for (state in listOf(
            RemoteButtonState.Ready,
            RemoteButtonState.Cancelled,
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
        )) {
            val d = diagram(state)
            assertTrue(
                d.edges.all { it == DiagramEdgeStatus.NOT_STARTED },
                "$state should have no edge in flight",
            )
        }
    }

    @Test
    fun armingLightsThePhoneWithoutLeavingIt() {
        // iOS previously collapsed these two into one case; keeping them
        // distinct here is what lets a platform tell them apart if it wants to.
        for (state in listOf(RemoteButtonState.Preparing, RemoteButtonState.AwaitingConfirmation)) {
            val d = diagram(state)
            assertEquals(DiagramNodeStatus.ACTIVE, d.phone, "phone for $state")
            assertEquals(DiagramEdgeStatus.NOT_STARTED, d.toServer, "toServer for $state")
        }
    }

    @Test
    fun aServerFailureLeavesTheServerWithNoOpinion() {
        // The request never arrived, so the server node must not claim a verdict.
        val d = diagram(RemoteButtonState.ServerFailed)
        assertEquals(DiagramEdgeStatus.FAILED, d.toServer)
        assertEquals(DiagramNodeStatus.IDLE, d.server)
        assertEquals(DiagramNodeStatus.IDLE, d.door)
    }

    @Test
    fun aDoorFailureKeepsTheFirstLegSucceeded() {
        // This is the distinction the button copy now also makes: the server
        // took it, the door did not move.
        val d = diagram(RemoteButtonState.DoorFailed)
        assertEquals(DiagramEdgeStatus.SUCCEEDED, d.toServer)
        assertEquals(DiagramEdgeStatus.FAILED, d.toDoor)
    }

    @Test
    fun successIsTheOnlyStateWhereEverythingSucceeded() {
        for (state in allStates) {
            val d = diagram(state)
            val allGood = d.nodes.all { it == DiagramNodeStatus.SUCCEEDED } &&
                d.edges.all { it == DiagramEdgeStatus.SUCCEEDED }
            assertEquals(state == RemoteButtonState.Succeeded, allGood, "all-succeeded for $state")
        }
    }

    @Test
    fun failureNeverAppearsBeforeItsCause() {
        // A later element cannot fail while an earlier one has not been tried.
        for (state in allStates) {
            val d = diagram(state)
            if (d.toDoor == DiagramEdgeStatus.FAILED) {
                assertEquals(DiagramEdgeStatus.SUCCEEDED, d.toServer, "toServer for $state")
            }
        }
    }

    private val allStates = listOf(
        RemoteButtonState.Ready,
        RemoteButtonState.Preparing,
        RemoteButtonState.AwaitingConfirmation,
        RemoteButtonState.Cancelled,
        RemoteButtonState.SendingToServer,
        RemoteButtonState.SendingToDoor,
        RemoteButtonState.Succeeded,
        RemoteButtonState.ServerFailed,
        RemoteButtonState.DoorFailed,
    )
}
