// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.media3.common.C
import androidx.media3.common.Player
import com.hgtv.star.core.AppUpdate
import com.hgtv.star.core.PhoneSearchServer
import com.hgtv.star.core.PlaybackQuality
import com.hgtv.star.core.PlaybackSpeed
import com.hgtv.star.core.ProfileList
import com.hgtv.star.core.SeriesSettings
import com.hgtv.star.core.UpdateChannel
import com.hgtv.star.core.UpdateSource
import com.hgtv.star.core.watchDuration
import com.hgtv.star.data.Profiles
import com.hgtv.star.player.PlaybackMenu
import com.hgtv.star.tv.ButtonSize
import com.hgtv.star.tv.ButtonTone
import com.hgtv.star.tv.TvButton
import com.hgtv.star.tv.TvDialog
import com.hgtv.star.tv.TvStyle
import com.hgtv.star.tv.Zh

// 橘子版新增的功能都集中在這個檔案（與上游的程式碼分開，之後合併上游比較不會衝突）：
//
// - 每部劇記住自己的清晰度與倍速（播放中改的只記在這部劇；設定頁的是全域預設）；
// - 片頭片尾自動跳過（每部劇各自設定，從播放選單「片頭片尾」那一行設）；
// - 設定頁的「橘子版」那一栏：介面語言、使用者、選擇版本安裝、更新來源、更新頻道、觀看統計、
//   自動跳過開關、關於。
//
// 和其他拆出去的檔案一樣，這些都是 MainActivity 的擴充成員。

/** 重建畫面後回到設定頁（換語言、換使用者時用）。 */
internal const val EXTRA_OPEN_SETTINGS = "tw.orange.hgtv.open-settings"
/** 片頭片尾設定後，至少還要留這麼長的正片才會跳，免得短片被整集跳掉。 */
private const val MIN_MAIN_PART_MS = 10_000L

// —— 每部劇的清晰度與倍速 ——

/** 這部劇實際用的清晰度：播放中改過就用改過的，否則用設定頁的全域預設。 */
internal fun MainActivity.playQuality(): Int =
    detail?.series?.id?.let { seriesStore.get(it).quality } ?: library.maxQuality

internal fun MainActivity.playSpeed(): Float =
    detail?.series?.id?.let { seriesStore.get(it).speed } ?: library.playbackSpeed

internal fun MainActivity.rememberQuality(value: Int) {
    val id = detail?.series?.id ?: return
    seriesStore.update(id) { it.copy(quality = PlaybackQuality.normalize(value)) }
}

internal fun MainActivity.rememberSpeed(value: Float) {
    val id = detail?.series?.id ?: return
    seriesStore.update(id) { it.copy(speed = PlaybackSpeed.normalize(value)) }
}

// —— 片頭片尾 ——

/** 這一集第一次可以播的時候呼叫：還在片頭裡就直接跳到正片。 */
internal fun MainActivity.skipIntro(p: Player) {
    val id = detail?.series?.id ?: return
    if (!seriesStore.autoSkip) return
    val intro = seriesStore.get(id).introMs
    val duration = p.duration
    if (intro <= 0 || duration == C.TIME_UNSET || duration < intro + MIN_MAIN_PART_MS) return
    if (p.currentPosition < intro - 1000) {
        p.seekTo(intro)
        tvTools.toast("已跳过片头 ${formatTime(intro)}")
    }
}

/**
 * 每秒檢查一次：進到片尾就直接跳到這一集的結尾。
 * 跳到結尾後播放器會進入「播完」狀態，存進度、自動連播都照原本的流程走。
 */
internal fun MainActivity.skipOutro() {
    val p = player ?: return
    val id = detail?.series?.id ?: return
    if (!playbackReady || !p.isPlaying || !seriesStore.autoSkip) return
    val outro = seriesStore.get(id).outroMs
    val duration = p.duration
    if (outro <= 0 || duration == C.TIME_UNSET || duration < outro + MIN_MAIN_PART_MS) return
    val position = p.currentPosition
    if (position >= duration - outro && position < duration - 500) {
        tvTools.toast("已跳过片尾")
        p.seekTo(duration)
    }
}

