---
category: reference
status: active
last_verified: 2026-06-12
---

# Release & Deployment Strategy

**A layered blueprint for shipping software safely — across components, targets, and
platforms — with one vocabulary and rules that compose.**

This document is the *why* behind how releases work here. It describes a **target
state**: what good release management looks like, independent of any one script or
workflow. It is a reusable north star — the general layers apply to any repository;
the platform layers apply when you use that platform; and one repository-specific
section (§9) shows how **this** repo composes them today, with a conformance audit.

For the operational *how* — exact commands, troubleshooting, GCP setup — see the
runbooks this document links to (chiefly [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md)
and the release-procedure sections of [`../CLAUDE.md`](../CLAUDE.md)). Per the
documentation contract ([`AGENTS.md`](AGENTS.md)), this doc owns the *principles*; the
runbooks own the *facts*. Where the two would overlap, this doc links rather than
restates.

---

## How to read this document

Rules use RFC-2119 keywords:

- **MUST** / **MUST NOT** — non-negotiable. A violation is a defect.
- **SHOULD** / **SHOULD NOT** — strong default. Deviate only with a recorded reason.
- **CAN** — explicitly permitted. Choose per situation.

The document is **layered and additive**. The rules in force for any single release
are the union of four layers:

```
Foundations  ∪  { Server | App }  ∪  { Firebase | Android | iOS | Web }  ∪  { repository }
   (always)        target type            platform                          local policy
  ── general ──────────────────────────────────────────────────────────► specific ──
```

These axes **compose**. A mobile app distributed through a store, talking to a backend
hosted on Firebase, inherits Foundations + App + (Android or iOS) + Firebase + that
repository's section simultaneously.

A more specific layer **MAY tighten** a looser rule — turn a SHOULD into a MUST, add a
gate — but **MUST NOT relax** a stricter one. **Strictness only increases as you
descend.** The general layers are the floor; specific layers raise the bar.

---

## Canonical vocabulary

One set of words, used precisely. Loose language ("ship it", "push to prod") is where
mistakes begin.

| Term | Meaning |
|---|---|
| **Build** | Produce an artifact from source. Deterministic and side-effect-free. |
| **Release** | Mark one specific commit as a shippable candidate by creating an immutable **release tag**. A release does not, by itself, touch any running system. |
| **Deploy** | Place a released artifact into a running **environment**. |
| **Environment** / **channel** | A live target audience — e.g. `staging` and `production` for a service; a test track and a public track for an app. |
| **Promote** | Deploy the *exact, already-tested commit* from a lower channel into a higher one. A promotion never rebuilds from a different source. |
| **Rollback** | Re-deploy a prior, known-healthy release — a deliberate backward move. |
| **Gate** | An automated check that blocks a release or deploy until satisfied. |
| **Override** | An explicit, audited bypass of a *gate*. An override always names a **value from reality** (a commit SHA, a tag) and fails if that value does not match. |
| **Anchor** | The known-healthy source a higher-risk deploy is bound to (e.g. production is deployed *from* a verified staging release). |
| **Smoke** | A minimal post-deploy check confirming the live target actually serves the new revision. |
| **Deploy record** | The deployed system's own truthful report of which commit it is running. |

---

## 1. Foundations — every repository, every release

### MUST

- **F1 — Tagged, automated path.** A deploy to any shared environment MUST be initiated
  by creating and pushing an immutable release tag, which an automated pipeline acts on.
  The normal path is never a human deploying by hand from a workstation.
- **F2 — Immutable, monotonic tags.** A release tag MUST be immutable once published and
  MUST carry a strictly increasing counter. Release versions are sequence numbers, not
  semantic versions — a continuously delivered service or app is not a pinned, published
  artifact. (Track a separate human-facing version only if a consumer needs one.)
- **F3 — Only mainline ships.** Only a commit that is part of the trunk's history is
  releasable; that is what guarantees it passed the required pre-merge checks. The deploy
  pipeline MUST re-assert this **server-side** and refuse anything else — it does not
  trust the person who cut the tag.
