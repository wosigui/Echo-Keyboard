package app.echo.pinyin

/** JNI entry points for the Apache-2.0 AOSP Pinyin decoder. Called on one worker. */
object NativePinyin {
    init { System.loadLibrary("echo_pinyin") }
    external fun open(dictionary: String, userDictionary: String): Boolean
    external fun query(input: String, t9: Boolean): Array<String>
    external fun queryT9(input: String, lockedSyllables: String): Array<String>
    external fun syllables(): Array<String>
    external fun predict(history: String): Array<String>
    external fun close()
}

data class PinyinCandidate(val text: String, val consumed: Int, val spelling: String = "", val corrected: Boolean = false) {
    companion object {
        fun decode(row: String): PinyinCandidate {
            val parts = row.split('\t', limit = 3)
            return PinyinCandidate(parts[2], parts[1].toInt(), parts[0])
        }
    }
}
