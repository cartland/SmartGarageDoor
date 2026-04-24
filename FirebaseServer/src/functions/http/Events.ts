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

import { v4 as uuidv4 } from 'uuid';

import * as functions from 'firebase-functions/v1';

import { DATABASE as SensorEventDatabase } from '../../database/SensorEventDatabase';

import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull } from '../../controller/EventInterpreter';
import { HandlerResult, ok, err } from '../HandlerResult';

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EVENT_HISTORY_MAX_COUNT_PARAM_KEY = "eventHistoryMaxCount";
const EVENT_HISTORY_MAX_COUNT_DEFAULT_VALUE = 12;

const SENSOR_A_PARAM_KEY = "sensorA";
const SENSOR_B_PARAM_KEY = "sensorB";
const TIMESTAMP_SECONDS_PARAM_KEY = "timestampSeconds";

const CURRENT_EVENT_DATA_KEY = "currentEventData";
const EVENT_HISTORY_KEY = "eventHistory";
const EVENT_HISTORY_COUNT_KEY = "eventHistoryCount";
const NEW_EVENT_KEY = "newEvent";
const OLD_EVENT_KEY = "oldEvent";

/**
 * Pure core for the currentEventData endpoint. H5 of the handler
 * testing plan.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - Missing/falsy buildTimestamp → 400 { error: 'Invalid buildTimestamp' }
 *  - Otherwise → 200 with { queryParams, body, session, buildTimestamp,
 *    currentEventData } — session echoed from query or generated as UUID.
 */
export async function handleCurrentEventData(input: {
  query: any;
  body: any;
}): Promise<HandlerResult<any>> {
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  if (input.query && SESSION_PARAM_KEY in input.query) {
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp) {
    return err(400, { error: 'Invalid buildTimestamp' });
  }
  const currentData = await SensorEventDatabase.getCurrent(buildTimestamp);
  data[CURRENT_EVENT_DATA_KEY] = currentData;
  return ok(data);
}

/**
 * Pure core for the eventHistory endpoint. H5 of the handler testing
 * plan. Behavior byte-identical — includes the `parseInt` + default
 * fallback chain for the max-count param.
 */
export async function handleEventHistory(input: {
  query: any;
  body: any;
}): Promise<HandlerResult<any>> {
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  if (input.query && SESSION_PARAM_KEY in input.query) {
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp) {
    return err(400, { error: 'Invalid buildTimestamp' });
  }
  if (input.query && EVENT_HISTORY_MAX_COUNT_PARAM_KEY in input.query) {
    data[EVENT_HISTORY_MAX_COUNT_PARAM_KEY] = input.query[EVENT_HISTORY_MAX_COUNT_PARAM_KEY];
  }
  let eventHistoryMaxCount = parseInt(data[EVENT_HISTORY_MAX_COUNT_PARAM_KEY]);
  if (!eventHistoryMaxCount) {
    eventHistoryMaxCount = EVENT_HISTORY_MAX_COUNT_DEFAULT_VALUE;
  }
  const allData = await SensorEventDatabase.getRecentForBuildTimestamp(buildTimestamp, eventHistoryMaxCount);
  data[EVENT_HISTORY_KEY] = allData;
  data[EVENT_HISTORY_COUNT_KEY] = allData.length;
  return ok(data);
}

/**
 * Pure core for the event ingestion endpoint. H5 of the handler
 * testing plan.
 *
 * Reads the current event, interprets a new one via
 * getNewEventOrNull, saves if the interpretation returned a new event,
 * and returns the { oldEvent, newEvent } tuple under the existing keys.
 *
 * Unlike the other Events handlers, this one does NOT 400-guard on a
 * missing buildTimestamp — the pre-extraction code allowed
 * `SensorEventDatabase.getCurrent(undefined)` to proceed, and the
 * caller's own validation catches the degenerate case. Preserving
 * that so logging/diagnostics don't change.
 */
export async function handleNextEvent(input: {
  query: any;
  body: any;
}): Promise<HandlerResult<any>> {
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  if (input.query && SESSION_PARAM_KEY in input.query) {
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];

  const sensorSnapshot = <SensorSnapshot>{
    sensorA: null,
    sensorB: null,
    timestampSeconds: 0,
  };
  if (input.query && SENSOR_A_PARAM_KEY in input.query) {
    sensorSnapshot.sensorA = String(input.query[SENSOR_A_PARAM_KEY]);
  }
  if (input.query && SENSOR_B_PARAM_KEY in input.query) {
    sensorSnapshot.sensorB = String(input.query[SENSOR_B_PARAM_KEY]);
  }
  let timestampSeconds: number = null;
  if (input.query && TIMESTAMP_SECONDS_PARAM_KEY in input.query) {
    timestampSeconds = parseInt(String(input.query[TIMESTAMP_SECONDS_PARAM_KEY]));
  }
  const oldEvent = await SensorEventDatabase.getCurrent(buildTimestamp);
  const newEvent = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
  if (newEvent !== null) {
    await SensorEventDatabase.save(buildTimestamp, newEvent);
  }
  data[OLD_EVENT_KEY] = oldEvent;
  data[NEW_EVENT_KEY] = newEvent;
  return ok(data);
}

/**
 * curl -H "Content-Type: application/json" http://localhost:5001/PROJECT-ID/us-central1/currentEventData?session=ABC&buildTimestamp=123&eventHistoryMaxCount=12
 */
export const httpCurrentEventData = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleCurrentEventData({ query: request.query, body: request.body });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error(error);
    response.status(500).send(error);
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5001/PROJECT-ID/us-central1/eventHistory?session=ABC&buildTimestamp=123&eventHistoryMaxCount=20
 */
export const httpEventHistory = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleEventHistory({ query: request.query, body: request.body });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error(error);
    response.status(500).send(error);
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/event?session=ABC
 */
export const httpNextEvent = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleNextEvent({ query: request.query, body: request.body });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error(error);
    response.status(500).send(error);
  }
});