- **F4 — Preview before commitment.** The release tool MUST offer a no-side-effect
  preview that prints the current state and the *exact* next command with real values
  filled in. Operators copy that command; they MUST NOT hand-type tags or commit
  identities from memory.
- **F5 — Confirmation is an assertion, not a selector.** Where the operator restates the
  intended tag, it MUST equal the independently computed next tag, or the release is
  refused. Restating the tag can never *choose* a different one.
- **F6 — Overrides are grounded in reality.** Any gate bypass MUST require the specific
  commit identity (or tag) it applies to and MUST fail if that value does not match.
  Correct use is easy (the value is in the preview); accidental use is hard.
- **F7 — Gates are overridable; invariants are not.** Every blocking *check* (CI status,
  validation, release notes, smoke) MUST offer a grounded override (F6), so a wrong or
  flaky gate can never wedge an emergency. Structural *invariants* — mainline-only (F3),
  immutable tags (F2), production-anchored (S2) — MUST NOT be overridable: their safe
  escape is the forward path (merge → tag → promote), which MUST always be available.
  **There is always a forward-safe path; there is never a shortcut through an invariant.**
- **F8 — Truthful deploy record.** A deployed system MUST be able to report the exact
  commit it is running, so "what is live?" is answerable without guessing.
- **F9 — Recoverable failures.** A failed release MUST leave no partial state (e.g. a
  dangling local tag). A failed deploy MUST surface as a failure, never a silent success.

### SHOULD

- **F10** — The release tool SHOULD support a dry run that executes every gate but creates
  nothing.
- **F11** — Deploy failures SHOULD open or update a single, deduplicated tracking record
  that resolves automatically on the next success.
- **F12** — Each release SHOULD have human-readable notes keyed to its tag, written
  *before* the release rather than reconstructed afterward.
- **F13** — Deploy credentials SHOULD be keyless and short-lived (federated identity), not
  long-lived secrets stored in the project.
- **F14** — The deployed workload SHOULD run under a dedicated, least-privilege identity
  distinct from the identity that performed the deploy.

### CAN

- **F-CAN1** — A repository CAN provide a hand-runnable deploy path for break-glass use,
  kept clearly separate from the normal tag-driven path.
- **F-CAN2** — A repository CAN enforce the tag-driven discipline with a guard that blocks
  ad-hoc tag pushes while permitting the sanctioned release tool.

### MUST NOT

- **F-NOT1** — MUST NOT deploy a commit that is not on the trunk.
- **F-NOT2** — MUST NOT reuse, move, or rewrite a published release tag.

---

## 2. Servers — services, backends, functions

*Inherits Foundations.*

### MUST

- **S1 — Separate channels for code under test.** A service SHOULD maintain a `staging`
  channel and a `production` channel, so code reaches production only after living on
  staging. A **single-environment** service (everything goes direct to production) is an
  accepted archetype for low-stakes or solo-maintained services — but it MUST be recorded
  as a deviation (§9) with a revisit trigger, because it forfeits S2/S3 below. A service
  carrying real user data or uptime commitments tightens this SHOULD to a MUST.
- **S2 — Production is anchored.** *When a service has more than one channel,* a
  production deploy MUST be bound to a known-healthy source: a promotion from a verified
  staging release, a rollback to a prior healthy production release, or an explicit
  override. Sending trunk-tip straight to production unverified is refused by design.
- **S3 — Promotion ships the identical commit.** A promotion MUST deploy the exact commit
  the source channel verified, and MUST confirm the source's own deploy concluded
  successfully before allowing it. No rebuild from a divergent source.
- **S4 — Self-verifying deploys.** A deploy MUST fail if the live target does not serve
  afterward, and SHOULD confirm the deploy record reports the exact commit just shipped —
  catching "the pipeline reported success but the running code did not change." (This
  failure mode is common and quiet; treat the post-deploy smoke as part of the deploy, not
  a manual afterthought.)
