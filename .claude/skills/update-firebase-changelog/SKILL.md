---
description: Add a Firebase server changelog entry for the next server/N tag based on commits since the last tag.
allowed-tools: Bash(*), Read, Edit, Write, Grep, Glob
user-invocable: true
---

# Update Firebase Changelog

Draft and add an entry to `FirebaseServer/CHANGELOG.md` for the next `server/N` tag, based on commits since the latest tag. The release script (`scripts/release-firebase.sh`) requires this entry before it will cut a tag.

## Steps

### 1. Compute next tag

```bash
git tag -l 'server/*' --sort=-version:refname | head -1
```

The next tag is `server/<N+1>`.

### 2. Read existing changelog

Read `FirebaseServer/CHANGELOG.md` to see style and the most recent entries.

### 3. Find changes since last tag

```bash
# Latest released tag
LATEST=$(git tag -l 'server/*' --sort=-version:refname | head -1)

# Commits since that tag
git log "$LATEST..HEAD" --oneline
```

### 4. Draft the entry

- 1-4 bullets, matching the existing style
- Focus on what matters 6 months later, not the full commit list
- Skip pure internal noise (CI tweaks, dep bumps without behavior change, refactors)
- If the release is ALL noise, write `Release with no behavior changes.` followed by a one-line summary of what the infra/dep change was
- No commit hashes, no PR numbers

Present the draft to the user before writing.

### 5. Apply the supersede rule if needed

If the previous tag's entry was an untested predecessor (the current release exists because it broke), offer to **delete** the predecessor's entry and replace it with a single entry on the new tag. Ask the user first — this is opt-in.

Example prompt: "server/11 was a broken fix attempt. Replace its entry with a single server/12 entry that covers both? (Recommended for bug-chase chains.)"

### 6. Write the entry

Insert the new `## server/N` section at the top of the release-history block — above the previous `## server/<N-1>` entry, below the `---` separator.

### 7. Create PR

Create a branch, commit, push, and create a PR with auto-merge. Title: `docs: Changelog for server/N`.

## Rules

- The tag number comes from `git tag -l`, not the user
- Match existing style (short bullets, no jargon, no commit hashes)
- If args are provided, use them as the entry content instead of inferring from commits
- Never write an empty entry — the release script will block the tag push
- For a release with no behavior changes, be explicit: `Release with no behavior changes. <one-line reason>.`
