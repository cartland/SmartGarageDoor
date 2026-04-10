# Design Iteration Instructions

This directory contains an iterative UX and design specification for the SmartGarageDoor Android app. Each iteration reviews what exists, brainstorms improvements, picks one area, and improves it.

## How to Run an Iteration

1. Read `ITERATION.txt` (current count) and `TARGET.txt` (goal)
2. If current >= target, stop — all iterations complete
3. Read `PLAN.md` for the step-by-step process
4. Read `HISTORY.md` for prior decisions and context
5. Read `SPEC.md` for the current design specification
6. Execute the plan steps, updating `SPEC.md` and `HISTORY.md`
7. Increment the number in `ITERATION.txt`
8. If interrupted, all state is in files — resume by re-reading them

## Files

| File | Purpose |
|------|---------|
| `INSTRUCTIONS.md` | This file — how the process works |
| `PLAN.md` | The step-by-step plan for each iteration |
| `ITERATION.txt` | Current iteration count (single integer) |
| `TARGET.txt` | Target number of iterations (single integer) |
| `SPEC.md` | The design specification (grows each iteration) |
| `HISTORY.md` | Decisions, context, and rationale log |

## Resuming After Interruption

All state is persisted in files. To resume:
1. Read `ITERATION.txt` to know where you are
2. Read `HISTORY.md` to know what was decided
3. Read `SPEC.md` to see the current state
4. Continue from the current iteration number
