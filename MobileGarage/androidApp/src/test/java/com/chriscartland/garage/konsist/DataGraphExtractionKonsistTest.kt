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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Derives the data graph FROM SOURCES and proves it equals the
 * hand-declared [DataGraph] registry (docs/DATA_GRAPH_PLAN.md §6,
 * phase 1 — the parity bridge).
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
 *
 * The extracted list runs through the SAME parameterized [DataGraph]
 * checks as the registry, and the parity test pins extracted ==
 * declared on everything both sides model: input (id, cadence) and
 * derived (id, edge set, sharing). While both exist they verify each
 * other — and this closes the previously-open direction (code ⊆
 * registry): an edge in code that the registry misses now fails
 * parity instead of going undetected.
 *
 * Comment-stripped text parsing follows the honesty-test precedent.
 * Every parser has a positive control below (the vacuous-pass rule),
 * and the parity test is the global control — a parser that returns
 * nothing cannot reproduce the reviewed registry. Scope is exactly the
 * G0 boundary: repository/manager inputs and `stateIn` UseCases are
 * parsed; ViewModels never are.
 *
 * Local-probe caveat: Konsist reads sources but Gradle tracks the
 * compiled classpath — probe rule changes with `--rerun-tasks`
 * (CLAUDE.md § Konsist).
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
            )
        }
    }

    private fun realExtraction(): Pair<List<DataGraph.Node>, List<String>> {
        val inputs = extractInputs()
        return assembleGraph(inputs, extractDeriveds(inputs))
    }

    // ---- the real graph, extracted, checked, and pinned to the registry ----

    @Test
    fun `the extracted graph is coherent and problem-free`() {
        val (nodes, problems) = realExtraction()
        assertEquals(emptyList<String>(), problems)
        assertEquals(emptyList<NodeId>(), DataGraph.missingNodes(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.duplicateIds(nodes))
        assertEquals(emptyList<NodeId>(), DataGraph.cycleMembers(nodes))
        assertEquals(emptyList<String>(), DataGraph.invalidGates(nodes))
        assertEquals(emptyList<String>(), DataGraph.eagerOverPolls(nodes))
    }

    @Test
    fun `extraction reproduces the hand-declared registry`() {
        val (nodes, _) = realExtraction()

        val extractedInputs = nodes
            .filterIsInstance<DataGraph.Input>()
            .map { it.id to it.cadence }
            .toSet()
        val declaredInputs = DataGraph.nodes
            .filterIsInstance<DataGraph.Input>()
            .map { it.id to it.cadence }
            .toSet()
        assertEquals(declaredInputs, extractedInputs)

        val extractedDerived = nodes
            .filterIsInstance<DataGraph.Derived>()
            .map { Triple(it.id, it.from.toSet(), it.sharing) }
            .toSet()
        val declaredDerived = DataGraph.nodes
            .filterIsInstance<DataGraph.Derived>()
            .map { Triple(it.id, it.from.toSet(), it.sharing) }
            .toSet()
        assertEquals(declaredDerived, extractedDerived)
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
    fun `assembly resolves a gate to its upstream poll`() {
        val (nodes, problems) = assembleGraph(
            listOf(RawInput("WearCompanionRepository", "observeWatchAppStatus", Cadence.POLL, "watchCompanion")),
            listOf(RawDerived("ObserveWatchAppStatusUseCase", setOf("watchCompanion"), "WhileSubscribed")),
        )
        assertEquals(emptyList<String>(), problems)
        val derived = nodes.filterIsInstance<DataGraph.Derived>().single()
        assertEquals(Sharing.Gated(poll = NodeId.WATCH_COMPANION), derived.sharing)
    }

    @Test
    fun `the derived-id naming convention resolves each shell shape`() {
        assertEquals("buttonHealthDisplay", derivedNodeId("ComputeButtonHealthDisplayUseCase"))
        assertEquals("watchAppStatus", derivedNodeId("ObserveWatchAppStatusUseCase"))
        assertEquals("somethingNew", derivedNodeId("SomethingNewUseCase"))
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

    private fun constructorParams(
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
            PARAM.find(raw.trim())?.let { it.groupValues[1] to it.groupValues[2] }
        }
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

    // ---- assembly (pure; controls call it with doctored raw lists) ----

    private fun assembleGraph(
        inputs: List<RawInput>,
        deriveds: List<RawDerived>,
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
                transform = EXTRACTED_TRANSFORM,
                shell = raw.className,
                sharing = Sharing.Eager,
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
        val SHARING = Regex("""SharingStarted\s*\.\s*([A-Za-z]+)""")
        val PARAM = Regex("""^(?:private\s+)?(?:val\s+|var\s+)?([A-Za-z0-9_]+)\s*:\s*([A-Za-z0-9_.]+)""")

        /** Transforms are not extracted (scheduled for deletion with the registry's strings). */
        const val EXTRACTED_TRANSFORM = "extracted-from-source"
    }
}
