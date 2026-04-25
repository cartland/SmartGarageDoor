---
category: reference
status: active
last_verified: 2026-04-24
---

# Firebase Handler Pattern

Canonical shape for HTTP and pub/sub handlers in `FirebaseServer/src/functions/`. Every existing handler follows this pattern after the H1–H6 extraction work that shipped in `server/18` (see `docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md` for the historical rollout).

## The shape

Split each handler into two pieces:

1. **A pure `handle<Action>(input)` function** — takes plain values, returns `HandlerResult<T>` (or a fixed shape). Testable with the existing fakes; no `functions.config()`, `Date.now()`, or framework globals inside.

2. **A thin wrapper** — `functions.https.onRequest(...)` or `functions.pubsub.schedule(...)` that reads framework globals, calls the pure core, and translates the result into the response.

```typescript
// Pure core — testable
export async function handleEchoRequest(input: {
  query: Record<string, unknown>;
  body: unknown;
  buildTimestamp: string;
  configSecret: string | null;
  authHeader: string | undefined;
}): Promise<HandlerResult<EchoResponse>> {
  // Pure logic. Returns ok(...) or err(401, {...}).
}

// Wrapper — reads externals, calls the pure core
export const httpEcho = functions.https.onRequest(async (request, response) => {
  const result = await handleEchoRequest({
    query: request.query,
    body: request.body,
    buildTimestamp: requireBuildTimestamp(getBuildTimestamp(config), 'httpEcho'),
    configSecret: getServerConfigSecret(config),
    authHeader: request.get('X-ServerConfigKey'),
  });
  if (result.kind === 'ok') {
    response.json(result.data);
  } else {
    response.status(result.status).send(result.body);
  }
});
```

## Key conventions

### `HandlerResult<T>` for multi-status handlers

Defined in `FirebaseServer/src/functions/HandlerResult.ts`:

```typescript
export type HandlerResult<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'err'; status: number; body: unknown };

export const ok = <T>(data: T): HandlerResult<T> => ({ kind: 'ok', data });
export const err = (status: number, body: unknown): HandlerResult<never> =>
  ({ kind: 'err', status, body });
```

Single-status handlers can return a plain shape; multi-status (auth, validation, rate-limit) handlers should use `HandlerResult` so every status code in the response is visible at the top level of the function.

### Inject externals via the wrapper

When a handler reads `functions.config()`, `Date.now()`, or any framework global, the **wrapper** reads it and passes a plain value to the pure core. The pure core never reads framework globals directly. This is what makes the core testable — fakes don't need to stub framework calls.

Examples:
- `httpServerConfig` — wrapper calls `readServerConfigSecret()`, passes the secret string into `handleServerConfig({secret, ...})`.
- `pubsubDataRetentionPolicy` — wrapper passes `Date.now()` as a `nowMillis?: number` parameter so tests can fix the time.
- `httpAddRemoteButtonCommand` — wrapper calls `verifyIdToken` via the `AuthService` singleton, passes the decoded email; the core just compares against the authorized list.

### Service bridges, not direct SDK calls

Side-effecting calls (Firestore writes, FCM sends, `verifyIdToken`) go through swappable singletons in `FirebaseServer/src/controller/` or `src/database/`:

```typescript
// Production
const SERVICE: AuthService = { verifyIdToken: (token) => _instance.verifyIdToken(token) };

// Tests
import { setImpl, resetImpl } from '../controller/AuthService';
beforeEach(() => setImpl(new FakeAuthService()));
afterEach(resetImpl);
```

This is the same pattern the database modules use (`setImpl`/`resetImpl` for `SensorEventDatabase`, `SnoozeNotificationsDatabase`, etc. — see `docs/FIREBASE_DATABASE_REFACTOR.md` for the full inventory).

### Test-helper `setupAuthHappyPath`

For the three auth-heavy handlers, `test/helpers/AuthTestHelper.ts#setupAuthHappyPath` seeds config + fake-auth in one call. Use it instead of repeating the boilerplate.

## Preserved quirks (read these before refactoring)

The H1–H6 extraction deliberately preserved three behaviors that look wrong but were the pre-extraction behavior. Tests pin them. Don't "fix" without explicit decision:

1. **`httpAddRemoteButtonCommand`'s missing-buildTimestamp branch still writes `save(undefined, data)`** because the pre-extraction `response.status(400).send(...)` had no `return`.
2. **`httpAddRemoteButtonCommand`'s `verifyIdToken` throw propagates to 500** while `httpSnoozeNotificationsRequest` catches and returns 401 — asymmetry retained.
3. **`httpRemoteButton`'s asymmetric "return fresh-read on clear, return pre-save on else" split** is retained.

Each is documented as a comment near the relevant code.

## When NOT to use this pattern

- **Firestore triggers** (`onWrite`, `onCreate`) — already thin, three-line wrappers over `controller/*` functions. `firestoreUpdateEvents` is the example. The pure logic is already in the controller; the trigger wrapper is just `change.after.data() → updateEvent(data, false)`.
- **One-off scripts** that aren't deployed as functions.

## References

- `FirebaseServer/src/functions/HandlerResult.ts` — type definition
- `FirebaseServer/src/controller/AuthService.ts` — service bridge example
- `docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md` — historical rollout plan with phase-by-phase shipping notes
- `FirebaseServer/CHANGELOG.md` `server/18` — release notes covering all 14 handlers
