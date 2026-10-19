// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 一次可用的更新：信息来自发布仓库的 `update.json`（Release 附件，由发布脚本生成）。
 *
 * 判定「有没有新版」只看 versionCode，不猜版本号字符串；`minSdk` 高于本机 SDK 的版本
 * 不算可用更新（电视继续用当前版本，界面会说明原因）。
 *
 * 橘子版：来源仓库可以改（[repository]），私有仓库带 token 时安装包改走 API 地址（[apiApkUrl]）。
 */
data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val tag: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val notes: String,
    /** 这一版来自哪个仓库（owner/repo），下载地址用它拼。 */
    val repository: String = REPOSITORY,
    /** GitHub API 上安装包附件的地址；只有带 token 读私有仓库时才用得到。 */
    val apiApkUrl: String = ""
) {
    fun newerThan(installed: Long, sdk: Int) = versionCode > installed && minSdk <= sdk
    val downloadUrl get() = "https://github.com/$repository/releases/download/$tag/$fileName"

    companion object {
        /** 默认来源：本仓库。设置里可以改成别的公开或私有仓库。 */
        const val REPOSITORY = "orange0730/HGTV-Orange"
        const val PACKAGE = "tw.orange.hgtv"
        const val RELEASES = "https://github.com/$REPOSITORY/releases"
        const val MAX_APK_BYTES = 80L * 1024 * 1024
        const val MAX_METADATA_BYTES = 64 * 1024
        const val MAX_NOTES = 1500

        private val VERSION = Regex("[0-9][0-9A-Za-z.+-]{0,63}")
        private val TAG = Regex("v[0-9][0-9A-Za-z.+-]{0,80}")
        /** 安装包命名：`HGTV-orange-<版本号>.apk`（发布脚本 `tools/version.sh apk` 产生的名字）。 */
        val APK = Regex("HGTV-orange-[0-9][0-9A-Za-z.+-]{0,80}\\.apk")
        private val SHA256 = Regex("[0-9a-f]{64}")
        /** `owner/repo`：GitHub 允许的字符，两段都不能是空的。 */
        val REPOSITORY_FORMAT = Regex("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")

        /** 解析并逐字段校验；任何一项不对就抛 [IOException]，调用方按「更新信息不可用」处理。 */
        fun parse(json: String, repository: String = REPOSITORY, apiApkUrl: String = ""): AppUpdate {
            val root = try { JSONObject(json) } catch (_: Exception) { throw IOException("更新信息格式不正确") }
            if (root.optInt("schema") != 1 || root.optString("packageName") != PACKAGE) throw IOException("更新信息不匹配")
            val apk = root.optJSONObject("apk") ?: throw IOException("更新信息缺少安装包")
            val update = AppUpdate(
                versionCode = root.optLong("versionCode"),
                versionName = root.optString("versionName"),
                minSdk = root.optInt("minSdk"),
                tag = root.optString("tag"),
                fileName = apk.optString("name"),
                size = apk.optLong("size"),
                sha256 = apk.optString("sha256").lowercase(),
                notes = root.optString("notes").take(MAX_NOTES),
                repository = repository,
                apiApkUrl = apiApkUrl
            )
            if (update.versionCode !in 1..Int.MAX_VALUE.toLong()) throw IOException("更新信息里的版本号不可用")
            if (update.minSdk !in 26..100) throw IOException("更新信息里的最低系统版本不可用")
            if (!VERSION.matches(update.versionName)) throw IOException("更新信息里的版本名不可用")
            if (!TAG.matches(update.tag)) throw IOException("更新信息里的版本标签不可用")
            if (!APK.matches(update.fileName)) throw IOException("更新信息里的安装包名不可用")
            if (update.size !in 1..MAX_APK_BYTES) throw IOException("更新信息里的安装包大小不可用")
            if (!SHA256.matches(update.sha256)) throw IOException("更新信息里的校验值不可用")
            return update
        }
    }
}

/** 更新频道：只收正式版，或连预览版（GitHub 上标为 Pre-release 的版本）也收。 */
enum class UpdateChannel(val storedValue: String, val label: String) {
    STABLE("stable", "只收正式版"),
    PREVIEW("preview", "正式版与预览版");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.storedValue == value } ?: STABLE
    }
}

/**
 * 更新从哪里来：仓库、可选的 token（读私有仓库用）、频道。
 *
 * token 只会加在 `api.github.com` 的请求上；GitHub 把下载转址到别的主机时，
 * OkHttp 会自动拿掉 Authorization 头，token 不会被带到别处。
 */
