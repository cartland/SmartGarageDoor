package com.chriscartland.garage.data.testfakes

import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.Setting

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
    private var value: T = default

    override fun get(): T = value

    override fun set(value: T) {
        this.value = value
    }

    override fun restoreDefault() {
        value = default
    }
}
