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
import { COLLECTION_CURRENT } from '../../src/database/ButtonHealthDatabase';

describe('ButtonHealthDatabase: collection-name contract', () => {
  // This string MUST match what firestoreCheckButtonHealth and
  // pubsubCheckButtonHealth read/write. A mismatch means new code
  // writes to a different Firestore collection than existing
  // production data — the indicator appears to work while historical
  // state becomes unreachable.
  //
  // Intentional change requires a full data migration:
  //   1. Copy documents from old collection to new in production.
  //   2. Update this test.
  //   3. Update any dashboards or alerts.
  //   4. Deploy atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('buttonHealthCurrent');
  });
});
