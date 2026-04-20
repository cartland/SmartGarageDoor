---
phase: 4
generated: 2026-04-20
source: docs/pr-review/PHASE3_SUMMARY.md
scope: audit only — no code changes, no proposed PRs
---

# Phase 4 audit — Phase 3 rules vs. current enforcement

Maps every still-current rule from `PHASE3_SUMMARY.md` against the repo's existing enforcement artifacts (lint, hooks, ADRs, guides, safety docs, contract tests, scripts). Each theme ends with a gap list. Closes with a ranked top-10 of highest-leverage gaps, which Phase 5 (if we do one) can consume.

**Audit only.** Deciding what to enforce, write, or tolerate is deferred to a later phase.

---

## Inventory

### Custom lint tasks (`AndroidGarage/buildSrc/src/main/kotlin/`)

| Task | What it enforces |
|---|---|
| `architecture/ArchitectureCheckTask` | Module dependency graph (`domain ← data ← usecase`, no cycles) |
| `architecture/SingletonGuardTask` | Critical `@Singleton` providers present (Database, Settings, HttpClient) |
| `architecture/ViewModelStateFlowCheckTask` | Bans `stateIn(viewModelScope, ...)` and VM-local mirrors of state-y types |
| `architecture/LayerImportCheckTask` | ViewModels don't import DataSources; UseCases don't import Bridges |
| `architecture/FakePublicVarCheckTask` | Public `var` banned on `Fake*`/`InMemory*` test doubles |
| `architecture/HardcodedColorCheckTask` | Colors live in theme, not inline `Color(0x...)` |
| `codestyle/NoFullyQualifiedNamesTask` | Bans FQNs in code; forces explicit imports |
| `codestyle/NoNav2ImportsTask` | Bans Navigation 2 imports (Nav3 only) |
| `codestyle/RememberSaveableGuardTask` | Warns on `rememberSaveable` without explicit `Saver` |
| `importboundary/ImportBoundaryCheckTask` | Bans `android.*`, `androidx.*`, `firebase.*` in `commonMain` |
| `testcoverage/TestCoverageCheckTask` | Every ViewModel/Repository has a test file (file-level, not %) |

### Git hooks (`.claude/hooks/`)

| Hook | What it enforces |
|---|---|
| `block-admin-bypass.sh` | Denies `--admin` on `gh pr merge` |
| `check-pr-backlog.sh` | Warns at 5+ open PRs, blocks at 10+ |
| `dev-mode.sh` | Keeps Claude working on parallel PRs when `.claude/.dev-mode` exists |
| `git-guardrails.sh` | Blocks push-to-main, force push, destructive commands, direct `git tag`, push to auto-merge PR; enforces squash-merge; warns on stale validation |
| `warn-shell-loops.sh` | Warns on `for`/`while` (prefers individual Bash calls) |

### ADRs (`AndroidGarage/docs/DECISIONS.md`, 22 total)

Numbered 001–022. Key ones referenced below: 001 (server-centric), 005 (DispatcherProvider), 006 (clean arch + UseCase), 008 (no `*Impl`), 009 (object-scoped fns), 010 (typed APIs), 011 (no-throw error handling), 013 (flow/stateflow boundaries — **superseded by 022**), 014 (FCM arch), 015 (app-scoped Managers), 016 (scope injection), 017 (7 test conventions), 018 (reactive auth), 019 (externalScope side-effects), 020 (release-build hardening), 021 (state ownership + VM scoping), 022 (StateFlow at repo boundary, restored).

### Guides (`AndroidGarage/docs/guides/`)

- `kotlin-inject.md` — scoping, abstract entry points
- `r8-keep-rules.md` — ProGuard keeps for serializable types + Companion
- `compose-nav3-vm-scoping.md` — Nav3 per-entry ViewModelStore; shared state via singletons
- `reactive-auth-listener.md` — platform listener vs. imperative polling
- `repository-api-patterns.md` — observation vs. one-time request; `AppResult<D, E>`

### Top-level safety docs

