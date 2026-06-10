package com.chriscartland.garage.domain.model

/**
 * In-memory pagination cursor for the recent-events list (ADR-022 — state-y).
 *
 * Owned by the door repository (a `@Singleton`) and exposed as a [StateFlow] so
 * the history screen can drive "load more". Never persisted — on cold start the
 * first-page fetch re-establishes it.
 *
 * - [nextPageToken] opaque cursor for the next OLDER page (null = at the oldest)
 * - [canLoadMore] true once a page reported more older events are available
 * - [isLoadingMore] an older-page fetch is in flight (single source of truth +
 *   reentrancy guard against double-fetch on a scroll fling)
 */
data class PaginationState(
    val nextPageToken: String? = null,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
) {
    companion object {
        val Initial = PaginationState()
    }
}
