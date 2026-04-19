# Compose Nav3 ViewModel scoping — multi-instance reality

**Who this is for:** Compose developers using
`androidx.navigation3` (Nav3) with
`rememberViewModelStoreNavEntryDecorator<T>()` to scope ViewModels per
navigation entry. Covers the default multi-instance behavior, what it
means for shared state, and the invariants to enforce.

## TL;DR

Nav3's `rememberViewModelStoreNavEntryDecorator<ScreenType>()` creates
one `ViewModelStore` **per navigation entry**. Two screens in the back
stack = two independent `ViewModelStore`s = two independent VM
instances of the same class. Shared state across screens lives in
`@Singleton` repositories, not in "the" ViewModel.

Treat "there is one `FooViewModel` in the app" as a false assumption.
Design for N.

## The default behavior

```kotlin
NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<Screen>(),
        rememberViewModelStoreNavEntryDecorator<Screen>(),
    ),
    entryProvider = entryProvider {
        entry<Screen.Home> { HomeContent(...) }
        entry<Screen.Profile> { ProfileContent(...) }
    },
)

@Composable
fun HomeContent() {
    val vm: FooViewModel = viewModel { component.fooViewModel }  // VM1
    // ...
}

@Composable
fun ProfileContent() {
    val vm: FooViewModel = viewModel { component.fooViewModel }  // VM2 (different!)
    // ...
}
```

The `rememberViewModelStoreNavEntryDecorator` attaches one
`ViewModelStoreOwner` per nav entry. `viewModel { ... }` keys on the
class by default — the cache lookup happens inside the entry's store,
so Home and Profile each get their own `FooViewModel` instance.

When the user navigates back from Profile and re-navigates to
Profile, Nav3 pops + re-creates that entry's store — so
**re-entering a screen creates a fresh VM** unless your back-stack
handling preserves the entry.

## Why this matters

Three common architectural assumptions silently break if you forget
the multi-instance reality:

### 1. "My ViewModel owns the state — there is one."

Per-nav-entry scoping makes this false. Each entry's VM has its own
`_state: MutableStateFlow<T>`. If two screens display the same domain
state (e.g., a snooze setting visible on both Home and Profile),
those two VMs diverge silently until something re-syncs them.

**Fix:** the ViewModel doesn't own the state. A `@Singleton`
repository does. Every VM exposes the **same** flow reference by
getting it from the repo via an observe-UseCase. See
[`kotlin-inject.md`](kotlin-inject.md) for how to make the repository
an actual singleton — the default in most DI frameworks requires
configuration.

### 2. "I can cache the result in the VM and it'll be there when the user comes back."

Only if the same nav entry is retained. A back/navigate-forward
sequence often re-creates the entry → new store → new VM → empty
cache. Anything expensive the VM did must be re-done unless the
underlying repository caches.

**Fix:** put the cache in the repository. The VM is a projection,
not a cache.

### 3. "Exactly one observer means I can use a `SharedFlow` with replay=0."

N observers in general. A `SharedFlow { replay = 0 }` as a
one-shot-event mechanism silently misses deliveries if a second VM
tries to collect after the emission. Use `Channel` with single
consumer, or a persistent state-based representation.

## The invariant to enforce

If two ViewModels of the same class can coexist (they can, in Nav3),
then the only correct way to share state between them is a
`@Singleton` repository that both VMs receive by injection. The
repository owns a `StateFlow<T>`; both VMs expose it by reference via
their own observe-UseCase property; neither VM holds a
`MutableStateFlow<T>` for the same domain state.

Combined with the two kotlin-inject rules (abstract entry points +
parameter-based providers, see [`kotlin-inject.md`](kotlin-inject.md)),
this gives you:

```
Home's FooViewModel           Profile's FooViewModel
   │                              │
   └──── snoozeState (ref) ───────┘
            │
            ▼
   SnoozeRepository (@Singleton)
        ._snoozeState: MutableStateFlow<SnoozeState>
```

Both VMs point at the same flow. A write from either VM's `submit()`
command reaches both observers because the repo is shared.

## How to verify your scoping works

### Runtime assertion

In an instrumented test, resolve two VMs from separate nav entries
and check they're **different** instances (non-singleton) but their
shared state flow is the **same** reference:

```kotlin
@Test
fun vmPerNavEntryButSharedRepoFlow() {
    val c = (application as MyApp).component
    val vm1 = c.fooViewModel
    val vm2 = c.fooViewModel
    // VMs are per-access (not singleton)
    assertNotSame(vm1, vm2)
    // But the state they expose is the SAME flow (repo is singleton)
    assertSame(vm1.fooState, vm2.fooState)
}
```

The `assertNotSame` on VMs documents the intent (per-nav-entry
scoping). The `assertSame` on the state flow proves shared state is
actually shared.

### Log-based smoke

Put a `Logger.i { "FooViewModel init: $this" }` in the VM's init
block. Cold-start the app and navigate through your screens. Count
the init lines per screen visit. If a screen that should retain its
VM on navigate-away/back instead shows a new init on each re-entry,
the back-stack handling is re-creating the entry.

## Minimum viable defense

- [ ] A `ComponentGraphTest` (or equivalent) with `assertNotSame` on
      VMs and `assertSame` on their shared repo flows.
- [ ] Every domain state type has exactly one `MutableStateFlow`
      instance in the app — enforced by lint / grep: no
      `MutableStateFlow<DomainState>` in any `*ViewModel.kt`.
- [ ] A top-of-file KDoc on your DI component noting "ViewModels are
      per-nav-entry by design; shared state lives in `@Singleton`
      repositories."
- [ ] Init-log-per-instance during development. Count occurrences.
      More than you expect = something is re-creating.

## Common pitfalls

### Pitfall: `activityViewModel { ... }` doesn't help

Nav3's decorator-based scoping bypasses the `ViewModelStoreOwner`
chain in many cases. Falling back to `activityViewModel` may work for
some screens but not others, and couples VM lifetime to the Activity
— fighting Nav3 rather than working with it. Prefer making the
state live in a singleton.

### Pitfall: `rememberSaveable` on a VM reference

A VM is not `Saveable`. Don't try. Put state-to-survive-process-death
in a `SavedStateHandle` wired into the VM, or in DataStore.

### Pitfall: "Nav3 + Hilt's `hiltViewModel()` works like Nav2 did"

Not quite. `hiltViewModel()` uses the nearest `ViewModelStoreOwner`
— Nav3's decorator is that owner, scoping per-entry. Same multi-
instance reality applies even with Hilt. Not unique to kotlin-inject.

### Pitfall: assuming one VM in code review

`viewModel { ... }` at two call sites looks like it returns "the"
ViewModel. Without knowing the Nav3 scoping, reviewers approve
architecturally-incorrect designs that rely on singleton VM
semantics. Make the scoping visible: a comment at every
`viewModel { ... }` call site helps, or a project-wide convention
enforced by lint.

## When to break the rule

Some state is genuinely per-screen and per-visit: form input,
scroll position, a currently-open dialog. These should live in the
VM (or even the Composable via `remember`) and are correctly discarded
when the screen is popped. The "share state via singleton"
prescription is for domain state, not presentation state.

## Reference

- [androidx.navigation3 docs](https://developer.android.com/reference/androidx/navigation3/runtime/package-summary)
- [`kotlin-inject.md`](kotlin-inject.md) — how to make the singleton
  actually a singleton; prerequisite for this guide's invariant.
- [`repository-api-patterns.md`](repository-api-patterns.md) — how to
  shape the singleton repository the VMs share.
