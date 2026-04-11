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

/**
 * Maps [RemoteButtonState] to [NetworkDiagramState] for the 3-node
 * Phone → Server → Door diagram.
 */
fun RemoteButtonState.toNetworkDiagramState(): NetworkDiagramState =
    when (this) {
        RemoteButtonState.Ready -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Idle, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted),
        )
        RemoteButtonState.Preparing -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted),
        )
        RemoteButtonState.AwaitingConfirmation -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted),
        )
        RemoteButtonState.Cancelled -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Idle, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted),
        )
        RemoteButtonState.SendingToServer -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.InProgress, EdgeStatus.NotStarted),
        )
        RemoteButtonState.SendingToDoor -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Succeeded, NodeStatus.Active, NodeStatus.Idle),
            edges = listOf(EdgeStatus.Succeeded, EdgeStatus.InProgress),
        )
        RemoteButtonState.Succeeded -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Succeeded, NodeStatus.Succeeded, NodeStatus.Succeeded),
            edges = listOf(EdgeStatus.Succeeded, EdgeStatus.Succeeded),
        )
        RemoteButtonState.ServerFailed -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Failed, NodeStatus.Idle, NodeStatus.Idle),
            edges = listOf(EdgeStatus.Failed, EdgeStatus.NotStarted),
        )
        RemoteButtonState.DoorFailed -> NetworkDiagramState(
            nodes = listOf(NodeStatus.Succeeded, NodeStatus.Failed, NodeStatus.Idle),
            edges = listOf(EdgeStatus.Succeeded, EdgeStatus.Failed),
        )
    }
