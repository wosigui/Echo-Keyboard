package app.echo.ime

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

internal object VoiceInputResult { var listener: ((String) -> Unit)? = null }

/** Delegates capture and recognition to a user-installed system speech service. */
class VoiceInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT,getString(R.string.voice_prompt))
            },1)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this,R.string.voice_unavailable,Toast.LENGTH_LONG).show(); finish()
        }
    }
    @Deprecated("Activity result compatibility with minSdk 24")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1 && resultCode==RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { VoiceInputResult.listener?.invoke(it) }
        finish()
    }
}
