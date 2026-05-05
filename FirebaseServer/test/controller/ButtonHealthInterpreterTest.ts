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
  computeHealthFromLastPoll,
  detectTransition,
  ONLINE_THRESHOLD_SEC,
} from '../../src/controller/ButtonHealthInterpreter';
import { ButtonHealthRecord } from '../../src/database/ButtonHealthDatabase';

describe('computeHealthFromLastPoll', () => {
  const NOW = 1_700_000_000;

  it('returns OFFLINE when lastPollSec is null', () => {
    expect(computeHealthFromLastPoll(null, NOW)).to.equal('OFFLINE');
  });

  it('returns OFFLINE when lastPollSec is undefined', () => {
    expect(computeHealthFromLastPoll(undefined as any, NOW)).to.equal('OFFLINE');
  });

  it('returns ONLINE when lastPollSec is exactly 60 sec ago (boundary)', () => {
    expect(computeHealthFromLastPoll(NOW - ONLINE_THRESHOLD_SEC, NOW)).to.equal('ONLINE');
  });

  it('returns ONLINE when lastPollSec is fresh (<60 sec ago)', () => {
    expect(computeHealthFromLastPoll(NOW - 30, NOW)).to.equal('ONLINE');
  });

  it('returns OFFLINE when lastPollSec is stale (>60 sec ago)', () => {
    expect(computeHealthFromLastPoll(NOW - 61, NOW)).to.equal('OFFLINE');
  });

  it('returns ONLINE when lastPollSec is in the future (clock skew clamp)', () => {
    expect(computeHealthFromLastPoll(NOW + 100, NOW)).to.equal('ONLINE');
  });
});

describe('detectTransition', () => {
  const NOW = 1_700_000_000;

  it('treats null prior as a transition into the computed state', () => {
    const result = detectTransition(null, 'ONLINE', NOW);
    expect(result.didTransition).to.equal(true);
    expect(result.newRecord).to.deep.equal({
      state: 'ONLINE',
      stateChangedAtSeconds: NOW,
    });
  });

  it('treats null prior as a transition into OFFLINE', () => {
    const result = detectTransition(null, 'OFFLINE', NOW);
    expect(result.didTransition).to.equal(true);
    expect(result.newRecord).to.deep.equal({
      state: 'OFFLINE',
      stateChangedAtSeconds: NOW,
    });
  });

  it('reports no transition when prior state matches computed (ONLINE no-op)', () => {
    const prior: ButtonHealthRecord = { state: 'ONLINE', stateChangedAtSeconds: NOW - 600 };
    const result = detectTransition(prior, 'ONLINE', NOW);
    expect(result.didTransition).to.equal(false);
    // CRITICAL: stateChangedAtSeconds MUST NOT be bumped on no-op writes —
    // the UI's "Offline since X" label depends on this.
    expect(result.newRecord).to.deep.equal(prior);
    expect(result.newRecord.stateChangedAtSeconds).to.equal(NOW - 600);
  });

  it('reports no transition when prior state matches computed (OFFLINE no-op)', () => {
    const prior: ButtonHealthRecord = { state: 'OFFLINE', stateChangedAtSeconds: NOW - 1200 };
    const result = detectTransition(prior, 'OFFLINE', NOW);
    expect(result.didTransition).to.equal(false);
    expect(result.newRecord).to.deep.equal(prior);
    expect(result.newRecord.stateChangedAtSeconds).to.equal(NOW - 1200);
  });

  it('reports transition ONLINE -> OFFLINE with fresh stateChangedAtSeconds', () => {
    const prior: ButtonHealthRecord = { state: 'ONLINE', stateChangedAtSeconds: NOW - 7200 };
    const result = detectTransition(prior, 'OFFLINE', NOW);
    expect(result.didTransition).to.equal(true);
    expect(result.newRecord).to.deep.equal({
      state: 'OFFLINE',
      stateChangedAtSeconds: NOW,
    });
  });

  it('reports transition OFFLINE -> ONLINE with fresh stateChangedAtSeconds', () => {
    const prior: ButtonHealthRecord = { state: 'OFFLINE', stateChangedAtSeconds: NOW - 1200 };
    const result = detectTransition(prior, 'ONLINE', NOW);
    expect(result.didTransition).to.equal(true);
    expect(result.newRecord).to.deep.equal({
      state: 'ONLINE',
      stateChangedAtSeconds: NOW,
    });
  });
});
