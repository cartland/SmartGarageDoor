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

/**
 * The pure half of deriving the data graph from sources: text parsers,
 * graph assembly, and the `DATA_GRAPH.md` rendering. No Konsist scope
 * and no filesystem — every function here takes plain values, which is
 * what lets `DataGraphExtractionKonsistTest`'s positive controls feed
 * doctored text and doctored raw lists directly (the vacuous-pass
 * rule). The test class owns the scope-bound orchestration.
 */
internal object DataGraphExtraction {
    val STATE_IN = Regex("""\.\s*stateIn\s*\(""")
    val ANNOTATION = Regex("""@NodeCadence\s*\(([^)]*)\)""")
    val STATE_FLOW_PROP = Regex("""val\s+[A-Za-z0-9_]+\s*:\s*StateFlow<([^<>]+)>""")

    private val TYPE_HEADER = Regex("""\b(interface|class|object)\s+([A-Za-z0-9_]+)""")
    private val DECLARATION = Regex("""\b(val|fun)\s+([A-Za-z0-9_]+)""")
    private val CLASS_NAME = Regex("""\bclass\s+([A-Za-z0-9_]+)""")
    private val CLASS_WITH_CTOR = Regex("""\bclass\s+([A-Za-z0-9_]+)\s*\(""")
    private val SHARING = Regex("""SharingStarted\s*\.\s*([A-Za-z]+)""")
    private val STATE_FLOW_PARAM = Regex("""^StateFlow<\s*([A-Za-z0-9_.]+)\s*>$""")

    // ---- raw shapes (assembly input) ----

    data class RawInput(
        val ownerType: String,
        val declarationName: String,
        val cadence: Cadence,
        val nodeId: String,
    )

    data class RawDerived(
        val className: String,
        val fromIds: Set<String>,
        /** "Eagerly", "WhileSubscribed", another literal, or null when none was found. */
        val sharingName: String?,
        /** The `StateFlow<X>` type argument of the stateIn property, for reader attribution. */
        val outputType: String? = null,
    )

    /** How the inputs reach the screens: reactive pass-through conduits and direct injections. */
    data class InputConsumption(
        /** Conduit class -> the input node ids it exposes. Only conduits some ViewModel injects. */
        val conduitInputs: Map<String, Set<String>>,
        /** Conduit class -> the ViewModels injecting it. */
        val conduitReaders: Map<String, List<String>>,
        /** Input node id -> ViewModels injecting the owner and referencing the declaration. */
        val directReaders: Map<String, List<String>>,
    )

    // ---- parsers ----

    fun annotationSites(strippedText: String): List<RawInput> {
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

    fun className(strippedText: String): String? = CLASS_NAME.find(strippedText)?.groupValues?.get(1)

    fun sharingName(afterStateIn: String): String? = SHARING.find(afterStateIn)?.groupValues?.get(1)

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
        return splitTopLevel(strippedText.substring(open + 1, end)).mapNotNull(::parseParam)
    }

    private fun parseParam(raw: String): Pair<String, String>? {
        var param = raw.trim()
        for (modifier in listOf("override", "private", "protected", "internal", "val", "var")) {
            param = param.removePrefix("$modifier ").trim()
        }
        val colon = param.indexOf(':')
        if (colon < 0) return null
        val name = param.substring(0, colon).trim()
        if (!name.matches(Regex("""[A-Za-z0-9_]+"""))) return null
        val type = param
            .substring(colon + 1)
            .substringBefore('=')
            .trim()
            .replace(Regex("""\s+"""), " ")
        return name to type
    }

