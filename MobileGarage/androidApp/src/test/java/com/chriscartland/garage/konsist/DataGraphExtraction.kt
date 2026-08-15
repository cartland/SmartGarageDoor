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
    private val FLOW_VAL = Regex("""\bval\s+([A-Za-z0-9_]+)\s*:\s*(?:StateFlow|SharedFlow|Flow)<""")
    private val FLOW_FUN = Regex("""\bfun\s+([A-Za-z0-9_]+)\s*\(\s*\)\s*:\s*(?:StateFlow|SharedFlow|Flow)<""")
    private val DECLARATION = Regex("""\b(val|fun)\s+([A-Za-z0-9_]+)""")
    private val FUN_DECL = Regex("""\bfun\s+([A-Za-z0-9_]+)\s*\(""")
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
        /** Extracted reactive upstream (ADR-015 manager impls only) — see [managerFromIds]. */
        val fromIds: Set<String> = emptySet(),
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
        /**
         * ALL extracted conduits, unfiltered: one without a reader fails
         * [orphanConduits] rather than silently vanishing from the graph.
         */
        val conduits: List<Conduit>,
        /** Conduit class -> the ViewModels injecting it (the diagram's class-level edges). */
        val conduitReaders: Map<String, List<String>>,
        /**
         * Every method-precise reactive input consumption by a screen:
         * conduit-method reads (route `"Conduit.method"`) and direct
         * injections (route = the node's own id, so two direct reads of
         * different nodes over one root stay two flows). Derived-node
         * reads are appended by the orchestration from `readBy`.
         */
        val reads: List<DataGraph.ScreenRead>,
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
     * An ADR-022 pass-through, method-precise: each fun of the conduit
     * mapped to the input node ids its body references. Method-level
     * resolution is what keeps the graph honest about granularity —
     * injecting `ObserveDoorEventsUseCase` does not mean reading every
     * door value; calling `.paginationState()` does.
     */
    data class Conduit(
        val className: String,
        val methods: Map<String, Set<String>>,
    ) {
        val inputs: Set<String> get() = methods.values.flatten().toSet()
    }

    /**
     * One file's conduit entry, or null. A conduit is an ADR-022
     * pass-through: an `Observe*` class (never a stateIn holder, never
     * an input owner) whose body references an input declaration. The
     * `Observe` prefix is the reactive-observation filter — action
     * UseCases read `.value` at act time, which is not observation.
     * Each fun's ids come from its own body slice (fun header to the
     * next fun), so `current()` and `paginationState()` resolve to
     * different nodes; `operator fun invoke` is the method `invoke`.
     */
    fun conduitEntry(
        strippedText: String,
        inputOwners: Set<String>,
        declsByOwner: Map<String, List<Pair<String, String>>>,
    ): Conduit? {
        val name = className(strippedText) ?: return null
        if (!name.startsWith("Observe") || name in inputOwners) return null
        if (STATE_IN.containsMatchIn(strippedText)) return null
        val params = constructorParams(strippedText, name)
        val funMatches = FUN_DECL.findAll(strippedText).toList()
        val methods = funMatches
            .mapIndexedNotNull { i, match ->
                val bodyEnd = funMatches.getOrNull(i + 1)?.range?.first ?: strippedText.length
                val body = strippedText.substring(match.range.first, bodyEnd)
                val ids = referencedInputIds(body, params, declsByOwner)
                if (ids.isEmpty()) null else match.groupValues[1] to ids
            }.toMap()
        return if (methods.isEmpty()) null else Conduit(name, methods)
    }

    /**
     * Method-precise conduit reads in one file's class constructors:
     * (reader, "Conduit.method", input node id) for every conduit
     * method the class actually calls. The route string is what lets
     * two reads of the SAME id count as two flows (G7's [ScreenRead]).
     */
    fun conduitMethodReadsIn(
        text: String,
        conduits: List<Conduit>,
    ): List<Triple<String, String, String>> {
        val byName = conduits.associateBy { it.className }
        return CLASS_WITH_CTOR
            .findAll(text)
            .toList()
            .flatMap { match ->
                val cls = match.groupValues[1]
                constructorParams(text, cls).flatMap { (paramName, paramType) ->
                    val conduit = byName[paramType] ?: return@flatMap emptyList()
                    conduit.methods.flatMap { (method, ids) ->
                        if (methodRefRegex(paramName, method).containsMatchIn(text)) {
                            ids.map { Triple(cls.removePrefix("Default"), "${conduit.className}.$method", it) }
                        } else {
                            emptyList()
                        }
                    }
                }
            }.distinct()
    }

    /** `param.method` for a named method; `param(` for `invoke` (the operator call). */
    private fun methodRefRegex(
        param: String,
        method: String,
    ): Regex =
        if (method == "invoke") {
            Regex("""(^|[^A-Za-z0-9_])${Regex.escape(param)}\s*\(""")
        } else {
            referenceRegex(param, method)
        }

    /**
     * The reactive upstream collected by an ADR-015 manager file's
     * implementation classes: direct input references plus conduit-
     * method references, minus the file's own declared ids. This is
     * what stops a manager from laundering a PUSH edge — its input
     * node carries the extracted `from`. Lifecycle coupling (caches
     * clearing on sign-out) is deliberately NOT extracted: only value
     * reads through inputs and conduits count.
     */
    fun managerFromIds(
        strippedText: String,
        conduits: List<Conduit>,
        declsByOwner: Map<String, List<Pair<String, String>>>,
        ownIds: Set<String>,
    ): Set<String> =
        (
            directInputPairsIn(strippedText, declsByOwner).map { it.first } +
                conduitMethodReadsIn(strippedText, conduits).map { it.third }
        ).toSet() - ownIds

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

    // ---- fail-closed sweeps (completeness: discovery paired with exhaustiveness) ----

    /**
     * The node-candidate universe of one file: parameterless flow-typed
     * members whose nearest enclosing type is an INTERFACE, as
     * (ownerInterface, declarationName). Parameterized flow funs are
     * excluded by construction — a keyed stream cannot be an app-wide
     * node (`countKey(key)`, `observeCount(key)`). Class/object members
     * are excluded — inputs are declared on the consumed interface,
     * never on an implementation (the `override` carries no annotation).
     */
    fun interfaceFlowMembers(strippedText: String): List<Pair<String, String>> {
        val typeHeaders = TYPE_HEADER
            .findAll(strippedText)
            .map { Triple(it.range.first, it.groupValues[1], it.groupValues[2]) }
            .toList()
        return (FLOW_VAL.findAll(strippedText) + FLOW_FUN.findAll(strippedText))
            .mapNotNull { match ->
                val owner = typeHeaders
                    .lastOrNull { it.first < match.range.first }
                    ?.takeIf { it.second == "interface" }
                    ?: return@mapNotNull null
                owner.third to match.groupValues[1]
            }.toList()
            .sortedBy { "${it.first}.${it.second}" }
    }

    /**
     * `Owner.decl | reason` entries; `#` comments and blank lines
     * ignored. An entry without a reason is itself an error — the file
     * exists to hold adjudications, not names.
     */
    fun parseNodeExemptions(text: String): Map<String, String> =
        text
            .lines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { line ->
                val key = line.substringBefore('|').trim()
                val reason = line.substringAfter('|', missingDelimiterValue = "").trim()
                require(reason.isNotEmpty()) { "node exemption `$key` has no reason — every exemption states why" }
                key to reason
            }

    /**
     * `Screen | root | reason` entries for adjudicated G7 findings;
     * the key half (`Screen | root`) matches [DataGraph.SharedRootFinding.key].
     * Same contract as [parseNodeExemptions]: comments and blanks
     * ignored, a reason is mandatory, stale entries fail the build.
     */
    fun parseSharedRootExemptions(text: String): Map<String, String> =
        text
            .lines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { line ->
                val parts = line.split('|').map { it.trim() }
                require(parts.size == 3 && parts.all { it.isNotEmpty() }) {
                    "shared-root exemption `$line` is not `Screen | root | reason` — every exemption states why"
                }
                "${parts[0]} | ${parts[1]}" to parts[2]
            }

    /**
     * The C1 verdict: universe members that are neither `@NodeCadence`
     * nodes nor exempted (missing), and exemption entries that no longer
     * name an unannotated member (stale — the declaration was deleted or
     * became a node, so the entry must go).
     */
    fun nodeSweepProblems(
        members: List<Pair<String, String>>,
        annotated: Set<Pair<String, String>>,
        exemptions: Set<String>,
    ): Pair<List<String>, List<String>> {
        val unannotated = members.filterNot { it in annotated }.map { "${it.first}.${it.second}" }.toSet()
        val missing = (unannotated - exemptions).sorted()
        val stale = (exemptions - unannotated).sorted()
        return missing to stale
    }

    /**
     * `.stateIn(` occurrences [path] may not host. Only `:usecase`
     * commonMain may share a flow on an app-wide scope (that is where
     * extraction reads derived nodes); `:viewmodel` may `stateIn` only
     * on `viewModelScope` (a G0 sink — presentation state, never an
     * app-wide node); the other shared modules may not at all
     * (repositories use ADR-022 always-on collectors).
     */
    fun illegalStateIns(
        path: String,
        strippedText: String,
    ): List<String> {
        if (path.contains("/usecase/src/commonMain/")) return emptyList()
        val matches = STATE_IN.findAll(strippedText).toList()
        if (matches.isEmpty()) return emptyList()
        if (!path.contains("/viewmodel/src/commonMain/")) {
            return matches.map { "$path: stateIn outside :usecase — repositories keep ADR-022 always-on collectors" }
        }
        return matches.mapNotNull { match ->
            val args = parenSlice(strippedText, match.range.last)
            if (args.contains("viewModelScope")) {
                null
            } else {
                "$path: stateIn in :viewmodel not scoped to viewModelScope — an app-scoped derivation belongs in :usecase"
            }
        }
    }

    /** The balanced-paren argument slice starting at [openIndex] (which must point at `(`). */
    private fun parenSlice(
        text: String,
        openIndex: Int,
    ): String {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex + 1, i)
                }
            }
        }
        return text.substring(openIndex + 1)
    }

    /** C5: extracted conduits no ViewModel injects — dead code or an extraction miss, never silence. */
    fun orphanConduits(
        conduits: List<Conduit>,
        conduitReaders: Map<String, List<String>>,
    ): List<String> =
        (conduits.map { it.className }.toSet() - conduitReaders.keys)
            .sorted()
            .map { "$it: conduit no ViewModel injects" }

    /** C5: derived nodes no screen reads — dead code or an extraction miss, never silence. */
    fun unreadDeriveds(nodes: List<DataGraph.Node>): List<String> =
        nodes
            .filterIsInstance<DataGraph.Derived>()
            .filter { it.readBy.isEmpty() }
            .map { "${it.id.id}: derived node no ViewModel reads" }
            .sorted()

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
                ?.let { id ->
                    DataGraph.Input(
                        id = id,
                        owner = raw.ownerType,
                        cadence = raw.cadence,
                        from = raw.fromIds
                            .mapNotNull { toNodeId(it, "manager edge of ${raw.ownerType}") }
                            .sortedBy { it.name },
                    )
                }
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
            appendLine("**Cadence** — what makes a value change. `USER_ACTION`: written only when")
            appendLine("the user or the app explicitly acts (a tap, a fetch). `PUSH`:")
            appendLine("server-initiated (FCM), can land at any time. `POLL`: a fixed-interval")
            appendLine("collection loop, the only cadence that justifies gating. `CLOCK`: an")
            appendLine("always-on tick.")
            appendLine()
            appendLine("**Read by** — the screens that reactively observe each value, with the")
            appendLine("conduit method in parentheses (`direct` = an injected manager or value).")
            appendLine("A dash means no screen observes it: the value feeds a derived node (see")
            appendLine("diagram) or is consumed at action time inside the data layer.")
            appendLine("`DiagnosticsViewModel` reads no graph nodes; Wear wires a subset of the")
            appendLine("inputs and none of the derived nodes.")
            appendLine()
            renderMermaid(inputs, deriveds, consumption)
            appendLine()
            appendLine("**Diagram legend** — `([x])` input (cadence-labeled) · `[x]` derived")
            appendLine("(`stateIn` UseCase) · `[[X]]` conduit (ADR-022 pass-through) · `{{X}}`")
            appendLine("screen ViewModel · `-. reacts .->` a manager's reactive upstream ·")
            appendLine("`-. poll, gated .->` a gated poll. Conduit → ViewModel edges are")
            appendLine("class-level; the table's Read by column is the per-value truth.")
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
            val readBy = inputReadBy(i.id.id, consumption.reads)
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

    /**
     * Method-precise readers of one input: `HomeViewModel (current,
     * position)` names the conduit methods; `direct` is an injected
     * manager or value. The diagram's conduit edges stay class-level,
     * so this column is where per-value consumption is stated.
     */
    private fun inputReadBy(
        id: String,
        reads: List<DataGraph.ScreenRead>,
    ): String {
        val mine = reads.filter { it.node.id == id }
        if (mine.isEmpty()) return "—"
        return mine
            .groupBy { it.screen }
            .toSortedMap()
            .map { (screen, screenReads) ->
                val routes = screenReads
                    .map { read -> if (read.route == id) "direct" else read.route.substringAfter('.') }
                    .distinct()
                    .sorted()
                "`$screen` (${routes.joinToString(", ")})"
            }.joinToString(", ")
    }

    private fun StringBuilder.renderMermaid(
        inputs: List<DataGraph.Input>,
        deriveds: List<DataGraph.Derived>,
        consumption: InputConsumption,
    ) {
        val conduits = consumption.conduits.sortedBy { it.className }
        val readers = (
            deriveds.flatMap { it.readBy } +
                consumption.conduitReaders.values.flatten() +
                consumption.reads.map { it.screen }
        ).distinct().sorted()
        appendLine("```mermaid")
        appendLine("graph LR")
        inputs.forEach { appendLine("    ${it.id.id}([\"${it.id.id} · ${it.cadence}\"])") }
        deriveds.forEach { appendLine("    ${it.id.id}[\"${it.id.id}\"]") }
        conduits.forEach { appendLine("    ${it.className}[[\"${it.className}\"]]") }
        readers.forEach { appendLine("    $it{{\"$it\"}}") }
        conduits.forEach { conduit ->
            conduit.inputs.sorted().forEach { appendLine("    $it --> ${conduit.className}") }
            // An orphan conduit (no readers) is a build failure via
            // orphanConduits; orEmpty keeps that failure attributed there.
            consumption.conduitReaders[conduit.className].orEmpty().forEach {
                appendLine("    ${conduit.className} --> $it")
            }
        }
        // Direct reads (route == the node's own id) — conduit reads are
        // already drawn class-level above.
        consumption.reads
            .filter { it.route == it.node.id }
            .map { it.node.id to it.screen }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
            .forEach { (id, screen) -> appendLine("    $id --> $screen") }
        // A manager input's reactive upstream (Input.from).
        inputs.forEach { input ->
            input.from.forEach { appendLine("    ${it.id} -. reacts .-> ${input.id.id}") }
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
