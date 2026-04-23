---
description: Release Firebase server functions via tag-based deployment.
---

# Release Firebase

Cut a Firebase server release by creating a tag via `scripts/release-firebase.sh`. The tag triggers CI to deploy Cloud Functions.

## Design principle

**The script's `--check` mode prints the exact command to run next, with SHAs and tag numbers already filled in. Copy and paste that command. Don't retype from memory.** Overrides take a specific value (a SHA from reality) that must match; wrong value = refused.

## Steps

### 1. Pre-flight checks

```bash
git checkout main && git pull
git status  # Must be clean
```

### 2. Run validation

**Always run validation before releasing.** Do not skip this step.

```bash
./scripts/validate-firebase.sh
```

Runs `npm run build` + `npm run tests` (80 tests including collection-name contract tests and verifyIdToken library-chain tests). Auto-switches Node via nvm to the version in `FirebaseServer/.nvmrc`. Writes marker at `.claude/.firebase-validation-passed` with the commit SHA.

### 3. Add a CHANGELOG entry

Edit `FirebaseServer/CHANGELOG.md`. Add a new section keyed by the tag you're about to create:

```markdown
## server/N
- One or more bullets on what shipped
```

Commit and push. The release script refuses to tag when the entry is missing or empty.

**Supersede rule.** If this release supersedes an untested predecessor (bug-chase chain — server/11 shipped broken, server/12 still broken, server/13 finally worked), you may **delete** the predecessor's entry and write a single entry on the final tag. Git log preserves the original content; the visible changelog stays clean.

Skip this step only for true emergencies — then add `--confirm-no-changelog <target-sha>` in step 5 and write the entry after the fact.

### 4. Check release state

```bash
./scripts/release-firebase.sh --check
```

This prints:
- Latest tag and its SHA, next computed tag, HEAD SHA, branch
- Validation state (PASSED/STALE/MISSING)
- Remote Firebase CI status (success/unknown/failed)
- Changelog state (PRESENT/EMPTY/MISSING/NO FILE)
- **A copy-paste-ready command for the scenario** (normal, rollback, emergency)

### 5. Cut the release

Paste the command from step 4. For a normal release:

```bash
./scripts/release-firebase.sh --confirm-tag server/N
```

For emergency release (validation not passing), `--check` prints:

```bash
./scripts/release-firebase.sh \
    --confirm-tag server/N \
    --confirm-unvalidated-release <40-char-sha>
```

For no-changelog release (emergency only), `--check` prints:

```bash
./scripts/release-firebase.sh \
    --confirm-tag server/N \
    --confirm-no-changelog <40-char-sha>
```

For rollback (detached HEAD on older tag), `--check` prints:

```bash
./scripts/release-firebase.sh \
    --confirm-tag server/N \
    --confirm-hash <full-sha-of-target> \
    --confirm-rollback-from <full-sha-of-previous-latest>
```

The script will:
- Verify clean git state (unless `--confirm-hash` is used)
- Verify on main branch (unless `--confirm-hash` is used)
- Verify validation marker matches target commit (unless `--confirm-unvalidated-release` is used)
- Verify `FirebaseServer/CHANGELOG.md` has a non-empty `## server/N` entry (unless `--confirm-no-changelog` is used)
- Verify remote Firebase CI passed (warn-only)
- Create and push the tag; on push failure, remove the local tag
- The tag triggers `.github/workflows/firebase-deploy.yml`

### 6. Verify deployment

```bash
gh run list --workflow=firebase-deploy.yml --limit 1
gh run watch <run-id>
```

Verify the affirmative success marker `✔ Deploy complete!` appears in the deploy log (see `docs/FIREBASE_DEPLOY_SETUP.md` for the silent-failure pattern to watch out for).

## Rollback recipe (two steps — intentionally hard to do accidentally)

```bash
# 1. Move HEAD to the commit you want to re-release.
git checkout server/M

# 2. Print the rollback command and paste it.
./scripts/release-firebase.sh --check
./scripts/release-firebase.sh \
    --confirm-tag server/N \
    --confirm-hash <full-sha-from-check> \
    --confirm-rollback-from <full-sha-from-check>
```

Both SHAs must match what the script computed on the checked-out commit. You can only produce them by running `--check` on the rollback target, which forces you to move HEAD deliberately.

## Rules

- **Never push tags directly** — hooks block `git tag`. Only the release script can create tags.
- **Tag pattern:** `server/N` (e.g., server/1, server/2)
- **Always start with `--check`** — it prints the right command for the current state.
- **Always validate first** — run `./scripts/validate-firebase.sh`.
- **Always write a CHANGELOG entry** — `FirebaseServer/CHANGELOG.md` must have `## server/N` with a non-empty body. Supersede-previous pattern is allowed for bug-chase chains.
- **Don't skip validation without asking.** `--confirm-unvalidated-release <sha>` is for emergencies.
- **Don't skip changelog without asking.** `--confirm-no-changelog <sha>` is for emergencies — add the entry retroactively.
- **Node version is pinned** — `FirebaseServer/.nvmrc` sets it. The `validate-firebase.sh` auto-switches via nvm.
