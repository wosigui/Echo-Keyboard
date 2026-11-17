package app.echo.ime

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainEditorTest {
    @Test fun editorVisibleAboveKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(R.id.test_input)
                input.requestFocus(); input.performClick()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(1000)
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(R.id.test_input)
                val root = activity.findViewById<ScrollView>(R.id.root)
                val frame = Rect(); activity.window.decorView.getWindowVisibleDisplayFrame(frame)
                val position = IntArray(2); input.getLocationOnScreen(position)
                Log.i("EchoViewport","scroll=${root.scrollY} padding=${root.paddingBottom} height=${root.height} child=${root.getChildAt(0).height} field=${input.top} position=${position[1]} frame=$frame")
                assertTrue("Test editor must remain visible when keyboard opens", position[1] + input.height <= frame.bottom)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun composingKeepsKeyboardHeightCandidatesAndEditorPositionStable() {
        assumeTrue(Build.VERSION.SDK_INT>=29)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(context.packageName+"/")==true)
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val input=activity.findViewById<EditText>(R.id.test_input)
                input.requestFocus();input.performClick()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
            }
            waitUntil { keyboard()!=null }
            SystemClock.sleep(500)
            for((layout,letters) in listOf(R.string.layout_full_title to "ni",R.string.layout_nine_title to "64")) {
                instrumentation.runOnMainSync {
                    val root=keyboard()!!.rootView
                    root.findViewById<View>(R.id.layout_toggle).performClick()
                    findText(root.findViewById(R.id.panel),context.getString(layout))!!.performClick()
                    activity.findViewById<EditText>(R.id.test_input).setText("")
                }
                instrumentation.waitForIdleSync()
                var height=0
                instrumentation.runOnMainSync {
                    height=keyboard()!!.rootView.findViewById<View>(R.id.keyboard_content).height
                    press(letters[0])
                }
                waitUntil { keyboard()!!.rootView.findViewById<LinearLayout>(R.id.candidates).childCount>1 }
                instrumentation.runOnMainSync {
                    val root=keyboard()!!.rootView
                    assertEquals("First letter must not resize the keyboard",height,root.findViewById<View>(R.id.keyboard_content).height)
                    val row=root.findViewById<LinearLayout>(R.id.candidates)
                    val previous=row.getChildAt(0)
                    press(letters[1])
                    assertSame("Keep the visible candidates while decoding",previous,row.getChildAt(0))
                    previous.performClick()
                    assertEquals("A stale candidate must not commit",letters,activity.findViewById<EditText>(R.id.test_input).text.toString())
                }
                waitUntil { keyboard()!!.rootView.findViewById<LinearLayout>(R.id.candidates).childCount>1 && keyboard()!!.rootView.findViewById<TextView>(R.id.composition).text.toString()==letters }
                instrumentation.waitForIdleSync()
                var scroll=0
                instrumentation.runOnMainSync {
                    assertEquals(height,keyboard()!!.rootView.findViewById<View>(R.id.keyboard_content).height)
                    val root=activity.findViewById<ScrollView>(R.id.root)
                    root.scrollBy(0,12)
                    scroll=root.scrollY
                    ViewCompat.dispatchApplyWindowInsets(root,ViewCompat.getRootWindowInsets(root)!!)
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals("Repeated insets must not force the editor to scroll",scroll,activity.findViewById<ScrollView>(R.id.root).scrollY)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                keyboard()?.rootView?.let { root ->
                    root.findViewById<View>(R.id.layout_toggle).performClick()
                    findText(root.findViewById(R.id.panel),context.getString(R.string.layout_full_title))?.performClick()
                }
                activity.finish()
            }
        }
    }

    private fun keyboard(): EchoKeyboardView? = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { it.findViewById<EchoKeyboardView>(R.id.keyboard)?.takeIf { keyboard -> keyboard.isShown } }

    private fun press(letter: Char) {
        val keyboard=keyboard()!!
        val key=keyboard.keyboard.keys.first { it.codes.first()==letter.code }
        val horizontal=(key.x+key.width/2+keyboard.paddingLeft).toFloat()
        val vertical=(key.y+key.height/2+keyboard.paddingTop).toFloat()
        val now=SystemClock.uptimeMillis()
        val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,horizontal,vertical,0)
        val up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,horizontal,vertical,0)
        keyboard.dispatchTouchEvent(down);keyboard.dispatchTouchEvent(up)
        down.recycle();up.recycle()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val deadline=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<deadline) {
            var ready=false
            instrumentation.runOnMainSync { ready=condition() }
            if(ready)return
            SystemClock.sleep(50)
        }
        fail("Keyboard did not reach the expected state")
    }

    private fun findText(root: View,label: String): View? {
        if(root is TextView && root.text.toString()==label)return root
        if(root is ViewGroup)for(index in 0 until root.childCount)findText(root.getChildAt(index),label)?.let { return it }
        return null
    }
}
