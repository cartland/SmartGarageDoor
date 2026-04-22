---
description: Release the Android app to Play Store internal track via tag-based deployment.
---

# Release Android

Cut an Android release by creating a tag via `scripts/release-android.sh`. The tag triggers CI to build and deploy to Play Store internal track (never production).

## Design principle

**The script's `--check` mode prints the exact command to run next, with SHAs and tag numbers already filled in. Copy and paste that command. Don't retype from memory.** Overrides take a specific value (a SHA from reality) that must match; wrong value = refused. Correctness is easy (read from `--check`); accidental correctness is hard.

## Steps

### 1. Pre-flight checks

```bash
git checkout main && git pull
git status  # Must be clean
```

### 2. Run validation

**Always run validation before releasing.** Do not skip this step.

```bash
./scripts/validate.sh
```

If validation fails, fix the issue before releasing. Do NOT skip validation unless the user explicitly confirms they want to release without it (e.g., emergency hotfix).

### 3. Check release state

```bash
./scripts/release-android.sh --check
```

This prints:
- Latest tag and its SHA, next computed tag, HEAD SHA, branch
- Validation state: `PASSED`, `STALE`, or `MISSING`
- **A copy-paste-ready command for the appropriate scenario** (normal, rollback, emergency)

### 4. Cut the release

Paste the command from step 3. For a normal release:

```bash
./scripts/release-android.sh --confirm-tag android/N
```

For an emergency release (validation not passing), `--check` will print:

```bash
./scripts/release-android.sh \
    --confirm-tag android/N \
    --confirm-unvalidated-release <40-char-sha>
```

For a rollback (detached HEAD on an older tag), `--check` will print:

```bash
./scripts/release-android.sh \
    --confirm-tag android/N \
    --confirm-hash <40-char-sha-of-target> \
    --confirm-rollback-from <40-char-sha-of-previous-latest>
```

The script will:
- Verify clean git state (unless `--confirm-hash` is used)
- Verify on main branch (unless `--confirm-hash` is used)
- Verify validation marker matches the target commit (unless `--confirm-unvalidated-release` is used)
- Create and push the tag
- The tag triggers `.github/workflows/release-android.yml`

### 5. Verify deployment

```bash
gh run list --workflow=release-android.yml --limit 1
gh run watch <run-id>
```

## Rollback recipe (two steps — intentionally hard to do accidentally)

```bash
# 1. Move HEAD to the commit you want to re-release.
git checkout android/M

# 2. Print the rollback command and paste it.
./scripts/release-android.sh --check
./scripts/release-android.sh \
    --confirm-tag android/N \
    --confirm-hash <full-sha-from-check> \
    --confirm-rollback-from <full-sha-from-check>
```

Both SHAs must match what the script computed from the current repo state. You can only produce them by actually running `--check` on the checked-out commit, which forces you to move HEAD deliberately.

## Rules

- **Never push tags directly** — hooks block `git tag` (except `git tag -l`). Only the release script can create tags.
- **Never deploy to production** — internal track only.
- **Tag version = versionCode** — `android/120` → `versionCode=120`.
- **Always start with `--check`** — it prints the right command for the current state. Don't type the flags from memory.
- **Always validate first** — run `./scripts/validate.sh` before every release.
- **Never skip validation without asking the user.** `--confirm-unvalidated-release` exists for emergencies (hotfixes, rollbacks of old tags). If validation hasn't passed, tell the user and ask whether to run validation or skip it.
- **`--skip-validation` is deprecated** but still accepted. Prefer `--confirm-unvalidated-release <sha>`. It requires explicitly stating the SHA you're skipping validation on, which prevents accidentally skipping on the wrong commit.
