@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package app.echo.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Gravity
import android.widget.PopupWindow
import android.widget.TextView
import android.graphics.drawable.GradientDrawable

class EchoKeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : KeyboardView(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density get() = width.takeIf { it>0 }?.div(360f) ?: resources.displayMetrics.density
    var segmentation = false
    private var compositionBubble: PopupWindow? = null
    var compositionText: String = ""
        set(value) {
            field=value
            compositionBubble?.dismiss();compositionBubble=null
            if(value.isNotEmpty()) post {
                if(!isShown || compositionText!=value) return@post
                val label=TextView(context).apply {
                    text=value;gravity=Gravity.CENTER
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,50f*density/3f)
                    setTextColor(color(R.color.keyboard_text))
                    background=GradientDrawable().apply { setColor(color(R.color.keyboard_background));cornerRadius=4*density;setStroke(1,color(R.color.key_shadow)) }
                }
                val position=IntArray(2);getLocationOnScreen(position)
                compositionBubble=PopupWindow(label,((value.length*27+97)*density/3).toInt(),(70*density/3).toInt(),false).apply {
                    isTouchable=false;isClippingEnabled=false
                    showAtLocation(this@EchoKeyboardView,Gravity.NO_GRAVITY,position[0],position[1]-(191*density/3).toInt())
                }
            }
        }
    var onAlternateText: ((String) -> Unit)? = null
    var onVoice: (() -> Unit)? = null
    var onCursorMove: ((Int) -> Unit)? = null
    var keyBubbleEnabled = true
    var showLowerCase = false
    private var spaceDown = false
    private var dragging = false
    private var downX = 0f
    private var cursorX = 0f
    private var preview: PopupWindow? = null
    private val dismissPreview = Runnable { preview?.dismiss(); preview=null }
    private fun color(id: Int) = context.getColor(id)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            downX=event.x;cursorX=event.x;dragging=false
            val key=keyboard?.keys?.firstOrNull { event.x-paddingLeft>=it.x && event.x-paddingLeft<it.x+it.width && event.y-paddingTop>=it.y && event.y-paddingTop<it.y+it.height }
            spaceDown=key?.codes?.firstOrNull()==32
            if(key!=null && key.codes[0]>32 && key.codes[0]!=10) showPreview(key)
        }
        if(event.actionMasked==MotionEvent.ACTION_MOVE && spaceDown && kotlin.math.abs(event.x-downX)>=8*density) {
            if(!dragging) {
                dragging=true
                val cancel=MotionEvent.obtain(event);cancel.action=MotionEvent.ACTION_CANCEL;super.onTouchEvent(cancel);cancel.recycle()
                dismissPreview.run()
            }
            val steps=((event.x-cursorX)/(8*density)).toInt()
            if(steps!=0) { cursorX+=steps*8*density;onCursorMove?.invoke(steps) }
            return true
        }
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) {
            removeCallbacks(dismissPreview);postDelayed(dismissPreview,70)
            spaceDown=false
            if(dragging) { dragging=false;return true }
        }
        return if(dragging) true else super.onTouchEvent(event)
    }
    private fun showPreview(key: Keyboard.Key) {
        if(!keyBubbleEnabled || !isAttachedToWindow || key.label==null) return
        removeCallbacks(dismissPreview);dismissPreview.run()
        val label=TextView(context).apply {
            text=if(showLowerCase) key.label.toString().lowercase() else key.label.toString().uppercase()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,50f*this@EchoKeyboardView.width/1080f);gravity=Gravity.CENTER;setTextColor(color(R.color.keyboard_text))
            background=GradientDrawable().apply { setColor(color(R.color.key_pressed));cornerRadius=8*density }
        }
        val width=(41*density).toInt();val height=(25*density).toInt()
        preview=PopupWindow(label,width,height,false).apply { isTouchable=false;isClippingEnabled=false }
        val position=IntArray(2);getLocationOnScreen(position)
        try { preview?.showAtLocation(this,Gravity.NO_GRAVITY,(position[0]+paddingLeft+key.x+key.width/2-width/2).toInt(),(position[1]+paddingTop+key.y-height-40*density).toInt()) }
        catch (_:android.view.WindowManager.BadTokenException) { dismissPreview.run() }
    }
    override fun onDetachedFromWindow() { compositionBubble?.dismiss();compositionBubble=null; removeCallbacks(dismissPreview);dismissPreview.run();super.onDetachedFromWindow() }

    override fun onLongPress(key: Keyboard.Key): Boolean {
        if (key.codes[0] == 32) { onVoice?.invoke(); return true }
        val hint = hints[key.codes[0].toChar()]
        if (hint != null) { onAlternateText?.invoke(hint); return true }
        return super.onLongPress(key)
    }
    override fun onDraw(canvas: Canvas) {
        val board = keyboard ?: return
        canvas.save(); canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        for (key in board.keys) {
            val code = key.codes.firstOrNull() ?: continue
            val special = code < 0 || code == 10
            rect.set(key.x + 2.33f*density, key.y + 3.33f*density, key.x + key.width - 2.33f*density, key.y + key.height - 6*density)
            paint.style = Paint.Style.FILL
            paint.color = color(when {
                key.pressed || code == -1 && isShifted -> R.color.key_pressed
                special -> R.color.key_secondary
                else -> R.color.key_surface
            })
            canvas.drawRoundRect(rect, 6.33f*density, 6.33f*density, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .5f*density
            paint.color = color(R.color.key_shadow)
            canvas.drawRoundRect(rect, 6.33f*density, 6.33f*density, paint)
            var label = key.label?.toString().orEmpty()
            val letter = code.toChar() in 'a'..'z' && label.length == 1
            val t9 = code in 50..57 && label.length >= 3
            if (letter) label = if(showLowerCase) label.lowercase() else label.uppercase()
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.style = Paint.Style.FILL
            if (letter || t9) {
                paint.textSize = 10*density; paint.color = color(R.color.key_hint)
                canvas.drawText(if (t9) (code - 48).toString() else hints[code.toChar()].orEmpty(), rect.centerX(), rect.top+12*density, paint)
            }
            val glyph = when (code) { -1 -> if(segmentation) null else "shift"; -5 -> "delete"; 32 -> "space"; 10 -> if (label == "↵") "enter" else null; else -> null }
            if(code == -1 && segmentation) label="分词"
            if (code == -10 && label=="中/英") {
                paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.LEFT
                paint.textSize=17*density;paint.color=color(R.color.keyboard_text)
                canvas.drawText("中",rect.centerX()-12*density,rect.centerY()+6*density,paint)
                paint.textSize=9*density;paint.color=color(R.color.key_hint)
                canvas.drawText("/英",rect.centerX()+4*density,rect.centerY()+5*density,paint)
            } else if (glyph != null) {
                val size = (if (code == 32) 33 else if(code==10) 32 else 24) * density
                canvas.save(); canvas.translate(rect.centerX()-size/2, rect.centerY()-size/2 + if (code == 32) 4*density else 0f); canvas.scale(size/24,size/24)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = color(R.color.keyboard_text)
                KeyboardGlyph.draw(canvas,paint,glyph); canvas.restore()
                if (code == 32) {
                    val s = 13*density; canvas.save(); canvas.translate(rect.centerX()-s/2, rect.top+11*density); canvas.scale(s/24,s/24)
                    paint.color = color(R.color.key_hint); paint.strokeWidth = 1.6f; KeyboardGlyph.draw(canvas,paint,"mic"); canvas.restore()
                }
            } else {
                paint.style = Paint.Style.FILL; paint.color = color(R.color.keyboard_text)
                paint.textSize = (if (letter) 19f else if(t9) 21f else if(label.length>2) 13f else 18f)*density
                val available = rect.width()-4*density
                if (paint.measureText(label)>available && available>0) paint.textSize *= available/paint.measureText(label)
                canvas.drawText(label,rect.centerX(),rect.centerY()+(if(letter || t9) 3*density else 0f)-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        canvas.restore()
    }
    companion object {
        private val hints = "qwertyuiopasdfghjklzxcvbnm".toList().zip(listOf("1","2","3","4","5","6","7","8","9","0","~","!","@","#","%","“","”","*","?","(",")","-","_",":",";","、")).toMap()
    }
}
