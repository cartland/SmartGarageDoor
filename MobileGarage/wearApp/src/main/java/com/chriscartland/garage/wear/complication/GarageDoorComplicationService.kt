/*
 * Copyright 2026 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.wear.GarageWearApplication
import com.chriscartland.garage.wear.MainActivity
import com.chriscartland.garage.wear.R
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * The garage door as a watch-face **complication**.
 *
 * The most glanceable surface the app has: no swipe, no tap, just there on
 * the face. It is also the least forgiving — seven characters, and colours
 * chosen by the watch face rather than by us.
 *
 * **The one number it shows is rendered by the watch face, not by us.** How
 * long the door has been in its state goes out as a
 * [TimeDifferenceComplicationText] anchored to the instant the door changed,
 * so the face computes it at read time and it is correct whenever it is read.
 * This surface is redrawn as rarely as every ten minutes; a duration we
 * calculated and froze would be silently wrong by up to that much, and "8m"
 * read eighteen minutes later is a specific, confident, wrong statement about
 * exactly what the reader is trying to judge.
 *
 * **It shows how long the DOOR has been that way, not when WE last checked.**
 * Those are different questions and only one is interesting: "open for 8
 * minutes" is a fact about the garage; "we checked 8 minutes ago" is a fact
 * about our plumbing. The second is still computed — it is what decides
 * liveness — but it is not printed.
 *
 * **Liveness is a word, not a measurement.** `Live` is implied by there being
 * a running duration at all; `Stale` replaces it when the garage has gone
 * quiet or we could not reach the server. Two states cannot drift the way a
 * figure can.
 *
 * **Read-only, like the tile.** Tapping opens the app. There is no route from
 * here to the garage button, and for a stronger reason than on the tile: a
 * complication sits on the watch face itself, the surface a wrist brushes
 * against all day. `GarageDoorComplicationSafetyTest` asserts the absence.
 *
 * **It supports only the types that have room to qualify what they show** —
 * [ComplicationType.SHORT_TEXT] and [ComplicationType.LONG_TEXT]. Icon-only
 * and ranged-value slots are refused on purpose: a door glyph would look
 * identical whether the reading was current or a week old.
 *
 * **Unlike the tile, this one waits for the network.** A tile render is
 * user-initiated — somebody swiped to it and is watching — so it answers from
 * cache instantly and corrects itself after. Nobody is waiting on a
 * complication, and answering from a cache nothing refreshes would leave it
 * permanently `Stale`. The contract allows around twenty seconds, so this
 * spends a bounded slice of that ([REFRESH_BUDGET_MILLIS]) asking for
 * something current, then presents whatever it has either way.
 */
class GarageDoorComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val glance = (applicationContext as GarageWearApplication).component.wearGlanceStatus
        // Bounded: a watch face waiting on a Bluetooth relay must still get an
        // answer. A timeout is not a failure worth reporting — what we already
        // have, qualified as Stale, is the honest outcome either way.
        withTimeoutOrNull(REFRESH_BUDGET_MILLIS) { glance.refresh() }
        val status = glance.current().status
        return complicationData(request.complicationType, status)
    }

    /**
     * Static sample for the watch-face editor.
     *
     * Deliberately shows a LIVE door: the picker is where someone decides
     * whether to give this a slot, and a preview that looked alarmed would
     * misrepresent the normal case. Uses a plain string rather than a running
     * duration — preview data is cached by the system and is meant to be
     * constant, so a live one would be the one place a moving number is
     * genuinely wrong. Must not touch the network or the cache: this runs on a
     * background thread.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                shortText(
                    text = plain(getString(R.string.complication_door_closed)),
                    title = plain(getString(R.string.complication_preview_duration)),
                    description = getString(R.string.complication_preview_description),
                )
            ComplicationType.LONG_TEXT ->
                longText(
                    text = plain(
                        getString(
                            R.string.complication_long_text,
                            getString(R.string.complication_door_closed),
                            getString(R.string.complication_preview_duration),
                        ),
                    ),
                    title = plain(getString(R.string.tile_title)),
                    description = getString(R.string.complication_preview_description),
                )
            else -> null
        }

    private fun complicationData(
        type: ComplicationType,
        status: GlanceStatus,
    ): ComplicationData? {
        val door = GarageComplicationWords.doorWord(status.headline)?.let(::getString)
        val description = door ?: getString(GarageComplicationWords.NO_DATA)
        return when (type) {
            ComplicationType.SHORT_TEXT -> shortTextFor(status, door, description)
            ComplicationType.LONG_TEXT -> longTextFor(status, door, description)
            // Every other slot would have to show the door with no room to
            // qualify it. See the class KDoc: that is the one thing this must
            // never do.
            else -> null
        }
    }

    private fun shortTextFor(
        status: GlanceStatus,
        door: String?,
        description: String,
    ): ComplicationData {
        if (door == null) {
            return shortText(plain(getString(GarageComplicationWords.NO_DATA)), null, description)
        }
        if (GarageComplicationWords.staleLeads(status)) {
            // A text-only face must not be left showing an unqualified door.
            return shortText(plain(getString(GarageComplicationWords.STALE)), plain(door), description)
        }
        return shortText(plain(door), runningDuration(status), description)
    }

    private fun longTextFor(
        status: GlanceStatus,
        door: String?,
        description: String,
    ): ComplicationData {
        if (door == null) {
            return longText(
                plain(getString(GarageComplicationWords.NO_DATA)),
                plain(getString(R.string.tile_title)),
                description,
            )
        }
        if (GarageComplicationWords.staleLeads(status)) {
            return longText(
                plain(getString(R.string.complication_long_stale, door)),
                plain(getString(R.string.tile_title)),
                description,
            )
        }
        // Long text has room for the door and its running duration together.
        // `^1` is where the face substitutes the duration it renders.
        val body = runningDuration(status, template = getString(R.string.complication_long_text, door, "^1"))
            ?: plain(door)
        return longText(body, plain(getString(R.string.tile_title)), description)
    }

    /**
     * How long the door has been in its state, as text the WATCH FACE renders.
     *
     * Null when the shared verdict withheld the instant, which it does
     * whenever the reading cannot be vouched for — a duration asserts the door
     * has been this way continuously, and a door we have lost contact with may
     * have moved twice since.
     *
     * [TimeDifferenceStyle.SHORT_SINGLE_UNIT] is the seven-character-friendly
     * one: minutes under an hour, then hours, then days.
     */
    private fun runningDuration(
        status: GlanceStatus,
        template: String? = null,
    ): ComplicationText? {
        val since = status.stateSinceEpochSeconds ?: return null
        return TimeDifferenceComplicationText
            .Builder(
                style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                countUpTimeReference = CountUpTimeReference(Instant.ofEpochSecond(since)),
            ).apply { template?.let { setText(it) } }
            .build()
    }

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    private fun shortText(
        text: ComplicationText,
        title: ComplicationText?,
        description: String,
    ): ComplicationData =
        ShortTextComplicationData
            .Builder(text = text, contentDescription = plain(description))
            .apply {
                title?.let { setTitle(it) }
                setTapAction(openTheApp())
            }.build()

    private fun longText(
        text: ComplicationText,
        title: ComplicationText?,
        description: String,
    ): ComplicationData =
        LongTextComplicationData
            .Builder(text = text, contentDescription = plain(description))
            .apply {
                title?.let { setTitle(it) }
                setTapAction(openTheApp())
            }.build()

    /** Opens the app on the door screen. The complication's only action. */
    private fun openTheApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        /**
         * How long to wait for a newer reading before presenting what we have.
         *
         * Well inside the roughly twenty seconds the platform allows, and
         * generous enough for the watch's slowest path (a Bluetooth relay to
         * the phone). Spending the whole budget would risk the system
         * unbinding mid-answer, which shows the user nothing at all.
         */
        const val REFRESH_BUDGET_MILLIS = 8_000L
    }
}
