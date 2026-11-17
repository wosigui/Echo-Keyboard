@file:Suppress("DEPRECATION")
package app.echo.ime

import app.echo.pinyin.NativePinyin
import app.echo.pinyin.PinyinCandidate

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File

class PinyinEngineTest {
    private lateinit var user: File
    @Before fun setUp() {

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dict = File(context.cacheDir, "engine-test.dat")
        context.assets.open("dict_pinyin.dat").use { input -> dict.outputStream().use { input.copyTo(it) } }
        user = File(context.cacheDir, "engine-test-user.dat")
        user.delete()
        assertTrue("AOSP dictionary must open", NativePinyin.open(dict.absolutePath, user.absolutePath))
    }
    @After fun tearDown() { NativePinyin.close(); user.delete() }
    private fun query(input: String, t9: Boolean = false) = NativePinyin.query(input, t9).map(PinyinCandidate::decode)
    @Test fun testFullPinyinAndSentence() {
        assertTrue(query("nihao").any { it.text == "你好" && it.consumed == 5 })
        assertTrue(query("zhongguo").any { it.text == "中国" })
        assertTrue(query("woaini").any { it.text == "我爱你" })
        assertTrue(query("jintiantianqihenhao").first().text.length >= 6)
    }
    @Test fun testAbbreviationsAndPartialSelection() {
        assertTrue(query("zg").any { it.text == "中国" })
        val partial = query("nihao").firstOrNull { it.text == "你" }
        assertNotNull("Single character selection must preserve hao", partial)
        assertEquals(2, partial!!.consumed)
    }
    @Test fun testT9AndBacktracking() {
        val start = System.nanoTime()
        assertTrue(query("64", true).any { it.text == "你" })
        assertTrue(query("64426", true).any { it.text == "你好" })
        assertTrue(query("94664486", true).any { it.text == "中国" })
        assertTrue(query("64", true).any { it.text == "你" })
        assertTrue(query("64426", true).any { it.text == "你好" })
        assertTrue("T9 must finish within a practical bound", (System.nanoTime() - start) / 1_000_000 < 5000)
    }
    @Test fun testPredictionAndInvalidInput() {
        assertTrue(NativePinyin.predict("中国").isNotEmpty())
        assertTrue(query("").isEmpty())
        assertTrue(query("111", true).isEmpty())
        assertTrue(query("nihao").isNotEmpty())
    }
}
