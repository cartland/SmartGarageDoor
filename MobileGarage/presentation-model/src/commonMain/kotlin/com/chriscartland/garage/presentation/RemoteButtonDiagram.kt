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

/** Status of a node (phone, server, door) in the send-progress diagram. */
enum class DiagramNodeStatus { IDLE, ACTIVE, SUCCEEDED, FAILED }

/** Status of an edge (the line between two nodes). */
enum class DiagramEdgeStatus { NOT_STARTED, IN_PROGRESS, SUCCEEDED, FAILED }

/**
 * The phone → server → door diagram as three node statuses and two edge statuses.
 *
 * Ordered phone-first, so index 0 is always the device in the user's hand.
 */
data class RemoteButtonDiagram(
    val phone: DiagramNodeStatus,
    val toServer: DiagramEdgeStatus,
    val server: DiagramNodeStatus,
    val toDoor: DiagramEdgeStatus,
    val door: DiagramNodeStatus,
) {
    val nodes: List<DiagramNodeStatus> get() = listOf(phone, server, door)
    val edges: List<DiagramEdgeStatus> get() = listOf(toServer, toDoor)
}

/**
 * The single source of truth for what the send-progress diagram shows.
 *
 * This is pure structure — there is not a word of user-visible text in it — which
 * is exactly why it belongs here rather than being written once per platform.
 * "Which of four statuses does each of five diagram elements take, in each of
 * nine machine states" is a product decision, and it was previously answered
 * twice: Android in `RemoteButtonDiagramMapping.kt` as a nine-arm table, iOS in
 * `HomeScreen.swift` as five computed properties over a locally-invented `Phase`
 * enum.
 *
 * The two had already diverged structurally even though they rendered the same:
 * iOS's `Phase` collapsed `Preparing` and `AwaitingConfirmation` into one case
 * and represented `Cancelled` as the absence of a phase, so it could not express
 * distinctions Android's table could. That is the shape of drift this prevents —
 * not a visible bug yet, but a representation that cannot hold the full state.
 */
object RemoteButtonDiagramMapper {
    fun forState(state: RemoteButtonState): RemoteButtonDiagram =
        when (state) {
            // Nothing sent yet. Cancelled looks identical to Ready on purpose:
            // the diagram describes an in-flight request, and a cancelled one
            // leaves nothing behind to describe.
            RemoteButtonState.Ready,
            RemoteButtonState.Cancelled,
            -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.IDLE,
                toServer = DiagramEdgeStatus.NOT_STARTED,
                server = DiagramNodeStatus.IDLE,
                toDoor = DiagramEdgeStatus.NOT_STARTED,
                door = DiagramNodeStatus.IDLE,
            )

            // Armed but not sent: the phone lights up, nothing has left it.
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.ACTIVE,
                toServer = DiagramEdgeStatus.NOT_STARTED,
                server = DiagramNodeStatus.IDLE,
                toDoor = DiagramEdgeStatus.NOT_STARTED,
                door = DiagramNodeStatus.IDLE,
            )

            RemoteButtonState.SendingToServer -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.ACTIVE,
                toServer = DiagramEdgeStatus.IN_PROGRESS,
                server = DiagramNodeStatus.IDLE,
                toDoor = DiagramEdgeStatus.NOT_STARTED,
                door = DiagramNodeStatus.IDLE,
            )

            RemoteButtonState.SendingToDoor -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.SUCCEEDED,
                toServer = DiagramEdgeStatus.SUCCEEDED,
                server = DiagramNodeStatus.ACTIVE,
                toDoor = DiagramEdgeStatus.IN_PROGRESS,
                door = DiagramNodeStatus.IDLE,
            )

            RemoteButtonState.Succeeded -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.SUCCEEDED,
                toServer = DiagramEdgeStatus.SUCCEEDED,
                server = DiagramNodeStatus.SUCCEEDED,
                toDoor = DiagramEdgeStatus.SUCCEEDED,
                door = DiagramNodeStatus.SUCCEEDED,
            )

            // The request never reached the server, so the server node stays
            // idle — it has no opinion about a message it never saw.
            RemoteButtonState.ServerFailed -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.FAILED,
                toServer = DiagramEdgeStatus.FAILED,
                server = DiagramNodeStatus.IDLE,
                toDoor = DiagramEdgeStatus.NOT_STARTED,
                door = DiagramNodeStatus.IDLE,
            )

            // The server took it and the door did not answer: the first leg
            // succeeded, the second failed.
            RemoteButtonState.DoorFailed -> RemoteButtonDiagram(
                phone = DiagramNodeStatus.SUCCEEDED,
                toServer = DiagramEdgeStatus.SUCCEEDED,
                server = DiagramNodeStatus.FAILED,
                toDoor = DiagramEdgeStatus.FAILED,
                door = DiagramNodeStatus.IDLE,
            )
        }
}
