@file:Suppress("DEPRECATION")
package app.echo.ime

import android.content.Context
import android.inputmethodservice.Keyboard

/** Resizes both drawing and hit testing; no layout-only scaling of touch targets. */
class SizedKeyboard(context: Context, xml: Int, width: Int, rowHeight: Int) : Keyboard(context,xml,0,width,0) {
    private val scaledHeight: Int
    init {
        val scale=rowHeight/52f
        scaledHeight=(super.getHeight()*scale).toInt()
        keys.forEach { it.y=(it.y*scale).toInt(); it.height=(it.height*scale).toInt() }
    }
    override fun getHeight() = scaledHeight
    override fun getNearestKeys(x: Int,y: Int): IntArray = keys.indices.toList().toIntArray()
}
