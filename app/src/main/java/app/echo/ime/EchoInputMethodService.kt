@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package app.echo.ime

import app.echo.pinyin.NativePinyin
import app.echo.pinyin.PinyinCandidate
import android.content.*
import android.content.ClipboardManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.os.*
import android.text.*
import android.view.*
import android.view.inputmethod.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.echo.ime.databinding.InputViewBinding
import java.io.File
import java.util.concurrent.Executors

class EchoInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    private var binding: InputViewBinding? = null
    private val composer=PinyinComposer()
    private val t9=T9Composition()
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val preferences get()=getSharedPreferences("keyboard",MODE_PRIVATE)
    private val repository by lazy { InputRepository(this) }
    private var decoder: InputDecoder?=null
    private var syllables=emptyList<String>()
    @Volatile private var generation=0
    @Volatile private var destroyed=false
    private var candidates=emptyList<PinyinCandidate>()
    private var panels: KeyboardPanels?=null
    private var chinese=true
    private var sensitive=false
    private var privateInput=false
    private var numeric=false
    private var numbers=false
    private var shifted=false
    private var nineKey=false
    private var engineReady=false
    private var engineFailed=false
    private var pendingSpace=false
    private val deferredKeys=mutableListOf<Int>()
    private var previousWord=""
    private var selectedRaw=""
    private var selectedWord=""
    private var selectedSpelling=""
    private var phrasePrevious=""
    private var emojiSearch: EditText?=null
    private var searchConnection: BaseInputConnection?=null
    private var voiceText: String?=null
    private var voiceEditor: Pair<String?,Int>?=null
    private val userDictionary by lazy { File(cacheDir,"pinyin-session.dat") }
    private val clipboardListener=ClipboardManager.OnPrimaryClipChangedListener {
        if(sensitive || privateInput) preferences.edit().putBoolean("clipboard_blocked",true).apply()
        else {
            preferences.edit().putBoolean("clipboard_blocked",false).apply()
            captureClipboard()
            if(panels?.current=="clipboard") panels?.clipboard()
        }
    }
    private fun connection(): InputConnection?=searchConnection ?: currentInputConnection
    private fun personalize()=!sensitive && !privateInput && !numeric && emojiSearch==null && preferences.getBoolean("learning",true)
    private fun useT9()=nineKey && chinese && !sensitive && !numeric && !numbers
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        nineKey=preferences.getBoolean("nine_key",false)
        getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener(clipboardListener)
        worker.execute {
            try {
                val dictionary=File(filesDir,"aosp-pinyin-v1.dat")
                if(!dictionary.exists()) assets.open("dict_pinyin.dat").use { source -> dictionary.outputStream().use { source.copyTo(it) } }
                userDictionary.delete()
                check(NativePinyin.open(dictionary.absolutePath,userDictionary.absolutePath))
                val model=InputDecoder(this,repository)
                decoder=model
                main.post { if(!destroyed) { engineReady=true;syllables=model.syllables;refreshCandidates() } }
            } catch (_:Exception) { main.post { engineFailed=true;showCandidates() } }
        }
    }
    override fun onCreateInputView(): View {
        val view=InputViewBinding.inflate(layoutInflater);binding=view
        ViewCompat.setOnApplyWindowInsetsListener(view.root) { root,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            root.setPadding(bars.left,0,bars.right,maxOf(bars.bottom,(44f*resources.displayMetrics.widthPixels/1080f).toInt()));insets
        }
        view.keyboard.setOnKeyboardActionListener(this)
        view.keyboard.isPreviewEnabled=false
        view.keyboard.onAlternateText={ commitLiteral(it) }
        view.keyboard.onVoice={ startVoice() }
        view.keyboard.onCursorMove={ steps ->
            flush()
            repeat(kotlin.math.abs(steps).coerceAtMost(50)) { sendCursor(if(steps>0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT,panels?.selecting==true) }
        }
        panels=KeyboardPanels(this,view,
            literal={ commitLiteral(it) },key={ onKey(it,null) },layout={ mode ->
                endEmojiSearch();flush();chinese=mode!="english";numbers=mode=="numbers" || numeric
                if(mode=="nine" || mode=="full") { nineKey=mode=="nine";preferences.edit().putBoolean("nine_key",nineKey).apply() }
                updateKeyboard()
            },edit={ action -> flush();connection()?.performContextMenuAction(action) },
            sensitive={ sensitive || numeric || privateInput },choose={ if(it in candidates)select(it) },
            filter={ spelling -> t9.clear();spelling?.split('\'')?.filter { it.isNotEmpty() }?.forEach { t9.select(it,composer.text) };refreshCandidates() },
            repository=repository,onSettingsChanged={ updateKeyboard();refreshCandidates() },
            onEmojiSearch={ beginEmojiSearch() },onEmoji={ insertEmoji(it) },captureClipboard={ captureClipboard() },
            clearLearning={ worker.execute { repository.clearLearning();main.post { if(!destroyed)refreshCandidates() } } }
        )
        view.tools.setOnClickListener { panels?.tools() }
        view.layoutToggle.setOnClickListener { panels?.layouts() }
        view.emoji.setOnClickListener { panels?.emoji() }
        view.editor.setOnClickListener { panels?.editor() }
        view.voice.setOnClickListener { startVoice() }
        view.hide.setOnClickListener { requestHideSelf(0) }
        view.systemKeyboard.setOnClickListener { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        view.clipboard.setOnClickListener { panels?.clipboard() }
        view.expand.setOnClickListener { if(panels?.current=="candidates")panels?.hide() else panels?.expanded(candidates) }
        view.oneHandRestore.setOnClickListener { preferences.edit().putInt("one_hand",0).apply();updateKeyboard() }
        view.emojiSearchClose.setOnClickListener { endEmojiSearch();showCandidates() }
        window?.window?.let {
            it.navigationBarColor=getColor(R.color.keyboard_background)
            if(Build.VERSION.SDK_INT>=26) it.decorView.systemUiVisibility=it.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        updateKeyboard();return view.root
    }
    override fun onStartInput(attribute:EditorInfo,restarting:Boolean) {
        super.onStartInput(attribute,restarting)
        endEmojiSearch();cancelPending();clearComposition();previousWord="";panels?.hide()
        val kind=attribute.inputType and InputType.TYPE_MASK_CLASS
        val variation=attribute.inputType and InputType.TYPE_MASK_VARIATION
        sensitive=kind==InputType.TYPE_CLASS_TEXT && variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_URI) || kind==InputType.TYPE_CLASS_NUMBER || kind==InputType.TYPE_CLASS_PHONE
        privateInput=attribute.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING!=0 || attribute.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0
        numeric=kind==InputType.TYPE_CLASS_NUMBER || kind==InputType.TYPE_CLASS_PHONE || kind==InputType.TYPE_CLASS_DATETIME
        numbers=numeric;shifted=false
        if(voiceEditor!=null && voiceEditor!=(attribute.packageName to attribute.fieldId)) { voiceText=null;voiceEditor=null;VoiceInputResult.listener=null }
        updateKeyboard()
    }
    override fun onStartInputView(info:EditorInfo,restarting:Boolean) { super.onStartInputView(info,restarting);updateKeyboard();main.post { deliverVoice();captureClipboard() } }
    override fun onWindowShown() { super.onWindowShown();main.post { deliverVoice();captureClipboard() } }
    private fun updateKeyboard() {
        val view=binding ?: return
        val oneHand=preferences.getInt("one_hand",0)
        val available=(view.root.width.takeIf { it>0 } ?: resources.displayMetrics.widthPixels)-view.root.paddingLeft-view.root.paddingRight
        val contentWidth=if(oneHand==0)available else (available*.84f).toInt()
        view.keyboardContent.layoutParams=FrameLayout.LayoutParams(if(oneHand==0)-1 else contentWidth,-2,if(oneHand==2)Gravity.RIGHT else Gravity.LEFT)
        view.oneHandRestore.visibility=if(oneHand==0)View.GONE else View.VISIBLE
        view.oneHandRestore.layoutParams=FrameLayout.LayoutParams(dp(40),dp(56),(if(oneHand==2)Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL)
        val unit=contentWidth/1080f
        fun referenceHeight(target: View, pixels: Int) { target.layoutParams=target.layoutParams.apply { height=(pixels*unit).toInt() } }
        referenceHeight(view.toolbar,121)
        referenceHeight(view.candidateBar,121)
        referenceHeight(view.bottomControls,112)
        view.bottomControls.setPadding((64*unit).toInt(),0,(64*unit).toInt(),0)
        listOf(view.systemKeyboard,view.clipboard).forEach {
            it.layoutParams=it.layoutParams.apply { width=(136*unit).toInt();height=(100*unit).toInt() }
        }
        referenceHeight(view.expand,121)
        view.expand.tag="right"
        val rowHeight=preferences.getInt("row_height",52).coerceIn(40,68)
        view.panel.layoutParams=view.panel.layoutParams.apply { height=dp(rowHeight*4) }
        view.keyboard.keyboard=SizedKeyboard(this,when { numbers -> R.xml.numbers;useT9() -> R.xml.nine_key;else -> R.xml.letters },(contentWidth-view.keyboard.paddingLeft-view.keyboard.paddingRight).coerceAtLeast(1),rowHeight,contentWidth)
        view.keyboard.isShifted=shifted
        view.keyboard.showLowerCase=!chinese && !shifted
        view.keyboard.keyBubbleEnabled=preferences.getBoolean("preview",true) && !sensitive
        val info=currentInputEditorInfo
        val action=info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val label=if(emojiSearch!=null || (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION!=0) "↵" else info?.actionLabel ?: when(action) {
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.action_search);EditorInfo.IME_ACTION_GO -> getString(R.string.action_go);EditorInfo.IME_ACTION_NEXT -> getString(R.string.action_next);EditorInfo.IME_ACTION_DONE -> getString(R.string.action_done);EditorInfo.IME_ACTION_SEND -> getString(R.string.action_send);else -> "↵"
        }
        view.keyboard.keyboard.keys.find { it.codes[0]==10 }?.label=label
        view.keyboard.keyboard.keys.find { it.codes[0]==-10 }?.label=if(chinese)"中/英" else "EN/中"
        view.keyboard.invalidateAllKeys()
        view.voice.isEnabled=!sensitive && !numeric && emojiSearch==null
        view.voice.alpha=if(view.voice.isEnabled)1f else .35f
        view.layoutToggle.isEnabled=!numeric && !sensitive
        showCandidates()
        if(numbers) panels?.numbers(label.toString())
    }
    private fun refreshCandidates() {
        val ticket=++generation
        val input=composer.text
        candidates=emptyList();showCandidates(updateCandidateItems=input.isEmpty() || !engineReady)
        if(input.isEmpty() || !engineReady)return
        val isChinese=chinese;val isT9=useT9();val prefix=t9.prefix;val personal=personalize()
        val correct=preferences.getBoolean("correction",true);val fuzzy=preferences.getBoolean("fuzzy",true)
        worker.execute {
            if(ticket!=generation || destroyed)return@execute
            val model=decoder ?: return@execute
            val result=if(isChinese)model.chinese(input,isT9,prefix,personal,correct,fuzzy) else model.english(input,personal)
            main.post { if(!destroyed && ticket==generation) { candidates=result;showCandidates() } }
        }
    }
    private fun showCandidates(updateCandidateItems:Boolean=true) {
        val view=binding ?: return
        view.composition.text=if(useT9() && t9.prefix.isNotEmpty()) t9.prefix+composer.text.drop(t9.lockedDigits) else composer.text
        view.composition.visibility=View.GONE
        view.keyboard.compositionText=composer.text
        view.keyboard.segmentation=chinese && composer.text.isNotEmpty()
        view.keyboard.invalidate()
        val hasInput=composer.text.isNotEmpty() || candidates.isNotEmpty()
        view.toolbar.visibility=if(hasInput || panels?.current in setOf("symbols","layouts"))View.GONE else View.VISIBLE
        view.candidateBar.visibility=if(hasInput)View.VISIBLE else View.GONE
        view.expand.isEnabled=candidates.isNotEmpty()
        val ticket=generation
        if(updateCandidateItems || view.candidates.childCount==0) {
            view.candidates.removeAllViews()
            if(candidates.isEmpty() && composer.text.isNotEmpty()) {
                candidateView(composer.text).apply { textSize=14f;setOnClickListener { if(ticket==generation)flush() };view.candidates.addView(this) }
            } else candidates.forEachIndexed { index,entry ->
                candidateView(entry.text).apply {
                    if(index==0)setTextColor(0xFF387BDA.toInt())
                    if(entry.corrected)contentDescription=getString(R.string.correction_label,entry.spelling)+": "+entry.text
                    setOnClickListener { if(ticket==generation)select(entry) };view.candidates.addView(this)
                }
            }
            if(candidates.isNotEmpty() && composer.text.isNotEmpty()) candidateView(composer.text).apply { textSize=13f;setTextColor(getColor(R.color.keyboard_muted));setOnClickListener { if(ticket==generation)flush() };view.candidates.addView(this) }
        }
        view.syllablesScroll.visibility=if(useT9()) { if(composer.text.isNotEmpty())View.VISIBLE else View.INVISIBLE } else View.GONE
        view.syllableChoices.removeAllViews()
        if(useT9() && composer.text.isNotEmpty()) {
            if(t9.prefix.isNotEmpty()) candidateView(getString(R.string.syllable_all)).apply { textSize=12f;setOnClickListener { t9.clear();refreshCandidates() };view.syllableChoices.addView(this) }
            syllableOptions().take(40).forEach { syllable -> candidateView(syllable).apply { textSize=15f;setOnClickListener { chooseSyllable(syllable) };view.syllableChoices.addView(this) } }
        }
        if(engineFailed && !hasInput) { candidateView(getString(R.string.engine_failed)).apply { textSize=12f;view.candidates.addView(this) };view.candidateBar.visibility=View.VISIBLE;view.toolbar.visibility=View.GONE }
        if(updateCandidateItems && panels?.current=="candidates")panels?.expanded(candidates)
        if(emojiSearch!=null)renderEmojiSearch()
    }
    private fun candidateView(text:String)=TextView(this).apply {
        this.text=text;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,54f*resources.displayMetrics.widthPixels/1080f);gravity=Gravity.CENTER;setTextColor(getColor(R.color.keyboard_text));setPadding(dp(14),0,dp(14),0)
        layoutParams=LinearLayout.LayoutParams(-2,-1);minWidth=dp(44);isClickable=true;isFocusable=true;background=getDrawable(R.drawable.candidate_background)
    }
    private fun syllableOptions()=t9.choices(composer.text,syllables).sortedByDescending { syllable -> candidates.count { it.spelling.replace("'","").drop(t9.lockedDigits).startsWith(syllable) } }
    private fun chooseSyllable(syllable:String) { if(t9.select(syllable,composer.text))refreshCandidates() }
    private fun select(entry:PinyinCandidate) {
        val editor=connection() ?: return
        val input=composer.text
        val consumed=input.take(entry.consumed)
        val allowed=personalize()
        val previous=previousWord
        panels?.hide();cancelPending()
        editor.beginBatchEdit();editor.commitText(entry.text,1);composer.consume(entry.consumed);t9.clear()
        if(composer.text.isNotEmpty())editor.setComposingText(composer.text,1)
        editor.endBatchEdit()
        if(input.isNotEmpty() && entry.spelling!="@en") {
            if(selectedRaw.isEmpty())phrasePrevious=previous
            selectedRaw+=consumed;selectedWord+=entry.text
            val spelling=entry.spelling.split('\'').take(entry.text.length).joinToString("'")
            selectedSpelling=listOf(selectedSpelling,spelling).filter { it.isNotEmpty() }.joinToString("'")
        }
        val learned=entry.copy(spelling=if(entry.spelling=="@en")"@en" else entry.spelling.split('\'').take(entry.text.length).joinToString("'"))
        worker.execute { repository.learn(consumed,learned,previous,allowed && preferences.getBoolean("learning",true)) }
        previousWord=entry.text
        if(composer.text.isNotEmpty()) { refreshCandidates();return }
        val phrase=selectedWord.takeIf { it.isNotEmpty() } ?: entry.text
        if(selectedWord!=entry.text && selectedRaw.isNotEmpty()) {
            val combined=PinyinCandidate(selectedWord,selectedRaw.length,selectedSpelling);val raw=selectedRaw;val before=phrasePrevious
            worker.execute { repository.learn(raw,combined,before,allowed && preferences.getBoolean("learning",true)) }
        }
        selectedRaw="";selectedWord="";selectedSpelling="";phrasePrevious=""
        candidates=emptyList();showCandidates()
        if(chinese && !sensitive && !numeric && !privateInput && emojiSearch==null && engineReady) {
            val ticket=generation
            worker.execute { val result=decoder?.predictions(phrase,allowed).orEmpty();main.post { if(!destroyed && generation==ticket) { candidates=result;showCandidates() } } }
        }
    }
    private fun cancelPending() { generation++;pendingSpace=false;deferredKeys.clear() }
    private fun clearComposition() { composer.clear();t9.clear();candidates=emptyList();selectedRaw="";selectedWord="";selectedSpelling="";phrasePrevious="" }
    private fun flush() {
        cancelPending()
        if(composer.text.isNotEmpty())connection()?.commitText(composer.text,1)
        clearComposition();showCandidates()
    }
    private fun commitLiteral(value:String) {
        if(composer.text.isNotEmpty()) {
            val first=candidates.firstOrNull()
            if(first!=null && first.consumed==composer.text.length)select(first) else flush()
        }
        cancelPending();candidates=emptyList();connection()?.commitText(value,1);previousWord="";showCandidates()
    }
    private fun firstCandidate() {
        if(candidates.isNotEmpty()) { select(candidates.first());if(!chinese)connection()?.commitText(" ",1);return }
        if(!engineReady) { flush();return }
        val ticket=generation;val input=composer.text;val isT9=useT9();val prefix=t9.prefix;val isChinese=chinese;val personal=personalize()
        val correct=preferences.getBoolean("correction",true);val fuzzy=preferences.getBoolean("fuzzy",true)
        pendingSpace=true
        worker.execute {
            val result=if(isChinese)decoder?.chinese(input,isT9,prefix,personal,correct,fuzzy) else decoder?.english(input,personal)
            main.post {
                if(!destroyed && ticket==generation) {
                    val queued=deferredKeys.toList();deferredKeys.clear();pendingSpace=false
                    result?.firstOrNull()?.let { select(it);if(!isChinese)connection()?.commitText(" ",1) } ?: flush()
                    queued.forEach { onKey(it,null) }
                }
            }
        }
    }
    override fun onKey(primaryCode:Int,keyCodes:IntArray?) {
        val editor=connection() ?: return
        if(pendingSpace) { deferredKeys+=primaryCode;return }
        when(primaryCode) {
            -1 -> { shifted=!shifted;binding?.keyboard?.isShifted=shifted;binding?.keyboard?.showLowerCase=!chinese && !shifted;binding?.keyboard?.invalidateAllKeys() }
            -2 -> panels?.symbols()
            -3 -> { flush();numbers=true;panels?.hide();updateKeyboard() }
            -4 -> { numbers=numeric;panels?.hide();updateKeyboard() }
            -10 -> { flush();chinese=!chinese;panels?.hide();updateKeyboard() }
            -102 -> panels?.syllables(syllableOptions()) { chooseSyllable(it) }
            -104 -> { if(composer.text.isNotEmpty())editor.commitText("",1);cancelPending();clearComposition();showCandidates() }
            -101 -> { flush();getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            in -314..-301 -> {
                flush();previousWord=""
                val key=when(primaryCode) { -301 -> KeyEvent.KEYCODE_MOVE_HOME;-303 -> KeyEvent.KEYCODE_MOVE_END;-302,-311 -> KeyEvent.KEYCODE_DPAD_UP;-304,-312 -> KeyEvent.KEYCODE_DPAD_LEFT;-305,-313 -> KeyEvent.KEYCODE_DPAD_DOWN;else -> KeyEvent.KEYCODE_DPAD_RIGHT }
                sendCursor(key,primaryCode<=-311,primaryCode==-301 || primaryCode==-303)
            }
            -5 -> {
                cancelPending()
                if(composer.text.isNotEmpty()) {
                    composer.delete();t9.deleted(composer.text.length)
                    if(composer.text.isEmpty())editor.commitText("",1) else editor.setComposingText(composer.text,1)
                    refreshCandidates()
                } else {
                    candidates=emptyList();previousWord=""
                    if(!editor.getSelectedText(0).isNullOrEmpty())editor.commitText("",1) else editor.deleteSurroundingTextInCodePoints(1,0)
                    showCandidates()
                }
            }
            32 -> if(composer.text.isNotEmpty())firstCandidate() else commitLiteral(" ")
            10 -> {
                if(emojiSearch!=null) { if(composer.text.isNotEmpty())firstCandidate();renderEmojiSearch();return }
                flush();val info=currentInputEditorInfo ?: return
                if(info.actionLabel!=null && info.actionId!=0)editor.performEditorAction(info.actionId) else {
                    val action=info.imeOptions and EditorInfo.IME_MASK_ACTION
                    if(info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION==0 && action !in listOf(EditorInfo.IME_ACTION_NONE,EditorInfo.IME_ACTION_UNSPECIFIED))editor.performEditorAction(action) else editor.commitText("\n",1)
                }
            }
            else -> {
                var char=primaryCode.toChar()
                if(!sensitive && !numeric && !numbers && (char in 'a'..'z' || char=='\'' || useT9() && char in '2'..'9')) {
                    if(chinese)composer.append(char,useT9()) else composer.appendEnglish(if(shifted)char.uppercaseChar() else char)
                    editor.setComposingText(composer.text,1);refreshCandidates()
                } else {
                    if(shifted)char=char.uppercaseChar()
                    if(chinese && !sensitive && !numeric && !numbers)char=when(char) { ',' -> '，';'.' -> '。';'?' -> '？';'!' -> '！';else -> char }
                    commitLiteral(char.toString())
                }
            }
        }
    }
    private fun sendCursor(code:Int,selecting:Boolean=false,ctrl:Boolean=false) {
        val meta=(if(selecting)KeyEvent.META_SHIFT_ON else 0) or (if(ctrl)KeyEvent.META_CTRL_ON else 0)
        val now=SystemClock.uptimeMillis()
        connection()?.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,meta));connection()?.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_UP,code,0,meta))
    }
    private fun captureClipboard() {
        if(!isInputViewShown || sensitive || privateInput || emojiSearch!=null || !preferences.getBoolean("clipboard_history",true) || preferences.getBoolean("clipboard_blocked",false))return
        val manager=getSystemService(ClipboardManager::class.java)
        val description=manager.primaryClipDescription ?: return
        if(description.extras?.getBoolean("android.content.extra.IS_SENSITIVE",false)==true)return
        val text=manager.primaryClip?.takeIf { it.itemCount>0 }?.getItemAt(0)?.text?.toString() ?: return
        val stamp=(if(Build.VERSION.SDK_INT>=26)description.timestamp.toString() else "")+":"+text.hashCode()
        if(preferences.getString("last_clip_signature",null)==stamp)return
        repository.captureClip(text,true);preferences.edit().putString("last_clip_signature",stamp).apply()
    }
    private fun beginEmojiSearch() {
        flush();panels?.hide()
        val view=binding ?: return
        val field=EditText(this)
        searchConnection=object:BaseInputConnection(field,true) { override fun getEditable():Editable=field.text }
        emojiSearch=field
        field.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { renderEmojiSearch() }
            override fun afterTextChanged(s:Editable?)=Unit
        })
        view.emojiSearchBar.visibility=View.VISIBLE;view.emojiResultsScroll.visibility=View.VISIBLE
        updateKeyboard();renderEmojiSearch()
    }
    private fun renderEmojiSearch() {
        val field=emojiSearch ?: return;val view=binding ?: return
        view.emojiSearchQuery.text=field.text.toString().ifEmpty { getString(R.string.emoji_search_hint) }
        view.emojiSearchResults.removeAllViews()
        val results=EmojiCatalog.search(field.text.toString()).take(40)
        if(results.isEmpty()) candidateView(getString(R.string.no_emoji_results)).apply { textSize=13f;view.emojiSearchResults.addView(this) }
        results.forEach { value -> candidateView(value).apply { setOnClickListener { insertEmoji(value) };view.emojiSearchResults.addView(this) } }
    }
    private fun endEmojiSearch() {
        if(emojiSearch==null)return
        searchConnection?.finishComposingText();emojiSearch=null;searchConnection=null;cancelPending();clearComposition()
        binding?.emojiSearchBar?.visibility=View.GONE;binding?.emojiResultsScroll?.visibility=View.GONE
    }
    private fun insertEmoji(value:String) {
        endEmojiSearch();flush();currentInputConnection?.commitText(value,1)
        repository.useEmoji(value,!sensitive && !privateInput && !numeric && preferences.getBoolean("learning",true))
        showCandidates()
    }
    private fun startVoice() {
        if(sensitive || numeric || emojiSearch!=null)return
        flush();val info=currentInputEditorInfo ?: return;voiceEditor=info.packageName to info.fieldId
        VoiceInputResult.listener={ text -> main.post { if(!destroyed) { voiceText=text;deliverVoice() } } }
        startActivity(Intent(this,VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun deliverVoice() {
        val value=voiceText ?: return;val info=currentInputEditorInfo ?: return
        if(voiceEditor==(info.packageName to info.fieldId) && !sensitive && !numeric && isInputViewShown) { currentInputConnection?.commitText(value,1);voiceText=null;voiceEditor=null;VoiceInputResult.listener=null }
    }
    override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        if(emojiSearch==null && composer.text.isNotEmpty() && (newSelStart!=candidatesEnd || newSelEnd!=candidatesEnd)) { cancelPending();clearComposition();currentInputConnection?.finishComposingText();showCandidates() }
    }
    override fun onFinishInput() { binding?.keyboard?.compositionText=""; cancelPending();connection()?.finishComposingText();endEmojiSearch();clearComposition();previousWord="";super.onFinishInput() }
    override fun onDestroy() {
        destroyed=true;cancelPending();VoiceInputResult.listener=null
        getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(clipboardListener)
        binding?.keyboard?.closing();binding=null;panels=null
        worker.execute { NativePinyin.close();userDictionary.delete();repository.close() };worker.shutdown();super.onDestroy()
    }
    override fun onEvaluateFullscreenMode()=false
    override fun onPress(primaryCode:Int) {
        if(preferences.getBoolean("haptic",false))binding?.keyboard?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if(preferences.getBoolean("sound",false))getSystemService(AudioManager::class.java).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }
    override fun onRelease(primaryCode:Int)=Unit
    override fun onText(text:CharSequence?) { text?.let { commitLiteral(it.toString()) } }
    override fun swipeLeft()=Unit
    override fun swipeRight()=Unit
    override fun swipeDown() { requestHideSelf(0) }
    override fun swipeUp()=Unit
}
