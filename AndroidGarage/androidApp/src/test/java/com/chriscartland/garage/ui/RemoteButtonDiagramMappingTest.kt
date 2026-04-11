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

package com.chriscartland.garage.ui

import com.chriscartland.garage.domain.model.RemoteButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteButtonDiagramMappingTest {
    @Test
    fun readyMapsToAllIdle() {
        val result = RemoteButtonState.Ready.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Idle, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun preparingMapsToPhoneActive() {
        val result = RemoteButtonState.Preparing.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun awaitingConfirmationMapsToPhoneActive() {
        val result = RemoteButtonState.AwaitingConfirmation.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun cancelledMapsToAllIdle() {
        val result = RemoteButtonState.Cancelled.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Idle, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun sendingToServerMapsToFirstEdgeInProgress() {
        val result = RemoteButtonState.SendingToServer.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.InProgress, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun sendingToDoorMapsToSecondEdgeInProgress() {
        val result = RemoteButtonState.SendingToDoor.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Succeeded, NodeStatus.Active, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.Succeeded, EdgeStatus.InProgress), result.edges)
    }

    @Test
    fun succeededMapsToAllSucceeded() {
        val result = RemoteButtonState.Succeeded.toNetworkDiagramState()
        assertEquals(
            listOf(NodeStatus.Succeeded, NodeStatus.Succeeded, NodeStatus.Succeeded),
            result.nodes,
        )
        assertEquals(listOf(EdgeStatus.Succeeded, EdgeStatus.Succeeded), result.edges)
    }

    @Test
    fun serverFailedMapsToFirstEdgeFailed() {
        val result = RemoteButtonState.ServerFailed.toNetworkDiagramState()
        assertEquals(listOf(NodeStatus.Failed, NodeStatus.Idle, NodeStatus.Idle), result.nodes)
        assertEquals(listOf(EdgeStatus.Failed, EdgeStatus.NotStarted), result.edges)
    }

    @Test
    fun doorFailedMapsToSecondEdgeFailed() {
        val result = RemoteButtonState.DoorFailed.toNetworkDiagramState()
        assertEquals(
            listOf(NodeStatus.Succeeded, NodeStatus.Failed, NodeStatus.Idle),
            result.nodes,
        )
        assertEquals(listOf(EdgeStatus.Succeeded, EdgeStatus.Failed), result.edges)
    }

    @Test
    fun allStatesProduceValidDiagramState() {
        // Verify every state produces a valid NetworkDiagramState (3 nodes, 2 edges).
        val allStates = listOf(
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
        for (state in allStates) {
            val result = state.toNetworkDiagramState()
            assertEquals("$state should have 3 nodes", 3, result.nodes.size)
            assertEquals("$state should have 2 edges", 2, result.edges.size)
        }
    }
}
