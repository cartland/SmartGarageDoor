/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.data.statuscache

import com.chriscartland.garage.domain.model.FeatureAllowlist
import kotlinx.serialization.Serializable

/**
 * Persisted shape of the per-user feature allowlist
 * (STATUS_CACHE_PLAN.md D4). The envelope's `accountEmail` MUST be set
 * on every write — hydration refuses a snapshot written by a different
 * account, which is the actual cross-account guard (the sign-out clear
 * is best-effort; see `SignOutCacheClearManager`).
 */
@Serializable
data class AllowlistSnapshotDto(
    val functionList: Boolean = false,
    val developer: Boolean = false,
) {
    fun toDomain(): FeatureAllowlist =
        FeatureAllowlist(
            functionList = functionList,
            developer = developer,
        )

    companion object {
        fun fromDomain(allowlist: FeatureAllowlist): AllowlistSnapshotDto =
            AllowlistSnapshotDto(
                functionList = allowlist.functionList,
                developer = allowlist.developer,
            )
    }
}

/** Cache identity + versioning for the allowlist snapshot. */
object AllowlistSnapshot {
    val KEY = StatusCacheKey("featureAllowlist")

    /** Bump to deliberately invalidate all persisted allowlist entries. */
    const val SCHEMA_VERSION: Int = 1
}
