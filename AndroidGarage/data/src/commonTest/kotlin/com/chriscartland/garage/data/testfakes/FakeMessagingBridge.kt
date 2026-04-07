package com.chriscartland.garage.data.testfakes

import com.chriscartland.garage.data.MessagingBridge

class FakeMessagingBridge : MessagingBridge {
    var subscribeResult = true
    var token: String? = "fake-token"
    val subscribedTopics = mutableListOf<String>()
    val unsubscribedTopics = mutableListOf<String>()

    override suspend fun subscribeToTopic(topic: String): Boolean {
        subscribedTopics.add(topic)
        return subscribeResult
    }

    override suspend fun unsubscribeFromTopic(topic: String) {
        unsubscribedTopics.add(topic)
    }

    override suspend fun getToken(): String? = token
}
