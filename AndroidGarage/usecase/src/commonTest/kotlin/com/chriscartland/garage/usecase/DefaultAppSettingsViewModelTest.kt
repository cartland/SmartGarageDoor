package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import com.chriscartland.garage.testcommon.FakeFeatureAllowlistRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settings = FakeAppSettingsRepository()
    private val featureAllowlistRepository = FakeFeatureAllowlistRepository()

    private fun createViewModel(): DefaultAppSettingsViewModel =
        DefaultAppSettingsViewModel(
            settings = AppSettingsUseCase(settings),
            observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
        )

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
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("", viewModel.fcmDoorTopic.value)
            assertEquals(true, viewModel.profileUserCardExpanded.value)
            assertEquals(false, viewModel.profileLogCardExpanded.value)
            assertEquals(true, viewModel.profileAppCardExpanded.value)
        }

    @Test
    fun setFcmDoorTopicUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setFcmDoorTopic("new-topic")
            advanceUntilIdle()

            assertEquals("new-topic", viewModel.fcmDoorTopic.value)
            assertEquals("new-topic", settings.fcmDoorTopic.flow.first())
        }

    @Test
    fun setProfileUserCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setProfileUserCardExpanded(false)
            advanceUntilIdle()

            assertEquals(false, viewModel.profileUserCardExpanded.value)
            assertEquals(false, settings.profileUserCardExpanded.flow.first())
        }

    @Test
    fun setProfileLogCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setProfileLogCardExpanded(true)
            advanceUntilIdle()

            assertEquals(true, viewModel.profileLogCardExpanded.value)
            assertEquals(true, settings.profileLogCardExpanded.flow.first())
        }

    @Test
    fun setProfileAppCardExpandedUpdatesFlowAndSetting() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
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

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals("pre-existing", vm.fcmDoorTopic.value)
            assertEquals(true, vm.profileLogCardExpanded.value)
        }

    @Test
    fun functionListAccessIsNullBeforeAllowlistResolves() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertNull(viewModel.functionListAccess.value)
        }

    @Test
    fun functionListAccessReflectsAllowlistTrue() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = true))
            advanceUntilIdle()
            assertEquals(true, viewModel.functionListAccess.value)
        }

    @Test
    fun functionListAccessReflectsAllowlistFalse() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = false))
            advanceUntilIdle()
            // Distinguish "false" from "null" — both deny but the tri-state
            // is load-bearing per docs/FEATURE_FLAGS.md.
            assertEquals(false, viewModel.functionListAccess.value)
        }
}
