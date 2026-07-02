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

package com.chriscartland.garage.data

/**
 * Android-side mirror of the server's
 * `FirebaseServer/src/model/ButtonHealthFcmTopic.ts` builder.
 *
 * Both sides MUST produce the same topic string for the same input.
 * Drift is caught by `ButtonHealthFcmTopicTest` running on both sides
 * with identical input/output pairs, per CLAUDE.md FCM safety rule.
 *
 * Distinct from the door's `String.toFcmTopic()` because the button
 * `buildTimestamp` is stored URL-encoded in server config (since
 * April 2021); the decode step + try/catch fallback handle that shape.
 *
 * Allowed FCM topic chars: `[a-zA-Z0-9-_.~%]`. Replacement char `.`
 * matches the door builder.
 */
object ButtonHealthFcmTopic {
    const val PREFIX: String = "buttonHealth-"

    fun fromBuildTimestamp(buildTimestamp: String): String {
        val decoded = try {
            decodePercent(buildTimestamp)
        } catch (_: Throwable) {
            buildTimestamp
        }
        require(decoded.isNotEmpty()) { "buttonHealth topic: empty buildTimestamp" }
        val sanitized = ALLOWED_TOPIC_CHARS_REGEX.replace(decoded, ".")
        return "$PREFIX$sanitized"
    }

    private val ALLOWED_TOPIC_CHARS_REGEX = Regex("[^a-zA-Z0-9\\-_.~%]")

    /**
     * decodeURIComponent equivalent. Throws on malformed `%XX` sequences,
     * which the caller catches and falls back to the raw string.
     */
    private fun decodePercent(input: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%') {
                require(i + 2 < input.length) { "Truncated % escape" }
                val hex = input.substring(i + 1, i + 3)
                val byte = hex.toInt(16) // throws NumberFormatException on bad hex
                out.append(byte.toChar())
                i += 3
            } else if (c == '+') {
                // decodeURIComponent keeps `+` as literal; do the same.
                out.append('+')
                i++
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
