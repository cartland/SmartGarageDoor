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
import com.chriscartland.garage.domain.graph.DataGraph.Cadence
import com.chriscartland.garage.domain.graph.DataGraph.NodeId
import com.chriscartland.garage.domain.graph.DataGraph.Sharing
import com.chriscartland.garage.konsist.DataGraphExtraction.InputConsumption
import com.chriscartland.garage.konsist.DataGraphExtraction.RawDerived
import com.chriscartland.garage.konsist.DataGraphExtraction.RawInput
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_FLOW_PROP
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_IN
import com.chriscartland.garage.konsist.DataGraphExtraction.annotationSites
import com.chriscartland.garage.konsist.DataGraphExtraction.assembleGraph
import com.chriscartland.garage.konsist.DataGraphExtraction.attributeReader
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitEntry
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitMethodReadsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.constructorParams
import com.chriscartland.garage.konsist.DataGraphExtraction.derivedNodeId
import com.chriscartland.garage.konsist.DataGraphExtraction.directInputPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.edgeIds
import com.chriscartland.garage.konsist.DataGraphExtraction.illegalStateIns
import com.chriscartland.garage.konsist.DataGraphExtraction.interfaceFlowMembers
import com.chriscartland.garage.konsist.DataGraphExtraction.managerFromIds
import com.chriscartland.garage.konsist.DataGraphExtraction.nodeSweepProblems
import com.chriscartland.garage.konsist.DataGraphExtraction.orphanConduits
import com.chriscartland.garage.konsist.DataGraphExtraction.parseNodeExemptions
import com.chriscartland.garage.konsist.DataGraphExtraction.parseSharedRootExemptions
import com.chriscartland.garage.konsist.DataGraphExtraction.render
import com.chriscartland.garage.konsist.DataGraphExtraction.sharingName
import com.chriscartland.garage.konsist.DataGraphExtraction.stripComments
import com.chriscartland.garage.konsist.DataGraphExtraction.unreadDeriveds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Positive controls for [DataGraphExtraction] — every parser, assembly
 * rule, sweep, and the renderer, each proven able to fire on doctored
 * input (the repo's vacuous-pass rule). No Konsist scope and no
 * filesystem here, which is exactly why these can feed doctored text:
 * the pure functions take plain values. The scope-bound orchestration,
 * the real-graph checks, and the rendering pin live in
 * [DataGraphExtractionKonsistTest].
 */
class DataGraphExtractionControlsTest {
    @Test
    fun `annotation parsing extracts owner, cadence, and id override — but never from comments`() {
        val text = stripComments(
            """
            interface WearCompanionRepository {
                // prose mentioning @NodeCadence(Cadence.CLOCK) must not count
                @NodeCadence(Cadence.POLL, id = "watchCompanion")
                fun observeWatchAppStatus(): Flow<WatchAppStatus>
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(RawInput("WearCompanionRepository", "observeWatchAppStatus", Cadence.POLL, "watchCompanion")),
            annotationSites(text),
        )
    }

    @Test
    fun `the nearest preceding type is the owner`() {
        // The LiveClock file shape: annotated interface declaration,
        // un-annotated override in a sibling class — one site, owned by
        // the interface.
        val text =
            """
            interface LiveClock {
                @NodeCadence(Cadence.CLOCK)
                val nowEpochSeconds: StateFlow<Long>
            }
            class DefaultLiveClock : LiveClock {
                override val nowEpochSeconds: StateFlow<Long> = someFlow
            }
            """.trimIndent()
        assertEquals(
            listOf(RawInput("LiveClock", "nowEpochSeconds", Cadence.CLOCK, "nowEpochSeconds")),
            annotationSites(text),
        )
    }

    @Test
    fun `edges come from the flow expression, never the stateIn seed`() {
        val stripped = stripComments(
            """
            class ComputeThingUseCase(
                thingRepository: ThingRepository,
                otherRepository: OtherRepository,
                liveClock: LiveClock,
                applicationScope: CoroutineScope,
            ) {
                private val state: StateFlow<Thing> =
                    combine(
                        thingRepository.thing,
                        liveClock
                            .nowEpochSeconds,
                    ) { t, now -> ThingLogic.compute(t, now) }
                        .stateIn(
                            scope = applicationScope,
                            started = SharingStarted.Eagerly,
                            initialValue = otherRepository.other.value,
                        )
            }
            """.trimIndent(),
        )
        val params = constructorParams(stripped, "ComputeThingUseCase")
        assertEquals(
            listOf(
                "thingRepository" to "ThingRepository",
                "otherRepository" to "OtherRepository",
                "liveClock" to "LiveClock",
                "applicationScope" to "CoroutineScope",
            ),
            params,
        )
        val split = STATE_IN.find(stripped)!!.range.first
        val owners = mapOf(
            "ThingRepository" to listOf("thing" to "thingNode"),
            "OtherRepository" to listOf("other" to "otherNode"),
            "LiveClock" to listOf("nowEpochSeconds" to "nowEpochSeconds"),
        )
        // The multiline `liveClock\n.nowEpochSeconds` reference counts;
        // the seed-only `otherRepository.other` does not.
        assertEquals(
            setOf("thingNode", "nowEpochSeconds"),
            edgeIds(stripped.substring(0, split), params, owners, emptyMap()),
        )
        assertEquals("Eagerly", sharingName(stripped.substring(split)))
        // The stateIn property's type argument is the node's output type.
        assertEquals(
            "Thing",
            STATE_FLOW_PROP
                .findAll(stripped.substring(0, split))
                .lastOrNull()
                ?.groupValues
                ?.get(1),
        )
    }

    @Test
    fun `a derived reading another derived is an edge, including via invoke`() {
        val params = listOf("upstream" to "ObserveUpstreamUseCase")
        val derivedIdByClass = mapOf("ObserveUpstreamUseCase" to "upstream")
        assertEquals(setOf("upstream"), edgeIds("upstream().map { it }", params, emptyMap(), derivedIdByClass))
        assertEquals(setOf("upstream"), edgeIds("upstream.status", params, emptyMap(), derivedIdByClass))
        assertEquals(emptySet<String>(), edgeIds("noReferenceHere", params, emptyMap(), derivedIdByClass))
    }

    @Test
    fun `reader attribution fires by shell type or by named output value, and only those`() {
        val deriveds = listOf(
            RawDerived("ComputeButtonHealthDisplayUseCase", emptySet(), "Eagerly", outputType = "ButtonHealthDisplay"),
            RawDerived("ComputeEffectiveSnoozeStateUseCase", emptySet(), "Eagerly", outputType = "SnoozeState"),
        )
        // Route (a): a constructor parameter typed as the shell class.
        assertEquals(
            "ComputeEffectiveSnoozeStateUseCase",
            attributeReader(deriveds, "anyName", "ComputeEffectiveSnoozeStateUseCase"),
        )
        // Route (b): StateFlow<output> AND named after the node.
        assertEquals(
            "ComputeButtonHealthDisplayUseCase",
            attributeReader(deriveds, "buttonHealthDisplay", "StateFlow<ButtonHealthDisplay>"),
        )
        // A StateFlow of the output type NOT named after the node is not
        // attributed (it could be any flow of that domain type).
        assertNull(attributeReader(deriveds, "someOtherFlow", "StateFlow<ButtonHealthDisplay>"))
        // An ambiguous output type (two nodes emit it) never attributes.
        val ambiguous = deriveds + RawDerived("ObserveOtherUseCase", emptySet(), "Eagerly", outputType = "SnoozeState")
        assertNull(attributeReader(ambiguous, "effectiveSnoozeState", "StateFlow<SnoozeState>"))
        // An unrelated type is not attributed.
        assertNull(attributeReader(deriveds, "x", "DispatcherProvider"))
    }

    @Test
    fun `assembly rejects DERIVED cadence, unknown ids, gateless polls, and missing sharing`() {
        val (_, derivedCadence) = assembleGraph(
            listOf(RawInput("X", "x", Cadence.DERIVED, "authState")),
            emptyList(),
        )
        assertTrue(derivedCadence.any { it.contains("DERIVED is not a declarable cadence") })

        val (_, unknownId) = assembleGraph(
            listOf(RawInput("X", "x", Cadence.PUSH, "notANode")),
            emptyList(),
        )
        assertTrue(unknownId.any { it.contains("no NodeId has id `notANode`") })

        val (_, gateless) = assembleGraph(
            listOf(RawInput("SnoozeRepository", "snoozeState", Cadence.USER_ACTION, "snoozeState")),
            listOf(RawDerived("ComputeEffectiveSnoozeStateUseCase", setOf("snoozeState"), "WhileSubscribed")),
        )
        assertTrue(gateless.any { it.contains("WhileSubscribed with no POLL-cadence source upstream") })

        val (_, noSharing) = assembleGraph(
            emptyList(),
            listOf(RawDerived("ComputeEffectiveSnoozeStateUseCase", emptySet(), null)),
        )
        assertTrue(noSharing.any { it.contains("no SharingStarted literal") })
    }

    @Test
    fun `assembly resolves a gate to its upstream poll, manager edges, and readers`() {
        val (nodes, problems) = assembleGraph(
            listOf(
                RawInput("WearCompanionRepository", "observeWatchAppStatus", Cadence.POLL, "watchCompanion"),
                RawInput(
                    "CheckInStalenessManager",
                    "isCheckInStale",
                    Cadence.CLOCK,
                    "isCheckInStale",
                    fromIds = setOf("watchCompanion"),
                ),
            ),
            listOf(RawDerived("ObserveWatchAppStatusUseCase", setOf("watchCompanion"), "WhileSubscribed")),
            readersByShell = mapOf("ObserveWatchAppStatusUseCase" to listOf("ProfileViewModel")),
        )
        assertEquals(emptyList<String>(), problems)
        val derived = nodes.filterIsInstance<DataGraph.Derived>().single()
        assertEquals(Sharing.Gated(poll = NodeId.WATCH_COMPANION), derived.sharing)
        assertEquals(listOf("ProfileViewModel"), derived.readBy)
        // The manager's fromIds resolve into Input.from.
        val manager = nodes.filterIsInstance<DataGraph.Input>().single { it.id == NodeId.IS_CHECK_IN_STALE }
        assertEquals(listOf(NodeId.WATCH_COMPANION), manager.from)
    }

    @Test
    fun `the derived-id naming convention resolves each shell shape`() {
        assertEquals("buttonHealthDisplay", derivedNodeId("ComputeButtonHealthDisplayUseCase"))
        assertEquals("watchAppStatus", derivedNodeId("ObserveWatchAppStatusUseCase"))
        assertEquals("somethingNew", derivedNodeId("SomethingNewUseCase"))
    }

    @Test
    fun `the rendering is deterministic and complete for a known graph`() {
        val nodes = listOf(
            DataGraph.Input(NodeId.CURRENT_DOOR_EVENT, owner = "DoorRepository", cadence = Cadence.PUSH),
            DataGraph.Input(
                NodeId.IS_CHECK_IN_STALE,
                owner = "CheckInStalenessManager",
                cadence = Cadence.CLOCK,
                from = listOf(NodeId.CURRENT_DOOR_EVENT),
            ),
            DataGraph.Derived(
                id = NodeId.BUTTON_HEALTH_DISPLAY,
                from = listOf(NodeId.CURRENT_DOOR_EVENT),
                shell = "ComputeButtonHealthDisplayUseCase",
                sharing = Sharing.Eager,
                readBy = listOf("HomeViewModel"),
            ),
        )
        val consumption = InputConsumption(
            conduits = listOf(
                DataGraphExtraction.Conduit(
                    "ObserveDoorEventsUseCase",
                    mapOf("current" to setOf("currentDoorEvent")),
                ),
            ),
            conduitReaders = mapOf("ObserveDoorEventsUseCase" to listOf("HistoryViewModel")),
            reads = listOf(
                DataGraph.ScreenRead("HistoryViewModel", "ObserveDoorEventsUseCase.current", NodeId.CURRENT_DOOR_EVENT),
                DataGraph.ScreenRead("HomeViewModel", "isCheckInStale", NodeId.IS_CHECK_IN_STALE),
            ),
        )
        val expected =
            """
            <!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.
                 Regenerate: ./scripts/generate-data-graph.sh
                 The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->

            # Shared data graph

            | Node | Kind | Cadence | Declared by | Sharing | Read by |
            |---|---|---|---|---|---|
            | `currentDoorEvent` | input | PUSH | `DoorRepository` | — | `HistoryViewModel` (current) |
            | `isCheckInStale` | input | CLOCK | `CheckInStalenessManager` | — | `HomeViewModel` (direct) |
            | `buttonHealthDisplay` | derived | — | `ComputeButtonHealthDisplayUseCase` | eager | `HomeViewModel` |

            **Cadence** — what makes a value change. `USER_ACTION`: written only when
            the user or the app explicitly acts (a tap, a fetch). `PUSH`:
            server-initiated (FCM), can land at any time. `POLL`: a fixed-interval
            collection loop, the only cadence that justifies gating. `CLOCK`: an
            always-on tick.

            **Read by** — the screens that reactively observe each value, with the
            conduit method in parentheses (`direct` = an injected manager or value).
            A dash means no screen observes it: the value feeds a derived node (see
            diagram) or is consumed at action time inside the data layer.
            `DiagnosticsViewModel` reads no graph nodes; Wear wires a subset of the
            inputs and none of the derived nodes.

            ```mermaid
            graph LR
                currentDoorEvent(["currentDoorEvent · PUSH"])
                isCheckInStale(["isCheckInStale · CLOCK"])
                buttonHealthDisplay["buttonHealthDisplay"]
                ObserveDoorEventsUseCase[["ObserveDoorEventsUseCase"]]
                HistoryViewModel{{"HistoryViewModel"}}
                HomeViewModel{{"HomeViewModel"}}
                currentDoorEvent --> ObserveDoorEventsUseCase
                ObserveDoorEventsUseCase --> HistoryViewModel
                isCheckInStale --> HomeViewModel
                currentDoorEvent -. reacts .-> isCheckInStale
                currentDoorEvent --> buttonHealthDisplay
                buttonHealthDisplay --> HomeViewModel
            ```

            **Diagram legend** — `([x])` input (cadence-labeled) · `[x]` derived
            (`stateIn` UseCase) · `[[X]]` conduit (ADR-022 pass-through) · `{{X}}`
            screen ViewModel · `-. reacts .->` a manager's reactive upstream ·
            `-. poll, gated .->` a gated poll. Conduit → ViewModel edges are
            class-level; the table's Read by column is the per-value truth.
            """.trimIndent() + "\n"
        assertEquals(expected, render(nodes, consumption))
    }

    @Test
    fun `conduits are Observe pass-throughs that reference an input — nothing else qualifies`() {
        val decls = mapOf(
            "DoorRepository" to listOf(
                "currentDoorEvent" to "currentDoorEvent",
                "paginationState" to "paginationState",
            ),
        )
        val passThrough =
            """
            class ObserveDoorEventsUseCase(
                private val doorRepository: DoorRepository,
            ) {
                fun current(): StateFlow<DoorEvent?> = doorRepository.currentDoorEvent

                fun paginationState(): StateFlow<PaginationState> = doorRepository.paginationState

                fun position(): Flow<DoorPosition> =
                    doorRepository.currentDoorEvent
                        .map { it?.doorPosition ?: DoorPosition.UNKNOWN }
            }
            """.trimIndent()
        // METHOD-PRECISE: each fun resolves only what its own body reads.
        assertEquals(
            DataGraphExtraction.Conduit(
                "ObserveDoorEventsUseCase",
                mapOf(
                    "current" to setOf("currentDoorEvent"),
                    "paginationState" to setOf("paginationState"),
                    "position" to setOf("currentDoorEvent"),
                ),
            ),
            conduitEntry(passThrough, emptySet(), decls),
        )
        // An action UseCase reading `.value` is not observation — the
        // Observe prefix is the filter.
        val action = passThrough.replace("ObserveDoorEventsUseCase", "FetchDoorEventsUseCase")
        assertNull(conduitEntry(action, emptySet(), decls))
        // An Observe class that references no input exposes nothing.
        val idle = "class ObserveIdleUseCase(\n    private val other: OtherThing,\n) { fun x() = other.y }"
        assertNull(conduitEntry(idle, emptySet(), decls))
        // A stateIn holder is a derived node, never a conduit.
        val derived = passThrough.replace(
            "fun current(): StateFlow<DoorEvent?> = doorRepository.currentDoorEvent",
            "fun current(): StateFlow<DoorEvent?> = doorRepository.currentDoorEvent.stateIn(s)",
        )
        assertNull(conduitEntry(derived, emptySet(), decls))
        // `operator fun invoke` is the method `invoke`.
        val invokeStyle =
            """
            class ObserveAuthStateUseCase(
                private val repo: DoorRepository,
            ) {
                operator fun invoke(): StateFlow<DoorEvent?> = repo.currentDoorEvent
            }
            """.trimIndent()
        assertEquals(
            DataGraphExtraction.Conduit("ObserveAuthStateUseCase", mapOf("invoke" to setOf("currentDoorEvent"))),
            conduitEntry(invokeStyle, emptySet(), decls),
        )
    }

    @Test
    fun `input consumption pairs fire for conduit injection and direct owner reads`() {
        val decls = mapOf("CheckInStalenessManager" to listOf("isCheckInStale" to "isCheckInStale"))
        val conduit = DataGraphExtraction.Conduit(
            "ObserveDoorEventsUseCase",
            mapOf(
                "current" to setOf("currentDoorEvent"),
                "position" to setOf("currentDoorEvent"),
                "paginationState" to setOf("paginationState"),
            ),
        )
        val vmText =
            """
            class DefaultHomeViewModel(
                observeDoorEvents: ObserveDoorEventsUseCase,
                private val checkInStalenessManager: CheckInStalenessManager,
                private val other: SomethingElse,
            ) {
                val current = observeDoorEvents.current()
                val machine = Machine(observeDoorEvents.position())
                val stale = checkInStalenessManager.isCheckInStale
            }
            """.trimIndent()
        assertEquals(
            listOf("ObserveDoorEventsUseCase" to "HomeViewModel"),
            conduitPairsIn(vmText, setOf("ObserveDoorEventsUseCase")),
        )
        // METHOD-PRECISE reads: current() and position() are two routes to
        // the same node; the un-called paginationState() is NOT a read.
        assertEquals(
            listOf(
                Triple("HomeViewModel", "ObserveDoorEventsUseCase.current", "currentDoorEvent"),
                Triple("HomeViewModel", "ObserveDoorEventsUseCase.position", "currentDoorEvent"),
            ),
            conduitMethodReadsIn(vmText, listOf(conduit)).sortedBy { it.second },
        )
        assertEquals(
            listOf("isCheckInStale" to "HomeViewModel"),
            directInputPairsIn(vmText, decls),
        )
        // An owner injected but never referenced yields no direct edge.
        val unreferenced = vmText.replace("checkInStalenessManager.isCheckInStale", "0")
        assertEquals(emptyList<Pair<String, String>>(), directInputPairsIn(unreferenced, decls))
    }

    @Test
    fun `a manager's reactive upstream is extracted, minus itself`() {
        // The DefaultCheckInStalenessManager shape: interface declares the
        // input, the impl collects a conduit method. Its own id never
        // becomes its own upstream.
        val decls = mapOf("CheckInStalenessManager" to listOf("isCheckInStale" to "isCheckInStale"))
        val conduit = DataGraphExtraction.Conduit(
            "ObserveDoorEventsUseCase",
            mapOf("current" to setOf("currentDoorEvent")),
        )
        val managerText =
            """
            interface CheckInStalenessManager {
                val isCheckInStale: StateFlow<Boolean>
            }
            class DefaultCheckInStalenessManager(
                private val observeDoorEvents: ObserveDoorEventsUseCase,
                private val scope: CoroutineScope,
            ) : CheckInStalenessManager {
                override val isCheckInStale: StateFlow<Boolean> = staleFlow
                fun start() {
                    scope.launch { observeDoorEvents.current().collect { } }
                }
            }
            """.trimIndent()
        assertEquals(
            setOf("currentDoorEvent"),
            managerFromIds(managerText, listOf(conduit), decls, ownIds = setOf("isCheckInStale")),
        )
        // A manager that collects nothing has no upstream (LiveClock).
        val clockText = "class DefaultLiveClock(\n    private val scope: CoroutineScope,\n) { }"
        assertEquals(
            emptySet<String>(),
            managerFromIds(clockText, listOf(conduit), decls, ownIds = setOf("nowEpochSeconds")),
        )
    }

    @Test
    fun `shared-root exemptions parse three parts and demand reasons`() {
        assertEquals(
            mapOf("HomeViewModel | authState" to "adjudicated"),
            parseSharedRootExemptions("# c\n\nHomeViewModel | authState | adjudicated\n"),
        )
        assertTrue(runCatching { parseSharedRootExemptions("HomeViewModel | authState") }.isFailure)
        assertTrue(runCatching { parseSharedRootExemptions("HomeViewModel | authState |") }.isFailure)
    }

    @Test
    fun `the flow-member universe sees interfaces and only interfaces`() {
        val text = stripComments(
            """
            interface DoorRepository {
                val currentDoorEvent: StateFlow<DoorEvent?>
                fun observeWatchAppStatus(): Flow<WatchAppStatus>
                fun countKey(key: String): Flow<Long>
                suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, FetchError>
            }
            class NetworkDoorRepository : DoorRepository {
                override val currentDoorEvent: StateFlow<DoorEvent?> = someFlow
            }
            """.trimIndent(),
        )
        // The val and the parameterless fun are universe members; the keyed
        // fun, the non-flow fun, and the class override are not.
        assertEquals(
            listOf(
                "DoorRepository" to "currentDoorEvent",
                "DoorRepository" to "observeWatchAppStatus",
            ),
            interfaceFlowMembers(text),
        )
    }

    @Test
    fun `the node sweep can fail both ways, and exemptions demand reasons`() {
        val members = listOf("Repo" to "annotated", "Repo" to "forgotten", "Repo" to "excused")
        val annotated = setOf("Repo" to "annotated")
        val (missing, stale) = nodeSweepProblems(members, annotated, setOf("Repo.excused", "Repo.gone"))
        assertEquals(listOf("Repo.forgotten"), missing)
        assertEquals(listOf("Repo.gone"), stale)
        // An annotated member's leftover exemption is stale too.
        val (_, nowANode) = nodeSweepProblems(members, annotated, setOf("Repo.annotated"))
        assertEquals(listOf("Repo.annotated"), nowANode)
        // Parsing: comments and blanks ignored; a reason is mandatory.
        assertEquals(
            mapOf("A.b" to "keyed stream"),
            parseNodeExemptions("# header\n\nA.b | keyed stream\n"),
        )
        val noReason = runCatching { parseNodeExemptions("A.b") }
        assertTrue("an exemption without a reason must be rejected", noReason.isFailure)
    }

    @Test
    fun `stateIn placement rules fire per module`() {
        val appScoped = "val x = flow.stateIn(scope = applicationScope, started = SharingStarted.Eagerly, initialValue = y)"
        val vmScoped = "val x = flow.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = y)"
        // :usecase is the legal home, whatever the scope.
        assertEquals(emptyList<String>(), illegalStateIns("/usecase/src/commonMain/X.kt", appScoped))
        // Repositories and the other shared modules: never.
        assertEquals(1, illegalStateIns("/data/src/commonMain/X.kt", appScoped).size)
        assertEquals(1, illegalStateIns("/domain/src/commonMain/X.kt", vmScoped).size)
        // :viewmodel: only on viewModelScope (a G0 sink).
        assertEquals(emptyList<String>(), illegalStateIns("/viewmodel/src/commonMain/X.kt", vmScoped))
        assertEquals(1, illegalStateIns("/viewmodel/src/commonMain/X.kt", appScoped).size)
        // No stateIn, no violation.
        assertEquals(emptyList<String>(), illegalStateIns("/data/src/commonMain/X.kt", "val x = 1"))
    }

    @Test
    fun `orphan conduits and unread deriveds are named, not dropped`() {
        assertEquals(
            listOf("ObserveIdleUseCase: conduit no ViewModel injects"),
            orphanConduits(
                conduits = listOf(
                    DataGraphExtraction.Conduit("ObserveIdleUseCase", mapOf("x" to setOf("x"))),
                    DataGraphExtraction.Conduit("ObserveReadUseCase", mapOf("y" to setOf("y"))),
                ),
                conduitReaders = mapOf("ObserveReadUseCase" to listOf("HomeViewModel")),
            ),
        )
        val unread = DataGraph.Derived(
            id = NodeId.WATCH_APP_STATUS,
            from = emptyList(),
            shell = "ObserveWatchAppStatusUseCase",
            sharing = Sharing.Eager,
            readBy = emptyList(),
        )
        assertEquals(
            listOf("watchAppStatus: derived node no ViewModel reads"),
            unreadDeriveds(listOf(unread)),
        )
        assertEquals(emptyList<String>(), unreadDeriveds(listOf(unread.copy(readBy = listOf("ProfileViewModel")))))
    }
}
