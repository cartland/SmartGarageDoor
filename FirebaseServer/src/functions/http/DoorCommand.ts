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

import * as functions from 'firebase-functions/v1';

import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { DATABASE as SENSOR_EVENT_DATABASE } from '../../database/SensorEventDatabase';
import {
  isRemoteButtonEnabled,
  getRemoteButtonPushKey,
  getRemoteButtonAuthorizedEmails,
  getBuildTimestamp,
  requireBuildTimestamp,
} from '../../controller/config/ConfigAccessors';
import { isEmailInAllowlist } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';
import {
  DoorCommand,
  DoorCommandVerdict,
  judgeDoorCommand,
  parseDoorCommand,
} from '../../controller/DoorCommandGate';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

/**
 * Directional door commands — "open" / "close" — judged against the door's
 * actual state. Runs in parallel to `addRemoteButtonCommand` (the toggle the
 * ESP32 polls) and deliberately does NOT replace it.
 *
 * ## This is for VOICE. Taps keep using the button.
 *
 * A spoken sentence carries a direction. "Open the garage door" and "close the
 * garage door" are different requests, and someone who says one does not mean
 * the other. That is the entire reason a directional endpoint is worth having,
 * and voice is the only surface that supplies a direction to check.
 *
 * A tap does not. The remote button is a toggle — one press, no direction — and
 * the phone's tap-to-confirm and the watch's press-and-hold are deliberately
 * the same shape: they mean "act on the door", and what that does depends on
 * where the door happens to be. **Those surfaces remain the primary way to work
 * the door and are not going to be moved onto this endpoint.** The two-tap
 * confirmation is an "are you sure", not a direction; there is nothing in it
 * for this gate to judge.
 *
 * Pointing these verdicts at a tap would actively break the button. With the
 * door OPEN, a tap is a perfectly good request — it closes. Asking this
 * endpoint `{"command":"open"}` in that same moment is correctly refused as
 * ALREADY_OPEN. The answers differ because the questions differ: "do this
 * specific thing" versus "do the thing". Only the first has a direction to
 * validate, so only voice should be asking.
 *
 * ## It cannot move the door, structurally
 *
 * The device opens the garage by polling `RemoteButtonCommandDatabase` for a
 * pending command. This module never imports that database — not to write, not
 * to read — so there is no expression it could evaluate that would reach the
 * door. `executed` is the literal `false` and there is no branch that sets it
 * otherwise. This is the development posture the endpoint was asked for, and
 * `HttpDoorCommandTest`'s "has no route to the door" asserts the import is
 * still absent rather than trusting this comment. That test was verified to
 * fail by temporarily adding the import back.
 *
 * When the time comes to let it act, that is a deliberate, reviewable change:
 * add the write, flip `executed`, and re-point that test. Nothing else about
 * the endpoint has to move, which is the reason for the next section.
 *
 * ## Why the auth is already the real auth
 *
 * The gates are mirrored from `handleAddRemoteButtonCommand`, in the same
 * order: config-enabled → push-key header → push-key match → id-token header →
 * verifyIdToken → email allowlist. A verdict is only a read and could have
 * been guarded more loosely, but an endpoint that is one commit away from
 * pressing a real garage-door button should not be growing its authentication
 * at the same moment it grows its side effect. Doing it now means the change
 * that arms this endpoint touches the acting code and nothing else.
 *
 * One deliberate difference from the push-button handler: `verifyIdToken` is
 * wrapped in try/catch and answers 401. The push-button endpoint lets a
 * malformed token propagate into a 500, a quirk its own KDoc calls out and
 * pins. That is worth preserving there (3.5 years of production traffic) and
 * not worth copying into new code.
 *
 * ## Why the server is deciding this
 *
 * The rule currently lives on the clients, once per platform, and has already
 * drifted: the phone refuses on a stale check-in, the watch has no staleness
 * signal to refuse on. Interpretation belongs on the server here (CLAUDE.md
 * § Server-Centric Design). The decision table itself is in
 * `controller/DoorCommandGate.ts`, pure and separately tested.
 */

const COMMAND_PARAM_KEY = 'command';

/** Always `false` while this endpoint is verdict-only. See the file KDoc. */
const EXECUTED = false;

export interface DoorCommandResponse {
  /** What the door would do with this command right now. */
  verdict: DoorCommandVerdict;
  /**
   * Whether the command was actually sent to the door. Always false today —
   * this endpoint reports and does not act.
   */
  executed: boolean;
}

