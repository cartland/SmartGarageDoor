/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * CVE guards — fail if a flagged dependency version is re-introduced.
 *
 * Each test corresponds to a specific Dependabot advisory. The test
 * asserts the resolved version is at or above the patched version. A
 * future `npm install` that downgrades (e.g. a cherry-picked revert)
 * fails this test, forcing a conscious decision to suppress or fix.
 *
 * Template: one `it()` per advisory, one-line revert to skip.
 *
 * Extends the `jws` library-chain regression test in
 * `test/controller/VerifyIdTokenTest.ts` — same philosophy, broader
 * scope. `jws` stays where it is because its test double-duties as a
 * signature-verification sanity check.
 *
 * See docs/FIREBASE_HARDENING_PLAN.md → Part B for rationale + when
 * to add new guards.
 */

import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Read a dependency's installed version by walking up from its resolved
 * main entry until a package.json with the matching name is found.
 * Avoids `require('<pkg>/package.json')` because some packages
 * (fast-xml-parser 5.x) use an `exports` field that restricts subpath
 * imports.
 */
function installedVersion(pkgName: string): string {
  let dir = path.dirname(require.resolve(pkgName));
  for (let i = 0; i < 20; i++) {
    const candidate = path.join(dir, 'package.json');
    if (fs.existsSync(candidate)) {
      const pkg = JSON.parse(fs.readFileSync(candidate, 'utf8'));
      if (pkg.name === pkgName) return pkg.version;
    }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  throw new Error(`Could not find package.json for ${pkgName}`);
}

function semverGte(version: string, min: string): boolean {
  const a = version.split('.').map(Number);
  const b = min.split('.').map(Number);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const av = a[i] ?? 0;
    const bv = b[i] ?? 0;
    if (av > bv) return true;
    if (av < bv) return false;
  }
  return true;
}

describe('CVE guards — fail if a flagged dep version is re-introduced', () => {
  // Dependabot alert #67 — uuid < 14.0.0 (GHSA: v3/v5/v6 buffer bounds
  // check). Our usage (v4() with no buf) is not in the vector, but
  // keeping uuid ≥ 14.0.0 clears the alert and future-proofs the shape
  // of the output tested in UuidTest.ts.
  it('uuid direct dep is ≥ 14.0.0', () => {
    const version = installedVersion('uuid');
    expect(
      semverGte(version, '14.0.0'),
      `uuid ${version} < 14.0.0`,
    ).to.be.true;
  });

  // Dependabot alert #66 — fast-xml-parser < 5.7.0 (GHSA: XMLBuilder
  // comment/CDATA delimiter injection). No direct usage in our src/;
  // pulled transitively via firebase-admin → @google-cloud/storage.
  // The patched version is a major bump (4 → 5); the override in
  // FirebaseServer/package.json forces the resolution. If this test
  // fails because the override stopped working, re-check @google-cloud/storage
  // compat before removing the override.
  it('fast-xml-parser resolved version is ≥ 5.7.0', () => {
    const version = installedVersion('fast-xml-parser');
    expect(
      semverGte(version, '5.7.0'),
      `fast-xml-parser ${version} < 5.7.0`,
    ).to.be.true;
  });
});
