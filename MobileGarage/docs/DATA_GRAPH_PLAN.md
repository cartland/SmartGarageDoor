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
| Screen-scoped derived node | `combine(…).stateIn(viewModelScope, Eagerly, seeded)` | `HomeViewModel` (`warning`, `sinceStatus`, voice gate) |
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
- **G7 — Nodes read together are derived together.** If one screen reads two
  derived nodes over a shared non-clock root, collapse them into one
  derivation. Live instance: `HomeViewModel`'s `warning` / `sinceStatus` /
  voice-gate projection all derive from `_currentDoorEvent` in separate
  `stateIn`s; the status-card ↔ voice-gate consistency is currently promised by
  a code comment. Clock roots are exempt (ticks dedup away).

## 4. The `DataGraph` registry

`domain/…/graph/DataGraph.kt`: an **inert description** — `Input(id, owner,
cadence)` and `Derived(id, from, transform, sharing)` entries with a
`Cadence` enum (`USER_ACTION` / `PUSH` / `POLL` / `CLOCK` / `DERIVED`) and
`Sharing` (`EAGER` / `GATED`). Nothing flows through it; values still move via
`StateFlow` and kotlin-inject exactly as today.

Checks that make it worth having (all land in the same PR as the registry —
a node list nothing checks is a second source of truth, strictly worse than
none):

- every edge names a node that exists; the graph is acyclic (iterative check,
  not the recursive helper);
- `GATED` requires a `POLL` in the transitive closure (G4, mechanized);
- no screen in `readBy` reads two derived nodes sharing a non-clock root (G7);
- a **positive control** proving the gating check can fail (the repo's
  vacuous-pass rule);
- an honesty test (Konsist, in `:androidApp`) asserting each `Input.owner` and
  `Derived.transform` names a declaration that exists in sources.

Registry v1 covers **app-scoped nodes only**; screen-scoped nodes (the
`HomeViewModel` trio) are G7-by-review until the fan-out collapse lands, after
which the collapsed node joins the registry. Stated so it doesn't look covered
when it isn't.

**The line not to cross:** deleting `DataGraph.kt` must break exactly its own
tests and nothing else. If the registry ever holds a flow, resolves a
dependency, or is read at runtime, it has become a reactive framework — paid
for in SKIE bridging, Konsist legibility, and every lint that reads
constructors. Don't.

## 5. Build order

Each item is one PR; status updated as they land.

1. **This doc + corrections** — renumber `DATA_CACHING_STRATEGY.md` §6's draft
   ADR to **ADR-036** (ADR-035 was taken by the strings ADR); fix CLAUDE.md's
   `WhileSubscribed` mechanism sentence and its "two DI components" undercount
   (three: `AppComponent`, `NativeComponent`, `WearComponent`).
2. **`DataGraph` registry + all §4 checks**, landed together.
3. **Collapse the Home fan-out (G7)** — one pure derivation producing the
   card + gate state; zero-snapshot-diff expectation; both DI graphs if a ctor
   changes.
4. **Structural rule for G3** — Konsist: no `MutableStateFlow<T>` in
   `:viewmodel` where `T` is declared in `:domain` (allowlist for
   `LoadingResult`/action wrappers); fix the known unseeded `DoorEvent?`
   mirrors first. Additive to `checkViewModelStateFlow`, per the Konsist
   posture.
5. **Live defects** from `DATA_CACHING_STRATEGY.md` §5: T2 (stranded
   `isLoadingMore` on a cancelled screen scope), T3 (sign-out clears disk but
   not memory), T4 (iOS pre-seeds auth with a value no listener produced).