- **`CLAUDE.md`** — Safety Rules (FCM contract), AppComponent Safety (3 mandatory rules), Room Database Safety (3-layer check), Code Patterns (no bare top-level, no extension on generics, no `*Impl`), Versioning, PR Workflow, Auto-merge race, Docs-only fast path.
- **`AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md`** — kotlin-inject scoping; four defensive layers (convention, lint, identity test, generated code inspection).
- **`AndroidGarage/docs/POSTMORTEM_ANDROID_170.md`** — 5-month singleton regression: timeline, invisibility properties, investigation failures, 7 prescriptions.
- **`AndroidGarage/docs/R8_INSTRUMENTED_TESTS.md`** — `-PtestR8=true -PdebuggableBenchmark=true` harness for reproducing release-only failures.
- **`AndroidGarage/docs/VIEWMODEL_SCOPING_ISSUE.md`** — Nav3 per-entry ViewModelStore multi-instance hazard.

### Detekt (`AndroidGarage/detekt.yml`)

`maxIssues: 0` (zero baseline). Tightened rules: `SwallowedException` (allow only `InterruptedException`, `CancellationException`), `TooGenericExceptionCaught` (disallow `Exception`/`RuntimeException`/`Throwable`), `ForbiddenComment` (bans `FIXME:`, `STOPSHIP:`).

### Contract tests

| Test | Pins |
|---|---|
| `ComponentGraphTest` (androidTest) | DI graph resolution + `*IsSingleton` identity asserts |
| `FcmTopicTest` (commonTest) | FCM topic name format |
| `FcmPayloadParsingTest` (commonTest) | FCM payload keys, types, optionality |
| `FcmMessageHandlerTest` (unit) | FCM payload routing |
| `RoomSchemaTest` (unit) | Room column structure + enum stability |

### Scripts

- `scripts/validate.sh` — Spotless, import boundaries, lint checks, unit tests, schema drift, screenshot compile. Writes marker consumed by git-guardrails hook.
- `scripts/release-android.sh`, `scripts/release-firebase.sh` — Compute next tag, require `--confirm-tag` match, gate on validate + main branch.
- `scripts/generate-android-screenshots.sh` — Sequential shell script (OOM-avoiding).

---

## Per-theme mapping

Conventions: "enforcement" names the specific artifact(s). `none` = no enforcement found. Gap categories: `ok` (fully enforced), `partial: ...` (enforcement exists with hole), `missing: ...` (relies on review/discipline).

### 1. Android architecture

| Rule | Enforcement | Gap |
|---|---|---|
| Clean four-layer KMP modules | lint:ArchitectureCheckTask + ADR-006 | ok |
| Descriptive impl names (`Network*`/`Cached*`/`Firebase*`/`Default*`) | ADR-008 + CLAUDE.md | partial: no lint for positive naming; `*Impl` ban not automated |
| No dependency cycles | lint:ArchitectureCheckTask | ok |
| Move shared models to `domain/`; Room entities map at boundary | ADR-006 + review | missing: no lint |
| DI shim repos (one-API wrappers) should be inlined | ADR-006 lesson | missing: relies on manual audit |

### 2. Dependency injection

| Rule | Enforcement | Gap |
|---|---|---|
| kotlin-inject `@Singleton` via abstract entry points | lint:SingletonGuardTask + CLAUDE.md:AppComponent Safety + manual generated-code read | **partial**: lint only checks 3 critical providers; doesn't verify abstract-entry shape for all singletons |
| Singletons resolve to one instance | contract-test:ComponentGraphTest.*IsSingleton | **partial**: tests exist; not run on every AppComponent change automatically |
| `@Provides fun` takes deps as parameters (no sibling calls) | CLAUDE.md:AppComponent KDoc + review | missing: no lint |
| Inject `DispatcherProvider`, never raw `Dispatchers` | ADR-005 + review | **missing**: no lint |
| Nav3 per-entry ViewModels route state through app-scoped singletons | ADR-021 + lint:ViewModelStateFlowCheckTask (mirror ban) | partial: only the mirror symptom is caught |

### 3. ViewModels & state

| Rule | Enforcement | Gap |
|---|---|---|
| No `stateIn(viewModelScope, ...)`; use MutableStateFlow + `init.collect` | lint:ViewModelStateFlowCheckTask | ok |
| Repo owns state-y `StateFlow`; VM exposes by reference | lint:ViewModelStateFlowCheckTask + ADR-022 | partial: enforcement presupposes singleton DI (see Theme 2) |
| `Channel` for events; `StateFlow` for state; `Flow` for lists | ADR-022 + review | missing: no lint; category discipline manual |
| App-scoped Managers for lifecycle operations | ADR-015 + review | **missing**: no lint |
| Ctor-injected `CoroutineScope` (viewModelScope prod, backgroundScope test) | ADR-016 + review | missing: no lint |
| Split State + Action into independent streams | ADR-021 + review | missing: no lint |
| Extract nav-stack logic from `LaunchedEffect` into pure fn | ADR-021 + review | missing: no lint |
| No delay loops in VM `init` | ADR-016 + review | missing: no lint; documented |
| VM tests use `setMain/resetMain` | ADR-017 rule #1 + review | missing: no lint |
| VM tests `drop(1)` first StateFlow emission | ADR-017 rule #2 + review | missing: no lint |

