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

### 4. (minor/major only) Draft the Play Store whatsnew entry

For **minor** and **major** bumps, the Play Store gets a release note. For **patch** bumps, skip this step — patches roll up into the previous minor/major entry.

The whatsnew file `AndroidGarage/distribution/whatsnew/whatsnew-en-US` is a flat list, newest first, one line per minor/major:

```
X.Y: Short user-facing description (1–2 sentences).
2.4: Redesigned garage door button with confirmation flow and network status diagram. Improved color contrast for accessibility.
2.3: Improved architecture and performance.
```

To draft the entry:

1. Read the current whatsnew file to see the established style.
2. Find the commits since the last whatsnew entry (`git log --oneline <last-tag>..HEAD`) — focus on user-visible changes only.
3. Draft a 1–2 sentence description in the same style. Match the tone of recent entries: direct, plain language, no jargon, no "we"/"I."
4. **Present the draft to the user and wait for confirmation or edits before writing it.** Offer 2–3 phrasings if the right wording isn't obvious.
5. After the user confirms, prepend the line to `whatsnew-en-US` (newest at top — note the line uses `X.Y`, not `X.Y.Z`).

### 5. Create PR

- Branch: `chore/bump-version-X.Y.Z`
- Commit: `chore: Bump versionName to X.Y.Z` — for minor/major, the same commit also includes the whatsnew prepend
- Create PR with `--base main` and enable auto-merge

## Rules

- Modify only `AndroidGarage/version.properties` (always) and `AndroidGarage/distribution/whatsnew/whatsnew-en-US` (minor/major only) — nothing else
- Do not update the changelog (use `/update-android-changelog` for that)
- Patches must not touch whatsnew — they roll up
- Confirm both the version bump *and* the whatsnew description with the user before creating the PR
