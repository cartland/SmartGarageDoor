---
category: reference
status: active
last_verified: 2026-04-25
---

# `/docs/` — Cross-cutting documentation

This folder holds documentation that spans more than one component (Android, Firebase server, ESP32 firmware), or that defines repo-wide conventions. Component-specific docs live next to the component (e.g., [`AndroidGarage/docs/`](../AndroidGarage/docs/), [`FirebaseServer/README.md`](../FirebaseServer/README.md)).

## Start here

- **[`AGENTS.md`](AGENTS.md)** — the documentation contract: doc categories, YAML front-matter, source-of-truth map. Read this first if you are an AI agent or a new contributor adding documentation.

## Firebase server (control docs)

These four files are the canonical home for cross-cutting Firebase rules. Other docs link here rather than restating the rule.

| Doc | Audience | What it owns |
|---|---|---|
| [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md) | Operator / on-call | Release process, rollback, monitoring, GCP setup, troubleshooting |
| [`FIREBASE_DATABASE_REFACTOR.md`](FIREBASE_DATABASE_REFACTOR.md) | Server engineer | Typed per-collection database modules, in-memory fakes, contract tests |
| [`FIREBASE_HANDLER_PATTERN.md`](FIREBASE_HANDLER_PATTERN.md) | Server engineer | Pure `handle<Action>(input)` core + thin wrapper convention for every HTTP/pubsub handler |
| [`FIREBASE_CONFIG_AUTHORITY.md`](FIREBASE_CONFIG_AUTHORITY.md) | Operator + engineer | Strict-mode rule that production server config (not code) is the single source of truth for device build timestamps |

## Component docs (links out)

- **Android:** [`../AndroidGarage/docs/ARCHITECTURE.md`](../AndroidGarage/docs/ARCHITECTURE.md), [`../AndroidGarage/docs/DECISIONS.md`](../AndroidGarage/docs/DECISIONS.md), [`../AndroidGarage/docs/guides/`](../AndroidGarage/docs/guides/)
- **Firebase:** [`../FirebaseServer/README.md`](../FirebaseServer/README.md), [`../FirebaseServer/CHANGELOG.md`](../FirebaseServer/CHANGELOG.md)
- **Agent operational rules:** [`../CLAUDE.md`](../CLAUDE.md)

## Archive

[`archive/`](archive/) holds shipped plans, postmortems, and the PR-review history. See [`archive/README.md`](archive/README.md) for the index.
