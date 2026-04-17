---
description: Quick health check of the repo — open PRs, CI status, recent releases, open issues, and migration progress.
allowed-tools: Bash(*), Read, Glob, Grep
user-invocable: true
---

# Repo Check

Quick health check of the repo state. Run anytime to get a snapshot of what's happening.

## Steps

### 1. Open PRs

```bash
gh pr list --state open --json number,title,mergeStateStatus,autoMergeRequest \
  --jq '.[] | {number, title, status: .mergeStateStatus, autoMerge: (.autoMergeRequest != null)}'
```

Report: count, any with conflicts (DIRTY), any without auto-merge.

### 2. CI Health

```bash
# Latest post-merge run on main
gh run list --branch main --workflow "Post-Merge CI" --limit 1 \
  --json conclusion,headSha,createdAt \
  --jq '.[0] | {conclusion, sha: .headSha[:7], date: .createdAt[:10]}'

# Any open CI failure issues
gh issue list --label "ci-failure/post-merge" --state open --json number,title --jq '.[]'
```

Report: last post-merge result, any open failure issues.

### 3. Recent Releases

```bash
# Latest Android release
git tag -l 'android/*' --sort=-version:refname | head -1

# Latest Firebase release
git tag -l 'server/*' --sort=-version:refname | head -1
```

### 4. Open Issues

```bash
gh issue list --state open --json number,title,labels --jq '.[] | {number, title, labels: [.labels[].name]}'
```

### 5. Migration Progress

Read `AndroidGarage/docs/MIGRATION.md` and report the Phase Summary table — which phases are complete and what's next.

### 6. Local State

```bash
git status --short
git branch --show-current
```

Report: current branch, clean/dirty, any uncommitted changes.

## Output Format

Summarize as a compact status report:

```
PRs:        N open (N conflicts, N auto-merge)
CI:         passing/failing (last run: date)
Issues:     N open
Releases:   android/N, server/N
Migration:  Phases 1-4,6,7 complete. Next: Phase 5 (KMP)
Local:      main, clean | main, dirty (list changed files)
```

Expand on anything that needs attention (conflicts, failures, stale PRs, uncommitted changes). A dirty working tree is NOT clean — always report it as needing attention.