- **S5 — Data changes are migrations, not edits.** A change to persisted data shape MUST
  follow a versioned migration path (expand → migrate → contract) and MUST NOT be treated
  as an internal refactor. Persisted data is a boundary.

### SHOULD

- **S6** — `staging` SHOULD be permissive; its purpose is to receive untested code. Gates
  concentrate on the staging-to-production boundary.
- **S7** — A named rollback path SHOULD exist (backward-only, verified against history) so
  reverting is a first-class operation, not an improvised override.
- **S8** — Production promotion SHOULD be gated on the target commit's deeper
  (post-merge / integration) check status, and that gate SHOULD be waived automatically
  for a rollback — the prior successful deploy is itself the health signal.

### CAN

- **S-CAN1** — Staging CAN deploy automatically on merge to trunk once its test suite is
  trusted; until then it stays tag-driven.
- **S-CAN2** — A service CAN layer richer post-deploy health checks (dependency probes,
  synthetic transactions) beyond basic smoke.

---

## 3. Apps — software delivered to end users

*Inherits Foundations.*

### MUST

- **A1 — Never auto-publish to users.** The pipeline MUST publish only to a pre-production
  channel (an internal/test track, a preview surface). Promotion to the production,
  user-facing channel MUST be a deliberate human action. This is the app analog of the
  server's production-anchor rule.
- **A2 — Release notes are mandatory.** A release MUST carry user-facing notes for the
  version being shipped; releasing without them requires an explicit, grounded override.
- **A3 — Monotonic, tag-derived versioning.** The user-visible version / build identifier
  MUST derive from the release tag's counter and MUST never be hand-adjusted backward.

### SHOULD

- **A4** — A local validation pass mirroring CI SHOULD run before release, recording the
  commit it validated; the release SHOULD refuse if that record is missing or does not
  match the commit being released.
- **A5** — Release-note length SHOULD be checked automatically against the destination
  channel's limits.

### CAN

- **A-CAN1** — A temporary, shareable preview build CAN be produced for review ahead of any
  tagged release.
- **A-CAN2** — A staged/phased rollout CAN govern the production channel, managed at the
  distribution platform after promotion.

---

## 4. Firebase — platform layer

*Inherits Foundations + (Server or App).*

### MUST

- **FB1 — One project per environment.** Each channel MUST map to its own isolated
  platform project. Two channels MUST NOT share one project.
- **FB2 — Scoped deploys only.** Every deploy MUST name exactly the surfaces it changes
  (functions, data rules, hosting, …). A bare deploy-everything is forbidden.
- **FB3 — Rules and code ship together.** Data-access rules and indexes MUST deploy
  atomically with the code that depends on them, and access MUST default to deny. (A
  function-only deploy that leaves a dependent rules/index change behind is a defect — it
  produces a window where the live code and the live rules disagree.)
- **FB4 — Schema is versioned and validated.** Persisted documents MUST carry a schema
  version and MUST be validated at the I/O boundary; shape changes follow the migration
  playbook (S5 made concrete).

### SHOULD

- **FB5** — The deploy identity SHOULD use keyless federated auth rather than a stored key
  file (F13 applied).
- **FB6** — Each runtime constraint a local fake cannot reproduce (storage-engine
  restrictions, deny-all rules, document limits) SHOULD be covered by at least one
  integration test exercising the real primitive — one path per boundary, not an
  enumeration of every case.
- **FB7** — The deploy record SHOULD be written at deploy time alongside the running code
  so the live system can report it truthfully (satisfying S4 / F8).

### CAN

- **FB-CAN1** — One hosting project CAN serve multiple bundles behind distinct path
  prefixes.
- **FB-CAN2** — Build-artifact retention CAN be capped on each deploy to bound cost.

---

## 5. Android — platform layer

*Inherits Foundations + App.*

### MUST

- **AN1 — Internal track from automation only.** The pipeline MUST publish to the
  internal/test track only. Production promotion is a manual action at the distribution
  console (A1 made concrete).
