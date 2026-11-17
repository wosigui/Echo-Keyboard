package app.echo.ime

/** Composition state shared by full spelling and T9. Conversion lives in AOSP. */
class PinyinComposer {
    var text: String = ""
        private set
    fun append(char: Char, t9: Boolean = false) {
        if (text.length < 32 && (char in 'a'..'z' || char == '\'' || t9 && char in '2'..'9')) text += char
    }
    fun consume(length: Int) { text = text.drop(length.coerceIn(0, text.length)) }
    fun appendEnglish(char: Char) { if(text.length<32 && (char in 'a'..'z' || char in 'A'..'Z' || char=='\'')) text+=char }
    fun delete() { text = text.dropLast(1) }
    fun clear() { text = "" }
}
