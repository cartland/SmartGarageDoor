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
 * Contract tests for buildTimestamp accessors. These pin the exact
 * shape of production config — the keys and encodings that existed
 * since April 2021. Changing the production config shape requires
 * updating these tests in lockstep with the config update.
 *
 * See docs/FIREBASE_HARDENING_PLAN.md → Part A, and the revert
 * rationale in PR #494 commit message for the history.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import {
  getBuildTimestamp,
  getRemoteButtonBuildTimestamp,
  resolveBuildTimestamp,
} from '../../../src/controller/config/ConfigAccessors';

describe('getBuildTimestamp (door sensor, production key: "buildTimestamp")', () => {
  // The door-sensor value is stored plain/decoded in production
  // config under the key `buildTimestamp` (no prefix). Pinning the
  // exact shape here guards against accidentally looking at a
  // different key.
  it('returns the value for the production key', () => {
    const config = { body: { buildTimestamp: 'Sat Mar 13 14:45:00 2021' } };
    expect(getBuildTimestamp(config)).to.equal('Sat Mar 13 14:45:00 2021');
  });

  it('returns null when the field is missing from body', () => {
    expect(getBuildTimestamp({ body: {} })).to.be.null;
  });

  it('returns null when body is missing', () => {
    expect(getBuildTimestamp({})).to.be.null;
  });

  it('returns null when config is null', () => {
    expect(getBuildTimestamp(null)).to.be.null;
  });

  it('returns null when config is undefined', () => {
    expect(getBuildTimestamp(undefined)).to.be.null;
  });

  it('returns null for an empty string value (treat as missing)', () => {
    // Why null and not the empty string: callers use `?? fallback`,
    // which only triggers on null/undefined. An empty string here
    // would bypass the fallback and pass "" into Firestore queries.
    expect(getBuildTimestamp({ body: { buildTimestamp: '' } })).to.be.null;
  });
});

describe('getRemoteButtonBuildTimestamp (remote button, production URL-encoded)', () => {
  // Production config stores this value URL-encoded — has been that
  // way since the first serverConfig.json in April 2021. The accessor
  // normalizes on read so callers always see the decoded form,
  // matching the pre-refactor hardcoded value in pubsub/RemoteButton.
  it('decodes the production URL-encoded value', () => {
    const config = {
      body: {
        remoteButtonBuildTimestamp: 'Sat%20Apr%2010%2023:57:32%202021',
      },
    };
    expect(getRemoteButtonBuildTimestamp(config)).to.equal('Sat Apr 10 23:57:32 2021');
  });

  it('returns an already-decoded value unchanged (decode is idempotent on plain ASCII)', () => {
    const config = {
      body: { remoteButtonBuildTimestamp: 'Sat Apr 10 23:57:32 2021' },
    };
    expect(getRemoteButtonBuildTimestamp(config)).to.equal('Sat Apr 10 23:57:32 2021');
  });

  it('returns the raw value if URL-decoding throws (malformed percent-encoding)', () => {
    // Defensive: never crash the pubsub job because the config was
    // authored wrong. The caller's fallback will kick in only if the
    // raw value is also useless (empty, null). This returns
    // something, leaving the fallback path to a separate null-check.
    const config = { body: { remoteButtonBuildTimestamp: 'Sat%ZZInvalid' } };
    expect(getRemoteButtonBuildTimestamp(config)).to.equal('Sat%ZZInvalid');
  });

  it('returns null when the field is missing from body', () => {
    expect(getRemoteButtonBuildTimestamp({ body: {} })).to.be.null;
  });

  it('returns null when body is missing', () => {
    expect(getRemoteButtonBuildTimestamp({})).to.be.null;
  });

  it('returns null when config is null', () => {
    expect(getRemoteButtonBuildTimestamp(null)).to.be.null;
  });

  it('returns null for an empty string value (treat as missing)', () => {
    expect(getRemoteButtonBuildTimestamp({ body: { remoteButtonBuildTimestamp: '' } })).to.be.null;
  });

  it('logs a warning when URL-decoding fails', () => {
    const warnSpy = sinon.spy(console, 'warn');
    try {
      getRemoteButtonBuildTimestamp({ body: { remoteButtonBuildTimestamp: 'Sat%ZZ' } });
      expect(warnSpy.calledOnce, 'expected console.warn to be called once').to.be.true;
      expect(warnSpy.firstCall.args[0]).to.match(/failed to URL-decode/i);
    } finally {
      warnSpy.restore();
    }
  });
});

describe('resolveBuildTimestamp (fallback-with-log helper)', () => {
  afterEach(() => {
    sinon.restore();
  });

  it('returns the config value unchanged when present', () => {
    const warnSpy = sinon.spy(console, 'warn');
    const result = resolveBuildTimestamp('from-config', 'fallback-literal', 'test-context');
    expect(result).to.equal('from-config');
    expect(warnSpy.called, 'expected no warning on normal path').to.be.false;
  });

  it('returns the fallback and emits a warning when config value is null', () => {
    const warnSpy = sinon.spy(console, 'warn');
    const result = resolveBuildTimestamp(null, 'fallback-literal', 'test-context');
    expect(result).to.equal('fallback-literal');
    expect(warnSpy.calledOnce, 'expected exactly one warning').to.be.true;
    expect(warnSpy.firstCall.args[0]).to.include('test-context');
    expect(warnSpy.firstCall.args[0]).to.match(/fallback/i);
  });
});
