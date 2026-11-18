@file:Suppress("DEPRECATION")
package app.echo.ime

import android.content.Context
import android.inputmethodservice.Keyboard

/** Resizes both drawing and hit testing; no layout-only scaling of touch targets. */
class SizedKeyboard(context: Context, xml: Int, width: Int, rowHeight: Int, referenceWidth: Int = 0) : Keyboard(context,xml,0,width,0) {
    private val scaledHeight: Int
    init {
        val scale=if(referenceWidth>0) (163f*referenceWidth/1080f)*(rowHeight/52f)/(52f*context.resources.displayMetrics.density) else rowHeight/52f
        scaledHeight=(super.getHeight()*scale).toInt()
        keys.forEach { it.y=(it.y*scale).toInt(); it.height=(it.height*scale).toInt() }
    }
    // Reference screenshots: 8 px outer cell inset, four 163 px rows at 1080 px width.
    init {
        if(referenceWidth>0 && xml==R.xml.letters) {
            val unit=referenceWidth/1080f
            val rows=keys.groupBy { it.y }.values.toList()
            val starts=listOf(
                FloatArray(11) { 8f+it*106.4f },
                FloatArray(10) { 63f+it*106.4f },
                floatArrayOf(8f,169f,276f,383f,490f,597f,704f,811f,925f,1072f),
                floatArrayOf(8f,169f,292f,399f,680f,788f,912f,1072f)
            )
            rows.forEachIndexed { row, cells -> cells.forEachIndexed { col, key ->
                key.x=(starts[row][col]*unit).toInt()
                key.width=((starts[row][col+1] - if(row==2 && col==0) 15f else 0f)*unit).toInt()-key.x
            } }
        }
    }
    override fun getHeight() = scaledHeight
    override fun getNearestKeys(x: Int,y: Int): IntArray = keys.indices.toList().toIntArray()
}
