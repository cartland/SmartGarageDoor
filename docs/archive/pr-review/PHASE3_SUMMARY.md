---
phase: 3
generated: 2026-04-20
source_corpus: docs/pr-review/pr-0001.md .. pr-0383.md
---

# Phase 3 summary — still-current goals and lessons

Compact synthesis of Phase 2 assessments across 381 merged/closed PRs. Organized by theme. For each theme: exemplar PRs (patterns still current in the codebase), the novel lessons worth propagating in Phase 4, plus any reversed guidance or bugs worth remembering.

Distribution that fed this doc: **99 great / 243 ok / 32 superseded / 2 buggy / 3 outdated-guidance / 2 abandoned.** About 114 PRs carried a still-current lesson.

Phase 4 reads this file, not the 381 individual files.

---

## 1. Android architecture

**Shape:** clean architecture in four layered KMP modules — `domain/` (pure types + repo interfaces), `data/` (data-source interfaces + repo impls), `usecase/` (business logic), `presentation-model/` (screen state). `androidApp/` owns Compose, Room entities, Firebase bridges, DI wiring.

**Exemplars:**
- **#44** — domain models + repo interfaces first. Foundation for every layer that follows.
- **#74** — first pure-Kotlin `data/` module (`LocalDoorDataSource` interface).
- **#133** — renamed `*Impl` → descriptive prefixes (`Network*`, `Cached*`, `Firebase*`, `Default*`). ADR-008.
- **#196** — build-time architecture enforcement (dependency graph, singleton guard, import boundary).
- **#203** — split `PushRepository` into `RemoteButtonRepository` + `SnoozeRepository` when the name started saying "and".

**Lessons:**
- A repository with "and" in its conceptual name is a smell — split when names diverge.
- When you finish a "X may only depend on Y" migration, land the lint that enforces it in the same wave. Conventions without enforcement decay one convenient import at a time.
- Move shared model types to `domain/` in one deliberate wave, with a persistence-boundary entity mapping to/from the domain type. Don't let Room entities leak into the domain layer.
- A repository interface that wraps exactly one Android-specific API call is a DI shim, not a repository — inline the action and save the abstraction budget.

---

## 2. Dependency injection

**Shape:** kotlin-inject (`AppComponent`), Hilt fully removed. Every `@Singleton` state-owning provider is an **abstract entry point** (`abstract val x: T`). Every `@Provides fun` body takes deps as parameters. `ComponentGraphTest` asserts identity (`assertSame`) for singletons and non-identity for per-request types.

**Exemplars:**
- **#86** — ship new DI alongside old; migrate consumers one at a time; delete old last.
- **#91** — complete kotlin-inject migration (all ViewModels + FCMService).
- **#105** — `ComponentGraphTest` instrumented test asserting every ViewModel/Repository resolves.
- **#371 / android/173** — root-cause fix: make `@Singleton` actually cache. Concrete `val x: T @Provides get() = …` silently bypassed `_scoped.get()`; only abstract overrides emit caching.

**Lessons:**
- Inject `DispatcherProvider`; never reference `Dispatchers.IO` directly. Production gets real; tests get `TestDispatcher` (virtual time).
- When a DI container has a silent-fail mode, read the generated code at least once. "Compiled" and "correct" are independent properties of annotation-driven codegen.
- When a DI binding changes, you need both asserts: `assertSame` that singletons are shared, `assertNotSame` that per-request types make new instances.

**Bug worth remembering — #371 (android/170):** kotlin-inject caching regression took 5 months to isolate. `val x @Provides get() = …` providers never cached; only abstract entry-point overrides with parameter-based bodies emit `_scoped.get()`. No user-visible error, identical visible behavior under low-frequency calls, hidden in generated build artifacts. **Prescription (Phase 4):** four defensive layers — postmortem (done, #374), CLAUDE.md safety rules (done), mandatory generated-code inspection step after AppComponent edits, identity test for every new singleton.

---

## 3. ViewModels & state

**Shape:** ViewModels own ephemeral UI state; repositories own long-lived domain state (`StateFlow<T>` exposed directly). Event-y data flows through `Channel`; state-y data flows through `StateFlow`; lists through `Flow`. No Activity/Intent/Context in ViewModels. App-scoped Managers own cross-screen operations.