### 4. Networking

| Rule | Enforcement | Gap |
|---|---|---|
| `NetworkResult<T>` sealed at HTTP boundary | ADR-010 + review | missing: no lint |
| HTTP types stay inside data-source impl | lint:LayerImportCheckTask + ADR-010 | partial: import check helps; no structural swap-safety check |
| Contract tests pin wire format | contract-test:FcmPayloadParsingTest, FcmTopicTest | **partial**: tests exist; not pre-merge-gated for push-changing PRs |
| Raw-body logs at decode site (for R8 diagnosis) | ADR-020 + review | missing: no lint |
| Never change FCM topic/payload without approval | CLAUDE.md:Safety Rules + contract tests | partial: contract tests catch; no explicit pre-commit gate |

### 5. Auth

| Rule | Enforcement | Gap |
|---|---|---|
| `AuthBridge` behind interface | ADR-018 + review | missing: no lint |
| Auth state as reactive stream; fire-and-forget writes | ADR-018 + review | missing: no lint |
| Google One-Tap in Compose; VM sees `GoogleIdToken` only | ADR-018 + review | missing: no lint detecting `Activity`/`Intent` in VM |
| `suspendCancellableCoroutine` needs BOTH success + failure callbacks | ADR-018 lesson + review | **missing**: no lint |

### 6. FCM & push notifications

| Rule | Enforcement | Gap |
|---|---|---|
| Contract tests pin topic + payload | contract-test:FcmTopicTest, FcmPayloadParsingTest | ok (tests exist) |
| Registration app-scoped via `FcmRegistrationManager`, idempotent | ADR-014 + review | missing: no lint |
| Retry on externalScope | ADR-014 + review | missing: no lint |
| Never change topic/payload without approval | CLAUDE.md:Safety Rules | partial: no pre-commit gate tying changes to contract test execution |

### 7. Room & local storage

| Rule | Enforcement | Gap |
|---|---|---|
| Schema-drift three-layer guard | validate.sh + hook:git-guardrails + contract-test:RoomSchemaTest | ok |
| Version increment on every schema change | CLAUDE.md:Room Safety instruction | **missing**: no hook enforcement; relies on discipline |
| DAO single-row queries return `Flow<Entity?>` or `suspend fun get(): Entity?` | review | missing: no lint |
| `rememberSaveable` with explicit `Saver` | lint:RememberSaveableGuardTask + ADR-017 | ok |

### 8. Testing philosophy & infrastructure

| Rule | Enforcement | Gap |
|---|---|---|
| Fakes over mocks; zero Mockito | review | **missing**: no lint banning `org.mockito.*` imports |
| Two fake boundaries (UseCase tests fake repo; data tests fake data source) | CLAUDE.md:Testing + review | missing: no structural check |
| Every VM/Repo has a test file | lint:TestCoverageCheckTask | ok |
| `test-common` KMP module hosts shared fakes | ADR-003 + review | missing: no structural lint |
| Preview Composables deterministic (no `Clock.System.now()`, `Random`) | validate.sh (screenshot compile) + review | missing: no lint detecting non-determinism |

### 9. CI, hooks, and guardrails

| Rule | Enforcement | Gap |
|---|---|---|
| Stop hooks for workflow invariants | hook:check-pr-backlog, dev-mode | ok |
| Block push to main, force push, enforce squash-merge | hook:git-guardrails | ok |
| Block direct `git tag` (release script computes) | hook:git-guardrails | ok |
| Pre-submit and post-merge CI, auto-issues on post-merge failure | GitHub Actions | ok (documented, not locally-verifiable) |
| Docs-only fast path | CLAUDE.md + GitHub Actions path filter | ok |
| Stacked PRs always `--base main` | CLAUDE.md | missing: no hook |
| Block push to PR with auto-merge enabled | hook:git-guardrails | ok |

### 10. Release workflow