/** 播放選單的「片头片尾」那一行：用目前的播放位置設定片頭結束 / 片尾開始。 */
internal fun MainActivity.skipChoices(rebuild: (Int) -> Unit): List<PlaybackMenu.Choice> {
    val id = detail?.series?.id
    val setting = id?.let { seriesStore.get(it) }
    val intro = setting?.introMs ?: 0
    val outro = setting?.outroMs ?: 0
    return listOf(
        PlaybackMenu.Choice(if (intro > 0) "片头到 ${formatTime(intro)}" else "片头到此刻", intro > 0) {
            if (id != null) seriesStore.update(id) { it.copy(introMs = SeriesSettings.clampSkip(currentPosition())) }
            rebuild(0)
        },
        PlaybackMenu.Choice(if (outro > 0) "片尾最后 ${formatTime(outro)}" else "片尾从此刻", outro > 0) {
            val duration = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0
            if (id != null && duration > 0) seriesStore.update(id) { it.copy(outroMs = SeriesSettings.clampSkip(duration - currentPosition())) }
            rebuild(1)
        },
        PlaybackMenu.Choice("清除本剧", false) {
            if (id != null) seriesStore.update(id) { it.copy(introMs = 0, outroMs = 0) }
            rebuild(2)
        },
        PlaybackMenu.Choice("自动跳过 · ${if (seriesStore.autoSkip) "开" else "关"}", seriesStore.autoSkip) {
            seriesStore.autoSkip = !seriesStore.autoSkip
            rebuild(3)
        }
    )
}

// —— 設定頁：「橘子版」那一栏 ——

/** 重建整個畫面並回到設定頁（換語言、換使用者後，所有文字與資料都要重新載入）。 */
private fun MainActivity.reopenOnSettings() {
    saveProgress(); library.flush(); stats.flush()
    intent.putExtra(EXTRA_OPEN_SETTINGS, true)
    recreate()
}

/**
 * 「橘子版」設定欄：整欄寬、兩格一行（與「按键设置」那一栏同一套排法）。
 *
 * 回傳所有格子（由左到右、由上到下）；這一栏與上下兩栏之間的焦點出口由 [MainActivity.settings] 接。
 */
internal fun MainActivity.orangeSettings(
    parent: LinearLayout,
    panel: (LinearLayout) -> Unit,
    heading: (LinearLayout, String, String) -> Unit
): List<TvButton> {
    val group = column().apply { clipChildren = false; clipToPadding = false }
    panel(group)
    parent.addView(group, lp(-1, -2).apply { topMargin = dp(TvStyle.GAP_GROUP) })
    heading(group, "橘子版", "语言、家人各自的记录、选择版本更新、观看统计")
    val lines = List(4) { row() }.onEachIndexed { index, line ->
        group.addView(line, lp(-1, -2).apply { if (index > 0) topMargin = dp(TvStyle.GAP_INLINE) })
    }
    val cells = mutableListOf<TvButton>()
    fun cell(label: String, action: (TvButton) -> Unit): TvButton {
        val line = lines[cells.size / 2]
        lateinit var view: TvButton
        view = TvButton(this, label, ButtonSize.NORMAL, ButtonTone.SECONDARY, alignStart = true, action = { action(view) })
        line.addView(view, lp(0, -2).apply { weight = 1f; if (line.childCount > 0) leftMargin = dp(TvStyle.GAP_INLINE) })
        cells += view
        return view
    }
    cell("界面语言  ·  ${Zh.label}") { anchor ->
        tvTools.choose("界面语言", listOf("繁体中文（台湾用语）", "简体中文"), anchor, { index ->
            val traditional = index == 0
            if (traditional != Zh.traditional) { Zh.set(this, traditional); reopenOnSettings() }
        })
    }
    cell("使用者  ·  ${profile.name}") { anchor -> showProfiles(anchor) }
    cell("选择版本安装") { anchor -> appUpdater.showVersions(anchor) }
    lateinit var sourceCell: TvButton
    sourceCell = cell("更新来源  ·  ${appUpdater.source.repository}") { anchor ->
        showUpdateSource(anchor) { sourceCell.setLabel("更新来源  ·  ${appUpdater.source.repository}") }
    }
    cell("更新频道  ·  ${appUpdater.source.channel.label}") { anchor ->
        val current = appUpdater.source
        val next = if (current.channel == UpdateChannel.STABLE) UpdateChannel.PREVIEW else UpdateChannel.STABLE
        appUpdater.changeSource(current.copy(channel = next))
        anchor.setLabel("更新频道  ·  ${next.label}")
    }
    cell("观看统计") { anchor -> showStats(anchor) }
    cell("片头片尾自动跳过  ·  ${if (seriesStore.autoSkip) "开启" else "关闭"}") { anchor ->
        seriesStore.autoSkip = !seriesStore.autoSkip
        anchor.setLabel("片头片尾自动跳过  ·  ${if (seriesStore.autoSkip) "开启" else "关闭"}")
    }
    cell("关于橘子版") { anchor -> showAbout(anchor) }
    // 格子之間：行內左右互指、行間上下互指，兩端停在自己身上。
    cells.forEachIndexed { index, view ->
        view.nextFocusLeftId = (cells.getOrNull(index - 1)?.takeIf { index % 2 == 1 } ?: view).id
        view.nextFocusRightId = (cells.getOrNull(index + 1)?.takeIf { index % 2 == 0 } ?: view).id
        view.nextFocusUpId = (cells.getOrNull(index - 2) ?: view).id
        view.nextFocusDownId = (cells.getOrNull(index + 2) ?: view).id
    }
    return cells
}

