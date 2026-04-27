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
import { isEmailInAllowlist } from '../../src/controller/Auth';

describe('isEmailInAllowlist', () => {
  it('allows authorized email', () => {
    expect(isEmailInAllowlist('authorized@gmail.com', ['authorized@gmail.com'])).to.equal(true);
  });
  it('blocks unauthorized email', () => {
    expect(
      isEmailInAllowlist('unauthorized@gmail.com', ['authorized@gmail.com', 'alsoauthorized@gmail.com']),
    ).to.equal(false);
  });
  it('returns false (deny-all) when allowlist is null', () => {
    expect(isEmailInAllowlist('anyone@gmail.com', null)).to.equal(false);
  });
  it('returns false (deny-all) when allowlist is empty', () => {
    expect(isEmailInAllowlist('anyone@gmail.com', [])).to.equal(false);
  });
});
