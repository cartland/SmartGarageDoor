/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { RuntimeOptions } from 'firebase-functions/v1';

/**
 * Shared runtime caps for every HTTP Cloud Function in this project.
 *
 * Security audit reference: H1 (no body-size limit, maxInstances, or
 * timeout configured anywhere — DoS / billing-amplification surface).
 *
 * Conservative values chosen so legitimate traffic is never throttled:
 *
 *   maxInstances: 50
 *     Default is 3000 — effectively unbounded for billing purposes. This
 *     app has one ESP32 device polling every ~5 s, one Android user
 *     making rare door-control calls, and rare maintenance calls.
 *     Realistic concurrent load is single-digit; 50 is ~10× headroom.
 *
 *   timeoutSeconds: 60
 *     v1 default. Each handler's actual work is a few Firestore reads /
 *     writes (sub-second). 60 s is generous; long enough to ride out
 *     Firestore cold-start latency.
 *
 *   memory: '256MB'
 *     v1 default. Handlers are stateless and call Firestore SDK + send
 *     a response; no heavy allocations. Bump per-function only if
 *     Cloud Logging shows OOM signals.
 *
 * If a future change adds a handler that legitimately exceeds these
 * (e.g. a long-running batch endpoint), declare its own RuntimeOptions
 * at the call site rather than relaxing the shared default.
 */
export const HTTP_RUNTIME_OPTS: RuntimeOptions = {
  maxInstances: 50,
  timeoutSeconds: 60,
  memory: '256MB',
};
