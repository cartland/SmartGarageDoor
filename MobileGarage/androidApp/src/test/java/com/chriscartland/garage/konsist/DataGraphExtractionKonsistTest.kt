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
import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * THE data graph: derived from sources, checked, and rendered
 * (docs/DATA_GRAPH_PLAN.md §6 — end state; there is no hand-declared
 * registry anymore).
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
 *
 * The extracted graph runs through the parameterized [DataGraph]
 * checks, and the generated `docs/DATA_GRAPH.md` rendering is pinned
 * byte-exact — a graph change is visible in review as a diff of that
 * committed file, and CI fails until it is regenerated
 * (`./scripts/generate-data-graph.sh`).
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

    // ---- raw shapes (assembly input; controls build these directly) ----

    private data class RawInput(
        val ownerType: String,
        val declarationName: String,
        val cadence: Cadence,
        val nodeId: String,
    )

    private data class RawDerived(
        val className: String,
        val fromIds: Set<String>,
        /** "Eagerly", "WhileSubscribed", another literal, or null when none was found. */
        val sharingName: String?,
        /** The `StateFlow<X>` type argument of the stateIn property, for reader attribution. */
        val outputType: String? = null,
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

    private fun extractDeriveds(inputs: List<RawInput>): List<RawDerived> {
        val stateInFiles = scope.files
            .filter { it.path.contains("/usecase/src/commonMain/") }
            .filter { STATE_IN.containsMatchIn(stripComments(it.text)) }
        require(stateInFiles.isNotEmpty()) { "no stateIn holders found in :usecase — extraction has nothing to read" }

        val inputOwners = inputs.map { it.ownerType }.toSet()
        val inputDeclsByOwner = inputs
            .groupBy { it.ownerType }
            .mapValues { (_, list) -> list.map { it.declarationName to it.nodeId } }
        val derivedClassNames = stateInFiles
            .mapNotNull { className(stripComments(it.text)) }
            .filterNot { it in inputOwners }
        val derivedIdByClass = derivedClassNames.associateWith(::derivedNodeId)

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
        val vmFiles = scope.files.filter { it.path.contains("/viewmodel/src/commonMain/") }
        require(vmFiles.isNotEmpty()) { "Konsist scope found no viewmodel files — scope misconfigured" }
        return vmFiles
            .flatMap { readerPairsIn(stripComments(it.text), deriveds) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
    }

    /** (derived shell class, reader name) pairs found in one file's class constructors. */
    private fun readerPairsIn(
        text: String,
        deriveds: List<RawDerived>,
    ): List<Pair<String, String>> =
        CLASS_WITH_CTOR.findAll(text).toList().flatMap { match ->
            val cls = match.groupValues[1]
            namedTypedParams(text, cls).mapNotNull { (paramName, paramType) ->
                attributeReader(deriveds, paramName, paramType)?.let { it to cls.removePrefix("Default") }
            }
        }

    private fun realExtraction(): Pair<List<DataGraph.Node>, List<String>> {
        val inputs = extractInputs()
        val deriveds = extractDeriveds(inputs)
        return assembleGraph(inputs, deriveds, extractReaders(deriveds))
    }

    // ---- the real graph: extracted, checked, rendered, pinned ----

    @Test
    fun `the extracted graph is coherent and problem-free`() {
        val (nodes, problems) = realExtraction()
        assertEquals(emptyList<String>(), problems)
        assertEquals(emptyList<NodeId>(), DataGraph.missingNodes(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.duplicateIds(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.cycleMembers(nodes))
        assertEquals(emptyList<String>(), DataGraph.invalidGates(nodes))
        assertEquals(emptyList<String>(), DataGraph.eagerOverPolls(nodes))
        assertEquals(emptyList<String>(), DataGraph.sharedRootViolations(nodes))
    }

    @Test
    fun `the committed DATA_GRAPH rendering matches the sources`() {
        val (nodes, problems) = realExtraction()
        require(problems.isEmpty()) { "extraction problems (fix before rendering): $problems" }
        val generated = render(nodes)

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
        val expected =
            """
            <!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.
                 Regenerate: ./scripts/generate-data-graph.sh
                 The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->

            # Shared data graph

            | Node | Kind | Cadence | Declared by | Sharing | Read by |
            |---|---|---|---|---|---|
            | `currentDoorEvent` | input | PUSH | `DoorRepository` | — | — |
            | `buttonHealthDisplay` | derived | — | `ComputeButtonHealthDisplayUseCase` | eager | `HomeViewModel` |

            ```mermaid
            graph LR
                currentDoorEvent(["currentDoorEvent · PUSH"])
                buttonHealthDisplay["buttonHealthDisplay"]
                HomeViewModel{{"HomeViewModel"}}
                currentDoorEvent --> buttonHealthDisplay
                buttonHealthDisplay --> HomeViewModel
            ```
            """.trimIndent() + "\n"
        assertEquals(expected, render(nodes))
    }

    // ---- parsers (pure; exercised by the controls above) ----

    private fun annotationSites(strippedText: String): List<RawInput> {
        val typeHeaders = TYPE_HEADER
            .findAll(strippedText)
            .map { it.range.first to it.groupValues[2] }
            .toList()
        return ANNOTATION
            .findAll(strippedText)
            .mapNotNull { match ->
                val owner = typeHeaders.lastOrNull { it.first < match.range.first }?.second
                    ?: return@mapNotNull null
                val args = match.groupValues[1]
                val cadence = Regex("""Cadence\.([A-Z_]+)""")
                    .find(args)
                    ?.groupValues
                    ?.get(1)
                    ?.let { name -> Cadence.entries.firstOrNull { it.name == name } }
                    ?: return@mapNotNull null
                val declName = DECLARATION.find(strippedText, match.range.last)?.groupValues?.get(2)
                    ?: return@mapNotNull null
                val idOverride = Regex("""id\s*=\s*"([^"]+)"""").find(args)?.groupValues?.get(1)
                RawInput(owner, declName, cadence, idOverride ?: declName)
            }.toList()
    }

    private fun className(strippedText: String): String? = CLASS_NAME.find(strippedText)?.groupValues?.get(1)

    private fun sharingName(afterStateIn: String): String? = SHARING.find(afterStateIn)?.groupValues?.get(1)

    /** Constructor parameters as (name, FULL type text, whitespace-normalized, default stripped). */
    private fun namedTypedParams(
        strippedText: String,
        className: String,
    ): List<Pair<String, String>> {
        val start = strippedText.indexOf("class $className(")
        if (start < 0) return emptyList()
        val open = start + "class $className".length
        var depth = 0
        var end = open
        for (i in open until strippedText.length) {
            when (strippedText[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        return splitTopLevel(strippedText.substring(open + 1, end)).mapNotNull { raw ->
            var param = raw.trim()
            for (modifier in listOf("override", "private", "protected", "internal", "val", "var")) {
                param = param.removePrefix("$modifier ").trim()
            }
            val colon = param.indexOf(':')
            if (colon < 0) return@mapNotNull null
            val name = param.substring(0, colon).trim()
            if (!name.matches(Regex("""[A-Za-z0-9_]+"""))) return@mapNotNull null
            val type = param
                .substring(colon + 1)
                .substringBefore('=')
                .trim()
                .replace(Regex("""\s+"""), " ")
            name to type
        }
    }

    /** Constructor parameters as (name, base type without generics) — the edge-matching shape. */
    private fun constructorParams(
        strippedText: String,
        className: String,
    ): List<Pair<String, String>> =
        namedTypedParams(strippedText, className).map { (name, type) ->
            name to type.substringBefore('<').trim()
        }

    private fun splitTopLevel(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in s) {
            when (c) {
                '(', '<', '[' -> {
                    depth++
                    current.append(c)
                }
                ')', '>', ']' -> {
                    depth--
                    current.append(c)
                }
                ',' ->
                    if (depth == 0) {
                        parts.add(current.toString())
                        current.clear()
                    } else {
                        current.append(c)
                    }
                else -> current.append(c)
            }
        }
        if (current.isNotBlank()) parts.add(current.toString())
        return parts
    }

    private fun edgeIds(
        flowExpr: String,
        params: List<Pair<String, String>>,
        inputDeclsByOwner: Map<String, List<Pair<String, String>>>,
        derivedIdByClass: Map<String, String>,
    ): Set<String> =
        params
            .flatMap { (paramName, paramType) ->
                val viaInputs = inputDeclsByOwner[paramType].orEmpty().mapNotNull { (decl, nodeId) ->
                    nodeId.takeIf { referenceRegex(paramName, decl).containsMatchIn(flowExpr) }
                }
                val viaDerived = derivedIdByClass[paramType]?.takeIf {
                    memberOrInvokeRegex(paramName).containsMatchIn(flowExpr)
                }
                viaInputs + listOfNotNull(viaDerived)
            }.toSet()

    private fun referenceRegex(
        param: String,
        decl: String,
    ) = Regex("""(^|[^A-Za-z0-9_])${Regex.escape(param)}\s*\.\s*${Regex.escape(decl)}($|[^A-Za-z0-9_])""")

    private fun memberOrInvokeRegex(param: String) = Regex("""(^|[^A-Za-z0-9_])${Regex.escape(param)}\s*[.(]""")

    private fun derivedNodeId(className: String): String =
        className
            .removePrefix("Compute")
            .removePrefix("Observe")
            .removeSuffix("UseCase")
            .replaceFirstChar { it.lowercaseChar() }

    /** The derived shell class this constructor parameter reads, or null (see the control for the rules). */
    private fun attributeReader(
        deriveds: List<RawDerived>,
        paramName: String,
        paramType: String,
    ): String? {
        deriveds.firstOrNull { it.className == paramType }?.let { return it.className }
        val outputArg = STATE_FLOW_PARAM.find(paramType)?.groupValues?.get(1) ?: return null
        return deriveds
            .filter { it.outputType == outputArg }
            .singleOrNull()
            ?.takeIf { derivedNodeId(it.className) == paramName }
            ?.className
    }

    // ---- assembly (pure; controls call it with doctored raw lists) ----

    private fun assembleGraph(
        inputs: List<RawInput>,
        deriveds: List<RawDerived>,
        readersByShell: Map<String, List<String>> = emptyMap(),
    ): Pair<List<DataGraph.Node>, List<String>> {
        val problems = mutableListOf<String>()

        fun toNodeId(
            id: String,
            context: String,
        ): NodeId? =
            NodeId.entries.firstOrNull { it.id == id } ?: run {
                problems.add("$context: no NodeId has id `$id`")
                null
            }

        val inputNodes = inputs.mapNotNull { raw ->
            if (raw.cadence == Cadence.DERIVED) {
                problems.add(
                    "${raw.nodeId}: DERIVED is not a declarable cadence — derived-ness is extracted from stateIn",
                )
                return@mapNotNull null
            }
            toNodeId(raw.nodeId, "input ${raw.ownerType}.${raw.declarationName}")
                ?.let { DataGraph.Input(it, owner = raw.ownerType, cadence = raw.cadence) }
        }

        val eagerDrafts = deriveds.mapNotNull { raw ->
            val id = toNodeId(derivedNodeId(raw.className), "derived ${raw.className}") ?: return@mapNotNull null
            DataGraph.Derived(
                id = id,
                from = raw.fromIds.mapNotNull { toNodeId(it, "edge of ${raw.className}") }.sortedBy { it.name },
                shell = raw.className,
                sharing = Sharing.Eager,
                readBy = readersByShell[raw.className].orEmpty(),
            )
        }
        val draftGraph = inputNodes + eagerDrafts

        val derivedNodes = eagerDrafts.map { draft ->
            val raw = deriveds.first { derivedNodeId(it.className) == draft.id.id }
            when (raw.sharingName) {
                "Eagerly" -> draft
                "WhileSubscribed" -> {
                    val polls = DataGraph
                        .sourcesOf(draft, draftGraph)
                        .filter { it.cadence == Cadence.POLL }
                        .sortedBy { it.id.name }
                    when (polls.size) {
                        1 -> draft.copy(sharing = Sharing.Gated(poll = polls.single().id))
                        0 -> {
                            problems.add("${draft.id.id}: WhileSubscribed with no POLL-cadence source upstream")
                            draft
                        }
                        else -> {
                            problems.add("${draft.id.id}: WhileSubscribed over ${polls.size} polls — ambiguous gate")
                            draft.copy(sharing = Sharing.Gated(poll = polls.first().id))
                        }
                    }
                }
                null -> {
                    problems.add("${draft.id.id}: no SharingStarted literal found after stateIn")
                    draft
                }
                else -> {
                    problems.add("${draft.id.id}: unrecognized SharingStarted.${raw.sharingName}")
                    draft
                }
            }
        }
        return (inputNodes + derivedNodes) to problems
    }

    // ---- rendering (pure; pinned to docs/DATA_GRAPH.md) ----

    private fun render(nodes: List<DataGraph.Node>): String {
        val ordered = nodes.sortedBy { it.id.ordinal }
        val inputs = ordered.filterIsInstance<DataGraph.Input>()
        val deriveds = ordered.filterIsInstance<DataGraph.Derived>()
        val readers = deriveds.flatMap { it.readBy }.distinct().sorted()
        return buildString {
            appendLine("<!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.")
            appendLine("     Regenerate: ./scripts/generate-data-graph.sh")
            appendLine("     The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->")
            appendLine()
            appendLine("# Shared data graph")
            appendLine()
            appendLine("| Node | Kind | Cadence | Declared by | Sharing | Read by |")
            appendLine("|---|---|---|---|---|---|")
            inputs.forEach { appendLine("| `${it.id.id}` | input | ${it.cadence} | `${it.owner}` | — | — |") }
            deriveds.forEach { d ->
                val sharing = when (val s = d.sharing) {
                    is Sharing.Eager -> "eager"
                    is Sharing.Gated -> "gated on `${s.poll.id}`"
                }
                val readBy = if (d.readBy.isEmpty()) "—" else d.readBy.joinToString(", ") { "`$it`" }
                appendLine("| `${d.id.id}` | derived | — | `${d.shell}` | $sharing | $readBy |")
            }
            appendLine()
            appendLine("```mermaid")
            appendLine("graph LR")
            inputs.forEach { appendLine("    ${it.id.id}([\"${it.id.id} · ${it.cadence}\"])") }
            deriveds.forEach { appendLine("    ${it.id.id}[\"${it.id.id}\"]") }
            readers.forEach { appendLine("    $it{{\"$it\"}}") }
            deriveds.forEach { d ->
                val poll = (d.sharing as? Sharing.Gated)?.poll
                d.from.forEach { from ->
                    if (from == poll) {
                        appendLine("    ${from.id} -. poll, gated .-> ${d.id.id}")
                    } else {
                        appendLine("    ${from.id} --> ${d.id.id}")
                    }
                }
                d.readBy.forEach { reader -> appendLine("    ${d.id.id} --> $reader") }
            }
            appendLine("```")
        }
    }

    /** Walk up from the test working directory to the Gradle root (the dir holding docs/DATA_GRAPH_PLAN.md). */
    private fun mobileGarageRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "docs/DATA_GRAPH_PLAN.md").exists()) return dir
            dir = dir.parentFile
        }
        error("could not locate the MobileGarage root from ${System.getProperty("user.dir")}")
    }

    /** Remove block + line comments so prose cannot create (or hide) a node or an edge. */
    private fun stripComments(text: String): String =
        text
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { line ->
                // `(?<!:)//` so a URL's :// is not a comment start.
                val match = Regex("""(?<!:)//""").find(line)
                if (match != null) line.substring(0, match.range.first) else line
            }

    private companion object {
        val STATE_IN = Regex("""\.\s*stateIn\s*\(""")
        val TYPE_HEADER = Regex("""\b(interface|class|object)\s+([A-Za-z0-9_]+)""")
        val ANNOTATION = Regex("""@NodeCadence\s*\(([^)]*)\)""")
        val DECLARATION = Regex("""\b(val|fun)\s+([A-Za-z0-9_]+)""")
        val CLASS_NAME = Regex("""\bclass\s+([A-Za-z0-9_]+)""")
        val CLASS_WITH_CTOR = Regex("""\bclass\s+([A-Za-z0-9_]+)\s*\(""")
        val SHARING = Regex("""SharingStarted\s*\.\s*([A-Za-z]+)""")
        val STATE_FLOW_PROP = Regex("""val\s+[A-Za-z0-9_]+\s*:\s*StateFlow<([^<>]+)>""")
        val STATE_FLOW_PARAM = Regex("""^StateFlow<\s*([A-Za-z0-9_.]+)\s*>$""")
    }
}
