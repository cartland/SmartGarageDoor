---
category: plan
status: active
---

# Data caching remediation plan

Turns [`DATA_CACHING_STRATEGY.md`](DATA_CACHING_STRATEGY.md) from a
description into an enforced reality.

The strategy doc catalogued 87 caching mechanisms, proposed ADR-035, and
listed 13 tensions. This plan is the execution order for closing them.
It exists because the strategy review surfaced a structural problem that
outranks every individual defect it found:

> **The repo's architecture rules are enforced almost entirely by tooling
> that never runs on a pull request.**

Thirty-three `check*` Gradle tasks encode this codebase's hard-won
architectural rules. Zero of them run in CI. They run only when a human
remembers to run `./scripts/validate.sh` locally. Several have silently
decayed into checks that cannot fail. Adding more rules on that
foundation would be building on sand — so the enforcement pipeline is
fixed first, before any new rule is written.

## Ordering principle

Each phase makes the next one safe:

1. **Phase 0** makes checks run at all, so later phases cannot silently regress.
2. **Phase 1** fixes the one defect with real user-visible impact.
3. **Phase 2** makes existing checks capable of failing.
4. **Phase 3** widens static analysis to the modules that hold the logic.
5. **Phase 4** adds invariant tests for the rule *categories* prose can't express.
6. **Phase 5** adds custom detekt rules, now that detekt can see the code.
7. **Phase 6** closes the iOS-side gaps.
8. **Phase 7** ratifies ADR-035 and reconciles conflicting docs.

Phases 0–3 are strictly higher value than 4–7: they cost little, carry
low risk, and convert existing latent work into active protection.
Phases 4–7 add genuinely new capability and should be paced by review
appetite, not rushed.

## Verification status legend

The strategy review adversarially verified 5 of its 13 tensions; both
examined findings came back **PARTIAL** with material corrections. This
plan carries that honesty forward:

- **Verified** — I reproduced the defect myself in this repo.
- **Reported** — an agent found it; not independently reproduced.

Do not treat a *Reported* item as established. Reproduce before fixing;
the two verifications that were performed both changed the fix.

---

## Phase 0 — Make architecture checks run in CI

**Status:** Verified. `git grep -n "check[A-Z]" -- .github/workflows/`
returns nothing. `scripts/validate.sh` invokes 33 `check*` tasks; the CI
workflows invoke none.

**Why first.** Konsist tests (inside `./gradlew test`) are currently the
*only* architecture enforcement that can block a PR. CLAUDE.md describes
Konsist as "explicitly additive, not migration" — a redundant second
enforcement point layered over the Gradle tasks. That framing is
inverted in practice: for the rules Konsist mirrors, it is the *sole*
gate; for the ~28 rules it does not mirror, there is no gate at all.

**Change.** Add an `architecture_checks` job to
`.github/workflows/ci-checks.yml` running the same task list as
`validate.sh`.

