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
  getDoorSensorBuildTimestamp,
  getRemoteButtonBuildTimestamp,
} from '../../../src/controller/config/ConfigAccessors';

describe('getDoorSensorBuildTimestamp', () => {
  it('returns the value when present in config body', () => {
    expect(getDoorSensorBuildTimestamp({ body: { doorSensorBuildTimestamp: 'Sat Mar 13 14:45:00 2021' } }))
      .to.equal('Sat Mar 13 14:45:00 2021');
  });

  it('returns null when the field is missing from body', () => {
    expect(getDoorSensorBuildTimestamp({ body: {} })).to.be.null;
  });

  it('returns null when body is missing', () => {
    expect(getDoorSensorBuildTimestamp({})).to.be.null;
  });

  it('returns null when config is null', () => {
    expect(getDoorSensorBuildTimestamp(null)).to.be.null;
  });

  it('returns null when config is undefined', () => {
    expect(getDoorSensorBuildTimestamp(undefined)).to.be.null;
  });
});

describe('getRemoteButtonBuildTimestamp', () => {
  it('returns the value when present in config body', () => {
    expect(getRemoteButtonBuildTimestamp({ body: { remoteButtonBuildTimestamp: 'Sat Apr 10 23:57:32 2021' } }))
      .to.equal('Sat Apr 10 23:57:32 2021');
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
});
