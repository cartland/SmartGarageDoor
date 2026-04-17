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