data class UpdateSource(
    val repository: String = AppUpdate.REPOSITORY,
    val token: String = "",
    val channel: UpdateChannel = UpdateChannel.STABLE
) {
    init {
        require(AppUpdate.REPOSITORY_FORMAT.matches(repository) && repository.split('/').none { it == "." || it == ".." }) { "仓库格式应为 owner/repo" }
        require(token.length <= 255 && token.none { it.isWhitespace() || it.isISOControl() }) { "token 格式不正确" }
    }
    val usesToken get() = token.isNotEmpty()
}

/**
 * 版本清单里的一行：GitHub 上的一个 Release。
 *
 * 清单只要一次 API 请求就拿得到；versionCode、校验值这些细节在 `update.json` 里，
 * 使用者选定某一版之后才去读（见 [AppUpdateRepository.metadata]）。
 */
data class ReleaseEntry(
    val tag: String,
    val title: String,
    /** 发布日期 `yyyy-MM-dd`。 */
    val date: String,
    val prerelease: Boolean,
    /** 发布说明的第一行（去掉 Markdown 记号，最多 60 字）。 */
    val summary: String,
    val apkName: String,
    val apkSize: Long,
    val apkApiUrl: String,
    val metadataUrl: String,
    val metadataApiUrl: String
)

object ReleaseList {
    const val MAX_SUMMARY = 60

    /**
     * 解析 `GET /repos/{owner}/{repo}/releases`。
     * 草稿跳过；没有 `update.json` 或没有符合命名的安装包的版本也跳过（那不是本应用发的版）。
     * 取字段一律用 [str]：Android 的 `optString` 遇到 JSON null 会回传字串 "null"。
     */
    fun parse(json: String): List<ReleaseEntry> {
        val array = try { JSONArray(json) } catch (_: Exception) { throw IOException("版本清单格式不正确") }
        return (0 until array.length()).mapNotNull { index ->
            val release = array.optJSONObject(index) ?: return@mapNotNull null
            if (release.optBoolean("draft")) return@mapNotNull null
            val assets = release.optJSONArray("assets") ?: return@mapNotNull null
            val list = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            val apk = list.firstOrNull { AppUpdate.APK.matches(it.str("name")) } ?: return@mapNotNull null
            val metadata = list.firstOrNull { it.str("name") == "update.json" } ?: return@mapNotNull null
            val tag = release.str("tag_name")
            if (tag.isBlank()) return@mapNotNull null
            ReleaseEntry(
                tag = tag,
                title = release.str("name").ifBlank { tag },
                date = release.str("published_at").take(10),
                prerelease = release.optBoolean("prerelease"),
                summary = summary(release.str("body")),
                apkName = apk.str("name"),
                apkSize = apk.optLong("size"),
                apkApiUrl = apk.str("url"),
                metadataUrl = metadata.str("browser_download_url"),
                metadataApiUrl = metadata.str("url")
            )
        }
    }

    /** 发布说明的第一行，给清单当副标题。 */
    fun summary(body: String): String {
        val line = body.lineSequence()
            .map { it.trim().trimStart('#', '>', '*', '-', ' ').trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return if (line.length > MAX_SUMMARY) line.take(MAX_SUMMARY) + "…" else line
    }

    /** 依频道挑出「最新的一版」：清单本来就是新到旧排。 */
    fun newest(entries: List<ReleaseEntry>, channel: UpdateChannel) =
        entries.firstOrNull { channel == UpdateChannel.PREVIEW || !it.prerelease }
}

/** 安装包的落盘与校验：流式接收，边收边算，先写 `.part` 再改名，失败不留半截文件。 */
object UpdateFiles {
    fun verify(file: File, update: AppUpdate): Boolean {
        if (!file.isFile || file.length() != update.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == update.sha256
    }

    /** 把 [input] 收进 [destination]；长度超出 [AppUpdate.size] 立即中止，避免磁盘被写满。 */
    fun receive(input: InputStream, destination: File, update: AppUpdate, progress: (Int) -> Unit = {}) {
        val partial = File(destination.parentFile, destination.name + ".part")
        destination.parentFile?.mkdirs()
        try {
            partial.outputStream().use { output ->
                val buffer = ByteArray(32768)
                var total = 0L
                var last = -1
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException("下载已取消")
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > update.size) throw IOException("安装包长度异常")
                    output.write(buffer, 0, n)
                    val percent = (total * 100 / update.size).toInt()
                    if (percent != last) { last = percent; progress(percent) }
                }
                output.fd.sync()
            }
            if (!verify(partial, update)) throw IOException("安装包校验未通过，请重新下载")
            if (!partial.renameTo(destination)) throw IOException("无法保存安装包")
        } finally {
            partial.delete()
        }
    }
}

