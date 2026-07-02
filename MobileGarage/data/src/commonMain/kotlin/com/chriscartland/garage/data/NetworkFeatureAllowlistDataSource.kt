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

package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.FeatureAllowlist

/**
 * Data source for fetching the per-user feature allowlist. Takes a
 * Firebase ID token; returns whatever the server says (deny-all on
 * missing config or non-allowlisted email arrives as `false` flags,
 * never as an error).
 */
interface NetworkFeatureAllowlistDataSource {
    suspend fun fetchAllowlist(idToken: String): NetworkResult<FeatureAllowlist>
}
