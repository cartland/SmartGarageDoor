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
 * Shared return type for extracted HTTP-handler cores that produce
 * multiple status codes — see docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * The pure `handle<Action>(input)` function returns a HandlerResult
 * instead of touching the express Response object directly. The thin
 * wrapper maps:
 *
 *   { kind: 'ok', data }          → response.status(200).send(data)
 *   { kind: 'error', status, body } → response.status(status).send(body)
 *
 * Handlers that only produce 200-or-500 (the "try/catch and rethrow"
 * shape) don't need this type — they can just `Promise<T>` and let
 * exceptions bubble to the wrapper's catch. Use HandlerResult when
 * the handler has validation/auth branches with distinct 4xx codes.
 */
export type HandlerResult<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'error'; status: number; body: unknown };

export function ok<T>(data: T): HandlerResult<T> {
  return { kind: 'ok', data };
}

export function err(status: number, body: unknown): HandlerResult<never> {
  return { kind: 'error', status, body };
}
