---
category: reference
status: active
last_verified: 2026-04-24
---
# Technology Guides

Portable, technology-specific lessons — intended to be useful **outside
this repository**. Each guide captures gotchas, detection recipes, and
tested patterns for one tool or library, without assuming familiarity
with Smart Garage Door specifically.

If you're new to a technology in this stack, start here. If you're
bringing a lesson learned here to another project, copy the relevant
guide — the content is deliberately non-repo-specific.

## Guides

- [`kotlin-inject.md`](kotlin-inject.md) — `@Singleton` scoping only
  works through abstract entry points; runtime identity tests are the
  only proof of singleton-ness.
- [`repository-api-patterns.md`](repository-api-patterns.md) —
  repository `StateFlow` + `fetchX()` contract: always-refresh
  semantics, null-preserves-cache, exception resilience, and the three
  tests that encode them.
- [`r8-keep-rules.md`](r8-keep-rules.md) — R8 silently strips
  reflection / codegen / generic-erased code. Categories, symptoms,
  keep-rule templates, and release-variant smoke testing.
- [`compose-nav3-vm-scoping.md`](compose-nav3-vm-scoping.md) — Nav3
  creates one VM per nav entry. Design for N instances; put shared
  state in `@Singleton` repositories.
- [`reactive-auth-listener.md`](reactive-auth-listener.md) — auth is
  a stream, not a one-shot. Repository subscribes to the platform's
  auth callback on app-lifetime scope; commands are fire-and-forget;
  the listener is the source of truth.

## How to use these guides in another project

Each guide is standalone Markdown. Copy it verbatim into the target
repo's `docs/guides/` and adapt the examples to that codebase's
conventions. The core lesson and checklist should be transferable
unchanged.

## How to add a new guide

1. Pick a technology with a concrete, testable failure mode you've hit
   or investigated.
2. Write the guide as **a new reader's reference**, not a diary. Third
   person. No mentions of specific bug numbers / PRs unless they
   contain a linkable, reproducible artifact.
3. Include: the shape of the failure, why it happens, how to detect
   it, how to fix it, how to prevent it (automated checks preferred).
4. Add one "Minimum viable defense" section — the fewest checks that
   would catch this class of bug in a new repo adopting the same tech.
