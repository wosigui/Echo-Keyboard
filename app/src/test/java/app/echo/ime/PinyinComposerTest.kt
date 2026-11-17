package app.echo.ime

import app.echo.pinyin.PinyinCandidate
import org.junit.Assert.*
import org.junit.Test
class PinyinComposerTest {
    @Test fun partialSelectionRetainsRemainingSpelling() {
        val composer = PinyinComposer()
        "nihaoshijie".forEach { composer.append(it) }
        composer.consume(5)
        assertEquals("shijie", composer.text)
        composer.delete()
        assertEquals("shiji", composer.text)
    }
    @Test fun nineKeyAndBounds() {
        val composer = PinyinComposer()
        "64426".forEach { composer.append(it, true) }
        assertEquals("64426", composer.text)
        composer.consume(2)
        assertEquals("426", composer.text)
        composer.clear()
        "1A!".forEach { composer.append(it) }
        assertEquals("", composer.text)
        repeat(100) { composer.append('a') }
        assertEquals(32, composer.text.length)
        composer.clear(); composer.delete()
        assertEquals("", composer.text)
    }
    @Test fun candidateMetadata() {
        assertEquals(PinyinCandidate("你好", 5, "nihao"), PinyinCandidate.decode("nihao\t5\t你好"))
    }
}
