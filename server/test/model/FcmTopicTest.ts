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

// npm run tests

import { expect } from 'chai';
import { buildTimestampToFcmTopic } from '../../src/model/FcmTopic';

describe('buildTimestampToFcmTopic', () => {
  it('can convert buildTimestamp to FcmTopic', () => {
    const input = 'Sat Mar 13 14:45:00 2021';
    const expected = 'door_open-Sat.Mar.13.14.45.00.2021';
    const actual = buildTimestampToFcmTopic(input);
    expect(actual).to.equal(expected);
  });
});
