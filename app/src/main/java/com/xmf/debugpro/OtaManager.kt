package com.xmf.debugpro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OTA 更新管理器
 *
 * 修复：Gitee CDN 重定向处理 + 直接流式下载（替代 DownloadManager）
 * 下载流程：HTTP 直连（自动跟随重定向）→ 写入缓存目录 → 安装
 */
class OtaManager(private val context: Context) {

    companion object {
        private const val OTA_VERSION_URL =
            "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/version.json"
        private const val TAG = "OtaManager"
    }

    data class VersionInfo(
        val version: String,
        val versionCode: Int,
        val url: String,
        val notes: String,
        val apkSize: Long = 0
    )

    /** 检查更新 */
    suspend fun check(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val remoteInfo = fetchVersionJson(OTA_VERSION_URL) ?: return@withContext null
            if (compareVersions(remoteInfo.version, currentVersion) > 0) return@withContext remoteInfo
            null
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e); null
        }
    }

    /**
     * 下载 APK — 使用 HTTP 流式下载（自动处理 Gitee CDN 重定向）
     */
    fun downloadAndInstall(info: VersionInfo, onProgress: (Int) -> Unit = {}, onFinish: (Boolean) -> Unit = {}) {
        Thread {
            try {
                val apkName = info.url.substringAfterLast("/")
                val downloadUrl = "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/$apkName"
                Log.d(TAG, "下载: $downloadUrl")

                // 建立连接，自动跟随重定向
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.connect()

                val totalSize = conn.contentLengthLong
                val inputStream = conn.inputStream
                val cacheFile = File(context.cacheDir, apkName)
                val outputStream = FileOutputStream(cacheFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgress = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        val pct = ((totalRead * 100) / totalSize).toInt()
                        if (pct != lastProgress) { lastProgress = pct; onProgress(pct) }
                    }
                }

                outputStream.close(); inputStream.close(); conn.disconnect()
                onProgress(100)
                Log.d(TAG, "下载完成: ${cacheFile.absolutePath}")

                // 安装
                installApk(Uri.fromFile(cacheFile))
                onFinish(true)
            } catch (e: Exception) {
                Log.e(TAG, "下载失败", e)
                // 兜底：浏览器下载
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
                onFinish(false)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(intent)
    }

    private fun fetchVersionJson(urlStr: String): VersionInfo? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply { connectTimeout = 10000; readTimeout = 10000; requestMethod = "GET" }
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "utf-8"))
            val text = reader.readText(); reader.close()
            val json = org.json.JSONObject(text)
            VersionInfo(
                version = json.getString("version"),
                versionCode = json.getInt("versionCode"),
                url = json.getString("url"),
                notes = json.optString("notes", "有新版本可用"),
                apkSize = json.optLong("apkSize", 0)
            )
        } finally { conn.disconnect() }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(p1.size, p2.size)) {
            val a = p1.getOrElse(i) { 0 }; val b = p2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}
