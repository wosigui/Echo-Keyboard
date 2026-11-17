package app.echo.ime

import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.echo.ime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fun scrollTestInputIntoView() {
            val fieldPosition=IntArray(2)
            val viewportPosition=IntArray(2)
            binding.testInput.getLocationInWindow(fieldPosition)
            binding.root.getLocationInWindow(viewportPosition)
            val top=viewportPosition[1]+binding.root.paddingTop
            val bottom=viewportPosition[1]+binding.root.height-binding.root.paddingBottom
            val offset=when {
                fieldPosition[1]<top -> fieldPosition[1]-top
                fieldPosition[1]+binding.testInput.height>bottom -> fieldPosition[1]+binding.testInput.height-bottom
                else -> 0
            }
            if(offset!=0)binding.root.scrollBy(0,offset)
        }
        fun revealTestInput() {
            binding.root.postDelayed({ scrollTestInputIntoView() }, 300)
        }
        binding.testInput.setOnClickListener { revealTestInput() }
        binding.testInput.setOnFocusChangeListener { _, focused -> if (focused) revealTestInput() }
        var keyboardVisible=false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom=maxOf(bars.bottom,ime.bottom)
            val paddingChanged=view.paddingLeft!=bars.left || view.paddingTop!=bars.top || view.paddingRight!=bars.right || view.paddingBottom!=bottom
            val visible=insets.isVisible(WindowInsetsCompat.Type.ime())
            if(paddingChanged)view.setPadding(bars.left,bars.top,bars.right,bottom)
            if (visible && (!keyboardVisible || paddingChanged)) {
                view.post {
                    if (binding.testInput.hasFocus()) scrollTestInputIntoView()
                }
            }
            keyboardVisible=visible
            insets
        }
        binding.enable.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        binding.choose.setOnClickListener {
            if (isEchoEnabled()) {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            } else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.enable_required)
                    .setPositiveButton(R.string.open_keyboard_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
    private fun isEchoEnabled(): Boolean =
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.any {
            it.component == ComponentName(this, EchoInputMethodService::class.java)
        }

    override fun onResume() {
        super.onResume()
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        binding.status.setText(when {
            !isEchoEnabled() -> R.string.pending
            ComponentName.unflattenFromString(selected.orEmpty()) == ComponentName(this, EchoInputMethodService::class.java) -> R.string.ready
            else -> R.string.enabled_not_selected
        })
    }
}
