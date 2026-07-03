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
 * Tests the safe config-flag endpoint cores (handleListConfigFlags /
 * handleSetConfigFlag). The load-bearing test is
 * `set preserves every other field` — it proves a flip can NEVER clobber
 * buildTimestamp, secrets, allowlists, or sibling flags.
 */
import { expect } from 'chai';

import {
  handleListConfigFlags,
  handleSetConfigFlag,
  EDITABLE_CONFIG_FLAGS,
} from '../../../src/functions/http/SetConfigFlag';
import {
  setImpl as setConfigDBImpl,
  resetImpl as resetConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setAuthImpl,
  resetImpl as resetAuthImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';

const ADMIN_EMAIL = 'admin@example.com';
const TOKEN = 'any-id-token';

// A realistic full config doc: queryParams + a body that mixes the load-bearing
// buildTimestamp, a secret, the admin allowlist, and two editable flags.
function fullConfig(overrides: any = {}) {
  return {
    queryParams: { some: 'query' },
    body: {
      buildTimestamp: 'Sat Mar 13 14:45:00 2021',
      remoteButtonPushKey: 'SUPER_SECRET',
      configFlagAdminAllowedEmails: [ADMIN_EMAIL],
      resolvedOnCloseEnabled: true,
      warningReplaceTagEnabled: false,
      ...overrides,
    },
  };
}

describe('SetConfigFlag endpoint cores', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeAuth: FakeAuthService;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeAuth = new FakeAuthService();
    setConfigDBImpl(fakeConfig);
    setAuthImpl(fakeAuth);
  });

  afterEach(() => {
    resetConfigDBImpl();
    resetAuthImpl();
  });

  const asAdmin = () => fakeAuth.seedDecoded({ email: ADMIN_EMAIL, email_verified: true });

  // --- Auth (shared by list + set) ---

  it('401 when no token header is present', async () => {
    fakeConfig.seed(fullConfig());
    const result = await handleListConfigFlags({ googleIdTokenHeader: undefined });
    expect(result.kind).to.equal('error');
    expect((result as any).status).to.equal(401);
  });

  it('401 when the token fails to verify', async () => {
    fakeConfig.seed(fullConfig());
    fakeAuth.failNextVerify(new Error('bad token'));
    const result = await handleListConfigFlags({ googleIdTokenHeader: TOKEN });
    expect((result as any).status).to.equal(401);
  });

  it('403 when the verified email is not in the admin allowlist', async () => {
    fakeConfig.seed(fullConfig());
    fakeAuth.seedDecoded({ email: 'stranger@example.com', email_verified: true });
    const result = await handleListConfigFlags({ googleIdTokenHeader: TOKEN });
    expect((result as any).status).to.equal(403);
  });

  it('403 when the email is unverified', async () => {
    fakeConfig.seed(fullConfig());
    fakeAuth.seedDecoded({ email: ADMIN_EMAIL, email_verified: false });
    const result = await handleListConfigFlags({ googleIdTokenHeader: TOKEN });
    expect((result as any).status).to.equal(403);
  });

  it('403 when no admin allowlist is configured (deny-all)', async () => {
    fakeConfig.seed(fullConfig({ configFlagAdminAllowedEmails: undefined }));
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'warningReplaceTagEnabled', value: true },
    });
    expect((result as any).status).to.equal(403);
    expect(fakeConfig.saved).to.be.empty;
  });

  // --- List ---

  it('lists the editable flags + current values for an admin', async () => {
    fakeConfig.seed(fullConfig());
    asAdmin();
    const result = await handleListConfigFlags({ googleIdTokenHeader: TOKEN });
    expect(result.kind).to.equal('ok');
    const data = (result as any).data;
    expect(data.editableFlags).to.deep.equal(EDITABLE_CONFIG_FLAGS);
    // Current values reflected; a missing flag reads as false; no secrets leaked.
    expect(data.flags.resolvedOnCloseEnabled).to.equal(true);
    expect(data.flags.warningReplaceTagEnabled).to.equal(false);
    expect(data.flags.snoozeNotificationsEnabled).to.equal(false); // absent → false
    expect(data.flags).to.not.have.property('remoteButtonPushKey');
    expect(JSON.stringify(data)).to.not.contain('SUPER_SECRET');
  });

  // --- Set: validation ---

  it('400 when the key is not an editable flag', async () => {
    fakeConfig.seed(fullConfig());
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'buildTimestamp', value: true },
    });
    expect((result as any).status).to.equal(400);
    expect(fakeConfig.saved).to.be.empty;
  });

  it('400 when the value is not a boolean', async () => {
    fakeConfig.seed(fullConfig());
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'warningReplaceTagEnabled', value: 'true' },
    });
    expect((result as any).status).to.equal(400);
    expect(fakeConfig.saved).to.be.empty;
  });

  it('500 (anti-clobber) when the current config is missing buildTimestamp — no write', async () => {
    fakeConfig.seed(fullConfig({ buildTimestamp: undefined }));
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'warningReplaceTagEnabled', value: true },
    });
    expect((result as any).status).to.equal(500);
    expect(fakeConfig.saved).to.be.empty; // never wrote an incomplete doc
  });

  // --- Set: happy path + the load-bearing preservation guarantee ---

  it('flips exactly one flag and PRESERVES every other field', async () => {
    fakeConfig.seed(fullConfig());
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'warningReplaceTagEnabled', value: true },
    });

    expect(result.kind).to.equal('ok');
    const data = (result as any).data;
    expect(data).to.deep.equal({ key: 'warningReplaceTagEnabled', value: true, previous: false });

    expect(fakeConfig.saved).to.have.length(1);
    const saved = fakeConfig.saved[0];
    // The target flag changed...
    expect(saved.body.warningReplaceTagEnabled).to.equal(true);
    // ...and NOTHING else did.
    expect(saved.body.buildTimestamp).to.equal('Sat Mar 13 14:45:00 2021');
    expect(saved.body.remoteButtonPushKey).to.equal('SUPER_SECRET');
    expect(saved.body.configFlagAdminAllowedEmails).to.deep.equal([ADMIN_EMAIL]);
    expect(saved.body.resolvedOnCloseEnabled).to.equal(true); // sibling flag untouched
    expect(saved.queryParams).to.deep.equal({ some: 'query' });
  });

  it('reports previous=null when the flag was absent, and sets it', async () => {
    fakeConfig.seed(fullConfig({ snoozeNotificationsEnabled: undefined }));
    asAdmin();
    const result = await handleSetConfigFlag({
      googleIdTokenHeader: TOKEN,
      body: { key: 'snoozeNotificationsEnabled', value: true },
    });
    expect(result.kind).to.equal('ok');
    expect((result as any).data).to.deep.equal({
      key: 'snoozeNotificationsEnabled',
      value: true,
      previous: null,
    });
    expect(fakeConfig.saved[0].body.snoozeNotificationsEnabled).to.equal(true);
    expect(fakeConfig.saved[0].body.buildTimestamp).to.equal('Sat Mar 13 14:45:00 2021');
  });
});
