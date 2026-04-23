/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { expect } from 'chai';
import {
  COLLECTION_CURRENT,
  COLLECTION_ALL,
} from '../../src/database/UpdateDatabase';

describe('UpdateDatabase: collection-name contract', () => {
  // These strings MUST match what controller/DatabaseCleaner.ts and
  // functions/http/Echo.ts wrote before centralization. A mismatch means
  // new code writes to a different Firestore collection than existing
  // production data — the Echo endpoint would silently skip writes to
  // the historical collection, and DatabaseCleaner would skip cleanup of
  // the old data.
  //
  // Intentional change requires a full data migration:
  //   1. Copy documents from old collection to new in production.
  //   2. Update this test.
  //   3. Update the data-retention cron.
  //   4. Deploy atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('updateCurrent');
  });

  it('all collection is pinned', () => {
    expect(COLLECTION_ALL).to.equal('updateAll');
  });
});
