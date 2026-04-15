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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.coroutines.AppClock

/**
 * Test clock with controllable time. Advance with [advanceSeconds].
 *
 * Not thread-safe — use only with single-threaded test dispatchers
 * (e.g., [kotlinx.coroutines.test.StandardTestDispatcher]).
 */
class FakeClock(
    private var nowSeconds: Long = 0L,
) : AppClock {
    override fun nowEpochSeconds(): Long = nowSeconds

    fun advanceSeconds(seconds: Long) {
        nowSeconds += seconds
    }
}
