# Migration Plan — ADR-021 + ADR-022

Rollout plan for the state-ownership principles (ADR-021) and the
repository-owns-`StateFlow` shape (ADR-022). Motivated by the
android/164-168 snooze-state propagation bug (`VIEWMODEL_SCOPING_ISSUE.md`).

## Goals

1. Move every state-y data type to the ADR-022 shape: `@Singleton`
   repository exposes `StateFlow<T>` via an always-on collector; UseCase
   passes through by reference; VM exposes by reference, no mirror.
2. Remove PR #354's direct-write workaround (`_snoozeState.value =
   result.data`) once the snooze path is migrated.
3. Add state-write logging (ADR-021 Rule 9) as each repository is touched.
4. Land the enforcement (lint + convention tests) so backsliding is
   caught by CI, not review.
5. Every PR leaves `main` in a working, releasable state.

## Phase 1 — State-y data that exists today

Scope `b` from our earlier discussion: the five state-y data types
already owned in some form by a repository. Phase 2 (scope `c`) audits
the remaining ViewModels and any hidden-domain state later.

### PR sequence (8 PRs)

#### PR 1: Integration test for snooze under the target shape

**Scope:** Add a `SnoozeMultiSubscriberIntegrationTest` that wires the
real `NetworkSnoozeRepository` with fake data sources + multiple
subscribers (simulating the three-VM production scenario). Asserts
every subscriber sees every emission, with no reliance on PR #354's
direct write. Extend `ViewModelStateFlowCheckTask` to *allow but not
require* `StateFlow` passthrough from UseCases (no enforcement yet).

**Risk:** Low. Test-only; can be deleted if flaky.

**Tests added:** `SnoozeMultiSubscriberIntegrationTest`.

**Rollback:** Trivial revert.

---

#### PR 2: `SnoozeRepository` → `val snoozeState: StateFlow<SnoozeState>`

**Scope:**
- Change `SnoozeRepository` interface: drop `observeSnoozeState(): Flow`,
  add `val snoozeState: StateFlow<SnoozeState>`.
- `NetworkSnoozeRepository`: already uses the always-on-collector pattern
  after ADR-019. Expose it directly; ensure writes are logged per Rule 9.
- `ObserveSnoozeStateUseCase`: passthrough returns `StateFlow`.
- `DefaultRemoteButtonViewModel`: `val snoozeState: StateFlow<SnoozeState>
  = observeSnoozeStateUseCase()`. **Delete** `_snoozeState` field, the
  `init { collect }` observer, and the PR #354 direct write in
  `snoozeOpenDoorsNotifications`.
- `FakeSnoozeRepository`: match the new interface.
- Add reference-identity test:
  ```kotlin
  assertSame(fake.snoozeState, vm.snoozeState,
      "VM must expose repo's StateFlow by reference (ADR-022)")
  ```

**Risk:** Medium. Removes the workaround that's been holding the snooze
UI working. Mitigated by PR 1's integration test + existing
`SnoozeStateInstrumentedPropagationTest`.

**Tests added/changed:**
- Update existing snooze tests for new shape.
- PR 1's integration test now exercises the real fix.

**Rollback:** Clean revert (single feature, single PR).

---

#### PR 3: Delete dead `Main.kt:122` `buttonViewModel`

**Scope:** Remove `val buttonViewModel = viewModel { ... }` at
`Main.kt:122`. Add a comment at each remaining VM provider in
`AppComponent.kt` noting "non-`@Singleton` is intentional; per-nav-entry
by design (ADR-021 Rule 4)." Optional cleanup of similar dead references
if any surface during audit.

**Risk:** Low. Dead code removal; behavior unchanged after PR 2.

**Tests:** None added.

**Rollback:** Trivial.

**Note:** Independent of PRs 4-7 — can land anywhere after PR 2 or even
before.

---

#### PR 4: `AuthRepository` → `val authState: StateFlow<AuthState>`

**Scope:**
- Change `AuthRepository` interface: drop
  `observeAuthState(): Flow<AuthState>` and `getAuthState(): suspend`,
  add `val authState: StateFlow<AuthState>`.
- `FirebaseAuthRepository`: already uses the Firebase listener on
  `externalScope` (ADR-018); expose the `MutableStateFlow<AuthState>`
  directly, add state-write logging.
- `ObserveAuthStateUseCase`: passthrough.
- `DefaultAuthViewModel`: drop any local `_authState`; expose repo's
  `StateFlow` directly. Keep `AuthAction`-style presentation state
  (sign-in command flight, "signing in…" spinner) as VM-local per
  Rule 3.
- Audit `activityViewModel(...)` usage for auth (SignInClient instance
  state). Ensure single-owner scoping is explicit per Rule 5.
- `FakeAuthRepository`: match the new interface.

**Risk:** Medium-high. Auth is user-sensitive.

**Tests added/changed:**
- `AuthMultiSubscriberIntegrationTest`.
- `assertSame(fake.authState, vm.authState)` in VM test.
- Verify sign-in / sign-out transitions log correctly per Rule 9.

**Rollback:** Clean revert.

---

#### PR 5: `DoorRepository.currentDoorEvent` → `StateFlow`

**Scope:**
- Add `val currentDoorEvent: StateFlow<DoorEvent?>` to
  `DoorRepository` interface, backed by an always-on collector in
  `NetworkDoorRepository.init` that consumes the Room Flow and writes
  `_currentDoorEvent.value`. **Do not** use
  `stateIn(externalScope, WhileSubscribed(5_000))` — ADR-022 bans it.
- Keep `observeRecentEvents(): Flow<List<DoorEvent>>` as cold (list-y
  per ADR-022).
- Update `ObserveDoorEventsUseCase` and any consumers.
- `DefaultDoorViewModel`: expose repo's `currentDoorEvent` by
  reference. `isCheckInStale` is a derivation of the state — keep it in
  the VM via `map { }.stateIn(viewModelScope, ...)` (acceptable because
  the transform is genuinely new VM-local state, not a mirror).
- `ButtonStateMachine.doorPosition` source updates.
- `FakeDoorRepository`: match new interface.

**Risk:** Medium. `DoorRepository` is central; the check-in-stale
derivation needs care.

**Tests added:**
- `DoorMultiSubscriberIntegrationTest`.
- Verify always-on collector survives rotation/backgrounding without
  restarting Room queries.

**Rollback:** Clean revert.

---

#### PR 6: `ServerConfigRepository` → `val serverConfig: StateFlow<ServerConfig?>`

**Scope:**
- `CachedServerConfigRepository` already caches; wrap cache in a
  `MutableStateFlow<ServerConfig?>`, expose as `StateFlow`. Fetch on
  `externalScope` (already the case).
- Replace `getServerConfigCached()` callers: if they need one-shot
  synchronous read, use `.value`. Keep `fetchServerConfig()` as suspend
  command.
- Update `FakeNetworkConfigDataSource` / any fake repo shape.

**Risk:** Low. Few consumers; mostly read at startup.

**Tests:** Update existing tests; add one `assertSame` for any VM that
might expose it (currently none do directly).

**Rollback:** Clean revert.

---

#### PR 7: `DoorFcmRepository` → `val fcmState: StateFlow<DoorFcmState>`

**Scope:**
- Expose `StateFlow<DoorFcmState>` in `DoorFcmRepository` / manager
  (`FcmRegistrationManager`). Existing methods become commands that
  update the state.
- Update consumers and fakes.
- Log state transitions per Rule 9.

**Risk:** Low. FCM registration is fire-and-forget; small consumer
footprint.

**Tests added:**
- One integration test verifying register → state transition.

**Rollback:** Clean revert.

---

#### PR 8: Lint enforcement + ADR cleanup

**Scope:**
- Extend `ViewModelStateFlowCheckTask` to ban `MutableStateFlow<T>` in
  a ViewModel when `T` matches a domain-state-type allowlist
  (`SnoozeState`, `AuthState`, `DoorEvent?`, `ServerConfig?`,
  `FcmRegistrationStatus`).
- Extend `SingletonGuardTask` to require any `*Repository` impl owning
  a `MutableStateFlow<T>` field to be `@Singleton` in `AppComponent`.
- (Optional) add a lint check requiring `Logger.*` on the same
  statement block as a `MutableStateFlow.value = ...` in
  `data/**/repository/**`.
- ADR status updates if not already done: ADR-013 "Partially
  superseded" (already done in this package); ADR-021 and ADR-022
  "Accepted and enforced."

**Risk:** Low. Compile-time guardrail; will fail fast if any
backsliding exists from PRs 2-7.

**Tests:** Lint fixture tests.

**Rollback:** Trivial.

---

### Parallelization

- PRs 3, 6, 7 are independent of each other and of PRs 4/5 — they can
  open in parallel after PR 2 lands on `main`.
- PR 8 must be last (it enforces what the prior PRs established).
- Realistic timing with auto-merge serialization: ~4 sequential merge
  cycles.

### Cross-cutting notes

- **Atomic interface changes are fine.** KMP `commonMain` modules are
  compiled together; there are no external consumers of `domain`. One
  PR per repo flips interface + impl + UseCase + VM + fakes in a single
  atomic change. No deprecation dance.
- **PR #354 workaround removal happens only in PR 2.** Earlier removal
  would regress; later is safe because the mirror is gone from PR 2.
- **Mid-migration convention drift:** the lint rule only tightens in
  PR 8. Between PR 1 and PR 8, some repos will be on the new shape and
  some won't. Code review catches drift in the meantime. A one-line
  tracking comment in the PR description ("SnoozeState migrated; next:
  AuthState") makes the state clear.
- **Bisect-friendliness:** each PR is one repository + its immediate
  dependents. Revert = revert one PR, no cascading.

### Observability (Rule 9) per PR

Every PR that touches a repository adds state-write logging:

```kotlin
_state.value = newState
Logger.i { "xStateFlow <- $newState (source=<POST|GET|upstream|signOut>)" }
```

This is a few lines per PR, consistent shape. Across all 7 repo PRs the
total addition is ~30 log lines, all grep-able.

## Phase 2 — Deferred work

These were flagged by the agent review as important but scope-creep for
Phase 1. Tracked here so nothing falls through the cracks.

### Phase 2a — User-scoped state clearing on sign-out (ADR-022)

Each repository that owns user-scoped state subscribes to
`AuthRepository.authState` on `externalScope` and resets its
`MutableStateFlow` on transition to `Unauthenticated`. Today's exposure
is narrow (home-IoT app, typically one user per device) but the rule is
documented in ADR-022.

Triggers: if state-write logs (Rule 9) ever show evidence of cross-user
leakage; or if the user base diversifies to multi-user-per-device
scenarios.

Repos that need this: `SnoozeRepository`, `DoorFcmRepository`
(user-scoped FCM token), possibly `AppSettingsRepository` if any setting
is per-user.

### Phase 2b — `AppLoggerViewModel` / `AppSettingsViewModel` audit (scope `c`)

Originally scope `c` from the initial question. Audit the remaining
ViewModels for any hidden domain state that should move to a
repository. Likely outcomes:

- `AppLogCount` is cold/list-y (Room query). Stays `Flow<Long>`.
- `AppSettings` entries are candidates for `StateFlow<AppSettings>` at
  the repository if the app reads them synchronously. Audit usage.

### Phase 2c — Write-ordering hardening (if evidenced)

If state-write logs show concurrent writes producing observable flashes,
add a `Mutex` inside the specific affected repository. Not a blanket
rule — per-repo opt-in once evidence exists.

### Phase 2d — Process-death cold-start flash mitigation (if evidenced)

If the cold-start `Loading → NotSnoozing → Snoozing` flash on process
restoration becomes a UX issue, persist last-known-good state in
DataStore and use it as the initial value. Today's snooze flash is
<1s; not worth addressing until evidenced.

## Phase 3 — iOS (Phase 38 in MIGRATION.md)

When iOS lands: wire Skie for `StateFlow<T>` → `@Published` / 
`AsyncSequence` mapping. Validate Skie compatibility per state-y type in
`domain/model`. This is tracked in `MIGRATION.md` Phase 38, not here.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| PR 2 regresses the snooze fix | PR 1 lands first as a regression test exercising the multi-subscriber path with the real repo. Instrumented test on emulator before merge. |
| PR 4 strands users mid sign-in session | Keep `signInWithGoogle()` and `signOut()` command semantics identical; only the observation API changes. Smoke test sign-in across rotation and process death. |
| Always-on collector in repo `init` leaks if externalScope has a bug | `externalScope` is the same `SupervisorJob + IO` used by auth and FCM — already battle-tested. |
| Lint rule (PR 8) catches legitimate transformation cases | Rule targets only type allowlist for VM `MutableStateFlow<T>`; `map { }`-then-`stateIn` on cold flows is still allowed. |
| PR starvation (8 sequential PRs delayed by auto-merge) | Parallelize 3/6/7 after PR 2. |
| Phase 2a (sign-out clearing) is deferred indefinitely | State-write logs make leakage detectable; triggered by evidence, not schedule. |

## Verification checklist (per PR)

- [ ] `./scripts/validate.sh` passes
- [ ] Reference-identity test added (`assertSame`)
- [ ] State-write log added at every new `_state.value = ...` site
- [ ] Fake repository updated
- [ ] Cross-ref ADR-021 / ADR-022 in the PR description
- [ ] No auto-merge on the enforcement PR (PR 8) without manual approval

## References

- `AndroidGarage/docs/DECISIONS.md` — ADR-006, 013, 017, 018, 019, 020,
  021, 022.
- `AndroidGarage/docs/VIEWMODEL_SCOPING_ISSUE.md` — the bug that
  motivated this.
- `AndroidGarage/docs/MIGRATION.md` — broader migration roadmap
  (Phase 38 for iOS).
