/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
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

/**
 * Unit tests for the extracted pubsubCheckForDoorErrors handler core.
 * H2 of docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * updateEvent is sinon-stubbed — its branching (old-event comparison,
 * FCM sending, Firestore persistence) is exercised by
 * EventUpdatesFakeTest.ts.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleCheckForDoorErrors } from '../../../src/functions/pubsub/DoorErrors';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import * as EventUpdates from '../../../src/controller/EventUpdates';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';

describe('handleCheckForDoorErrors (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let updateEventStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
    updateEventStub = sinon.stub(EventUpdates, 'updateEvent').resolves();
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('throws when production config has no buildTimestamp (context label: pubsubCheckForDoorErrors)', async () => {
    fakeConfig.seed({ body: {} });

    let caught: unknown;
    try {
      await handleCheckForDoorErrors();
    } catch (e) {
      caught = e;
    }
    expect(caught).to.be.instanceOf(Error);
    expect((caught as Error).message).to.match(/pubsubCheckForDoorErrors.*buildTimestamp missing/);
    expect(updateEventStub.called).to.be.false;
  });

  it('calls updateEvent with { buildTimestamp } and scheduledJob=true', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });

    await handleCheckForDoorErrors();

    expect(updateEventStub.calledOnce).to.be.true;
    const [data, scheduledJob] = updateEventStub.firstCall.args;
    expect(data).to.deep.equal({ buildTimestamp: BUILD_TIMESTAMP });
    expect(scheduledJob).to.equal(true);
  });

  it('resolves with no return value on the happy path', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });

    const result = await handleCheckForDoorErrors();

    expect(result).to.be.undefined;
  });
});
