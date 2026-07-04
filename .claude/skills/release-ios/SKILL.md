---
description: Release the iOS app to TestFlight Internal via tag-based deployment.
---

# Release iOS

Canonical procedure: `CLAUDE.md` § Releasing iOS. Full runbook (Apple/App Store Connect setup, secret-vs-committed map, build-numbering, gotchas): `docs/IOS_RELEASE_SETUP.md`. This file is the agent-facing operational shortcut — `--check` is the source of truth for which command to paste.

## The six steps

```bash
# 1. Clean state
git checkout main && git pull && git status

# 2. Validate
./scripts/validate-ios.sh
# Writes marker at .claude/.ios-validation-passed.

# 3. Add a CHANGELOG entry — REQUIRED by default
# Edit MobileGarage/iosApp/CHANGELOG.md, add:
#   ## X.Y.Z          (X.Y.Z = current MARKETING_VERSION from project.yml)
#   - One or more bullets describing user-facing changes
# Commit and push. The release script blocks on missing/empty entries.

# 4. Read the next-step command from --check
./scripts/release-ios.sh --check
# Prints validation state (PASSED/STALE/MISSING), MARKETING_VERSION,
# changelog state (PRESENT/EMPTY/MISSING), AND a copy-paste-ready command
# for the right scenario (normal, rollback, emergency, unvalidated,
# no-changelog).

# 5. Paste the command --check printed.
# Normal:        ./scripts/release-ios.sh --confirm-tag ios/N
# Rollback:      --confirm-tag, --confirm-hash, --confirm-rollback-from (with full SHAs)
# Emergency:     --confirm-tag, --confirm-unvalidated-release (with target SHA)
# No-changelog:  --confirm-tag, --confirm-no-changelog <sha>

# 6. Watch the deploy
gh run list --workflow=release-ios.yml --limit 1
gh run watch <run-id>
```

## What you should NOT do

- **Don't retype flags from memory.** `--check` prints the right command with the right SHAs filled in. Copy-paste prevents wrong-SHA accidents.
- **Don't `git tag` directly.** Hooks block it.
- **Don't deploy to the public App Store.** This script only uploads to TestFlight Internal. Promotion to App Store review is a manual App Store Connect step.
- **Don't skip validation without asking the user.** If `--check` shows validation is `STALE` or `MISSING`, the right move is almost always to run `./scripts/validate-ios.sh`. Ask before reaching for `--confirm-unvalidated-release`.
- **Don't skip the changelog.** `MobileGarage/iosApp/CHANGELOG.md` is the permanent history. `--confirm-no-changelog <sha>` is for emergencies — write the entry retroactively.
- **Don't hand-pick the build number from a doc.** `ios/N`'s suggested `N` is git-tags-only advisory; App Store Connect is the authoritative source (it can run ahead of git if a build was uploaded out-of-band or a release failed after archiving). Trust `--check`'s live computation, not a remembered value.

## Versioning

`ios/N` tag ↔ `CFBundleVersion (build number) = N`. `MARKETING_VERSION` (X.Y.Z, in `MobileGarage/iosApp/project.yml`) is the CHANGELOG key, bumped by hand alongside the changelog entry — same split as Android's `versionCode`/`versionName`.

`--confirm-tag` accepts any strictly-higher `ios/N` than the highest local git tag (skip-ahead), because the **authoritative** build-number check is the CI pre-flight (`scripts/asc-latest-build.rb`), not the local script — App Store Connect silently renumbers a duplicate build instead of failing, so a collision needs a higher number, and only CI can see what's actually taken.

## Rollback (two steps, by design)

```bash
git checkout ios/M              # move HEAD to the commit you want to re-release
./scripts/release-ios.sh --check   # prints the rollback command with the right SHAs
```

Both SHAs in the rollback command must match what `--check` computed on the checked-out commit. You can only produce them by running `--check` on the rollback target, which forces deliberate HEAD movement.

## See also

- `CLAUDE.md` § Releasing iOS — shipped status, load-bearing setup points
- `docs/IOS_RELEASE_SETUP.md` — full runbook (Apple/ASC setup, secrets, build-numbering, gotchas)
- `scripts/release-ios.sh` itself — the truth of which flags do what
- `.github/workflows/release-ios.yml` — the CI that the tag triggers (pinned `macos-26` for the iOS 26 SDK requirement; verifies the tagged commit is on `main` before touching any secrets)
