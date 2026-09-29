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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The static shortcuts are XML, so no compiler checks that they still point
 * at a [LaunchTarget] — and a shortcut is how the launcher could grow a
 * button with no Kotlin changing. This reads the resource and pins every
 * entry to the one shape that can only look: MainActivity, the per-variant
 * package, and exactly one extra naming a declared target.
 */
class ShortcutsResourceTest {
    @Test
    fun everyShortcutIsAPlaceToLook() {
        val blocks = SHORTCUT_BLOCK.findAll(shortcutsXml()).map { it.groupValues[1] }.toList()
        assertTrue("no <shortcut> blocks found — the parser has gone blind", blocks.isNotEmpty())
        blocks.forEach { block ->
            assertEquals("shortcut block:\n$block", emptyList<String>(), problemsIn(block))
        }
    }

    @Test
    fun theManifestPointsAtTheResource() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("missing ${manifest.absolutePath}", manifest.exists())
        val activity = manifest
            .readText()
            .substringAfter("android:name=\".MainActivity\"", missingDelimiterValue = "")
            .substringBefore("</activity>")
        assertTrue("MainActivity is not declared in the manifest, or was renamed", activity.isNotEmpty())
        assertTrue(
            "MainActivity must carry the android.app.shortcuts meta-data, or the resource is never read",
            activity.contains("android:name=\"android.app.shortcuts\"") &&
                activity.contains("android:resource=\"@xml/shortcuts\""),
        )
    }

    @Test
    fun theTargetPackageIsSuppliedPerVariant() {
        // res/xml cannot see manifest placeholders, so build.gradle.kts hands
        // the variant's applicationId over as a string resource. Without it
        // the debug build's shortcuts would target the release package.
        val gradle = File("build.gradle.kts")
        assertTrue("missing ${gradle.absolutePath}", gradle.exists())
        assertTrue(
            "build.gradle.kts no longer declares the shortcut_target_package resValue",
            gradle.readText().contains("\"shortcut_target_package\""),
        )
    }

    @Test
    fun theseChecksCanActuallyFail() {
        // Positive control: a shortcut that presses must be rejected, and for
        // more than one reason, or the predicate is measuring nothing.
        val problems = problemsIn(PRESSING_SHORTCUT)
        assertTrue("a pressing shortcut passed: $problems", problems.size >= 2)
    }

    private fun shortcutsXml(): String {
        val file = File("src/main/res/xml/shortcuts.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private fun problemsIn(block: String): List<String> {
        val problems = mutableListOf<String>()
        val targetClass = attr(block, "targetClass")
        if (targetClass != "com.chriscartland.garage.MainActivity") {
            problems += "targetClass must be MainActivity, was $targetClass"
        }
        val targetPackage = attr(block, "targetPackage")
        if (targetPackage != "@string/shortcut_target_package") {
            problems += "targetPackage must be the per-variant resValue, was $targetPackage"
        }
        val extras = EXTRA.findAll(block).map { it.groupValues[1] to it.groupValues[2] }.toList()
        if (extras.size != 1) {
            problems += "exactly one extra (the LaunchTarget) is allowed, found $extras"
        }
        extras.forEach { (name, value) ->
            if (name != LaunchTarget.EXTRA) {
                problems += "unknown extra $name"
            }
            if (LaunchTarget.from(value) == LaunchTarget.HOME) {
                problems += "'$value' is not a declared target (HOME needs no shortcut; the icon is one)"
            }
        }
        return problems
    }

    private fun attr(
        block: String,
        name: String,
    ): String? = Regex("""android:$name="([^"]+)"""").find(block)?.groupValues?.get(1)

    private companion object {
        val SHORTCUT_BLOCK = Regex("""<shortcut\b(.*?)</shortcut>""", RegexOption.DOT_MATCHES_ALL)
        val EXTRA = Regex("""<extra\s+android:name="([^"]+)"\s+android:value="([^"]+)"""")

        val PRESSING_SHORTCUT =
            """
            android:shortcutId="press">
            <intent
                android:action="android.intent.action.VIEW"
                android:targetClass="com.chriscartland.garage.PressActivity"
                android:targetPackage="com.chriscartland.garage">
                <extra
                    android:name="com.chriscartland.garage.LAUNCH_TARGET"
                    android:value="press" />
                <extra
                    android:name="com.chriscartland.garage.CONFIRMED"
                    android:value="true" />
            </intent>
            """.trimIndent()
    }
}
