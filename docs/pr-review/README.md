# PR Review

**Phase 1 complete as of 2026-04-20.** All 381 PRs reviewed across 26 sessions. Ready for Phase 2 design.

Retrospective review of every PR ever merged or closed in this repo. The end goal is to extract current goals, lessons, and patterns — then apply them universally across the Android code.

This document is the durable plan. It survives across many sessions (up to ~100) with no loss of progress. Any session can start cold, read this file, and pick up where the previous session stopped.

## Phases

Work proceeds in four phases. Each phase spans multiple sessions (10–30 PRs per session).

1. **Phase 1 — List (current).** Write one minimal markdown file per PR capturing goal, files touched, state, date. This is a mechanical pass, not a judgment pass.
2. **Phase 2 — Assess.** Latest opinion on each PR (great / ok / superseded / buggy / outdated guidance). *Protocol designed in a fresh session after Phase 1 completes.*
3. **Phase 3 — Summarize.** Extract the still-current goals and lessons from Phase 2 into a compact doc. *Protocol designed after Phase 2 completes.*
4. **Phase 4 — Apply.** Plan to apply the current lessons universally across Android code. *Protocol designed after Phase 3 completes.*

Phases are designed incrementally so each one can be informed by the output of the previous one.

---

## Session protocol (Phase 1)

A session is one sitting of work, 10–30 PR reviews, ending in a single PR merged to main.

### 1. Session start

**Step 1 — Regenerate the queue.**

```bash
gh pr list --state all --limit 9999 --json number,title,state,mergedAt,closedAt,baseRefName > /tmp/pr-queue.json
```

**Step 2 — Filter the queue client-side.** Work only with PRs where:
- `state` is `MERGED` or `CLOSED` (exclude `OPEN` — they're moving targets; they'll be reviewed after they reach a terminal state)
- No file `docs/pr-review/pr-NNNN.md` exists (NNNN is the PR number zero-padded to 4 digits)
- The loop-breaker rule (below) doesn't exclude the PR

Sort the remaining PRs by number **descending** (newest first).

**Step 3 — Apply the loop-breaker.** For each candidate PR, run:

```bash
gh pr view <n> --json files --jq '[.files[].path] | all(startswith("docs/pr-review/"))'
```

If the result is `true`, skip — it's a session-generated PR that only modified this directory. Do not write a review file for it.

**Step 4 — Create the session branch.** Branch name format: `docs/pr-review-batch-YYYY-MM-DD-HHMMSS` (UTC). Every session must use a unique timestamp, even when two sessions happen the same day.

Do **not** use shell command substitution (`$(...)`) — the git-guardrails hook flags it for approval. Instead, get the timestamp in one Bash call, then type it literally in the next:

```bash
# First call — just get the timestamp
date -u +%Y-%m-%d-%H%M%S
```

Then run (filling in the literal timestamp from the previous output):

```bash
git checkout -b docs/pr-review-batch-2026-04-19-123456 origin/main
```

### 2. For each PR in the queue

**Step 1 — Fetch the PR metadata in one call:**

```bash
gh pr view <n> --json number,title,body,state,mergedAt,closedAt,files,baseRefName
```

**Step 2 — Infer the goal.** Write one sentence describing what this PR was trying to achieve. Use the title, body, and file paths as primary signals.

**Step 3 — If still unclear, spend up to ~2 minutes of extra research:**
- Read top-level commit messages: `gh api repos/cartland/SmartGarageDoor/pulls/<n>/commits --jq '.[].commit.message'`
- Read comments: `gh pr view <n> --comments`

If after this the goal is still unclear, set `unclear: true` in frontmatter and describe what's known in `**Notes:**`. Move on — Phase 2 can revisit.

**Step 4 — Write the review file** at `docs/pr-review/pr-NNNN.md` (zero-padded to 4 digits):

```markdown
---
pr: 123
state: merged
date: 2026-04-19
phase1_reviewed: 2026-04-20
---

# PR #123: <exact PR title>

**Goal:** <one sentence>

**Files:** <one-line summary, e.g., "AndroidGarage/data/**, 5 files — Ktor data sources">
```

**Optional fields:**
- `unclear: true` in frontmatter — only when goal can't be determined even after extra research
- `**Notes:**` section below Files — only when something surprising or ambiguous is worth recording for Phase 2

**Date fields:**
- `state: merged` → `date:` is the `mergedAt` date
- `state: closed` → `date:` is the `closedAt` date
- `phase1_reviewed:` is today's date (UTC)

**Step 5 — Commit:**

```bash
git add docs/pr-review/pr-NNNN.md
git commit -m "pr-review: PR #NNNN — <short PR title>"
```

One commit per file. This preserves git-blame value.

### 3. Session stop