    /** Constructor parameters as (name, base type without generics) — the edge-matching shape. */
    fun constructorParams(
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

    fun edgeIds(
        flowExpr: String,
        params: List<Pair<String, String>>,
        inputDeclsByOwner: Map<String, List<Pair<String, String>>>,
        derivedIdByClass: Map<String, String>,
    ): Set<String> =
        referencedInputIds(flowExpr, params, inputDeclsByOwner) +
            params.mapNotNull { (paramName, paramType) ->
                derivedIdByClass[paramType]?.takeIf { memberOrInvokeRegex(paramName).containsMatchIn(flowExpr) }
            }

    /** Input node ids referenced as `param.declaration` in [text], for the given typed params. */
    fun referencedInputIds(
        text: String,
        params: List<Pair<String, String>>,
        declsByOwner: Map<String, List<Pair<String, String>>>,
    ): Set<String> =
        params
            .flatMap { (paramName, paramType) ->
                declsByOwner[paramType].orEmpty().mapNotNull { (decl, nodeId) ->
                    nodeId.takeIf { referenceRegex(paramName, decl).containsMatchIn(text) }
                }
            }.toSet()

    private fun referenceRegex(
        param: String,
        decl: String,
    ) = Regex("""(^|[^A-Za-z0-9_])${Regex.escape(param)}\s*\.\s*${Regex.escape(decl)}($|[^A-Za-z0-9_])""")

    private fun memberOrInvokeRegex(param: String) = Regex("""(^|[^A-Za-z0-9_])${Regex.escape(param)}\s*[.(]""")

    fun derivedNodeId(className: String): String =
        className
            .removePrefix("Compute")
            .removePrefix("Observe")
            .removeSuffix("UseCase")
            .replaceFirstChar { it.lowercaseChar() }

    /** The derived shell class this constructor parameter reads, or null (see the control for the rules). */
    fun attributeReader(
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

    /**
     * One file's conduit entry, or null. A conduit is an ADR-022
     * pass-through: an `Observe*` class (never a stateIn holder, never
     * an input owner) whose body references an input declaration. The
     * `Observe` prefix is the reactive-observation filter — action
     * UseCases read `.value` at act time, which is not observation.
     */
    fun conduitEntry(
        strippedText: String,
        inputOwners: Set<String>,
        declsByOwner: Map<String, List<Pair<String, String>>>,
    ): Pair<String, Set<String>>? {
        val name = className(strippedText) ?: return null
        if (!name.startsWith("Observe") || name in inputOwners) return null
        if (STATE_IN.containsMatchIn(strippedText)) return null
        val ids = referencedInputIds(strippedText, constructorParams(strippedText, name), declsByOwner)
        return if (ids.isEmpty()) null else name to ids
    }

    /** (derived shell class, reader name) pairs found in one file's class constructors. */
    fun readerPairsIn(
        text: String,
        deriveds: List<RawDerived>,
    ): List<Pair<String, String>> =
        CLASS_WITH_CTOR.findAll(text).toList().flatMap { match ->
            val cls = match.groupValues[1]
            namedTypedParams(text, cls).mapNotNull { (paramName, paramType) ->
                attributeReader(deriveds, paramName, paramType)?.let { it to cls.removePrefix("Default") }
            }
        }

    /** (conduit class, reader name) pairs found in one file's class constructors. */
    fun conduitPairsIn(
        text: String,
        conduitNames: Set<String>,
    ): List<Pair<String, String>> =
        CLASS_WITH_CTOR.findAll(text).toList().flatMap { match ->
            val cls = match.groupValues[1]
            namedTypedParams(text, cls).mapNotNull { (_, paramType) ->
                paramType.takeIf { it in conduitNames }?.let { it to cls.removePrefix("Default") }
            }
        }

    /** (input node id, reader name) pairs for direct owner injections in one file's constructors. */
    fun directInputPairsIn(
        text: String,
        declsByOwner: Map<String, List<Pair<String, String>>>,
    ): List<Pair<String, String>> =
        CLASS_WITH_CTOR.findAll(text).toList().flatMap { match ->
            val cls = match.groupValues[1]
            referencedInputIds(text, constructorParams(text, cls), declsByOwner)
                .map { it to cls.removePrefix("Default") }
        }

    fun declsByOwner(inputs: List<RawInput>): Map<String, List<Pair<String, String>>> =
        inputs
            .groupBy { it.ownerType }
            .mapValues { (_, list) -> list.map { it.declarationName to it.nodeId } }

    /** Remove block + line comments so prose cannot create (or hide) a node or an edge. */
    fun stripComments(text: String): String =
        text
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { line ->
                // `(?<!:)//` so a URL's :// is not a comment start.
                val match = Regex("""(?<!:)//""").find(line)
                if (match != null) line.substring(0, match.range.first) else line
            }

    // ---- assembly ----

    fun assembleGraph(
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
            resolveSharing(draft, raw.sharingName, draftGraph, problems)
        }
        return (inputNodes + derivedNodes) to problems
    }

    private fun resolveSharing(
        draft: DataGraph.Derived,
        sharingName: String?,
        draftGraph: List<DataGraph.Node>,
        problems: MutableList<String>,
    ): DataGraph.Derived =
        when (sharingName) {
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
                problems.add("${draft.id.id}: unrecognized SharingStarted.$sharingName")
                draft
            }
        }

