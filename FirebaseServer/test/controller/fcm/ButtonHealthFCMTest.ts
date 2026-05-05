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
import { buildTransitionPayload } from '../../../src/controller/fcm/ButtonHealthFCM';
import { ButtonHealthRecord } from '../../../src/database/ButtonHealthDatabase';
import { AndroidMessagePriority } from '../../../src/model/FCM';

describe('buildTransitionPayload', () => {
  const buildTimestamp = 'Sat Apr 10 23:57:32 2021';

  it('builds a data-only payload for ONLINE transitions', () => {
    const record: ButtonHealthRecord = {
      state: 'ONLINE',
      stateChangedAtSeconds: 1_730_000_000,
    };
    const message = buildTransitionPayload(buildTimestamp, record);
    expect(message.topic).to.equal('buttonHealth-Sat.Apr.10.23.57.32.2021');
    expect(message.data).to.deep.equal({
      buttonState: 'ONLINE',
      stateChangedAtSeconds: '1730000000',
      buildTimestamp: 'Sat Apr 10 23:57:32 2021',
    });
    expect(message.android.collapse_key).to.equal('button_health_update');
    expect(message.android.priority).to.equal(AndroidMessagePriority.HIGH);
  });

  it('builds a data-only payload for OFFLINE transitions', () => {
    const record: ButtonHealthRecord = {
      state: 'OFFLINE',
      stateChangedAtSeconds: 1_730_000_500,
    };
    const message = buildTransitionPayload(buildTimestamp, record);
    expect(message.data.buttonState).to.equal('OFFLINE');
    expect(message.data.stateChangedAtSeconds).to.equal('1730000500');
  });

  it('omits the notification block (data-only by design)', () => {
    const record: ButtonHealthRecord = {
      state: 'OFFLINE',
      stateChangedAtSeconds: 1_730_000_000,
    };
    const message = buildTransitionPayload(buildTimestamp, record);
    // data-only — must NEVER carry a `notification` block.
    expect(message.notification).to.equal(undefined);
  });

  it('preserves the ORIGINAL buildTimestamp in the data payload', () => {
    // Topic gets sanitized; data field must carry the original (un-sanitized)
    // string so Android can use it as an identity key.
    const record: ButtonHealthRecord = {
      state: 'ONLINE',
      stateChangedAtSeconds: 1_730_000_000,
    };
    const message = buildTransitionPayload(buildTimestamp, record);
    expect(message.data.buildTimestamp).to.equal(buildTimestamp);
    expect(message.data.buildTimestamp).to.not.equal(message.topic);
  });

  it('serializes stateChangedAtSeconds as a string (FCM data values must be strings)', () => {
    const record: ButtonHealthRecord = {
      state: 'ONLINE',
      stateChangedAtSeconds: 42,
    };
    const message = buildTransitionPayload(buildTimestamp, record);
    expect(message.data.stateChangedAtSeconds).to.be.a('string');
    expect(message.data.stateChangedAtSeconds).to.equal('42');
  });
});
