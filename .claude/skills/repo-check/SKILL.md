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
# Latest Android post-merge run on main
gh run list --branch main --workflow "Android Post-Merge CI" --limit 1 \
  --json conclusion,headSha,createdAt \
  --jq '.[0] | {conclusion, sha: .headSha[:7], date: .createdAt[:10]}'

# Latest Firebase post-merge run on main
gh run list --branch main --workflow "Firebase Post-Merge CI" --limit 1 \
  --json conclusion,headSha,createdAt \
  --jq '.[0] | {conclusion, sha: .headSha[:7], date: .createdAt[:10]}'

# Any open CI failure issues (both labels) — title includes which sub-job
# failed in the form `(checks: <status>, instrumented: <status>)`.
gh issue list --label "ci-failure/post-merge" --state open --json number,title --jq '.[]'
gh issue list --label "ci-failure/firebase-post-merge" --state open --json number,title --jq '.[]'
```

If any CI failure issue is open, **also fetch its body to identify the
source PR and commit** — the auto-created issue lists them under fixed
fields:

```bash
# For each open failure issue, surface "Source: PR #N" + commit SHA.
# The auto-issue body has fixed `- **Run:**`, `- **Commit:**`, `- **Source:**`
# bullets — match by the colon-suffixed field name (BSD grep's ERE parser
# refuses `\*\*(...)\*\*` directly; the colon-anchored form works).
for n in $(gh issue list --label "ci-failure/post-merge" --state open --json number --jq '.[].number'; gh issue list --label "ci-failure/firebase-post-merge" --state open --json number --jq '.[].number'); do
  echo "Issue #$n:"
  gh issue view "$n" --json body --jq '.body' \
    | grep -E '^- \*\*(Source|Commit|Run):' \
    | sed 's/^- \*\*//; s/\*\*//'
  echo
done
```

Why this matters: the bare issue title only says "checks: X, instrumented:
Y" — useful for which sub-job failed but not which PR introduced it. The
body always carries `**Source:** PR #N (<title>)` and `**Commit:** SHA`,
which is what you need to decide if the failure already has a fix in
flight (e.g. a follow-up PR fixing the same test). Always pull these for
open failure issues.

Report: last post-merge result for each, any open failure issues, and
the source PR + commit for each open failure. The exact workflow names
matter — `gh run list --workflow "Post-Merge CI"` (without the platform
prefix) returns nothing.

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
CI:         passing/failing (last run: date) [if failing, name the failed sub-job]
Issues:     N open [if any are CI failures, name source PR per issue: "#701 ← PR #700"]
Releases:   android/N, server/N
Migration:  Phases 1-4,6,7 complete. Next: Phase 5 (KMP)
Local:      main, clean | main, dirty (list changed files)
```

Expand on anything that needs attention (conflicts, failures, stale PRs,
uncommitted changes). A dirty working tree is NOT clean — always report
it as needing attention.

For open CI failure issues, **always state the source PR**. A user reading
"1 issue open" learns nothing actionable; "issue #701 ← PR #700 (instrumented test)"
tells them whether the failure already has a fix in flight (look for an
open PR touching the same test or area) or needs a new investigation.
