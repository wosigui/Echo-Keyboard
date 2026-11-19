package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import org.fcitx.fcitx5.android.R

class MicrophonePermissionActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        Toast.makeText(this, R.string.voice_permission_hint, Toast.LENGTH_LONG).show()
        finish()
    }
}
