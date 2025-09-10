/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
import * as firebase from 'firebase-admin';

import { TimeSeriesDatabase } from '../../src/database/TimeSeriesDatabase';

describe('TimeSeriesDatabase', () => {
  let firestoreStub;

  beforeEach(() => {
    // Stub the entire firestore functionality
    firestoreStub = {
      collection: sinon.stub().returnsThis(),
      doc: sinon.stub().returnsThis(),
      set: sinon.stub().resolves(),
      add: sinon.stub().resolves({ id: 'test-id' }),
      get: sinon.stub().resolves({ data: () => ({ key: 'value' }) }),
      where: sinon.stub().returnsThis(),
      update: sinon.stub().resolves(),
    };
    sinon.stub(firebase, 'app').get(() => () => ({ firestore: () => firestoreStub }));
  });

  afterEach(() => {
    sinon.restore();
  });

  const db = new TimeSeriesDatabase('current', 'all');

  describe('save', () => {
    it('should call set and add on the correct collections', async () => {
      const session = 'test-session';
      const data = { key: 'value' };
      await db.save(session, data);

      expect(firestoreStub.collection.calledWith('current')).to.be.true;
      expect(firestoreStub.doc.calledWith(session)).to.be.true;
      expect(firestoreStub.set.calledOnce).to.be.true;
      expect(firestoreStub.collection.calledWith('all')).to.be.true;
      expect(firestoreStub.add.calledOnce).to.be.true;
    });
  });

  describe('getCurrent', () => {
    it('should call get on the correct collection and return the data', async () => {
      const session = 'test-session';
      const result = await db.getCurrent(session);

      expect(firestoreStub.collection.calledWith('current')).to.be.true;
      expect(firestoreStub.doc.calledWith(session)).to.be.true;
      expect(firestoreStub.get.calledOnce).to.be.true;
      expect(result).to.deep.equal({ key: 'value' });
    });
  });

  describe('updateCurrentWithMatchingCurrentEventTimestamp', () => {
    it('should call save when no matching event is found', async () => {
      firestoreStub.get.resolves({ size: 0, docs: [] });
      const session = 'test-session';
      const data = { currentEvent: { timestampSeconds: 12345 } };
      await db.updateCurrentWithMatchingCurrentEventTimestamp(session, data);

      expect(firestoreStub.set.calledOnce).to.be.true;
      expect(firestoreStub.add.calledOnce).to.be.true;
    });

    it('should call save when multiple matching events are found', async () => {
      firestoreStub.get.resolves({ size: 2, docs: [{}, {}] });
      const session = 'test-session';
      const data = { currentEvent: { timestampSeconds: 12345 } };
      await db.updateCurrentWithMatchingCurrentEventTimestamp(session, data);

      expect(firestoreStub.set.calledOnce).to.be.true;
      expect(firestoreStub.add.calledOnce).to.be.true;
    });

    it('should call set and update when a single matching event is found', async () => {
      const doc = { ref: { update: sinon.stub().resolves() } };
      const snapshot = {
        size: 1,
        docs: [doc],
        forEach: (callback) => {
          callback(doc);
        },
      };
      firestoreStub.get.resolves(snapshot);
      const session = 'test-session';
      const data = { currentEvent: { timestampSeconds: 12345 } };
      await db.updateCurrentWithMatchingCurrentEventTimestamp(session, data);

      expect(firestoreStub.set.calledOnce).to.be.true;
      expect(doc.ref.update.calledOnce).to.be.true;
    });
  });
});