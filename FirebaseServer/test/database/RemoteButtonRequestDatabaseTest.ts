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
} from '../../src/database/RemoteButtonRequestDatabase';

describe('RemoteButtonRequestDatabase: collection-name contract', () => {
  // These strings MUST match what pubsub/RemoteButton.ts and
  // http/RemoteButton.ts wrote before centralization. A mismatch means
  // new code writes to a different Firestore collection than existing
  // production data — the app appears to work while historical data
  // becomes unreachable.
  //
  // Intentional change requires a full data migration:
  //   1. Copy documents from old collection to new in production.
  //   2. Update this test.
  //   3. Update the data-retention cron.
  //   4. Update any dashboards or alerts.
  //   5. Deploy atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('remoteButtonRequestCurrent');
  });

  it('all collection is pinned', () => {
    expect(COLLECTION_ALL).to.equal('remoteButtonRequestAll');
  });
});
