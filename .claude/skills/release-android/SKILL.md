---
description: Release the Android app to Play Store internal track via tag-based deployment.
---

# Release Android

Cut an Android release by creating a tag via `scripts/release-android.sh`. The tag triggers CI to build and deploy to Play Store internal track (never production).

## Steps

### 1. Pre-flight checks

```bash
# Must be on main with clean tree
git checkout main && git pull
git status  # Must be clean
```

Verify CI is green on latest main:
```bash
gh run list --branch main --limit 1 --json conclusion,headSha --jq '.[0]'
```

### 2. Check release state

```bash
scripts/release-android.sh --check
```

This prints the latest tag and the computed next tag (`android/<N+1>`).

### 3. Cut the release

```bash
scripts/release-android.sh --confirm-tag android/N
```

Where `N` is the next tag number from step 2. The `--confirm-tag` is a safety check — it must match the computed tag exactly (cannot override).

The script will:
- Verify clean git state
- Verify on main branch
- Verify CI passed on HEAD
- Create and push the tag
- The tag triggers `.github/workflows/release-android.yml`

### 4. Verify deployment

Watch the release workflow:
```bash
gh run list --workflow=release-android.yml --limit 1
gh run watch <run-id>
```

### Rules

- **Never push tags directly** — hooks block `git tag` (except `git tag -l`). Only the release script can create tags.
- **Never deploy to production** — internal track only.
- **Tag version = versionCode** — `android/120` → `versionCode=120`.
- **Don't skip CI** — the script verifies CI passed before tagging.
