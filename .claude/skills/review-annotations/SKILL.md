---
description: Review GitHub Actions annotations from recent CI runs. Surfaces new warnings and suggests fixes or suppressions.
allowed-tools: Bash(*), Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Review CI Annotations

Review GitHub Actions annotations from recent CI runs to catch warnings before they become urgent.

## Steps

### 1. Fetch annotations from latest main run

```bash
# Get latest commit on main
LATEST_SHA=$(gh api repos/OWNER/REPO/branches/main --jq '.commit.sha')

# Get all check runs with annotations
gh api "repos/OWNER/REPO/commits/$LATEST_SHA/check-runs" \
  --jq '.check_runs[] | select(.output.annotations_count > 0) | {id, name, count: .output.annotations_count}'

# For each check run, fetch annotations
gh api "repos/OWNER/REPO/check-runs/CHECK_RUN_ID/annotations" \
  --jq '.[] | {level: .annotation_level, message: .message, path, line: .start_line}'
```

### 2. Filter against ignore list

Compare each annotation message against `.github/annotation-ignores.txt`. Report only unignored annotations.

### 3. For each new annotation, recommend one of:

- **Fix** — if it's actionable (e.g., update a dependency, fix a deprecation)
- **Ignore** — add pattern to `.github/annotation-ignores.txt` with a comment explaining why
- **Defer** — note it but don't act yet (e.g., waiting for upstream fix)

### 4. Report summary

List all annotations grouped by status:
- New (needs action)
- Ignored (in ignore list)
- Fixed (since last review)

## Notes

- Run this periodically, not on every CI run
- The ignore list uses simple string matching (grep -F)
- Focus on warnings — errors should already be caught by CI failure tracking
