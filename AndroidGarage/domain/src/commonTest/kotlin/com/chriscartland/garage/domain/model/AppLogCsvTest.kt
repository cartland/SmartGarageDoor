package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the app-log CSV export format. Shared so Android (writes to a content
 * `Uri`) and iOS (shares via a share sheet) produce byte-identical files from
 * one source of truth ([AppLogCsv]).
 */
class AppLogCsvTest {
    @Test
    fun build_empty_isHeaderOnly() {
        assertEquals(AppLogCsv.HEADER, AppLogCsv.build(emptyList()))
    }

    @Test
    fun build_singleEvent_isHeaderPlusRow() {
        val event = AppLogEvent(eventKey = "fcm_received", timestampMillis = 1_700_000_000_000L, appVersion = "2.20.2")
        assertEquals(
            "Key,Epoch,Version\nfcm_received,1700000000000,2.20.2\n",
            AppLogCsv.build(listOf(event)),
        )
    }

    @Test
    fun build_multipleEvents_preservesOrder() {
        val events = listOf(
            AppLogEvent("a", 1L, "v1"),
            AppLogEvent("b", 2L, "v2"),
            AppLogEvent("c", 3L, "v3"),
        )
        assertEquals(
            AppLogCsv.HEADER + "a,1,v1\n" + "b,2,v2\n" + "c,3,v3\n",
            AppLogCsv.build(events),
        )
    }

    @Test
    fun build_isHeaderPlusConcatenatedRows() {
        // The full build must equal HEADER + each row in order — the contract
        // both platforms rely on for identical output.
        val events = listOf(AppLogEvent("k", 10L, "9.9.9"), AppLogEvent("k2", 20L, "9.9.9"))
        val expected = buildString {
            append(AppLogCsv.HEADER)
            events.forEach { append(it.toCsvRow()) }
        }
        assertEquals(expected, AppLogCsv.build(events))
    }
}
