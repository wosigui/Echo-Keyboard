package app.echo.ime

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import app.echo.ime.databinding.InputViewBinding
import app.echo.pinyin.PinyinCandidate

/** In-keyboard pages; all actions are routed back to the current editor. */
class KeyboardPanels(
    private val context: Context,
    private val binding: InputViewBinding,
    private val literal: (String) -> Unit,
    private val key: (Int) -> Unit,
    private val layout: (String) -> Unit,
    private val edit: (Int) -> Unit,
    private val sensitive: () -> Boolean,
    private val choose: (PinyinCandidate) -> Unit,
    private val filter: (String?) -> Unit,
    private val repository: InputRepository? = null,
    private val onSettingsChanged: () -> Unit = {},
    private val onEmojiSearch: () -> Unit = {},
    private val onEmoji: (String) -> Unit = literal,
    private val captureClipboard: () -> Unit = {},
    private val clearLearning: () -> Unit = {}
) {
    var current: String? = null
        private set
    var selecting = false
        private set
    private val preferences = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    fun hide() {
        current = null
        binding.panel.visibility = View.GONE
        binding.keyboard.visibility = View.VISIBLE
        binding.expand.tag = "down"; binding.expand.invalidate()
    }
    private fun label(text: String, size: Float = 15f) = TextView(context).apply {
        this.text = text; textSize = size; gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.keyboard_text))
        minHeight = dp(44); isFocusable = true
        background = context.getDrawable(R.drawable.candidate_background)
    }
    private fun page(kind: String, title: Int): LinearLayout {
        current = kind
        binding.keyboard.visibility = View.GONE
        binding.panel.visibility = View.VISIBLE
        binding.panel.removeAllViews()
        val header = LinearLayout(context)
        header.addView(label("‹  " + context.getString(R.string.back_keyboard), 13f).apply { setOnClickListener { hide() } }, LinearLayout.LayoutParams(dp(104),dp(40)))
        header.addView(label(context.getString(title), 14f).apply { typeface = Typeface.DEFAULT_BOLD; isFocusable = false }, LinearLayout.LayoutParams(0,dp(40),1f))
        binding.panel.addView(header)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        binding.panel.addView(content, LinearLayout.LayoutParams(-1,0,1f))
        binding.expand.tag = "up"; binding.expand.invalidate()
        return content
    }
    private fun grid(parent: LinearLayout, cells: List<Pair<String, () -> Unit>>, columns: Int = 4, large: Boolean = false) {
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        cells.chunked(columns).forEach { group ->
            val row = LinearLayout(context)
            group.forEach { (text, action) ->
                row.addView(label(text, if (large) 23f else 14f).apply {
                    setOnClickListener { action() }
                    val fill = GradientDrawable().apply { setColor(context.getColor(R.color.key_surface)); cornerRadius = dp(5).toFloat() }
                    background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(context.getColor(R.color.key_pressed)),fill,null)
                }, LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            }
            repeat(columns-group.size) { row.addView(View(context),LinearLayout.LayoutParams(0,dp(44),1f)) }
            rows.addView(row)
        }
        scroll.addView(rows); parent.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
    }
    fun layouts() {
        grid(page("layouts", R.string.keyboard_layout), listOf(
            context.getString(R.string.layout_nine_title) to { layout("nine"); hide() },
            context.getString(R.string.layout_full_title) to { layout("full"); hide() },
            context.getString(R.string.english_title) to { layout("english"); hide() },
            context.getString(R.string.numbers_title) to { layout("numbers"); hide() }
        ),2)
    }
    fun tools() {
        fun toggle(name: String) { preferences.edit().putBoolean(name,!preferences.getBoolean(name,name in setOf("learning","clipboard_history","preview","correction","fuzzy"))).apply(); onSettingsChanged(); tools() }
        val cells = listOf(
            R.string.keyboard_layout to { layouts() }, R.string.symbols_title to { symbols() },
            R.string.emoji to { emoji() }, R.string.text_editing to { editor() },
            R.string.clipboard to { clipboard() },
            (if(preferences.getBoolean("haptic",false)) R.string.haptic_on else R.string.haptic_off) to { toggle("haptic") },
            (if(preferences.getBoolean("sound",false)) R.string.sound_on else R.string.sound_off) to { toggle("sound") },
            R.string.clear_composition to { key(-104); hide() },
            (if(preferences.getBoolean("learning",true)) R.string.learning_on else R.string.learning_off) to { toggle("learning") },
            R.string.clear_learning to { clearLearning() },
            (if(preferences.getBoolean("clipboard_history",true)) R.string.clipboard_history_on else R.string.clipboard_history_off) to { toggle("clipboard_history") },
            (if(preferences.getBoolean("preview",true)) R.string.preview_on else R.string.preview_off) to { toggle("preview") },
            (if(preferences.getBoolean("correction",true)) R.string.correction_on else R.string.correction_off) to { toggle("correction") },
            (if(preferences.getBoolean("fuzzy",true)) R.string.fuzzy_on else R.string.fuzzy_off) to { toggle("fuzzy") },
            R.string.keyboard_size to { sizes() }

        )
        grid(page("tools",R.string.tools),cells.map { context.getString(it.first) to it.second },3)
    }
    fun expanded(candidates: List<PinyinCandidate>) {
        grid(page("candidates",R.string.all_candidates), candidates.map { it.text to { hide(); choose(it) } },4,true)
    }
    fun spellings(candidates: List<PinyinCandidate>) {
        val body = page("spelling", R.string.spelling_title)
        val options = candidates.map { it.spelling }.filter { it.isNotEmpty() }.distinct()
        if (options.isEmpty()) body.addView(label(context.getString(R.string.spelling_empty),13f))
        else grid(body, listOf(context.getString(R.string.all_spelling) to { filter(null); hide() }) + options.map { value -> value to { filter(value); hide() } },3)
    }
    fun clipboard() {
        val body = page("clipboard",R.string.clipboard)
        if (sensitive()) { body.addView(label(context.getString(R.string.clipboard_sensitive),13f)); return }
        captureClipboard()
        val manager=context.getSystemService(ClipboardManager::class.java)
        val safe=manager.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE",false)!=true
        val text=if(safe) manager.primaryClip?.takeIf { it.itemCount>0 }?.getItemAt(0)?.text?.toString() else null
        val rows=if(preferences.getBoolean("clipboard_history",true)) repository?.clips().orEmpty().ifEmpty { listOfNotNull(text) } else listOfNotNull(text)
        body.addView(label(context.getString(R.string.clear_clipboard),13f).apply { setOnClickListener { repository?.clearClips(); clipboardRecords(emptyList()) } },LinearLayout.LayoutParams(-1,dp(32)))
        clipboardRows(body,rows)
    }
    private fun clipboardRecords(rows: List<String>) { clipboardRows(page("clipboard",R.string.clipboard),rows) }
    private fun clipboardRows(body: LinearLayout,rows: List<String>) {
        if(rows.isEmpty()) { body.addView(label(context.getString(R.string.clipboard_empty),13f)); return }
        val scroll=ScrollView(context)
        val list=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        rows.forEach { value ->
            val row=LinearLayout(context)
            row.addView(label(value.take(200),14f).apply {
                gravity=Gravity.START or Gravity.CENTER_VERTICAL; maxLines=3; setPadding(dp(10),dp(8),dp(10),dp(8))
                contentDescription=context.getString(R.string.paste_clip)+": "+value.take(40)
                setOnClickListener { literal(value); hide() }
            },LinearLayout.LayoutParams(0,dp(72),1f))
            row.addView(label(context.getString(R.string.delete_record),12f).apply { setOnClickListener { repository?.deleteClip(value); clipboardRecords(rows.filter { it!=value }) } },LinearLayout.LayoutParams(dp(52),dp(72)))
            list.addView(row)
        }
        scroll.addView(list);body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
    }
    fun sizes() {
        val body=page("sizes",R.string.keyboard_size)
        val height=preferences.getInt("row_height",52).coerceIn(40,68)
        val title=label(context.getString(R.string.keyboard_height)+": "+height+" dp",13f)
        body.addView(title,LinearLayout.LayoutParams(-1,dp(28)))
        body.addView(SeekBar(context).apply {
            max=7;progress=(height-40)/4
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                    if(fromUser) { preferences.edit().putInt("row_height",40+value*4).apply();title.text=context.getString(R.string.keyboard_height)+": "+(40+value*4)+" dp";onSettingsChanged() }
                }
                override fun onStartTrackingTouch(bar:SeekBar?)=Unit
                override fun onStopTrackingTouch(bar:SeekBar?)=Unit
            })
        },LinearLayout.LayoutParams(-1,dp(36)))
        grid(body,listOf(R.string.one_hand_full,R.string.one_hand_left,R.string.one_hand_right).mapIndexed { index,id -> context.getString(id) to { preferences.edit().putInt("one_hand",index).apply();onSettingsChanged();sizes() } },3)
    }
    fun syllables(values: List<String>,chooseSyllable: (String) -> Unit) {
        val body=page("spelling",R.string.spelling_title)
        grid(body,values.map { value -> value to { chooseSyllable(value);hide() } },4)
    }
    fun editor() {
        val cells = listOf(
            R.string.select_all to { edit(android.R.id.selectAll) }, R.string.cut_text to { edit(android.R.id.cut) },
            R.string.copy_text to { edit(android.R.id.copy) }, R.string.paste_text to { edit(android.R.id.paste) },
            R.string.cursor_home to { key(-301) }, R.string.cursor_up to { key(if(selecting) -311 else -302) },
            R.string.cursor_end to { key(-303) }, R.string.delete_text to { key(-5) },
            R.string.cursor_left to { key(if(selecting) -312 else -304) }, R.string.cursor_down to { key(if(selecting) -313 else -305) },
            R.string.cursor_right to { key(if(selecting) -314 else -306) },
            (if(selecting) R.string.stop_select else R.string.select_text) to { selecting = !selecting; editor() }
        )
        grid(page("editor",R.string.text_editing),cells.map { context.getString(it.first) to it.second })
    }
    fun symbols(category: Int = 0) {
        val categories = listOf(R.string.cat_common,R.string.cat_chinese,R.string.cat_english,R.string.cat_math,R.string.cat_arrows)
        val values = listOf(
            listOf("，","。","？","！","、","：","；","…","@","#","%","&","（","）","“","”","《","》","～","—"),
            listOf("，","。","、","；","：","？","！","“","”","‘","’","（","）","【","】","《","》","〈","〉","「","」","『","』","·"),
            listOf(",",".","?","!",":",";","'","\"","@","#","$","%","&","_","-","/","\\","(",")","[","]","{","}"),
            listOf("+","−","×","÷","=","≠","≈","≤","≥","±","∞","√","π","°","℃","‰"),
            listOf("←","↑","→","↓","↔","↕","↗","↘","↙","↖","⇒","⇐","⇔","↵","⇧","⌘")
        )
        val body = page("symbols",R.string.symbols_title)
        tabs(body,categories,category) { symbols(it) }
        grid(body,values[category].map { s -> s to { literal(s) } },6,true)
    }
    fun emoji(category: Int = -1) {
        val body=page("emoji",R.string.emoji)
        val header=LinearLayout(context)
        header.addView(label(context.getString(R.string.emoji_search),13f).apply { setOnClickListener { hide();onEmojiSearch() } },LinearLayout.LayoutParams(0,dp(34),1f))
        header.addView(label(context.getString(R.string.clear_emoji_recent),12f).apply { setOnClickListener { repository?.clearEmoji();emoji() } },LinearLayout.LayoutParams(0,dp(34),1f))
        body.addView(header)
        tabs(body,listOf(R.string.cat_recent,R.string.cat_faces,R.string.cat_gestures,R.string.cat_objects,R.string.cat_kaomoji),category+1) { emoji(it-1) }
        val values=if(category<0) repository?.recentEmoji().orEmpty().ifEmpty { EmojiCatalog.entries.filter { it.category==0 }.map { it.value } } else EmojiCatalog.entries.filter { it.category==category }.map { it.value }
        grid(body,values.map { value -> value to { onEmoji(value) } },if(category==3) 2 else 6,category!=3)
    }
    private fun tabs(body: LinearLayout, categories: List<Int>, selected: Int, action: (Int) -> Unit) {
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context)
        categories.forEachIndexed { index,id -> row.addView(label(context.getString(id),13f).apply {
            if(index==selected) setTextColor(0xFF8FBDF5.toInt())
            setOnClickListener { action(index) }
        },LinearLayout.LayoutParams(dp(64),dp(34))) }
        scroll.addView(row); body.addView(scroll)
    }
}
