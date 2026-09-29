package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.repository.RemoteButtonRepository

class FakeRemoteButtonRepository : RemoteButtonRepository {
    data class PushCall(
        val buttonAckToken: String,
    )

    private val _pushCalls = mutableListOf<PushCall>()
    val pushCalls: List<PushCall> get() = _pushCalls
    val pushCount: Int get() = _pushCalls.size

    private var pushError: ActionError? = null

    /** Set to false to simulate a network failure: every push fails with [ActionError.NetworkFailed]. */
    fun setPushSucceeds(value: Boolean) {
        pushError = if (value) null else ActionError.NetworkFailed
    }

    /** Every push fails with [error] until cleared with null; a 403 is [ActionError.Forbidden]. */
    fun setPushError(error: ActionError?) {
        pushError = error
    }

    override suspend fun pushButton(buttonAckToken: String): AppResult<Unit, ActionError> {
        _pushCalls.add(PushCall(buttonAckToken = buttonAckToken))
        return pushError?.let { AppResult.Error(it) } ?: AppResult.Success(Unit)
    }
}
