package org.fcitx.fcitx5.android.data.journal

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.time.LocalDate
import java.time.ZoneId

internal object JournalRules {
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long,Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun eligible(inputType: Int, imeOptions: Int): Boolean {
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        val type = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return type != InputType.TYPE_NULL && !(type == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) &&
            !(type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
}
