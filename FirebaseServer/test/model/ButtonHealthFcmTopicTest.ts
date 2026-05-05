/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

// This test pins the FCM topic format on the SERVER side.
// AndroidGarage/.../buttonhealth/ButtonHealthFcmTopicTest.kt MUST
// share the exact same input/output pairs to catch drift between the
// server topic builder and Android topic-string assumption.

import { expect } from 'chai';
import { buildTimestampToButtonHealthFcmTopic } from '../../src/model/ButtonHealthFcmTopic';

describe('buildTimestampToButtonHealthFcmTopic', () => {
  it('converts a plain ASCII buildTimestamp to a sanitized topic', () => {
    const input = 'Sat Mar 13 14:45:00 2021';
    const expected = 'buttonHealth-Sat.Mar.13.14.45.00.2021';
    expect(buildTimestampToButtonHealthFcmTopic(input)).to.equal(expected);
  });

  it('decodes a URL-encoded buildTimestamp before sanitizing', () => {
    // URL-encoded form of 'Sat Apr 10 23:57:32 2021' (the production button buildTimestamp shape).
    const input = 'Sat%20Apr%2010%2023%3A57%3A32%202021';
    const expected = 'buttonHealth-Sat.Apr.10.23.57.32.2021';
    expect(buildTimestampToButtonHealthFcmTopic(input)).to.equal(expected);
  });

  it('falls back to raw input when decodeURIComponent throws', () => {
    // '%ZZ' is invalid URL encoding; decodeURIComponent throws URIError.
    // The builder must catch and proceed with the raw string + sanitization.
    const input = '%ZZ';
    const expected = 'buttonHealth-%ZZ';   // % is allowed in FCM topic chars; Z stays.
    expect(buildTimestampToButtonHealthFcmTopic(input)).to.equal(expected);
  });

  it('throws on empty buildTimestamp', () => {
    expect(() => buildTimestampToButtonHealthFcmTopic('')).to.throw(Error);
  });

  it('preserves valid FCM topic chars without sanitization', () => {
    // a-zA-Z0-9-_.~% are all valid FCM topic chars per Firebase spec.
    const input = 'abc-123_xyz.~%';
    const expected = 'buttonHealth-abc-123_xyz.~%';
    expect(buildTimestampToButtonHealthFcmTopic(input)).to.equal(expected);
  });
});
