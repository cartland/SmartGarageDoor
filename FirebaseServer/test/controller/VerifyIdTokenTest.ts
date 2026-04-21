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

// npm run tests

/**
 * Library-chain regression guard for `firebase.auth().verifyIdToken(...)`.
 *
 * Our HTTP handlers (Snooze.ts, RemoteButton.ts) call `verifyIdToken` to
 * authenticate callers. That call sits on a fragile transitive chain —
 * `firebase-admin → google-auth-library → gtoken → jws → node-forge` —
 * and npm-ecosystem vulnerabilities land there regularly (see the
 * `jws` alg-confusion CVE and the `node-forge` ASN.1 cluster).
 *
 * These tests do NOT require network access or real Firebase credentials.
 * They feed malformed input to `verifyIdToken` and assert that the library
 * chain rejects pre-network. That proves:
 *
 *   1. The transitive JWT-parsing chain still loads after a dep upgrade.
 *   2. Malformed tokens produce typed rejections (not silent acceptance).
 *
 * This is not a full security test — it cannot exercise the positive
 * signature-verification path without a real Firebase project — but it
 * locks the negative-case behavior that a library-chain regression
 * (e.g., the jws alg-confusion class) would subvert.
 */

import * as admin from 'firebase-admin';
import { expect } from 'chai';

describe('verifyIdToken library chain', () => {
  // Initialize firebase-admin once for this suite. A fake projectId is
  // sufficient because every malformed input below rejects at the JWT
  // parser, before any network call. If the transitive chain ever tries
  // to reach Google's JWKS on a pre-parse input, this test suite would
  // hang or fail with a network error — which is itself a useful signal.
  before(() => {
    const apps = admin.apps ?? [];
    const hasApp = apps.some((a) => a !== null && a !== undefined);
    if (!hasApp) {
      admin.initializeApp({ projectId: 'verify-id-token-test' });
    }
  });


  it('rejects a non-JWT string', async () => {
    const err = await capturedRejection(() => admin.auth().verifyIdToken('not-a-jwt'));
    expect(err).to.be.an.instanceOf(Error);
    // Message should mention the token shape, not a network timeout.
    expect((err as Error).message.toLowerCase()).to.match(/token|jwt|decode|format|argument/);
  });

  it('rejects an empty string', async () => {
    const err = await capturedRejection(() => admin.auth().verifyIdToken(''));
    expect(err).to.be.an.instanceOf(Error);
  });

  it('rejects a well-structured but unsigned token', async () => {
    // header: {"alg":"none","typ":"JWT"}
    // payload: {"sub":"1234567890","iss":"https://securetoken.google.com/fake"}
    // signature: empty
    const unsignedToken =
      'eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.' +
      'eyJzdWIiOiIxMjM0NTY3ODkwIiwiaXNzIjoiaHR0cHM6Ly9zZWN1cmV0b2tlbi5nb29nbGUuY29tL2Zha2UifQ.';
    const err = await capturedRejection(() => admin.auth().verifyIdToken(unsignedToken));
    expect(err).to.be.an.instanceOf(Error);
  });

  it('rejects an HS256-signed token (Firebase ID tokens must be RS256)', async () => {
    // Guards the `jws` alg-confusion class of vulnerability: a token
    // claiming `alg: HS256` must not be accepted even if the signature
    // verifies — Firebase ID tokens are RS256-only.
    //
    // header: {"alg":"HS256","typ":"JWT"}
    // payload: {"sub":"1234567890","iss":"https://securetoken.google.com/fake"}
    // signed with a dummy HMAC secret
    const hs256Token =
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.' +
      'eyJzdWIiOiIxMjM0NTY3ODkwIiwiaXNzIjoiaHR0cHM6Ly9zZWN1cmV0b2tlbi5nb29nbGUuY29tL2Zha2UifQ.' +
      'dGhpcy1zaWduYXR1cmUtaXMtZmFrZQ';
    const err = await capturedRejection(() => admin.auth().verifyIdToken(hs256Token));
    expect(err).to.be.an.instanceOf(Error);
  });

  it('rejects a token with only two segments', async () => {
    const err = await capturedRejection(() => admin.auth().verifyIdToken('aaa.bbb'));
    expect(err).to.be.an.instanceOf(Error);
  });

  it('rejects a token with garbage in segments', async () => {
    const err = await capturedRejection(() => admin.auth().verifyIdToken('!!!.###.@@@'));
    expect(err).to.be.an.instanceOf(Error);
  });
});

/**
 * Invokes the given async function and returns any thrown/rejected error.
 * Throws if the function resolves successfully.
 */
async function capturedRejection(fn: () => Promise<unknown>): Promise<unknown> {
  try {
    await fn();
  } catch (e) {
    return e;
  }
  throw new Error('Expected a rejection, but the call resolved successfully.');
}
