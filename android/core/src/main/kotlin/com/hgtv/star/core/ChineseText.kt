// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import java.io.IOException

/**
 * 繁簡轉換：OpenCC 詞典的 Kotlin 實作（橘子版新增）。
 *
 * 詞典檔放在 `core/src/main/resources/opencc/`，原樣取自 OpenCC ver.1.4.2（Apache-2.0，
 * 授權全文在同一個資料夾的 `LICENSE.txt`）。轉換規則照抄 OpenCC 自己的設定檔：
 *
 * - [toTraditional] ＝ `s2twp.json`：簡體 → 臺灣正體，並換成臺灣慣用詞（视频 → 影片、设置 → 設定）；
 * - [toSimplified] ＝ `tw2sp.json`：反方向，把使用者輸入的繁體搜尋詞轉回平台認得的簡體。
 *
 * 每一步是一組詞典，比對方式與 OpenCC 的 `short_circuit` 相同：在目前位置依序問組裡每一本詞典，
 * **第一本**查得到的就用它最長的那個詞；全都查不到就照抄這個字，往下一個字走。
 *
 * OpenCC 另外有兩本建置時才產生的詞典（`STPhrases_GeneratedFromRegionalPhrases`、
 * `TSCharactersExt`）沒有文字檔可取，這裡不收；少了它們只影響極少數罕用詞。
 */
object ChineseText {

    /** 一本詞典：詞 → 第一個候選詞；另外記下「以某個字開頭的詞最長幾個字」，比對時少試幾次。 */
    class Dictionary(entries: Map<String, String>) {
        private val map = HashMap(entries)
        private val longest = HashMap<Char, Int>().also { lengths ->
            map.keys.forEach { key -> if (key.isNotEmpty()) lengths.merge(key[0], key.length, ::maxOf) }
        }
        val size get() = map.size

        /** 從 [start] 起最長的那個詞：回傳（詞長, 換成什麼），查不到回傳 null。 */
        fun match(text: String, start: Int): Pair<Int, String>? {
            val max = minOf(longest[text[start]] ?: return null, text.length - start)
            for (length in max downTo 1) {
                map[text.substring(start, start + length)]?.let { return length to it }
            }
            return null
        }

        fun reversed(): Dictionary = Dictionary(map.entries.associate { (key, value) -> value to key })

        companion object {
            /** 讀 OpenCC 的文字詞典：每行「詞<Tab>候選1 候選2…」，`#` 開頭是註解。 */
            fun parse(text: String): Dictionary {
                val entries = LinkedHashMap<String, String>()
                text.lineSequence().forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val first = line.substring(tab + 1).trim().substringBefore(' ')
                    if (first.isNotEmpty()) entries.putIfAbsent(line.substring(0, tab), first)
                }
                return Dictionary(entries)
            }

            fun load(name: String): Dictionary {
                val stream = ChineseText::class.java.getResourceAsStream("/opencc/$name.txt")
                    ?: throw IOException("找不到繁簡詞典 $name")
                return stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
            }
        }
    }

    /** 轉換的一步：一組依序比對的詞典。 */
    class Step(private val group: List<Dictionary>) {
        fun convert(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                // 英數、標點與空白不會在詞典裡，直接照抄，長字串（簡介）因此快很多。
                val hit = if (c.code < 0x2E80) null else group.firstNotNullOfOrNull { it.match(text, i) }
                if (hit == null) {
                    out.append(c); i++
                } else {
                    out.append(hit.second); i += hit.first
                }
            }
            return out.toString()
        }
    }

    private val toTaiwan: List<Step> by lazy {
        listOf(
            Step(listOf(Dictionary.load("STPhrases"), Dictionary.load("STCharacters"))),
            Step(listOf(Dictionary.load("TWPhrases"), Dictionary.load("TWVariantsPhrases"), Dictionary.load("TWVariants")))
        )
    }

    private val fromTaiwan: List<Step> by lazy {
        listOf(
            Step(listOf(Dictionary.load("TWPhrasesRev"), Dictionary.load("TWVariantsRevPhrases"), Dictionary.load("TWVariants").reversed())),
            Step(listOf(Dictionary.load("TSPhrases"), Dictionary.load("TSCharacters")))
        )
    }

    /** 介面上同樣的字串（劇名、按鈕）會一再出現：記住最近轉過的，不必每次重算。 */
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 2048
    }

    private fun hasCjk(text: String) = text.any { it.code >= 0x2E80 }

    /** 簡體 → 臺灣正體（含臺灣慣用詞）。沒有中文的字串原樣回傳。 */
    fun toTraditional(text: String): String {
        if (!hasCjk(text)) return text
        synchronized(cache) { cache[text] }?.let { return it }
        val converted = toTaiwan.fold(text) { acc, step -> step.convert(acc) }
        synchronized(cache) { cache[text] = converted }
        return converted
    }

    /** 臺灣正體 → 簡體（含大陸慣用詞），給搜尋用：使用者打「總裁」，送出去的是「总裁」。 */
    fun toSimplified(text: String): String {
        if (!hasCjk(text)) return text
        return fromTaiwan.fold(text) { acc, step -> step.convert(acc) }
    }

    /** 在背景執行緒先把詞典讀進來，第一個畫面不必等。 */
    fun preload() {
        toTaiwan.size
        fromTaiwan.size
    }
}
