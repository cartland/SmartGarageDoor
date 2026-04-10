# Iteration Plan

Each iteration is executed by a sub-agent. The orchestrator reads the current state, dispatches the agent, writes results, commits, and increments.

## Orchestrator Steps (run by main Claude)

1. Read `ITERATION.txt` and `TARGET.txt`. If current >= target, stop.
2. Read `SPEC.md` and `HISTORY.md` to build context for the agent.
3. Dispatch a sub-agent with the full context + instructions below.
4. Write the agent's output to `SPEC.md` and `HISTORY.md`.
5. Increment `ITERATION.txt`.
6. Commit: `docs: Design iteration N — [one-line summary]`
7. Repeat from step 1.

## Sub-Agent Prompt Template

The sub-agent receives:
- The current SPEC.md content
- The current HISTORY.md content  
- The iteration number

The sub-agent must return:
- **area**: which area to improve (1 line)
- **rationale**: why this area (2-3 sentences)
- **options**: 3 concrete improvement options considered
- **decision**: which option chosen and why (2-3 sentences)
- **spec_addition**: the new or updated section for SPEC.md (full markdown)
- **summary**: one-line summary for the commit message

## Sub-Agent Instructions

You are a UX designer iterating on a garage door controller app's design specification.

Review the current spec and history. Identify one area that is:
- Missing or underspecified
- Could be meaningfully improved with concrete detail
- Not already well-covered by recent iterations

Early iterations (1-10) should establish foundations. Middle iterations (11-30) should detail specific components and screens. Late iterations (31-50) should refine edge cases, micro-interactions, and polish.

Be specific: include dimensions (dp), colors (hex), timing (ms), and exact text strings where relevant. Reference existing spec sections. Do not repeat what's already documented — extend or refine it.

Each iteration should make the spec meaningfully better in one focused area. Small, concrete improvements beat broad vague ones.
