package app.echo.ime

import org.junit.Assert.*
import org.junit.Test

class InputSupportTest {
    @Test fun syllableLocksRejectMismatchesAndBacktrack() {
        val composition = T9Composition()
        assertFalse(composition.select("hao", "64426"))
        assertTrue(composition.select("ni", "64426"))
        assertEquals(listOf("hao"), composition.choices("64426", listOf("ni", "hao", "ma")))
        assertTrue(composition.select("hao", "64426"))
        assertEquals("ni'hao'", composition.prefix)
        composition.deleted(4)
        assertEquals("ni'", composition.prefix)
        composition.deleted(1)
        assertEquals("", composition.prefix)
    }

    @Test fun typoAndFuzzySwitchesAreIndependentAndBounded() {
        assertTrue(PinyinCorrections.variants("nihap", fuzzy = false).contains("nihao"))
        assertTrue(PinyinCorrections.variants("nihoa", fuzzy = false).contains("nihao"))
        assertTrue(PinyinCorrections.variants("nihaao", fuzzy = false).contains("nihao"))
        assertTrue(PinyinCorrections.variants("zongguo", typos = false).contains("zhongguo"))
        assertTrue(PinyinCorrections.variants("zongguo", false, false).isEmpty())
        assertTrue(PinyinCorrections.variants("nihao2").isEmpty())
        assertTrue(PinyinCorrections.variants("a".repeat(33)).isEmpty())
        assertTrue(PinyinCorrections.variants("zhongguorenmin").size <= 96)
    }

    @Test fun englishPrefixesPreserveFrequencyAndCase() {
        val lexicon = EnglishLexicon(listOf("hello", "help", "helmet", "hello", "bad-word"))
        assertEquals(listOf("hello", "help", "helmet"), lexicon.complete("hel"))
        assertEquals(listOf("HELLO", "HELP", "HELMET"), lexicon.complete("HEL"))
        assertEquals(listOf("Hello", "Help", "Helmet"), lexicon.complete("Hel"))
        assertTrue(lexicon.complete("").isEmpty())
        assertTrue(lexicon.complete("unknown").isEmpty())
    }

    @Test fun emojiSearchRequiresEveryKeyword() {
        assertEquals(listOf("🎂"), EmojiCatalog.search(" birthday cake "))
        assertTrue(EmojiCatalog.search("不存在的表情").isEmpty())
        assertTrue(EmojiCatalog.search("开心").contains("😀"))
    }
}
