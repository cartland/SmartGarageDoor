---
description: Release the Android app to Play Store internal track via tag-based deployment.
---

# Release Android

Canonical procedure: `CLAUDE.md` § Releasing Android. This file is the agent-facing operational shortcut — it tells you the exact commands to run in order. The `--check` output is the source of truth for which command to paste.

## The five steps

```bash
# 1. Clean state
git checkout main && git pull && git status   # working tree must be clean

# 2. Validate
./scripts/validate.sh                          # required; the release script blocks on STALE/MISSING

# 3. Read the next-step command from --check
./scripts/release-android.sh --check
# This prints validation state (PASSED/STALE/MISSING) AND a copy-paste-ready command
# for the right scenario (normal, rollback, emergency, unvalidated).

# 4. Paste the command --check printed.
# Normal:    ./scripts/release-android.sh --confirm-tag android/N
# Rollback:  --confirm-tag, --confirm-hash, --confirm-rollback-from (with full SHAs)
# Emergency: --confirm-tag, --confirm-unvalidated-release (with target SHA)

# 5. Watch the deploy
gh run list --workflow=release-android.yml --limit 1
gh run watch <run-id>
```

## What you should NOT do

- **Don't retype flags from memory.** `--check` prints the right command with the right SHAs filled in. Copy-paste prevents wrong-SHA accidents.
- **Don't `git tag` directly.** Hooks block it.
- **Don't deploy to production.** This script only deploys to the Play Store internal track.
- **Don't skip validation without asking the user.** If `--check` shows validation is `STALE` or `MISSING`, the right move is almost always to run `./scripts/validate.sh`. Ask before reaching for `--confirm-unvalidated-release`.

## Versioning

`android/N` tag ↔ `versionCode = N`. The script enforces this. `versionName` is bumped via `/bump-android-version` separately.

## Rollback (two steps, by design)

```bash
git checkout android/M           # move HEAD to the commit you want to re-release
./scripts/release-android.sh --check   # prints the rollback command with the right SHAs
```

Both SHAs in the rollback command must match what `--check` computed on the checked-out commit. You can only produce them by running `--check` on the rollback target, which forces deliberate HEAD movement.

## See also

- `CLAUDE.md` § Releasing Android — full design principle, why each guard exists
- `scripts/release-android.sh` itself — the truth of which flags do what
- `.github/workflows/release-android.yml` — the CI that the tag triggers
