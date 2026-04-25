---
category: archive
status: shipped
---
# Postmortem — android/170 snooze propagation regression

**Final status:** Resolved on android/174 (2026-04-19).
**Root cause:** kotlin-inject silently ignored every `@Singleton`
annotation in `AppComponent` because the component had no abstract
entry points. Every `provideSnoozeRepository()` call constructed a
fresh `NetworkSnoozeRepository` with its own `MutableStateFlow`. The
POST write and the VM's observer subscribed to different flow instances.

**Time to resolution:** ~5 months of partial fixes masking the real
bug; 1 session (2026-04-19) to identify and permanently fix once the
investigation shifted from symptoms to generated code.

This document captures why it was hard to identify, every release in
the cycle, and the lessons — with concrete checklists — for future
invisible-configuration bugs.

## Timeline of releases

| Tag | Commit context | Intent | Actual behavior |
|---|---|---|---|
| android/164-167 | Pre-migration snooze flow | Normal releases | Users reported: save snooze → card title doesn't flip; kill+restart fixes it. Intermittent / hard to reproduce. |
| android/168 | Various debugging attempts | Fix snooze propagation | Failed to fix. Same symptom. |
| android/169 | PR #354 | Direct-write `_snoozeState.value = result.data` in VM on success | **Symptom disappeared.** Declared "fixed." (Actually: direct-write bypassed the DI-level split, but nobody knew that.) |
| android/170 | PRs #358–#365 (Phase 1 of ADR-021/022) | Move state ownership to repository `StateFlow`; VM exposes by reference; remove PR #354 workaround | **Symptom returned immediately.** Users reported within hours. |
| android/171 | Rollback to android/169's commit via `--confirm-hash` | Unblock users | Users unblocked. |
| android/172 | PR #369 | Restore android/169 pattern (VM-local mirror + direct write) | Symptom gone again (same mask as android/169). ADR-022 marked Withdrawn. |
| android/173 | PR #371 (Phase 2f) | Fix kotlin-inject scoping with abstract entry points + parameter-based providers + `ComponentGraphTest.*IsSingleton` | **Root cause actually fixed.** Snooze still works. |
| android/174 | PR #372 | Remove the android/172 VM-local mirror, restore ADR-022 pass-through | Snooze still works → empirical proof Phase 2f was the full root cause. |
| — | PR #373 | Restore ADR-022 Rule 2 lint enforcement | Bug class structurally prevented going forward. |

## Why it was hard to identify

### Four properties of the bug made it invisible

1. **Annotation-shaped.** `@Singleton` was present on every provider.
   Source code looked correct. Reviewers and static analysis had no
   hook to flag the problem.
2. **Transitively broken in generated code.** The failure lived in
   `build/generated/ksp/*/kotlin/.../InjectAppComponent.kt` — a file
   no human had ever read. From the hand-written source, nothing
   pointed at "the wrong repo instance."
3. **Masked by unit/instrumented tests.** Every debug test constructed
   a single VM with a manually-wired repo via `new NetworkSnoozeRepository(...)`.
   The multi-instance DI graph was never exercised. All tests passed
   through the entire bug window.
4. **Masked by a workaround.** PR #354's VM-local direct-write made
   the symptom go away without fixing the propagation chain. For ~5
   months this was treated as "the fix," so nobody questioned the
   underlying architecture.

### Specific failures of investigation

The bug survived multiple targeted investigations. Each of them
reasoned correctly from the available signals — but the available
signals were all wrong.

- **"The snooze state uses StateFlow; it should propagate."** True —
  *if* the observer and writer share a StateFlow. Assumption: they
  did, because the repo was `@Singleton`. The assumption was wrong.
- **"The instrumented test reproduces the full VM → repo chain."**
  Only in the tests' wire-up, not in the production DI graph. The
  test constructed one repo and passed it explicitly. Production DI
  constructed N.
- **"ADR-022 Rule 2 pattern works because tests prove reference
  identity via `assertSame`."** The identity assertion was only done
  between `fake.snoozeState` and `vm.snoozeState` within one manually-
  built VM. It never compared across VMs, across UseCase providers, or
  across component accesses. All the places the bug actually lived
  were untested.
