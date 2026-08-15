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
import com.chriscartland.garage.domain.graph.DataGraph.NodeId
import com.chriscartland.garage.konsist.DataGraphExtraction.ANNOTATION
import com.chriscartland.garage.konsist.DataGraphExtraction.InputConsumption
import com.chriscartland.garage.konsist.DataGraphExtraction.RawDerived
import com.chriscartland.garage.konsist.DataGraphExtraction.RawInput
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_FLOW_PROP
import com.chriscartland.garage.konsist.DataGraphExtraction.STATE_IN
import com.chriscartland.garage.konsist.DataGraphExtraction.annotationSites
import com.chriscartland.garage.konsist.DataGraphExtraction.assembleGraph
import com.chriscartland.garage.konsist.DataGraphExtraction.className
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitEntry
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitMethodReadsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.conduitPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.constructorParams
import com.chriscartland.garage.konsist.DataGraphExtraction.declsByOwner
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
import com.chriscartland.garage.konsist.DataGraphExtraction.readerPairsIn
import com.chriscartland.garage.konsist.DataGraphExtraction.render
import com.chriscartland.garage.konsist.DataGraphExtraction.sharingName
import com.chriscartland.garage.konsist.DataGraphExtraction.stripComments
import com.chriscartland.garage.konsist.DataGraphExtraction.unreadDeriveds
import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * THE data graph: derived from sources, checked, and rendered
 * (docs/DATA_GRAPH_PLAN.md §6 — end state; there is no hand-declared
 * registry anymore). The pure parsers/assembly/rendering live in
 * [DataGraphExtraction], their positive controls in
 * [DataGraphExtractionControlsTest]; this class owns the Konsist-scope
 * orchestration, the checks, and the rendering pin.
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
 *  - INPUT CONSUMPTION, method-precise (C4/C6): the ADR-022
 *    pass-through CONDUITS (`Observe*` UseCase classes — not stateIn
 *    holders — whose body references an input declaration), resolved
 *    PER METHOD so `current()` and `position()` are two routes to one
 *    root; the ViewModels injecting them; and DIRECT reads (a VM
 *    constructor parameter typed as an input owner whose declaration
 *    the VM references). Every consumption becomes a
 *    `DataGraph.ScreenRead` — the G7 read-path check's input. Action
 *    UseCases reading `.value` at act time are deliberately NOT
 *    conduits — the same reasoning as stateIn seeds: an act-time read
 *    is not reactive observation. An input with no reader at all
 *    (e.g. the server config, consumed by the data layer as fetch
 *    plumbing) renders with a footnote, not an invented edge.
 *  - MANAGER EDGES (C3): an ADR-015 manager file's impl classes are
 *    parsed the same way, and what they collect becomes the input's
 *    `Input.from` — a CLOCK manager cannot launder a PUSH upstream
 *    past G7's per-root clock exemption.
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
 * a positive control in [DataGraphExtractionControlsTest] (the
 * vacuous-pass rule), and the rendering pin is the global control — a
 * parser that returns nothing cannot reproduce the reviewed committed
 * rendering.
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

    private fun extractConduits(inputs: List<RawInput>): List<DataGraphExtraction.Conduit> {
        val inputOwners = inputs.map { it.ownerType }.toSet()
        val decls = declsByOwner(inputs)
        return scope.files
            .filter { it.path.contains("/usecase/src/commonMain/") }
            .mapNotNull { conduitEntry(stripComments(it.text), inputOwners, decls) }
            .sortedBy { it.className }
    }

    /**
     * C3: enrich manager-declared inputs with their extracted reactive
     * upstream. A `:usecase` file that declares more than one input AND
     * collects upstream cannot be attributed file-level — that is a
     * problem to report, not to guess through.
     */
    private fun enrichManagerFrom(
        inputs: List<RawInput>,
        conduits: List<DataGraphExtraction.Conduit>,
    ): Pair<List<RawInput>, List<String>> {
        val decls = declsByOwner(inputs)
        val problems = mutableListOf<String>()
        val fromByOwner = mutableMapOf<String, Set<String>>()
        scope.files
            .filter { it.path.contains("/usecase/src/commonMain/") }
            .filter { it.text.contains("@NodeCadence") }
            .forEach { file ->
                val text = stripComments(file.text)
                val sites = annotationSites(text)
                if (sites.isEmpty()) return@forEach
                val own = sites.map { it.nodeId }.toSet()
                val from = managerFromIds(text, conduits, decls, own)
                if (from.isEmpty()) return@forEach
                if (sites.size > 1) {
                    problems.add(
                        "${file.path}: declares ${sites.size} inputs and collects upstream — " +
                            "manager edges cannot be attributed file-level; split the file",
                    )
                    return@forEach
                }
                fromByOwner[sites.single().ownerType] = from
            }
        return inputs.map { raw ->
            fromByOwner[raw.ownerType]?.let { raw.copy(fromIds = it) } ?: raw
        } to problems
    }

    private fun extractInputConsumption(
        inputs: List<RawInput>,
        conduits: List<DataGraphExtraction.Conduit>,
    ): InputConsumption {
        val decls = declsByOwner(inputs)
        val vmFiles = vmFiles()
        val conduitNames = conduits.map { it.className }.toSet()
        val conduitReaders = vmFiles
            .flatMap { conduitPairsIn(stripComments(it.text), conduitNames) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
        val reads = vmFiles
            .flatMap { file ->
                val text = stripComments(file.text)
                conduitMethodReadsIn(text, conduits).map { (screen, route, id) ->
                    Triple(screen, route, id)
                } +
                    directInputPairsIn(text, decls).map { (id, screen) ->
                        // Route = the node's own id: two direct reads of
                        // different nodes over one root stay two flows.
                        Triple(screen, id, id)
                    }
            }.mapNotNull { (screen, route, id) ->
                NodeId.entries
                    .firstOrNull { it.id == id }
                    ?.let { DataGraph.ScreenRead(screen, route, it) }
            }.distinct()
        return InputConsumption(
            conduits = conduits,
            conduitReaders = conduitReaders,
            reads = reads,
        )
    }

    private fun vmFiles() =
        scope.files
            .filter { it.path.contains("/viewmodel/src/commonMain/") }
            .also { require(it.isNotEmpty()) { "Konsist scope found no viewmodel files — scope misconfigured" } }

    private fun realExtraction(): Extraction {
        val rawInputs = extractInputs()
        val conduits = extractConduits(rawInputs)
        val (inputs, managerProblems) = enrichManagerFrom(rawInputs, conduits)
        val deriveds = extractDeriveds(inputs)
        val (nodes, problems) = assembleGraph(inputs, deriveds, extractReaders(deriveds))
        return Extraction(nodes, managerProblems + problems, extractInputConsumption(inputs, conduits))
    }

    /** Every read for G7: the consumption's input reads plus the derived-node reads from `readBy`. */
    private fun allReads(
        nodes: List<DataGraph.Node>,
        consumption: InputConsumption,
    ): List<DataGraph.ScreenRead> =
        consumption.reads +
            nodes.filterIsInstance<DataGraph.Derived>().flatMap { d ->
                d.readBy.map { screen -> DataGraph.ScreenRead(screen, d.id.id, d.id) }
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
        // C5: nothing extracted may dangle unread — a derived node or a
        // conduit nobody injects is dead code or an extraction miss.
        assertEquals(emptyList<String>(), unreadDeriveds(nodes))
        assertEquals(
            emptyList<String>(),
            orphanConduits(consumption.conduits, consumption.conduitReaders),
        )
    }

    @Test
    fun `every shared-root finding is fixed or adjudicated`() {
        // C4 = G7 over every read: derived nodes, conduit methods, and
        // direct injections alike. A screen observing one root through
        // two independent flows can render two projections of the same
        // instant one frame apart; each such finding is either collapsed
        // into one derivation or exempted WITH ITS REASONING in
        // data-graph-shared-root-exemptions.txt (stale entries fail).
        val (nodes, problems, consumption) = realExtraction()
        require(problems.isEmpty()) { "extraction problems: $problems" }
        val reads = allReads(nodes, consumption)
        require(reads.isNotEmpty()) { "no screen reads extracted — reader parsers misconfigured" }
        val findings = DataGraph.sharedRootFindings(nodes, reads)
        val exemptions = parseSharedRootExemptions(sharedRootExemptionsFile().readText())
        assertEquals(
            "unadjudicated shared-root finding — collapse the reads into one derivation " +
                "or add a reasoned entry in data-graph-shared-root-exemptions.txt",
            emptyList<String>(),
            findings.filterNot { it.key in exemptions.keys }.map { it.toString() },
        )
        assertEquals(
            "stale shared-root exemption — the finding no longer exists; remove the entry",
            emptyList<String>(),
            (exemptions.keys - findings.map { it.key }.toSet()).sorted(),
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

    private fun nodeExemptionsFile(): File = File(mobileGarageRoot(), "data-graph-node-exemptions.txt")

    private fun sharedRootExemptionsFile(): File = File(mobileGarageRoot(), "data-graph-shared-root-exemptions.txt")

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
