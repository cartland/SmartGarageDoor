---
category: reference
status: active
last_verified: 2026-06-29
---

# Dependency Upgrades — Sequencing Playbook

How to land a multi-PR dependency upgrade safely: GitHub Actions
versions, npm runtime/dev deps, Dependabot alerts, transitive overrides.
The 5-PR sweep on 2026-04-27 (PRs #581–#585, closed all 5 Dependabot
alerts and 2 of 3 Node-20 deprecations) is the canonical exemplar.

## Dependabot auto-update posture (2026-06-29)

`.github/dependabot.yml` is configured **minor/patch only** — major version
updates are `ignore`d on purpose. The first run opened an 11-PR flood including
several majors (agp 8→9, spotless 7→8, firebase-functions 6→7, firebase-admin
13→14, compose-bom, action majors), each of which needs this playbook, not a
drop-in auto-PR. **Take majors by hand** when you choose; let Dependabot handle
the safe minor/patch churn (grouped per ecosystem, `open-pull-requests-limit: 3`).

**The Kotlin toolchain is ignored even at semver-minor.** Dependabot's
semver classification is too coarse for a Kotlin project: a "minor" bump of the
Kotlin Gradle Plugin can remove a build DSL. The first gradle group PR (#1004,
58 grouped updates) bumped **Kotlin 2.1.20 → 2.4.0**, and 2.4.0 turned the
deprecated `jvmTarget: String` DSL into a hard **error** — `:buildSrc:compileKotlin`
failed, which failed every Android job uniformly, making the whole auto-group
dead-on-arrival. So the gradle ecosystem `ignore`s the Bucket-C toolchain
outright (`org.jetbrains.kotlin*`, `com.google.devtools.ksp*`, AGP plugin
markers, `androidx.room:*`, `me.tatarka.inject:*`, `co.touchlab.skie*`,
`org.jetbrains.kotlinx:kotlinx-serialization*`). These move by hand, serial,
with a real `android/N` release as proof; their version lockstep (Kotlin ↔ KSP ↔
SKIE, plus the kotlin-inject 0.8.0 pin) is exactly why they can't auto-group.
Dependabot still auto-handles the safe libs (androidx, compose-bom, coroutines,
kermit, datastore, lifecycle, detekt, spotless, mockito, etc.). A wrong ignore
pattern is non-destructive — it only affects Dependabot's own PRs, never `main`
— so refine reactively if a future group still drags a toolchain dep in.

**Why Android-side Dependabot PRs need CI help:** GitHub withholds repo Actions
secrets from Dependabot-triggered runs, so `setup-android`'s `google-services.json`
decrypt no-ops and **every Android job fails at `processGoogleServices`** — a
secret artifact, unrelated to the dependency change (this is why the first run's
gradle/actions PRs were all red). The fix (`.github/actions/setup-android/action.yml`
+ `.github/workflows/ci-checks.yml`): a committed placeholder `google-services.json`
is dropped in when there's no encrypt key, and the **Build Release AAB** job (which
also needs the real signing keystore) is skipped on Dependabot runs — the
`if: always()` "Android CI Complete" gate tolerates the skip. npm PRs are
unaffected (Firebase CI needs no Android secrets).

## Hard rules (every PR must satisfy)

- **Each PR is safe to release standalone.** Main must be tag-and-deployable
  at every commit. No "interim broken state" PRs that depend on a follow-up
  to be safe.
- **Each PR is auto-merge-eligible.** Narrow scope, passes CI, no manual
  ops gates.
- **Risk is staged.** Low-risk first; risky changes isolated so a failure
  can be reverted without blocking unrelated work.
- **Empty-backlog windows are leverage.** When zero PRs are open you can
  parallelize aggressively; when several are in flight, serialize anything
  that touches the same file (especially `package-lock.json`).

## The four buckets

Every upgrade slots into one of four buckets. Order across buckets is
A → B → C → D; parallelism within is described per bucket.

### Bucket A — Docs / CI-config-only
Markdown, `.claude/`, comments. Hits the docs-only fast path; pre-submit
gates skip the heavy pipeline. **Rollback:** revert; impact zero.
**Parallelism:** fully parallel with everything else.