// —— 使用者 ——

private val PRESET_NAMES = listOf("爸爸", "妈妈", "哥哥", "姐姐", "弟弟", "妹妹", "爷爷", "奶奶", "客人")

private val NAME_FORM = PhoneSearchServer.Form(
    "使用者名字", "输入这位使用者的名字（最多 ${ProfileList.MAX_NAME} 个字）。", "name", "名字",
    "保存到电视", "已保存到电视", ProfileList.MAX_NAME, 1024
)

internal fun MainActivity.showProfiles(anchor: View) {
    val all = profiles.list()
    val current = profile
    val labels = all.map { if (it.id == current.id) "${it.name}  ·  目前使用" else it.name }
    val dialog = TvDialog(this, "使用者")
        .message("每位使用者有自己的收藏、观看记录、播放偏好与统计。界面语言与更新设置全家共用。", 14f, TvStyle.muted)
        .choices(labels, all.indexOfFirst { it.id == current.id }) { index ->
            val target = all[index]
            if (target.id != current.id) { profiles.switchTo(target.id); reopenOnSettings() }
        }
    if (all.size < ProfileList.MAX) dialog.button("新增使用者") { addProfile(anchor) }
    dialog.button("重新命名") { renameProfile(anchor) }
    if (all.size > 1) dialog.button("删除使用者") { removeProfile(anchor) }
    dialog.button("关闭")
    dialog.show(anchor)
}

private fun MainActivity.addProfile(anchor: View) {
    val taken = profiles.list().map { it.name }.toSet()
    val presets = PRESET_NAMES.filter { it !in taken }
    tvTools.choose("新增使用者", presets + "用手机输入名字", anchor, { index ->
        if (index < presets.size) createProfile(presets[index], anchor)
        else tvTools.phoneForm(anchor, "新增使用者", NAME_FORM, "手机扫码后输入名字。") { name -> createProfile(name, anchor) }
    })
}

private fun MainActivity.createProfile(name: String, anchor: View) {
    val created = profiles.add(name)
    if (created == null) { tvTools.info("无法新增", "名字需为 1–${ProfileList.MAX_NAME} 个字，最多 ${ProfileList.MAX} 位使用者。", anchor); return }
    tvTools.confirm("已新增「${created.name}」", "现在切换到「${created.name}」吗？新使用者的收藏与记录是空的。", anchor) {
        profiles.switchTo(created.id); reopenOnSettings()
    }
}

private fun MainActivity.renameProfile(anchor: View) {
    val current = profile
    tvTools.phoneForm(anchor, "重新命名「${current.name}」", NAME_FORM, "手机扫码后输入新名字。") { name ->
        if (profiles.rename(current.id, name)) reopenOnSettings()
        else tvTools.info("无法重新命名", "名字需为 1–${ProfileList.MAX_NAME} 个字。", anchor)
    }
}

/**
 * 刪除使用者：預設使用者與**目前正在用的**使用者不能刪。
 * 目前這個畫面還開著那位使用者的記錄檔，刪掉之後關畫面時的最後一次寫入會把檔案又寫回來；
 * 要刪自己就先切換到別人。
 */
private fun MainActivity.removeProfile(anchor: View) {
    val removable = profiles.list().filter { it.id != ProfileList.DEFAULT.id && it.id != profile.id }
    if (removable.isEmpty()) {
        tvTools.info("没有可删除的使用者", "「${profiles.list().first().name}」与目前正在使用的使用者不能删除；要删除自己，请先切换到别的使用者。", anchor)
        return
    }
    tvTools.choose("删除哪一位？", removable.map { it.name }, anchor, { index ->
        val target = removable[index]
        tvTools.confirm("删除「${target.name}」", "会一起删除「${target.name}」的收藏、观看记录、播放偏好与统计，无法复原。", anchor) {
            profiles.remove(target.id)
            reopenOnSettings()
        }
    })
}

// —— 更新來源 ——

private val REPO_FORM = PhoneSearchServer.Form(
    "更新来源", "输入 GitHub 仓库，格式是 owner/repo，例如 ${AppUpdate.REPOSITORY}。", "repo", "owner/repo",
    "保存到电视", "已保存到电视", 201, 1024
)

private val TOKEN_FORM = PhoneSearchServer.Form(
    "GitHub token", "贴上能读取该仓库 Releases 的 token（建议用只读、只限这个仓库的 fine-grained token）。token 只存在这台电视上。",
    "token", "github_pat_…", "保存到电视", "已保存到电视", 255, 2048
)

