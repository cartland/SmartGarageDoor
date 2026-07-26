---
category: reference
status: active
last_verified: 2026-07-25
---
# Data management and caching strategy

Status: draft for review. Companion to ADR-021 (state ownership), ADR-022 (observable types), ADR-019 (repository scope), ADR-034 (persisted status snapshots).

All paths are relative to `MobileGarage/` unless prefixed with `scripts/` or `.github/`. Line numbers were read at the time of writing; treat them as anchors, not contracts.

---

## 1. Where data can live

Eleven distinct storage locations exist in this architecture. Every one of them is in use today. The table is the summary; the notes below it carry the parts that do not fit in a cell.

| # | Location | Lifetime | Survives recomposition | Survives tab switch | Survives process death | Cleared on sign-out | Survives app upgrade | Cost to add one |
|---|---|---|---|---|---|---|---|---|
| 1 | `remember { }` in a Composable / SwiftUI `@State` | Composition | Yes | Home only | No | n/a | No | Free |
| 2 | `rememberSaveable { }` | Composition + SavedState | Yes | Home only | Yes (bundle) | n/a | No | Saver correctness |
| 3 | Root `remember` + `staticCompositionLocalOf` | Root composition | Yes | Yes | No | n/a | No | Free, invisible to DI/tests |
| 4 | Screen ViewModel field | NavBackStackEntry `ViewModelStore` / iOS `@StateObject` wrapper | Yes | Home only | No | No | No | Low, but see #4 note |
| 5 | UseCase `stateIn(applicationScope, …)` | Process | Yes | Yes | No | No | No | `@Singleton` in 3 components + identity tests |
| 6 | Singleton repository `MutableStateFlow` | Process | Yes | Yes | No | **No** (see P8) | No | Same, plus write arbitration |
| 7 | Singleton non-flow bookkeeping (`var`, mutex-guarded fields) | Process | Yes | Yes | No | No | No | Same, plus strand risk |
| 8 | `StatusSnapshotStore` (`status_cache.preferences_pb`) | Disk, per-key envelope | Yes | Yes | **Yes** | Yes, if key is registered | Yes, unless `schemaVersion` bumped | Highest: schema, TTL, skew, backup XMLs, sign-out registration, fake |
| 9 | `app_settings.preferences_pb` / `diagnostics_counters.preferences_pb` | Disk, permanent | Yes | Yes | **Yes** | **No** | Yes, silently reverts to default on key rename | Backup XMLs + `@Singleton` provider |
| 10 | Room (`AppDatabase`, v12) | Disk, permanent | Yes | Yes | **Yes** | **No** | Yes, if a migration is declared; otherwise every table is dropped | Schema JSON, migration, `RoomSchemaTest` |
| 11 | Platform stores (Firebase ID-token cache, FCM topic registry, iOS pasteboard) | Owned by the platform | Yes | Yes | Varies | Varies | Varies | Cannot be tested locally |

Notes that do not fit the grid:

**#1/#2 — the split is "is losing it on rotation a bug?".** Six Settings sheets use `rememberSaveable` (`androidApp/.../ui/settings/ProfileContent.kt:123-128`); `HomeContent.kt:179`'s info sheet uses plain `remember` and therefore closes on rotation. Both choices are defensible; only one is documented. `RememberSaveableGuardTask` fails the build on a `rememberSaveable` with no explicit saver, added after Nav3 `Screen` objects crashed the Bundle write.

**#3 — the sanctioned home for view-only memory.** `DoorAnimationMemory` (`domain/.../model/DoorAnimationMemory.kt`) is `remember`ed at the Compose root (`androidApp/.../ui/Main.kt:95`) and published via `LocalDoorAnimationMemory`; iOS mirrors it with a `@State` on `MainScreen` plus an environment key. It is deliberately not in the DI graph. Lifetime contract: survives tab switch and back-nav because the root is never disposed, resets on process death because a cold open *should* replay the door slide.

**#4 — nav topology decides whether a ViewModel is actually a cache.** `TabNavigation.navigateToTab` (`androidApp/.../ui/Main.kt:675-693`) keeps `Screen.Home` pinned at the bottom of the back stack and pops everything above it. So `DefaultHomeViewModel` survives every tab switch, and `DoorHistoryViewModel` / `ProfileViewModel` / `FunctionListViewModel` / `DiagnosticsViewModel` are destroyed and rebuilt on every visit. Any default-valued seed in those four is re-applied on every tab tap. On iOS the equivalent is `SharedViewModel<VM>` + `KmpViewModelStore`, cleared in `deinit`, which is why the strong-`self` capture bug in three wrappers matters (see anti-pattern A11).

**#5 vs #6 — a `stateIn` UseCase is a cache; a pass-through UseCase is not.** Exactly three `stateIn` call sites exist in `usecase/src/commonMain`: `ComputeButtonHealthDisplayUseCase.kt:73`, `ComputeEffectiveSnoozeStateUseCase.kt:74`, `ObserveWatchAppStatusUseCase.kt:70`. Everything else named `Observe*` returns the repository's flow by reference and holds nothing.

**#8 vs #9 vs #10 — three durability policies with different guarantees.** Only the status cache has a `schemaVersion`, a freshness envelope, a never-throws contract, decode-failure self-healing, a `ReplaceFileCorruptionHandler`, and sign-out clearing. `app_settings` has none of those and is the source of truth for its own data (which is why it deliberately has no corruption handler). Room has schema versioning and a migration gate but no TTL, no eviction on the `DoorEvent` table, and no sign-out clearing.

