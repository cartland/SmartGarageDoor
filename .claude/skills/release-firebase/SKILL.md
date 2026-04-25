---
description: Release Firebase server functions via tag-based deployment.
---

# Release Firebase

Canonical procedure: `CLAUDE.md` § Releasing Firebase Server. Operational details: `docs/FIREBASE_DEPLOY_SETUP.md`. This file is the agent-facing operational shortcut — `--check` is the source of truth for which command to paste.

## The six steps

```bash
# 1. Clean state
git checkout main && git pull && git status

# 2. Validate
./scripts/validate-firebase.sh
# Auto-switches Node via nvm to FirebaseServer/.nvmrc (Node 22).
# Runs `npm run build` + `npm run tests` (collection-name contracts,
# verifyIdToken library-chain, FCM contract tests).
# Writes marker at .claude/.firebase-validation-passed.

# 3. Add a CHANGELOG entry — REQUIRED by default
# Edit FirebaseServer/CHANGELOG.md, add:
#   ## server/N
#   - One or more bullets describing what shipped
# Commit and push. The release script blocks on missing/empty entries.
# Use `/update-firebase-changelog` to draft.

# 4. Read the next-step command from --check
./scripts/release-firebase.sh --check
# Prints validation state (PASSED/STALE/MISSING), remote CI status,
# changelog state (PRESENT/EMPTY/MISSING), AND a copy-paste-ready
# command for the right scenario.

# 5. Paste the command --check printed.
# Normal:        ./scripts/release-firebase.sh --confirm-tag server/N
# Rollback:      --confirm-tag, --confirm-hash, --confirm-rollback-from
# Emergency:     --confirm-tag, --confirm-unvalidated-release <sha>
# No-changelog:  --confirm-tag, --confirm-no-changelog <sha>

# 6. Watch the deploy AND verify the success marker
gh run list --workflow=firebase-deploy.yml --limit 1
gh run watch <run-id>
# CRITICAL: confirm `✔ Deploy complete!` is in the log. firebase-tools
# can exit 0 with a `⚠ failed to update function` warning — workflow
# shows green but production never updated. See FIREBASE_DEPLOY_SETUP.md
# § "silent-failure pattern".
```

## Supersede rule for the changelog

If a release supersedes an untested predecessor (bug-chase chain — e.g., server/11 broken, server/12 still broken, server/13 finally worked), **delete** the predecessor's entry and write a single entry on the final tag. Git log of `CHANGELOG.md` preserves the original content; the visible changelog stays clean.

## Rollback (two steps, by design)

```bash
git checkout server/M           # move HEAD to the commit you want to re-release
./scripts/release-firebase.sh --check   # prints the rollback command with the right SHAs
```

Both SHAs in the rollback command must match `--check`'s computed values. You can only produce them by running `--check` on the rollback target, which forces deliberate HEAD movement.

## What you should NOT do

- **Don't `git tag` directly.** Hooks block it.
- **Don't skip validation.** `--confirm-unvalidated-release <sha>` is for emergencies — ask the user first.
- **Don't skip the changelog.** `--confirm-no-changelog <sha>` is for emergencies — write the entry retroactively.
- **Don't trust GitHub Actions "success" alone.** Always look for `✔ Deploy complete!` in the deploy log.
- **Don't unquote the mocha test glob** in `package.json` if you touch it. See `CLAUDE.md` § Build Commands for the silent-test-skip story.

## See also

- `CLAUDE.md` § Releasing Firebase Server — full design + rules
- `docs/FIREBASE_DEPLOY_SETUP.md` — operational guide (deploy, rollback, monitoring, GCP setup, troubleshooting table)
- `scripts/release-firebase.sh` — flag reference is in the script header
- `.github/workflows/firebase-deploy.yml` — the CI that the tag triggers
