package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.journal.JournalRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JournalRulesTest {
    @Test fun excludesAllPasswordVariationsAndPrivateFields() {
        // Text password, visible password, web password, numeric PIN, null input.
        listOf(0x81,0x91,0xe1,0x12,0).forEach { assertFalse(JournalRules.eligible(it,0)) }
        assertFalse(JournalRules.eligible(1,0x1000000))
        assertTrue(JournalRules.eligible(1,0))
        assertTrue(JournalRules.eligible(0x20001,0)) // multiline text
        assertTrue(JournalRules.eligible(2,0)) // ordinary number
    }
    @Test fun localDaysFollowDaylightSavingAndMidnight() {
        val zone = ZoneId.of("America/New_York")
        val spring = JournalRules.dayBounds(LocalDate.of(2026,3,8),zone)
        val autumn = JournalRules.dayBounds(LocalDate.of(2026,11,1),zone)
        assertEquals(23L*3600000,spring.second-spring.first)
        assertEquals(25L*3600000,autumn.second-autumn.first)
        assertEquals(spring.second,JournalRules.dayBounds(LocalDate.of(2026,3,9),zone).first)
        val china = JournalRules.dayBounds(LocalDate.of(2026,11,19),ZoneId.of("Asia/Shanghai"))
        assertEquals(java.time.Instant.parse("2026-11-18T16:00:00Z").toEpochMilli(),china.first)
    }
}
