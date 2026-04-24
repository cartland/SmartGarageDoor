/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as firebase from 'firebase-admin';
import { AuthService } from '../../src/controller/AuthService';

/**
 * In-memory fake for AuthService. Tests seed a decoded-token payload and
 * every verifyIdToken() call resolves with it — the `idToken` argument is
 * captured but not inspected (no real token parsing).
 *
 * Use `failNextVerify(error)` to arm the next call to reject, pinning the
 * auth-throw behavior difference between httpAddRemoteButtonCommand
 * (propagates → 500) and httpSnoozeNotificationsRequest (catches → 401).
 */
export class FakeAuthService implements AuthService {
  /** Audit log of every verifyIdToken call (succeeded OR threw). */
  readonly verifyCalls: string[] = [];

  private _decoded: firebase.auth.DecodedIdToken | null = null;
  private _nextVerifyError: Error | null = null;

  async verifyIdToken(idToken: string): Promise<firebase.auth.DecodedIdToken> {
    this.verifyCalls.push(idToken);
    if (this._nextVerifyError) {
      const e = this._nextVerifyError;
      this._nextVerifyError = null;
      throw e;
    }
    if (!this._decoded) {
      // A real DecodedIdToken has many fields; tests that don't seed a
      // decoded payload usually only care about `.email`. Returning a
      // minimal shape forces tests to be explicit about what they rely on.
      return <firebase.auth.DecodedIdToken>{ uid: 'fake-uid' };
    }
    return this._decoded;
  }

  /** Test-only: seed the DecodedIdToken returned by the next verifyIdToken. */
  seedDecoded(decoded: Partial<firebase.auth.DecodedIdToken>): void {
    this._decoded = <firebase.auth.DecodedIdToken>{
      uid: 'fake-uid',
      ...decoded,
    };
  }

  /** Test-only: arm the NEXT verifyIdToken call to reject with `error`. */
  failNextVerify(error: Error): void { this._nextVerifyError = error; }

  /** Test-only: wipe audit log + seed + any armed failure. */
  clear(): void {
    this.verifyCalls.length = 0;
    this._decoded = null;
    this._nextVerifyError = null;
  }
}
