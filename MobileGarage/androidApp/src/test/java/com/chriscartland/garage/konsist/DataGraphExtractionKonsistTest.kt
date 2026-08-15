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
import com.chriscartland.garage.konsist.DataGraphExtraction.ANNOTATION
import com.chriscartland.garage.konsist.DataGraphExtraction.InputConsumption
import com.chriscartland.garage.konsist.DataGraphExtraction.RawDerived
import com.chriscartland.garage.konsist.DataGraphExtraction.RawInput
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_FLOW_PROP
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_IN
import com.chriscartland.garage.konsist.DataGraphExtraction.annotationSites
import com.chriscartland.garage.konsist.DataGraphExtraction.assembleGraph
import com.chriscartland.garage.konsist.DataGraphExtraction.attributeReader
import com.chriscartland.garage.konsist.DataGraphExtraction.className
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitEntry
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.constructorParams
import com.chriscartland.garage.konsist.DataGraphExtraction.declsByOwner
import com.chriscartland.garage.konsist.DataGraphExtraction.derivedNodeId
import com.chriscartland.garage.konsist.DataGraphExtraction.directInputPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.edgeIds
import com.chriscartland.garage.konsist.DataGraphExtraction.illegalStateIns
import com.chriscartland.garage.konsist.DataGraphExtraction.interfaceFlowMembers
import com.chriscartland.garage.konsist.DataGraphExtraction.nodeSweepProblems
import com.chriscartland.garage.konsist.DataGraphExtraction.orphanConduits
import com.chriscartland.garage.konsist.DataGraphExtraction.parseNodeExemptions
import com.chriscartland.garage.konsist.DataGraphExtraction.readerPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.render
import com.chriscartland.garage.konsist.DataGraphExtraction.sharingName
import com.chriscartland.garage.konsist.DataGraphExtraction.stripComments
import com.chriscartland.garage.konsist.DataGraphExtraction.unreadDeriveds
import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * THE data graph: derived from sources, checked, and rendered
 * (docs/DATA_GRAPH_PLAN.md §6 — end state; there is no hand-declared
 * registry anymore). The pure parsers/assembly/rendering live in
 * [DataGraphExtraction]; this class owns the Konsist-scope
 * orchestration, the checks, the rendering pin, and the positive
 * controls.
 *
 * What is extracted, and from what:
 *  - INPUT nodes from `@NodeCadence` annotations on repository/manager
 *    flow declarations. Cadence (plus an id override where the
 *    declaration name is not the node name) is the only hand-written
 *    metadata; the owner is the nearest enclosing type declaration.
 *  - DERIVED nodes from `stateIn` holders in `:usecase` commonMain:
 *    the node id from the class name (`Compute`/`Observe` … `UseCase`
 *    stripped, decapitalized), the edges from `param.declaration`
 *    references in the flow expression BEFORE `.stateIn(` (so a seed
 *    reading `.value` inside `stateIn(...)` is construction-time
 *    plumbing, not a reactive edge), and the sharing from the
 *    `SharingStarted` literal. A `WhileSubscribed` gate's poll is
 *    resolved as the unique POLL-cadence source in the node's
 *    transitive closure.
 *  - READERS (the G7 key) from `:viewmodel` constructors: a parameter
 *    typed as a derived shell class, or typed `StateFlow<output>` and
 *    NAMED after the node (the injected-value shape,
 *    `buttonHealthDisplay`). Constructor references are consumption of
 *    the terminal surface — VM STATE stays out of the graph (G0);
 *    reader names drop the `Default` prefix (the screen identity).
 *  - INPUT CONSUMPTION, so no observed input renders as isolated: the
 *    ADR-022 pass-through CONDUITS (`Observe*` UseCase classes — not
 *    stateIn holders — whose body references an input declaration)
 *    plus the ViewModels injecting them, and DIRECT reads (a VM
 *    constructor parameter typed as an input owner whose declaration
 *    the VM references). Action UseCases reading `.value` at act time
 *    are deliberately NOT conduits — the same reasoning as stateIn
 *    seeds: an act-time read is not reactive observation. An input
 *    with no reader at all (e.g. the server config, consumed by the
 *    data layer as fetch plumbing) renders with a footnote, not an
 *    invented edge.
 *
 * The extracted graph runs through the parameterized [DataGraph]
 * checks, and the generated `docs/DATA_GRAPH.md` rendering is pinned
 * byte-exact — a graph change is visible in review as a diff of that
 * committed file, and CI fails until it is regenerated
 * (`./scripts/generate-data-graph.sh`).
 *
 * DISCOVERY IS PAIRED WITH EXHAUSTIVENESS — the fail-closed layer. An
 * extractor alone is complete only for what matches it; the byte-pin
 * catches drift but not absence. So every discovery rule here has a
 * sweep over an enumerable universe:
 *  - C1: every parameterless flow-typed member of a :domain/:usecase
 *    INTERFACE is a node or a reasoned entry in
 *    `data-graph-node-exemptions.txt` (stale entries fail).
 *  - C2: `stateIn` may live only in :usecase commonMain (where derived
 *    extraction reads it) — :viewmodel only on `viewModelScope` (G0
 *    sink), repositories never (ADR-022 always-on collectors).
 *  - C5: a derived node nobody reads or a conduit nobody injects FAILS
 *    instead of silently vanishing from the rendering.
 *
 * Comment-stripped text parsing throughout; every parser and rule has
 * a positive control below (the vacuous-pass rule), and the rendering
 * pin is the global control — a parser that returns nothing cannot
 * reproduce the reviewed committed rendering.
 *
 * Local-probe caveat: Konsist reads sources but Gradle tracks the
 * compiled classpath — and the committed rendering is read at RUNTIME,
 * so a docs-only edit never re-triggers the task by itself. Probe (and
 * regenerate) with `--rerun-tasks` (CLAUDE.md § Konsist); CI always
 * runs fresh.
 */