- **"PR #354's direct-write is a defense-in-depth layer."** It was
  actually the load-bearing workaround — the only thing keeping the
  snooze UI correct. Removing it (PR 2 of Phase 1) was treated as a
  cleanup; it was a de facto regression.
- **"If it were a DI bug, every other `@Singleton` would also be
  broken."** They *were* broken — `AuthRepository`, `DoorRepository`,
  `ServerConfigRepository`, `DoorFcmRepository` all had the same
  multiplication. They just didn't manifest user-visibly because
  incidental singletons underneath (Firebase SDK, Room flow, Ktor
  client) did the deduplication for them. This hid the scope of the
  problem.
- **Initial android/170 investigation reached for R8 first.** The
  logic: "release-only regression, debug tests pass, must be
  minification." Built an R8 instrumented test harness. Ran the
  snooze test under R8 — it failed, but with `ClassNotFoundException:
  kotlin.LazyKt`, a test-APK minification issue, not the bug.
  User intervened: *"I am skeptical about this being an R8 issue
  because part of the app sees the data and another part does not."*
  That reframing from "selective failure rules out R8" is what
  unblocked the investigation.
- **Only after spawning a multi-agent research team** did one agent
  actually read `InjectAppComponent.kt` and count its 15 lines. That
  was the first time the generated code had been inspected in this
  bug's entire 5-month lifetime.

## Lessons

### Lesson 1: For framework contracts, read the generated code.

If your architecture depends on a framework-shaped invariant
(`@Singleton`, `@Inject`, `@Serializable`, `@Parcelize`, R8 keep rules),
the hand-written source is not the authoritative signal — the generated
or post-processed code is. If you have not read it, you do not know
what the runtime will do.

**Apply this:** before claiming an annotation-backed invariant holds,
open the corresponding generated file. Count the overrides / count
the members / grep for the cache key. If the shape doesn't match your
mental model, stop and investigate.

### Lesson 2: Identity is the only proof of singleton-ness.

"There's only one `@Provides` function so it must be a singleton" is
wrong on its face once you remember that a function can be called
multiple times. The only proof is `assertSame(c.x, c.x)` across
independent accesses. If that test doesn't exist, the scope annotation
is decorative.

**Apply this:** for every invariant that asserts "there is exactly
one" (singleton instances, deduped listeners, shared scopes), add a
runtime test that would fail if the invariant relaxed. Don't rely on
annotation presence or static analysis.

### Lesson 3: Workarounds are a failure signal, not a fix.

When a bug is "fixed" by adding a belt-and-suspenders layer that
bypasses the normal mechanism (PR #354's direct-write, defensive mirror,
manual invalidation), treat that as strong evidence the normal
mechanism is broken. The workaround masks the symptom for this feature
while leaving the bug in place for every other feature that shares the
mechanism.

**Apply this:** when you add a workaround to unblock a user:
- File a follow-up to investigate why the normal path failed.
- Attach a test that would fail if the workaround were removed. If
  the workaround is truly a defense-in-depth layer, removing it should
  not cause a failure. If it does, you have a bug.
- Be suspicious of "this fixed it" reports before the workaround is
  removed and the test re-run.

### Lesson 4: Selective failure is a diagnostic.

In this bug, the overlay (`SnoozeAction`, VM-local `MutableStateFlow`)
updated correctly while the card title (`SnoozeState`, repo-owned
`StateFlow`) did not. Both came from the same VM. That selectivity
ruled out "everything is broken" theories like R8 stripping or
process-wide coroutine failure. It pointed at something specific about
the `snoozeState` propagation path.

The user identified this reframing before the investigation did:
*"Part of the app sees the data and another part does not."*

**Apply this:** when triaging an intermittent or selective failure,
the first question is "what's different between the working and broken
path?" Not "what's broken about coroutines / Compose / R8 / the
framework." Selective failure points at a specific wiring difference;
global hypotheses rarely fit.

### Lesson 5: Spawn independent research agents when stuck.

