/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.macrobenchmark

import android.graphics.Point
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollJankBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollJank() =
        benchmarkRule.measureRepeated(
            packageName = "com.chriscartland.garage.benchmark",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            setupBlock = {
                // https://issuetracker.google.com/issues/278214396
                // startupMode = StartupMode.COLD,
                // Setting the startupMode to COLD breaks the UI test.
                // To approximate "COLD" start,
                // we will kill the process ourselves, then launch the Activity.
                killProcess()
                startActivityAndWait()
                // Navigate to the History tab.
                val historyTab = device.findObject(By.textContains("History"))
                historyTab.click()
                device.waitForIdle()
            },
        ) {
            device.swipe(
                arrayOf(
                    Point(device.displayWidth / 2, device.displayHeight * 2 / 3),
                    Point(device.displayWidth / 2, device.displayHeight * 1 / 3),
                ),
                10,
            )
            device.waitForIdle(5000L)
        }
}
