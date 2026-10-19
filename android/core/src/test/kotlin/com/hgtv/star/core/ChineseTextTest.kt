// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseTextTest {
    @Test fun simplifiedBecomesTaiwanTraditionalWithLocalTerms() {
        assertEquals("影片設定", ChineseText.toTraditional("视频设置"))
        assertEquals("預設清晰度", ChineseText.toTraditional("默认清晰度"))
        assertEquals("搜尋", ChineseText.toTraditional("搜索"))
        assertEquals("霸道總裁愛上我", ChineseText.toTraditional("霸道总裁爱上我"))
    }

    @Test fun phrasesBeatSingleCharacters() {
        // 「后」單字會變「後」，但「皇后」整個詞在詞典裡，必須保持原樣。
        assertEquals("皇后", ChineseText.toTraditional("皇后"))
        assertEquals("頭髮", ChineseText.toTraditional("头发"))
        // 「这里」→「這裏」→ 臺灣異體字「這裡」：兩步串起來。
        assertEquals("這裡", ChineseText.toTraditional("这里"))
    }

    @Test fun nonChineseTextIsUntouched() {
        assertEquals("HGTV 1080P · 2.0×", ChineseText.toTraditional("HGTV 1080P · 2.0×"))
        assertEquals("", ChineseText.toTraditional(""))
        assertEquals("第 12 集", ChineseText.toTraditional("第 12 集"))
    }

    @Test fun traditionalSearchTermsGoBackToSimplified() {
        assertEquals("总裁", ChineseText.toSimplified("總裁"))
        assertEquals("视频设置", ChineseText.toSimplified("影片設定"))
        assertEquals("搜索网络", ChineseText.toSimplified("搜尋網路"))
        // 已經是簡體的字串不能被改壞。
        assertEquals("霸道总裁爱上我", ChineseText.toSimplified("霸道总裁爱上我"))
    }

    @Test fun firstDictionaryInGroupWinsLikeOpenCcShortCircuit() {
        val phrases = ChineseText.Dictionary(mapOf("甲乙" to "X"))
        val characters = ChineseText.Dictionary(mapOf("甲" to "1", "乙" to "2", "甲乙丙" to "Y"))
        // 第一本查得到「甲乙」就用它，即使第二本有更長的「甲乙丙」。
        val step = ChineseText.Step(listOf(phrases, characters))
        assertEquals("X丙", step.convert("甲乙丙"))
        // 第一本查不到時才輪到第二本。
        assertEquals("21", step.convert("乙甲"))
    }

    @Test fun parserTakesFirstCandidateAndSkipsComments() {
        val dictionary = ChineseText.Dictionary.parse("# comment\n\n里\t裏 里 哩\n后\t後 后\n")
        assertEquals(2, dictionary.size)
        assertEquals(1 to "裏", dictionary.match("里", 0))
        assertNull(dictionary.match("你", 0))
    }
}
