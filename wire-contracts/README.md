# Wire Contracts

This directory holds **canonical JSON fixtures** that pin the over-the-wire shape
of every HTTP endpoint shared between the Firebase server and the Android client.

The repo's other tests (server-side Mocha, Android-side `commonTest`) verify
their own halves of the network round-trip — but neither side actually agrees on
the bytes that travel between them. This directory closes that gap by giving
both sides the same JSON files to test against.

## How to add a new endpoint

1. Pick a slug for the endpoint, e.g. `functionListAccess`. This must match the
   route name on the server.
2. Create a directory at `wire-contracts/<slug>/`.
3. Inside, add one `<scenario>.json` per response shape worth pinning. Suggested
   naming: `response_<descriptor>.json` for happy paths, `response_<error>.json`
   for documented error shapes. Keep one fixture per file — do not pile multiple
   shapes into one document.

## Server-side usage (TypeScript / Mocha)

In `FirebaseServer/test/functions/http/<EndpointName>Test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const FIXTURE_DIR = path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'functionListAccess');
const ENABLED_TRUE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_enabled_true.json'), 'utf8'),
);
expect(result.data).to.deep.equal(ENABLED_TRUE_FIXTURE);
```

A handler-output rename (`enabled` → `allowed`) breaks this assertion — either
because the fixture got updated (so the server test pins the new key) or
because it didn't (so the test fails outright).

## Android-side usage (Kotlin / Ktor MockEngine)

In `AndroidGarage/data/src/commonTest/kotlin/.../<EndpointName>DataSourceTest.kt`:

```kotlin
import kotlinx.serialization.json.Json

// Use STRICT decoding in tests so unknown / renamed keys fail loudly.
// Production keeps `ignoreUnknownKeys = true` for forward-compat on field
// additions — but tests deserialize the fixture in strict mode so a rename
// cannot slip through.
private val strictJson = Json { ignoreUnknownKeys = false }

@Test
fun decodesFixture() {
    val fixture = readFixture("functionListAccess/response_enabled_true.json")
    val parsed = strictJson.decodeFromString<KtorFunctionListAccessResponse>(fixture)
    assertEquals(true, parsed.enabled)
}
```

Both sides loading the same `.json` files makes wire-shape disagreement a
test failure on at least one side.

## Why JSON fixtures rather than OpenAPI / Protobuf

For a single-developer repo with two stacks (TS server + KMP Android client) and
a tiny endpoint surface, hand-written JSON fixtures are the cheapest mechanism
that gives meaningful drift protection. OpenAPI + codegen has higher
maintenance overhead and weaker support for `commonMain` Kotlin types; protobuf
is overkill for a JSON-over-HTTPS API.

If the endpoint surface grows past ~10 endpoints or a second developer joins,
revisit and consider promoting this directory to a real schema with codegen.

## What this directory does NOT lock

- **HTTP method, route, headers** — the fixture only constrains the response
  body bytes. Method/route/header drift still relies on the data-source test
  capturing the request shape via `MockEngine`.
- **Status codes** — covered by the per-side tests, not by the fixture.
- **Server side-effects** — covered by the per-side tests.
- **Endpoint deprecation** — when a slug stops being served (e.g. it's been
  superseded by a bulk endpoint), delete the slug's directory in the same PR
  that removes the route. Keep both during the deprecation window so old
  clients still have a fixture to test against.

## Error responses

The naming convention for error fixtures is `response_<error>.json` (e.g.
`response_unauthorized.json`, `response_forbidden.json`). The fixture should
contain only the **body** bytes — the HTTP status code is asserted in the
per-side test, not in the fixture file. This keeps fixtures readable and
keeps "is this 401 or 403?" out of the byte-shape contract.

## File format

- One JSON document per file.
- Plain ASCII; no trailing newline beyond what the editor inserts.
- Sorted keys (matters for byte-exact comparisons if the test ever checks
  `JSON.stringify(out) === fs.readFileSync(...)` — today's tests use
  `deep.equal` which is structural, not byte-exact).
