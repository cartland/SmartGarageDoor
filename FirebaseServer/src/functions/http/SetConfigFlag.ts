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
import { getConfigFlagAdminAllowedEmails, getBuildTimestamp } from '../../controller/config/ConfigAccessors';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { isEmailInAllowlist } from '../../controller/Auth';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

/**
 * A SAFE, restricted way to read + flip server feature flags — the alternative
 * to the whole-doc `httpServerConfigUpdate` (which overwrites the entire config
 * and can clobber buildTimestamp / secrets / allowlists) and to hand-editing
 * Firestore. See docs/FIREBASE_CONFIG_AUTHORITY.md.
 *
 * Three layered guarantees make it hard to mess up:
 *  1. Permission: the caller must present a valid Firebase ID token (from the
 *     Android/iOS "copy auth token" affordance) whose verified email is in the
 *     `configFlagAdminAllowedEmails` allowlist (console-edited; deny-all when
 *     unset). Per-person, auditable, revocable by editing the list — no shared
 *     secret. Works identically from either app.
 *  2. Scope: only the keys in EDITABLE_CONFIG_FLAGS (hardcoded, never
 *     request-supplied) can be written. buildTimestamp, remoteButtonPushKey,
 *     the allowlists, and everything else are structurally unreachable.
 *  3. Integrity: values must be booleans; the write is a read-modify-write that
 *     changes ONLY `body[key]` (preserving queryParams + every other body
 *     field), and it refuses to write if the current config is missing
 *     buildTimestamp (never persist an incomplete doc). The existing history
 *     append (configAll) records every flip.
 */

/**
 * The ONLY config keys this endpoint may write. Hardcoded here, never taken from
 * the request — this is guarantee #2. All are boolean feature flags. Adding a
 * key is a deliberate one-line change + deploy; NEVER add buildTimestamp, a
 * secret, an allowlist, or anything destructive (e.g. deleteOldDataEnabled).
 */
export const EDITABLE_CONFIG_FLAGS: readonly string[] = [
  'resolvedOnCloseEnabled',
  'warningReplaceTagEnabled',
  'resolvedNotificationPayloadEnabled',
  'snoozeNotificationsEnabled',
  'remoteButtonEnabled',
];

/**
 * On success: the caller's email + the config read for the allowlist check
 * (reused for the list/merge). On failure: an error HandlerResult (401 =
 * missing/invalid token; 403 = verified but unverified email or not an admin).
 * Callers narrow with `'kind' in auth` — a HandlerResult has `kind`, the success
 * shape does not.
 */
type AdminAuth = { email: string; config: any } | HandlerResult<unknown>;

async function authorizeConfigAdmin(googleIdTokenHeader: string | undefined): Promise<AdminAuth> {
  if (!googleIdTokenHeader || googleIdTokenHeader.length <= 0) {
    return err(401, { error: 'Unauthorized (token).' });
  }
  let email: string;
  try {
    const decoded = await AuthService.verifyIdToken(googleIdTokenHeader);
    if (!decoded.email || decoded.email_verified !== true) {
      return err(403, { error: 'Forbidden.' });
    }
    email = decoded.email;
  } catch (error: any) {
    console.error(error);
    return err(401, { error: 'Unauthorized (token).' });
  }
  const config = await ServerConfigDatabase.get();
  if (!isEmailInAllowlist(email, getConfigFlagAdminAllowedEmails(config))) {
    return err(403, { error: 'Forbidden.' });
  }
  return { email, config };
}

/** Editable flags with their current values (missing = false). Never a secret. */
function editableFlagValues(config: any): Record<string, boolean> {
  const body = config?.body ?? {};
  const flags: Record<string, boolean> = {};
  for (const key of EDITABLE_CONFIG_FLAGS) {
    flags[key] = body[key] === true;
  }
  return flags;
}

/**
 * Pure core — GET: list the editable flags + their current values. Admin-gated.
 * Returns only the editable flags, never other config or secrets.
 */
export async function handleListConfigFlags(input: {
  googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<unknown>> {
  const auth = await authorizeConfigAdmin(input.googleIdTokenHeader);
  if ('kind' in auth) {
    return auth; // an error HandlerResult (401/403)
  }
  return ok({ editableFlags: EDITABLE_CONFIG_FLAGS, flags: editableFlagValues(auth.config) });
}

/**
 * Pure core — POST: flip ONE allowlisted boolean flag. Gates in order:
 * admin auth → key in EDITABLE_CONFIG_FLAGS → value is boolean → anti-clobber
 * read-guard. The write changes ONLY `body[key]`.
 */
export async function handleSetConfigFlag(input: {
  googleIdTokenHeader: string | undefined;
  body: any;
}): Promise<HandlerResult<unknown>> {
  const auth = await authorizeConfigAdmin(input.googleIdTokenHeader);
  if ('kind' in auth) {
    return auth; // an error HandlerResult (401/403)
  }

  const key = input.body?.key;
  const value = input.body?.value;
  if (typeof key !== 'string' || !EDITABLE_CONFIG_FLAGS.includes(key)) {
    return err(400, { error: 'key is not an editable flag.', editableFlags: EDITABLE_CONFIG_FLAGS });
  }
  if (typeof value !== 'boolean') {
    return err(400, { error: 'value must be a boolean (true or false).' });
  }

  // Anti-clobber guard: never overwrite the config doc with data that is missing
  // the load-bearing buildTimestamp — a partial/incomplete read must abort.
  const config = auth.config;
  if (getBuildTimestamp(config) === null) {
    console.error('SetConfigFlag: current config missing buildTimestamp; refusing to write.');
    return err(500, { error: 'Refusing to write: current config is missing buildTimestamp.' });
  }

  // Prior value, or null when the flag was unset/undefined (false stays false —
  // nullish coalescing only fills null/undefined).
  const previous = config.body?.[key] ?? null;
  const newConfig = { ...config, body: { ...config.body, [key]: value } };
  await ServerConfigDatabase.set(newConfig);
  return ok({ key, value, previous });
}

/**
 * GET  → list editable flags + current values.
 * POST → set one flag: body { key, value:boolean }.
 * Auth header: X-AuthTokenGoogle: <Firebase ID token>.
 *
 * curl -H "X-AuthTokenGoogle: <id_token>" \
 *      "https://us-central1-<project>.cloudfunctions.net/configFlags"
 */
export const httpConfigFlags = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    let result: HandlerResult<unknown>;
    if (request.method === 'GET') {
      result = await handleListConfigFlags({ googleIdTokenHeader: request.get('X-AuthTokenGoogle') });
    } else if (request.method === 'POST') {
      result = await handleSetConfigFlag({
        googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
        body: request.body,
      });
    } else {
      result = err(405, { error: 'Method Not Allowed.' });
    }
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