class DataGraphExtractionKonsistTest {
    private val scope = Konsist.scopeFromProduction()

    private data class Extraction(
        val nodes: List<DataGraph.Node>,
        val problems: List<String>,
        val consumption: InputConsumption,
    )

    // ---- extraction from the real sources ----

    /** The only legal homes for an input declaration (G0): `:domain` interfaces and `:usecase` managers. */
    private fun isInputHome(path: String): Boolean = path.contains("/domain/src/commonMain/") || path.contains("/usecase/src/commonMain/")

    private fun extractInputs(): List<RawInput> {
        val files = scope.files
            .filter { isInputHome(it.path) }
            .filter { it.text.contains("@NodeCadence") }
        require(files.isNotEmpty()) { "no @NodeCadence annotations found — extraction has nothing to read" }
        val inputs = files.flatMap { annotationSites(stripComments(it.text)) }
        require(inputs.isNotEmpty()) { "@NodeCadence present but no site parsed — parser misconfigured" }
        return inputs
    }

    private fun extractDeriveds(inputs: List<RawInput>): List<RawDerived> {
        val stateInFiles = scope.files
            .filter { it.path.contains("/usecase/src/commonMain/") }
            .filter { STATE_IN.containsMatchIn(stripComments(it.text)) }
        require(stateInFiles.isNotEmpty()) { "no stateIn holders found in :usecase — extraction has nothing to read" }

        val inputOwners = inputs.map { it.ownerType }.toSet()
        val inputDeclsByOwner = declsByOwner(inputs)
        val derivedIdByClass = stateInFiles
            .mapNotNull { className(stripComments(it.text)) }
            .filterNot { it in inputOwners }
            .associateWith { derivedNodeId(it) }

        return stateInFiles.mapNotNull { file ->
            val text = stripComments(file.text)
            val name = className(text) ?: return@mapNotNull null
            // A class with an annotated member is an input OWNER (the
            // ADR-015 manager shape), never a derived node.
            if (name in inputOwners) return@mapNotNull null
            val split = STATE_IN.find(text)!!.range.first
            val flowExpr = text.substring(0, split)
            RawDerived(
                className = name,
                fromIds = edgeIds(flowExpr, constructorParams(text, name), inputDeclsByOwner, derivedIdByClass),
                sharingName = sharingName(text.substring(split)),
                outputType = STATE_FLOW_PROP
                    .findAll(flowExpr)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
                    ?.trim(),
            )
        }
    }

