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

package com.chriscartland.garage.domain.graph

/**
 * The vocabulary and checks of the shared data graph
 * (docs/DATA_GRAPH_PLAN.md, rule G6).
 *
 * There is NO hand-declared node list — since §6 the graph is DERIVED
 * FROM SOURCES by `DataGraphExtractionKonsistTest` (`:androidApp`):
 * inputs from `@NodeCadence` annotations, derived nodes from the
 * `:usecase` `stateIn` holders, readers from ViewModel constructors.
 * This object holds what remains shared: the node/edge types, and the
 * pure check functions the extracted graph runs through. The
 * human-readable rendering is the generated `docs/DATA_GRAPH.md`,
 * pinned to the sources by the same test.
 *
 * Correctness by construction, where the language can carry it:
 *  - Node identity is the [NodeId] enum — the closed id vocabulary.
 *    Extraction resolves each discovered node id against it, so a
 *    declaration the enum doesn't know is a loud failure, and
 *    [missingNodes] fires when an enum entry's code was deleted: the
 *    enum and the code pin each other in both directions.
 *  - [Sharing.Gated] REQUIRES the poll that justifies it as a
 *    constructor argument — a gate cannot be declared without naming
 *    the expensive source it exists for. [invalidGates] verifies the
 *    named poll really is a POLL-cadence node upstream.
 *
 * Two kinds of node:
 *  - [Input]: a node that is written to. Exactly one owner (the
 *    type declaring the annotated `StateFlow`/flow function).
 *  - [Derived]: computed from other nodes, with [Derived.shell] the
 *    class holding its `stateIn` (the policy shell, rule G1).
 *
 * THE LINE NOT TO CROSS: deleting this file must break exactly its own
 * tests ([DataGraphTest], the extraction test) and nothing else. If
 * the graph description ever holds a flow, resolves a dependency, or
 * is read at runtime, it has become a reactive framework — paid for in
 * SKIE bridging, Konsist legibility, and every lint that reads
 * constructors. (`@NodeCadence`'s SOURCE retention makes the runtime
 * half of this impossible by construction.)
 *
 * Scope (rule G0, docs/DATA_GRAPH_PLAN.md): the graph ENDS at the
 * UseCase boundary. Inputs are repository/manager StateFlows; derived
 * nodes are `stateIn` UseCases; together they are the terminal
 * app-wide surface the VM layer consumes. ViewModel-level state is
 * structurally a SINK — `:usecase` cannot import `:viewmodel`, so VM
 * state can never be anyone's upstream — and is never a node (its
 * consistency is a presentation-layer concern; see G7's VM-level
 * residence, exemplified by `HomeDoorStateMapper`). Wear wires a
 * subset of the same inputs but none of the derived nodes.
 *
 * The check functions take the node list as a parameter — the
 * extraction test passes the extracted graph, and every check has a
 * positive control proving it can fail on a doctored graph (the
 * repo's vacuous-pass rule).
 */
object DataGraph {
    /**
     * Every node in the graph, as a closed enum — the id vocabulary
     * extraction resolves against. [id] is the camelCase name the code
     * uses for the value: the declaration name (or `@NodeCadence` id
     * override) for inputs, the decapitalized `Compute`/`Observe` …
     * `UseCase` class stem for derived nodes.
     */
    enum class NodeId(
        val id: String,
    ) {
        AUTH_STATE("authState"),
        CURRENT_DOOR_EVENT("currentDoorEvent"),
        RECENT_DOOR_EVENTS("recentDoorEvents"),
        PAGINATION_STATE("paginationState"),
        BUTTON_HEALTH("buttonHealth"),
        SNOOZE_STATE("snoozeState"),
        SERVER_CONFIG("serverConfig"),
        ALLOWLIST("allowlist"),
        TEST_NOTIFICATION_SANDBOX("testNotificationSandbox"),
        NOW_EPOCH_SECONDS("nowEpochSeconds"),
        IS_CHECK_IN_STALE("isCheckInStale"),
        WATCH_COMPANION("watchCompanion"),
        BUTTON_HEALTH_DISPLAY("buttonHealthDisplay"),
        EFFECTIVE_SNOOZE_STATE("effectiveSnoozeState"),
        WATCH_APP_STATUS("watchAppStatus"),
    }

    /** What makes a node's value change. Part of every node's contract. */
    enum class Cadence {
        /** Quiet unless an explicit call writes it: a user action or an app-initiated fetch. */
        USER_ACTION,

        /** Server-initiated (FCM); can land at any time, including in Doze. */
        PUSH,

        /**
         * A fixed-interval collection loop — the only cadence that
         * justifies [Sharing.Gated], because it is the only one that
         * costs battery/IPC/network to keep open.
         */
        POLL,

