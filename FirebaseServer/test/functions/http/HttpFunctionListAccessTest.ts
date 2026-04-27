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
import * as fs from 'fs';
import * as path from 'path';

import { handleFunctionListAccess } from '../../../src/functions/http/FunctionListAccess';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';

const ALLOWED_EMAIL = 'allowed@example.com';
const DENIED_EMAIL = 'denied@example.com';
const OK_TOKEN = 'google-id-token-xyz';

// Shared wire-contract fixtures — the bytes both server and Android decode.
// See `wire-contracts/README.md`.
const FIXTURE_DIR = path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'functionListAccess');
const ENABLED_TRUE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_enabled_true.json'), 'utf8'),
);
const ENABLED_FALSE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_enabled_false.json'), 'utf8'),
);

describe('handleFunctionListAccess (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeAuth: FakeAuthService;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeAuth = new FakeAuthService();
    setServerConfigDBImpl(fakeConfig);
    setAuthServiceImpl(fakeAuth);
    fakeConfig.seed({
      body: {
        featureFunctionListAllowedEmails: [ALLOWED_EMAIL],
      },
    });
    fakeAuth.seedDecoded({ email: ALLOWED_EMAIL });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetAuthServiceImpl();
  });

  const happyInput = (overrides: any = {}) => ({
    method: 'GET',
    googleIdTokenHeader: OK_TOKEN,
    ...overrides,
  });

  it('returns 405 Method Not Allowed for non-GET methods', async () => {
    const result = await handleFunctionListAccess(happyInput({ method: 'POST' }));
    expect(result).to.deep.equal({ kind: 'error', status: 405, body: { error: 'Method Not Allowed.' } });
  });

  it('returns 401 Unauthorized (token) when googleIdToken header is missing', async () => {
    const result = await handleFunctionListAccess(happyInput({ googleIdTokenHeader: undefined }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('returns 401 Unauthorized (token) when googleIdToken header is empty string', async () => {
    const result = await handleFunctionListAccess(happyInput({ googleIdTokenHeader: '' }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('returns 401 Unauthorized (token) when verifyIdToken throws', async () => {
    fakeAuth.failNextVerify(new Error('auth/invalid-id-token'));
    const result = await handleFunctionListAccess(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('returns 200 enabled=true when email is in the allowlist (matches wire fixture)', async () => {
    const result = await handleFunctionListAccess(happyInput());
    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      // Pin the actual response bytes against the shared wire fixture. A
      // unilateral rename of `enabled` on the server breaks here.
      expect(result.data).to.deep.equal(ENABLED_TRUE_FIXTURE);
    }
  });

  it('returns 200 enabled=false when email is NOT in the allowlist (matches wire fixture)', async () => {
    fakeAuth.seedDecoded({ email: DENIED_EMAIL });
    const result = await handleFunctionListAccess(happyInput());
    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data).to.deep.equal(ENABLED_FALSE_FIXTURE);
    }
  });

  it('returns 200 enabled=false when allowlist field is missing from config (deny-all default)', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: {} });
    const result = await handleFunctionListAccess(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: ENABLED_FALSE_FIXTURE });
  });

  it('returns 200 enabled=false when allowlist is empty array', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { featureFunctionListAllowedEmails: [] } });
    const result = await handleFunctionListAccess(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: ENABLED_FALSE_FIXTURE });
  });

  it('returns 200 enabled=false when verified token has no email claim', async () => {
    fakeAuth.seedDecoded({}); // no email
    const result = await handleFunctionListAccess(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: ENABLED_FALSE_FIXTURE });
  });

});
