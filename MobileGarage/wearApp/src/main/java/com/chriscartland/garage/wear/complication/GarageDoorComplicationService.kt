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
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.wear.GarageWearApplication
import com.chriscartland.garage.wear.MainActivity
import com.chriscartland.garage.wear.R
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The garage door as a watch-face **complication**.
 *
 * The most glanceable surface the app has: no swipe, no tap, just there on
 * the face. It is also the least forgiving — seven characters, and colours
 * chosen by the watch face rather than by us.
 *
 * **Read-only, like the tile.** Tapping opens the app. There is no route from
 * here to the garage button, and for a stronger reason than on the tile: a
 * complication sits on the watch face itself, the surface a wrist brushes
 * against all day. `GarageDoorComplicationSafetyTest` asserts the absence.
 *
 * **It supports only the types that have room to say how old the reading is**
 * — [ComplicationType.SHORT_TEXT] and [ComplicationType.LONG_TEXT]. Icon-only
 * and ranged-value slots are refused on purpose. A door glyph that silently
 * means "open" is precisely the lie the rest of this work exists to remove:
 * it would look identical whether the reading arrived a minute ago or last
 * Tuesday. Offering nothing in those slots is the honest answer, and the user
 * simply will not see Garage listed for them.
 *
 * **Unlike the tile, this one waits for the network.** A tile render is
 * user-initiated — somebody swiped to it and is watching — so it answers from
 * cache instantly and corrects itself after. Nobody is waiting on a
 * complication; it is redrawn on the system's schedule, and answering from a
 * cache nothing ever refreshes would mean a face that reads "6h ago" forever.
 * The contract allows around twenty seconds, so this spends a bounded slice
 * of that ([REFRESH_BUDGET_MILLIS]) asking for something current, then
 * presents whatever it has either way.
 */
class GarageDoorComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val glance = (applicationContext as GarageWearApplication).component.wearGlanceStatus
        // Bounded: a watch face waiting on a Bluetooth relay must still get an
        // answer. A timeout is not a failure worth reporting — the reading we
        // already have is presented with its true age, which is the honest
        // outcome either way.
        withTimeoutOrNull(REFRESH_BUDGET_MILLIS) { glance.refresh() }
        val status = glance.current().status
        return complicationData(request.complicationType, status)
    }

    /**
     * Static sample for the watch-face editor.
     *
     * Deliberately shows a CONFIRMED door: the picker is where someone
     * decides whether to give this a slot, and a preview that looked alarmed
     * would misrepresent the normal case. Must not touch the network or the
     * cache — this runs on a background thread and the system caches whatever
     * it returns.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                shortText(
                    text = getString(R.string.complication_door_closed),
                    title = getString(R.string.complication_age_minutes_short, PREVIEW_AGE_MINUTES),
                    description = getString(R.string.complication_preview_description),
                )
            ComplicationType.LONG_TEXT ->
                longText(
                    text = getString(R.string.complication_door_closed),
                    title = getString(R.string.tile_title),
                    description = getString(R.string.complication_preview_description),
                )
            else -> null
        }

    private fun complicationData(
        type: ComplicationType,
        status: GlanceStatus,
    ): ComplicationData? {
        val door = GarageComplicationWords.doorWord(status.headline)?.let(::getString)
        val description = door
            ?: getString(R.string.complication_no_data)
        return when (type) {
            ComplicationType.SHORT_TEXT -> shortTextFor(status, door, description)
            ComplicationType.LONG_TEXT -> longTextFor(status, door, description)
            // Every other slot would have to show the door with no way to
            // date it. See the class KDoc: that is the one thing this must
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
            return shortText(getString(R.string.complication_no_data), null, description)
        }
        return if (GarageComplicationWords.ageLeads(status)) {
            // Not vouched for: the age leads, so a face that shows only the
            // main text still tells the truth.
            shortText(words(GarageComplicationWords.leadingAge(status.age)), door, description)
        } else {
            shortText(door, GarageComplicationWords.shortAge(status.age)?.let(::words), description)
        }
    }

    private fun longTextFor(
        status: GlanceStatus,
        door: String?,
        description: String,
    ): ComplicationData {
        if (door == null) {
            return longText(getString(R.string.complication_no_data), getString(R.string.tile_title), description)
        }
        // Long text has room for both at once, so nothing has to be demoted.
        val age = GarageComplicationWords.longAge(status.age)?.let(::words)
        val body = age?.let { getString(R.string.complication_long_text, door, it) } ?: door
        return longText(body, getString(R.string.tile_title), description)
    }

    private fun words(words: GarageComplicationWords.Words): String =
        words.quantity?.let { getString(words.resId, it) } ?: getString(words.resId)

    private fun shortText(
        text: String,
        title: String?,
        description: String,
    ): ComplicationData =
        ShortTextComplicationData
            .Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            ).apply {
                title?.let { setTitle(PlainComplicationText.Builder(it).build()) }
                setTapAction(openTheApp())
            }.build()

    private fun longText(
        text: String,
        title: String?,
        description: String,
    ): ComplicationData =
        LongTextComplicationData
            .Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            ).apply {
                title?.let { setTitle(PlainComplicationText.Builder(it).build()) }
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

        /** Minutes shown in the editor preview; see [getPreviewData]. */
        const val PREVIEW_AGE_MINUTES = 2
    }
}
