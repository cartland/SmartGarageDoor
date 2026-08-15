---
category: plan
status: active
last_verified: 2026-08-14
---

# Shared data graph — rules and build order

The shared KMP layer is a data graph: **input nodes** you can set, **pure
functions** between them, and **derived nodes** the UIs observe. This doc names
the parts that already exist, states the rules that keep the graph honest, and
lists the PRs that bring the repo into conformance. Companion to
[`DATA_CACHING_STRATEGY.md`](./DATA_CACHING_STRATEGY.md) (where a value may
*live*); this doc is about how values *flow*.

Goals (maintainer, 2026-08-14): adding a new type of data should mean adding one
input; new features should be new pure functions over existing nodes; the edges
should be describable so "where does this come from, and when can it change" has
one answer; and it should be explicit which parts of the graph compute always
vs. only while observed.

## 1. The graph as built

| Concept | Construct | Where |
|---|---|---|
| Input node (set/update) | `@Singleton` repository owning a `MutableStateFlow`, exposing `StateFlow` | 7 in `data/…/repository/` |
| Pure function | Named `object` with a `compute`/`forX` fun — no flows, no platform types (ADR-009) | `presentation-model/`, `ButtonHealthDisplayLogic`, `SnoozeStateExpiry` |
| Derived node | `combine(…).stateIn(applicationScope, …)` in a `@Singleton` UseCase | `ComputeButtonHealthDisplayUseCase`, `ComputeEffectiveSnoozeStateUseCase`, `ObserveWatchAppStatusUseCase` |
| VM-internal composition (outside the graph, G0) | `combine(…).stateIn(viewModelScope, Eagerly, seeded)` | `HomeViewModel.doorState` via `HomeDoorStateMapper` (#1203 collapse) |
| Edges | Constructor parameters, mirrored by hand across three DI components | `AppComponent` / `NativeComponent` / `WearComponent` |
| Observers | Shared `*ViewModel`s | Compose, SwiftUI, Wear |

## 2. Eager vs. observed-only (the settled question)

**Derived nodes are eager. Gating belongs on sources, and only on sources that
cost something to keep open.**

The proof is already running: `DefaultLiveClock` ticks at 1 Hz for the whole
process, feeds three derived nodes, and has never surfaced as a cost. That works
because of two preconditions, which are part of the rule, not an accident:

1. **The transform is pure and cheap** (a comparison, a `when` — never I/O).
2. **The output type dedups** — `StateFlow` conflates by equality, so an
   emission that changes nothing propagates to no one. A transform that
   allocates a fresh non-`equals` value every tick breaks this silently
   (that failure mode is #672's auth loop, at the input edge).

The one sanctioned gate is `SharingStarted.WhileSubscribed(5s)` on a node whose
**transitive upstream includes a live poll** — `ObserveWatchAppStatusUseCase`
gates a 15s Play Services poll. Note where the gate sits: on the poll, not the
value. `replayExpirationMillis` defaults to `Long.MAX_VALUE`, so the last value
is retained across the pause and a returning subscriber reads it synchronously.
`WhileSubscribed` does **not** re-emit `initialValue` after the timeout (a prior
CLAUDE.md sentence claimed it does; corrected in this PR). It is still never
correct on repository-owned state, where an emission in the dead window is lost.

Making laziness transitive through the middle of the graph is the repo's
most-repeated bug: a node with no value at first composition renders a
one-frame lie on every fresh `NavBackStackEntry` (#738, #739, and
`ObserveWatchAppStatusUseCase`'s own KDoc — the same fix three times).

## 3. Rules

- **G0 — The graph ends where `:viewmodel` begins.** Inputs are
  repository/manager `StateFlow`s; derived nodes are `stateIn` UseCases;
  together they are the terminal app-wide surface the VM layer consumes (the
  Phase-43 dependency rule read from the other side, including the sanctioned
  ADR-015 manager reads). ViewModel state is structurally a **sink** —
  `:usecase` cannot import `:viewmodel`, so VM state can never be anyone's
  upstream — and is deliberately NOT in the registry. VM-internal derivations
  (LoadingResult wrappers, action overlays, compositions like
  `HomeDoorState`) are presentation-layer concerns governed by the VM rules
  (ADR-023, P2 seeding, G7's VM-level residence below), not graph nodes.
  Adopted 2026-08-15; this is why `homeDoorState` was removed from the
  registry after briefly being added in #1206.
- **G1 — Pure core, policy shell.** The derivation is an `object` taking plain
  values; the UseCase holds the `combine`, the scope, and the sharing policy,
  nothing else. Canonical: `SnoozeStateExpiry.effective(state, now)`.
- **G2 — Edges project to the narrowest type the transform reads.** Signed-in
  gate → `Boolean`, not `AuthState`. This is also what guarantees G4's dedup
  precondition at the input edge. Scar: #672 (token rotation re-fired a
  `combine` forever). Enforced: `checkAuthStateProjection`.
- **G3 — One owner per input node.** A `MutableStateFlow` in a ViewModel whose
  type argument is a `:domain` type is a defect. Scar: `android/164-168`.
- **G4 — Derived nodes are eager; only expensive sources gate** (§2, with its
  two preconditions). `GATED` requires a poll in the transitive closure.
- **G5 — Mirrors seed from `upstream.value`,** never `Loading`/`null`/a
  literal. If `.value` is unreadable, widen the upstream type. Scar: #738/#739.
- **G6 — The node list is written once and checked, not searched for.** An
  inert `DataGraph` registry (§4) describes nodes and edges; tests keep it
  coherent and honest against sources. Adding an input = one registry line +
  one provider per component. Scar: #871 → #873 (only `AppComponent` updated;
  iOS `main` broke).
- **G7 — Nodes read together are derived together.** Two residences, per G0:
  (a) **Graph level, mechanized**: if one screen reads two derived UseCase
  nodes over a shared non-clock root, collapse them — `sharedRootViolations`
  checks this over the registry's `readBy`. (b) **VM level, by review**: the
  same principle inside a ViewModel — the original instance was
  `HomeViewModel`'s `warning` / `sinceStatus` / voice-gate trio deriving from
  the same mirror in separate `stateIn`s, with card ↔ gate consistency
  promised only by a comment; the `HomeDoorStateMapper` collapse (#1203) is
  the exemplar fix and stays, even though the node is no longer in the
  registry. Clock roots are exempt (ticks dedup away).

## 4. The `DataGraph` registry

`domain/…/graph/DataGraph.kt`: an **inert description** — `Input(id, owner,
cadence)` and `Derived(id, from, transform, shell, sharing)` entries. Since the
typed-registry hardening, correctness-by-construction carries what it can:

- **Node identity is the `NodeId` enum** — edges reference enum constants, so
  a typo'd or dangling edge is a compile error, not a test failure. A
  bijection check (`missingNodes` + `duplicateIds`) pins list ↔ enum.
- **`Sharing` is sealed**: `Eager`, or `Gated(poll: NodeId)` — a gate cannot
  be *declared* without naming the poll that justifies it; `invalidGates`
  verifies the named poll is real, upstream, and POLL-cadence.

Runtime-checkable properties (each with a positive control, per the repo's
vacuous-pass rule): acyclicity (iterative Kahn), the G7 shared-root rule, and
the bijection above. The honesty test (Konsist, `:androidApp`) checks the
registry against SOURCES: every `owner` / `transform` / `shell` / `readBy`
names a real declaration, and **edge accuracy (registry ⊆ code)** — each
derived node's `shell` file must reference its transform call and every
declared `from` node (by camelCase id or owner name), with comments stripped
so prose can't satisfy the check. The unchecked direction — an edge present
in code but missing from the registry — is stated, not covered; closing it
needs combine-argument parsing.

Registry scope is **exactly the G0 boundary**: repository/manager inputs and
`stateIn` UseCase deriveds — nothing screen-scoped. VM-internal derivations
are presentation compositions outside the graph (G0), governed by the VM-layer
rules, not registry entries.

**The line not to cross:** deleting `DataGraph.kt` must break exactly its own
tests and nothing else. If the registry ever holds a flow, resolves a
dependency, or is read at runtime, it has become a reactive framework — paid
for in SKIE bridging, Konsist legibility, and every lint that reads
constructors. Don't.

## 5. Build order — EXECUTED 2026-08-14

Each item was one PR:

1. ✅ **This doc + corrections** (#1201) — ADR draft renumbered to **ADR-036**;
   CLAUDE.md's `WhileSubscribed` mechanism sentence and "two DI components"
   undercount fixed (three: `AppComponent`, `NativeComponent`,
   `WearComponent`).
2. ✅ **`DataGraph` registry + all §4 checks** (#1202), landed together.
3. ✅ **Home fan-out collapsed (G7)** (#1203) — `HomeDoorState` +
   `HomeDoorStateMapper`, one combine at `viewModelScope`; the voice gate is a
   projection of the same node; zero snapshot diffs; no ctor change, DI graphs
   untouched.
4. ✅ **Structural rule for G3** (#1206) — `ViewModelDomainMirrorKonsistTest`
   (additive to `checkViewModelStateFlow`); both unseeded `DoorEvent?` mirrors
   deleted (ADR-022 pass-through); `homeDoorState` added to the registry (removed again 2026-08-15 when G0 drew the boundary at the UseCase layer).
5. ✅ **Live defects**: T2 fixed (#1204 — `externalScope.async{}.await()` +
   `finally`, test verified failing pre-fix); T3 fixed (#1205 —
   `registerInMemoryReset`, sign-out clears both tiers); **T4 was found
   already fixed upstream** when re-verified (the iOS bridge seeds
   `initialUser: restoredUser()`, pinned by `IosAuthUserStateHolderTest`) —
   the strategy doc's row was corrected instead of writing code.

Still open (tracked in `DATA_CACHING_STRATEGY.md` §5): T1 (widen
`CheckInStalenessManager` to `StateFlow`), T5–T13 minus the parts closed
above, and the nav-rail settings-mirror burn-down exemption in
`ViewModelDomainMirrorKonsistTest`.
