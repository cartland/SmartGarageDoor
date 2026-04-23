/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Contract tests for our `uuid` dependency. Pins the shape of `v4()`
 * output so that a future version bump (or a silent regression) can't
 * change what we store in Firestore as session IDs without a test
 * failure.
 *
 * The CVE-guard (uuid ≥ 14.0.0) lives in CveGuardTest.ts alongside
 * other dependency guards.
 */

import { expect } from 'chai';
import { v4 as uuidv4 } from 'uuid';

describe('uuid v4 contract', () => {
  it('returns 36-character canonical-form v4 strings', () => {
    const id = uuidv4();
    expect(id).to.match(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it('returns distinct values on each call', () => {
    const a = uuidv4();
    const b = uuidv4();
    expect(a).to.not.equal(b);
  });
});
