# test/fakes/

In-memory fake implementations of database interfaces in `src/database/`,
for use in unit tests.

## Purpose

Each database module in `src/database/` exports a typed interface and a
module-level singleton. Tests swap the singleton's implementation via
`setImpl(fake)` in `beforeEach`, reset in `afterEach`. Fakes in this
directory provide that swappable in-memory backing.

This pattern is preferred over `sinon.stub(TimeSeriesDatabase.prototype,
...)` because:

- **Typed per-collection.** The fake implements the same TypeScript
  interface as the production class; method signatures are checked by
  the compiler.
- **No prototype pollution.** Instantiating a fresh fake per test
  eliminates cross-test leakage.
- **Self-documenting tests.** `fake.seed(...); call(); expect(fake.saved)`
  reads like a spec.
- **Precise.** Stubbing the singleton of one collection leaves every
  other collection untouched. Prototype stubs intercept every instance
  of `TimeSeriesDatabase` in the process.

## File naming

One fake per DB module:

```
src/database/EventDatabase.ts           ← interface + FirestoreImpl + singleton
test/fakes/FakeEventDatabase.ts         ← in-memory impl of EventDatabase
```

The class name is `Fake<Name>Database` to match.

## Fake shape

Minimal template for a new fake:

```typescript
// test/fakes/FakeEventDatabase.ts

import { EventDatabase } from '../../src/database/EventDatabase';

export class FakeEventDatabase implements EventDatabase {
  // Storage
  private readonly store = new Map<string, Record<string, unknown>>();

  // Audit log for test assertions
  readonly saved: Array<[string, Record<string, unknown>]> = [];

  // Interface methods
  async getCurrent(key: string): Promise<Record<string, unknown> | null> {
    return this.store.get(key) ?? null;
  }

  async save(key: string, data: Record<string, unknown>): Promise<void> {
    this.store.set(key, data);
    this.saved.push([key, data]);
  }

  // Test-only helpers
  seed(key: string, data: Record<string, unknown>): void {
    this.store.set(key, data);
  }

  clear(): void {
    this.store.clear();
    this.saved.length = 0;
  }
}
```

## Usage from a test

```typescript
import { setImpl, DATABASE } from '../src/database/EventDatabase';
import { FakeEventDatabase } from './fakes/FakeEventDatabase';

describe('someHandler', () => {
  let fake: FakeEventDatabase;

  beforeEach(() => {
    fake = new FakeEventDatabase();
    setImpl(fake);
  });

  afterEach(() => {
    // Each DB module exports a `resetImpl()` that swaps back to the
    // Firestore impl. Always call it; it costs nothing and prevents
    // cross-test leakage.
    // (See the DB module for the exact function name if it differs.)
  });

  it('writes the event', async () => {
    await handler(/* ... */);
    expect(fake.saved).to.deep.equal([['build-123', { /* ... */ }]]);
  });
});
```

## Rules of thumb

- **Fakes mirror the interface — that's it.** Don't simulate Firestore
  error conditions, latency, or ordering unless a specific test requires
  it. A fake is a `Map` and a `[]`, not a mini-database.
- **Don't bundle fakes with production code.** They live here, not in
  `src/`. This keeps them out of the deployed Cloud Functions bundle
  and prevents accidental use at runtime.
- **Keep fakes tiny.** If a fake starts needing branching logic, that's
  a signal to split into multiple fakes (one for each behavior) or to
  assert behavior at the caller level instead.
- **Export test-only helpers on the fake**, not on the interface. The
  interface is the production contract. The fake adds things like
  `seed()` and `clear()` for test convenience.

## Related

- [`docs/FIREBASE_DATABASE_REFACTOR.md`](../../../docs/FIREBASE_DATABASE_REFACTOR.md)
  — full refactor plan, goals, and long-term maintenance principles.
