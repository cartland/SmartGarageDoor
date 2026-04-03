---
description: Capture session knowledge into project documentation before session ends.
---

# Dump Context

Capture conversation knowledge into durable artifacts before a session ends or context is compacted.

## When to Use

- Before ending a session where significant work was done
- When context window is getting large and compaction is likely
- After completing a complex investigation or multi-step task

## Steps

### Phase 1: Scan Conversation

Review the conversation and categorize knowledge:

1. **Actionable items** — tasks, bugs, follow-ups not yet tracked
2. **Decisions** — design choices, trade-offs, patterns established
3. **Operational knowledge** — workarounds, commands, mistakes corrected
4. **Status** — what's completed, in progress, blocked

Write a brief summary before proceeding.

### Phase 2: Update Documentation

Update these files with knowledge from the conversation:

#### `CLAUDE.md` — Agent Instructions
- New workflow rules or conventions
- Changes to CI/hooks/validation process
- Mistakes to avoid (especially incorrect state reported to user)

#### `AndroidGarage/docs/TESTING.md` — Testing Plan
- Mark completed phases
- Add new test gaps discovered
- Update test counts

#### `AndroidGarage/docs/DECISIONS.md` — Architecture Decisions
- New ADRs from decisions made during session
- Updates to existing ADRs

#### `AndroidGarage/docs/MIGRATION.md` — Migration Progress
- Mark completed migration steps
- Add new steps discovered

### Phase 3: Update Memory

Save session-specific learnings to memory files:

```
~/.claude/projects/-Users-cartland-github-cartland-SmartGarageDoor/memory/
```

- **feedback_*.md** — Corrections, validated approaches, workflow preferences
- **project_*.md** — Project state, ongoing work, decisions
- Update `MEMORY.md` index

### Phase 4: Check Open PRs

Before committing, check and report on open PR status:

```bash
gh pr list --state open --json number,title,mergeStateStatus,autoMergeRequest
```

- Note any PRs with merge conflicts (DIRTY)
- Note any PRs without auto-merge
- Update branches if behind: `gh pr update-branch <number>`

### Phase 5: Commit via PR

1. Create branch from latest main:
   ```bash
   git checkout -b docs/dump-context-YYYY-MM-DD origin/main
   ```

2. Stage and commit:
   ```bash
   git add CLAUDE.md AndroidGarage/docs/
   git commit -m "docs: Dump session context — [brief summary]"
   ```

3. Push and create PR:
   ```bash
   git push -u origin docs/dump-context-YYYY-MM-DD
   gh pr create --title "docs: Dump session context" --body "..."
   gh pr merge --auto --squash --delete-branch
   ```

## Tips

- Don't put session-specific state in docs — only document confirmed knowledge
- Be specific about what changed and why
- If the session was trivial, skip phases that don't apply
- Check for merge conflicts on all open PRs before finishing