internal fun MainActivity.showUpdateSource(anchor: View, changed: () -> Unit) {
    val source = appUpdater.source
    val dialog = TvDialog(this, "更新来源")
        .message(
            "仓库：${source.repository}${if (source.repository == AppUpdate.REPOSITORY) "（默认）" else ""}\n" +
                "频道：${source.channel.label}\n" +
                "Token：${if (source.usesToken) "已设置" else "未设置（只能读公开仓库）"}"
        )
        .message("「选择版本安装」与自动检查都从这里读。别的仓库发的安装包，签名必须与目前安装的一致才装得上。", 14f, TvStyle.muted)
    fun apply(next: UpdateSource) { appUpdater.changeSource(next); changed() }
    dialog.button("手机输入仓库") {
        tvTools.phoneForm(anchor, "更新来源", REPO_FORM, "手机扫码后输入 owner/repo。") { value ->
            val next = runCatching { source.copy(repository = value.trim().removePrefix("https://github.com/").trimEnd('/')) }
                .mapCatching { UpdateSource(it.repository, it.token, it.channel) }.getOrNull()
            if (next == null) tvTools.info("格式不正确", "请输入 owner/repo，例如 ${AppUpdate.REPOSITORY}。", anchor)
            else apply(next)
        }
    }
    if (source.usesToken) dialog.button("清除 token") { apply(source.copy(token = "")) }
    else dialog.button("设置 token") {
        tvTools.phoneForm(anchor, "GitHub token", TOKEN_FORM, "手机扫码后贴上 token。\n只建议在信任的家用网络使用（局域网内以 HTTP 传送）。") { value ->
            val next = runCatching { UpdateSource(source.repository, value.trim(), source.channel) }.getOrNull()
            if (next == null) tvTools.info("token 格式不正确", "token 不能有空白，长度最多 255 个字符。", anchor)
            else apply(next)
        }
    }
    if (source.repository != AppUpdate.REPOSITORY || source.usesToken) dialog.button("恢复默认") { apply(UpdateSource(channel = source.channel)) }
    dialog.button("关闭")
    dialog.show(anchor)
}

// —— 觀看統計 ——

internal fun MainActivity.showStats(anchor: View) {
    stats.flush()
    val s = stats.summary()
    val top = if (s.top.isEmpty()) "还没有观看记录。"
    else s.top.mapIndexed { index, item -> "${index + 1}. ${item.title.ifBlank { "（未命名）" }}  ·  ${watchDuration(item.seconds)}  ·  看完 ${item.episodes} 集" }.joinToString("\n")
    val dialog = TvDialog(this, "观看统计 · ${profile.name}")
        .message(
            "今天：${watchDuration(s.todaySeconds)}\n" +
                "最近 7 天：${watchDuration(s.weekSeconds)}\n" +
                "最近 30 天：${watchDuration(s.monthSeconds)}\n" +
                "累计：${watchDuration(s.totalSeconds)} · 看完 ${s.totalEpisodes} 集\n" +
                "连续观看：${s.streakDays} 天"
        )
        .message("看最久的剧", 15f, TvStyle.text)
        .message(top, 14f, TvStyle.textDim)
        .message("统计只存在这台电视上，按使用者分开，不上传。", 13f, TvStyle.muted)
    dialog.button("清除统计") {
        tvTools.confirm("清除观看统计", "清除「${profile.name}」的观看统计？收藏与观看记录不受影响。", anchor) { stats.clear() }
    }
    dialog.button("关闭")
    dialog.show(anchor)
}

// —— 關於 ——

internal fun MainActivity.showAbout(anchor: View) {
    val dialog = TvDialog(this, "关于红果TV 橘子版 ${BuildConfig.VERSION_NAME}")
        .message(
            "由 orange0730 修改自 pan8664716/HGTV（GPL-3.0），后者派生自 N3urda/hongguoTV。\n" +
                "橘子版新增：繁体中文界面与繁体搜索、手机扫码输入、选择任一版本更新、多位使用者、观看统计、" +
                "每部剧记住清晰度与倍速、片头片尾自动跳过。\n\n" +
                "本应用不是红果官方客户端，与平台无关联；平台改版可能导致部分功能失效。"
        )
    val code = tvTools.qr("https://github.com/${AppUpdate.REPOSITORY}")
    val side = dp(160)
    val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
    line.addView(code, lp(side, side))
    line.addView(text("扫码打开源代码与发布页\ngithub.com/${AppUpdate.REPOSITORY}", 14f, TvStyle.textDim), lp(0, -2).apply { weight = 1f; leftMargin = dp(TvStyle.GAP_BLOCK) })
    dialog.content.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(TvStyle.GAP_BLOCK) })
    dialog.button("开源许可") { tvTools.info("开源许可", licenseText(), anchor) }
    dialog.button("关闭")
    dialog.show(anchor)
}