**Why this needs no branch-protection dance.** The required context is
`Android CI Complete`, which gates on the *result of the whole reusable
workflow* (`needs: [checks]`). A new job inside `ci-checks.yml` is
covered automatically — no new required context, no rename ordering
problem (see CLAUDE.md § "Renaming a branch-protection-required gate
job" for why that matters).

**Risk: low.** `validate.sh` passes locally on `main` today, so the task
list is known-green. The job needs the same `setup-android` action as
its siblings (a placeholder `google-services.json` on Dependabot runs).

**Exit criteria.** A PR that violates any of the 33 rules fails CI
without a human running anything locally.

**Follow-on.** Keep the CI job and `validate.sh` in sync. The task list
now lives in two places; a future cleanup could have both read one
shared list, but duplicating 33 task names is not worth a build-script
abstraction until it drifts once.

---

## Phase 1 — P0: sign-out does not clear in-memory caches

**Status:** Verified. `SignOutCacheClearManager` terminates at
`userScopedCache.clearUserScopedEntries()`; `DefaultUserScopedCache`
forwards to `statusSnapshotStore.clear(...)` — the **disk** store only.
`NetworkButtonHealthRepository`, `NetworkSnoozeRepository`, and
`DefaultTestNotificationRepository` contain **zero** references to
`authState` or `AuthState`.

**Impact.** These repositories are `@Singleton`: their `StateFlow`s live
for the process. Signing out clears the persisted snapshot, but the
in-memory value keeps rendering. Sign in as a different account without
killing the process and the previous account's data is on screen.

Two aggravating factors:

- **Snooze suppresses its own correction.** `NetworkSnoozeRepository`
  keeps `lastFetchedAtSeconds` across sign-out, so `fetchIfStale` treats
  the stale value as fresh and skips the network for up to
  `FETCH_TTL_SECONDS` (300s). The stale value prevents the fetch that
  would replace it.
- **Test-notification keeps a live FCM subscription** to the previous
  user's private topic while signed out.

**Change.** Give each user-scoped in-memory repository the same
auth-projection collector the disk path already has: reset in-memory
state (including fetch timestamps) and drop subscriptions on the
`Unauthenticated` edge.

**Design constraint — follow the existing pattern, don't invent one.**
`SignOutCacheClearManager` already projects `authState` to a boolean
before reacting, specifically so token-refresh writes of new
`Authenticated` instances cannot re-trigger it (CLAUDE.md § "Avoid
auth-state feedback loops"; PR #672 shipped a fix for exactly this
loop). Any new collector must project identically. The
`checkAuthStateProjection` task exists to enforce this — and per Phase 0
it currently never runs on a PR.

**Extend the manager, don't scatter collectors.** ADR-015 puts
app-scoped lifecycle work in managers. Adding three independent
`authState` collectors inside `data/` repositories would put lifecycle
orchestration in the wrong layer and create three chances to get the
projection wrong. Preferred shape: repositories expose a
`clearUserScopedState()` (a suspend function on an interface the
manager can call), and the existing manager fans out to them. This also
makes the behavior testable without a real auth stack.

**Exit criteria.** A test proves that after an `Unauthenticated`
emission, each repository's exposed state is empty/`Unknown`, its fetch
timestamp is reset, and the test-notification subscription is dropped.

---

## Phase 2 — De-vacuum the checks that cannot fail

**Status:** Reported (four items). Reproduce each before fixing — a
check that "looks broken" may have a narrower intended scope.

| Check | Reported defect | Live violations claimed |
|---|---|---|
| `checkTestCoverage` | Unwired, wrong scope, and its regex `(?:class\|object)\s+(\w+)\s` cannot match `class Foo(` | 100% dead |
| `checkViewModelStateFlow` | `stateIn` regex is per-line; real calls use multi-line named arguments | 3 (`HomeViewModel.kt`) |
| `checkUiLayerNoGraphAccess` | Matches `component.*UseCase`/`*Repository` but not `component.appSettings` | 3 (`Main.kt`) |
| `checkPreviewCoverage` | Reports 100% while blind to 5 public previews | `TabPreviews.kt` |

**Sequencing trap.** Fixing a vacuous check *surfaces* its live
violations, which turns CI red the moment Phase 0 lands. So each fix and
its violations must land in the **same PR**. Do these one check per PR:
four small green PRs, not one large red one.

**Do not add these to an exemptions file.** This repo's exemption files
(`screen-viewmodel-exemptions.txt`,
`ui-layer-graph-access-exemptions.txt`) are burn-down lists whose stated
goal is empty, and the checks fail on stale entries. Three
`Main.kt` violations of a documented ADR are a fix, not a grandfather
clause.

---

## Phase 3 — Widen detekt beyond `:androidApp`

**Status:** Verified. `ci-checks.yml` runs `./gradlew :androidApp:detekt`.
Every KMP module reports `NO-SOURCE`: `:viewmodel`, `:usecase`, `:data`,
`:data-local`, `:domain`, `:iosFramework`, `:test-common`,
`:presentation-model`, `:android-screenshot-tests`. `:wearApp:detekt`
**fails**.

**Consequence.** detekt has never examined a ViewModel, a UseCase, a
repository, or a domain type — precisely the layers this strategy is
about. This also means **a custom detekt rule written today would scan
zero relevant files**, which is why Phase 5 must come after this one.

**Root detekt is red on `main`.** Two `TooGenericExceptionCaught`
findings (`WearAuthRelayClient.kt:70`, `FirebaseAuthBridge.kt:110`).
This is invisible today because CI only runs the `:androidApp` task.

**Change.** KMP modules need explicit source configuration
(`source.setFrom(...)` over `commonMain` and friends) — the Android
plugin's source-set autodetection is what leaves them `NO-SOURCE`.
Then move CI from `:androidApp:detekt` to the aggregate `detekt` task.

**Risk: medium, and this is the phase most likely to balloon.** Turning
detekt on for nine modules at once will surface a findings backlog of
unknown size. Recommended order: enable modules one at a time, baseline
nothing, fix what appears. If a module's findings are large, that is
information worth having before deciding whether to fix or configure.

**Exit criteria.** `./gradlew detekt` is green and CI runs it; no module
silently reports `NO-SOURCE`.

---

## Phase 4 — Generic invariant tests

The rule categories below cannot be expressed as a grep and are not
tied to one call site. They are the highest-leverage new tests because
each one closes a *class* of bug rather than an instance.

**4a. DI parity across all three components.** `AppComponent`
(`@Singleton`), `NativeComponent` (`@SharedSingleton`), and
`WearComponent` (`@WearSingleton`, 17 providers) are mirrored **by
hand**. CLAUDE.md documents two of the three and describes the
Android↔iOS drift trap that broke iOS `main` in PR #871. A test that
asserts a shared-module type scoped in one component is scoped in all
components that wire it converts a recurring manual-review burden into
a compile-time-ish guarantee.

**4b. Sign-out classification.** `StatusCacheKeys.CLEARED_ON_SIGN_OUT`
carries a prose rule in its KDoc: *"A PR that persists a per-user value
MUST add its key here in the same change."* Nothing enforces it. A test
that enumerates every declared `StatusCacheKey` and requires each to be
explicitly classified — cleared-on-sign-out or explicitly
device-scoped — makes the omission a build failure instead of a code
review miss. This is the generic form of the Phase 1 bug.

**4c. First-frame harness.** The Watch-section animation bug (PR #1131)
was: a `NavBackStackEntry`-scoped ViewModel seeded a default, then
corrected it one frame later. That is a *category*, not an incident —
the same shape produced the Home icon flash, the History empty flash,
and the button-health flicker, each fixed individually. A harness that
constructs a ViewModel against a warm fake and asserts its first emitted
value is already the real value would catch the next one before it
ships.

**Placement matters — and is currently wrong.** `ComponentGraphTest`
lives in `androidTest/` (post-merge only) and `NativeComponentTest` in
`iosTest` (iOS CI is not a required check). The two highest-value DI
invariant tests in the repo **cannot block a bad PR today**. Any new
invariant test must land in a source set that a required check runs, or
it inherits the same problem. Moving the existing two is worth doing in
this phase.

---

## Phase 5 — Custom detekt rules

**Blocked on Phase 3.** A rule cannot fire on files detekt does not
scan.

Mechanically this needs a `:detekt-rules` subproject exposing a
`RuleSetProvider` via `META-INF/services/`, consumed through
`detektPlugins`. Candidate rules, in descending value:

1. `stateIn(viewModelScope)` — banned by ADR-017 R6; the current grep
   check misses multi-line calls (Phase 2). A PSI rule reads the
   receiver properly.
2. Repository mutation on `viewModelScope` rather than `externalScope`
   (ADR-019 R1) — the `fetchOlderDoorEvents` defect.
3. `MutableStateFlow` mirror of an upstream `StateFlow` (ADR-022).
4. Unprojected `authState` in a `combine` (feedback-loop class).
5. `try` without `finally` around an in-flight flag mutation.

**Judgment call.** Rules 1–3 are worth building. Rules 4–5 are
pattern-matching on intent and will produce false positives; prefer a
Konsist test or leave them to review. Do not build all five reflexively.

---

## Phase 6 — iOS parity gaps

**Status: Reported, not verified.** Reproduce first — the iOS findings
came from the least-verified part of the review.

- **No backup exclusion.** Android keeps DataStore files and the Room DB
  out of cloud backup via `checkBackupRulesExcludes` (an enforced lint).
  A repo-wide grep for `NSURLIsExcludedFromBackupKey` returns nothing,
  and the iOS equivalents live under `NSDocumentDirectory`, which iCloud
  backs up by default. If confirmed, door history rides into iCloud on
  iOS but is deliberately excluded on Android — the same data, opposite
  policies, one of them unexamined.
- **Auth pre-seeded with `null`.** A signed-in cold start may emit a
  spurious `Unauthenticated`, firing `SignOutCacheClearManager` and
  wiping the very snapshots ADR-034 exists to display. Note this
  interacts with Phase 1: widening the sign-out clear to in-memory state
  makes a spurious `Unauthenticated` **more** damaging. **Verify this
  one before Phase 1 ships**, not after.
- **Wrappers retain `self` in never-ending `for await`** (3 files), so
  `deinit` never runs. Same family as the `ios/7` launch crash, where a
  discarded `StateObject` wrapper's Task outlived its owner — worth
  checking against the `self!` ban already enforced by
  `scripts/check-ios-self-force-unwrap.sh`.

---

## Phase 7 — Ratify ADR-035 and reconcile docs

ADR-035 is **proposed, not adopted**. Merging the strategy doc did not
adopt it. Several checks designed in Phases 4–5 encode its rules, so
ratify (or amend) before building enforcement against it.

Doc conflicts to resolve in the same pass:

- **ADR-015 vs ADR-022.** ADR-015 says app-scoped managers expose
  `Flow`; ADR-022 says pass cached `StateFlow` through by reference.
  `CheckInStalenessManager` follows the former and re-emits a default
  per navigation; `FcmRegistrationManager` already follows the latter.
  This is a genuine documented conflict, not an oversight — pick one.
- **CLAUDE.md says two DI components.** There are three.
- **CLAUDE.md calls Konsist "explicitly additive."** Until Phase 0
  lands, it is the only gate.
- **`DoorEvent` retention is undecided, not decided.** The table is
  unbounded while `AppEvent` in the same database is capped at 1000 per
  key. The most personal dataset in the app has the weakest retention
  policy by default rather than by choice. Decide and write it down.

---

## What this plan deliberately does not do

- **No caching rewrite.** Every phase is additive or corrective. The
  87 mechanisms stay; the plan makes their rules enforceable.
- **No new exemption files.** Violations found by fixing a check get
  fixed, not grandfathered.
- **No manual smoke steps.** Per CLAUDE.md, if a change can only be
  verified on a device, that is a design defect — build the fixture
  inside the PR.
- **No rules without a home.** A rule that cannot run in a required
  check is not enforcement; it is a comment.
