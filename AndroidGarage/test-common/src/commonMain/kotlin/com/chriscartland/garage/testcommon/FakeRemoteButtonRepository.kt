package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.yield

class FakeRemoteButtonRepository : RemoteButtonRepository {
    private val _pushButtonStatus = MutableStateFlow(PushStatus.IDLE)
    override val pushButtonStatus: StateFlow<PushStatus> = _pushButtonStatus

    var pushCount = 0
        private set
    var lastIdToken: String? = null
        private set

    fun setPushStatus(status: PushStatus) {
        _pushButtonStatus.value = status
    }

    override suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ) {
        pushCount++
        lastIdToken = idToken
        _pushButtonStatus.value = PushStatus.SENDING
        // Yield to let StateFlow collectors observe SENDING before it changes
        // to IDLE. Without this, StateFlow conflation can swallow SENDING
        // entirely — the collector never sees it, so the state machine never
        // starts the network timeout or transitions to SendingToDoor.
        // The real repository has an HTTP call here which naturally yields.
        yield()
        _pushButtonStatus.value = PushStatus.IDLE
    }
}