        /** Always-on tick (NOW_EPOCH_SECONDS); fans out widely, dedups away downstream. */
        CLOCK,

        /** Changes only when an upstream node changes. Implied for every [Derived]. */
        DERIVED,
    }

    /**
     * How a [Derived] node's `stateIn` is started (DATA_GRAPH_PLAN.md
     * §2). Sealed so that gating carries its own justification: you
     * cannot write [Gated] without naming the poll it pauses.
     */
    sealed interface Sharing {
        /** Always computed. Legal only when no [Cadence.POLL] is in the transitive closure. */
        data object Eager : Sharing

        /**
         * Pauses its upstream while unobserved; the last value is
         * retained. [poll] is the POLL-cadence source this gate
         * exists to pause — [invalidGates] verifies it is really a
         * poll and really upstream.
         */
        data class Gated(
            val poll: NodeId,
        ) : Sharing
    }

    sealed interface Node {
        val id: NodeId
        val cadence: Cadence
    }

    /**
     * A node that is written to. [owner] is the class holding the
     * MutableStateFlow. [from] is the reactive upstream an ADR-015
     * manager's implementation collects (extracted from its class body) —
     * `isCheckInStale` re-evaluates on every `currentDoorEvent`, so
     * rendering it as a bare root would launder a PUSH edge past the G7
     * shared-root rule. [cadence] describes the node's SELF-driven half
     * (the periodic tick that makes the manager more than a derivation);
     * root analysis counts the node itself AND everything in [from].
     * Lifecycle coupling (a user-scoped cache clearing on sign-out) is
     * NOT an edge — edges are value derivations, not lifetime triggers.
     */
    data class Input(
        override val id: NodeId,
        val owner: String,
        override val cadence: Cadence,
        val from: List<NodeId> = emptyList(),
    ) : Node

    /**
     * A node computed from other nodes. [shell] is the class holding
     * the `stateIn` (rule G1's policy shell); [from] is extracted from
     * the flow expression feeding that `stateIn`. [readBy] names the
     * screen ViewModels that observe it (extracted from their
     * constructors) — it feeds the rendering and becomes a [ScreenRead]
     * for the G7 [sharedRootFindings] check.
     */
    data class Derived(
        override val id: NodeId,
        val from: List<NodeId>,
        val shell: String,
        val sharing: Sharing,
        val readBy: List<String> = emptyList(),
    ) : Node {
        override val cadence: Cadence get() = Cadence.DERIVED
    }

    fun find(
        id: NodeId,
        nodes: List<Node>,
    ): Node? = nodes.firstOrNull { it.id == id }