**Exemplars:**
- **#237 + #238** — unified `ButtonStateMachine`: `Channel` consumer serializes events, injected `TestDispatcher` for virtual time, `drop(1)` on StateFlow, stateless Composable.
- **#266** — rename state-machine variants to user-visible phases (`SendingToServer` → `SendingToDoor` → `Succeeded`) so the state flow reads like the UX.
- **#289** — `FcmRegistrationManager` app-scoped singleton with idempotent `start()` — operations that outlive any screen don't belong in ViewModels.
- **#291** — inject `CoroutineScope` into ViewModels; tests pass `backgroundScope`, production passes `viewModelScope`.
- **#296 / ADR-017** — seven numbered test rules, lint-enforced: `setMain/resetMain` for VM tests, `MutableStateFlow + init.collect` (banned `stateIn` in VMs), etc.
- **#344** — stuck-Loading bug fix: singleton `StateFlow` first-write must come from a scope that can't be cancelled (`externalScope.launch {}` in repo `init`).

**Lessons:**
- Operations that outlive any screen (FCM registration, push retry, staleness ticker) belong in app-scoped Managers, not ViewModels. Singleton instance, idempotent `start()`, lives on `applicationScope`.
- Any repository call that writes shared state must run on `externalScope`, not `viewModelScope` — caller cancellation is the default failure mode.
- Current state and action-in-progress/outcome are two **independent** streams — modeling them as one enum forces UI to conflate them. Split into `State` + `Action` sealed types.
- A Composable with `delay()`, `coroutineScope {}`, or `remember { mutableStateOf(...) }` for business transitions is hiding a state machine. Extract it; make the Composable a stateless renderer.
- Navigation-stack manipulation is business logic. Extract from `LaunchedEffect`/Composables into a pure function; unit-test the rules.
- `LaunchedEffect` *key* controls direction of sync. Key on the external source so external→local fires on arrival; keying on local state writes default back and clobbers it.
- Delay loops in ViewModel `init` hang tests under `runTest`. Inject the scope; tests pass `backgroundScope` (cancelled at test end, no cleanup).
- "Tests pass" ≠ "tests are correct" — any class extending `ViewModel` using `viewModelScope` needs `setMain/resetMain` even if tests currently pass. Absence is a time-bomb.
- "Succeeded" is not always one shape — if a command can succeed with multiple semantically distinct outcomes, make them different sealed variants.

