package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.MessagingBridge

/**
 * Fake [MessagingBridge] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks calls via read-only lists
 * so tests cannot reset or reorder them mid-test (ADR-017 Rule 5 — call-list pattern).
 */
class FakeMessagingBridge : MessagingBridge {
    private var subscribeResult = true
    private var token: String? = "fake-token"

    private val _subscribedTopics = mutableListOf<String>()
    val subscribedTopics: List<String> get() = _subscribedTopics

    private val _unsubscribedTopics = mutableListOf<String>()
    val unsubscribedTopics: List<String> get() = _unsubscribedTopics

    fun setSubscribeResult(value: Boolean) {
        subscribeResult = value
    }

    fun setToken(value: String?) {
        token = value
    }

    override suspend fun subscribeToTopic(topic: String): Boolean {
        _subscribedTopics.add(topic)
        return subscribeResult
    }

    override suspend fun unsubscribeFromTopic(topic: String) {
        _unsubscribedTopics.add(topic)
    }

    override suspend fun getToken(): String? = token
}
