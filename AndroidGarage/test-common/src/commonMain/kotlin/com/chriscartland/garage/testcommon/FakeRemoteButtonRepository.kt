package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.repository.RemoteButtonRepository

class FakeRemoteButtonRepository : RemoteButtonRepository {
    var pushCount = 0
        private set
    var lastIdToken: String? = null
        private set

    override suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ) {
        pushCount++
        lastIdToken = idToken
    }
}
