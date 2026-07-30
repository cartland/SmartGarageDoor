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

package com.chriscartland.garage.presentation

/**
 * One line of the notification-permission justification banner.
 *
 * The banner escalates: it opens with the reason, and as the user keeps
 * declining it adds progressively more pointed explanations. Each arm is one
 * line; the platform supplies the sentence.
 */
enum class PermissionNagLine {
    /** Why the app wants notifications at all. Always present. */
    BASE,

    /** Points at the OS settings app, where the decision can be reversed. */
    MENTION_SETTINGS,

    /** Explains that the OS itself may now be suppressing the prompt. */
    MENTION_REPEATED_DENIAL,

    /**
     * States how many times the button has been tapped. The platform
     * interpolates the count it already holds on the alert.
     */
    ATTEMPT_COUNT,
}

/**
 * Decides how far the permission banner escalates for a given attempt count.
 *
 * The thresholds are a product decision about how hard to push, and they were
 * written out twice — `attempt > 2 / > 3 / > 4` on Android, `attemptCount > 2 /
 * > 3 / > 4` on iOS. The two happened to agree, but each was free to drift, and
 * "how insistent is this app about notifications" is exactly the kind of thing
 * that should not differ by platform.
 *
 * Cumulative by construction: every level includes the lines below it, so the
 * banner only ever grows as the user keeps declining. Returning the lines
 * themselves rather than a level ordinal keeps the platform's job purely
 * mechanical — map each arm to a sentence and join.
 */
object PermissionNagMapper {
    /** Lines to show, in display order, for [attemptCount] taps so far. */
    fun linesFor(attemptCount: Int): List<PermissionNagLine> =
        buildList {
            add(PermissionNagLine.BASE)
            if (attemptCount > MENTION_SETTINGS_AFTER) add(PermissionNagLine.MENTION_SETTINGS)
            if (attemptCount > MENTION_REPEATED_DENIAL_AFTER) add(PermissionNagLine.MENTION_REPEATED_DENIAL)
            if (attemptCount > ATTEMPT_COUNT_AFTER) add(PermissionNagLine.ATTEMPT_COUNT)
        }

    private const val MENTION_SETTINGS_AFTER = 2
    private const val MENTION_REPEATED_DENIAL_AFTER = 3
    private const val ATTEMPT_COUNT_AFTER = 4
}
