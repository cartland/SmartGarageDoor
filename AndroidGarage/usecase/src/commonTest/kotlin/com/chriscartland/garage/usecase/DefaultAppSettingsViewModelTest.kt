package com.chriscartland.garage.usecase

import com.chriscartland.garage.usecase.testfakes.FakeAppSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultAppSettingsViewModelTest {
    private val settings = FakeAppSettingsRepository()
    private val viewModel = DefaultAppSettingsViewModel(settings)

    @Test
    fun initialStateReflectsSettings() {
        assertEquals("", viewModel.fcmDoorTopic.value)
        assertEquals(true, viewModel.profileUserCardExpanded.value)
        assertEquals(false, viewModel.profileLogCardExpanded.value)
        assertEquals(true, viewModel.profileAppCardExpanded.value)
    }

    @Test
    fun setFcmDoorTopicUpdatesFlowAndSetting() {
        viewModel.setFcmDoorTopic("new-topic")
        assertEquals("new-topic", viewModel.fcmDoorTopic.value)
        assertEquals("new-topic", settings.fcmDoorTopic.get())
    }

    @Test
    fun setProfileUserCardExpandedUpdatesFlowAndSetting() {
        viewModel.setProfileUserCardExpanded(false)
        assertEquals(false, viewModel.profileUserCardExpanded.value)
        assertEquals(false, settings.profileUserCardExpanded.get())
    }

    @Test
    fun setProfileLogCardExpandedUpdatesFlowAndSetting() {
        viewModel.setProfileLogCardExpanded(true)
        assertEquals(true, viewModel.profileLogCardExpanded.value)
        assertEquals(true, settings.profileLogCardExpanded.get())
    }

    @Test
    fun setProfileAppCardExpandedUpdatesFlowAndSetting() {
        viewModel.setProfileAppCardExpanded(false)
        assertEquals(false, viewModel.profileAppCardExpanded.value)
        assertEquals(false, settings.profileAppCardExpanded.get())
    }

    @Test
    fun initialStateReadsFromExistingSettings() {
        settings.fcmDoorTopic.set("pre-existing")
        settings.profileLogCardExpanded.set(true)
        val vm = DefaultAppSettingsViewModel(settings)
        assertEquals("pre-existing", vm.fcmDoorTopic.value)
        assertEquals(true, vm.profileLogCardExpanded.value)
    }
}
