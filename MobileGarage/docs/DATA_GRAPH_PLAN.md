---
category: plan
status: active
last_verified: 2026-08-15
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
- **G6 — The node list is derived from code and checked, never hand-written.**
  The graph is extracted from sources (§4): adding an input = one
  `@NodeCadence` annotation + one `NodeId` enum entry + one provider per
  component; adding a derived node = the `stateIn` UseCase itself + its enum
  entry. The extraction test keeps the graph coherent and the generated
  `DATA_GRAPH.md` keeps it reviewable. Scar: #871 → #873 (only `AppComponent`
  updated; iOS `main` broke).
- **G7 — Nodes read together are derived together.** Two residences, per G0:
  (a) **Graph level, mechanized**: if one screen reads two derived UseCase
  nodes over a shared non-clock root, collapse them — `sharedRootViolations`
  checks this over `readBy`, extracted from the ViewModel constructors. (b) **VM level, by review**: the
  same principle inside a ViewModel — the original instance was
  `HomeViewModel`'s `warning` / `sinceStatus` / voice-gate trio deriving from
  the same mirror in separate `stateIn`s, with card ↔ gate consistency
  promised only by a comment; the `HomeDoorStateMapper` collapse (#1203) is
  the exemplar fix and stays, even though the node is no longer in the
  registry. Clock roots are exempt (ticks dedup away).

## 4. The graph description — derived from code

There is **no hand-declared node list**. `domain/…/graph/DataGraph.kt` holds
the shared vocabulary and the pure check functions; the graph itself is
extracted from sources by `DataGraphExtractionKonsistTest` (`:androidApp`):

- **Inputs** from `@NodeCadence` annotations on the consumed
  repository/manager declarations — cadence (plus an id override where the
  declaration name isn't the node name) is the only hand-written metadata.
  Placement is fenced to `:domain`/`:usecase` (a `:viewmodel` input cannot
  exist, per G0), and `SOURCE` retention makes the annotation unreadable at
  runtime by construction.
- **Derived nodes** from the `:usecase` `stateIn` holders — id from the class
  name, edges from the flow expression before `.stateIn(` (seeds reading
  `.value` are construction-time plumbing, not edges), sharing from the
  `SharingStarted` literal, a gate's poll resolved from the transitive
  closure.
- **Readers** (the G7 key) from `:viewmodel` constructors: a parameter typed
  as a derived shell class, or `StateFlow<output>` named after the node.
  Constructor references are consumption of the terminal surface — VM STATE
  stays out of the graph (G0).

Correctness-by-construction, where the language carries it: node identity is
the closed `NodeId` enum (extraction resolves against it — an unknown id is a
loud failure, and `missingNodes` fires when an enum entry's code was deleted,
so enum and code pin each other in both directions), and `Sharing` is sealed
(`Gated(poll)` cannot be declared without naming its justification).

Checks run over the extracted graph, each with a positive control (the
vacuous-pass rule): acyclicity, `invalidGates`, `eagerOverPolls` (an Eager
node may not sit over a POLL source), the G7 shared-root rule, and the
enum bijection. The human-readable rendering is the **generated
[`DATA_GRAPH.md`](./DATA_GRAPH.md)** (table + mermaid), pinned byte-exact by
the same test — a graph change shows up in review as a diff of that file, and
CI fails until `./scripts/generate-data-graph.sh` regenerates it.

**The line not to cross:** deleting `DataGraph.kt` must break exactly its own
tests and nothing else. If the graph description ever holds a flow, resolves
a dependency, or is read at runtime, it has become a reactive framework —
paid for in SKIE bridging, Konsist legibility, and every lint that reads
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

A same-day hardening series followed (#1208–#1211): hydration-vs-sign-out
generation guards in both snapshot-hydrating repos, the typed registry
(`NodeId` enum + sealed `Sharing`) with the shell-file edge-accuracy check,
T1 closed stronger than proposed (#1210 — `CheckInStalenessManager` widened
to `StateFlow`, both VM mirrors deleted as ADR-022 pass-throughs, ADR-015
amended), and explicit `MutableStateFlow` type arguments in `:viewmodel`
making the G3 mirror rule total (#1211). #1212 then drew the G0 boundary.

Still open (tracked in `DATA_CACHING_STRATEGY.md` §5): T5–T13 minus the parts
closed above, and the nav-rail settings-mirror burn-down exemption in
`ViewModelDomainMirrorKonsistTest`.

## 6. Derive the graph from code (agreed 2026-08-15 — EXECUTED same day)

The registry's residual weakness was its stringly-typed half: `owner`,
`transform`, `shell`, and `readBy` were prose pinned to sources by a
Konsist honesty test, and the edge check covered only registry ⊆ code. The
agreed end state inverts the model: **the code is the declaration, and the
graph is extracted from it** — G6 becomes "the node list is derived and
checked," with nothing left to hand-maintain.

Design, settled in discussion 2026-08-15:

- **The extraction domain is exactly the G0 boundary**: inputs = `@Singleton`
  repository/manager-owned `StateFlow`s; derived nodes = `stateIn` UseCases.
  No VM parsing — VM state is a sink (G0), so the extractor never has to
  normalize screen-scoped outliers.
- **Cadence is the only non-derivable metadata.** A small annotation (e.g.
  `@NodeCadence(PUSH)`) on the input's public `StateFlow` property declares
  the one fact code cannot express. Everything else is read from sources:
  edges from `combine` arguments, sharing from the `SharingStarted` literal,
  owner/shell from the declaring file, readers from constructor references.
- **A Konsist-based extractor** builds the node list from sources. The
  existing `DataGraph` check functions are already parameterized on a `nodes`
  list, so acyclicity / gate-justification / shared-root re-point at the
  extracted list unchanged. This closes the currently-open direction
  (code ⊆ registry) by construction: an edge in code IS a graph edge.
- **The rendered graph is generated, not written**: a `DATA_GRAPH.md` with a
  mermaid diagram, produced by a generator with a `--check` mode (the
  `generate-ui-gallery.py` pattern) so CI fails when the committed rendering
  drifts from the code.
- **End state deletes the hand-declared `nodes` list** and the `owner` /
  `transform` / `shell` strings; the `NodeId` enum either becomes the
  annotation vocabulary or falls away with the list.

The §4 "line not to cross" stands unchanged: extraction happens in tests and
tooling, never at runtime — the graph description stays inert.

**Phase 1 — LANDED (annotation + extractor + parity bridge).** `@NodeCadence`
(`domain/…/graph/NodeCadence.kt`, SOURCE retention so it cannot exist at
runtime) sits on all 11 input declarations — the consumed interface/manager
declaration, never an `override`; `id` is overridden only where the
declaration name is not the node name (`state` → `testNotificationSandbox`,
`observeWatchAppStatus` → `watchCompanion`).
`DataGraphExtractionKonsistTest` extracts inputs from the annotations and
derived nodes from the `:usecase` `stateIn` holders (edges from the flow
expression before `.stateIn(`, so seeds reading `.value` don't count;
sharing from the `SharingStarted` literal; a gate's poll resolved as the
unique POLL source upstream), runs the extracted list through the same
parameterized checks, and pinned **extracted == registry** on input
(id, cadence) and derived (id, edge set, sharing) while both existed —
the bridge that let the registry review the extractor before retiring.
The same PR mechanized `Sharing.Eager`'s stated precondition as
`DataGraph.eagerOverPolls`, and fenced `@NodeCadence` placement to the G0
boundary.

**End state — LANDED (registry deleted; the graph is the code).** The
hand-declared `nodes` list, the `transform` strings, and the Konsist honesty
test are gone; `DataGraph.kt` keeps only the vocabulary (the `NodeId` enum —
extraction resolves against it and `missingNodes` fires when an entry's code
is deleted, so enum and code pin each other) and the parameterized checks.
`readBy` found its extracted home: `:viewmodel` constructor parameters typed
as a derived shell class, or `StateFlow<output>` named after the node (the
injected-value shape) — consumption of the terminal surface, never VM state
(G0). The reviewable artifact is the generated
[`DATA_GRAPH.md`](./DATA_GRAPH.md) (table + mermaid), pinned byte-exact by
the extraction test and regenerated with `./scripts/generate-data-graph.sh`
(whose `--check` is the pin itself; it runs in the required Android unit-test
gate on every PR). One deliberate cost: a wrong-but-coherent extraction now
has no second declaration to disagree with — the committed rendering diff in
review, the per-parser positive controls, and the checks are the safety net.

**Fail-closed layer — LANDED 2026-08-15 (completeness: discovery paired with
exhaustiveness).** An extractor is complete only for what matches it, and the
byte-pin catches *drift*, never *absence* — a node that never appeared
produces no diff. Two silent holes proved it (`paginationState`, observed by
the history screen yet absent; `currentDoorPosition`, a second repo flow of
the same row). So every discovery rule now has a sweep over an enumerable
universe, in `DataGraphExtractionKonsistTest`:

- **C1 — node membership.** Every parameterless flow-typed member of a
  `:domain` or `:usecase` INTERFACE is a `@NodeCadence` node or a reasoned
  entry in `MobileGarage/data-graph-node-exemptions.txt` (stale entries fail;
  a reason is mandatory; parameterized funs are excluded by construction — a
  keyed stream cannot be an app-wide node). Cadence stays the one hand-written
  fact, but its *presence* is now enforced: an unannotated flow is a decision
  someone writes down, never a hole. Outcome of the first run:
  `paginationState` became a USER_ACTION node; `currentDoorPosition` was
  DELETED — position is a pure projection of `currentDoorEvent`, so the map
  moved into `ObserveDoorEventsUseCase.position()` and the repo no longer
  owns a second root for G7 to chase.
- **C2 — derivation placement.** `.stateIn(` may live only in `:usecase`
  commonMain (where derived extraction reads it). `:viewmodel` may `stateIn`
  only on `viewModelScope` — a G0 sink; the other shared modules never
  (repositories keep ADR-022 always-on collectors). An app-scoped `stateIn`
  elsewhere would be a shared derivation the graph cannot see.
- **C5 — no silent dead nodes.** A derived node no screen reads, or an
  `Observe*` conduit no ViewModel injects, FAILS the build instead of
  vanishing from the rendering (the extraction used to filter unread conduits
  out — an extraction miss was indistinguishable from dead code).
