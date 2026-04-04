---
description: Release Firebase server functions via tag-based deployment.
---

# Release Firebase

Cut a Firebase server release by creating a tag via `scripts/release-firebase.sh`. The tag triggers CI to deploy Cloud Functions.

## Steps

### 1. Pre-flight checks

```bash
# Must be on main with clean tree
git checkout main && git pull
git status  # Must be clean
```

Verify Firebase CI is green on latest main:
```bash
gh run list --branch main --workflow=firebase-ci.yml --limit 1 --json conclusion,headSha --jq '.[0]'
```

### 2. Check release state

```bash
scripts/release-firebase.sh --check
```

This prints the latest tag and the computed next tag (`server/<N+1>`).

### 3. Cut the release

```bash
scripts/release-firebase.sh --confirm-tag server/N
```

Where `N` is the next tag number from step 2. The `--confirm-tag` is a safety check — it must match the computed tag exactly.

The script will:
- Verify clean git state
- Verify on main branch
- Verify Firebase CI passed on HEAD
- Create and push the tag
- The tag triggers `.github/workflows/firebase-deploy.yml`

### 4. Verify deployment

Watch the deploy workflow:
```bash
gh run list --workflow=firebase-deploy.yml --limit 1
gh run watch <run-id>
```

### Rules

- **Never push tags directly** — hooks block `git tag`. Only release scripts can create tags.
- **Tag pattern:** `server/N` (e.g., server/1, server/2)
- **Don't skip CI** — the script verifies Firebase CI passed before tagging.
