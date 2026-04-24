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
 * Unit tests for the extracted server-config handler cores. H5 of the
 * handler testing plan.
 *
 * The handlers take `expectedKey` as a plain input string — the
 * wrapper extracts it from `functions.config()` before calling.
 * That keeps this test free of firebase-functions runtime stubs.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import {
  handleServerConfigRead,
  handleServerConfigUpdate,
  readServerConfigSecret,
} from '../../../src/functions/http/ServerConfig';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';

const SECRET = 'correct-horse-battery-staple';

describe('readServerConfigSecret', () => {
  it('returns null when functions.config() is missing the serverconfig section', () => {
    expect(readServerConfigSecret({}, 'key')).to.be.null;
    expect(readServerConfigSecret(null, 'key')).to.be.null;
  });

  it('returns null when the requested field is absent', () => {
    expect(readServerConfigSecret({ serverconfig: {} }, 'key')).to.be.null;
    expect(readServerConfigSecret({ serverconfig: { updatekey: 'x' } }, 'key')).to.be.null;
  });

  it('returns the secret string when present', () => {
    expect(readServerConfigSecret({ serverconfig: { key: 'abc' } }, 'key')).to.equal('abc');
    expect(readServerConfigSecret({ serverconfig: { updatekey: 'def' } }, 'updatekey')).to.equal('def');
  });
});

describe('handleServerConfigRead (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
    fakeConfig.seed({ body: { some: 'config' } });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('returns 500 when functions.config() has no serverconfig.key (expectedKey=null)', async () => {
    const result = await handleServerConfigRead({
      method: 'GET',
      requestKey: SECRET,
      expectedKey: null,
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 500,
      body: { error: 'Deploy Firebase Functions config with serverconfig.key' },
    });
  });

  it('returns 401 when X-ServerConfigKey header is missing', async () => {
    const result = await handleServerConfigRead({
      method: 'GET',
      requestKey: undefined,
      expectedKey: SECRET,
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 401,
      body: { error: 'Unauthorized.' },
    });
  });

  it('returns 403 when the request key does not match the configured key', async () => {
    const result = await handleServerConfigRead({
      method: 'GET',
      requestKey: 'wrong',
      expectedKey: SECRET,
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 403,
      body: { error: 'Forbidden.' },
    });
  });

  it('returns 405 for non-GET methods when fully authorized', async () => {
    const result = await handleServerConfigRead({
      method: 'POST',
      requestKey: SECRET,
      expectedKey: SECRET,
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 405,
      body: { error: 'Method Not Allowed.' },
    });
  });

  it('returns 200 ok with the current config when authorized and method is GET', async () => {
    const result = await handleServerConfigRead({
      method: 'GET',
      requestKey: SECRET,
      expectedKey: SECRET,
    });
    expect(result).to.deep.equal({
      kind: 'ok',
      data: { body: { some: 'config' } },
    });
  });
});

describe('handleServerConfigUpdate (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('returns 500 with the updatekey-specific error when expectedKey is null', async () => {
    const result = await handleServerConfigUpdate({
      method: 'POST',
      requestKey: SECRET,
      expectedKey: null,
      query: {},
      body: {},
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 500,
      body: { error: 'Deploy Firebase Functions config with serverconfig.updatekey' },
    });
  });

  it('returns 405 when the method is not POST', async () => {
    const result = await handleServerConfigUpdate({
      method: 'GET',
      requestKey: SECRET,
      expectedKey: SECRET,
      query: {},
      body: {},
    });
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.status).to.equal(405);
    }
  });

  it('saves { queryParams, body } to ServerConfigDatabase and returns the fresh config', async () => {
    const query = { foo: 'bar' };
    const body = { nested: { value: 42 } };

    const result = await handleServerConfigUpdate({
      method: 'POST',
      requestKey: SECRET,
      expectedKey: SECRET,
      query,
      body,
    });

    expect(fakeConfig.saved).to.have.lengthOf(1);
    expect(fakeConfig.saved[0]).to.deep.equal({
      queryParams: query,
      body: body,
    });
    expect(result).to.deep.equal({
      kind: 'ok',
      data: { queryParams: query, body: body },
    });
  });
});
