---
description: Add a changelog entry for the current Android version based on recent commits.
allowed-tools: Bash(*), Read, Edit, Write, Grep, Glob
user-invocable: true
---

# Update Android Changelog

Add an entry to `AndroidGarage/CHANGELOG.md` for the current version based on recent changes.

`CHANGELOG.md` is the **permanent history** — every version (patch, minor, major) gets an entry. The Play Store `whatsnew/whatsnew-en-US` is the rolling, current-version-only counterpart maintained by `/bump-android-version`. When a minor/major ships, the whatsnew text gets distilled here too (or the changelog entry covers the same ground), so older whatsnew lines are never lost when whatsnew is overwritten.

## Steps

### 1. Read current version and changelog

```bash
cat AndroidGarage/version.properties
```

Read `AndroidGarage/CHANGELOG.md` to see existing entries and format.

### 2. Find changes since last release

```bash
# Latest released tag
git tag -l 'android/*' --sort=-version:refname | head -1

# Commits since that tag
git log android/N..HEAD --oneline
```

### 3. Draft changelog entry

Summarize the user-facing changes (not internal refactors or CI fixes). Group by category if there are many changes. Match the style of existing entries — short bullet points, no commit hashes.

Present the draft to the user for review before writing.

### 4. Write the entry

Add the new version section at the top of the changelog (after the header), above the previous version entry.

### 5. Create PR

Create a branch, commit, push, and create a PR with auto-merge.

## Rules

- Only include user-facing changes — skip CI, docs, refactoring, test-only PRs
- Match the existing changelog style (short bullets, no jargon)
- The version number comes from `version.properties`, not from the user
- If the version already has a changelog entry, update it rather than duplicating
- If args are provided, use them as the changelog content instead of inferring from commits
