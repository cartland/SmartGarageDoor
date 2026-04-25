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
 * Unit tests for the extracted Echo handler core. Pilot for the
 * handler-body extraction plan — see docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md
 * (Phase H1). The pure function is tested via FakeUpdateDatabase.
 *
 * The HTTP wrapper (`httpEcho`) is trivial map-over-try/catch and is
 * not directly tested — firebase-functions tests that plumbing on its
 * side. See the plan for the rationale.
 */

import { expect } from 'chai';

import { handleEchoRequest } from '../../../src/functions/http/Echo';
import {
  setImpl as setUpdateDBImpl,
  resetImpl as resetUpdateDBImpl,
} from '../../../src/database/UpdateDatabase';
import { FakeUpdateDatabase } from '../../fakes/FakeUpdateDatabase';

// Pattern for matching a UUID v4 session identifier.
const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe('handleEchoRequest (pure handler core)', () => {
  let fakeDB: FakeUpdateDatabase;

  beforeEach(() => {
    fakeDB = new FakeUpdateDatabase();
    setUpdateDBImpl(fakeDB);
  });

  afterEach(() => {
    resetUpdateDBImpl();
  });

  it('saves to UpdateDatabase using the session id from the query', async () => {
    const query = { session: 'client-session-123' };
    const body = { payload: 'hello' };

    await handleEchoRequest({ query, body });

    expect(fakeDB.saved).to.have.lengthOf(1);
    const [session, data] = fakeDB.saved[0];
    expect(session).to.equal('client-session-123');
    expect(data.session).to.equal('client-session-123');
    expect(data.queryParams).to.deep.equal(query);
    expect(data.body).to.deep.equal(body);
  });

  it('generates a v4 UUID session id when the query omits one', async () => {
    const query = {};
    const body = { payload: 'no-session' };

    await handleEchoRequest({ query, body });

    expect(fakeDB.saved).to.have.lengthOf(1);
    const [session, data] = fakeDB.saved[0];
    expect(session).to.match(UUID_V4_RE, 'expected a v4 UUID session');
    expect(data.session).to.equal(session);
    expect(data.queryParams).to.deep.equal(query);
    expect(data.body).to.deep.equal(body);
  });

  it('returns the saved data retrieved via getCurrent(session)', async () => {
    const query = { session: 'roundtrip', buildTimestamp: 'Sat Mar 13 14:45:00 2021' };
    const body = { a: 1 };

    const result = await handleEchoRequest({ query, body });

    // The returned value is what getCurrent(session) holds after save —
    // byte-identical to the stored document in the fake.
    expect(result.session).to.equal('roundtrip');
    expect(result.queryParams).to.deep.equal(query);
    expect(result.body).to.deep.equal(body);
    expect(result.buildTimestamp).to.equal('Sat Mar 13 14:45:00 2021');
  });

  it('passes buildTimestamp through unchanged when the query provides it', async () => {
    const query = { session: 's', buildTimestamp: 'Sat%20Apr%2010%2023:57:32%202021' };
    const body = {};

    await handleEchoRequest({ query, body });

    // Echo is a pass-through — the value is preserved byte-for-byte,
    // whatever encoding the caller sent. No decoding, no validation.
    expect(fakeDB.saved[0][1].buildTimestamp).to.equal(
      'Sat%20Apr%2010%2023:57:32%202021',
    );
  });

  it('omits buildTimestamp from the stored data when the query has none', async () => {
    const query = { session: 's' };
    const body = {};

    await handleEchoRequest({ query, body });

    const stored = fakeDB.saved[0][1];
    expect(stored).to.not.have.property('buildTimestamp');
  });
});
