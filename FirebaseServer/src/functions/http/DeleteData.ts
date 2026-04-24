/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
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
import { isDeleteOldDataEnabled } from '../../controller/config/ConfigAccessors';
import { deleteOldData } from '../../controller/DatabaseCleaner';
import { HandlerResult, ok, err } from '../HandlerResult';

export interface DeleteOldDataResult {
  dryRun: boolean;
  summary: object;
}

/**
 * Pure core — testable via FakeServerConfigDatabase + sinon stub on
 * deleteOldData. H5 of the handler testing plan.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - disabled config → 400 { error: 'Disabled' } + existing info log
 *  - otherwise       → parseInt the cutoff param (NaN if absent), pass
 *    dryRun flag (boolean presence in query), call deleteOldData,
 *    return { dryRun, summary: deleteCount }
 */
export async function handleDeleteOldData(input: {
  query: any;
}): Promise<HandlerResult<DeleteOldDataResult>> {
  const config = await ServerConfigDatabase.get();
  if (!isDeleteOldDataEnabled(config)) {
    console.log('Deleting data is disabled');
    return err(400, { error: 'Disabled' });
  }
  let cutoffTimestampSeconds: number = null;
  let dryRun = false;
  if (input.query && 'dryRun' in input.query) {
    dryRun = true;
  }
  if (input.query && 'cutoffTimestampSeconds' in input.query) {
    const s: string = input.query['cutoffTimestampSeconds'].toString();
    cutoffTimestampSeconds = parseInt(s);
  }
  const deleteCount = await deleteOldData(cutoffTimestampSeconds, dryRun);
  return ok({
    dryRun: dryRun,
    summary: deleteCount,
  });
}

export const httpDeleteOldData = functions.https.onRequest(async (request, response) => {
  const result = await handleDeleteOldData({ query: request.query });
  if (result.kind === 'error') {
    response.status(result.status).send(result.body);
  } else {
    await response.status(200).send(result.data);
  }
});
