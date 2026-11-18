package app.echo.ime

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal object VoiceInputResult { var listener: ((String) -> Unit)? = null }

/** Microphone capture belongs to this foreground activity; only final results reach the editor. */
class VoiceInputActivity : Activity(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var retry: Button
    private var listening = false
    private var external = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            setPadding(32,48,32,32); setBackgroundColor(0xFF17181A.toInt())
        }
        status=TextView(this).apply { textSize=22f; setTextColor(0xFFE5E5E7.toInt()); gravity=Gravity.CENTER; text="语音输入" }
        body.addView(status,LinearLayout.LayoutParams(-1,180))
        retry=Button(this).apply { text="开始说话"; setOnClickListener { if(listening) { recognizer?.stopListening();status.text="正在识别…";listening=false } else begin() } }
        body.addView(retry)
        body.addView(Button(this).apply { text="取消";setOnClickListener { finish() } })
        setContentView(body)
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) begin()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==10 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) begin()
        else { status.text="需要麦克风权限才能语音输入";retry.text="重新授权";retry.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10) } }
    }
    private fun begin() {
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) {
            try { external=true; @Suppress("DEPRECATION") startActivityForResult(intent,1) }
            catch (_: android.content.ActivityNotFoundException) { external=false; status.text="手机未安装可用的语音识别服务，请在系统设置中安装并启用中文语音识别服务";retry.text="重试" }
            return
        }
        try {
            recognizer?.destroy()
            recognizer=SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this);it.startListening(intent) }
            listening=true;status.text="正在连接麦克风…";retry.text="说完了"
        } catch (_: RuntimeException) { failed("无法启动语音识别，请重试") }
    }
    private fun failed(message:String) { listening=false;status.text=message;retry.text="重试" }
    override fun onReadyForSpeech(params:Bundle?) { status.text="请说话…" }
    override fun onBeginningOfSpeech() { status.text="正在聆听…" }
    override fun onRmsChanged(rmsdB:Float) = Unit
    override fun onBufferReceived(buffer:ByteArray?) = Unit
    override fun onEndOfSpeech() { listening=false;status.text="正在识别…";retry.text="重试" }
    override fun onError(error:Int) { failed(when(error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不可用"
        SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音服务网络不可用，请连接网络后重试"
        SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "语音服务不支持中文或尚未下载中文模型"
        SpeechRecognizer.ERROR_AUDIO -> "无法录音，请检查麦克风是否被占用"
        else -> "语音识别暂不可用（$error），请重试"
    }) }
    override fun onResults(results:Bundle?) {
        val value=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if(value.isNullOrBlank()) failed("没有听清，请再说一次")
        else { VoiceInputResult.listener?.invoke(value);finish() }
    }
    override fun onPartialResults(partialResults:Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { status.text=it }
    }
    override fun onEvent(eventType:Int, params:Bundle?) = Unit
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1) {
            external=false
            val value=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if(resultCode==RESULT_OK && !value.isNullOrBlank()) { VoiceInputResult.listener?.invoke(value);finish() }
            else failed("语音输入已取消，可点击重试")
        }
    }
    override fun onStop() { super.onStop();if(!external) { recognizer?.cancel(); listening=false;retry.text="重试" } }
    override fun onDestroy() { recognizer?.destroy();recognizer=null;super.onDestroy() }
}
