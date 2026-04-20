# PR Review

Retrospective review of every PR ever merged or closed in this repo. The end goal is to extract current goals, lessons, and patterns — then apply them universally across the Android code.

This document is the durable plan. It survives across many sessions (up to ~100) with no loss of progress. Any session can start cold, read this file, and pick up where the previous session stopped.

## Phases

Work proceeds in four phases. Each phase spans multiple sessions (30+ PRs per session).

1. **Phase 1 — List.** ✅ Complete 2026-04-20. One minimal markdown file per PR capturing goal, files, state, date.
2. **Phase 2 — Assess.** Latest opinion on each PR (great / ok / superseded / buggy / outdated-guidance / abandoned). **Protocol below.**
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

## Session protocol (Phase 2)

A Phase 2 session is one sitting of work, 30+ PR assessments, ending in a single PR merged to main. Mechanics match Phase 1; the per-PR work is judgment, not capture.

### 1. Session start

**Step 1 — Phase 1 catchup first.** Run the Phase 1 queue check (see "Session protocol (Phase 1)"). If there are new PRs needing Phase 1 files, write those first, in the same session branch. Then continue with Phase 2.

**Step 2 — Regenerate the Phase 2 queue.** List all `docs/pr-review/pr-NNNN.md` files that lack a `## Phase 2 assessment` section:

```bash
# One Bash call
grep -L "## Phase 2 assessment" docs/pr-review/pr-*.md | sort -r
```

`-L` prints files that *don't* match. `sort -r` gives newest-first by filename (since filenames are zero-padded PR numbers).

**Step 3 — Create the session branch.** Branch name format: `docs/pr-review-phase2-batch-YYYY-MM-DD-HHMMSS` (UTC). Get timestamp in one Bash call, type it literally in the next.

### 2. For each PR in the queue

**Step 1 — Read the Phase 1 file.** The goal, files, and any notes are the starting point.

**Step 2 — Classify (fast path).** Pick one of six primary labels:

| Label | When |
|---|---|
| **great** | Intent and outcome both correct. Pattern still current. Worth replicating. |
| **ok** | Served its purpose, no issues, nothing exceptional. Most PRs. |
| **superseded** | Correct at the time but later replaced by different work (not a fix — a new approach). |
| **buggy** | Shipped with a bug that was later fixed, OR the code was wrong. |
| **outdated-guidance** | Added docs/ADR/rule we later decided was wrong and reversed. |
| **abandoned** | Closed-unmerged and not replaced. |

**Tie-breaker: latest opinion.** If the current codebase would do it this way → great/ok. If we'd do it differently now → superseded / outdated-guidance. Pattern over code: if the *pattern* still lives even when the specific code was absorbed, primary=great.

**Step 3 — Escalate if ANY of these apply** (don't guess):

1. Phase 1 notes mention supersedence / revert / fix without naming the PR
2. Primary label would be **great** (lesson depends on getting this right)
3. Primary label would be **buggy** or **outdated-guidance** (Phase 4 depends on this)
4. The rationale would read "I think…" or "probably" (genuine uncertainty)
5. Multiple concerns in one PR and unclear which dominates

**Escalation playbook** (stop when sufficient):

1. Read the Phase 1 file fully.
2. Search Phase 1 for later PRs mentioning this one: `grep -l "#NNN" docs/pr-review/pr-*.md`.
3. Check current code state: for "great" candidates, does the pattern still exist? (`git grep`, `Read` on named files). For "outdated-guidance": does the ADR still exist with original wording?
4. Read PR body + comments: `gh pr view <n> --comments`.
5. Read the diff (last resort): `gh pr diff <n>`.

**Step 4 — Write the assessment section.** Append to the existing `docs/pr-review/pr-NNNN.md`:

```markdown

## Phase 2 assessment

**Primary:** great
**Secondary:** [ok]
**Phase 2 reviewed:** 2026-04-20

**Rationale:** <one sentence — why this label>

**Superseded by:** #xxx           <!-- only if superseded -->
**Fixed by:** #xxx                 <!-- only if buggy -->
**Reversed by:** #xxx or ADR-xxx   <!-- only if outdated-guidance -->

**Still-current lesson:** <one sentence — only if novel/non-obvious AND not already codified in an ADR/guide/lint rule; omit otherwise>
```

**Secondary labels** (frontmatter `[list]`) catch mixed content. Example: `primary: great, secondary: [ok]` for a PR whose main pattern is great but consumer updates are routine. Use `[]` if nothing secondary applies.

**Still-current lesson — HIGH bar.** Skip if:
- Already in an ADR (`AndroidGarage/docs/DECISIONS.md`)
- Already in a guide (`AndroidGarage/docs/guides/`)
- Already enforced by a lint rule (`buildSrc/.../architecture/*`)
- Obvious to any Android developer

Include only genuinely novel, non-obvious lessons that Phase 4 could propagate.

**Step 5 — Commit:**

```bash
git add docs/pr-review/pr-NNNN.md
git commit -m "pr-review-phase2: PR #NNNN — <primary-label>"
```

One commit per file.

### 3. Session stop

Stop when **any** of these applies:
- 30+ assessments done this session AND context usage is getting heavy
- Queue empty (Phase 2 is complete — see end condition below)
- User says stop

**Create the session PR** (same pattern as Phase 1):

```bash
git push -u origin docs/pr-review-phase2-batch-YYYY-MM-DD-HHMMSS
gh pr create --base main --title "docs: PR review phase 2 batch YYYY-MM-DD (PRs #X–#Y, N assessments)" --body "<body>"
gh pr merge --auto --squash --delete-branch <n>
```

---

## Phase 2 end condition

Phase 2 is complete when a session starts, runs the Phase 2 queue check, and finds **zero** `pr-NNNN.md` files without a `## Phase 2 assessment` section (and no Phase 1 catchup needed).

At that point:
1. Add a note to this README's top: `**Phase 2 complete as of YYYY-MM-DD.**`
2. Open a new session (clean context) to design Phase 3 via clarifying questions.
3. Commit that design session's output as a Phase 3 section in this README.

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

- **Phase 1 (complete):** 381 PRs, 26 sessions, finished 2026-04-20.
- **Phase 2:** At 30+ assessments per session, ~13 sessions to cover 381 PRs. Escalation is per-PR, not per-session, so most sessions stay on-pace.
