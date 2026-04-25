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

### 4. (minor/major only) Replace the Play Store whatsnew

For **minor** and **major** bumps, the Play Store gets a release note. For **patch** bumps, skip this step — patches roll up into the next minor/major's entry.

`AndroidGarage/distribution/whatsnew/whatsnew-en-US` is **rolling, current-version-only**. Replace its entire content with a single entry for the new version. CHANGELOG.md is the permanent history (every version, including patches) — older whatsnew lines are already preserved there.

Format:

```
X.Y: Short user-facing description.
```

- Single line preferred (matches the rest of the Play Store ecosystem).
- Multiline allowed for minors when one line can't capture the change. Major releases can use multiple lines.
- Whole file must stay under **500 bytes** (`scripts/check-whatsnew-length.sh` enforces; `validate.sh` calls it). Google Play rejects the upload otherwise — see release-failure tombstone `android/177` for the original incident.

To draft:

1. Find user-visible commits since the last whatsnew/release: `git log --oneline <last-tag>..HEAD`.
2. Draft in the same style as recent CHANGELOG entries: direct, plain language, no jargon, no "we"/"I."
3. **Present the draft to the user and wait for confirmation or edits before writing.** Offer 2–3 phrasings if the right wording isn't obvious. Confirm length stays under 500 bytes.
4. After the user confirms, **overwrite** `whatsnew-en-US` with just the new entry. Do not prepend; do not preserve older lines. (CHANGELOG.md owns history.)

### 5. Create PR

- Branch: `chore/bump-version-X.Y.Z`
- Commit: `chore: Bump versionName to X.Y.Z` — for minor/major, the same commit also includes the whatsnew replacement
- Create PR with `--base main` and enable auto-merge

## Rules

- Modify only `AndroidGarage/version.properties` (always) and `AndroidGarage/distribution/whatsnew/whatsnew-en-US` (minor/major only) — nothing else
- Whatsnew is rolling — **replace**, never accumulate. CHANGELOG.md is the permanent history.
- Do not update the changelog here (use `/update-android-changelog` for that)
- Patches must not touch whatsnew — they roll up into the next minor/major
- Confirm both the version bump *and* the whatsnew description with the user before creating the PR
