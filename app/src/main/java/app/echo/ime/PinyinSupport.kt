package app.echo.ime

/** T9 syllables are locked independently from the original digit buffer. */
class T9Composition {
    private val selected = mutableListOf<String>()
    val prefix get() = selected.joinToString("'").let { if(it.isEmpty()) "" else "$it'" }
    val lockedDigits get() = selected.sumOf { it.length }
    fun select(syllable: String, digits: String): Boolean {
        if (syllable.isEmpty() || !digits.drop(lockedDigits).startsWith(encode(syllable))) return false
        selected += syllable
        return true
    }
    fun deleted(newLength: Int) { while(selected.isNotEmpty() && lockedDigits > newLength) selected.removeAt(selected.lastIndex) }
    fun clear() { selected.clear() }
    fun choices(digits: String, syllables: List<String>): List<String> {
        val remaining = digits.drop(lockedDigits)
        if(remaining.isEmpty()) return emptyList()
        return syllables.filter { it.isNotEmpty() && remaining.startsWith(encode(it)) }.distinct().sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }
    companion object {
        fun encode(spelling: String): String = buildString {
            spelling.lowercase().forEach { c ->
                append(when(c) { in 'a'..'c' -> '2'; in 'd'..'f' -> '3'; in 'g'..'i' -> '4'; in 'j'..'l' -> '5'; in 'm'..'o' -> '6'; in 'p'..'s' -> '7'; in 't'..'v' -> '8'; in 'w'..'z' -> '9'; else -> return@forEach })
            }
        }
    }
}

/** Bounded, single-error correction: adjacent swaps, QWERTY neighbours, and fuzzy initials/finals. */
object PinyinCorrections {
    private val neighbours = "qwertyuiop".toList().let { row -> row.mapIndexed { i,c -> c to row.filterIndexed { j,_ -> kotlin.math.abs(i-j)==1 } }.toMap() } +
        "asdfghjkl".toList().let { row -> row.mapIndexed { i,c -> c to row.filterIndexed { j,_ -> kotlin.math.abs(i-j)==1 } }.toMap() } +
        "zxcvbnm".toList().let { row -> row.mapIndexed { i,c -> c to row.filterIndexed { j,_ -> kotlin.math.abs(i-j)==1 } }.toMap() }
    fun variants(input: String, fuzzy: Boolean = true, typos: Boolean = true): List<String> {
        if(input.length !in 2..32 || input.any { it !in 'a'..'z' && it != '\'' }) return emptyList()
        val result = linkedSetOf<String>()
        if(fuzzy) {
            val pairs = listOf("zh" to "z","ch" to "c","sh" to "s","ang" to "an","eng" to "en","ing" to "in","n" to "l")
            for((a,b) in pairs) for((from,to) in listOf(a to b,b to a)) {
                var at = input.indexOf(from)
                while(at>=0) { result += input.replaceRange(at,at+from.length,to); at=input.indexOf(from,at+1) }
            }
        }
        if(typos) {
            for(i in 0 until input.lastIndex) if(input[i] != '\'' && input[i+1] != '\'') {
                val chars=input.toCharArray(); val c=chars[i]; chars[i]=chars[i+1]; chars[i+1]=c; result += String(chars)
            }
            input.forEachIndexed { i,c -> neighbours[c].orEmpty().forEach { replacement -> result += input.replaceRange(i,i+1,replacement.toString()) } }
            for(i in input.indices) if(i>0 && input[i]==input[i-1]) result += input.removeRange(i,i+1)
        }
        result.remove(input)
        return result.take(96)
    }
}
