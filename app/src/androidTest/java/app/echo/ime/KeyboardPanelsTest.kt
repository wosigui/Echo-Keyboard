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
        assertNotNull(text(binding.panel,context.getString(R.string.layout_full_title)))
        text(binding.panel,context.getString(R.string.layout_full_title))!!.performClick()
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
        text(binding.panel,"，")!!.performClick()
        assertEquals("，",committed)
        assertEquals("symbols",panels.current)
        panels.emoji()
        text(binding.panel,"😀")!!.performClick()
        assertEquals("😀",committed)
        panels.spellings(listOf(PinyinCandidate("你好",5,"nihao")))
        text(binding.panel,"nihao")!!.performClick()
        assertEquals("nihao",spelling)
        assertNull(panels.current)
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