**Reversed guidance — ADR-013 → ADR-022:** "no `StateFlow` below ViewModel layer" was reversed. Repo-owned `StateFlow` is now correct for state-y data. Multi-layer `stateIn(Eagerly)` rewrap caused conflation bugs (#283, #284, #357, #360, #361). Cross-cutting rule: **categorize data into state-y / list-y / event-y before writing a layer rule** (#357). When a standard API has a subtle failure in your context, ban it at lint, not just docs (#300).

**Bug worth remembering — #265 (buggy):** button timeout started on `PushStatus.SENDING` event; when UseCase failed before emitting SENDING, timeout never started. Lesson: gating a critical timeout on a flow event adds a failure mode every time the event can go missing. Prefer "start timeout on explicit boundary call" over "start timeout when you hear this signal."

---

## 4. Networking

**Shape:** Ktor HTTP client + kotlinx.serialization. Data sources return sealed `NetworkResult<T>` at the HTTP boundary (`Success`, `HttpError`, `ConnectionFailed`). HTTP types stay inside the data-source impl; repositories see plain domain types.

**Exemplars:**
- **#78** — abstract HTTP library behind `NetworkDoorDataSource` interface; Repository no longer sees `Response`/null/HTTP codes.
- **#100** — Retrofit → Ktor swap: only DI wiring changes at consumer call sites; contract holds.
- **#158** — `NetworkResult<T>` replaces nullable/Boolean returns; exhaustive `when` forces callers to decide on every failure mode.
- **#353** — raw-body logs at network-data-source boundaries for state-critical decodes turn release-only R8 failures from silent-wrong into one `adb logcat` line.

**Lessons:**
- Network boundary returns a sealed `NetworkResult<T>` — not `T?`, not `Boolean`. Exhaustive `when`; no `else`.
- HTTP-library types (Ktor `HttpResponse`) stay inside the impl. Swappability is the payoff.
- Any string the server parses is a wire format — pin the exact shape in a contract test, not just a round-trip integration test.
- When unit + JVM + emulator tests pass but production fails → suspect R8. Three required responses: explicit keeps (`$Companion`, `$$serializer`), raw-body log at the decode site, instrumented propagation test.

---

## 5. Auth

**Shape:** Firebase Auth SDK hidden behind `AuthBridge`. `FirebaseAuthRepository` injects the bridge. Auth state is a stream (`AuthStateListener`), not a query. Google One-Tap lives in the Compose layer; the ViewModel only sees `GoogleIdToken`.

**Exemplars:**
- **#152** — `AuthBridge` extraction; `FakeAuthBridge` enables unit testing.
- **#181** — Google Sign-In moved to Compose layer; `AuthViewModel.signInWithGoogle(idToken: GoogleIdToken)` — no Activity/Intent/IntentSender.
- **#328** — reactive auth via `AuthStateListener`.

**Lessons:**
- Auth is a stream, not a query — subscribe and let writes be fire-and-forget. Imperative `forceRefresh` at sign-in is a network race by design.
- ViewModels shouldn't take `Activity` or `Intent`. When a platform SDK wants an Activity, put orchestration in a Compose helper; VM consumes the result as a domain value.
- Any platform SDK you'd otherwise mock in tests should have a thin bridge interface in `commonMain`.
- For any `suspendCancellableCoroutine` wrapping a callback API, register BOTH success and failure callbacks — a missing failure callback doesn't throw, it silently hangs forever (#53).

**Bug worth remembering — #295:** auth state `stateIn(Eagerly)` rewrap conflated emissions; #328 (auth state touched the screen) masked the symptom enough to fool testing. Lesson: when a fix "works on device" after you change something, don't assume the thing you changed was the cause — demand a failing-test-first reproduction before claiming root cause.

---

## 6. FCM & push notifications

**Shape:** Topic-name format and payload keys are a frozen wire contract. Contract tests (`FcmTopicTest`, `FcmPayloadParsingTest`) pin the exact shape. Registration is app-scoped (`FcmRegistrationManager`), not ViewModel-scoped. `MessagingBridge` decouples FCM SDK.

**Exemplars:**
- **#50** — contract tests pin FCM topic format and payload keys (renames, missing fields, extra fields).
- **#289** — registration moved from ViewModel to app-scoped manager with idempotent `start()` and forever retry loop. ADR-014.

**Lessons:**
- Features that fail silently in production (push, crash reporters, remote config) need contract tests that pin exact wire format. Changes require explicit approval (enforced in CLAUDE.md).
- When introducing a cross-cutting architectural rule, categorize data into buckets (state-y / list-y / event-y) first — one-size-fits-all rules don't survive mixed shapes.

---

## 7. Room & local storage

**Shape:** `fallbackToDestructiveMigration` — users lose cached data, which is re-fetched. Three-layer schema-drift guard: validate.sh JSON diff, git hook warning on entity/DAO edits, `RoomSchemaTest` asserting columns + enum stability. DataStore (not SharedPreferences) for any user setting that backs reactive UI.

**Exemplars:**
- **#56** — three-layer Room schema safety check.
- **#199** — DataStore over SharedPreferences wrappers for reactive reads.

**Lessons:**
- Room schema changes break at runtime, not compile time. Ship three guards in one wave: CI drift check, git hook, contract tests.
- Room DAOs returning a non-nullable single row throw on empty table. Always `Flow<Entity?>` or `suspend fun get(): Entity?` for queries that can legitimately have zero rows.
- `rememberSaveable` without an explicit `Saver` is a latent crash waiting for process death — lint for it, contract-test the types you save, instrumented-test screen recreation.

---

## 8. Testing philosophy & infrastructure

**Shape:** fakes over mocks (zero Mockito imports). `test-common` KMP module hosts shared fakes. Two fake boundaries for two test layers: UseCase tests fake at repository interface; data tests fake at data source. `validate.sh` mirrors CI.

**Exemplars:**
- **#12** — testing plan as a risk inventory, not a coverage target.
- **#16** — `validate.sh` + hooks baseline safety net.
- **#107** — screenshot tests via AGP Screenshot Plugin with sequential shell script (OOM prevention).
- **#215** — zero Mockito; every interface has a `test-common` fake.
- **#219** — real repos + fake data sources for data tests.
- **#296 / ADR-017** — seven numbered test rules, lint-enforced.

**Lessons:**
- Start a testing plan with a risk inventory, not a coverage target. Tests that target specific real risks compound; tests chasing a number don't.
- Two fake boundaries for two test layers — UseCase tests fake at repository interface; data tests fake at data source. Don't share fakes between layers.
- Zero-mock policy is achievable with `test-common` fakes for every interface — the payoff is tests that don't lie about method ordering.
- Preview Composables must be deterministic (fixed `Instant.parse(...)`, never `Clock.System.now()` or `Random`). Non-deterministic previews cause spurious screenshot diffs.
- AGP Screenshot Plugin OOMs when all tests run in one Gradle invocation. Use a sequential shell script per test file; never `updateDebug`+`validateDebug` in the same gradle call.
- For state-critical UI flows, write an instrumented test that exercises the **full** chain `MutableStateFlow` → `collectAsState` → recomposition.
- For shared-state repositories, write an N-subscriber integration test before removing any VM-local mirror.
- Reproducing release-only bugs needs a **deliberate** path (`-Pflag` routing `connectedAndroidTest` to a minified variant on demand) — default-on would ruin CI speed.
- File-level "missing test file" lint beats coverage thresholds — a missing test is actionable.
- `val` + mutable-collection-type is a hidden `var`. Lint that bans public `var` needs a matching rule for mutable collection fields.

---

## 9. CI, hooks, and guardrails

**Shape:** Android and Firebase each have split CI — pre-submit (blocks PRs) and post-merge (catches regressions, auto-files GitHub issues with `ci-failure/<workflow>` label, auto-closes on fix). Shared steps via `workflow_call` reusable workflow. Docs-only fast path skips the full pipeline. Stop hooks and git-guardrails enforce push/merge invariants that prompts alone can't.

**Exemplars:**
- **#18** — stop hooks enforce workflow invariants (PR backlog, dev mode).
- **#25** — release AAB builds on every PR (catches release-breaking changes before merge).
- **#29** — Detekt static analysis with zero-baseline tolerance.
- **#40** — release script computes next tag; hook blocks direct `git tag`. Two-layer guard.
- **#109** — split CI into pre-submit + post-merge with `workflow_call` shared steps.
- **#108** — auto-issue on post-merge failure, auto-comment on recurrence, auto-close on fix.
- **#330** — always `--base main` for stacked PRs; CI only triggers on PRs targeting protected branches.
- **#379** — docs-only fast path rule: skip CI only when the change cannot affect what CI verifies.

**Lessons:**
- Stop hooks enforce workflow invariants prompts can't ("too many open PRs, merge first", "dev mode until rm").
- Two-layer tag guard (computed next tag + hook block on direct `git tag`) beats trusting humans.
- Build both debug and release on every PR — passing debug then breaking release is one merge away.
- Pre-submit vs post-merge do different things. `workflow_call` yaml keeps the contexts from diverging.
- Post-merge failures deserve automatic issues per workflow with auto-close on fix.
- GitHub CI only triggers on PRs targeting protected branches. `--base feature-branch` silently disables CI; changing the base later doesn't retrigger. Always `--base main` for stacks; document merge order in each PR body.
- Skip CI only when the change cannot affect what CI verifies — safe no-op optimization, not a speed trade-off.
- If `--auto` + a push on the same branch can drop commits, block the push at the tool-use layer. Hooks are where anti-footgun rules live.

---

## 10. Release workflow

**Shape:** `./scripts/release-android.sh` and `./scripts/release-firebase.sh` — both compute `<type>/<highest+1>`, require `--confirm-tag` matching the computed tag (cannot override), require `validate.sh` passed on HEAD. Android deploys to Play internal track only, never production. Two changelogs: internal (`CHANGELOG.md`, every version) + public (`distribution/whatsnew/`, minor/major only).

**Exemplars:**
- **#40** — Android release script with tag safety guardrails.
- **#63** — Firebase release mirror (`firebase-deploy.yml`).
- **#337** — two-changelog model.
- **#376** — every `fetchX()` gets three contract tests (always-refresh, null-preserves-cache, exception-doesn't-clobber).

**Lessons:**
- Release script computes the next tag; hook blocks direct `git tag`. Non-negotiable two-layer guard.
- Versioning rule: major = rewrite; minor = user-facing feature added or removed; patch = everything else.
- Keep two changelogs: internal (every version) + public (minor/major only). Mixing them forces choices that serve neither audience.
- After a root-cause fix to invisible framework behavior, keep a "remove the workaround" diff ready and ship it as its own release — the only empirical proof the root cause was complete.

---

## 11. Code style & conventions

**Shape:** Spotless formatting across all Kotlin. Detekt zero-baseline. ADR-008 naming: no `*Impl`, use descriptive prefixes. ADR-009: no bare top-level functions (group in `object {}`, Composables exempt). Generic names over Android-specific (`AppStartup.run()`, not `onActivityCreated()`).

**Exemplars:**
- **#3** — Spotless landed first. Reformat once upfront, not incrementally.
- **#29** — Detekt with zero baseline.
- **#134** — completed `*Impl` rename + ADR-009 `object {}` grouping.
- **#157** — tightened Detekt: `TooGenericExceptionCaught` and `SwallowedException` (allow only `CancellationException`); paired with ADR-011.
- **#217** — a repository interface that wraps exactly one Android API call is a DI shim, not a repository. Inline it.

**Lessons:**
- Formatting lands first. Reformat once upfront is infinitely easier than reformatting incrementally.
- `*Impl` says nothing. Name after what it uses (`Firebase*`, `Room*`, `Network*`) or role (`Default*`, `Fake*`).
- No bare top-level functions. Group in a named `object {}`. Composables exempt.
- Tighten error-handling Detekt rules in pairs with an ADR — lint catches the easy cases; the ADR resolves the hard ones.

---

## 12. Observability & logging

**Exemplars:**
- **#22** — gate `HttpLoggingInterceptor.level` on `BuildConfig.DEBUG`. Release = `Level.NONE` (auth tokens were leaking to Logcat).

**Lessons:**
- Any log that prints HTTP bodies, headers, or auth tokens must be gated on `BuildConfig.DEBUG`. Default-on is a secret-leak waiting for a production build.
- Raw-body logs at network-data-source boundaries for state-critical decodes turn release-only R8 failures from silent-wrong into one `adb logcat` line — add them **before** any suspected R8 bug.

---

## 13. KMP & multiplatform

**Shape:** KMP business logic (common); native UI per platform (SwiftUI on iOS, Compose on Android). `expect fun` for platform factories (HTTP, DB). Import-boundary lint blocks `android.*`/`androidx.*`/`firebase.*` in `commonMain`.

**Exemplars:**
- **#127** — UseCases take repo deps in constructor; request args in `invoke()`. Split keeps UseCases shareable.
- **#128** — UseCase KMP module extracted.
- **#129** — Kermit multiplatform logging (replaced `android.util.Log`; lazy-lambda API).
- **#136** — `ImportBoundaryCheckTask` scanning `commonMain` for forbidden platform packages.
- **#187** — Room 2.7+ is KMP-ready with `@ConstructedBy` + `BundledSQLiteDriver` + per-platform `DatabaseFactory`.
- **#197** — DataStore over SharedPreferences wrappers (reactive reads are the deciding property).
- **#201** — HTTP engine in shared module via `expect fun createPlatformX()`.
- **#202** — goal is SwiftUI on iOS with shared KMP logic, not Compose Multiplatform.

**Lessons:**
- For KMP modules, add a build-time import-boundary lint that bans platform-specific packages. Accidental Android imports compile fine — until iOS.
- Replace `android.util.Log` with Kermit before moving code to `commonMain`. Lazy-lambda API (`Logger.d { ... }`) wins on both KMP-compatibility and concat-performance.
- "Where does the test live" matches "where does the code live" — `commonMain` code gets `commonTest` tests.
- KMP-compatibility alone doesn't pick a library — pick the one whose API shape matches your UI layer. Reactive UI needs reactive settings.

---

## 14. Documentation, ADRs, and memory

**Shape:** `AndroidGarage/docs/DECISIONS.md` holds project ADRs (this-project decisions). `AndroidGarage/docs/guides/` holds portable third-person tech lessons. `MIGRATION.md` tracks phased roadmap with commit hashes. `CLAUDE.md` holds safety rules and workflow invariants. Memory files persist user preferences across sessions.

**Exemplars:**
- **#14** — architecture + ADRs + migration plan + testing doc landed early.
- **#32** — `/dump-context` skill persists session knowledge before context ends.
- **#296 / ADR-017** — numbered test rules with lint enforcement.
- **#374** — postmortem as first-class doc (timeline, invisibility properties, investigation failures, prescriptions).
- **#375** — `docs/guides/` split from ADRs. ADRs = this project; guides = transferable lessons.

**Lessons:**
- Start with ADRs early. A handful at project-start is easier to add to than to retrofit onto implicit decisions.
- Session-level context gets dumped into repo docs (CLAUDE.md, TESTING.md, DECISIONS.md) before a conversation ends — otherwise knowledge evaporates with chat history.
- ADRs record **this project's** decisions; portable third-person guides record **transferable** tech lessons. Separate folders.
- When an ADR lands a structural invariant (who owns which state), write the lint check the same week. ADRs without enforcement decay on the next regression.
- When a production regression disproves a rule, disable the rule and ship the fix fast — but mark the reversal as provisional until the root cause is known. Don't delete the guardrail permanently on one data point.
- When a bug's shape is structural, write the analysis doc **before** the fix. "Here are five candidates, I'm not picking yet" is a valid deliverable.

---

## 15. ESP32 firmware & Firebase server

**ESP32 (FreeRTOS migration, button-token protocol, sensor debouncing):** exemplar patterns are the component split (`garage_hal`, `door_sensors`, `button_token`, `wifi_connector`, `garage_http_client`) and fakes-via-menuconfig for component-level testing without hardware.

**Firebase server (TypeScript, Mocha + Chai):** server-centric business logic is the architectural rule — ESP32 reports raw sensor data; server interprets. Enables feature updates without client changes. Pub/Sub scheduled tasks, event-driven Firestore functions, HTTP endpoints.

**Lessons:**
- Server handles all critical business logic; clients (hardware and mobile) stay simple. This is the ADR that informs every boundary choice downstream.
- In npm projects, pin deps to exact versions (no `^`, no `~`). Floating ranges break silently when a transitive dep changes (#67).

---

## 16. Cross-cutting themes

These apply across multiple themes above; captured here for Phase 4 planning.

**Migration discipline:**
- Ship the lint rule in the same wave as the migration — conventions without enforcement decay one file at a time.
- For long migrations, write a living `MIGRATION.md` with commit hashes. Future sessions need the trail.
- After a library migration, follow up with a PR that **deletes** the old library — unused-but-present gets re-introduced via auto-import.

**Invisible bugs (DI, FCM, R8, auth, Room schema):**
- Stack multiple independent layers — convention, lint, loud-fail test, runtime-identity test, docs, empirical release. No single layer is enough for annotation-shaped invariants.
- "Tests pass" ≠ "architecture works" for framework-contract code. Demand runtime identity tests and generated-code inspection, not just unit tests.
- Features that fail silently in production (push, crash reporters, remote config, release-only R8) need contract tests that pin the exact behavior.

**State architecture:**
- Categorize data as state-y / list-y / event-y before writing a layer rule.
- Repository owns state-y data as `StateFlow` (ADR-022, reversed ADR-013).
- Every extra `stateIn` rewrap on a StateFlow chain is a new place for conflation to drop a signal. Pass the repo's StateFlow by reference.
- First writes to singleton state must come from a scope that can't be cancelled (`externalScope.launch {}` in repo `init`).
- Writes that update shared state always run on `externalScope`, never `viewModelScope`.

**Correctness primitives:**
- `withLock { }` not bare `lock()`/`unlock()`. Throw between the pair strands the mutex.
- `suspendCancellableCoroutine` needs BOTH `onSuccess` and `onFailure` callbacks — missing failure silently hangs forever.
- `fetchX()` always means force-refresh; `.value` reads cache. Never short-circuit fetch on "already cached."
- If a server POST returns the new state in its body, **use** it. Follow-up GET is slower and a race window.
- Coalesce concurrent fetches (mutex, still hits network) and return cached (skip network) are different operations. Review the distinction on every cached-repo refactor.

---

## Inputs Phase 4 should consume first

Phase 4 ("apply the current lessons universally across Android code") should:
1. Start from the **Invisible bugs** and **State architecture** cross-cutting sections above. Those are the highest-leverage.
2. Audit each theme's exemplars — verify the pattern still holds in the current codebase (one `git grep` per rule).
3. Produce a per-module checklist: where does each rule apply, where is it already covered by lint, where is enforcement missing.
4. For any rule not yet enforced by lint or a CLAUDE.md safety rule, decide whether to add enforcement or document the exception.

Protocol for Phase 4 to be designed in a new session via clarifying questions, per the README pattern for Phase 2 and Phase 3.
