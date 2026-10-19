// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import com.hgtv.star.core.ChineseText

/**
 * 介面語言（橘子版新增）：繁體（臺灣用語）或簡體，預設繁體。
 *
 * 程式碼裡的介面文字與平台給的劇名、簡介都是簡體；這裡**只在顯示的那一刻**轉成繁體，
 * 送給平台的資料（分類名、搜尋詞、劇集 id）一律不動，因此不會因為改了字而查不到東西。
 *
 * 顯示的入口只有兩個：
 * - [ZhTextView]：全站的文字控件（`tvText`、`TvButton`、選集格…）都是它，`text = …` 時自動轉；
 * - [ui]：畫在 Canvas 上的字（彈幕）與輸入框的提示文字，呼叫端自己轉。
 *
 * 語言是**整台裝置**的設定，不跟著使用者檔案走，存在 `orange-device-v1`。
 */
internal object Zh {
    private const val PREFS = "orange-device-v1"
    private const val KEY = "language"
    const val TRADITIONAL = "zh-TW"
    const val SIMPLIFIED = "zh-CN"

    @Volatile var traditional = true
        private set

    /** Activity 建立時呼叫一次：讀設定，並在背景先把詞典讀進來。 */
    fun init(context: Context) {
        traditional = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, TRADITIONAL) != SIMPLIFIED
        if (traditional) Thread({ runCatching { ChineseText.preload() } }, "hgtv-opencc").start()
    }

    /** 改語言；呼叫端接著重建畫面（`recreate()`），所有文字重新轉一次。 */
    fun set(context: Context, useTraditional: Boolean) {
        traditional = useTraditional
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, if (useTraditional) TRADITIONAL else SIMPLIFIED).apply()
    }

    val label get() = if (traditional) "繁体中文" else "简体中文"

    /** 顯示用：繁體模式把簡體轉成繁體；帶樣式的文字（Spanned）原樣保留，免得樣式被洗掉。 */
    fun ui(text: CharSequence?): CharSequence? = when {
        !traditional || text == null || text is Spanned -> text
        else -> ChineseText.toTraditional(text.toString())
    }

    fun ui(text: String): String = if (traditional) ChineseText.toTraditional(text) else text

    /**
     * 送給平台的搜尋詞：不論介面語言，一律轉成簡體。
     * 使用者在電視或手機上打「總裁」，平台收到的是「总裁」。
     */
    fun query(text: String): String = ChineseText.toSimplified(text.trim())
}

/** 會自動轉繁簡的文字控件。全站所有顯示文字的 TextView 都從這裡來。 */
internal open class ZhTextView(context: Context) : TextView(context) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(Zh.ui(text), type)
    }

    override fun setContentDescription(contentDescription: CharSequence?) {
        super.setContentDescription(Zh.ui(contentDescription))
    }
}
