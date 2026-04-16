package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.MessagingBridge

/**
 * Fake [MessagingBridge] for unit testing.
 *
 * Configure responses with `setX()` methods. ADR-017 Rule 5.
 */
class FakeMessagingBridge : MessagingBridge {
    private var subscribeResult = true
    private var token: String? = "fake-token"
    val subscribedTopics = mutableListOf<String>()
    val unsubscribedTopics = mutableListOf<String>()

    fun setSubscribeResult(value: Boolean) {
        subscribeResult = value
    }

    fun setToken(value: String?) {
        token = value
    }

    override suspend fun subscribeToTopic(topic: String): Boolean {
        subscribedTopics.add(topic)
        return subscribeResult
    }

    override suspend fun unsubscribeFromTopic(topic: String) {
        unsubscribedTopics.add(topic)
    }

    override suspend fun getToken(): String? = token
}
