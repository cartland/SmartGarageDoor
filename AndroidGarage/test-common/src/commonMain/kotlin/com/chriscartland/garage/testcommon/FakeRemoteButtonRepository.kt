package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.repository.RemoteButtonRepository

/**
 * Fake [RemoteButtonRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. ADR-017 Rule 5.
 */
class FakeRemoteButtonRepository : RemoteButtonRepository {
    var pushCount = 0
        private set
    var lastIdToken: String? = null
        private set

    /** Set to false to simulate network failure. */
    private var pushSucceeds = true

    fun setPushSucceeds(value: Boolean) {
        pushSucceeds = value
    }

    override suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ): Boolean {
        pushCount++
        lastIdToken = idToken
        return pushSucceeds
    }
}
