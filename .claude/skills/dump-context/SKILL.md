---
description: Capture session knowledge into project documentation before session ends.
---

# Dump Context

Capture conversation knowledge into durable artifacts before a session ends or context is compacted.

## Priority: repo first, memory second

**The repo is where long-lived knowledge lives.** `CLAUDE.md`, `AndroidGarage/docs/`, `FirebaseServer/docs/`, `docs/`, and code-adjacent `*/README.md` files all survive machine rebuilds, benefit from PR review, and are discoverable by anyone on the project (including future-you, reading the repo cold).

Memory is only for information that is **machine-local or session-pointer in nature**:

- User's tool preferences on this dev box (e.g. "they use `nvm` not `asdf`")
- Paths, shell aliases, IDE state specific to this machine
- Short-lived pointers that will rot (e.g. "currently tracking bug in PR #N") — but only when a repo doc would be wrong
- Inbox-style reminders that don't belong in a reviewed doc

**When in doubt, put it in the repo.** A paragraph in CLAUDE.md that everyone can see is more valuable than a memory entry only the current session recalls. Memory can never be reviewed by a human in a PR.

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

For each item, decide: **repo doc, code comment, or memory?** Default to repo. Memory only for truly local/transient content.

Write a brief summary before proceeding.

### Phase 2: Update Repo Docs (PRIMARY)

This is the main phase. Most dump-context work should land here.

#### `CLAUDE.md` — Agent Instructions
- New workflow rules or conventions
- Changes to CI / hooks / validation process
- Mistakes to avoid (especially incorrect state reported to user)
- New scripts, commands, or safety rules

#### Component docs
- `AndroidGarage/docs/TESTING.md` — testing plan, completed phases, test gaps
- `AndroidGarage/docs/DECISIONS.md` — new ADRs from decisions made during the session
- `AndroidGarage/docs/MIGRATION.md` — migration progress
- `FirebaseServer/CHANGELOG.md` — every release entry (gate-enforced)
- `FirebaseServer/docs/*.md` — Firebase-server-specific patterns and plans
- `docs/FIREBASE_DEPLOY_SETUP.md`, `docs/FIREBASE_DATABASE_REFACTOR.md` — cross-component runbooks

#### Code-adjacent
- Header comments in scripts and workflow files — ordering rules, coupling notes
- Module doc comments — shape/pattern conventions (e.g. the service pattern in `*Database.ts` / `*FCM.ts`)
- `test/fakes/README.md` — fake-pattern conventions

### Phase 3: Update Memory (SECONDARY, narrow use)

Only use memory for content that doesn't belong in the repo. Before writing a memory entry, ask: "Could this go in CLAUDE.md or a docs file instead?" If yes, put it there.

```
~/.claude/projects/-Users-cartland-github-cartland-SmartGarageDoor/memory/
```

- **feedback_*.md** — user's machine-specific preferences (e.g. "uses nvm, pins Node 22")
- **project_*.md** — session-pointer state that would be wrong in a reviewed doc (e.g. "currently mid-investigation of PR #N")
- Update `MEMORY.md` index

Avoid creating memory entries that duplicate content already in CLAUDE.md or repo docs — those are redundant at best and drift-prone at worst.

### Phase 4: Check Open PRs

Before committing, check and report on open PR status:

```bash
gh pr list --state open --json number,title,mergeStateStatus,autoMergeRequest
```

- Note any PRs with merge conflicts (DIRTY)
- Note any PRs without auto-merge
- Update branches if behind: `gh pr update-branch <number>`

### Phase 5: Commit via PR

If Phase 2 produced repo changes (expected for most dumps):

1. Create branch from latest main:
   ```bash
   git checkout -b docs/dump-context-YYYY-MM-DD origin/main
   ```

2. Stage and commit:
   ```bash
   git add CLAUDE.md AndroidGarage/docs/ FirebaseServer/ docs/
   git commit -m "docs: Dump session context — [brief summary]"
   ```

3. Push and create PR:
   ```bash
   git push -u origin docs/dump-context-YYYY-MM-DD
   gh pr create --title "docs: Dump session context" --body "..."
   gh pr merge --auto --squash --delete-branch
   ```

If the dump is memory-only (rare), the repo PR step is skipped — but think twice first. Most session knowledge earns its place in the repo.

## Tips

- Don't put session-specific state in docs — only document confirmed knowledge
- Be specific about what changed and why
- If the session was trivial, skip phases that don't apply
- Check for merge conflicts on all open PRs before finishing
- If you catch yourself writing "this is worth remembering for next session" — consider whether a repo doc is the better home. It usually is.
