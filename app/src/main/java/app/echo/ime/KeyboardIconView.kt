package app.echo.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Original vector drawing for the keyboard controls, sized in a 24-unit grid. */
class KeyboardIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = 22 * resources.displayMetrics.density
        canvas.save()
        canvas.translate((width - size) / 2, (height - size) / 2)
        canvas.scale(size / 24, size / 24)
        paint.color = context.getColor(R.color.keyboard_muted)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        KeyboardGlyph.draw(canvas, paint, tag?.toString().orEmpty())
        canvas.restore()
    }
}

internal object KeyboardGlyph {
    fun draw(c: Canvas, p: Paint, kind: String) {
        fun line(vararg xy: Float) {
            val path = Path(); path.moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i + 1])
            c.drawPath(path, p)
        }
        when (kind) {
            "grid" -> for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) c.drawRoundRect(x,y,x+7,y+7,1.8f,1.8f,p)
            "keyboard", "keyboard_box" -> {
                if (kind == "keyboard_box") c.drawRoundRect(1f,2f,23f,22f,4f,4f,p)
                val old = p.style; p.style = Paint.Style.FILL
                for (x in listOf(5f,10f,15f,20f)) for (y in listOf(7f,12f)) c.drawRoundRect(x-1.2f,y-1.2f,x+1.2f,y+1.2f,.4f,.4f,p)
                p.style = old; line(5f,18f,20f,18f)
            }
            "smile" -> { c.drawCircle(12f,12f,9f,p); c.drawCircle(9f,9f,.5f,p); c.drawCircle(15f,9f,.5f,p); c.drawArc(7f,9f,17f,17f,15f,150f,false,p) }
            "edit" -> { line(9f,3f,15f,3f); line(12f,3f,12f,21f); line(9f,21f,15f,21f); line(5f,8f,2f,12f,5f,16f); line(19f,8f,22f,12f,19f,16f) }
            "mic" -> { c.drawRoundRect(8f,2f,16f,16f,4f,4f,p); c.drawArc(5f,6f,19f,20f,0f,180f,false,p); line(12f,20f,12f,23f) }
            "down" -> line(4f,9f,12f,17f,20f,9f)
            "up" -> line(4f,15f,12f,7f,20f,15f)
            "shift" -> line(4f,12f,12f,3f,20f,12f,15f,12f,15f,22f,9f,22f,9f,12f,4f,12f)
            "delete" -> { line(9f,5f,21f,5f,23f,8f,23f,17f,21f,20f,9f,20f,2f,12f,9f,5f); line(13f,9f,18f,16f); line(18f,9f,13f,16f) }
            "enter" -> { line(22f,6f,22f,13f,20f,15f,2f,15f); line(7f,10f,2f,15f,7f,20f) }
            "space" -> line(2f,15f,2f,19f,22f,19f,22f,15f)
            "clipboard" -> { c.drawRoundRect(2f,2f,19f,20f,2f,2f,p); c.drawRoundRect(7f,7f,23f,23f,2f,2f,p); line(11f,12f,19f,12f); line(11f,17f,19f,17f) }
        }
    }
}