/**
 * 更新仓库的读取端。只允许 HTTPS 且只跟随 GitHub 自己的主机，
 * 不会去请求元数据里给的任意主机。
 */
class AppUpdateRepository(private val source: UpdateSource = UpdateSource()) : java.io.Closeable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            val allowed = url.scheme == "https" && url.host in setOf(
                "github.com", "api.github.com",
                "release-assets.githubusercontent.com", "objects.githubusercontent.com"
            )
            if (!allowed) throw IOException("更新下载地址不受支持")
            chain.proceed(chain.request())
        }
        .build()

    /**
     * [binary] 为 true 时向 API 要附件的原始内容（私有仓库下载用）。
     * token 只加在 api.github.com 上。
     */
    private fun request(url: String, binary: Boolean = false) = Request.Builder().url(url)
        .header("User-Agent", "HGTV-Orange-Updater")
        .header("Cache-Control", "no-cache")
        .apply {
            if (url.startsWith("https://api.github.com/")) {
                header("Accept", if (binary) "application/octet-stream" else "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                if (source.usesToken) header("Authorization", "Bearer ${source.token}")
            }
        }
        .build()

    private fun failure(code: Int, what: String) = IOException(
        when (code) {
            401 -> "GitHub 拒绝了这个 token，请重新设置"
            403, 429 -> "GitHub 暂时限制请求次数，请稍后再试"
            404 -> if (source.usesToken) "找不到 $what，请检查仓库名与 token 权限" else "找不到 $what；私有仓库需要设置 token"
            else -> "无法读取 $what（HTTP $code），请稍后重试"
        }
    )

    /** 版本清单（新到旧，最多 30 个版本）。 */
    fun releases(): List<ReleaseEntry> {
        val call = http.newCall(request("https://api.github.com/repos/${source.repository}/releases?per_page=30"))
        call.timeout().timeout(25, TimeUnit.SECONDS)
        return call.execute().use { response ->
            if (!response.isSuccessful) throw failure(response.code, "版本清单")
            val body = response.body ?: throw IOException("版本清单为空")
            val text = body.byteStream().use { read(it, 2 * 1024 * 1024, "版本清单过大") }
            ReleaseList.parse(text.toString(Charsets.UTF_8))
        }
    }

    /** 读某一版的 `update.json`。 */
    fun metadata(entry: ReleaseEntry): AppUpdate {
        val url = if (source.usesToken) entry.metadataApiUrl else entry.metadataUrl
        val call = http.newCall(request(url, binary = source.usesToken))
        call.timeout().timeout(25, TimeUnit.SECONDS)
        return call.execute().use { response ->
            if (!response.isSuccessful) throw failure(response.code, "${entry.tag} 的更新信息")
            val body = response.body ?: throw IOException("更新信息为空")
            val text = body.byteStream().use { read(it, AppUpdate.MAX_METADATA_BYTES, "更新信息过大") }
            val update = AppUpdate.parse(text.toString(Charsets.UTF_8), source.repository, entry.apkApiUrl)
            if (update.tag != entry.tag || update.fileName != entry.apkName) throw IOException("${entry.tag} 的更新信息与发布内容不符")
            update
        }
    }

    /** 依频道取最新的一版；一个版本都没有时给一句可读的提示。 */
    fun latest(): AppUpdate {
        val newest = ReleaseList.newest(releases(), source.channel) ?: throw IOException("尚无可用的更新版本")
        return metadata(newest)
    }

    fun download(update: AppUpdate, file: File, progress: (Int) -> Unit) {
        if (UpdateFiles.verify(file, update)) return
        val viaApi = source.usesToken && update.apiApkUrl.isNotEmpty()
        http.newCall(request(if (viaApi) update.apiApkUrl else update.downloadUrl, binary = viaApi)).execute().use { response ->
            if (!response.isSuccessful) throw failure(response.code, "安装包")
            val body = response.body ?: throw IOException("安装包为空")
            val declared = body.contentLength()
            if (declared >= 0 && declared != update.size) throw IOException("安装包长度异常")
            body.byteStream().use { UpdateFiles.receive(it, file, update, progress) }
        }
    }

    fun cancel() = http.dispatcher.cancelAll()

    override fun close() {
        cancel()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    private fun read(input: InputStream, limit: Int, tooLarge: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) throw IOException(tooLarge)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
