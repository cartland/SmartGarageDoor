# ViewModel Scoping Issue — Multi-Instance Propagation Hazard

**Status:** Documented, not yet fixed. Workaround shipped in PR #354.

This document captures a structural issue in how the app instantiates
ViewModels, and why it caused a subtle production-only propagation bug
in the snooze feature. The issue is not specific to snooze — it affects
every shared ViewModel that exposes a `StateFlow`.

## TL;DR

The app creates **three separate `DefaultRemoteButtonViewModel` instances**
at runtime. Each has its own `_snoozeState: MutableStateFlow<SnoozeState>`
and its own observer coroutine watching the repository's singleton flow.
Compose reads from only ONE of those instances. Under production
conditions, the one Compose reads from can silently miss a repository
emission while the other two see it — producing the "card title stays
enabled after saving a snooze" bug.

Tests never caught this because every test wires a single VM directly.

PR #354 worked around the symptom by writing `_snoozeState` directly from
the UseCase's suspend return value, bypassing the observer chain. The
root architectural issue — three competing VM instances — is unchanged.

## Flow map (snooze path)

```
NetworkSnoozeRepository (@Singleton)
  └─ snoozeStateFlow: MutableStateFlow<SnoozeState>         ← one instance, source of truth
        │
        │ exposed as: observeSnoozeState(): Flow<SnoozeState>
        │             (type widened to Flow, same object)
        │
        ▼
  ObserveSnoozeStateUseCase.invoke() → Flow<SnoozeState>    ← passthrough, same object
        │
        │ collected by: RemoteButtonViewModel.init (one per VM)
        │
   ┌────┼────┬────────────────────────────────┬────────────────────────────┐
   │    │    │                                │                            │
   ▼    ▼    ▼                                ▼                            ▼
  VM #1 (Activity scope, dead code)    VM #2 (Home nav entry)         VM #3 (Profile nav entry)
    ._snoozeState (MutableStateFlow)     ._snoozeState                  ._snoozeState
    ._snoozeAction                       ._snoozeAction                 ._snoozeAction
    viewModelScope.launch(io){collect}   (same pattern)                 (same pattern)
                                                │                            │
                                                ▼                            ▼
                                         HomeContent reads                ProfileContent reads
                                         .buttonState only                .snoozeState + .snoozeAction
                                         (no snooze UI)                   (the user-facing UI)
```

**Reference identity:** the repository's `MutableStateFlow` object is
preserved through `observeSnoozeState()` and `ObserveSnoozeStateUseCase`
(no `.map`, `.asStateFlow()`, `.stateIn`, etc.). After that point,
reference identity fragments — each VM constructs its own internal
`MutableStateFlow` from scratch.

## Root cause: three VMs, not one

Three independent factors combine to produce three `DefaultRemoteButtonViewModel`
instances:

### 1. The DI provider is not `@Singleton`

`AppComponent.kt:155-164`:

```kotlin
val remoteButtonViewModel: DefaultRemoteButtonViewModel
    @Provides get() = DefaultRemoteButtonViewModel(...)
```

No `@Singleton` annotation. Every access to `component.remoteButtonViewModel`
returns a new instance. Compare to `provideSnoozeRepository()` at line 336,
which is correctly `@Singleton`.

The same applies to `authViewModel` (line 103), `appLoggerViewModel`
(line 121), `appSettingsViewModel` (line 137), `doorViewModel` (line 143).
None of the ViewModels have `@Singleton`.

### 2. Nav3 creates a separate ViewModelStore per nav entry

`Main.kt:170`:

```kotlin
entryDecorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<Screen>(),
    rememberViewModelStoreNavEntryDecorator<Screen>(),
),
```

`rememberViewModelStoreNavEntryDecorator` replaces `LocalViewModelStoreOwner`
with a per-entry owner for the content of each nav destination. `viewModel { }`
inside that destination resolves against that per-entry store.

### 3. `viewModel { component.remoteButtonViewModel }` is called three times

