package com.xmf.debugpro

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OTA 更新管理器
 *
 * 加速方案：
 *  - 检查版本用 jsDelivr CDN（国内速度快）
 *  - 下载 APK 用 ghproxy.com 代理（GitHub raw 在国内慢）
 *  - 下载时通过 onProgress 回调实时更新进度
 */
class OtaManager(private val context: Context) {

    companion object {
        // jsDelivr CDN — 国内访问快，用于检查版本
        private const val OTA_VERSION_URL =
            "https://cdn.jsdelivr.net/gh/wsf8848/XiaoMiFengDebugPro@master/dist/version.json"
        // GitHub raw 兜底（jsDelivr 失效时）
        private const val OTA_VERSION_URL_FALLBACK =
            "https://raw.githubusercontent.com/wsf8848/XiaoMiFengDebugPro/master/dist/version.json"
        // ghproxy.com 代理 — 下载 APK 加速
        private const val GH_PROXY = "https://ghproxy.com/"

        private const val TAG = "OtaManager"
    }

    data class VersionInfo(
        val version: String,
        val versionCode: Int,
        val url: String,
        val notes: String,
        val apkSize: Long = 0
    )

    /** 检查更新（先试 CDN，失败后降级到 GitHub raw） */
    suspend fun check(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val remoteInfo = try {
                fetchVersionJson(OTA_VERSION_URL) ?: fetchVersionJson(OTA_VERSION_URL_FALLBACK)
            } catch (_: Exception) {
                fetchVersionJson(OTA_VERSION_URL_FALLBACK)
            } ?: return@withContext null

            Log.d(TAG, "当前: $currentVersion, 远程: ${remoteInfo.version}")
            if (compareVersions(remoteInfo.version, currentVersion) > 0) return@withContext remoteInfo
            null
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            null
        }
    }

    /**
     * 下载 APK（走代理加速），通过 onProgress 回调进度
     * @param onProgress 0~100 的百分比进度
     * @param onFinish 下载完成回调（成功 true / 失败 false）
     */
    fun downloadAndInstall(info: VersionInfo, onProgress: (Int) -> Unit = {}, onFinish: (Boolean) -> Unit = {}) {
        try {
            // 下载 URL 走 ghproxy 代理加速
            val downloadUrl = "$GH_PROXY${info.url}"
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("小蜜蜂调试助手Pro 更新")
                .setDescription("正在下载 v${info.version} ...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    info.url.substringAfterLast("/")
                )
                .setMimeType("application/vnd.android.package-archive")
                // 允许蜂窝网络下载
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)

            // 轮询下载进度
            val query = DownloadManager.Query().setFilterById(downloadId)
            Thread {
                var finished = false
                var lastProgress = -1
                while (!finished) {
                    Thread.sleep(500) // 500ms 轮询一次，更平滑
                    var cursor: Cursor? = null
                    try {
                        cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            when (status) {
                                DownloadManager.STATUS_RUNNING -> {
                                    val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                    val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                    if (total > 0) {
                                        val pct = ((downloaded * 100) / total).toInt()
                                        if (pct != lastProgress) {
                                            lastProgress = pct; onProgress(pct)
                                        }
                                    }
                                }
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    finished = true; onProgress(100)
                                    val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                    installApk(Uri.parse(uri))
                                    onFinish(true)
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    finished = true; onFinish(false)
                                    Log.e(TAG, "下载失败")
                                }
                            }
                        }
                    } catch (_: Exception) {
                        finished = true; onFinish(false)
                    } finally {
                        cursor?.close()
                    }
                }
            }.apply { isDaemon = true }.start()
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

    /** 获取 version.json（支持 CDN 和直连） */
    private fun fetchVersionJson(urlStr: String): VersionInfo? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }
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
