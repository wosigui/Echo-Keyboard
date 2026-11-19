package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object VoiceInput {
    fun show(service: FcitxInputMethodService, context: Context) {
        val type = service.currentInputEditorInfo?.inputType ?: return
        val variation = type and InputType.TYPE_MASK_VARIATION
        if ((type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)) return
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            service.startActivity(Intent(service, MicrophonePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        service.showDialog(AlertDialog.Builder(context).setTitle(R.string.voice_input)
            .setItems(arrayOf(context.getString(R.string.voice_chinese), context.getString(R.string.voice_english))) { _, index ->
                val name = if (index == 0) "vosk-model-small-cn-0.22" else "vosk-model-small-en-us-0.15"
                val directory = File(service.noBackupFilesDir, "voice/$name")
                if (File(directory, ".ready").exists()) listen(service, context, directory, index == 0)
                else service.showDialog(AlertDialog.Builder(context).setTitle(R.string.voice_input)
                    .setMessage(R.string.voice_download_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ -> listen(service, context, directory, index == 0) }
                    .setNegativeButton(android.R.string.cancel, null).create())
            }.setNegativeButton(android.R.string.cancel, null).create())
    }

    private fun listen(service: FcitxInputMethodService, context: Context, directory: File, chinese: Boolean) {
        val editor = service.currentInputConnection ?: return
        val preview = EditText(context).apply {
            inputType = InputType.TYPE_NULL
            setText(R.string.voice_loading)
            setPadding(32, 24, 32, 24)
        }
        var model: Model? = null
        var recognizer: Recognizer? = null
        var speech: SpeechService? = null
        var job: Job? = null
        var active = true
        var completed = ""
        val dialog = AlertDialog.Builder(context).setTitle(R.string.voice_input).setView(preview)
            .setPositiveButton(R.string.voice_finish, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        fun clean(text: String) = if (chinese) text.replace(" ", "") else text
        fun update(text: String) { preview.setText(completed + clean(text)) }
        service.showDialog(dialog) {
            active = false
            job?.cancel()
            speech?.stop()
            speech?.shutdown()
            recognizer?.close()
            model?.close()
        }
        val finish = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        finish.isEnabled = false
        finish.setOnClickListener {
            speech?.stop() // final callback includes the unfinished utterance
        }
        job = service.lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    if (!File(directory, ".ready").exists()) {
                        directory.mkdirs()
                        val connection = URL("https://alphacephei.com/vosk/models/${directory.name}.zip").openConnection().apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                        }
                        ZipInputStream(connection.getInputStream().buffered()).use { zip ->
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val entry = zip.nextEntry ?: break
                                val relative = entry.name.removePrefix(directory.name + "/")
                                if (relative.isEmpty()) continue
                                val target = File(directory, relative)
                                require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                                if (entry.isDirectory) target.mkdirs() else {
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            ensureActive()
                                            val count = zip.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            require(total <= 300L * 1024 * 1024)
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                            }
                        }
                        File(directory, ".ready").writeText("1")
                    }
                    // Transfer ownership on the main thread, including cancellation.
                    Model(directory.absolutePath).also { loaded ->
                        service.lifecycleScope.launch(Dispatchers.Main.immediate) {
                            if (active) model = loaded else loaded.close()
                        }
                    }
                }
                if (!active) return@launch
                model = loaded
                recognizer = Recognizer(loaded, 16000f)
                speech = SpeechService(recognizer, 16000f)
                preview.setText(R.string.voice_listening)
                check(speech!!.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String) { if (active) update(JSONObject(hypothesis).optString("partial")) }
                    override fun onResult(hypothesis: String) {
                        if (!active) return
                        val text = clean(JSONObject(hypothesis).optString("text"))
                        if (text.isNotBlank()) completed += text + if (chinese) "" else " "
                        update("")
                    }
                    override fun onFinalResult(hypothesis: String) {
                        if (!active) return
                        val text = (completed + clean(JSONObject(hypothesis).optString("text"))).trim()
                        if (text.isNotBlank() && service.currentInputConnection === editor) {
                            editor.finishComposingText()
                            service.commitText(text)
                        }
                        dialog.dismiss()
                    }
                    override fun onError(error: Exception) {
                        if (active) { preview.setText(R.string.voice_error); finish.isEnabled = false; speech?.shutdown() }
                    }
                    override fun onTimeout() { if (active) speech?.stop() }
                }, 60000))
                finish.isEnabled = true
            } catch (error: Exception) {
                if (active) { preview.setText(R.string.voice_error); finish.isEnabled = false }
            }
        }
    }
}