/**
 * Pure core.
 *
 *  - Method !== POST                  → 405 { error: 'Method Not Allowed.' }
 *  - Remote button disabled in config → 400 { error: 'Disabled' }
 *  - Missing X-RemoteButtonPushKey    → 401 { error: 'Unauthorized (key).' }
 *  - Push key mismatch                → 403 { error: 'Forbidden (key).' }
 *  - Missing X-AuthTokenGoogle        → 401 { error: 'Unauthorized (token).' }
 *  - verifyIdToken throws / no email  → 401 / 403
 *  - Email not in allowlist           → 403 { error: 'Forbidden (user).' }
 *  - `command` not "open" or "close"  → 400 { error: ..., accepted: ['OPEN','CLOSE'] }
 *  - Otherwise                        → 200 DoorCommandResponse
 *
 * A REFUSED command is a 200, not a 4xx: the caller asked a well-formed
 * question and got a real answer. 4xx here would conflate "you may not ask"
 * with "the door says no", and the client needs to tell those apart to know
 * whether to show a refusal or a sign-in prompt.
 */
export async function handleDoorCommand(input: {
  method: string;
  body: any;
  query: any;
  pushKeyHeader: string | undefined;
  googleIdTokenHeader: string | undefined;
  nowSeconds: number;
}): Promise<HandlerResult<DoorCommandResponse>> {
  if (input.method !== 'POST') {
    return err(405, { error: 'Method Not Allowed.' });
  }
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    return err(400, { error: 'Disabled' });
  }
  if (!input.pushKeyHeader || input.pushKeyHeader.length <= 0) {
    return err(401, { error: 'Unauthorized (key).' });
  }
  if (getRemoteButtonPushKey(config) !== input.pushKeyHeader) {
    return err(403, { error: 'Forbidden (key).' });
  }
  if (!input.googleIdTokenHeader || input.googleIdTokenHeader.length <= 0) {
    return err(401, { error: 'Unauthorized (token).' });
  }
  let email: string;
  try {
    const decodedToken = await AuthService.verifyIdToken(input.googleIdTokenHeader);
    if (!decodedToken.email) {
      return err(403, { error: 'Forbidden (user).' });
    }
    email = decodedToken.email;
  } catch (error: any) {
    console.error(error);
    return err(401, { error: 'Unauthorized (token).' });
  }
  if (!isEmailInAllowlist(email, getRemoteButtonAuthorizedEmails(config))) {
    return err(403, { error: 'Forbidden (user).' });
  }

  // Accept the command from the body or the query string. The push-button
  // endpoint reads its parameters from the query, and curl-ing this by hand
  // during development is the whole point of it existing right now.
  const raw = input.body?.[COMMAND_PARAM_KEY] ?? input.query?.[COMMAND_PARAM_KEY];
  const command: DoorCommand | null = parseDoorCommand(raw);
  if (command === null) {
    return err(400, {
      error: `Missing or invalid parameter: ${COMMAND_PARAM_KEY}`,
      accepted: [DoorCommand.Open, DoorCommand.Close],
    });
  }

  // Throws when config has no buildTimestamp; the wrapper turns that into a
  // 500 and Cloud Logging gets an ERROR. See docs/FIREBASE_CONFIG_AUTHORITY.md.
  const buildTimestamp = requireBuildTimestamp(getBuildTimestamp(config), 'httpDoorCommand');
  // The reading is NESTED inside the stored document, so it has to be unwrapped
  // rather than read off the document. getCurrentEvent() owns that unwrap and
  // answers null for every shape that carries no reading, including the `{}`
  // that getCurrent() returns for a missing document — which then projects to
  // Unknown with a stale check-in, refusing both directions.
  const event = await SENSOR_EVENT_DATABASE.getCurrentEvent(buildTimestamp);

  const verdict = judgeDoorCommand({
    event,
    command,
    nowSeconds: input.nowSeconds,
  });
  console.log('doorCommand verdict', { email, command, verdict, executed: EXECUTED });
  return ok({ verdict, executed: EXECUTED });
}

/**
 * curl -X POST -H "Content-Type: application/json" \
 *      -H "X-RemoteButtonPushKey: <push_key>" \
 *      -H "X-AuthTokenGoogle: <id_token>" \
 *      -d '{"command":"close"}' \
 *      "http://localhost:5000/PROJECT-ID/us-central1/doorCommand"
 */
export const httpDoorCommand = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleDoorCommand({
      method: request.method,
      body: request.body,
      query: request.query,
      pushKeyHeader: request.get('X-RemoteButtonPushKey'),
      googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
      nowSeconds: Math.floor(Date.now() / 1000),
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error(error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
