---
category: reference
status: active
last_verified: 2026-04-24
---

# AGENTS.md

Documentation contract and reading guide for any AI agent (Claude Code, Cursor, custom agents) working on this repo.

## 1. Why this repo is agent-designed

This repository is explicitly designed for collaboration with AI agents. A solo human developer accepts work from many agents over time. Each agent session is ephemeral — no agent remembers the previous session — so the documentation must be the durable, complete record of what the system is, why it is that way, and how to change it safely.

That has three consequences for how docs are written:

1. **Docs are load-bearing.** Architectural decisions, test rules, breaking-change pitfalls, and deployment procedures live in version-controlled docs, not in human memory.
2. **Mode signals must be explicit.** Agents can't reliably infer from prose whether a doc describes current reality, future intent, or past history. Every doc declares its mode in YAML front-matter.
3. **Single source of truth per fact.** When the same rule is restated in two places, agents follow the path that finds it first — and drift becomes invisible. Each fact has one canonical home; other docs link instead of restating.

## 2. Reading order for a new agent

If you've just landed in this repo, read in this order:

1. **`/CLAUDE.md`** (~10 min) — project overview, build commands, agent operational rules (PR workflow, validation, hooks).
2. **This file** (`/docs/AGENTS.md`, ~5 min) — taxonomy, front-matter, trust-but-verify rule.
3. **`/AndroidGarage/docs/ARCHITECTURE.md`** (~10 min) — current Android module structure and data flows. Skip if working on Firebase only.
4. **`/AndroidGarage/docs/DECISIONS.md`** (skim, ~5 min) — 24 ADRs. Read the one you're about to interact with; skim the rest.
5. **Task-specific docs** — listed in §6 below.

## 3. Document categories

Every doc declares one of four categories via front-matter `category:`. Skills are a separate native-Claude-Code system and don't use this convention.

| Category | Definition | Examples |
|---|---|---|
| `reference` | Describes current state. Must stay in sync with code. | `ARCHITECTURE.md`, `CLAUDE.md`, `DECISIONS.md`, guides |
| `plan` | Active intent. Future or in-progress work. | `MIGRATION.md`, `MIGRATION_PLAN.md` |
| `archive` | Historical / superseded / shipped. Frozen. | postmortems, completed plans, design history, PR reviews |
| `skill` | Workflow definitions in `.claude/skills/`. Has its own native frontmatter — does NOT use this `category` field. |

## 4. Front-matter contract

Every in-scope `.md` file (see §4.4) starts with a YAML front-matter block:

```yaml
---
category: reference | plan | archive
status: active | shipped | superseded
last_verified: 2026-04-24       # required when category=reference + status=active
superseded_by: path/to/doc.md   # required iff status=superseded
---
```

### 4.1 Field rules

- **`category`** (required, enum) — one of `reference`, `plan`, `archive`. Skills are excluded.
- **`status`** (required, enum):
  - `active` — actively maintained, follow this doc
  - `shipped` — completed work, kept for reference, no longer changing
  - `superseded` — replaced by another doc; `superseded_by` is required
- **`last_verified`** (ISO date) — required when `category: reference, status: active`. Optional otherwise.
- **`superseded_by`** (path) — required iff `status: superseded`; forbidden otherwise.

### 4.2 Stale `last_verified` policy

Docs older than 90 days surface as **warnings** in `validate.sh` output. Warnings never block CI or merging. They're a discoverability signal: the next agent who touches an adjacent file should consider re-verifying.

### 4.3 Validation

`scripts/check-doc-frontmatter.sh` enforces the contract. Wired into `validate.sh`. Validation errors block; staleness warnings don't.

### 4.4 Scope

**In scope** (front-matter required, link-checker checks): `AndroidGarage/`, `FirebaseServer/`, `docs/`, root `README.md`, root `CLAUDE.md`.

**Excluded** (no front-matter required, ignored by checks):
- `.claude/skills/` — has native Claude Code frontmatter
- `GarageFirmware_ESP32/`, `Arduino_ESP32/` — out of primary doc scope
- `node_modules/`, `**/build/`, `.claude/worktrees/`
- Generated content: screenshot galleries, `detekt.md`

## 5. Trust but verify

Agents should treat docs as authoritative until a reason arises to verify against the code.

**Verify when:**
- You're about to modify code the doc describes — read the code first to spot recent changes.
- The doc's `last_verified` is more than 90 days old AND the doc's claim is load-bearing for your task.
- Two docs contradict each other.
- A documented procedure fails in an unexpected way.

