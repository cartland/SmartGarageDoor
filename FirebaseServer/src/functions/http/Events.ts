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

import { DATABASE as SensorEventDatabase, EventPage, TimestampCursor, PageDirection } from '../../database/SensorEventDatabase';

import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull } from '../../controller/EventInterpreter';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EVENT_HISTORY_MAX_COUNT_PARAM_KEY = "eventHistoryMaxCount";

const SENSOR_A_PARAM_KEY = "sensorA";
const SENSOR_B_PARAM_KEY = "sensorB";
const TIMESTAMP_SECONDS_PARAM_KEY = "timestampSeconds";

const CURRENT_EVENT_DATA_KEY = "currentEventData";
const EVENT_HISTORY_KEY = "eventHistory";
const EVENT_HISTORY_COUNT_KEY = "eventHistoryCount";
const NEW_EVENT_KEY = "newEvent";
const OLD_EVENT_KEY = "oldEvent";

// Pagination params + response keys (additive — old clients ignore unknown keys).
const PAGE_SIZE_PARAM_KEY = "pageSize";
const PAGE_TOKEN_PARAM_KEY = "pageToken";
const DIRECTION_PARAM_KEY = "direction";
const NEXT_PAGE_TOKEN_KEY = "nextPageToken";
const PREV_PAGE_TOKEN_KEY = "prevPageToken";
const HAS_MORE_KEY = "hasMore";

const PAGE_SIZE_MAX = 100;
const PAGE_SIZE_DEFAULT = 100;
const WINDOW_SECONDS = 7 * 24 * 60 * 60; // default first-page window: last 7 days
const PAGE_TOKEN_VERSION = 1;

/**
 * Opaque cursor token: the boundary doc's `FIRESTORE_databaseTimestamp`
 * (seconds + nanoseconds, sub-second precision so same-second events aren't
 * skipped), the direction it pages toward, and the buildTimestamp it is scoped
 * to. base64url(JSON) — clients treat it as fully opaque.
 */
interface PageToken {
  v: number;
  bt: string;
  s: number;
  n: number;
  d: PageDirection;
}

function encodePageToken(bt: string, cursor: TimestampCursor, d: PageDirection): string {
  const payload: PageToken = { v: PAGE_TOKEN_VERSION, bt, s: cursor.seconds, n: cursor.nanoseconds, d };
  return Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
}

function decodePageToken(token: string): PageToken | null {
  try {
    const parsed = JSON.parse(Buffer.from(token, 'base64url').toString('utf8'));
    if (!parsed || parsed.v !== PAGE_TOKEN_VERSION) return null;
    if (typeof parsed.bt !== 'string') return null;
    if (typeof parsed.s !== 'number' || typeof parsed.n !== 'number') return null;
    if (parsed.d !== 'older' && parsed.d !== 'newer') return null;
    return parsed as PageToken;
  } catch {
    return null;
  }
}

function parsePositiveInt(value: any): number | null {
  const n = parseInt(value);
  return Number.isFinite(n) && n > 0 ? n : null;
}

/** Typed shape of the eventHistory response (legacy keys + additive pagination). */
export interface EventHistoryResponse {
  queryParams: any;
  body: any;
  session: string;
  buildTimestamp: string;
  eventHistoryMaxCount?: any;
  eventHistory: any[];
  eventHistoryCount: number;
  nextPageToken: string | null; // older / into the past; null = no older events
  prevPageToken: string | null; // newer / toward the present
  hasMore: boolean; // mirrors nextPageToken != null (the older direction the UI consumes)
}

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
 * Pure core for the eventHistory endpoint.
 *
 * Universal default: every non-cursor request returns the last 7 days of
 * events, capped at the page size (default/max 50), newest first. A valid
 * `pageToken` pages further into the past (cursor mode, no time window). The
 * response preserves the legacy `eventHistory`/`eventHistoryCount` keys and
 * adds opaque `nextPageToken` (older), `prevPageToken` (newer), and `hasMore`.
 *
 * `pageSize` and the legacy `eventHistoryMaxCount` both set the limit (pageSize
 * wins); the client may send both during transition. `nowMillis` is injected so
 * the 7-day window is deterministic in tests.
 */
