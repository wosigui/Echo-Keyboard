package app.echo.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.SeekBar
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.echo.ime.databinding.InputViewBinding
import app.echo.pinyin.PinyinCandidate
import org.junit.Assert.*
import org.junit.Test

class KeyboardPanelsTest {
    private fun onUi(test: (android.content.Context, InputViewBinding) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext,R.style.Theme_Echo)
            test(context, InputViewBinding.inflate(LayoutInflater.from(context)))
        }
    }
    private fun text(root: View, text: String): View? {
        if(root is TextView && root.text.toString() == text) return root
        if(root is ViewGroup) for(i in 0 until root.childCount) text(root.getChildAt(i),text)?.let { return it }
        return null
    }
    @Test fun keyboardLayoutSelectionAndCandidateConsumption() = onUi { context,binding ->
        var selectedLayout = ""
        var selected: PinyinCandidate? = null
        val panels = KeyboardPanels(context,binding,{}, {}, { selectedLayout=it }, {}, { false }, { selected=it }, {})
        panels.layouts()
        assertEquals(View.GONE,binding.keyboard.visibility)
        assertNotNull(text(binding.panel,"26键拼音"))
        text(binding.panel,"26键拼音")!!.performClick()
        assertEquals("full",selectedLayout)
        assertEquals(View.VISIBLE,binding.keyboard.visibility)
        val entry = PinyinCandidate("你",2,"nihao")
        panels.expanded(listOf(entry))
        text(binding.panel,"你")!!.performClick()
        assertEquals(entry,selected)
        assertNull(panels.current)
    }
    @Test fun symbolsEmojiAndSpellingCallbacks() = onUi { context,binding ->
        var committed = ""
        var spelling: String? = null
        val panels = KeyboardPanels(context,binding,{ committed=it }, {}, {}, {}, { false }, {}, { spelling=it })
        panels.symbols()
        text(binding.panel,"!")!!.performClick()
        assertEquals("!",committed)
        assertEquals("symbols",panels.current)
        panels.emoji()
        text(binding.panel,"😀")!!.performClick()
        assertEquals("😀",committed)
        panels.spellings(listOf(PinyinCandidate("你好",5,"nihao")))
        text(binding.panel,"nihao")!!.performClick()
        assertEquals("nihao",spelling)
        assertNull(panels.current)
    }
    @Test fun referencePanelsRouteKeysAndRestoreToolbar() = onUi { context,binding ->
        var committed=""
        var pressed=0
        val panels=KeyboardPanels(context,binding,{ committed=it }, { pressed=it }, {}, {}, { false }, {}, {})
        fun capture(name:String) {
            val width=context.resources.displayMetrics.widthPixels
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            binding.root.layout(0,0,width,binding.root.measuredHeight)
            val bitmap=android.graphics.Bitmap.createBitmap(width,binding.root.height,android.graphics.Bitmap.Config.ARGB_8888)
            binding.root.draw(android.graphics.Canvas(bitmap))
            java.io.File(context.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        panels.layouts(); capture("reference-layouts")
        panels.numbers("发送"); capture("reference-numbers")
        assertEquals(View.VISIBLE,binding.toolbar.visibility)
        text(binding.panel,"8")!!.performClick()
        assertEquals("8",committed)
        text(binding.panel,"⌫")!!.performClick()
        assertEquals(-5,pressed)
        text(binding.panel,"发送")!!.performClick()
        assertEquals(10,pressed)
        text(binding.panel,"符")!!.performClick()
        assertEquals("symbols",panels.current)
        capture("reference-symbols")
        assertEquals(View.GONE,binding.toolbar.visibility)
        text(binding.panel,"网络")!!.performClick()
        text(binding.panel,"https://")!!.performClick()
        assertEquals("https://",committed)
        assertNull(panels.current)
        assertEquals(View.VISIBLE,binding.toolbar.visibility)
    }

    @Test fun sensitiveFieldDisablesClipboardAndEditorSelectionWorks() = onUi { context,binding ->
        var key = 0
        var edit = 0
        val panels = KeyboardPanels(context,binding,{}, { key=it }, {}, { edit=it }, { true }, {}, {})
        panels.clipboard()
        assertNotNull(text(binding.panel,context.getString(R.string.clipboard_sensitive)))
        assertNull(text(binding.panel,context.getString(R.string.paste_clip)))
        panels.editor()
        text(binding.panel,context.getString(R.string.select_all))!!.performClick()
        assertEquals(android.R.id.selectAll,edit)
        text(binding.panel,context.getString(R.string.select_text))!!.performClick()
        text(binding.panel,context.getString(R.string.cursor_left))!!.performClick()
        assertEquals(-312,key)
    }

    @Test fun feedbackSettingsAndOneHandModePersist() = onUi { context,binding ->
        val preferences=context.getSharedPreferences("keyboard",android.content.Context.MODE_PRIVATE)
        val saved=preferences.all
        try {
            preferences.edit().putBoolean("haptic",false).putBoolean("sound",false).putBoolean("preview",true).apply()
            var updates=0
            val panels=KeyboardPanels(context,binding,{}, {}, {}, {}, { false }, {}, {}, onSettingsChanged={ updates++ })
            panels.tools()
            text(binding.panel,context.getString(R.string.haptic_off))!!.performClick()
            text(binding.panel,context.getString(R.string.sound_off))!!.performClick()
            text(binding.panel,context.getString(R.string.preview_on))!!.performClick()
            assertTrue(preferences.getBoolean("haptic",false))
            assertTrue(preferences.getBoolean("sound",false))
            assertFalse(preferences.getBoolean("preview",true))
            panels.sizes()
            assertNotNull(findSeekBar(binding.panel))
            text(binding.panel,context.getString(R.string.one_hand_left))!!.performClick()
            assertEquals(1,preferences.getInt("one_hand",0))
            text(binding.panel,context.getString(R.string.one_hand_right))!!.performClick()
            assertEquals(2,preferences.getInt("one_hand",0))
            text(binding.panel,context.getString(R.string.one_hand_full))!!.performClick()
            assertEquals(0,preferences.getInt("one_hand",-1))
            assertEquals(6,updates)
        } finally {
            val editor=preferences.edit().clear()
            saved.forEach { (name,value) -> when(value) {
                is Boolean -> editor.putBoolean(name,value)
                is Int -> editor.putInt(name,value)
                is Long -> editor.putLong(name,value)
                is Float -> editor.putFloat(name,value)
                is String -> editor.putString(name,value)
            } }
            editor.commit()
        }
    }

    @Test fun resizingChangesBothKeyGeometryAndKeyboardHeight() = onUi { context,_ ->
        val width=context.resources.displayMetrics.widthPixels
        val standard=SizedKeyboard(context,R.xml.letters,width,52)
        val taller=SizedKeyboard(context,R.xml.letters,width,68)
        assertTrue(taller.height>standard.height)
        assertEquals(standard.keys.size,taller.keys.size)
        standard.keys.zip(taller.keys).forEach { (normal,large) ->
            assertTrue(large.height>normal.height)
            assertEquals(normal.x,large.x)
            assertEquals(normal.width,large.width)
            assertTrue(large.isInside(large.x+large.width/2,large.y+large.height/2))
        }
    }

    private fun findSeekBar(root: View): SeekBar? {
        if(root is SeekBar)return root
        if(root is ViewGroup)for(index in 0 until root.childCount)findSeekBar(root.getChildAt(index))?.let { return it }
        return null
    }
}
