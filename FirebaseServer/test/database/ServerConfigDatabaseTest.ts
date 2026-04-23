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
  CURRENT_KEY,
} from '../../src/database/ServerConfigDatabase';

describe('ServerConfigDatabase: collection-name contract', () => {
  // These strings MUST match what the pre-refactor ServerConfigDatabase
  // wrote. `CURRENT_KEY` ('current') is the single document ID within
  // the `configCurrent` collection — changing it would orphan every
  // existing config read. A mismatch would cause the server to read a
  // blank config, falling back to every feature flag's default
  // (disabled), silently breaking the app.
  //
  // Intentional change requires a full data migration:
  //   1. Copy the single config document from old → new location.
  //   2. Update this test.
  //   3. Redeploy every caller atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('configCurrent');
  });

  it('all collection is pinned', () => {
    expect(COLLECTION_ALL).to.equal('configAll');
  });

  it('current document key is pinned', () => {
    expect(CURRENT_KEY).to.equal('current');
  });
});