Stop when **any** of these applies:
- At least 10 files have been created this session AND context usage is getting heavy
- 30 files have been created this session (hard upper bound)
- The queue is empty (Phase 1 is complete — see end condition below)
- The user says stop

**Create the session PR:**

```bash
git push -u origin docs/pr-review-batch-YYYY-MM-DD-HHMMSS
gh pr create --base main --title "docs: PR review batch YYYY-MM-DD (PRs #LOWEST–#HIGHEST, N files)" --body "<body>"
gh pr merge --auto --squash --delete-branch <n>
```

PR body template:

```
## Summary
Phase 1 PR review batch — N files. Reviews PRs #X through #Y (descending; newest-first ordering).

## Test plan
- [x] Only touches `docs/pr-review/**` (self-excludes from review queue via loop-breaker rule)
- [x] Each file follows schema in `docs/pr-review/README.md`
```

---

## Loop-breaker rule

Every session generates a PR that modifies `docs/pr-review/**`. Without a rule, those PRs would themselves appear in the queue → infinite loop.

**Rule:** Before reviewing a PR, check whether **all** of its files are under `docs/pr-review/`:

```bash
gh pr view <n> --json files --jq '[.files[].path] | all(startswith("docs/pr-review/"))'
```

If `true`, skip. No review file is created. No bookkeeping needed.

This rule is content-based (not branch-name or label-based), so it's robust across future phases. If Phase 4 produces PRs that touch Android code, those **will** be reviewable — because they're "real" work, not self-referential bookkeeping.

---

## Phase 1 end condition

Phase 1 is complete when a session starts, regenerates the queue, applies the loop-breaker, and finds **zero** PRs needing review.

At that point:
1. Add a note to this README's top: `**Phase 1 complete as of YYYY-MM-DD.**`
2. Open a new session (clean context) to design Phase 2 via clarifying questions (same pattern as Phase 1 was designed).
3. Commit that design session's output as a Phase 2 section in this README.

**Convergence guarantee:** New PRs that merge between sessions naturally land at the top of the next session's queue (they have high numbers). They get reviewed in the next session. The end condition is self-healing — it only fires when the queue is genuinely empty.

---

## File schema (strict)

Each PR file at `docs/pr-review/pr-NNNN.md`:

- **Filename:** Zero-padded to 4 digits (supports up to PR #9999). Example: `pr-0123.md`, `pr-0001.md`, `pr-0383.md`.
- **Frontmatter (required):** `pr`, `state`, `date`, `phase1_reviewed`.
- **Frontmatter (optional):** `unclear: true`.
- **H1 (required):** `# PR #N: <title>` — N is unpadded.
- **Goal (required):** `**Goal:** <one sentence>`.
- **Files (required):** `**Files:** <one-line summary>`.
- **Notes (optional):** `**Notes:** <one paragraph>` — only when useful for Phase 2.

Phase 2+ will append `## Phase N assessment` sections below. **Never edit Phase 1 sections retroactively** — if Phase 1 got something wrong, note the correction in the Phase 2 section.

---

## Handling new PRs between sessions

New PRs merged since the last session naturally appear at the top of the queue (highest numbers, newest-first ordering). No special-case handling. The queue is regenerated fresh every session.

If a new PR is merged **during** Phases 2/3/4, it lacks a Phase 1 file. Phase 2+ sessions must first catch up any missing Phase 1 entries before continuing current-phase work.

---

## Branch and PR conventions

- **Branch name:** `docs/pr-review-batch-YYYY-MM-DD-HHMMSS` — UTC timestamp ensures uniqueness even on same-day re-runs.
- **One PR per session.** Don't mix PR-review batches.
- **Auto-merge squash:** `gh pr merge --auto --squash --delete-branch <n>`.
- **No shell command substitution** (`$(...)`) in Bash invocations — the guardrails hook flags it. Get values in one Bash call, type them literally in the next.
- **Base branch:** Always `--base main` on PR creation. Stacked PRs are not used here (sessions are sequential, not stacked).

---

## What NOT to do

- **Do not** review PRs in non-terminal states (`OPEN`). They'll be reviewed once they merge or close.
- **Do not** skip a PR because it's "boring" — "all PRs ever, no sampling" is the rule. A one-line review is still a review.
- **Do not** write deep assessments in Phase 1. Save judgment for Phase 2.
- **Do not** edit prior Phase 1 files retroactively. Corrections go in Phase 2 sections.
- **Do not** create per-PR GitHub PRs. One PR per session batches all reviews.
- **Do not** rely on the branch name for loop-breaking. The content rule (`all paths under docs/pr-review/`) is authoritative.

---

## Rough budget

At 381 PRs (365 merged + 16 closed as of this plan's creation) and ~20 PRs per session, Phase 1 fits in approximately **19 sessions**. New PRs merged during Phase 1 add marginal work (they show up at the top of the queue next session).
