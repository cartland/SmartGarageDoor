/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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
 *
 */

package com.chriscartland.garage.domain.model

/**
 * Per-user feature access decisions, fetched from the server after
 * sign-in. Each field is a single boolean for "is this user on the
 * allowlist for feature X?". The fields are deliberately not nullable —
 * a `false` from the server is a load-bearing answer (deny). The whole
 * value being `null` (in `StateFlow<FeatureAllowlist?>`) means "haven't
 * fetched yet" or "fetch failed", which both gate-closed by convention.
 *
 * The server is the security boundary; this struct is a UI hint.
 */
data class FeatureAllowlist(
    val functionList: Boolean,
)