**#11 — what we deliberately do not own.** The Firebase ID token is cached by the Firebase SDK, never by us (ADR-027; `checkNoTokenInUseCase` + `checkRepositoryInterfaceNoToken` enforce it). FCM topic subscription state lives on Google's servers; the repositories keep only a `lastSubscribedTopic: String?` short-circuit that is deliberately not persisted, because re-subscribe on next manager start is idempotent. On iOS the copied auth token goes to `UIPasteboard` with a 120s `expirationDate` (`iosApp/Features/FunctionList/AuthTokenCopier.swift:36-48`), the substitute for Android's `EXTRA_IS_SENSITIVE`.

---

## 2. The decision rule

Answer in order. Stop at the first "yes".

1. **Can it be derived from data that is already cached?** Then derive it at the read site and store nothing. A derived value that needs to be shared or must not re-seed per screen entry is question 6, not a new cache.
2. **Is it an event rather than a state** (every occurrence matters, conflating two would lose information)? Return it from a `suspend` function, or use a `Channel` / `MutableSharedFlow(replay = 0)`. Never `StateFlow`, never a cache. This is ADR-013 and the reason `RemoteButtonRepository.pushButtonStatus: StateFlow<PushStatus>` is permanently banned.
3. **Does exactly one screen read it, and is losing it on navigation correct?** Compose `remember` (or `rememberSaveable` if losing it on rotation would be a bug). Do not put it in a ViewModel; do not put it in DI.
4. **Is it a fact about the world or the user, with a current value that more than one caller could observe?** One `@Singleton` repository owns a `MutableStateFlow`, exposes `StateFlow`, and everyone downstream passes it through by reference. No mirrors.
5. **Is it presentation phase layered over #4** (a `LoadingResult<T>` wrapper, an action overlay that auto-resets)? Screen ViewModel. Seed it from `upstream.value`, never from `Loading`/`null`.
6. **Is it a derivation over #4 that multiple screens read, or that must not re-seed per screen entry?** `@Singleton` UseCase with `stateIn(applicationScope, …)`. Choose `started` by upstream cost: `Eagerly` when the upstream is cheap in-memory flows, `WhileSubscribed(5_000)` when collecting costs battery, IPC, or network.
7. **Would a cold start show something visibly wrong without it?** Persist it, and pick the store by shape: a status verdict or per-user grant goes in `StatusSnapshotStore`; a user preference goes in `app_settings`; a list or log goes in Room. Never persist a secret (this is why `ServerConfig` is memory-only: it carries `remoteButtonPushKey`).
8. **Is it per-user?** Then, in the same change: register the key in `StatusCacheKeys.CLEARED_ON_SIGN_OUT`, set `accountEmail` on the envelope, and reset the in-memory copy on `AuthState.Unauthenticated`. All three, not one of them.

### Decision table

| The datum | Layer | Observable type | Seeded from | Persisted? |
|---|---|---|---|---|
| Sheet open, scroll offset, text field | Compose `remember` / `rememberSaveable` | `MutableState` | Literal default | No |
| Animation replay dedup, window size class, inset bridge | Root `remember` + CompositionLocal | plain object / value | Empty | No |
| `LoadingResult<T>` phase over repo data; action overlay (`SnoozeAction`, button state machine) | Screen ViewModel | `StateFlow` from private `MutableStateFlow` | `upstream.value` for mirrors; `Idle`/`Ready` for overlays | No |
| Auth state, door event, snooze, button health, server config, pagination cursor, FCM registration | `@Singleton` repository or ADR-015 manager | `StateFlow` | Constructor initial + always-on `init` collector, or disk hydration | Case by case |
| Room-backed lists (history, app log) | `@Singleton` repository over Room DAO | cold `Flow<List<T>>` | Room query | Yes (Room) |
| `ButtonHealthDisplay`, effective snooze, watch app status | `@Singleton` UseCase `stateIn(applicationScope)` | `StateFlow` | Synchronously computed from upstream `.value`, or an honest constant (`Hidden`, `Unknown`) | No |
| Verdicts worth showing before the first network round-trip | `StatusSnapshotStore` envelope | n/a (read at hydration) | Disk, TTL-gated, through the same guarded writer as live data | Yes |
| User preferences, FCM topic, sandbox topic | `app_settings` DataStore | `Setting<T>.flow` | Per-setting `default` | Yes |
| ID token, FCM subscription, clipboard | Platform | n/a | n/a | Platform-owned |

---

## 3. Principles

### P1. Exactly one owner per datum

**Rule.** Every piece of domain state has exactly one owner, and that owner is a `@Singleton` repository (or an ADR-015 manager for app-scoped operational status). Everyone else observes it. A `MutableStateFlow` in a ViewModel whose type argument is a repository-owned domain type is a bug, not a style choice.

**Why.** Two copies drift, and the drift is invisible until a user reports the wrong number. `android/164-168` was exactly this: a VM-local snooze mirror that diverged from the repository.

**Enforced today.** `checkViewModelStateFlow` (`buildSrc/.../architecture/ViewModelStateFlowCheckTask.kt`) bans `MutableStateFlow<T>` in `*ViewModel.kt` for four types: `SnoozeState`, `AuthState`, `FcmRegistrationStatus`, `WatchAppStatus`. `checkSingletonGuard` asserts `@Singleton` on ten named providers (`build.gradle.kts:88-106`). Reference-identity tests (`assertSame(fakeRepo.x, vm.x)`) in unit tests.

