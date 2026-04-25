---
category: archive
status: shipped
---

# Android Documentation Archive

Historical Android docs kept for context. **None of these are current guidance.** For current state see:

- `../ARCHITECTURE.md` — current Android module + data-flow reference
- `../DECISIONS.md` — ADR catalog (still active)
- `../MIGRATION.md` — KMP migration roadmap (still active)
- `../MIGRATION_PLAN.md` — ADR-021/022 state-ownership rollout (Phase 2a–2e still active)
- `../guides/` — pattern guides for kotlin-inject, Nav3, R8, reactive auth, repository APIs
- `../DI_SINGLETON_REQUIREMENTS.md` — current `@Singleton` rules and verification recipe
- `../TESTING.md` — current test strategy

## What's here

| File | Why archived |
|---|---|
| `POSTMORTEM_ANDROID_170.md` | Snooze regression investigation — root-caused and fixed in android/174. Kept for the "tests pass ≠ architecture works" lesson and for understanding why ADR-022 + DI_SINGLETON_REQUIREMENTS exist. |
| `VIEWMODEL_SCOPING_ISSUE.md` | Multi-instance VM hazard — superseded by the kotlin-inject `@Singleton` fix (android/173) and ADR-022. Kept for reasoning history. |
| `DI-MIGRATION.md` | Hilt → kotlin-inject migration. Phase 3 complete; Hilt fully removed. Kept as a worked example of the migration pattern. |
| `design/PLAN-BUTTON-UX-REDESIGN.md` | Button UX implementation plan — shipped via ADR-012. |
| `design/PLAN-REMOVE-PUSH-STATUS.md` | PushStatus removal plan — shipped via #282. |
| `design/PLAN-HOME-SPLIT-BUTTON-COLORS-EMPTY-STATES.md` | Home redesign plan — shipped. |

## Reading rules for agents

- These files describe state *as of* the time they were written. Cross-references to specific PRs, line numbers, or class names may no longer match the current tree.
- If you need the current rule, follow the link in the active doc that pointed you here, not these files directly.
- Do not modify archive files except to fix factual errors about the historical period they describe.
