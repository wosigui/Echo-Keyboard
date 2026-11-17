package app.echo.ime

import androidx.test.platform.app.InstrumentationRegistry
import app.echo.pinyin.NativePinyin
import app.echo.pinyin.PinyinCandidate
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InputFeaturesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: InputRepository
    private lateinit var decoder: InputDecoder
    private lateinit var dictionary: File
    private lateinit var user: File
    private val databaseName = "input-features-test.db"

    @Before fun setUp() {
        context.deleteDatabase(databaseName)
        repository = InputRepository(context, databaseName)
        dictionary = File(context.cacheDir, "features-dictionary.dat")
        user = File(context.cacheDir, "features-user.dat")
        user.delete()
        context.assets.open("dict_pinyin.dat").use { source -> dictionary.outputStream().use { source.copyTo(it) } }
        assertTrue(NativePinyin.open(dictionary.absolutePath, user.absolutePath))
        decoder = InputDecoder(context, repository)
    }

    @After fun tearDown() {
        NativePinyin.close()
        repository.close()
        context.deleteDatabase(databaseName)
        dictionary.delete()
        user.delete()
    }

    @Test fun abbreviationAndDailyPrediction() {
        assertTrue(decoder.chinese("bjdx", false).any { it.text == "北京大学" && it.consumed == 4 })
        assertEquals("天气", decoder.predictions("今天", false).first().text)
    }

    @Test fun lockedT9SyllablesAndBacktracking() {
        val composition = T9Composition()
        assertTrue(composition.choices("64426", decoder.syllables).contains("ni"))
        assertTrue(composition.select("ni", "64426"))
        val result = decoder.chinese("64426", true, composition.prefix)
        assertTrue(result.any { it.text == "你好" && it.consumed == 5 })
        assertTrue(result.all { it.spelling.replace("'", "").startsWith("ni") })
        composition.deleted(1)
        assertEquals("", composition.prefix)
        assertTrue(decoder.chinese("64", true).any { it.text == "你" })
    }

    @Test fun typoAndFuzzyCorrection() {
        assertTrue(decoder.chinese("nihap", false).any { it.text == "你好" && it.corrected && it.consumed == 5 })
        assertTrue(decoder.chinese("zongguo", false).any { it.text == "中国" && it.corrected })
        assertTrue(decoder.chinese("nihap", false, correct = false, fuzzy = false).none { it.corrected })
        assertTrue(decoder.chinese("zongguo", false, correct = false, fuzzy = true).any { it.text == "中国" && it.corrected })
    }

    @Test fun frequencyLearningSurvivesReopeningAndCanBeCleared() {
        val university = PinyinCandidate("北京大学", 12, "bei'jing'da'xue")
        val capital = PinyinCandidate("北京大雪", 12, "bei'jing'da'xue")
        repository.learn("beijingdaxue", university, "今天", true)
        repeat(3) { repository.learn("beijingdaxue", capital, "今天", true) }
        repository.close()
        repository = InputRepository(context, databaseName)
        decoder = InputDecoder(context, repository)
        assertEquals("北京大雪", decoder.chinese("bjdx", false).first().text)
        assertEquals("北京大雪", decoder.chinese(T9Composition.encode("beijingdaxue"), true).first().text)
        assertEquals("北京大雪", decoder.predictions("今天", true).first().text)
        repository.clearLearning()
        assertTrue(repository.words("bjdx").isEmpty())
        assertTrue(repository.next("今天").isEmpty())
    }

    @Test fun englishCompletionAndPrivateLearning() {
        repository.learn("hel", PinyinCandidate("heliosphere", 3, "@en"), "", true)
        assertEquals("Heliosphere", decoder.english("Hel", true).first().text)
        assertTrue(repository.words("heliosphere").isEmpty())
        assertTrue(decoder.english("hel", false).all { it.text.startsWith("hel") })
        repository.learn("secret", PinyinCandidate("私人词语", 6, "si'ren'ci'yu"), "今天", false)
        assertTrue(repository.words("secret").isEmpty())
        assertTrue(repository.next("今天").isEmpty())
    }

    @Test fun clipboardHistoryAndEmojiRecents() {
        repository.captureClip("first", true)
        repository.captureClip("private", false)
        repository.captureClip("second", true)
        assertEquals(setOf("first", "second"), repository.clips().toSet())
        repository.deleteClip("first")
        assertEquals(listOf("second"), repository.clips())
        repository.clearClips()
        assertTrue(repository.clips().isEmpty())
        repository.useEmoji("😀")
        repository.useEmoji("😭", false)
        assertEquals(listOf("😀"), repository.recentEmoji())
        assertTrue(EmojiCatalog.search("开心").contains("😀"))
        assertTrue(EmojiCatalog.search("birthday cake").contains("🎂"))
        repository.clearEmoji()
        assertTrue(repository.recentEmoji().isEmpty())
    }
}