A single investigation converged on the wrong frame (R8) because each
step built on the previous step's assumption. Launching five parallel
agents — each given one hypothesis to verify or falsify — produced
five independent analyses. The one that found the bug was the one
assigned to audit DI scoping; the others confirmed the chain was
otherwise correct.

**Apply this:** when stuck on a hard bug for more than an hour, don't
keep deepening a single investigation. List the candidate hypotheses.
Launch one agent per hypothesis in parallel, each with a narrow brief.
Compare results. Divergent conclusions are useful; converged
conclusions promote high-confidence fixes.

### Lesson 6: User reframing is a gift.

*"I am skeptical about this being an R8 issue because..."* is the
single highest-leverage sentence in this investigation. The user saw
the overlay + card title asymmetry and correctly identified it as a
propagation problem, not a minification problem. This shifted the
investigation from "test under R8" to "check the graph."

**Apply this:** treat user observations about selective failure,
timing, or asymmetric behavior as high-signal diagnostics, not
uninformed commentary. If the user's reframing contradicts the current
investigation's assumption, suspect the assumption first.

### Lesson 7: Some bugs need defense-in-depth, not a single fix.

android/170 taught that `@Singleton` scoping can fail silently. One
fix (abstract entry points) isn't enough — without the identity test,
a future refactor silently reintroduces the bug. Without the lint
that bans VM-local mirrors, a future workaround silently masks it.
Five layers make the bug class structurally dead:

1. In-file convention + KDoc
2. `checkSingletonGuard` (static — `@Singleton` on every state-owning
   provider)
3. `checkViewModelStateFlow` (loud-fail — ban VM-local mirrors so DI
   breakage produces visible UI regressions, not silent failures)
4. `ComponentGraphTest.*IsSingleton` (runtime — identity asserts)
5. `DI_SINGLETON_REQUIREMENTS.md` (docs — failure modes, detection
   recipes)

**Apply this:** for any annotation-shaped invariant (DI scope, R8 keep
rules, serialization contract, Room schema), sketch 3-5 independent
signals that would fail independently if the invariant broke. Cost is
small (a few lines each). The cost of any single signal failing
silently is a user-visible regression + rollback + multi-release
investigation. Always take the defense-in-depth route for invisible
invariants.

## Checklists — use these when you see the shape

### When a bug "feels architectural"

- [ ] Have I read the generated code? (KSP / kapt / proguard output)
- [ ] Have I written an identity / count / size assertion that would
      fail if the invariant relaxed?
- [ ] Is there a workaround in place whose presence I can't explain?
      (If yes: that's the first thing to investigate, not the last.)
- [ ] Does the failure pattern rule out entire classes of cause? (What
      would R8 stripping look like? Would it match the selective pattern
      I'm seeing?)
- [ ] Have I asked the user what they actually see, not what they're
      telling me "is broken"?

### When stuck for > 1 hour on a hard bug

- [ ] Write down the 3-5 candidate hypotheses as one-sentence claims.
- [ ] Spawn one research agent per hypothesis, each with narrow brief.
- [ ] Compare results — do any two agents converge on the same evidence?
- [ ] Pick one hypothesis to falsify, not to confirm.

### When touching any framework-generated code path

- [ ] Read the generated output before merging the change.
- [ ] Add a runtime test that exercises the production graph, not a
      manually-wired test double.
- [ ] Document the invariant in ADR + in-file KDoc + a detection
      recipe (what to look for if this breaks).

### When a workaround "fixes" a bug

- [ ] File a follow-up to investigate the underlying mechanism.
- [ ] Write a test that fails when the workaround is removed.
- [ ] Do NOT remove the workaround until the underlying mechanism is
      fixed AND the test still passes.

## Related docs

- `docs/DI_SINGLETON_REQUIREMENTS.md` — requirements, constraints,
  detection recipes
- `docs/DECISIONS.md` ADR-022 — `StateFlow` at the repository boundary;
  timeline of withdrawal and restoration
- `docs/MIGRATION_PLAN.md` Phase 2f — fix rollout plan
- `docs/VIEWMODEL_SCOPING_ISSUE.md` — the original android/167 bug
  report (now known to have been a symptom of the DI bug from the start)
