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

package com.chriscartland.garage.ui

/**
 * Where a launch lands, when something outside the app asked for a place in it.
 *
 * Static app shortcuts (`res/xml/shortcuts.xml`, the launcher's long-press
 * menu) are the only caller today. Each names a target by [id] in the launch
 * intent's [EXTRA]; `MainActivity` reads it once, in `onCreate`, and seeds the
 * back stack from [stack].
 *
 * READ-ONLY by construction: every target is a place to LOOK, and there is
 * deliberately no `PRESS` (strategy 3.4). A shortcut is a single tap on a menu
 * that opens from a long press, with nowhere to put the confirm gesture the
 * door button needs, so arming the door there would swap the strongest guard
 * in the app for the weakest gesture the launcher has. `LaunchTargetTest` pins
 * the set of targets to exactly these three, and `ShortcutsResourceTest` pins
 * every shortcut in the resource to one of them.
 *
 * Every stack starts at [Screen.Home]. `TabNavigation`'s model is "back from
 * a tab reveals Home", so a target that REPLACED Home as the root would exit
 * the app on the first back press — a shortcut that lands somewhere should
 * behave exactly as if the user had opened the app and tapped their way there.
 */
enum class LaunchTarget(
    val id: String,
) {
    HOME("home"),
    HISTORY("history"),
    SNOOZE("snooze"),
    ;

    /** The back stack to open on, root first. */
    val stack: List<Screen>
        get() =
            when (this) {
                HOME -> listOf(Screen.Home)
                HISTORY -> listOf(Screen.Home, Screen.History)
                SNOOZE -> listOf(Screen.Home, Screen.Profile)
            }

    /**
     * Whether the snooze sheet opens on arrival: the same hand-off Home's
     * "Snooze notifications" makes (strategy 2.4), started from outside.
     */
    val opensSnoozeSheet: Boolean
        get() = this == SNOOZE

    companion object {
        /** The launch intent extra carrying an [id]. */
        const val EXTRA = "com.chriscartland.garage.LAUNCH_TARGET"

        /**
         * An unknown or absent id is a plain launch, never an error. A shortcut
         * pinned to a home screen outlives the build that created it, so a
         * target this build no longer knows must still open the app.
         */
        fun from(id: String?): LaunchTarget = entries.firstOrNull { it.id == id } ?: HOME
    }
}
