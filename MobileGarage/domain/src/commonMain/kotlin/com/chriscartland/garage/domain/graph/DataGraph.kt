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
 * Correctness by construction, where the language can carry it:
 *  - Node identity is the [NodeId] enum — a typo'd or dangling edge
 *    does not compile, so there is no dangling-edge check to run.
 *    [missingNodes] + [duplicateIds] pin the remaining direction: the
 *    entry list is a bijection with the enum.
 *  - [Sharing.Gated] REQUIRES the poll that justifies it as a
 *    constructor argument — a gate cannot be declared without naming
 *    the expensive source it exists for. [invalidGates] verifies the
 *    named poll really is a POLL-cadence node upstream.
 *
 * Two kinds of entry:
 *  - [Input]: a node that is written to. Exactly one owner (the
 *    `@Singleton` repository or ADR-015 manager holding the
 *    MutableStateFlow).
 *  - [Derived]: computed from other nodes by a named pure function,
 *    with [Derived.shell] naming the class that holds its `stateIn`
 *    (the policy shell, rule G1) — the file the honesty test checks
 *    the edges against.
 *
 * THE LINE NOT TO CROSS: deleting this file must break exactly its own
 * tests ([DataGraphTest], the Konsist honesty test) and nothing else.
 * If the registry ever holds a flow, resolves a dependency, or is read
 * at runtime, it has become a reactive framework — paid for in SKIE
 * bridging, Konsist legibility, and every lint that reads constructors.
 *
 * Scope: app-scoped nodes of the phone graph (Android + iOS share
 * these VMs), plus the one screen-scoped derivation that survived the
 * G7 fan-out collapse (HOME_DOOR_STATE — a single stateIn at
 * viewModelScope). Wear wires a subset of the same inputs but none of
 * the derived nodes below.
 *
 * The check functions take the node list as a parameter so tests can
 * run them against doctored graphs — every check has a positive
 * control proving it can fail (the repo's vacuous-pass rule).
 */
object DataGraph {
    /**
     * Every node in the graph, as a closed enum. Edges reference these
     * constants, so an edge to a nonexistent node is a compile error,
     * not a test failure. [id] is the camelCase name the code uses for
     * the value (property names, combine arguments) — the honesty
     * test's text anchor.
     */
    enum class NodeId(
        val id: String,
    ) {
        AUTH_STATE("authState"),
        CURRENT_DOOR_EVENT("currentDoorEvent"),
        RECENT_DOOR_EVENTS("recentDoorEvents"),
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
        HOME_DOOR_STATE("homeDoorState"),
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

    /** A node that is written to. [owner] is the class holding the MutableStateFlow. */
    data class Input(
        override val id: NodeId,
        val owner: String,
        override val cadence: Cadence,
    ) : Node

    /**
     * A node computed from other nodes by the named pure function
     * ("Object.function", or [IDENTITY] for a pass-through cache).
     * [shell] names the class holding the `stateIn` (rule G1's policy
     * shell) — the honesty test resolves that file and checks it
     * references [transform] and every [from] node. [readBy] names the
     * screen ViewModels that observe it — the G7 shared-root check
     * keys on it.
     */
    data class Derived(
        override val id: NodeId,
        val from: List<NodeId>,
        val transform: String,
        val shell: String,
        val sharing: Sharing,
        val readBy: List<String> = emptyList(),
    ) : Node {
        override val cadence: Cadence get() = Cadence.DERIVED
    }

    /** Transform sentinel for a pass-through cache with no mapping. */
    const val IDENTITY: String = "identity"

    val nodes: List<Node> = listOf(
        // ---- Inputs: one owner each (DATA_GRAPH_PLAN.md G3) ----
        Input(NodeId.AUTH_STATE, owner = "FirebaseAuthRepository", cadence = Cadence.USER_ACTION),
        Input(NodeId.CURRENT_DOOR_EVENT, owner = "NetworkDoorRepository", cadence = Cadence.PUSH),
        Input(NodeId.RECENT_DOOR_EVENTS, owner = "NetworkDoorRepository", cadence = Cadence.PUSH),
        Input(NodeId.BUTTON_HEALTH, owner = "NetworkButtonHealthRepository", cadence = Cadence.PUSH),
        Input(NodeId.SNOOZE_STATE, owner = "NetworkSnoozeRepository", cadence = Cadence.USER_ACTION),
        Input(NodeId.SERVER_CONFIG, owner = "CachedServerConfigRepository", cadence = Cadence.USER_ACTION),
        Input(NodeId.ALLOWLIST, owner = "CachedFeatureAllowlistRepository", cadence = Cadence.USER_ACTION),
        Input(NodeId.TEST_NOTIFICATION_SANDBOX, owner = "DefaultTestNotificationRepository", cadence = Cadence.USER_ACTION),
        Input(NodeId.NOW_EPOCH_SECONDS, owner = "DefaultLiveClock", cadence = Cadence.CLOCK),
        // A derivation implemented as manager-owned state (doorEvent +
        // clock -> stale flag). Listed as an Input because that is the
        // shape of the code (the manager owns the MutableStateFlow and
        // exposes StateFlow since the T1 widening; consumers pass the
        // reference through). Cadence is CLOCK: ticks drive the writes.
        Input(NodeId.IS_CHECK_IN_STALE, owner = "CheckInStalenessManager", cadence = Cadence.CLOCK),
        // Owner is the interface: the polling impl is per-platform
        // (Play Services on Android; Unavailable elsewhere).
        Input(NodeId.WATCH_COMPANION, owner = "WearCompanionRepository", cadence = Cadence.POLL),
        // ---- Derived nodes (pure core + policy shell, G1/G4) ----
        Derived(
            id = NodeId.BUTTON_HEALTH_DISPLAY,
            from = listOf(NodeId.AUTH_STATE, NodeId.BUTTON_HEALTH, NodeId.NOW_EPOCH_SECONDS),
            transform = "ButtonHealthDisplayLogic.compute",
            shell = "ComputeButtonHealthDisplayUseCase",
            sharing = Sharing.Eager,
            readBy = listOf("HomeViewModel"),
        ),
        Derived(
            id = NodeId.EFFECTIVE_SNOOZE_STATE,
            from = listOf(NodeId.SNOOZE_STATE, NodeId.NOW_EPOCH_SECONDS),
            transform = "SnoozeStateExpiry.effective",
            shell = "ComputeEffectiveSnoozeStateUseCase",
            sharing = Sharing.Eager,
            readBy = listOf("ProfileViewModel"),
        ),
        Derived(
            id = NodeId.WATCH_APP_STATUS,
            from = listOf(NodeId.WATCH_COMPANION),
            transform = IDENTITY,
            shell = "ObserveWatchAppStatusUseCase",
            sharing = Sharing.Gated(poll = NodeId.WATCH_COMPANION),
            readBy = listOf("ProfileViewModel"),
        ),
        // Screen-scoped (viewModelScope, not applicationScope): the G7
        // collapse of the former warning / sinceStatus / voice-gate
        // fan-out into ONE derivation. Its from-edges point at the root
        // inputs; the VM-side LoadingResult wrapper over
        // CURRENT_DOOR_EVENT is presentation phase, not a graph node.
        Derived(
            id = NodeId.HOME_DOOR_STATE,
            from = listOf(NodeId.CURRENT_DOOR_EVENT, NodeId.IS_CHECK_IN_STALE, NodeId.NOW_EPOCH_SECONDS),
            transform = "HomeDoorStateMapper.compute",
            shell = "HomeViewModel",
            sharing = Sharing.Eager,
            readBy = listOf("HomeViewModel"),
        ),
    )

    fun find(
        id: NodeId,
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
        val seen = mutableSetOf<NodeId>()
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

    /**
     * [NodeId] constants with no entry in [nodes]. With [duplicateIds]
     * empty too, the list is a bijection with the enum — an edge can
     * then never point at a missing entry (the compile-time enum
     * reference plus the bijection close what the old dangling-edge
     * check covered, and more). Empty = coherent.
     */
    fun missingNodes(nodes: List<Node> = this.nodes): List<NodeId> {
        val present = nodes.map { it.id }.toSet()
        return NodeId.entries.filterNot(present::contains)
    }

    /** Node ids declared more than once. Empty = coherent. */
    fun duplicateIds(nodes: List<Node> = this.nodes): List<NodeId> =
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
    fun cycleMembers(nodes: List<Node> = this.nodes): List<NodeId> {
        val byId = nodes.associateBy { it.id }
        val remainingDeps = nodes.associate { node ->
            node.id to when (node) {
                is Input -> mutableSetOf<NodeId>()
                is Derived -> node.from.filter(byId::containsKey).toMutableSet()
            }
        }
        val dependents = mutableMapOf<NodeId, MutableList<NodeId>>()
        nodes.filterIsInstance<Derived>().forEach { d ->
            d.from.forEach { dependents.getOrPut(it) { mutableListOf() }.add(d.id) }
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
    fun invalidGates(nodes: List<Node> = this.nodes): List<String> =
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
                    }.map { (a, b) -> "$screen reads ${a.id.id} + ${b.id.id} over a shared non-clock root" }
            }.sorted()
    }
}