    // ---- rendering (pinned to docs/DATA_GRAPH.md) ----

    fun render(
        nodes: List<DataGraph.Node>,
        consumption: InputConsumption,
    ): String {
        val ordered = nodes.sortedBy { it.id.ordinal }
        val inputs = ordered.filterIsInstance<DataGraph.Input>()
        val deriveds = ordered.filterIsInstance<DataGraph.Derived>()
        return buildString {
            appendLine("<!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.")
            appendLine("     Regenerate: ./scripts/generate-data-graph.sh")
            appendLine("     The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->")
            appendLine()
            appendLine("# Shared data graph")
            appendLine()
            renderTable(inputs, deriveds, consumption)
            appendLine()
            appendLine("Screens observe inputs through the listed `Observe*` pass-throughs (ADR-022)")
            appendLine("or direct manager injection. An input with no outgoing edge in the diagram is")
            appendLine("consumed at action time inside the data layer (fetch plumbing), not observed.")
            appendLine()
            renderMermaid(inputs, deriveds, consumption)
        }
    }

    private fun StringBuilder.renderTable(
        inputs: List<DataGraph.Input>,
        deriveds: List<DataGraph.Derived>,
        consumption: InputConsumption,
    ) {
        appendLine("| Node | Kind | Cadence | Declared by | Sharing | Read by |")
        appendLine("|---|---|---|---|---|---|")
        inputs.forEach { i ->
            val readBy = inputReadBy(i.id.id, consumption)
            appendLine("| `${i.id.id}` | input | ${i.cadence} | `${i.owner}` | — | $readBy |")
        }
        deriveds.forEach { d ->
            val sharing = when (val s = d.sharing) {
                is Sharing.Eager -> "eager"
                is Sharing.Gated -> "gated on `${s.poll.id}`"
            }
            val readBy = if (d.readBy.isEmpty()) "—" else d.readBy.joinToString(", ") { "`$it`" }
            appendLine("| `${d.id.id}` | derived | — | `${d.shell}` | $sharing | $readBy |")
        }
    }

    private fun inputReadBy(
        id: String,
        consumption: InputConsumption,
    ): String {
        val via = consumption.conduitInputs.keys
            .sorted()
            .filter { id in consumption.conduitInputs.getValue(it) }
            .map { "via `$it`" }
        val direct = consumption.directReaders[id].orEmpty().map { "`$it`" }
        val all = via + direct
        return if (all.isEmpty()) "—" else all.joinToString(", ")
    }

    private fun StringBuilder.renderMermaid(
        inputs: List<DataGraph.Input>,
        deriveds: List<DataGraph.Derived>,
        consumption: InputConsumption,
    ) {
        val conduits = consumption.conduitInputs.keys.sorted()
        val readers = (
            deriveds.flatMap { it.readBy } +
                consumption.conduitReaders.values.flatten() +
                consumption.directReaders.values.flatten()
        ).distinct().sorted()
        appendLine("```mermaid")
        appendLine("graph LR")
        inputs.forEach { appendLine("    ${it.id.id}([\"${it.id.id} · ${it.cadence}\"])") }
        deriveds.forEach { appendLine("    ${it.id.id}[\"${it.id.id}\"]") }
        conduits.forEach { appendLine("    $it[[\"$it\"]]") }
        readers.forEach { appendLine("    $it{{\"$it\"}}") }
        conduits.forEach { conduit ->
            consumption.conduitInputs
                .getValue(conduit)
                .sorted()
                .forEach { appendLine("    $it --> $conduit") }
            consumption.conduitReaders.getValue(conduit).forEach { appendLine("    $conduit --> $it") }
        }
        consumption.directReaders.keys.sorted().forEach { id ->
            consumption.directReaders.getValue(id).forEach { appendLine("    $id --> $it") }
        }
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