- **AN2 — Signing material is protected.** Signing secrets (keystore, key passwords) MUST
  be encrypted at rest or injected at build time, never stored in plaintext and never
  logged. Any decrypted material MUST be removed after the build.
- **AN3 — Monotonic version code.** The version code MUST derive monotonically from the
  release tag's counter.

### SHOULD

- **AN4** — A pre-release validation pass (build + unit + instrumented as feasible) SHOULD
  record the validated commit, and the release SHOULD refuse on a missing or stale record.
- **AN5** — "What's new" text SHOULD be length-checked against store limits.
- **AN6** — Rendered UI screenshots SHOULD be kept as a reviewable visual record, not used
  as a pixel-exact pass/fail gate (cross-host rendering is not reproducible).

### CAN

- **AN-CAN1** — A phased production rollout CAN be managed at the console after promotion.

---

## 6. iOS / App Store — platform layer

*Inherits Foundations + App.*

### MUST

- **IO1 — TestFlight from automation only.** The pipeline MUST publish to TestFlight (a
  pre-production track) only. Promotion to the public App Store track MUST be a deliberate
  human action in App Store Connect (A1 made concrete).
- **IO2 — Signing material is protected.** Certificates and provisioning profiles MUST be
  encrypted at rest or injected at build time, never stored in plaintext and never logged;
  decrypted material MUST be removed after the build (AN2's iOS form).
- **IO3 — Monotonic build number.** The build number MUST derive monotonically from the
  release tag's counter; it MUST never be hand-adjusted backward. (App Store Connect
  rejects a non-increasing build number for a given version, so this is also enforced
  upstream — but the pipeline MUST get it right by construction.)

### SHOULD

- **IO4** — A pre-release validation pass (build + unit + UI tests as feasible) SHOULD
  record the validated commit, and the release SHOULD refuse on a missing or stale record.
- **IO5** — "What to Test" / release-note text SHOULD be length-checked against App Store
  Connect limits before upload.
- **IO6** — Rendered UI screenshots SHOULD be kept as a reviewable visual record, not a
  pixel-exact pass/fail gate (cross-device rendering is not reproducible).

### CAN

- **IO-CAN1** — A phased release CAN govern the public App Store track, managed in App
  Store Connect after promotion.

---

## 7. Web — platform layer

*Inherits Foundations + App (and, when hosted on a backend platform, that platform's
layer too).*

### MUST

- **W1 — Content-addressed, atomic deploys.** Built assets MUST be immutably versioned
  (fingerprinted), and a deploy MUST swap to the new version atomically — no window where
  old markup loads new assets or vice versa.
- **W2 — No secrets in the client.** The shipped bundle MUST contain no credentials or
  secrets; everything in it is public by definition.
- **W3 — Correct cache contract.** Fingerprinted assets MUST be served immutably
  cacheable; the entry document MUST be served non-cacheable (or revalidated) so a deploy
  is picked up promptly.
- **W4 — Discoverable deploy record.** The running site MUST expose the commit it was
  built from (e.g. an embedded marker), so "what is live?" is answerable from the client.

### SHOULD