    private fun extractReaders(deriveds: List<RawDerived>): Map<String, List<String>> {
        val vmFiles = vmFiles()
        return vmFiles
            .flatMap { readerPairsIn(stripComments(it.text), deriveds) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
    }

    private fun extractConduits(inputs: List<RawInput>): Map<String, Set<String>> {
        val inputOwners = inputs.map { it.ownerType }.toSet()
        val decls = declsByOwner(inputs)
        return scope.files
            .filter { it.path.contains("/usecase/src/commonMain/") }
            .mapNotNull { conduitEntry(stripComments(it.text), inputOwners, decls) }
            .toMap()
    }

    private fun extractInputConsumption(
        inputs: List<RawInput>,
        conduits: Map<String, Set<String>>,
    ): InputConsumption {
        val decls = declsByOwner(inputs)
        val vmFiles = vmFiles()
        val viaPairs = vmFiles.flatMap { conduitPairsIn(stripComments(it.text), conduits.keys) }
        val directPairs = vmFiles.flatMap { directInputPairsIn(stripComments(it.text), decls) }
        val conduitReaders = viaPairs
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
        return InputConsumption(
            conduitInputs = conduits,
            conduitReaders = conduitReaders,
            directReaders = directPairs
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, names) -> names.distinct().sorted() },
        )
    }

    private fun vmFiles() =
        scope.files
            .filter { it.path.contains("/viewmodel/src/commonMain/") }
            .also { require(it.isNotEmpty()) { "Konsist scope found no viewmodel files — scope misconfigured" } }

    private fun realExtraction(): Extraction {
        val inputs = extractInputs()
        val deriveds = extractDeriveds(inputs)
        val (nodes, problems) = assembleGraph(inputs, deriveds, extractReaders(deriveds))
        return Extraction(nodes, problems, extractInputConsumption(inputs, extractConduits(inputs)))
    }

    // ---- the real graph: extracted, checked, rendered, pinned ----

    @Test
    fun `NodeCadence appears only at the G0 boundary`() {
        // An input node can live only where the graph lives: a :domain
        // interface or a :usecase manager. Anywhere else — a :viewmodel
        // property especially — would smuggle an input past G0, so a
        // misplaced annotation is a violation, not a silently ignored
        // declaration.
        val misplaced = scope.files
            .filterNot { isInputHome(it.path) }
            .filter { ANNOTATION.containsMatchIn(stripComments(it.text)) }
            .map { it.path }
        assertEquals(emptyList<String>(), misplaced)
        // Scope sanity: the complement set is genuinely scanned.
        require(scope.files.any { !isInputHome(it.path) }) { "no files outside the input homes — scope misconfigured" }
    }

    @Test
    fun `the extracted graph is coherent and problem-free`() {
        val (nodes, problems, consumption) = realExtraction()
        assertEquals(emptyList<String>(), problems)
        assertEquals(emptyList<NodeId>(), DataGraph.missingNodes(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.duplicateIds(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.cycleMembers(nodes))
        assertEquals(emptyList<String>(), DataGraph.invalidGates(nodes))
        assertEquals(emptyList<String>(), DataGraph.eagerOverPolls(nodes))
        assertEquals(emptyList<String>(), DataGraph.sharedRootViolations(nodes))
        // C5: nothing extracted may dangle unread — a derived node or a
        // conduit nobody injects is dead code or an extraction miss.
        assertEquals(emptyList<String>(), unreadDeriveds(nodes))
        assertEquals(
            emptyList<String>(),
            orphanConduits(consumption.conduitInputs, consumption.conduitReaders),
        )
    }

    // ---- fail-closed sweeps: discovery paired with exhaustiveness ----

    @Test
    fun `every flow declared at the graph boundary is a node or a reasoned exemption`() {
        // C1. The extractor only sees what carries @NodeCadence; this
        // sweep enumerates what COULD carry it — every parameterless
        // flow-typed member of a :domain or :usecase interface — and
        // demands each be a node or an exemption with a reason. Without
        // it the graph is "what we remembered to annotate", and an
        // unannotated repository StateFlow is not a violation but a
        // silent hole (paginationState was exactly that).
        val files = scope.files.filter { isInputHome(it.path) }
        val members = files.flatMap { interfaceFlowMembers(stripComments(it.text)) }
        require(members.isNotEmpty()) { "no flow-typed interface members found — sweep parser misconfigured" }
        val annotated = files
            .flatMap { annotationSites(stripComments(it.text)) }
            .map { it.ownerType to it.declarationName }
            .toSet()
        val exemptions = parseNodeExemptions(nodeExemptionsFile().readText())
        val (missing, stale) = nodeSweepProblems(members, annotated, exemptions.keys)
        assertEquals(
            "unannotated flow at the graph boundary — add @NodeCadence or a reasoned entry " +
                "in data-graph-node-exemptions.txt",
            emptyList<String>(),
            missing,
        )
        assertEquals(
            "stale node exemption — the declaration is gone or is now a node; remove the entry",
            emptyList<String>(),
            stale,
        )
    }

    @Test
    fun `stateIn stays inside the usecase layer`() {
        // C2. Derived-node extraction reads stateIn holders in :usecase
        // commonMain — so an app-scoped stateIn anywhere else is a shared
        // derivation the graph cannot see. :viewmodel may stateIn only on
        // viewModelScope (G0: VM state is a sink); repositories use
        // ADR-022 always-on collectors, never stateIn.
        val sweptModules = listOf(
            "/data/src/commonMain/",
            "/data-local/src/commonMain/",
            "/domain/src/commonMain/",
            "/presentation-model/src/commonMain/",
            "/viewmodel/src/commonMain/",
        )
        val swept = scope.files.filter { file -> sweptModules.any(file.path::contains) }
        require(swept.isNotEmpty()) { "stateIn sweep found no shared-module files — scope misconfigured" }
        val violations = swept.flatMap { illegalStateIns(it.path, stripComments(it.text)) }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `the committed DATA_GRAPH rendering matches the sources`() {
        val (nodes, problems, consumption) = realExtraction()
        require(problems.isEmpty()) { "extraction problems (fix before rendering): $problems" }
        val generated = render(nodes, consumption)

        val root = mobileGarageRoot()
        val artifact = File(root, "androidApp/build/reports/data-graph/DATA_GRAPH.md")
        artifact.parentFile.mkdirs()
        artifact.writeText(generated)

        val committed = File(root, "docs/DATA_GRAPH.md")
        assertTrue(
            "docs/DATA_GRAPH.md is missing — run ./scripts/generate-data-graph.sh",
            committed.exists(),
        )
        assertEquals(
            "docs/DATA_GRAPH.md is out of date — run ./scripts/generate-data-graph.sh",
            generated,
            committed.readText(),
        )
    }

    // ---- positive controls: every parser and rule can actually fire ----

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
    fun `assembly resolves a gate to its upstream poll and attaches readers`() {
        val (nodes, problems) = assembleGraph(
            listOf(RawInput("WearCompanionRepository", "observeWatchAppStatus", Cadence.POLL, "watchCompanion")),
            listOf(RawDerived("ObserveWatchAppStatusUseCase", setOf("watchCompanion"), "WhileSubscribed")),
            readersByShell = mapOf("ObserveWatchAppStatusUseCase" to listOf("ProfileViewModel")),
        )
        assertEquals(emptyList<String>(), problems)
        val derived = nodes.filterIsInstance<DataGraph.Derived>().single()
        assertEquals(Sharing.Gated(poll = NodeId.WATCH_COMPANION), derived.sharing)
        assertEquals(listOf("ProfileViewModel"), derived.readBy)
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
            DataGraph.Derived(
                id = NodeId.BUTTON_HEALTH_DISPLAY,
                from = listOf(NodeId.CURRENT_DOOR_EVENT),
                shell = "ComputeButtonHealthDisplayUseCase",
                sharing = Sharing.Eager,
                readBy = listOf("HomeViewModel"),
            ),
        )
        val consumption = InputConsumption(
            conduitInputs = mapOf("ObserveDoorEventsUseCase" to setOf("currentDoorEvent")),
            conduitReaders = mapOf("ObserveDoorEventsUseCase" to listOf("HistoryViewModel")),
            directReaders = mapOf("currentDoorEvent" to listOf("HomeViewModel")),
        )
        val expected =
            """
            <!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.
                 Regenerate: ./scripts/generate-data-graph.sh
                 The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->

            # Shared data graph

            | Node | Kind | Cadence | Declared by | Sharing | Read by |
            |---|---|---|---|---|---|
            | `currentDoorEvent` | input | PUSH | `DoorRepository` | — | via `ObserveDoorEventsUseCase`, `HomeViewModel` |
            | `buttonHealthDisplay` | derived | — | `ComputeButtonHealthDisplayUseCase` | eager | `HomeViewModel` |

            Screens observe inputs through the listed `Observe*` pass-throughs (ADR-022)
            or direct manager injection. An input with no outgoing edge in the diagram is
            consumed at action time inside the data layer (fetch plumbing), not observed.

            ```mermaid
            graph LR
                currentDoorEvent(["currentDoorEvent · PUSH"])
                buttonHealthDisplay["buttonHealthDisplay"]
                ObserveDoorEventsUseCase[["ObserveDoorEventsUseCase"]]
                HistoryViewModel{{"HistoryViewModel"}}
                HomeViewModel{{"HomeViewModel"}}
                currentDoorEvent --> ObserveDoorEventsUseCase
                ObserveDoorEventsUseCase --> HistoryViewModel
                currentDoorEvent --> HomeViewModel
                currentDoorEvent --> buttonHealthDisplay
                buttonHealthDisplay --> HomeViewModel
            ```
            """.trimIndent() + "\n"
        assertEquals(expected, render(nodes, consumption))
    }

    @Test
    fun `conduits are Observe pass-throughs that reference an input — nothing else qualifies`() {
        val decls = mapOf("DoorRepository" to listOf("currentDoorEvent" to "currentDoorEvent"))
        val passThrough =
            """
            class ObserveDoorEventsUseCase(
                private val doorRepository: DoorRepository,
            ) {
                fun current(): StateFlow<DoorEvent?> = doorRepository.currentDoorEvent
            }
            """.trimIndent()
        assertEquals(
            "ObserveDoorEventsUseCase" to setOf("currentDoorEvent"),
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
            "doorRepository.currentDoorEvent",
            "doorRepository.currentDoorEvent.stateIn(s)",
        )
        assertNull(conduitEntry(derived, emptySet(), decls))
    }

    @Test
    fun `input consumption pairs fire for conduit injection and direct owner reads`() {
        val decls = mapOf("CheckInStalenessManager" to listOf("isCheckInStale" to "isCheckInStale"))
        val vmText =
            """
            class DefaultHomeViewModel(
                observeDoorEvents: ObserveDoorEventsUseCase,
                private val checkInStalenessManager: CheckInStalenessManager,
                private val other: SomethingElse,
            ) {
                val stale = checkInStalenessManager.isCheckInStale
            }
            """.trimIndent()
        assertEquals(
            listOf("ObserveDoorEventsUseCase" to "HomeViewModel"),
            conduitPairsIn(vmText, setOf("ObserveDoorEventsUseCase")),
        )
        assertEquals(
            listOf("isCheckInStale" to "HomeViewModel"),
            directInputPairsIn(vmText, decls),
        )
        // An owner injected but never referenced yields no direct edge.
        val unreferenced = vmText.replace("checkInStalenessManager.isCheckInStale", "0")
        assertEquals(emptyList<Pair<String, String>>(), directInputPairsIn(unreferenced, decls))
    }

    // ---- positive controls for the fail-closed sweeps ----

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
                conduitInputs = mapOf("ObserveIdleUseCase" to setOf("x"), "ObserveReadUseCase" to setOf("y")),
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

    private fun nodeExemptionsFile(): File = File(mobileGarageRoot(), "data-graph-node-exemptions.txt")

    /** Walk up from the test working directory to the Gradle root (the dir holding docs/DATA_GRAPH_PLAN.md). */
    private fun mobileGarageRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "docs/DATA_GRAPH_PLAN.md").exists()) return dir
            dir = dir.parentFile
        }
        error("could not locate the MobileGarage root from ${System.getProperty("user.dir")}")
    }
}
