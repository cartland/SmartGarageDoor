package com.chriscartland.garage.usecase.testfakes

import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.Setting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppSettingsRepository : AppSettingsRepository {
    override val fcmDoorTopic: Setting<String> = InMemorySetting("")
    override val profileAppCardExpanded: Setting<Boolean> = InMemorySetting(true)
    override val profileLogCardExpanded: Setting<Boolean> = InMemorySetting(false)
    override val profileUserCardExpanded: Setting<Boolean> = InMemorySetting(true)
}

class InMemorySetting<T>(
    private val default: T,
) : Setting<T> {
    override val key: String = "fake"
    private val _flow = MutableStateFlow(default)
    override val flow: Flow<T> = _flow

    override suspend fun set(value: T) {
        _flow.value = value
    }

    override suspend fun restoreDefault() {
        _flow.value = default
    }
}