- **W5** — A preview/staging origin SHOULD serve a release for review before it reaches the
  production origin (the web form of A1's pre-production channel).
- **W6** — Rollback SHOULD be a near-instant repoint to a prior immutable version, not a
  rebuild.
- **W7** — The production origin SHOULD be reachable only over TLS, with security headers
  appropriate to the app.

### CAN

- **W-CAN1** — A temporary public preview URL CAN be exposed for ad-hoc review of
  unreleased work.
- **W-CAN2** — Progressive/percentage rollout or A-B routing CAN govern the production
  origin.

---

## 8. Firmware / embedded — a note, not yet a layer

Embedded firmware (microcontrollers, OTA-updated devices) has genuinely different release
semantics — flashed over a wire or pushed as a signed OTA image, often device-by-device,
with rollback meaning "the device boots the previous slot." Those rules are real, but they
are **not codified here** because firmware in this repo is **not released through a tagged
CI pipeline** today; it is flashed manually.

Until firmware has an automated, tag-driven release path, it sits **outside** this
strategy. Codify a `firmware` platform layer when — and only when — there is a real
release pipeline to anchor the rules to (per the principle: invest in proportion to
migration cost; don't write speculative rules with no implementation to pin them). At that
point the Foundations layer still applies unchanged (immutable monotonic tags, preview,
grounded overrides, deploy record, failure tracking); the new layer adds only what is
genuinely firmware-specific (signed images, slot-based rollback, staged device cohorts).

---

## 9. Repository composition — this repo today

Repository sections do not introduce new *kinds* of rules. They record how a specific
codebase composes the layers above, and audit where it stands. This section describes the
current state and is the home for any **deliberate deviation** (per design principle #7).

### 9.1 What this repo is

A multi-component repository:

- A **Firebase Cloud Functions** backend — **single-environment** (direct to production,
  no staging), released by `server/N` tags. *Composes: Foundations + Server + Firebase.*
- An **Android** app (Compose Multiplatform) — released to the Play **internal** track by
  `android/N` tags; production promotion is manual. *Composes: Foundations + App +
  Android.*
- An **iOS** app (the shared CMP codebase) — **builds in CI but has no release pipeline
  yet.** *Would compose: Foundations + App + iOS.*
- **ESP32 firmware** — flashed manually; outside this strategy (§8).

There is **no web frontend**, so the Web layer (§7) does not apply.

The operational details — exact commands, the deployer identity, GCP roles,
troubleshooting — live in [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md) and the
release-procedure sections of [`../CLAUDE.md`](../CLAUDE.md). This section assesses
*conformance*, not procedure.

### 9.2 Conformance audit

Legend: ✅ met · ◐ partial · ⚠️ deviation (deliberate, recorded) · ❌ gap (follow-up below).

**Foundations**

| Rule | State | Notes |
|---|---|---|
| F1 tagged/automated | ✅ | `server/N` / `android/N` tags trigger the deploy workflows; never push-to-main. |
| F2 immutable, monotonic tags | ✅ | Plain incrementing counters; "never delete/move a tag" is documented (F-NOT2 ✅). |
| F3 only mainline ships | ◐ | The release scripts enforce on-`main` + clean-tree *locally*, and branch protection (required checks, `enforce_admins`, no `--admin`) guards the merge. But the **deploy workflow** only re-checks the tagged commit's CI conclusion — it does not re-assert the commit is an ancestor of `main` server-side. See gap **G5**. |
| F4 preview before commitment | ✅ | `--check` prints state + the exact next command with real SHAs. |
| F5 confirm = assertion | ✅ | `--confirm-tag` must equal the computed next tag, or it refuses. |
| F6 grounded overrides | ✅ | Every override flag takes a SHA/tag and fails on mismatch ("a value from reality"). |
| F7 gates overridable, invariants not | ✅ | Validation + changelog gates have grounded overrides; monotonic tag + confirm-assertion are not overridable. (Smoke override is moot until smoke runs in-pipeline — G4.) |
| F8 truthful deploy record | ❌ | The running functions do not report the commit they were built from; "what's live?" is reconstructed from `gcloud` update-time + git. See gap **G1**. |
| F9 recoverable failures | ✅ | A failed tag push deletes the local tag; the silent-success class is documented as a known watch-point. |
| F10 dry run | ✅ | `--dry-run` runs the gates and creates nothing. |
| F11 deploy-failure tracking | ✅ | Both deploy workflows open/auto-close a deduped `release-failure/<target>` issue. |
| F12 release notes pre-written | ✅ | CHANGELOG gate refuses a release without a non-empty section for the tag/version. |
| F13 keyless credentials | ❌ | Both Firebase and Play deploys use long-lived stored service-account JSON keys. See gap **G2**. |
| F14 least-privilege runtime identity | ◐ | The deploy identity is separate from the runtime identity, but the runtime is the project's broad default service account. See gap **G3**. |
| F-CAN1 break-glass manual path | ✅ | A documented hand-run deploy exists, separate from the tag path. |
| F-CAN2 ad-hoc-tag guard | ✅ | A commit-time guard blocks direct `git tag`, steering to the release scripts. |

**Server**

| Rule | State | Notes |
|---|---|---|
| S1 staging + production | ⚠️ | **Single-environment by deliberate choice** (solo-maintained, low blast radius). Recorded deviation; see trigger **D1**. |
| S2 production anchored | ⚠️ | N/A under single-env (no staging to anchor to). Re-applies if a staging channel is added (D1). |
| S3 promotion ships identical commit | ⚠️ | N/A under single-env. |
| S4 self-verifying deploys | ❌ | A smoke check exists but is a **documented manual `curl`**, not a deploy-workflow step — so a deploy can report success without serving. This exact class bit once historically. High-priority gap **G4**. |
| S5 data changes are migrations | ◐ | Typed per-collection modules + contract tests are in place (see `FIREBASE_DATABASE_REFACTOR.md`); explicit schema-version + expand/migrate/contract discipline is not separately verified here. |
| S6 staging permissive | ⚠️ | N/A under single-env. |
| S7 named rollback path | ✅ | Rollback is a first-class script flow (re-release a prior tag with a grounded `--confirm-rollback-from`) and is documented with three options. |
| S8 promotion gated on deeper checks | ⚠️ | N/A under single-env. |

**Firebase**

| Rule | State | Notes |
|---|---|---|
| FB1 one project per environment | ✅ | One environment, one project (trivially satisfied; re-applies per-channel if staging is added). |
| FB2 scoped deploys only | ✅ | The deploy names a single surface (`--only functions`). |
| FB3 rules and code ship together | ❌ | The tag pipeline deploys functions only; Firestore **rules + indexes** (and any hosting) are out of the deploy scope, so a code change depending on a rules change won't ship atomically. See gap **G6**. |
| FB4 schema versioned + validated | ◐ | See S5. |
| FB5 keyless deploy auth | ❌ | See F13 / gap **G2**. |
| FB6 integration test per real-runtime boundary | ◐ | CI runs emulator-backed checks + contract tests; coverage is partial, not a per-boundary enumeration. |
| FB7 deploy record at deploy time | ❌ | See F8 / gap **G1**. |

**App + Android**

| Rule | State | Notes |
|---|---|---|
| A1 / AN1 never auto-publish; internal only | ✅ | Releases go to the Play internal track; production promotion is manual. |
| A2 release notes mandatory | ✅ | CHANGELOG gate on the `versionName`. |
| A3 / AN3 monotonic tag-derived version | ✅ | Version code is derived from the `android/N` counter. |
| A4 / AN4 validation marker | ✅ | A validation marker must match the released commit (grounded override for emergencies). |
| AN2 signing protected | ✅ | Keystore encrypted at rest, decrypted at build via an injected key, passwords as secrets, decrypted keystore removed after the build. |
| A5 / AN5 release-note length checked | ◐ | A "what's new" directory ships, but its length is not auto-checked against store limits. Minor gap **G7**. |
| AN6 screenshots as record, not gate | ✅ | Screenshot assets are a reviewable record, not a pixel-diff CI gate. |

**iOS**

| Rule | State | Notes |
|---|---|---|
| IO1–IO6 | ❌ | The iOS app builds in CI (not a required check) but has **no release pipeline** — no TestFlight/App Store automation. Entire layer is a pending gap **G8**. |

**Web** — N/A (no web frontend).

### 9.3 Gaps & follow-ups

Each item names a **revisit trigger** (per design principle #7 — invest when the trigger
fires, not before). None of these block today's releases; they are the prioritized path
toward full conformance.

- **G1 — F8/FB7: no deploy record.** *Trigger:* the next time "is the new code actually
  live?" is ambiguous (the silent-deploy-success class). *Action:* write the git SHA at
  deploy time and expose it (a `/build-info`-style endpoint or an embedded marker) so the
  running service can report its own commit.
- **G2 — F13/FB5: stored SA keys, not keyless.** *Trigger:* a key-rotation chore or any
  leaked-key scare. *Action:* migrate the Firebase deploy to Workload Identity Federation
  (keyless GitHub→GCP OIDC); for Play, scope and rotate the publisher key and minimize its
  lifetime.
- **G3 — F14: runtime not least-privilege.** *Trigger:* when the function's blast radius
  starts to matter (more data, more surfaces). *Action:* provision a dedicated runtime
  service account with narrow roles and run the functions as it; grant the deployer only
  `actAs` on it.
- **G4 — S4: deploy not self-verifying.** *Trigger:* already bit once — treat as the
  highest-priority gap. *Action:* run the existing smoke check as a **deploy-workflow
  step** that fails the job on a non-expected response, and (with G1) assert the live
  deploy record equals the just-shipped commit. Add a grounded `OVERRIDE_SKIP_SMOKE=<sha>`
  for break-glass (F7), never set in CI.
- **G5 — F3: server-side mainline re-assertion is partial.** *Trigger:* low (local script
  + branch protection already cover the normal path), but cheap to close. *Action:* add a
  `git merge-base --is-ancestor "$GITHUB_SHA" origin/main` gate to the deploy workflow so
  the pipeline refuses any tag not on `main`, independent of who cut it.
- **G6 — FB3: rules/indexes not deployed with code.** *Trigger:* the first change that
  couples a function to a Firestore rules or index change. *Action:* include `firestore`
  (rules + indexes) — and hosting if it ever serves content — in the deploy scope so they
  ship atomically with the functions, or add a documented coupled-deploy step.
- **G7 — A5/AN5: release-note length unchecked.** *Trigger:* the first time a store upload
  is rejected/truncated for length. *Action:* add a length check against the store limit to
  the release gate.
- **G8 — iOS has no release pipeline.** *Trigger:* when you want to ship iOS to testers.
  *Action:* add an `ios/N` tag-driven release mirroring Android — TestFlight-only,
  monotonic build number from the counter, signing material encrypted at rest, manual App
  Store promotion (the §6 layer).

### 9.4 Deliberate deviations

- **D1 — Single-environment server (no staging).** *Why:* solo-maintained service, small
  blast radius; a second environment is real ongoing cost for little current benefit.
  *Consequence:* forgoes S2/S3/S6/S8 (anchored production, verified promotion). *Revisit
  trigger:* when a bad deploy reaching users becomes costly enough to justify a pre-prod
  gate — at which point add a `staging` Firebase project and promote-by-tag, and S1
  tightens to MUST.

---

## Appendix: the design principles behind the rules

1. **Make the right thing easy and the wrong thing hard.** Previews print exact commands;
   confirmations are assertions; overrides demand a value from reality. Safety comes from
   friction placed precisely where mistakes happen, not from blanket process.
2. **Separate the durable from the swappable.** The released commit is the identity
   (durable); the channel it lands in is routing (swappable). Promotion and rollback move
   the same identity between channels without rebuilding.
3. **Verify against the real runtime.** A deploy is not done when the tool exits zero; it
   is done when the live system serves the expected commit. Constraints only the real
   platform enforces get a test against the real platform.
4. **Strictness increases with blast radius.** Staging is permissive; production is
   anchored. Internal tracks are automatic; user-facing promotion is human. The further a
   change can reach, the more it must earn.
5. **Boundaries are versioned; internals are free.** Persisted data, public URLs, and
   published identifiers are migrated deliberately. Everything else can change cheaply.
6. **Never stuck, never a shortcut.** Every blocking check has a grounded override so a
   wrong gate can't trap an emergency; structural invariants have none, because their
   escape is the forward-safe path (merge → tag → promote), which is always available. You
   can always get *out*; you can never go *around*.
7. **Deviations live with the code, not in the general rules.** The general and platform
   layers stay aspirational and repo-agnostic. When this repo *deliberately* departs from a
   rule, it records that in §9 — citing the rule and naming a revisit trigger — rather than
   weakening the rule. The aspiration stays clean; the exception stays discoverable where
   someone will hit it.
