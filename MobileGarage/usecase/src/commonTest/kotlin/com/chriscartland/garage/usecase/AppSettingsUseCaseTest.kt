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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsUseCaseTest {
    @Test
    fun observeAndSetFcmDoorTopicRoundTrip() =
        runTest {
            val repo = FakeAppSettingsRepository()
            val useCase = AppSettingsUseCase(repo)

            useCase.setFcmDoorTopic("topic-123")

            assertEquals("topic-123", useCase.observeFcmDoorTopic().first())
        }

    @Test
    fun observeAndSetProfileUserCardExpandedRoundTrip() =
        runTest {
            val repo = FakeAppSettingsRepository()
            val useCase = AppSettingsUseCase(repo)

            useCase.setProfileUserCardExpanded(false)

            assertEquals(false, useCase.observeProfileUserCardExpanded().first())
        }

    @Test
    fun observeAndSetProfileLogCardExpandedRoundTrip() =
        runTest {
            val repo = FakeAppSettingsRepository()
            val useCase = AppSettingsUseCase(repo)

            useCase.setProfileLogCardExpanded(true)

            assertEquals(true, useCase.observeProfileLogCardExpanded().first())
        }

    @Test
    fun observeAndSetProfileAppCardExpandedRoundTrip() =
        runTest {
            val repo = FakeAppSettingsRepository()
            val useCase = AppSettingsUseCase(repo)

            useCase.setProfileAppCardExpanded(false)

            assertEquals(false, useCase.observeProfileAppCardExpanded().first())
        }
}
