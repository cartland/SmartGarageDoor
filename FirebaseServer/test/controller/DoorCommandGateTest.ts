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
 * The decision table, driven by the shared fixture in
 * `wire-contracts/doorCommand/` so the Kotlin client and this server are
 * asserting against the same document rather than two copies of a rule.
 */
import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';

import {
  DoorCommand,
  DoorCommandRejection,
  DoorGateState,
  CHECK_IN_STALE_THRESHOLD_SECONDS,
  judgeDoorCommand,
  parseDoorCommand,
  projectDoorState,
  rejectionFor,
} from '../../src/controller/DoorCommandGate';
import { SensorEventType } from '../../src/model/SensorEvent';

interface VerdictRow {
  sensorEventType: string | null;
  doorState: string;
  open: string | null;
  close: string | null;
}

const TABLE = JSON.parse(
  fs.readFileSync(
    path.join(__dirname, '../../../wire-contracts/doorCommand/verdict_table.json'),
    'utf8',
  ),
) as { staleThresholdSeconds: number; rows: VerdictRow[] };

const NOW = 1_700_000_000;
/** A check-in recent enough that staleness never enters into it. */
const FRESH = NOW - 5;

describe('DoorCommandGate', () => {
  describe('the shared verdict table', () => {
    it('covers every sensor event type the server can emit', () => {
      // Without this, a new SensorEventType would silently fall through to the
      // projection's `default` and no row would ever exercise it — the table
      // would still pass while saying nothing about the new state.
      const covered = new Set(TABLE.rows.map((r) => r.sensorEventType));
      const missing = Object.values(SensorEventType).filter((t) => !covered.has(t));
      expect(missing, 'sensor event types absent from the fixture').to.deep.equal([]);
    });

    it('agrees with the server on the stale threshold', () => {
      // The client pins the same number (CheckInStatusMapper.STALE_THRESHOLD_SECONDS).
      expect(TABLE.staleThresholdSeconds).to.equal(CHECK_IN_STALE_THRESHOLD_SECONDS);
    });

    TABLE.rows.forEach((row) => {
      const label = row.sensorEventType ?? 'no event';
      it(`${label} projects to ${row.doorState}`, () => {
        expect(projectDoorState(row.sensorEventType as SensorEventType | null))
          .to.equal(row.doorState);
      });

      it(`${label} answers ${row.open ?? 'ACCEPT'} to open and ${row.close ?? 'ACCEPT'} to close`, () => {
        const state = row.doorState as DoorGateState;
        expect(rejectionFor(state, DoorCommand.Open), 'open').to.equal(row.open);
        expect(rejectionFor(state, DoorCommand.Close), 'close').to.equal(row.close);
      });
    });

    it('accepts at least one command and refuses at least one', () => {
      // Positive control. Every assertion above compares the gate against the
      // fixture, so a `rejectionFor` that returned one constant for everything
      // would fail the table only if the table itself contains both outcomes.
      // Assert that it does, or the whole suite could pass vacuously.
      const outcomes = TABLE.rows.flatMap((r) => [r.open, r.close]);
      expect(outcomes.some((o) => o === null), 'no accepting row in the table').to.be.true;
      expect(outcomes.some((o) => o !== null), 'no refusing row in the table').to.be.true;
    });
  });

  describe('staleness', () => {
    it('forces UNKNOWN even when the reported position is clean', () => {
      const verdict = judgeDoorCommand({
        event: {
          type: SensorEventType.Closed,
          checkInTimestampSeconds: NOW - CHECK_IN_STALE_THRESHOLD_SECONDS - 1,
        },
        command: DoorCommand.Open,
        nowSeconds: NOW,
      });
      expect(verdict.checkInStale).to.be.true;
      expect(verdict.doorState).to.equal(DoorGateState.Unknown);
      expect(verdict.accepted).to.be.false;
      expect(verdict.rejection).to.equal(DoorCommandRejection.DoorStateUnknown);
      // The raw reading is still reported, so a client can explain itself.
      expect(verdict.sensorEventType).to.equal(SensorEventType.Closed);
    });

    it('accepts the same door one second inside the threshold', () => {
      // The other half of the boundary. Without it, a gate that called
      // everything stale would pass the test above.
      const verdict = judgeDoorCommand({
        event: {
          type: SensorEventType.Closed,
          checkInTimestampSeconds: NOW - CHECK_IN_STALE_THRESHOLD_SECONDS + 1,
        },
        command: DoorCommand.Open,
        nowSeconds: NOW,
      });
      expect(verdict.checkInStale).to.be.false;
      expect(verdict.accepted).to.be.true;
    });

    it('treats a missing check-in as stale rather than fresh', () => {
      const verdict = judgeDoorCommand({
        event: { type: SensorEventType.Closed },
        command: DoorCommand.Open,
        nowSeconds: NOW,
      });
      expect(verdict.checkInStale).to.be.true;
      expect(verdict.checkInAgeSeconds).to.equal(null);
      expect(verdict.accepted).to.be.false;
    });
  });

  describe('judgeDoorCommand', () => {
    it('reports the whole basis of the decision, not just the answer', () => {
      const verdict = judgeDoorCommand({
        event: { type: SensorEventType.OpeningTooLong, checkInTimestampSeconds: FRESH },
        command: DoorCommand.Close,
        nowSeconds: NOW,
      });
      expect(verdict).to.deep.equal({
        command: DoorCommand.Close,
        accepted: true,
        rejection: null,
        doorState: DoorGateState.Stuck,
        sensorEventType: SensorEventType.OpeningTooLong,
        checkInStale: false,
        checkInAgeSeconds: 5,
      });
    });

    it('refuses to open a stuck door it would let you close', () => {
      const stuck = { type: SensorEventType.ClosingTooLong, checkInTimestampSeconds: FRESH };
      expect(judgeDoorCommand({ event: stuck, command: DoorCommand.Close, nowSeconds: NOW }).accepted)
        .to.be.true;
      const opening = judgeDoorCommand({ event: stuck, command: DoorCommand.Open, nowSeconds: NOW });
      expect(opening.accepted).to.be.false;
      expect(opening.rejection).to.equal(DoorCommandRejection.DoorStuck);
    });

    it('treats a missing event and an empty document the same way', () => {
      // TimeSeriesDatabase.getCurrent answers `{}` for a missing document, so
      // both shapes reach the gate in practice.
      [null, {}].forEach((event) => {
        const verdict = judgeDoorCommand({ event, command: DoorCommand.Close, nowSeconds: NOW });
        expect(verdict.doorState).to.equal(DoorGateState.Unknown);
        expect(verdict.sensorEventType).to.equal(null);
        expect(verdict.accepted).to.be.false;
      });
    });

    it('projects an unrecognized type from a future firmware to UNKNOWN', () => {
      const verdict = judgeDoorCommand({
        event: { type: 'SOMETHING_NEW', checkInTimestampSeconds: FRESH },
        command: DoorCommand.Close,
        nowSeconds: NOW,
      });
      expect(verdict.doorState).to.equal(DoorGateState.Unknown);
      expect(verdict.accepted).to.be.false;
      expect(verdict.sensorEventType).to.equal('SOMETHING_NEW');
    });
  });

  describe('parseDoorCommand', () => {
    it('accepts either case', () => {
      expect(parseDoorCommand('open')).to.equal(DoorCommand.Open);
      expect(parseDoorCommand('OPEN')).to.equal(DoorCommand.Open);
      expect(parseDoorCommand('Close')).to.equal(DoorCommand.Close);
    });

    it('rejects anything else', () => {
      [undefined, null, '', 'opening', 'toggle', 'press', 42, {}].forEach((raw) => {
        expect(parseDoorCommand(raw), String(raw)).to.equal(null);
      });
    });
  });
});
