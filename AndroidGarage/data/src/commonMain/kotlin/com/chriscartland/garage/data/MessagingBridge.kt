package com.chriscartland.garage.data

/**
 * Platform abstraction for push notification messaging.
 *
 * Decouples FCM operations from Firebase SDK, enabling:
 * - Unit testing with fakes
 * - Future iOS implementation with APNs
 */
interface MessagingBridge {
    /** Subscribe to a topic. Returns true on success. */
    suspend fun subscribeToTopic(topic: String): Boolean

    /** Unsubscribe from a topic. */
    suspend fun unsubscribeFromTopic(topic: String)

    /** Get the push notification registration token, or null on failure. */
    suspend fun getToken(): String?
}
