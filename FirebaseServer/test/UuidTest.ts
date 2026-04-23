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
 * Also doubles as the CVE-guard: the "is ≥ 14.0.0" test fails if
 * `uuid` is ever downgraded below the patched version for Dependabot
 * alert #67 (GHSA — missing buffer bounds check in v3/v5/v6 when buf
 * is provided). See docs/FIREBASE_HARDENING_PLAN.md → Part B.
 */

import { expect } from 'chai';
import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @typescript-eslint/no-require-imports
const uuidPkg = require('uuid/package.json');

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

describe('CVE guards — fail if a flagged version is re-introduced', () => {
  // Dependabot alert #67: uuid < 14.0.0 (GHSA: v3/v5/v6 buffer bounds).
  // Our usage (v4() with no buf) is not in the vector, but keeping
  // uuid ≥ 14.0.0 clears the alert and future-proofs the test anchor.
  it('uuid resolved version is ≥ 14.0.0', () => {
    const [major] = String(uuidPkg.version).split('.').map(Number);
    expect(major, `uuid ${uuidPkg.version} < 14.0.0`).to.be.gte(14);
  });
});