**How to verify:** grep the codebase, read the source file the doc references, run the documented command. If the doc is wrong, fix it (PR) and update `last_verified`.

**Memory caveat:** the auto-memory system (`~/.claude/projects/.../memory/`) stores facts that were true *when the memory was written*. Before recommending action based on a memory entry, verify the fact still holds in the current tree.

## 6. Source-of-truth map

When you need to find or update a fact, this table tells you which doc owns it. Other docs may link to the canonical home but should not restate the rule.

### Cross-cutting

| Fact | Canonical doc |
|---|---|
| Build commands (Android, Firebase) | `CLAUDE.md` § Build Commands |
| PR workflow rules (stacked PRs, auto-merge, branch protection) | `CLAUDE.md` § PR Workflow |
| CI architecture | `CLAUDE.md` § CI Architecture |
| Agent design + doc taxonomy | This file |

### Android

| Fact | Canonical doc |
|---|---|
| Module structure + data flows | `AndroidGarage/docs/ARCHITECTURE.md` |
| Architectural decisions (ADRs) | `AndroidGarage/docs/DECISIONS.md` |
| `@Singleton` + kotlin-inject rules | `AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md` |
| Room schema change recipe | `CLAUDE.md` § Room Database Safety + `ARCHITECTURE.md` § Database |
| Migration roadmap (KMP phases) | `AndroidGarage/docs/MIGRATION.md` |
| ADR-021/022 state-ownership rollout | `AndroidGarage/docs/MIGRATION_PLAN.md` |
| Pattern guides (DI, Nav3 scoping, R8 keep rules, reactive auth, repo APIs) | `AndroidGarage/docs/guides/` |
| Library bug workarounds | `AndroidGarage/docs/library-bugs/` |
| Release procedure (Android) | `CLAUDE.md` § Releasing Android |
| Release version history | `AndroidGarage/CHANGELOG.md` |

### Firebase server

| Fact | Canonical doc |
|---|---|
| Release procedure (server) | `CLAUDE.md` § Releasing Firebase Server |
| Release version history | `FirebaseServer/CHANGELOG.md` (control doc — gates `release-firebase.sh`) |
| Operational guide (deploy, rollback, monitoring, GCP setup) | `docs/FIREBASE_DEPLOY_SETUP.md` |
| Database collection rules + canonical patterns | `docs/FIREBASE_DATABASE_REFACTOR.md` |
| Handler-extraction pattern | `docs/FIREBASE_HANDLER_PATTERN.md` |
| Config-authority rule | `docs/FIREBASE_CONFIG_AUTHORITY.md` |
| Firebase server onboarding | `FirebaseServer/README.md` |

### ESP32

ESP32 firmware documentation is **out of primary scope**. Reference docs in `GarageFirmware_ESP32/` and `Arduino_ESP32/` describe the firmware but are not held to this contract. ESP32 sections in root `README.md` and `CLAUDE.md` are marked with `<!-- not-actively-maintained -->` HTML comments.

## 7. Document lifecycle

A doc moves through three states:

1. **`active`** — currently true. Update `last_verified` whenever you change the underlying fact in code or verify the doc against current code.
2. **`shipped`** — work the doc described is complete. Doc kept for context. Move to an `archive/` folder, set `status: shipped`. Plans become history; principles stay live (extract them to a separate `reference` doc if needed).
3. **`superseded`** — replaced by a different doc. Set `status: superseded`, `superseded_by: path/to/successor.md`.

**Archive folders:** `AndroidGarage/docs/archive/` for Android, `docs/archive/` for cross-component / Firebase. Each has a `README.md` explaining what's there.

## 8. Self-diagnosis — what an agent should check at session end

Before ending a working session, check:

- [ ] Did I modify code that a doc describes? Update the doc + `last_verified` in the same PR.
- [ ] Did I read a `category: reference` doc whose `last_verified` is >90 days old? If the claim was load-bearing for my work, verify and bump the date.
- [ ] Did I encounter contradicting docs? Fix the stale one.
- [ ] Did I see a doc with `status: active` describing completed work? Move it to archive.
- [ ] Did I add a new doc? Did it get front-matter?

## 9. What this file does NOT do

This file does not duplicate `CLAUDE.md` (which has agent operational rules — hooks, PR safety, dev-mode behavior) or `ARCHITECTURE.md` (which has the Android system design). It only describes the documentation contract. When a rule is about *the system* it lives in the appropriate reference doc; only *meta-rules about how docs work* live here.
