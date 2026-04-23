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
} from '../../src/database/SensorEventDatabase';

describe('SensorEventDatabase: collection-name contract', () => {
  // These strings MUST match what pubsub/OpenDoor.ts, http/OpenDoor.ts,
  // controller/EventUpdates.ts, and controller/DatabaseCleaner.ts wrote
  // before centralization. A mismatch means new code writes to a different
  // Firestore collection than existing production data — the app appears
  // to work while historical door-event data becomes unreachable.
  //
  // firestore.rules and firestore.indexes.json also reference these strings;
  // both must stay in sync with this contract test.
  //
  // Intentional change requires a full data migration:
  //   1. Copy documents from old collection to new in production.
  //   2. Update this test.
  //   3. Update the data-retention cron.
  //   4. Update firestore.rules and firestore.indexes.json.
  //   5. Deploy atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('eventsCurrent');
  });

  it('all collection is pinned', () => {
    expect(COLLECTION_ALL).to.equal('eventsAll');
  });
});