### Bucket B — Pre-submit-only surfaces
Actions used *only* in pre-submit workflows (`ci.yml`, `firebase-ci*.yml`,
etc.), plus FirebaseServer **devDependencies**, plus Android test-only
deps. These cannot reach the deploy path.
**Failure manifests as:** red CI on the PR or post-merge.
**Rollback:** revert.
**Parallelism:** parallel within itself, parallel with A.

### Bucket C — Shared / runtime-touching but not deploy-only
AGP, Kotlin, kotlin-inject, FirebaseServer runtime **dependencies**,
Gradle wrapper, JDK in `setup-java`. Goes into the artifact users
actually run.
**Catch:** green pre-submit is necessary but not sufficient. Required:
the next `android/N` or `server/N` tag-push must be the production proof
**before** the next C-bucket PR queues.
**Rollback:** revert + cut a fresh `android/N+1` / `server/N+1` if the
bad version was tagged.
**Parallelism:** serial within C (one runtime bump at a time so a
regression bisects unambiguously). Parallel with A and B.

### Bucket D — Deploy-pipeline surfaces
`firebase-deploy.yml`, `release-android.yml`, `google-github-actions/auth`,
`setup-node` *inside the deploy job*, any Node-engine bump.
**Catch:** green pre-submit proves nothing about deploy. Required: push
a real `server/N` or `android/N` tag and inspect logs for
`✔ Deploy complete!` (the silent-failure signature from
[`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md)). Auto-merge can
still land the PR; the gate is on the *next bump*, not the merge itself.
**Parallelism:** strictly serial within D, and serial with C on the same
surface.

## Batching principle

Default: **one logical bump per PR.** Batch only when *all three* hold:
- (a) Same surface (e.g. several GH Actions, or several FirebaseServer
  transitive npm patches)
- (b) Minor/patch only — no major version, no Node-engine change, no
  peer-dep ripple
- (c) Failure mode is shared and bisectable from one revert

So: a clutch of `actions/checkout` patch bumps can ride together. Major
action bumps, anything that touches `firebase-deploy.yml`, AGP, Kotlin,
or kotlin-inject each get their own PR.

## Per-class risk register

### GH Action minor/patch bumps
- Default flag flip changes behaviour silently (e.g. `actions/checkout`
  `fetch-depth` defaulting). Catch: post-merge run on `main` auto-opens
  a `ci-failure/*` issue.
- Cache-key format change invalidates cache; CI passes but slows 5×.
  Catch: cache-hit ratio in run summary.

### GH Action major bumps
Adds the minor risks plus:
- Removed inputs / changed output names break composite calls. Catch:
  required gate jobs fail loudly.
- Internal Node runtime bump (e.g. Node 16 → 20). Surfaces as `EBADENGINE`
  or native-module mismatch.
- Permission-model change (`GITHUB_TOKEN` scope tightened). Audit
  `firebase-deploy.yml`'s `verify-ci` step which depends on the exact
  check-run name `Firebase Checks / Unit Tests`.

### `r0adkll/upload-google-play` (special case)
- Track / status semantics change — release is "uploaded" but invisible
  on internal track. Mirrors the firebase silent-failure shape. Catch:
  affirmative success marker — assert the Play Console API echo
  (versionCode + track), not just the action's exit code.
- Service-account scope drift. Catch: dry-run on a throwaway versionCode.

### Firebase npm runtime deps
- **Loud:** breaking type/API change in `firebase-functions` v5/v6 — `tsc`
  fails in `firebase-npm.sh run build`.
- **Silent:** `firebase-admin` SDK changes Firestore query semantics
  (e.g. `Timestamp` vs `Date` coercion) — handler tests pass with fakes
  but production behaves differently. Catch: the
  `httpServerConfigUpdate` / `buildTimestamp` config-authority tests
  ([FIREBASE_CONFIG_AUTHORITY.md](FIREBASE_CONFIG_AUTHORITY.md)).
- **Silent:** FCM payload-shape regression. Catch: `FcmPayloadParsingTest`
  + `FcmTopicTest` contract tests.

### Firebase devDeps / test-side
- Mocha glob handler change re-introduces the silent-skip bug (CLAUDE.md
  "single-quoted glob"). Assert test count doesn't drop after upgrade.
- ts-node loader change re-triggers `admin.initializeApp is not a
  function` — verify `NODE_OPTIONS='--no-experimental-strip-types'`
  still wins.

### Android Gradle / AGP / Kotlin
- Pre-submit catches: ktlint, lint, unit tests, `assembleDebug`,
  `checkSingletonCaching`, `checkScreenViewModelCardinality`, Room schema
  drift.
- Pre-submit MISSES: R8/proguard regressions (debug builds skip R8).
  Kotlin bump can change `@Serializable` codegen — silent NPE in release.
  Catch: manual `assembleRelease` smoke + log raw JSON body before
  deserialization (ADR-020).
- Pre-submit MISSES: kotlin-inject KSP regressions — `@Singleton` stops
  scoping. Catch: `ComponentGraphTest` `assertSame` identity tests.

### Wire-contract-touching bumps (highest risk)
- kotlinx.serialization / ktor-client-core defaults — silent JSON shape
  drift. Both `wire-contracts/` strict-mode tests must pass on server +
  Android.
- Never ship a wire-contract change one-sided.

## Operational gotchas (learned the hard way)

### Dependabot alert summaries can lie
The human-readable summary often quotes a narrow version list, but the
formal `vulnerable_version_range` may be much broader. Example: alert
#67 said *"uuid: missing buffer bounds check in v3/v5/v6"* but the formal
range was `< 14.0.0` — every uuid below 14 was flagged. Always check
the formal range:

```bash
gh api repos/cartland/SmartGarageDoor/dependabot/alerts/<n> \
  --jq '.security_advisory.vulnerabilities[].vulnerable_version_range'
```

### `overrides` vs. exact-pinned direct deps ⇒ `EOVERRIDE`
If a top-level dep is pinned exact (e.g. `"uuid": "14.0.0"`) and you add
an override with a caret range (`"uuid": "^14.0.0"`), `npm install` fails
with `npm error code EOVERRIDE — Override for uuid@14.0.0 conflicts with
direct dependency`. Fix: relax the direct dep to caret too, or make both
exact.

### Stacked PRs on shared `package-lock.json`
When two PRs both regenerate the lockfile, branch the second PR from the
first's branch with `--base main`. After the parent merges, GitHub
auto-collapses the diff to just the second PR's own changes. See
CLAUDE.md § Stacked PRs for the full pattern.

### Transient `npm install` ECONNRESET
Real flake — happens occasionally on the GitHub-hosted runner. Recovery:
`gh run rerun <run-id> --failed`. Don't mask post-merge flakes; do retry
pre-merge ones once.

## Stop conditions

Pause the queue if any fire:
- Post-merge CI failure on the just-landed bump (don't retry-mask).
- A Dependabot alert claimed-resolved by a merged PR still appears open.
- Firebase deploy "succeeded" without `✔ Deploy complete!` in the log,
  or with any `⚠ failed to update function` line — silent-failure
  regression signal.
- Validate scripts emit new warnings post-release.
- A C/D-bucket bump where no release has shipped yet — never stack two
  unverified runtime bumps.

## Active decisions

### Project Node runtime stays on **Node 22** (as of 2026-04-27)

The 2026-06-02 GitHub Actions cutover forces *Action internals* to Node
24, not the project. `FirebaseServer/.nvmrc` and `engines.node` remain
`22`. Cloud Functions Node 22 is supported through 2027-04. **Tripwire
to revisit:** Firebase Functions release notes flagging Node 24 as
battle-tested for 2nd-gen functions, AND a concrete reason to move
(feature, perf, security). Don't migrate just because we *can* —
re-introducing silent-deploy-failure surface costs more than it gains
on a solo project. See discussion: PRs #581–#585.

### `r0adkll/upload-google-play` deferred
No Node-24 release upstream as of 2026-04-27 ([upstream issue #256](https://github.com/r0adkll/upload-google-play/issues/256)).
Recheck mid-May 2026 before the 2026-06-02 forced-Node-24 cutover.
