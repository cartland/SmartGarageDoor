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
 * An inert description of the shared data graph
 * (docs/DATA_GRAPH_PLAN.md, rule G6).
 *
 * This object computes nothing and nothing flows through it. Values
 * still move through StateFlow and kotlin-inject exactly as they do
 * today; this is the description those wirings are CHECKED against, so
 * "where does this value come from, and when can it change" has one
 * written answer instead of three hand-mirrored DI components' worth.
 *
 * Two kinds of entry:
 *  - [Input]: a node that is written to. Exactly one owner (the
 *    `@Singleton` repository or ADR-015 manager holding the
 *    MutableStateFlow).
 *  - [Derived]: computed from other nodes by a named pure function.
 *    Never written to.
 *
 * THE LINE NOT TO CROSS: deleting this file must break exactly its own
 * tests ([DataGraphTest], the Konsist honesty test) and nothing else.
 * If the registry ever holds a flow, resolves a dependency, or is read
 * at runtime, it has become a reactive framework — paid for in SKIE
 * bridging, Konsist legibility, and every lint that reads constructors.
 *
 * Scope (v1): app-scoped nodes of the phone graph (Android + iOS share
 * these VMs). Screen-scoped derivations (the HomeViewModel trio) join
 * after the G7 fan-out collapse; Wear wires a subset of the same inputs
 * but none of the derived nodes below.
 *
 * The check functions take the node list as a parameter so tests can
 * run them against doctored graphs — every check has a positive
 * control proving it can fail (the repo's vacuous-pass rule).
 */
object DataGraph {
    /** What makes a node's value change. Part of every node's contract. */
    enum class Cadence {
        /** Quiet unless an explicit call writes it: a user action or an app-initiated fetch. */
        USER_ACTION,

        /** Server-initiated (FCM); can land at any time, including in Doze. */
        PUSH,

        /**
         * A fixed-interval collection loop — the only cadence that
         * justifies [Sharing.GATED], because it is the only one that
         * costs battery/IPC/network to keep open.
         */
        POLL,

        /** Always-on tick ([nowEpochSeconds]); fans out widely, dedups away downstream. */
        CLOCK,

        /** Changes only when an upstream node changes. Implied for every [Derived]. */
        DERIVED,
    }

    /** How a [Derived] node's `stateIn` is started (DATA_GRAPH_PLAN.md §2). */
    enum class Sharing {
        /** Always computed. Legal only when no [Cadence.POLL] is in the transitive closure. */
        EAGER,

        /** Pauses its upstream while unobserved; the last value is retained. */
        GATED,
    }

    sealed interface Node {
        val id: String
        val cadence: Cadence
    }

    /** A node that is written to. [owner] is the class holding the MutableStateFlow. */
    data class Input(
        override val id: String,
        val owner: String,
        override val cadence: Cadence,
    ) : Node

    /**
     * A node computed from other nodes by the named pure function
     * ("Object.function", or [IDENTITY] for a pass-through cache).
     * [readBy] names the screen ViewModels that observe it — the G7
     * shared-root check keys on it.
     */
    data class Derived(
        override val id: String,
        val from: List<String>,
        val transform: String,
        val sharing: Sharing,
        val readBy: List<String> = emptyList(),
    ) : Node {
        override val cadence: Cadence get() = Cadence.DERIVED
    }

    /** Transform sentinel for a pass-through cache with no mapping. */
    const val IDENTITY: String = "identity"

    val nodes: List<Node> = listOf(
        // ---- Inputs: one owner each (DATA_GRAPH_PLAN.md G3) ----
        Input("authState", owner = "FirebaseAuthRepository", cadence = Cadence.USER_ACTION),
        Input("currentDoorEvent", owner = "NetworkDoorRepository", cadence = Cadence.PUSH),
        Input("recentDoorEvents", owner = "NetworkDoorRepository", cadence = Cadence.PUSH),
        Input("buttonHealth", owner = "NetworkButtonHealthRepository", cadence = Cadence.PUSH),
        Input("snoozeState", owner = "NetworkSnoozeRepository", cadence = Cadence.USER_ACTION),
        Input("serverConfig", owner = "CachedServerConfigRepository", cadence = Cadence.USER_ACTION),
        Input("allowlist", owner = "CachedFeatureAllowlistRepository", cadence = Cadence.USER_ACTION),
        Input("testNotificationSandbox", owner = "DefaultTestNotificationRepository", cadence = Cadence.USER_ACTION),
        Input("nowEpochSeconds", owner = "DefaultLiveClock", cadence = Cadence.CLOCK),
        // A derivation implemented as manager-owned state (doorEvent +
        // clock -> stale flag). Listed as an Input because that is the
        // shape of the code today; DATA_CACHING_STRATEGY T1 tracks
        // widening it. Cadence is CLOCK because ticks drive the writes.
        Input("isCheckInStale", owner = "CheckInStalenessManager", cadence = Cadence.CLOCK),
        // Owner is the interface: the polling impl is per-platform
        // (Play Services on Android; Unavailable elsewhere).
        Input("watchCompanion", owner = "WearCompanionRepository", cadence = Cadence.POLL),
        // ---- Derived nodes (pure core + policy shell, G1/G4) ----
        Derived(
            id = "buttonHealthDisplay",
            from = listOf("authState", "buttonHealth", "nowEpochSeconds"),
            transform = "ButtonHealthDisplayLogic.compute",
            sharing = Sharing.EAGER,
            readBy = listOf("HomeViewModel"),
        ),
        Derived(
            id = "effectiveSnoozeState",
            from = listOf("snoozeState", "nowEpochSeconds"),
            transform = "SnoozeStateExpiry.effective",
            sharing = Sharing.EAGER,
            readBy = listOf("ProfileViewModel"),
        ),
        Derived(
            id = "watchAppStatus",
            from = listOf("watchCompanion"),
            transform = IDENTITY,
            sharing = Sharing.GATED, // 15s Play Services poll upstream
            readBy = listOf("ProfileViewModel"),
        ),
    )

    fun find(
        id: String,
        nodes: List<Node> = this.nodes,
    ): Node? = nodes.firstOrNull { it.id == id }

    /**
     * Every [Input] transitively upstream of [start]. Iterative (a
     * cycle cannot overflow it; [cycleMembers] reports cycles).
     */
    fun sourcesOf(
        start: Node,
        nodes: List<Node> = this.nodes,
    ): Set<Input> {
        val byId = nodes.associateBy { it.id }
        val sources = mutableSetOf<Input>()
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque<Node>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!seen.add(node.id)) continue
            when (node) {
                is Input -> sources.add(node)
                is Derived -> node.from.mapNotNull { byId[it] }.forEach(stack::addLast)
            }
        }
        return sources
    }

    /** Edges that name a node that does not exist. Empty = coherent. */
    fun danglingEdges(nodes: List<Node> = this.nodes): List<String> {
        val ids = nodes.map { it.id }.toSet()
        return nodes
            .filterIsInstance<Derived>()
            .flatMap { d -> d.from.filterNot(ids::contains).map { "${d.id} <- $it" } }
    }

    /** Node ids declared more than once. Empty = coherent. */
    fun duplicateIds(nodes: List<Node> = this.nodes): List<String> =
        nodes
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .sorted()

    /**
     * Nodes that participate in a dependency cycle, by Kahn peeling:
     * every node whose dependencies resolve gets removed; whatever
     * remains is cyclic. Empty = acyclic.
     */
    fun cycleMembers(nodes: List<Node> = this.nodes): List<String> {
        val byId = nodes.associateBy { it.id }
        val remainingDeps = nodes.associate { node ->
            node.id to when (node) {
                is Input -> mutableSetOf<String>()
                is Derived -> node.from.filter(byId::containsKey).toMutableSet()
            }
        }
        val dependents = mutableMapOf<String, MutableList<String>>()
        nodes.filterIsInstance<Derived>().forEach { d ->
            d.from.forEach { dependents.getOrPut(it) { mutableListOf() }.add(d.id) }
        }
        val ready = ArrayDeque(remainingDeps.filterValues { it.isEmpty() }.keys)
        val resolved = mutableSetOf<String>()
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            if (!resolved.add(id)) continue
            dependents[id]?.forEach { dependentId ->
                val deps = remainingDeps.getValue(dependentId)
                deps.remove(id)
                if (deps.isEmpty()) ready.addLast(dependentId)
            }
        }
        return nodes.map { it.id }.filterNot(resolved::contains).sorted()
    }

    /**
     * G4 mechanized: a [Sharing.GATED] node must have a [Cadence.POLL]
     * somewhere in its transitive closure. Gating a node whose sources
     * are all cheap buys nothing and costs a value-less first frame on
     * every screen entry. Empty = conformant.
     */
    fun unjustifiedGates(nodes: List<Node> = this.nodes): List<String> =
        nodes
            .filterIsInstance<Derived>()
            .filter { it.sharing == Sharing.GATED }
            .filterNot { d -> sourcesOf(d, nodes).any { it.cadence == Cadence.POLL } }
            .map { it.id }

    /**
     * G7 mechanized: two derived nodes read by the same screen over a
     * shared non-clock root emit independently, so the screen can
     * render them one frame apart. Collapse them into one derivation.
     * [Cadence.CLOCK] roots are exempt (ticks that change nothing
     * dedup away). Empty = conformant.
     */
    fun sharedRootViolations(nodes: List<Node> = this.nodes): List<String> {
        val derived = nodes.filterIsInstance<Derived>()
        return derived
            .flatMap { d -> d.readBy.map { screen -> screen to d } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (screen, readers) ->
                readers
                    .flatMap { a -> readers.map { b -> a to b } }
                    .filter { (a, b) -> a.id < b.id }
                    .filter { (a, b) ->
                        sourcesOf(a, nodes)
                            .intersect(sourcesOf(b, nodes))
                            .any { it.cadence != Cadence.CLOCK }
                    }.map { (a, b) -> "$screen reads ${a.id} + ${b.id} over a shared non-clock root" }
            }.sorted()
    }
}