**Better.** The banned-type list is a denylist that only grows after an incident. Invert it: fail on any `MutableStateFlow<T>` in a `*ViewModel.kt` whose `T` is declared in `:domain`, with an allowlist for genuinely VM-owned wrappers (`LoadingResult<…>`, `*Action`, `*State` machines). Today `DoorEvent?` is mirrored, unseeded, by both `ProfileViewModel.kt:343` and `FunctionListViewModel.kt:150`, which the current denylist does not catch even though ADR-022's own text says it should.

### P2. Never seed a mirror with `Loading` or `null` when the upstream already has a value

**Rule.** A ViewModel mirror of upstream state is seeded `MutableStateFlow(LoadingResult.Complete(observeX().value))`. If the upstream type makes `.value` unreadable, that is a bug in the upstream's type, not a licence to seed a default.

**Why.** `MutableStateFlow` conflates by equality and the first frame renders before any collector delivers. A default seed produces a one-frame lie on every fresh `NavBackStackEntry`: door icon flashes UNKNOWN, history flashes empty, stale banner flashes absent, counters flash zero.

**Enforced today.** Nothing. `HomeViewModel.kt:211-215` and `DoorHistoryViewModel.kt:108-112` do it correctly with comments naming the flicker; `HomeViewModel.kt:247`, `DoorHistoryViewModel.kt:114`, `ProfileViewModel.kt:303-313`, `DiagnosticsViewModel.kt:95-131` and the three feature-access mirrors all still seed literals. The fix was applied per symptom, not as a rule.

**Better.** A lint over `*ViewModel.kt`: any `MutableStateFlow(<literal>)` whose value is later assigned inside `init { … collect … }` from a `StateFlow` upstream is a violation. Cheaper interim: make it a review checklist item and widen the two upstream types that block correct seeding (P3).

### P3. Pass through by reference; mirror only to add a layer

**Rule.** A UseCase returns `StateFlow` when it is a direct pass-through of a repository `StateFlow`, and `Flow` when it transforms. A ViewModel exposes the upstream reference directly unless it is adding something the upstream does not have (a `LoadingResult` phase, a combine with a second source). An app-scoped manager that owns a `MutableStateFlow` exposes `StateFlow`, never `Flow`.

**Why.** Narrowing `StateFlow` to `Flow` destroys `.value`, which forces every downstream consumer into P2's failure mode. `CheckInStalenessManager.kt:54` owns a `MutableStateFlow(false)` and publishes `Flow<Boolean>` at line 57; `FcmRegistrationManager.kt:48-51` owns the same shape and publishes `StateFlow`. The first forces two VM mirrors seeded `false`; the second forces none. Decide it: **`StateFlow`.** ADR-015's "managers expose Flow" wording predates ADR-022 and should be amended, not preserved.

**Enforced today.** Convention only.

**Better.** Amend ADR-015 to say `StateFlow` for always-has-a-value manager status, widen `CheckInStalenessManager.isCheckInStale`, and fix the two seeds in the same PR. Roughly a 5-line change.

### P4. `Eagerly` vs `WhileSubscribed` is keyed on upstream cost, not on taste

**Rule.** `stateIn(applicationScope, Eagerly, initial)` when the upstream is composed of in-memory `StateFlow`s, because running it forever costs nothing and the cache is never invalidated. `stateIn(applicationScope, WhileSubscribed(5_000), initial)` when collecting costs battery, IPC, or network, because the last value is still retained (`replayExpirationMillis` defaults to `Long.MAX_VALUE`) while the expensive upstream pauses. Never `WhileSubscribed` for a repository-owned `StateFlow`, where an emission landing in the dead window is lost forever.

**Why.** The three shapes in the tree are already chosen by this criterion, but the criterion is written down in exactly one KDoc (`usecase/.../ObserveWatchAppStatusUseCase.kt:44-62`) while two normative documents state a flat ban. CLAUDE.md's stated mechanism ("it re-emits the initial value after the timeout window") is factually wrong for the default `replayExpiration`, which makes the rule impossible to reason from. `ObserveWatchAppStatusUseCase` correctly uses `WhileSubscribed` because its upstream is a 15s Play Services poll; `ComputeButtonHealthDisplayUseCase` correctly uses `Eagerly` because its upstream is three in-memory flows.

**Enforced today.** `WatchAppStatus` is in the banned-mirror list specifically so a VM cannot defeat the cache; `ObserveWatchAppStatusUseCaseTest.retainsLastStatusForALaterCollector` pins the retention behaviour. The choice itself is unenforced.

**Better.** Rewrite the CLAUDE.md and ADR-022 sentences to state the cost criterion and the three distinct objects the word `WhileSubscribed` applies to (repository state: banned; combine UseCase over cheap flows: pointless; combine UseCase over an expensive poll: correct). No new lint needed; this is a doc defect.

### P5. Mutating state on a process-lifetime object runs on `externalScope`, and in-flight flags reset in `finally`

**Rule.** A repository method that writes singleton state around a suspending call wraps the work in `externalScope.async { … }.await()` (ADR-019 Rule 1) and resets any in-flight flag in a `finally`. State comes from the authoritative POST body, never a follow-up GET.

