package app.echo.ime

import android.content.Context
import app.echo.pinyin.NativePinyin
import app.echo.pinyin.PinyinCandidate

class EnglishLexicon(words: List<String>) {
    private val entries = words.map { it.lowercase() }.filter { it.matches(Regex("[a-z]{2,24}")) }.distinct()
    private val prefixes = entries.groupBy { it.take(3) }
    fun complete(input: String): List<String> {
        if(input.isEmpty()) return emptyList()
        val lower=input.lowercase()
        val source=if(lower.length>=3) prefixes[lower.take(3)].orEmpty() else entries
        return source.asSequence().filter { it.startsWith(lower) }.take(12).map { matchCase(input,it) }.toList()
    }
    companion object {
        fun matchCase(input: String, value: String) = when {
            input.length>1 && input.all { it.isUpperCase() } -> value.uppercase()
            input.firstOrNull()?.isUpperCase()==true -> value.replaceFirstChar { it.uppercase() }
            else -> value
        }
    }
}

/** Accessed only by the input service's single worker. */
class InputDecoder(context: Context, private val repository: InputRepository) {
    private val english=EnglishLexicon(context.assets.open("en_50k.txt").bufferedReader().useLines { lines -> lines.map { it.substringBefore(' ') }.take(35000).toList() })
    val syllables=NativePinyin.syllables().toList()
    private val fullSyllables=syllables.toSet()
    private val cache=object : LinkedHashMap<String,List<PinyinCandidate>>(64,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,List<PinyinCandidate>>?) = size>96
    }
    fun chinese(input: String,t9: Boolean,locked: String="",personalize: Boolean=true,correct: Boolean=true,fuzzy: Boolean=true): List<PinyinCandidate> {
        if(input.isEmpty()) return emptyList()
        val key="$input|$t9|$locked|$correct|$fuzzy"
        val baseline=cache.getOrPut(key) {
            val exact=(if(t9)NativePinyin.queryT9(input,locked) else NativePinyin.query(input,false)).map(PinyinCandidate::decode)
            val corrections=if(!t9 && (correct || fuzzy)) PinyinCorrections.variants(input,fuzzy,correct).flatMap { variant ->
                NativePinyin.query(variant,false).map(PinyinCandidate::decode)
                    .filter { it.consumed==variant.length && it.text.length>=2 && it.spelling.split('\'').all(fullSyllables::contains) }
                    .take(1).map { it.copy(consumed=input.length,corrected=true) }
            }.distinctBy { it.text }.take(8) else emptyList()
            val complete=exact.filter { it.consumed==input.length }
            val partial=exact.filter { it.consumed!=input.length }
            (complete.take(4)+corrections+complete.drop(4)+partial).distinctBy { it.text }.take(60)
        }
        val personal=if(personalize) repository.words(input,locked) else emptyList()
        return (personal+baseline).distinctBy { it.text }.take(60)
    }
    fun english(input: String,personalize: Boolean): List<PinyinCandidate> {
        val personal=if(personalize) repository.english(input).map { EnglishLexicon.matchCase(input,it) } else emptyList()
        return (personal+english.complete(input)).distinct().map { PinyinCandidate(it,input.length,"@en") }
    }
    fun predictions(word: String,personalize: Boolean): List<PinyinCandidate> {
        val learned=if(personalize) repository.next(word) else emptyList()
        return (learned+commonContinuations[word].orEmpty()+NativePinyin.predict(word).toList()).distinct().take(30).map { PinyinCandidate(it,0) }
    }
    companion object {
        private val commonContinuations=mapOf(
            "今天" to listOf("天气","下午","晚上","早上"),"明天" to listOf("见","早上","下午"),
            "天气" to listOf("很好","不错","预报"),"北京" to listOf("大学","欢迎你"),
            "谢谢" to listOf("你","大家"),"你好" to listOf("呀","朋友"),"生日" to listOf("快乐"),
            "工作" to listOf("顺利","完成"),"周末" to listOf("愉快","快乐"),"新年" to listOf("快乐"),
            "晚上" to listOf("好","见"),"早上" to listOf("好"),"中国" to listOf("人民","发展"),
            "辛苦" to listOf("了"),"学习" to listOf("进步","计划"),"注意" to listOf("安全","休息")
        )
    }
}
