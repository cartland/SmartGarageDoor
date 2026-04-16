package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.repository.RemoteButtonRepository

/**
 * Fake [RemoteButtonRepository] for unit testing.
 *
 * Tracks each call via [pushCalls] (call-list pattern, ADR-017 Rule 5) so tests
 * can assert on the exact arguments passed, not just call counts. `pushCount` is
 * a convenience accessor backed by the call list.
 */
class FakeRemoteButtonRepository : RemoteButtonRepository {
    data class PushCall(
        val idToken: String,
        val buttonAckToken: String,
    )

    private val _pushCalls = mutableListOf<PushCall>()
    val pushCalls: List<PushCall> get() = _pushCalls
    val pushCount: Int get() = _pushCalls.size
    val lastIdToken: String? get() = _pushCalls.lastOrNull()?.idToken

    /** Set to false to simulate network failure. */
    private var pushSucceeds = true

    fun setPushSucceeds(value: Boolean) {
        pushSucceeds = value
    }

    override suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ): Boolean {
        _pushCalls.add(PushCall(idToken = idToken, buttonAckToken = buttonAckToken))
        return pushSucceeds
    }
}