| Call site | File:line | `ViewModelStoreOwner` it resolves against | Is the resulting VM used? |
|---|---|---|---|
| `AppNavigation` (outside `NavDisplay`) | `Main.kt:122` | Activity (fallback via `LocalViewModelStoreOwner`) | **No — dead code.** The `buttonViewModel` local is never read again in `Main.kt`. |
| `HomeContent` | `HomeContent.kt:79` | Home nav entry's store | Yes — reads `.buttonState` for the garage button. **Does not read `.snoozeState`.** |
| `ProfileContent` | `ProfileContent.kt:65` | Profile nav entry's store | Yes — reads both `.snoozeState` and `.snoozeAction`. This is the user-facing snooze UI. |

Each call goes through the factory lambda `{ component.remoteButtonViewModel }`,
which (because of #1) constructs a brand-new `DefaultRemoteButtonViewModel`.
The `viewModel { }` helper then caches it in whichever store was active.
Three owners → three cached instances.

## Why tests didn't catch it

All existing tests construct a single `DefaultRemoteButtonViewModel`
directly, not through the DI graph and not through `NavDisplay`:

- `RemoteButtonViewModelTest` — builds one VM.
- `SnoozeStateFlowPropagationTest` — builds one VM.
- `RealNetworkSnoozeRepositoryPropagationTest` — builds one VM with a real
  `NetworkSnoozeRepository`.
- `SharedRepositoryUseCasesTest` (new) — correctly models multiple UseCases
  sharing one repository, but still uses one implicit VM-equivalent
  subscriber per test.
- `SnoozeStateInstrumentedPropagationTest` — launches a minimal Composable
  host, not the full `NavDisplay` with per-entry decorators.

In every test, the repository's emission has exactly one subscriber, and
that subscriber is the one the test asserts against. In production, the
repository has three subscribers and the UI reads from one of them —
different object, different observer coroutine.

## Why prod behaves differently from tests (mechanism)

StateFlow guarantees every active subscriber sees every distinct value,
with conflation. In principle, all three VMs' observers should see every
emission. The propagation failure is therefore not a correctness violation
of StateFlow but a consequence of:

- **Dispatcher contention.** Each observer coroutine runs on
  `viewModelScope.launch(dispatchers.io)`, and `Dispatchers.IO` is a thread
  pool shared by every IO-bound coroutine in the app. Three observers on
  one pool can be scheduled in any order.
- **Lifecycle timing.** The Profile nav entry's VM and its observer
  coroutine are created lazily on first navigation to Profile. The observer
  subscribes some time after the emission that triggered the state change.
  StateFlow's replay-on-subscribe is supposed to cover this, but only if
  the subscription actually starts.
- **Compose recomposition.** `collectAsState` binds to a specific VM
  instance; if that VM's `_snoozeState` never received the emission, the
  UI never recomposes. The other two VMs' `_snoozeState`s can be correct
  and nobody is looking.

We have NOT definitively identified the exact mechanism that drops the
emission for Profile's VM. We have ruled out (via instrumented tests and
code review):

