package com.chriscartland.garage.usecase

import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settings = FakeAppSettingsRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateReflectsSettings() =
        runTest(testDispatcher) {
            val viewModel = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            assertEquals("", viewModel.fcmDoorTopic.value)
            assertEquals(true, viewModel.profileUserCardExpanded.value)
            assertEquals(false, viewModel.profileLogCardExpanded.value)
            assertEquals(true, viewModel.profileAppCardExpanded.value)
        }

    @Test
    fun setFcmDoorTopicUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            viewModel.setFcmDoorTopic("new-topic")
            advanceUntilIdle()

            assertEquals("new-topic", viewModel.fcmDoorTopic.value)
            assertEquals("new-topic", settings.fcmDoorTopic.flow.first())
        }

    @Test
    fun setProfileUserCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            viewModel.setProfileUserCardExpanded(false)
            advanceUntilIdle()

            assertEquals(false, viewModel.profileUserCardExpanded.value)
            assertEquals(false, settings.profileUserCardExpanded.flow.first())
        }

    @Test
    fun setProfileLogCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            viewModel.setProfileLogCardExpanded(true)
            advanceUntilIdle()

            assertEquals(true, viewModel.profileLogCardExpanded.value)
            assertEquals(true, settings.profileLogCardExpanded.flow.first())
        }

    @Test
    fun setProfileAppCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            viewModel.setProfileAppCardExpanded(false)
            advanceUntilIdle()

            assertEquals(false, viewModel.profileAppCardExpanded.value)
            assertEquals(false, settings.profileAppCardExpanded.flow.first())
        }

    @Test
    fun initialStateReadsFromExistingSettings() =
        runTest(testDispatcher) {
            settings.fcmDoorTopic.set("pre-existing")
            settings.profileLogCardExpanded.set(true)

            val vm = DefaultAppSettingsViewModel(AppSettingsUseCase(settings))
            advanceUntilIdle()

            assertEquals("pre-existing", vm.fcmDoorTopic.value)
            assertEquals(true, vm.profileLogCardExpanded.value)
        }
}