| Rule | Enforcement | Gap |
|---|---|---|
| Release script computes next tag; hook blocks direct tagging | script:release-android.sh + hook:git-guardrails | ok |
| `--confirm-tag` must match computed tag | script:release-android.sh | ok |
| Validate.sh required before release | script:release-android.sh | ok |
| Play internal track only (never production) | script documentation | partial: documented, not enforced by script body |
| Two changelogs: internal (CHANGELOG.md, every version) + public (`distribution/whatsnew/`, minor/major) | review | missing: no lint enforcing changelog update on version bump |
| Versioning rule (major/minor/patch) | CLAUDE.md | missing: no lint |

### 11. Code style & conventions

| Rule | Enforcement | Gap |
|---|---|---|
| Spotless formatting | validate.sh | ok |
| Detekt zero-baseline | validate.sh + detekt.yml | ok |
| No `*Impl` suffix | ADR-008 + review | **missing**: no lint |
| No bare top-level functions (Composables exempt) | ADR-009 + review | **missing**: no lint |
| Generic app-scoped naming (`AppStartup.run()`, not `onActivityCreated()`) | CLAUDE.md | missing: no lint |
| No FQNs in code | lint:NoFullyQualifiedNamesTask | ok |
| No Nav2 imports | lint:NoNav2ImportsTask | ok |

### 12. Observability & logging

| Rule | Enforcement | Gap |
|---|---|---|
| HttpLoggingInterceptor gated on `BuildConfig.DEBUG` | review | **missing**: no lint |
| Raw-body logs at decode sites for state-critical decodes | ADR-020 + guide:r8-keep-rules.md | missing: no lint asserting diagnostic logs exist |

### 13. KMP & multiplatform

| Rule | Enforcement | Gap |
|---|---|---|
| Ban `android.*`/`androidx.*`/`firebase.*` in `commonMain` | lint:ImportBoundaryCheckTask | ok |
| UseCase patterns: deps in ctor, request args in `invoke()` | ADR-006 + lint:LayerImportCheckTask | partial: imports checked; no structural verification |
| `expect fun` for platform factories (HTTP, DB) | compiler errors | missing: no lint — discovered only at compile |
| Replace `android.util.Log` with Kermit | lint:ImportBoundaryCheckTask (bans `android.*`) | ok (transitive) |

### 14. Documentation, ADRs, and memory

| Rule | Enforcement | Gap |
|---|---|---|
| `DECISIONS.md` for project ADRs | file exists + CLAUDE.md | ok |
| `guides/` for portable tech lessons | file exists | ok |
| `MIGRATION.md` tracks phases + commit hashes | CLAUDE.md | missing: no lint for updates |
| `CLAUDE.md` holds safety rules | file exists | ok |
| Memory files persist session knowledge | `/dump-context` skill | missing: no automated capture |
| ADR with structural invariant gets lint same week | ADR-008/017/022 paired | **partial**: ADR-009, ADR-015, ADR-018 landed without lint |

### 15. ESP32 firmware & Firebase server

| Rule | Enforcement | Gap |
|---|---|---|
| Server-centric business logic (ESP32 reports raw; server interprets) | CLAUDE.md + ADR-001 | ok (architectural) |
| Component split (garage_hal, door_sensors, button_token, wifi_connector, garage_http_client) | code structure | ok (structural) |
| Fakes via menuconfig for component tests | code review | ok (structural) |
| npm deps pinned to exact versions | review + package-lock.json | **missing**: no lint on `package.json` ranges |

### 16. Cross-cutting themes

| Rule | Enforcement | Gap |
|---|---|---|
| Ship lint same wave as migration | paired ADR + lint (newer ADRs) | partial: ADR-009/015/018 landed without lint (Theme 14 dupe) |
| Long migrations: living `MIGRATION.md` with hashes | CLAUDE.md | missing (Theme 14 dupe) |
| Multi-layer invisible-bug defense (convention, lint, loud-fail test, identity test, docs, release) | android/170 five-layer pattern | partial: generated-code inspection step is manual |
| Runtime identity tests for DI/dedup | contract-test:ComponentGraphTest | ok (exists; not auto-required on AppComponent edits) |
| Contract tests pin exact behavior (not integration) | FcmTopicTest, FcmPayloadParsingTest, RoomSchemaTest | ok |
| Categorize state-y / list-y / event-y before layer rule | ADR-022 + review | missing: no lint |
| `withLock { }` not bare `lock()`/`unlock()` | review | **missing**: no lint |
| `suspendCancellableCoroutine` needs BOTH callbacks | review | **missing**: no lint (Theme 5 dupe) |
| `fetchX()` always force-refresh; `.value` reads cache | review | **missing**: no lint |
| Server POST returns new state → use it | review | missing: no lint |
| Distinguish coalesce-in-flight (mutex) vs. return-cached (skip network) | review | missing: no lint |