export async function handleEventHistory(input: {
  query: any;
  body: any;
  nowMillis?: number;
}): Promise<HandlerResult<EventHistoryResponse>> {
  const query = input.query || {};
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  data[SESSION_PARAM_KEY] = SESSION_PARAM_KEY in query ? query[SESSION_PARAM_KEY] : uuidv4();
  if (BUILD_TIMESTAMP_PARAM_KEY in query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = query[BUILD_TIMESTAMP_PARAM_KEY];
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp) {
    return err(400, { error: 'Invalid buildTimestamp' });
  }
  // Preserve the legacy echo of eventHistoryMaxCount when present.
  if (EVENT_HISTORY_MAX_COUNT_PARAM_KEY in query) {
    data[EVENT_HISTORY_MAX_COUNT_PARAM_KEY] = query[EVENT_HISTORY_MAX_COUNT_PARAM_KEY];
  }

  // limit = pageSize ?? legacy eventHistoryMaxCount ?? 50, clamped to [1, 50].
  const requested =
    parsePositiveInt(query[PAGE_SIZE_PARAM_KEY]) ??
    parsePositiveInt(query[EVENT_HISTORY_MAX_COUNT_PARAM_KEY]) ??
    PAGE_SIZE_DEFAULT;
  const limit = Math.min(Math.max(requested, 1), PAGE_SIZE_MAX);

  const rawToken = query[PAGE_TOKEN_PARAM_KEY];
  const decoded =
    typeof rawToken === 'string' && rawToken.length > 0 ? decodePageToken(rawToken) : null;

  // A token is only honored for the buildTimestamp it was scoped to; otherwise
  // (or if malformed) fall back leniently to a fresh windowed first page.
  let isCursorPage = false;
  let direction: PageDirection = 'older';
  let page: EventPage;
  if (decoded !== null && decoded.bt === buildTimestamp) {
    isCursorPage = true;
    const requestedDir = query[DIRECTION_PARAM_KEY];
    direction = requestedDir === 'older' || requestedDir === 'newer' ? requestedDir : decoded.d;
    page = await SensorEventDatabase.getPageForBuildTimestamp({
      buildTimestamp,
      limit,
      direction,
      startAfter: { seconds: decoded.s, nanoseconds: decoded.n },
    });
  } else {
    const nowMillis = input.nowMillis ?? Date.now();
    const sinceSeconds = Math.floor(nowMillis / 1000) - WINDOW_SECONDS;
    page = await SensorEventDatabase.getPageForBuildTimestamp({
      buildTimestamp,
      limit,
      direction: 'older',
      sinceSeconds,
    });
  }

  // nextPageToken always continues OLDER (into the past); prevPageToken always
  // continues NEWER (toward the present), regardless of which way this page ran.
  let nextPageToken: string | null = null;
  let prevPageToken: string | null = null;
  if (direction === 'older') {
    nextPageToken =
      page.hasMore && page.oldestCursor
        ? encodePageToken(buildTimestamp, page.oldestCursor, 'older')
        : null;
    prevPageToken =
      isCursorPage && page.newestCursor
        ? encodePageToken(buildTimestamp, page.newestCursor, 'newer')
        : null;
  } else {
    prevPageToken =
      page.hasMore && page.newestCursor
        ? encodePageToken(buildTimestamp, page.newestCursor, 'newer')
        : null;
    nextPageToken = page.oldestCursor
      ? encodePageToken(buildTimestamp, page.oldestCursor, 'older')
      : null;
  }

  data[EVENT_HISTORY_KEY] = page.items;
  data[EVENT_HISTORY_COUNT_KEY] = page.items.length;
  data[NEXT_PAGE_TOKEN_KEY] = nextPageToken;
  data[PREV_PAGE_TOKEN_KEY] = prevPageToken;
  data[HAS_MORE_KEY] = nextPageToken !== null;
  return ok(data as EventHistoryResponse);
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
export const httpCurrentEventData = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleCurrentEventData({ query: request.query, body: request.body });
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

/**
 * curl -H "Content-Type: application/json" http://localhost:5001/PROJECT-ID/us-central1/eventHistory?session=ABC&buildTimestamp=123&pageSize=50
 * Page further into the past with the opaque nextPageToken from the response:
 * curl ".../eventHistory?buildTimestamp=123&pageSize=50&pageToken=<token>"
 */
export const httpEventHistory = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleEventHistory({ query: request.query, body: request.body, nowMillis: Date.now() });
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

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/event?session=ABC
 */
export const httpNextEvent = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleNextEvent({ query: request.query, body: request.body });
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
