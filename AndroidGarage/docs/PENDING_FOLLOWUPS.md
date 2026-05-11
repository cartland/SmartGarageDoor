---
category: plan
status: active
last_verified: 2026-05-11
---

> **Last update 2026-05-11:** Server side of the Developer-allowlist follow-up shipped in `server/26`; Android side is the next concrete PR. `2.16.17`–`2.16.20` shipped along the way (smoke items in `PENDING_SMOKE_TESTS.md`).

# Pending Follow-ups

User-flagged items that aren't tied to a specific release and aren't smoke-test verifications — feature follow-ups, copy revisions, design open questions. Each item should be discrete enough that a single PR could close it.

**Scope:** items that need their own design/implementation effort. Smoke-test verifications belong in [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md). Per-version implementation history belongs in [`../CHANGELOG.md`](../CHANGELOG.md). Architectural conventions belong in [`../../CLAUDE.md`](../../CLAUDE.md).

**Why this lives in the repo, not memory:** project-specific TODOs need to be reviewable in PRs and discoverable to anyone reading the repo cold. See `feedback_dump_context_repo_first.md` for the rule.

## Open

### 1. Migrate user-visible strings to Android string resources

**Status:** flagged 2026-05-11 as a separate, dedicated PR (or PR series). Not started.

**Background:** user-visible strings are currently inlined as Kotlin string literals throughout the app code (Composables, ViewModels, mappers, copy objects like `NotificationPermissionCopy`). This works but has three downsides: (a) makes future localization (`values-<lang>/strings.xml`) impossible without first extracting strings, (b) makes it hard to grep all user-facing copy in one place for style sweeps (em-dash, sentence case), (c) couples copy revisions to code review of unrelated logic.

**Plan:** move all user-visible strings to `androidApp/src/main/res/values/strings.xml`. Replace literals with `stringResource(R.string.X)` in Composables and `Context.getString(R.string.X)` (or an injected resource provider for KMP-compatible code) elsewhere.

**Scope decisions to make before starting:**
- **Per PR boundary** — one PR per feature area (Home, Settings, Diagnostics, permission copy, error messages) is more reviewable than one mega-PR. ~5–8 PRs total.
- **Plurals / formatting** — use `<plurals>` and `getQuantityString()` for count-based copy (history list "N events"), and `formatArgs` placeholders for interpolated strings.
- **Non-Composable contexts (UseCases, ViewModels, mappers)** — these can't call `stringResource`. Options: (i) inject a `StringProvider` interface (KMP-friendly), (ii) move the string assembly into the Composable and pass typed inputs to the VM, (iii) keep them as Kotlin literals if the string is genuinely internal (logs, telemetry tags).
- **What counts as "user-visible"** — visible UI text yes; log/telemetry strings no; test fixtures no; preview-only strings no (since they're not shipped). The `checkNoLiteralStringsInCompose` lint pattern (used by other projects) could enforce after the migration but pre-migration the audit is grep-driven.
- **Style-rule enforcement** — once strings are in XML the existing `checkPreviewCoverage` shape can be extended with a string-resource style lint (no em dashes in `values/strings.xml`, sentence case for headings).

**Files to start with (highest user-visibility):** `NotificationPermissionCopy`, `HomeContent` permission/warning copy, `SettingsContent` row labels, info-sheet bodies, error UI strings.

**Out of scope for the migration PR(s):** copy revisions. If a string reads poorly during the move, file a follow-up — don't conflate "extract to resource" with "rewrite".

## Done (recent)

- **Dedicated Developer allowlist flag — both sides shipped.** Server: `server/26` (2026-05-10) added `GET /developerAccess` and the Firestore `featureDeveloperAllowedEmails` field. Android: `android/235` / 2.16.21 (2026-05-11, PR #781) flipped Settings → Developer's outer gate to the new endpoint and added an independent `showFunctionListRow` gate (still keyed on `functionListAccess`) so the two allowlists can diverge. `KtorNetworkFeatureAllowlistDataSource` now issues both endpoint GETs in parallel and combines them; the data-source-level abstraction stayed unchanged so no new repository/DI wiring was needed (the original ~6-file plan turned out to be ~12 files concentrated at the data + UI layers instead). Smoke matrix in `PENDING_SMOKE_TESTS.md` item 6.
- **Home permission banner copy revision** — closed in 2.16.16 (`copy/home-permission-banner-shorter`). Production `NotificationPermissionCopy.justificationText(0)` now reads *"Turn on notifications to get alerted when the door is left open."* (2 lines on Home, was 3). Same imperative-request framing so escalation lines at attempt 3+/4+ still flow without changes. Settings row copy was already short (em-dash sweep landed in 2.16.9). Three variants are no longer in tension; if a future PR wants a single canonical string both surfaces could read from, that's a fresh follow-up.