---

## Highest-leverage gaps (top 10)

Ranked by (impact × probability of regression). Each gap is one candidate for Phase 5 if we do enforcement work.

| # | Gap | Theme | Impact | P(regression) | Why it's at the top |
|---|---|---|---|---|---|
| 1 | **Generated-code inspection step for `AppComponent` edits** | 2 | Catastrophic (silent multi-instance DI) | Very high | kotlin-inject `@Singleton` is annotation-shaped; 5-month regression proves no current layer catches it automatically. Identity tests require manual invocation. |
| 2 | **Auto-run `ComponentGraphTest` on AppComponent changes** | 2 | Catastrophic | High | Tests exist but need a connected device and aren't in `validate.sh`. A guard that detects `AppComponent.kt` edits and requires `ComponentGraphTest` to have passed on HEAD would seal the main hole. |
| 3 | **FCM contract-test pre-merge gate** | 6 | High (silent feature failure, no error) | Medium-high | `FcmTopicTest` + `FcmPayloadParsingTest` exist but aren't tied to a must-pass CI job that specifically fires on FCM-touching PRs. |
| 4 | **DispatcherProvider injection lint** | 2, 3 | High (untestable VMs, flaky timing-dependent tests) | High | ADR-005 documents the rule; no lint. Every new VM is a chance to hardcode `Dispatchers.IO`. |
| 5 | **Bare top-level function lint (ADR-009)** | 11 | Medium (namespace pollution, KMP API-surface leak) | Medium | Rule is ADR-009; `NoFullyQualifiedNamesTask` targets FQNs, not bare-function placement. A file-scan rule would close it. |
| 6 | **App-scoped Manager pattern lint (ADR-015)** | 3, 6 | Medium-high (lifecycle bugs, duplicate work, resource leaks) | Medium | No static check prevents a new VM from owning what belongs in a Manager. The FCM registration regression shape would repeat. |
| 7 | **Release-build R8 instrumented test run** | 4, 12 | High (release-only silent-wrong behavior) | Medium | `R8_INSTRUMENTED_TESTS.md` harness exists but `validate.sh` doesn't invoke it. Changes to `@Serializable`, `@kotlinx.serialization`, or ProGuard rules land without a release-minified sanity check. |
| 8 | **`*Impl` suffix lint (ADR-008)** | 1, 11 | Low-medium (convention drift) | Medium | Long-term rule; new contributors and auto-generated code keep tempting the suffix back. A simple class-name scan would hold it. |
| 9 | **`withLock { }` mandate lint (correctness primitive)** | 16 | High (process-life mutex strand) | Low-medium | Bare `mutex.lock()`/`unlock()` is a silent correctness bug; flagged in user memory as a recurring concern. Code-review-only. |
| 10 | **Mockito import ban (ADR-003)** | 8 | Low-medium (pattern adherence) | Low-medium | Zero-Mockito policy achieved; a lint would stop new tests from re-introducing it via auto-import. |

### Honorable mentions (rank 11–15)

- Activity/Intent/Context in VM constructors or parameters (Theme 5, 3).
- Logging guard on HTTP interceptors (Theme 12).
- Exact-version pin lint for `FirebaseServer/package.json` (Theme 15).
- Changelog-update-on-version-bump lint (Theme 10).
- `stateIn` chain depth lint — warns on 3+ rewrap layers (Theme 3, 16).

---

## Phase 5 hand-off

If a Phase 5 is opened, each top-10 gap is one candidate scope. Suggested first cut:

1. Start with **gaps 1–3** — the ones that cost us the android/170 regression and the FCM silent-failure class. These are the ones where the Phase 2 corpus carries explicit bug stories.
2. Gaps 4–6 are DI/state hygiene — most likely to pay off during the iOS migration when shared-scope bugs can't be caught by Android-only instrumentation.
3. Gaps 7–10 are convention enforcement — lower leverage individually, but cheap to build as small lint tasks and useful as consistency signals.

Protocol for Phase 5 to be designed in a new session via clarifying questions, per the pattern established in Phases 2/3/4.
