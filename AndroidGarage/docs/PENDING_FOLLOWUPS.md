---
category: plan
status: active
last_verified: 2026-05-10
---

# Pending Follow-ups

User-flagged items that aren't tied to a specific release and aren't smoke-test verifications — feature follow-ups, copy revisions, design open questions. Each item should be discrete enough that a single PR could close it.

**Scope:** items that need their own design/implementation effort. Smoke-test verifications belong in [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md). Per-version implementation history belongs in [`../CHANGELOG.md`](../CHANGELOG.md). Architectural conventions belong in [`../../CLAUDE.md`](../../CLAUDE.md).

**Why this lives in the repo, not memory:** project-specific TODOs need to be reviewable in PRs and discoverable to anyone reading the repo cold. See `feedback_dump_context_repo_first.md` for the rule.

## Open

### 1. Dedicated "Developer" allowlist flag (server + Android)

**Status:** flagged twice in conversation as a temporary-shortcut to clean up. Not started.

**Background:** PR #648 (shipped in `android/196` / 2.10.2 on 2026-05-06) renamed Settings "Tools" → "Developer" and moved Diagnostics into it. The whole Developer section is currently gated by `functionListAccess` (the Functions allowlist) — the user has explicitly flagged this as a temporary shortcut. The CHANGELOG.md 2.10.2 entry foreshadows the fix: *"A dedicated 'Developer' allowlist will follow in a later release so Functions and Developer access can diverge."*

**Plan:** introduce a dedicated allowlist flag (its own server config key, route, domain field) and switch the gate from `functionListAccess` to it. Pattern to follow: [`../../docs/FEATURE_FLAGS.md`](../../../docs/FEATURE_FLAGS.md) (uses Function List as the canonical example).

**Suggested names:**
- Server config: `developerAllowedEmails`
- Route: `developerAccess`
- Domain field: `developer: Boolean`

**Touch points** (mirror the Functions flag — ~15 files total):
- 8 files server-side (handler, route, config reader, etc.)
- 6 files Android (route, repo, viewmodel call site, ProfileContent gate)
- 1 wire-contract fixture

**Single relevant Android edit:** `ProfileContent.kt` — `showDeveloperSection = functionListAccess == true` → `developerAccess == true`.

### 2. Home permission banner copy revision

**Status:** open question — does production want to adopt the punchier wording the preview used to have?

**Background:** Production text in `NotificationPermissionCopy.justificationText(0)` (`AndroidGarage/.../permissions/NotificationPermission.kt:70`):

> "Please turn on notifications to be notified when the door is left open."

This wraps to three lines in the Home banner. Before PR #656 the screenshot preview hardcoded a punchier alternative — *"Notifications are off — you won't be alerted when the door opens"* — which the user *preferred*, but the screenshot was lying about what users actually see.

PR #656 (shipped in `android/196`) made the preview honest by calling the production function. The user explicitly chose this over keeping the punchier preview text, with the principle: *"screenshots should use production code paths."*

**Open question:** should production adopt the punchier wording? If yes, edit `baseText` in `NotificationPermissionCopy.justificationText` and the screenshot will follow automatically because it now reads from the same source. The Settings row's equivalent copy is *"Notifications disabled — tap to enable"* (PR #654) — a third variant. If a single canonical phrasing is desired, both surfaces should pull from the same string.

**Note (2026-05-10):** when revisiting, also check the em-dash style sweep from 2.16.9 — both the punchier wording and the Settings row contain em dashes that the style rule forbids. The current production text doesn't.

## Done (recent)

(Empty — items move from Open to Done when the closing PR merges, then get archived from this list at the next dump-context.)