**Why.** The caller's scope is a screen. `NetworkDoorRepository.fetchOlderDoorEvents` (`data/.../repository/NetworkDoorRepository.kt:153-191`) sets `isLoadingMore = true` at line 165, calls the network, and resets only inside the three result branches. It runs on `viewModelScope.launch(dispatchers.io)` (`DoorHistoryViewModel.kt:166`), and `KtorNetworkDoorDataSource` rethrows `CancellationException`. Leaving History mid-page strands `isLoadingMore = true` in a process-lifetime singleton; the guard at line 156 then short-circuits every subsequent load-more, and History shows a stuck footer spinner until a successful pull-to-refresh resets the state. `NetworkSnoozeRepository` (`:138`, `:141-146`, `:164-167`) and `NetworkButtonHealthRepository` (`:107-117`) already implement the correct shape; the door repository does not even retain its `externalScope` parameter as a property.

**Enforced today.** Convention. `checkNoRawDispatchers` does not scan `data/`.

**Better.** Fix the divergence first (retain `externalScope`, wrap, add `finally`). Then a lint over `data/**/repository/*.kt`: a `suspend fun` that assigns to a `_*.value` both before and after a suspension point must be wrapped in `externalScope`.

### P6. What earns disk

**Rule.** Persist only when a cold start would otherwise show something visibly wrong, and only through one of the three sanctioned stores. Then answer, in the same PR: what invalidates it, what its schema version is, whether it is per-user, and whether it is excluded from platform backup. A secret never earns disk.

**Why.** The three current policies differ by two orders of magnitude in rigour and there is no stated criterion. `ServerConfig` is correctly memory-only because it carries `remoteButtonPushKey` and the payoff was invisible (ADR-034 §D5, settled 2026-07-18). The `DoorEvent` table has no cap, no age eviction, no TTL, no sign-out clearing, and grows with every appended pagination page, while the `AppEvent` table in the same database is capped at 1000 rows per key and pruned at startup. The most personal dataset in the app has the weakest policy, by default rather than by decision.

**Enforced today.** `checkDataStoreSingleton` (double-construct crash), `checkBackupRulesExcludes` (Android XMLs), `RoomSchemaTest` (migration coverage), the ADR-034 envelope contract.

**Better.** Decide the `DoorEvent` policy explicitly: cap it (a `LIMIT`-based prune mirroring `AppLoggerLimits.DEFAULT_PER_KEY_LIMIT`) or state in the DAO KDoc why unbounded is correct. Extend the ADR-034 new-persisted-value checklist to cover Room tables, not just `StatusSnapshotStore` keys.

### P7. TTL gates the fetch; it never gates the display

**Rule.** Two independent timestamps. `fetchedAt` decides whether to skip a network call. `confirmedAt` decides whether a persisted value is still worth showing. A value past its display TTL is hidden; a value inside it is shown immediately and revalidated in the background. A timestamp more than 60s in the future reads as maximally stale, everywhere, via `StatusSnapshot.ageSeconds`.

**Why.** Conflating them produces either a spinner over data we already have or a stale value nobody re-checks. The 60s skew rule exists so a device clock correction cannot pin a value as permanently fresh.

**Enforced today.** Unit tests per repository. `StatusSnapshot.kt:58-68` owns the skew constant.

**Better.** Route every freshness comparison through `StatusSnapshot`'s helpers. `NetworkSnoozeRepository.fetchIfStale` (`:148-158`) currently hand-computes `age = currentTimeSeconds() - lastFetchedAtSeconds` and applies a stricter policy (`age in 0..FETCH_TTL_SECONDS`, so any future skew is stale) than the shared 60s tolerance. Both policies are defensible; having two on the same envelope timestamps is not. Pick the shared one.

### P8. Sign-out clears memory and disk, or it clears nothing

**Rule.** A datum registered in `StatusCacheKeys.CLEARED_ON_SIGN_OUT` must also have its in-memory copy reset on `AuthState.Unauthenticated`, along with any freshness bookkeeping that would let the next session skip revalidation.

**Why.** Today the clear is disk-only. `SignOutCacheClearManager.kt:62-71` calls exactly one method, `userScopedCache.clearUserScopedEntries()`, which terminates at `storage.remove(...)`. `NetworkButtonHealthRepository` and `NetworkSnoozeRepository` contain zero references to `authState` or `Unauthenticated`; only `CachedFeatureAllowlistRepository.kt:96-104` also nulls memory. So after sign-out then sign-in in the same process, the previous session's button-health verdict renders instantly from a singleton the clear never touched, and snooze's surviving `lastFetchedAtSeconds` suppresses revalidation for up to the 5-minute fetch TTL. The registry KDoc frames membership as the privacy boundary; membership governs only the disk tier.

**Enforced today.** Nothing checks the memory half.

**Better.** Give `UserScopedCache` a second responsibility: a registry of `suspend () -> Unit` in-memory resets that state-owning repositories register at construction, invoked by the same manager in the same transition. That keeps one call site and makes "did you clear memory too?" structurally answerable. A `ComponentGraphTest` assertion that every repository holding a `CLEARED_ON_SIGN_OUT` key registered a reset closes the loop.

### P9. Both platforms observe the same auth timeline

**Rule.** The auth flow emits `Unknown` until the platform listener produces a real value. No layer synthesizes an `Unauthenticated` from the absence of information.

