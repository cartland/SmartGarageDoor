---
description: Increment the Android versionName in version.properties. Default patch, supports minor and major.
allowed-tools: Bash(*), Read, Edit
user-invocable: true
---

# Bump Android Version

Increment `versionName` in `AndroidGarage/version.properties` and create a PR.

## Arguments

- `patch` (default) — 2.4.1 → 2.4.2
- `minor` — 2.4.1 → 2.5.0
- `major` — 2.4.1 → 3.0.0

If no argument is provided, default to `patch`.

## Versioning rule

See [`AndroidGarage/CHANGELOG.md#versioning`](../../../AndroidGarage/CHANGELOG.md) for the authoritative rule:

- **Major** — App rewrite or a core-experience shift so significant the previous version feels like a different product.
- **Minor** — A new user-facing feature or capability (something a user couldn't do before), **or** the removal of a user-facing feature.
- **Patch** — Bug fixes, UI polish, performance, refactors. No new capability.

Before bumping, classify the pending work against this rule and confirm with the user if the requested bump type doesn't match (e.g., user asks for `minor` but no new capability has landed — that's a patch).

## Steps

### 1. Read current version

```bash
cat AndroidGarage/version.properties
```

Parse the `versionName=X.Y.Z` value.

### 2. Compute next version

Apply the increment type to the current version:
- **patch**: increment Z, keep X.Y
- **minor**: increment Y, reset Z to 0, keep X
- **major**: increment X, reset Y and Z to 0

### 3. Update version.properties

Write the new version to the file.

### 4. Create PR

- Branch: `chore/bump-version-X.Y.Z`
- Commit: `chore: Bump versionName to X.Y.Z`
- Create PR with `--base main` and enable auto-merge

## Rules

- Only modify `AndroidGarage/version.properties` — nothing else
- Do not update the changelog (use `/update-android-changelog` for that)
- Confirm the version bump with the user before creating the PR