    /**
     * Every [Input] transitively upstream of [start] — including, for a
     * manager input with [Input.from], the input itself AND its
     * upstream roots. Iterative (a cycle cannot overflow it;
     * [cycleMembers] reports cycles).
     */
    fun sourcesOf(
        start: Node,
        nodes: List<Node>,
    ): Set<Input> {
        val byId = nodes.associateBy { it.id }
        val sources = mutableSetOf<Input>()
        val seen = mutableSetOf<NodeId>()
        val stack = ArrayDeque<Node>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!seen.add(node.id)) continue
            when (node) {
                is Input -> {
                    sources.add(node)
                    node.from.mapNotNull { byId[it] }.forEach(stack::addLast)
                }
                is Derived -> node.from.mapNotNull { byId[it] }.forEach(stack::addLast)
            }
        }
        return sources
    }

    /**
     * [NodeId] constants with no entry in [nodes]. With [duplicateIds]
     * empty too, the list is a bijection with the enum — an edge can
     * then never point at a missing entry (the compile-time enum
     * reference plus the bijection close what the old dangling-edge
     * check covered, and more). Empty = coherent.
     */
    fun missingNodes(nodes: List<Node>): List<NodeId> {
        val present = nodes.map { it.id }.toSet()
        return NodeId.entries.filterNot(present::contains)
    }

    /** Node ids declared more than once. Empty = coherent. */
    fun duplicateIds(nodes: List<Node>): List<NodeId> =
        nodes
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .sortedBy { it.name }

    /**
     * Nodes that participate in a dependency cycle, by Kahn peeling:
     * every node whose dependencies resolve gets removed; whatever
     * remains is cyclic. Empty = acyclic.
     */
    fun cycleMembers(nodes: List<Node>): List<NodeId> {
        val byId = nodes.associateBy { it.id }
        val fromOf = { node: Node ->
            when (node) {
                is Input -> node.from
                is Derived -> node.from
            }
        }
        val remainingDeps = nodes.associate { node ->
            node.id to fromOf(node).filter(byId::containsKey).toMutableSet()
        }
        val dependents = mutableMapOf<NodeId, MutableList<NodeId>>()
        nodes.forEach { node ->
            fromOf(node).forEach { dependents.getOrPut(it) { mutableListOf() }.add(node.id) }
        }
        val ready = ArrayDeque(remainingDeps.filterValues { it.isEmpty() }.keys)
        val resolved = mutableSetOf<NodeId>()
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            if (!resolved.add(id)) continue
            dependents[id]?.forEach { dependentId ->
                val deps = remainingDeps.getValue(dependentId)
                deps.remove(id)
                if (deps.isEmpty()) ready.addLast(dependentId)
            }
        }
        return nodes.map { it.id }.filterNot(resolved::contains).sortedBy { it.name }
    }

    /**
     * G4 mechanized over the typed gate: a [Sharing.Gated] node's
     * declared [Sharing.Gated.poll] must be a [Cadence.POLL] node in
     * its transitive closure. The type already forces every gate to
     * name a poll; this verifies the named poll is real and upstream.
     * Empty = conformant.
     */
    fun invalidGates(nodes: List<Node>): List<String> =
        nodes
            .filterIsInstance<Derived>()
            .mapNotNull { d ->
                val gated = d.sharing as? Sharing.Gated ?: return@mapNotNull null
                val upstream = sourcesOf(d, nodes)
                val declared = upstream.firstOrNull { it.id == gated.poll }
                when {
                    declared == null -> "${d.id.id}: declared poll ${gated.poll.id} is not upstream"
                    declared.cadence != Cadence.POLL -> "${d.id.id}: declared poll ${gated.poll.id} is not POLL-cadence"
                    else -> null
                }
            }.sorted()

    /**
     * [Sharing.Eager]'s stated precondition, mechanized: an Eager node
     * may not have a [Cadence.POLL] source in its transitive closure —
     * eager collection would keep the poll running for the whole
     * process, which is exactly what gating exists to prevent
     * (DATA_GRAPH_PLAN.md §2). Empty = conformant.
     */
    fun eagerOverPolls(nodes: List<Node>): List<String> =
        nodes
            .filterIsInstance<Derived>()
            .filter { it.sharing == Sharing.Eager }
            .mapNotNull { d ->
                val polls = sourcesOf(d, nodes).filter { it.cadence == Cadence.POLL }
                if (polls.isEmpty()) {
                    null
                } else {
                    "${d.id.id}: Eager over poll ${polls.joinToString("+") { it.id.id }}"
                }
            }.sorted()

    /**
     * One reactive consumption of a graph node by a screen ViewModel.
     * [route] names the path — a conduit method (`"current"`,
     * `"position"`), a derived node's id, an input's own manager
     * (`"isCheckInStale"`), or `"direct"` — so two independent flows of
     * the same root are two reads even when they carry the same id.
     */
    data class ScreenRead(
        val screen: String,
        val route: String,
        val node: NodeId,
    )

    /**
     * One G7 finding: [screen] observes [root] through two or more
     * independent flows ([routes]), so it can render two projections of
     * the same instant one frame apart. The remedy is to collapse them
     * into one derivation — or an adjudicated exemption keyed on [key].
     */
    data class SharedRootFinding(
        val screen: String,
        val root: NodeId,
        val routes: List<String>,
    ) {
        val key: String get() = "$screen | ${root.id}"

        override fun toString(): String = "$key | via ${routes.joinToString(" + ")}"
    }

    /**
     * G7 mechanized over EVERY screen read — derived nodes, conduit
     * methods, and direct input reads alike (the pre-C4 form paired
     * only Derived×Derived, which missed a screen reading a derived
     * node next to one of that node's own inputs). A root reached
     * through ≥2 distinct routes into one screen is a finding.
     * [Cadence.CLOCK] roots are exempt (ticks that change nothing
     * dedup away) — the exemption is per ROOT, so a CLOCK-cadence
     * manager input with a PUSH upstream no longer launders that
     * upstream. Empty = conformant.
     */
    fun sharedRootFindings(
        nodes: List<Node>,
        reads: List<ScreenRead>,
    ): List<SharedRootFinding> {
        val byId = nodes.associateBy { it.id }
        return reads
            .distinct()
            .flatMap { read ->
                val node = byId[read.node] ?: return@flatMap emptyList()
                sourcesOf(node, nodes)
                    .filter { it.cadence != Cadence.CLOCK }
                    .map { root -> Triple(read.screen, root.id, read.route) }
            }.distinct()
            .groupBy({ it.first to it.second }, { it.third })
            .filterValues { it.size >= 2 }
            .map { (screenRoot, routes) ->
                SharedRootFinding(screenRoot.first, screenRoot.second, routes.sorted())
            }.sortedBy { it.key }
    }
}