**Why.** `IosAuthUserStateHolder.kt:42` is `MutableStateFlow<AuthUserInfo?>(null)`, created as a stored property of the Swift bridge before `init` registers Firebase's async listener. `FirebaseAuthRepository` maps `null` to `Unauthenticated`. `AppDelegate` builds the bridge and resolves `appStartup.run()` on the same synchronous main-thread turn, while the shared collectors run on `Dispatchers.IO`, so the pre-seeded `null` usually wins the race and `SignOutCacheClearManager` fires on a signed-in cold start, wiping the exact snapshots ADR-034 exists to display. Android's bridge is a cold `callbackFlow` with no pre-seed. The fix is one line: seed the holder from `FirebaseAuthBridge.getCurrentUser()` in `init`, a method that currently has zero call sites anywhere in the tree.

**Enforced today.** Nothing. The failure is silent (the app shows "Checking…" more often).

**Better.** Seed from `getCurrentUser()`, and add an `iosSimulatorArm64Test` asserting the repository never emits `Unauthenticated` before the listener has delivered.

### P10. Backup exclusion is a cross-platform policy or it is not a policy

**Rule.** Whatever is excluded from cloud backup on Android is excluded on iOS, by the same PR.

**Why.** `checkBackupRulesExcludes` parses only the two Android XMLs, so it fails open for iOS by construction. `DataStoreFactory.ios.kt:64` and `DatabaseFactory.ios.kt:32` both resolve under `NSDocumentDirectory`, which iCloud backs up by default, and a repo-wide grep for `NSURLIsExcludedFromBackupKey` returns nothing. Door-activity history and the status cache are deliberately kept out of Google Drive and ride into iCloud unchanged. The check is also not a required CI gate: it runs only from `scripts/validate.sh:93`.

**Enforced today.** Android only, locally only.

**Better.** Set `NSURLIsExcludedFromBackupKey` on `garage.db` and the three `.preferences_pb` files at creation, and extend `BackupRulesExcludeCheckTask` to assert that every filename it finds also appears in the iOS exclusion call site. Same task, one more input.

### P11. DI parity is a three-component problem, not two

**Rule.** A constructor change to any class in a shared KMP module updates every DI component that wires it: `androidApp/.../di/AppComponent.kt` (`@Singleton`), `iosFramework/.../NativeComponent.kt` (`@SharedSingleton`), and `wearApp/.../di/WearComponent.kt` (`@WearSingleton`, 17 scoped providers wiring `FirebaseAuthRepository`, `CachedServerConfigRepository`, `NetworkDoorRepository`, `NetworkRemoteButtonRepository`). Every scoped state owner needs a matching `abstract val` entry point and an `assertSame` identity test in that component's test.

**Why.** `@Singleton` is silent when misused: without an entry point the annotation generates nothing (`android/170`). `validate.sh` compiles Android targets only, so a stale iOS or Wear provider passes every required check; #871 broke iOS `main` this way. CLAUDE.md still says "two DI components", which undercounts.

**Enforced today.** `checkSingletonCaching` parses the generated Android component; `checkDataStoreSingleton` scans `androidApp/src/main/java` only; `ComponentGraphTest` lives in `androidTest/` so `validate.sh` compiles it but never runs it; `NativeComponentTest` runs only in non-required iOS CI. Per-symbol coverage diverges between the two test files in both directions.

**Better.** Move the identity assertions to a source set that runs pre-submit (a JVM unit test over a headless component, or at minimum promote `:iosFramework:iosSimulatorArm64Test` to a required check). Then make the three assertion lists mirror each other, generated from the `abstract val` declarations rather than hand-written.

### P12. Cache-shaped enforcement should not be a hardcoded name list

**Rule.** A check that protects "state-owning bindings" must derive its subject list from the code, not from a literal list of provider names.

**Why.** `checkSingletonGuard` names ten providers. Every state owner added since is absent: `provideButtonHealthRepository`, `provideFeatureAllowlistRepository`, `provideTestNotificationRepository`, `provideStatusSnapshotStore`, `provideStatusCacheStorage`, `provideSnoozeDoorEventBridge`, and both `Eagerly` combine UseCases. Dropping `@Singleton` from any of them passes the check silently, and `checkSingletonCaching` also stays silent because an unannotated provider is not in the enumeration it parses. Two documents describe this check as structural (`DECISIONS.md:1257`, `ARCHITECTURE.md:182`); it is not.

**Enforced today.** The ten names.

**Better.** Konsist is already a `testImplementation` dep and parses typed PSI: assert that every class in `:data` implementing a `*Repository` interface and declaring a `MutableStateFlow` property has a `@Singleton`/`@SharedSingleton`/`@WearSingleton` provider in each component that wires it. Until then, add the seven missing names and fix the two doc sentences.

### P13. One clock

**Rule.** Everything that reads wall time takes the injected `AppClock`.

**Why.** `NetworkButtonHealthRepository` takes `AppClock` (`:86`); `NetworkSnoozeRepository` takes `currentTimeSeconds: () -> Long` (`:87`) and the DI wires an equivalent inline lambda per platform. Substituting `AppClock` in a test or a future time-travel diagnostic silently does not affect snooze expiry or its fetch TTL.

**Enforced today.** Nothing.

**Better.** Delete the lambda parameter, inject `AppClock`. Two constructor edits plus three component edits (P11).

### P14. Every state write is logged with flow, value, and source

**Rule.** Each `_state.value = …` in a repository emits a kermit line naming the flow, the new value, and where it came from: `"snoozeState <- $newState (source=$source)"`.

**Why.** Race and lifecycle bugs in this layer are rare, non-reproducible, and expensive to instrument after the fact. ADR-021 Rule 9 chose observability over exhaustive prevention deliberately. Compliance today is genuinely good across all seven stateful repositories.

