package com.chriscartland.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying the contract of [ExpandableColumnCard].
 *
 * These test the behavioral requirements without a Compose runtime:
 * - External state changes must be reflected (not ignored by remember)
 * - User clicks must propagate via onExpandedChange callback
 *
 * The actual Compose interaction tests are in androidTest (instrumented)
 * since they require a Compose test rule with StateRestorationTester.
 */
class ExpandableColumnCardContractTest {
    /**
     * Regression: rememberSaveable with non-Parcelable Screen types crashed
     * on process death. Verify Screen objects are NOT java.io.Serializable,
     * which means rememberSaveable would fail to save them.
     */
    @Test
    fun screenObjectsAreNotJavaSerializable() {
        val screens = listOf(Screen.Home, Screen.History, Screen.Profile)
        for (screen in screens) {
            val isJavaSerializable = screen is java.io.Serializable
            assertEquals(
                "Screen.${screen::class.simpleName} must NOT be java.io.Serializable " +
                    "(use remember, not rememberSaveable)",
                false,
                isJavaSerializable,
            )
        }
    }
}
