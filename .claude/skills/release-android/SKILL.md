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

### 2. Run validation

**Always run validation before releasing.** Do not skip this step.

```bash
./scripts/validate.sh
```

If validation fails, fix the issue before releasing. Do NOT use `--skip-validation` unless the user explicitly confirms they want to release without validation (e.g., emergency rollback).

### 3. Check release state

```bash
scripts/release-android.sh --check
```

This prints the latest tag and the computed next tag (`android/<N+1>`). Verify validation shows "passed".

### 4. Cut the release

```bash
scripts/release-android.sh --confirm-tag android/N
```

Where `N` is the next tag number from step 3. The `--confirm-tag` is a safety check — it must match the computed tag exactly (cannot override).

The script will:
- Verify clean git state
- Verify on main branch
- Verify validate.sh passed on HEAD
- Create and push the tag
- The tag triggers `.github/workflows/release-android.yml`

### 5. Verify deployment

Watch the release workflow:
```bash
gh run list --workflow=release-android.yml --limit 1
gh run watch <run-id>
```

### Rules

- **Never push tags directly** — hooks block `git tag` (except `git tag -l`). Only the release script can create tags.
- **Never deploy to production** — internal track only.
- **Tag version = versionCode** — `android/120` → `versionCode=120`.
- **Always validate first** — run `./scripts/validate.sh` before every release.
- **Never use `--skip-validation` without asking the user.** It exists for emergencies only (rollbacks, hotfixes). If validation hasn't passed, tell the user and ask whether to run validation or skip it.
