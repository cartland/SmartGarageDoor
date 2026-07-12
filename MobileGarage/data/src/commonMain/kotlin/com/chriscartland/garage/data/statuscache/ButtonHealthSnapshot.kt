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

import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import kotlinx.serialization.Serializable

/**
 * Persisted shape of the last-known [ButtonHealth] verdict
 * (STATUS_CACHE_PLAN.md D2). `:domain` stays annotation-free, so the
 * domain model gets a parallel `@Serializable` DTO here. The state is
 * stored by enum NAME; an unrecognized stored name decodes to
 * [ButtonHealthState.UNKNOWN] (same forward-compat posture as the wire
 * decoder) instead of throwing away the whole snapshot.
 */
@Serializable
data class ButtonHealthSnapshotDto(
    val state: String,
    val stateChangedAtSeconds: Long? = null,
    val lastPollAtSeconds: Long? = null,
) {
    fun toDomain(): ButtonHealth =
        ButtonHealth(
            state = ButtonHealthState.entries.firstOrNull { it.name == state } ?: ButtonHealthState.UNKNOWN,
            stateChangedAtSeconds = stateChangedAtSeconds,
            lastPollAtSeconds = lastPollAtSeconds,
        )

    companion object {
        fun fromDomain(health: ButtonHealth): ButtonHealthSnapshotDto =
            ButtonHealthSnapshotDto(
                state = health.state.name,
                stateChangedAtSeconds = health.stateChangedAtSeconds,
                lastPollAtSeconds = health.lastPollAtSeconds,
            )
    }
}

/** Cache identity + versioning for the button-health snapshot. */
object ButtonHealthSnapshot {
    val KEY = StatusCacheKey("buttonHealth")

    /** Bump to deliberately invalidate all persisted button-health entries. */
    const val SCHEMA_VERSION: Int = 1
}
