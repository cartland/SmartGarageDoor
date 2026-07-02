---
category: reference
status: active
last_verified: 2026-04-24
---
# Library Bugs & Issues

Known bugs or design issues in third-party libraries that affect this project. One file per issue.

Each file documents:
- **What** — the bug and how to reproduce
- **Impact** — when it manifests in our code
- **Mitigation** — our safeguards (tests, lint, patterns)
- **Upstream status** — whether a bug has been filed and its state

## Index

| File | Library | Summary | Mitigated? |
|------|---------|---------|------------|
| [room-nullability.md](room-nullability.md) | Room 2.7.2 | Non-nullable `@Query` return types crash on empty tables instead of compile-time error | Yes — unit test + nullable types |
