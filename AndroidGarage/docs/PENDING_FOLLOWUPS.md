---
category: plan
status: active
last_verified: 2026-05-11
---

> **Last update 2026-05-11:** Developer-allowlist flag fully shipped (server/26 + android/235). String-resource migration started — first PR (#784) covered SettingsContent; comprehensive plan added below.

# Pending Follow-ups

User-flagged items that aren't tied to a specific release and aren't smoke-test verifications — feature follow-ups, copy revisions, design open questions. Each item should be discrete enough that a single PR could close it.

**Scope:** items that need their own design/implementation effort. Smoke-test verifications belong in [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md). Per-version implementation history belongs in [`../CHANGELOG.md`](../CHANGELOG.md). Architectural conventions belong in [`../../CLAUDE.md`](../../CLAUDE.md).

**Why this lives in the repo, not memory:** project-specific TODOs need to be reviewable in PRs and discoverable to anyone reading the repo cold. See `feedback_dump_context_repo_first.md` for the rule.

## Open

> **No open items.** The string-resource migration completed 2026-05-11; entry moved to Done. Add new follow-ups here as they're flagged.

<!-- Historical reference: original Phase 1/2/3 migration plan now in Done. -->

<details>
<summary>Archived: original migration plan (closed 2026-05-11)</summary>

### 1. Migrate user-visible strings to Android string resources [DONE]

**Status:** COMPLETE 2026-05-11 in 12 PRs (#784–#795). See the Done section below for the summary entry.

**Aspiration (2026-05-11 user direction):** "I want this app to be a good example so I want this to be done well across the whole app." — the bar is exemplary, not just functional. When this plan is complete, the only `String` literals in production code paths are (a) server-returned text carried verbatim, (b) developer-only / log strings, (c) animation / Compose-tooling debug metadata. Every user-facing label is a resource ID.

**Why migrate:** (a) unblocks future localization (`values-<lang>/strings.xml` becomes a drop-in), (b) centralizes copy for style sweeps (em-dash, sentence case), (c) decouples copy revisions from code review, (d) lets tests assert on typed state instead of fragile text content.

#### Architectural decisions

##### A. Non-Composable contexts: typed-hint refactor (NOT `StringProvider`)

Mappers, ViewModels, and UseCases MUST NOT return user-visible strings. They emit **typed states** (sealed types, enums, primitive args). Composables convert types to localized strings at render time.

This is the pattern the codebase **already uses** for `SnoozeRowState`, `HomeAlert`, `AccountRowState`, `AppLayoutMode`, etc. The migration extends the same pattern to cover the remaining string-emitting cases.

**Concrete refactor examples:**

| Today | After |
|---|---|
| `HomeMapper.stateLabel(DoorPosition): String` returns `"Open"` / `"Closed"` / etc. | DELETED. Composable maps `DoorPosition` enum → `stringResource(R.string.door_state_*)` directly via a `when` block in the UI layer. |
| `HomeMapper.warning(event): String?` returns `"Opening, taking longer than expected"` etc. | Returns `DoorWarning?` sealed type (server-message variant carries the verbatim server string; fallback variants are enum cases). Composable resolves enum to resource. |
| `HomeMapper.formatDuration(seconds): String` returns `"5 hr 30 min"` | DELETED. Composable does `formatDurationDisplay(seconds)` with `pluralStringResource` for count-based parts. |
| `NotificationPermissionCopy.justificationText(int): String` builds the multi-line message | Returns `NotificationJustification(attemptCount: Int)` data class. Composable assembles via `stringResource` + `pluralStringResource`. |
| `HomeAlert.Stale(message = "Not receiving updates from server")` (default arg) | `data object Stale : HomeAlert` (no `message` field). Composable maps `Stale` → `R.string.home_alert_stale_message`. |

**Why typed-hint, not `StringProvider`:**

- (a) Aligns with existing codebase pattern — `SnoozeRowState`, `HomeAlert`, `AccountRowState` are already typed-hint. Don't introduce a parallel abstraction.
- (b) Mappers stay pure-function unit-testable without `Context` / `Resources`.
- (c) KMP-portable — mapper modules have no Android dependency.
- (d) `StringProvider` would add a DI dep to ~30 sites. Typed-hint adds ~5 sealed types in `domain/` or `usecase/`.
- (e) Tests can assert on typed shape (`assertEquals(DoorWarning.OpeningTooLong, mapper.warning(event))`) rather than text — so a copy revision doesn't break the test.

##### B. Naming convention

`<screen>_<section>_<purpose>[_<state>]`. Snake_case (Android convention). Examples already in `strings.xml`:

```
settings_account_sign_in
settings_notifications_subtitle_loading
settings_notifications_subtitle_snoozing_until    (with %1$s arg)
settings_about_version_subtitle                    (with %1$s, %2$s args)
```

For door states, alerts, warnings (Phase 2):
```
home_door_state_open
home_door_state_closed
home_warning_opening_too_long
home_alert_stale_message
home_alert_action_retry
home_duration_days                (plural: one/other)
```

##### C. What gets migrated

| Class | Migrate? | Notes |
|---|---|---|
| `Text("literal")` in Composable body | ✅ Yes | Direct `stringResource` swap |
| Mapper / ViewModel returning user-visible string | 🔁 Refactor | Emit typed state, Composable renders |
| Server-returned text (`event.message`) | ❌ No | Arbitrary data, not a label |
| `Logger.*` messages | ❌ No | Internal only |
| `AppAnimatedVisibility(label = "...")` | ❌ No | Compose-tooling debug metadata |
| `@Preview` fake data (display names, sample times, version strings) | ❌ No | Not user-visible at runtime |
| Test fixture strings | ❌ No | Internal to tests |
| `.takeIf { it.isNotBlank() } ?: "(unknown)"` style fallback | ✅ Yes | User sees this when name is blank |

##### D. Plurals + format args

- Count-based strings → `<plurals>` + `pluralStringResource(R.plurals.X, count, count)`. Examples: history-event count, duration "N days" / "N min".
- Interpolated strings → `formatArgs` (`%1$s`, `%2$d`). Already in use for snooze-until-time and version subtitle in `strings.xml`.

##### E. Lint enforcement (ratchet)

After ~80% of the migration is complete, add `checkNoLiteralStringsInCompose` to `validate.sh`:

- Scans `androidApp/src/main/.../**/*.kt` for `Text("...")`, `Text(text = "...")` and other text-rendering Composables with literal first arg.
- Allows: `Text("")`, `Text("\n")`, `Text(stringResource(...))`, `Text(arg)` where `arg` is a parameter or property.
- Exemption file (`androidApp/string-literal-exemptions.txt`) lists violations remaining at lint-introduction time. New violations are blocked.
- Exemption-file shape mirrors `screen-viewmodel-exemptions.txt` from ADR-026.

**Don't add the lint early** — it would gate the migration PRs themselves.

Optional follow-up lint: scan `strings.xml` for em dashes (`—`) and warn (CLAUDE.md style rule centralization).

#### Phased file checklist

##### Phase 1 — Composable surfaces (low risk, mechanical)

These files have user-visible literal strings inside Composable bodies. Direct `stringResource` swap; no architectural change. One PR per file unless two are tightly coupled.

| File | Approx strings | PR |
|---|---|---|
| `androidApp/.../ui/settings/SettingsContent.kt` | 16 | ✅ #784 |
| `androidApp/.../ui/home/HomeContent.kt` | ~12 (section headers, alert action labels, "Allow", "Retry") | TODO |
| `androidApp/.../ui/home/DoorHistoryContent.kt` | ~6 | TODO |
| `androidApp/.../ui/FunctionListContent.kt` | ~8 (function-list warning + action labels) | TODO |
| `androidApp/.../ui/settings/DiagnosticsContent.kt` | ~10 (counter labels, button labels, dialog text) | TODO |
| `androidApp/.../ui/settings/SnoozeBottomSheet.kt` | ~6 (duration option labels) | TODO |
| `androidApp/.../ui/settings/AccountBottomSheet.kt` | ~3 (sign out label + body) | TODO |
| `androidApp/.../ui/settings/VersionBottomSheet.kt` | ~5 (row labels, copy labels) | TODO |
| `androidApp/.../ui/home/DoorStatusInfoBottomSheet.kt` | ~10 (title + 5 paragraph bodies) | TODO |
| `androidApp/.../ui/home/RemoteControlInfoBottomSheet.kt` | ~6 | TODO |
| `androidApp/.../ui/ProfileContent.kt` | ~2 (`"(unknown)"` fallback, version-sheet copy labels) | TODO |
| `androidApp/.../ui/auth/AuthTokenCopier.kt` | ~2 (Toast strings) | TODO |

##### Phase 2 — Typed-hint refactor (mappers / non-Composable copy objects)

Each is its own PR because the API change ripples across module boundaries (mapper test assertions change from text to type).

| Refactor | Strings | PR |
|---|---|---|
| `HomeMapper`: `stateLabel` deleted (Composable does `when (DoorPosition)` → `stringResource`); `warning` → `DoorWarning` sealed type; `formatDuration` deleted (Composable side w/ plurals) | ~12 + plurals | TODO |
| `HistoryMapper`: parallel refactor — date-grouping labels, item subtitles | ~6 | TODO |
| `NotificationPermissionCopy` → `NotificationJustification(attemptCount: Int)` data class; `Composable.justificationMessage(j)` assembles via stringResource + plural for "$attemptCount times" | 4 strings + 1 plural | TODO |
| `HomeAlert.Stale` / `HomeAlert.FetchError` default messages → drop default-string args, Composable resolves type → resource | 3 | TODO |
| `DiagnosticsMapper` (if any): counter labels → typed enum | TBD | TODO |

##### Phase 3 — Lint + cleanup

| Work | PR |
|---|---|
| Add `checkNoLiteralStringsInCompose` lint to `validate.sh` + exemption file for any Phase-1/2 stragglers | TODO |
| Optional: `checkNoEmDashInStringResources` lint scanning `strings.xml` for `—` | TODO |
| Review entire `strings.xml` for sentence case consistency | TODO |

#### Per-PR checklist

- [ ] Strings extracted with `<screen>_<section>_<purpose>[_<state>]` naming (snake_case)
- [ ] `formatArgs` (`%1$s`, `%2$d`) for interpolated strings
- [ ] `<plurals>` + `pluralStringResource` for count-based strings
- [ ] `@Preview` fake data NOT migrated (not production-visible labels)
- [ ] All values byte-identical to pre-migration → no screenshot churn (assert `git status AndroidGarage/android-screenshot-tests/` is clean)
- [ ] If screenshot churn happens, it's an intentional copy change in the same PR — note it in the PR body
- [ ] `R` imported as `com.chriscartland.garage.R`
- [ ] `import androidx.compose.ui.res.stringResource` (or `pluralStringResource`)
- [ ] `validate.sh` PASS before push (per the validate-before-first-push rule in `CLAUDE.md`)
- [ ] PR description lists strings extracted + what's deferred to other PRs

#### Out of scope for migration PRs

- **Copy revisions.** If a string reads poorly during the move, file a follow-up — don't conflate "extract to resource" with "rewrite". Mixing both makes the PR harder to review and the diff harder to revert.
- **Localization itself.** This plan unblocks localization but does not add `values-<lang>/strings.xml` files; that's a separate decision.
- **String resources for FCM payloads, Firestore field names, log keys.** These are wire / internal — not user-visible labels.

</details>

## Done (recent)

- **String-resource migration COMPLETE — 12 PRs, 2026-05-10 → 2026-05-11.** Every user-visible label in the production app now lives in `androidApp/src/main/res/values/strings.xml`; mappers and ViewModels emit typed values; the Composable layer resolves to localized strings via `stringResource` + `pluralStringResource` at render time. Phase 1 (Composable-scope mechanical migrations, ~50 strings across 12 files): SettingsContent (#784), HomeContent (#786), DiagnosticsContent + SnoozeBottomSheet + AccountBottomSheet + VersionBottomSheet + ProfileContent + AuthTokenCopier (#787), DoorHistoryContent + InfoBottomSheet (#788), FunctionListContent (#789). Phase 2 (typed-hint refactors of mapper APIs): `DoorWarning` sealed type (#790), `HomeMapper.stateLabel` deletion + `doorStateLabel(DoorPosition)` Composable resolver (#791), `HomeStatusFormatter` pure-function utility + `rememberSinceLine` Composable + `home_duration_*` plurals + `HomeAlert` default-arg drops (#793), `HistoryMapper` full refactor — `AnomalyKind` / `DayLabel` / `TransitWarning` / `HistoryFormatter`, ~50 test rewrites (#794), `NotificationJustification` typed (#795). Phase 3: `checkNoLiteralStringsInCompose` lint Gradle task in `validate.sh` (#792) — guards against new `Text("literal")` regressions; exemption file empty. Plan + per-PR rationale archived in this file under "Archived". Typed-hint pattern (`AnomalyKind`, `DayLabel`, `TransitWarning`, `DoorWarning`, `NotificationJustification`, etc.) now established as the convention for any new mapper that would otherwise emit user-visible strings.
- **Dedicated Developer allowlist flag — both sides shipped.** Server: `server/26` (2026-05-10) added `GET /developerAccess` and the Firestore `featureDeveloperAllowedEmails` field. Android: `android/235` / 2.16.21 (2026-05-11, PR #781) flipped Settings → Developer's outer gate to the new endpoint and added an independent `showFunctionListRow` gate (still keyed on `functionListAccess`) so the two allowlists can diverge. `KtorNetworkFeatureAllowlistDataSource` now issues both endpoint GETs in parallel and combines them; the data-source-level abstraction stayed unchanged so no new repository/DI wiring was needed (the original ~6-file plan turned out to be ~12 files concentrated at the data + UI layers instead). Smoke matrix in `PENDING_SMOKE_TESTS.md` item 6.
- **Home permission banner copy revision** — closed in 2.16.16 (`copy/home-permission-banner-shorter`). Production `NotificationPermissionCopy.justificationText(0)` now reads *"Turn on notifications to get alerted when the door is left open."* (2 lines on Home, was 3). Same imperative-request framing so escalation lines at attempt 3+/4+ still flow without changes. Settings row copy was already short (em-dash sweep landed in 2.16.9). Three variants are no longer in tension; if a future PR wants a single canonical string both surfaces could read from, that's a fresh follow-up.
