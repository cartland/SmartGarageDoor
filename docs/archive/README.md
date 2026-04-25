---
category: archive
status: shipped
---

# Cross-Component Documentation Archive

Historical docs that span Firebase server (and PR-review history). For current state see:

- `../FIREBASE_DEPLOY_SETUP.md` — current Firebase ops guide (active)
- `../FIREBASE_DATABASE_REFACTOR.md` — kept active for its long-term DB-pattern rules (refactor itself is complete)
- `../FIREBASE_HANDLER_PATTERN.md` — canonical handler-extraction pattern (extracted from the shipped testing plan)
- `../FIREBASE_CONFIG_AUTHORITY.md` — config-authority rule (extracted from the shipped hardening plan A3)
- `../AGENTS.md` — documentation contract for any agent working on this repo
- `../../FirebaseServer/CHANGELOG.md` — release history (active control doc)
- `../../FirebaseServer/README.md` — Firebase onboarding (active)

## What's here

| File / folder | Why archived |
|---|---|
| `FIREBASE_HARDENING_PLAN.md` | A1+A3+B all shipped through `server/15`–`server/17`. The reusable rule (config is authoritative for buildTimestamps) lives in `../FIREBASE_CONFIG_AUTHORITY.md`. |
| `FIREBASE_HANDLER_TESTING_PLAN.md` | H1–H6 all shipped through `server/18`. The reusable pattern (`handle<Action>(input)` extraction) lives in `../FIREBASE_HANDLER_PATTERN.md`. |
| `pr-review/` | Per-PR review snapshots from past work. Archival by definition; the review summaries (`PHASE3_SUMMARY.md`, `PHASE4_AUDIT.md`) document past audit phases that have shipped. |

## Reading rules for agents

- These files describe state *as of* the time they were written. PR numbers and tag references stay correct; current code may have moved beyond them.
- For current operational guidance, prefer the active docs at the top of this README.
- Do not modify archive files except to fix factual errors about the historical period they describe.
