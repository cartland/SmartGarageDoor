/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { DATABASE } from '../../src/database/Database';
import { TimeSeriesDatabase } from '../../src/database/TimeSeriesDatabase';

describe('Database', () => {
  afterEach(() => {
    sinon.restore();
  });

  describe('set', () => {
    it('should call TimeSeriesDatabase.save with the correct arguments', async () => {
      const saveStub = sinon.stub(TimeSeriesDatabase.prototype, 'save').resolves();
      const session = 'test-session';
      const data = { key: 'value' };
      await DATABASE.set(session, data);
      expect(saveStub.calledOnceWith(session, data)).to.be.true;
    });
  });

  describe('get', () => {
    it('should call TimeSeriesDatabase.getCurrent with the correct arguments and return the expected value', async () => {
      const expectedValue = { key: 'value' };
      const getCurrentStub = sinon.stub(TimeSeriesDatabase.prototype, 'getCurrent').resolves(expectedValue);
      const session = 'test-session';
      const result = await DATABASE.get(session);
      expect(getCurrentStub.calledOnceWith(session)).to.be.true;
      expect(result).to.deep.equal(expectedValue);
    });
  });
});
