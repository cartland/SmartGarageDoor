package com.chriscartland.garage.domain.model

/**
 * A page of door history events plus the opaque pagination tokens that bound it.
 *
 * Tokens are server state — the client treats them as opaque strings and passes
 * them back to fetch adjacent pages. [nextPageToken] pages OLDER (into the past),
 * [prevPageToken] pages NEWER (toward the present); a null token means there is
 * nothing more in that direction. [hasMore] mirrors `nextPageToken != null` (the
 * older direction the history UI consumes).
 */
data class DoorEventPage(
    val events: List<DoorEvent>,
    val nextPageToken: String?,
    val prevPageToken: String?,
    val hasMore: Boolean,
)
