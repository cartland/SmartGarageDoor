/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { FakeServerConfigDatabase } from '../fakes/FakeServerConfigDatabase';
import { FakeAuthService } from '../fakes/FakeAuthService';

export const DEFAULT_PUSH_KEY = 'test-push-key';
export const DEFAULT_EMAIL = 'authorized@example.com';

/**
 * Seed the given fake-config with a remote-button auth configuration and
 * prime the given fake-auth-service to decode into the given email. After
 * this call, the three auth-heavy HTTP handlers
 * (httpRemoteButton-add-command, httpSnoozeNotificationsRequest) will
 * accept:
 *  - X-RemoteButtonPushKey: <pushKey>   (DEFAULT_PUSH_KEY if omitted)
 *  - X-AuthTokenGoogle:     <any string — FakeAuthService ignores content>
 * and route through the authorized-user happy path.
 *
 * Callers can still override any piece individually — this is a
 * convenience, not a contract.
 */
export function setupAuthHappyPath(
  fakeConfig: FakeServerConfigDatabase,
  fakeAuth: FakeAuthService,
  opts: {
    email?: string;
    pushKey?: string;
    snoozeNotificationsEnabled?: boolean;
    remoteButtonEnabled?: boolean;
  } = {},
): void {
  const email = opts.email ?? DEFAULT_EMAIL;
  const pushKey = opts.pushKey ?? DEFAULT_PUSH_KEY;
  fakeConfig.seed({
    body: {
      remoteButtonEnabled: opts.remoteButtonEnabled ?? true,
      snoozeNotificationsEnabled: opts.snoozeNotificationsEnabled ?? true,
      remoteButtonPushKey: pushKey,
      remoteButtonAuthorizedEmails: [email],
    },
  });
  fakeAuth.seedDecoded({ email });
}