**Enforced today.** Code review. ADR-022 says "upgrade to lint after the migration settles"; the migration has settled and the lint does not exist.

**Better.** A `buildSrc` check over `data/**/repository/*.kt`: any line assigning to a `_*.value` must be within three lines of a `Logger.` call. Crude, but it locks in a property that is currently at 100% and would otherwise decay.

---

## 4. Anti-patterns

Each of these happened in this repository.

**A1. ViewModel mirrors repository state.** `android/164-168`: a VM-local snooze `MutableStateFlow` diverged from the repository, so the Settings row and the snooze sheet disagreed. Fix: ADR-021/ADR-022 pass-through by reference; `checkViewModelStateFlow` bans the type.

**A2. `@Singleton` silently ignored.** `android/170`: a `@Singleton` provider reachable only through a concrete `val x: T @Provides get()` generated no override, so every caller got its own repository and its own timeline. Fix (`android/173`): `abstract val` entry points, `checkSingletonCaching` parsing the generated component, `assertSame` identity tests.

**A3. `Loading` latches forever.** `2.4.4` Home regression (PR #518): a fetch returned a value equal to the cached one, `MutableStateFlow` conflated it, no observer fired, and the screen stayed on Loading. Fix: ADR-023, every `AppResult.Success` branch writes `Complete(result.data)` explicitly. Still has no static check.

**A4. Mirror seeded `Loading(null)`.** PRs #738/#739: the door icon flashed UNKNOWN and the history list flashed empty on every fresh `NavBackStackEntry`. Fix: seed from `observeX().value`. Applied to two mirrors; a dozen others still seed literals (P2).

**A5. Cold-start fetch in ViewModel `init`.** Every tab tap re-fetched. Fix (PR #731): `InitialDoorFetchManager`, a `@Singleton` with a `job != null` one-shot guard so `MainActivity.onCreate` re-firing on rotation cannot re-trigger it, and `fetchOnInit = false` on both VMs.

**A6. `stateIn` wrap-then-rewrap.** PR #283: a UseCase `stateIn`'d inside a VM that exposed a `StateFlow` derived from the same upstream, hiding a timing bug behind two layers of conflation. Fix: pass the reference through (ADR-022).

**A7. Auth-state feedback loop.** PR #672: `combine(authRepository.authState, …)` re-fired on every token refresh, because `refreshIdToken()` writes a new `Authenticated` instance. Fix: project to `it is AuthState.Authenticated` + `distinctUntilChanged` before combining; `checkAuthStateProjection` enforces it, with an inline `authState-passthrough-ok:` escape for pure derivations.

**A8. `WhileSubscribed` on repository-owned state.** Subscriber-count thrash from navigation and backgrounding dropped emissions landing in the dead window. Fix: always-on collector launched in the repository's `init` on `externalScope` (ADR-022).

**A9. In-flight flag stranded on a cancelled caller scope.** Live today: `fetchOlderDoorEvents` (P5). History's load-more dies until the next successful pull-to-refresh if the user leaves the screen mid-page.

**A10. Sign-out clears disk but not memory.** Live today: the previous session's button-health verdict survives sign-out in the singleton (P8), and snooze's fetch TTL suppresses revalidation for the new session.

**A11. iOS wrapper retains `self` inside a never-ending `for await`.** `SettingsViewModelWrapper.swift:69-72` documents the rule; `HistoryViewModelWrapper`, `FunctionListViewModelWrapper` and the `clearInFlight` task in `DiagnosticsViewModelWrapper` do `guard let self else { return }` and then hold `self` strongly for the life of a loop that never terminates. `deinit` never runs, so `KmpViewModelStore.clear()` never cancels the Kotlin `viewModelScope` and the dismissed screen's ViewModel keeps collecting singleton flows for the rest of the process. Related: `ios/7`'s launch crash came from the same family (a discarded `StateObject` wrapper's Task plus `self!`), which is why `self!` is banned and `scripts/check-ios-self-force-unwrap.sh` runs first in `validate-ios.sh`.

**A12. New DataStore file with no backup exclusion.** Security audit finding M6: a new `.preferences_pb` file fails open into Google Drive Auto Backup. Fix: `BackupRulesExcludeCheckTask` parses the whole `data-local/src` tree for filename constants and inline literals and requires each in both XMLs, failing loudly if zero filenames parse. Still Android-only (P10).

**A13. Presentation state pushed into DI.** Door animation replay memory was a candidate for a `@Singleton`. Rejected: it lives as a root `remember` + CompositionLocal so it resets on process death, which is exactly when a cold open should replay the slide (ADR-025 amendment).

**A14. Room destructive fallback wiping unrelated tables.** PR #660, caught on review: `fallbackToDestructiveMigration(false)` drops *every* Room-managed table on any undeclared version mismatch, so an index-only change to `appEvent` would also wipe the user's door history. Fix: declare an `AutoMigration` or `Migration` for every consecutive version pair; `RoomSchemaTest.everyVersionPairHasDeclaredMigration` enforces it after stripping comments.

**A15. Unkeyed `remember` over a parameter.** `androidApp/.../ui/theme/DoorStatusTheme.kt:31` does `remember { doorStatusColorScheme }`, latching the palette to the first value. Masked today because `MainActivity` declares no `configChanges` so a uiMode flip recreates the Activity, but any in-app theme toggle would surface it. Fix: `remember(doorStatusColorScheme)`, or no `remember`.

**A16. Stateful repository with no hydration.** `DefaultTestNotificationRepository` seeds `MutableStateFlow(TestNotificationSandboxState())` and has no `init` block; the flow only becomes truthful after the first mutating call runs `publishState()` (`:145-153`). Its own KDoc calls the DataStore key the authoritative record while the exposed flow contradicts it. On a fresh process, an already-subscribed user reads "not subscribed" until something happens to call `getTopic()`.

---

## 5. Open tensions to resolve

Verification status is stated per item. "Adversarially verified" means a second pass tried to refute the claim and reported a verdict.

| ID | Tension | Status | Recommendation | Cost |
|---|---|---|---|---|
| T1 | `CheckInStalenessManager.isCheckInStale` is `Flow`, forcing two default-seeded VM mirrors | Verified, PARTIAL | Widen to `StateFlow`; seed both mirrors from `.value`. Amend ADR-015's "expose Flow" wording, which is the actual source of the conflict. The recurring defect is History-only (Home's entry survives tab switches); Home's seed is a cold-start race | S, ~5 lines + 1 doc edit |
| T2 | `fetchOlderDoorEvents` mutates singleton state on the caller's scope with no `finally` | Verified, PARTIAL | Retain `externalScope`, wrap in `async{}.await()`, reset in `finally`. Impact is a stuck History footer recoverable by pull-to-refresh, not a permanent process-lifetime break | S, ~10 lines + a cancellation test |
| T3 | Sign-out clears disk but not memory for button health and snooze | **Verified, CONFIRMED** | Add in-memory reset registration to `UserScopedCache` (P8). Also reset `lastFetchedAtSeconds` | M, ~40 lines across 3 files + tests |
| T4 | iOS pre-seeds auth with `null`, emitting a spurious `Unauthenticated` on cold start | Verified, PARTIAL | Seed `IosAuthUserStateHolder` from `getCurrentUser()` in the bridge `init`. It is a race, not a certainty, and the most likely victim is the allowlist snapshot via `SignOutCacheClearManager` rather than the allowlist repo's own branch | S, 1 line + 1 test |
| T5 | Android excludes the data from cloud backup; iOS does not | Verified, PARTIAL | Set `NSURLIsExcludedFromBackupKey` on all four iOS files; extend the check to assert it. Note the check is a local `validate.sh` gate, not CI | M, ~30 lines + task edit |
| T6 | Two clock abstractions; snooze applies a different skew policy than the shared envelope rule | Unverified | Inject `AppClock` everywhere; route freshness through `StatusSnapshot` helpers | S, ~15 lines across 3 components |
| T7 | Singleton identity tests never run pre-submit; per-symbol coverage diverges between the two components | Unverified | Promote `:iosFramework:iosSimulatorArm64Test` to a required check, or move the assertions to a JVM unit test. Then mirror the two lists | M, CI topology change |
| T8 | `checkSingletonGuard` is a 10-name list missing every state owner added since; two docs describe it as structural | Unverified | Add the seven missing names now; replace with a Konsist structural rule later; fix `DECISIONS.md:1257` and `ARCHITECTURE.md:182` | S now, M for the structural version |
| T9 | The `stateIn(viewModelScope)` ban is unenforceable (positional-only regex) and the banned-mirror list omits `DoorEvent?` | Unverified | Fix the regex to match the named-arg form, and simultaneously carve out the sanctioned pattern (`Eagerly` + synchronously computed `initialValue`), which `HomeViewModel`'s three uses satisfy. Ban `WhileSubscribed` and literal-seeded `initialValue` in ViewModels instead. Add `DoorEvent?` and seed the two mirrors | M, regex + rule text + 2 seeds |
| T10 | The real `Eagerly`/`WhileSubscribed` criterion lives in one KDoc; two rule sources state a flat ban with a wrong mechanism | Unverified | Rewrite both rule sources per P4. No code change | S, doc only |
| T11 | `DefaultTestNotificationRepository` has no hydration; its flow contradicts its own authoritative record | Unverified | Add an `init` block that publishes from DataStore, matching the other six stateful repositories | S, ~10 lines |
| T12 | Durability policy varies with no stated selection rule; `DoorEvent` is unbounded and never cleared | Unverified | Decide and document the `DoorEvent` policy (cap or justified unbounded); extend the ADR-034 checklist to Room tables | M, includes a migration if capped |
| T13 | Three hand-mirrored DI components; the rule and the checks account for two | Unverified | Update CLAUDE.md and the ADR-034 checklist to say three; point `checkSingletonGuard`/`checkDataStoreSingleton` at `wearApp` too | S, doc + 2 task inputs |

Sequencing suggestion: T3, T2, T4 first (live user-visible or privacy-relevant defects with small fixes), then T13 and T8 (cheap enforcement gap closures), then T5 and T7 (platform parity), then the doc-only items T10 and T13, then T12 as a deliberate decision rather than an inherited default.

---

## 6. Proposed ADR

```markdown
## ADR-035: Data placement and cache lifetime — one owner, seeded pass-through, cost-keyed sharing, earned persistence

**Status**: Proposed

**Date**: 2026-07-25

**Context**

The app now has eleven distinct places a value can live: Compose `remember`,
`rememberSaveable`, a root `remember` published via CompositionLocal, a screen
ViewModel, a `stateIn` UseCase, singleton repository memory, singleton non-flow
bookkeeping, the ADR-034 status-snapshot cache, two DataStore preference files,
Room, and platform stores. Placement rules exist but are spread across ADR-013,
ADR-015, ADR-019, ADR-021, ADR-022, ADR-023, ADR-025, ADR-028 and ADR-034, plus
CLAUDE.md. Several of those statements now contradict each other or the code:
ADR-022 sanctions `stateIn(viewModelScope, WhileSubscribed(5_000))` that ADR-017
and `checkViewModelStateFlow` ban; ADR-015 says managers expose `Flow` while
ADR-022 requires `StateFlow` for state-y data; CLAUDE.md states a flat
`WhileSubscribed` ban whose stated mechanism is factually wrong for the default
`replayExpirationMillis`.

The practical cost is not theoretical. Seeding a mirror from a literal instead
of `upstream.value` was fixed twice by symptom (PRs #738, #739) and remains in
place in a dozen other mirrors. Sign-out clears the disk tier and leaves the
in-memory tier intact for two of three user-scoped values. `fetchOlderDoorEvents`
mutates a process-lifetime flag on a screen's coroutine scope with no `finally`.
iOS pre-seeds the auth flow with a value no listener produced. There is no
stated criterion for which of the three durability policies a new persisted
value should inherit.

**Decision**

Adopt a single ordered placement procedure and eight placement rules.

Placement procedure, evaluated in order, stopping at the first "yes":
1. Derivable from an existing cache? Derive; store nothing.
2. An event rather than a state? `suspend` return, `Channel`, or
   `MutableSharedFlow(replay = 0)`. Never `StateFlow`.
3. Read by one screen and correctly lost on navigation? Compose `remember`
   (or `rememberSaveable` if losing it on rotation is a bug).
4. A fact with a current value that more than one caller may observe? One
   `@Singleton` repository owns a `MutableStateFlow`; downstream passes it
   through by reference.
5. Presentation phase layered over (4)? Screen ViewModel, seeded from
   `upstream.value`.
6. A derivation over (4) read by multiple screens or that must not re-seed per
   entry? `@Singleton` UseCase with `stateIn(applicationScope, …)`.
7. Would a cold start be visibly wrong without it? Persist, choosing the store
   by shape. Never persist a secret.
8. Per-user? Register the key in `CLEARED_ON_SIGN_OUT`, set `accountEmail` on
   the envelope, and reset the in-memory copy on `Unauthenticated`. All three.

Rules:
- **R1 Single owner.** One `@Singleton` owner per datum; observers never mirror
  a repository-owned domain type.
- **R2 Seed from upstream.** A ViewModel mirror of upstream state is seeded from
  `upstream.value`. A `Loading`/`null`/literal seed is a defect.
- **R3 `StateFlow` for state.** Any always-has-a-value state — including
  ADR-015 manager status — is exposed as `StateFlow`, never narrowed to `Flow`.
  This supersedes ADR-015's "expose Flow" wording.
- **R4 Sharing keyed on upstream cost.** `Eagerly` when the upstream is cheap
  in-memory flows; `WhileSubscribed(5_000)` when collecting costs battery, IPC,
  or network (the last value is retained; only the poll pauses). Never
  `WhileSubscribed` for a repository-owned `StateFlow`.
- **R5 Mutations own their scope.** A repository method that writes singleton
  state around a suspending call runs on `externalScope` and resets in-flight
  flags in `finally`.
- **R6 Two clocks, two jobs.** `fetchedAt` gates the fetch; `confirmedAt` gates
  the display. Freshness comparisons go through `StatusSnapshot`'s helpers so
  the 60s skew rule is applied once. Wall time comes from the injected
  `AppClock`.
- **R7 Sign-out is total.** Memory and disk, or neither.
- **R8 Three components.** `AppComponent`, `NativeComponent` and `WearComponent`
  are updated together; every scoped state owner has an `abstract val` entry
  point and an identity assertion in each component's test.

Amend, in the same change: ADR-015 (R3), ADR-022's `stateIn(viewModelScope)`
sentence and its overstated enforcement paragraph, CLAUDE.md's
`WhileSubscribed` sentence and its "two DI components" line, and
`ARCHITECTURE.md:182`'s description of `checkSingletonGuard`.

**Consequences**

Positive. A new value has one place to go and the reasoning is mechanical. The
three flicker fixes already in the tree (seed-from-upstream, hoist-to-singleton,
cache-a-cold-poll) become a stated menu rather than three independent
precedents. Sign-out gains a single call site that covers both tiers. The
`Eagerly`/`WhileSubscribed` decision stops depending on one file's KDoc.

Negative. R3 and R7 require touching repositories that are currently correct in
isolation. R8 makes every shared-constructor change a three-file edit, which is
already true in practice but is now stated, so the friction is visible.

Enforcement gaps this ADR does not close, listed so they are not mistaken for
covered: R2 and R5 have no static check; the identity tests that back R8 do not
run pre-submit on either platform; `checkSingletonGuard` remains a name list;
`checkBackupRulesExcludes` remains Android-only and runs only from
`scripts/validate.sh`. Each has a proposed mechanism in the strategy document
and should be scheduled independently rather than blocking adoption.

**Alternatives considered**

*Leave the rules distributed across ADRs.* Rejected: the contradictions are the
problem, and they are only visible when the statements are placed side by side.

*Enforce first, document after.* Rejected: two of the enforcement gaps (R2, R5)
need a rule text before a lint can be written, and three of the corrections are
pure doc defects with no code change.
```
