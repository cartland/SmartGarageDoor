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
 * Contract tests for the AuthService bridge.
 *
 * The bridge is introduced ahead of the H3/H4 HTTP handler extractions
 * (see docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md). There are no callers yet;
 * these tests pin the swap-in pattern so the follow-up handler tests
 * can rely on it.
 */

import { expect } from 'chai';

import {
  SERVICE as AuthService,
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../src/controller/AuthService';
import { FakeAuthService } from '../fakes/FakeAuthService';

describe('AuthService (swappable bridge)', () => {
  let fake: FakeAuthService;

  beforeEach(() => {
    fake = new FakeAuthService();
    setAuthServiceImpl(fake);
  });

  afterEach(() => {
    resetAuthServiceImpl();
  });

  it('routes verifyIdToken() calls through the injected fake', async () => {
    fake.seedDecoded({ email: 'user@example.com' });

    const decoded = await AuthService.verifyIdToken('abc.def.ghi');

    expect(decoded.email).to.equal('user@example.com');
    expect(fake.verifyCalls).to.deep.equal(['abc.def.ghi']);
  });

  it('propagates the thrown error armed by failNextVerify', async () => {
    const boom = new Error('auth/invalid-id-token');
    fake.failNextVerify(boom);

    let caught: unknown;
    try {
      await AuthService.verifyIdToken('bad-token');
    } catch (e) {
      caught = e;
    }
    expect(caught).to.equal(boom);
  });

  it('clears the armed failure after a single rejection (single-shot semantics)', async () => {
    fake.failNextVerify(new Error('once'));
    fake.seedDecoded({ email: 'next@example.com' });

    let firstThrew = false;
    try {
      await AuthService.verifyIdToken('first');
    } catch {
      firstThrew = true;
    }
    expect(firstThrew).to.be.true;

    // Second call succeeds with the seeded decoded token.
    const decoded = await AuthService.verifyIdToken('second');
    expect(decoded.email).to.equal('next@example.com');
    expect(fake.verifyCalls).to.deep.equal(['first', 'second']);
  });
});