- R8 serializer stripping (PR #352 added explicit keep rules).
- `viewModelScope` cancellation on the submit path (PR #344-#346 moved
  state-critical writes to `externalScope`).
- Stale POST response parsing (PR #349 uses the POST response directly).
- Observer-chain reference-identity mismatch (the map above confirms
  identity is preserved up to the VM's internal flow).

What remains is structural: three independent subscribers on a shared
thread pool + a lifecycle-sensitive UI bound to one specific subscriber.

## Dead code: `Main.kt:122`

```kotlin
val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
```

This line constructs VM #1 (Activity scope). The `buttonViewModel` local
is never read elsewhere in `Main.kt`. Its only effect is to spin up a
ghost VM whose `init` block launches a door-event collector, a snooze
observer, and a button state machine — all running for the lifetime of
the Activity, consuming IO pool slots and Activity memory — for zero
benefit.

## Related: `DefaultDoorViewModel` has the same issue

`AppComponent.kt:143-153` declares `doorViewModel` without `@Singleton`.
`Main.kt:118` receives one via parameter (from `MainActivity` ownership).
If any nav entry also calls `viewModel { component.doorViewModel }`, a
second instance exists with its own state. The current code doesn't do
this — but the vulnerability is there.

`DefaultAuthViewModel` is somewhat protected: `ActivityViewModels.kt:31`
provides an `activityViewModel(...)` helper specifically documented as
"critical for ViewModels with instance state (e.g., SignInClient in
AuthViewModel)." If auth uses that helper, it survives this class of bug.
If not, check.

## What PR #354 did (the workaround)

`DefaultRemoteButtonViewModel.snoozeOpenDoorsNotifications` was modified
to write directly to `_snoozeState` from the UseCase's suspend return
value:

```kotlin
is AppResult.Success -> {
    val newState = result.data
    _snoozeState.value = newState   // direct write — bypasses the observer
    _snoozeAction.value = when (newState) { ... }
    scheduleActionReset()
}
```

This guarantees Profile's VM (whichever VM the submit happened through)
gets the correct state, because the write happens in the same coroutine
where the return value arrived — no dispatcher hop, no subscription race.

The observer chain is still present and still fires for the other two
VMs, but the UI doesn't depend on it anymore.

## Open questions

1. **Should the VM be singleton-scoped, or do the per-tab ViewModelStores
   serve a purpose we'd lose by consolidating?** The current architecture
   implies tabs might want their own state (e.g., a per-tab snackbar).
   For `RemoteButtonViewModel` and `DoorViewModel`, the state is
   inherently global (one door, one button). For truly per-screen state,
   a different VM would make sense.

2. **Should the `AppComponent` providers be `@Singleton`?** Adding
   `@Singleton` alone is not enough — Android's `ViewModelStore.clear()`
   would still call `onCleared()` on the shared VM when any nav entry is
   popped, canceling its `viewModelScope`. So `@Singleton` must come with
   either (a) a single, long-lived `ViewModelStoreOwner` (Activity), or
   (b) not using Android's `ViewModelStore` at all for these VMs.

3. **Should we keep the PR #354 direct write even after the structural
   fix?** Defensive programming argues yes. Simplicity argues no. The
   trade-off is one extra line in the VM vs. one more thing that could
   drift out of sync if someone changes the interpretation function.

4. **Does `DefaultAuthViewModel` use `activityViewModel(...)` consistently
   everywhere it's consumed?** If yes, it's safe. If no, it has the same
   hazard (and the existence of `ActivityViewModels.kt` suggests someone
   has already seen a related bug in auth).

5. **Does `DefaultDoorViewModel` have any latent data-propagation bug
   waiting to be discovered?** Currently it's created once (in
   `MainActivity` and passed as a parameter down the tree), so the bug
   doesn't manifest. But the DI pattern doesn't enforce this.

## Solution space (for discussion)

Not picking one here — this document is the starting point for the
discussion. Candidate approaches, each with trade-offs:

- **A. `activityViewModel(...)` at every call site.** Use the existing
  helper at `ActivityViewModels.kt:31` with an explicit Activity-scoped
  owner. One VM per Activity, reads and writes converge. Requires getting
  the Activity `ViewModelStoreOwner` into each Composable (via
  `LocalActivity.current as ViewModelStoreOwner` or a custom
  `CompositionLocal`).

- **B. `@Singleton` in DI + shared `ViewModelStoreOwner`.** Annotate the
  provider, but also ensure `viewModel { }` uses a single owner everywhere
  (typically the Activity). Effectively the same as A.

- **C. Shared domain-level StateFlow, no per-VM `_snoozeState`.** Expose
  the repository's `StateFlow<SnoozeState>` directly through the UseCase
  (typed as `StateFlow`, not `Flow`), and have Compose `collectAsState`
  on that object across all screens. VMs stop mediating this particular
  state; they keep their action overlays etc.

- **D. Leave the workaround, accept the cost.** PR #354's direct write
  is a complete fix for the only observed symptom. We could stop here,
  at the cost of one line of "I know this looks redundant" in the VM
  and three competing observer coroutines that waste IO threads.

- **E. Kill `Main.kt:122`'s dead VM unconditionally.** Low-risk, small
  win, independent of the bigger decision.

Any of A/B/C would make the workaround in D unnecessary. E is orthogonal
and worth doing regardless.
