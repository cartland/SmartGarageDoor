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

import { SensorEvent } from './SensorEvent';

/**
 * The stored shape of `eventsCurrent/<buildTimestamp>`, and the one place that
 * knows a door reading is NESTED inside it.
 *
 * ## Why this file exists
 *
 * The document is a wrapper, not a reading:
 *
 * ```
 * { buildTimestamp, previousEvent: {...}, currentEvent: { type, checkInTimestampSeconds, ... } }
 * ```
 *
 * `SensorEventDatabase.getCurrent()` returns that whole wrapper as `any`, so
 * reading `doc.type` compiles, returns `undefined`, and looks exactly like a
 * door that has never reported. Every consumer used to re-derive the unwrap
 * from the literal `'currentEvent'`, which meant every consumer could get it
 * wrong independently — and `httpDoorCommand` did, refusing every spoken
 * command with DOOR_STATE_UNKNOWN for the whole of server/35. See
 * `readCurrentEvent` for the guarantee that replaced that literal.
 */
export const CURRENT_EVENT_KEY = 'currentEvent';

/** Companion of {@link CURRENT_EVENT_KEY}: the reading this one replaced. */
export const PREVIOUS_EVENT_KEY = 'previousEvent';

/**
 * The wrapper document stored under `eventsCurrent/<buildTimestamp>`.
 *
 * Every field is optional on purpose: this describes untrusted Firestore data,
 * including the `{}` that `getCurrent()` answers for a missing document.
 */
export interface SensorEventDocument {
  buildTimestamp?: string;
  previousEvent?: SensorEvent | null;
  currentEvent?: SensorEvent | null;
}

/**
 * Read the current door reading out of a stored document.
 *
 * This is the ONLY sanctioned way to get from a stored document to a
 * `SensorEvent`. Reach for it instead of writing `doc.currentEvent`, so that
 * the nesting is stated once rather than once per caller.
 *
 * Answers `null` for every shape that does not carry a reading — a missing
 * document (`{}`), an explicit null, and, importantly, a BARE event that was
 * stored without its wrapper. That last case is the bug this function exists
 * to make impossible to reintroduce silently: a test fixture seeded flat now
 * reads back as "no event" in tests exactly as it does in production, so a
 * fixture can no longer agree with a wrong reader. Pinned by
 * `test/model/SensorEventDocumentTest.ts`.
 */
export function readCurrentEvent(doc: SensorEventDocument | any): SensorEvent | null {
  if (!doc || typeof doc !== 'object') {
    return null;
  }
  const event = doc[CURRENT_EVENT_KEY];
  if (!event || typeof event !== 'object') {
    return null;
  }
  return event as SensorEvent;
}
