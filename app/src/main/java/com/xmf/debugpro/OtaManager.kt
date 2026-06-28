package com.xmf.debugpro

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
 * 工作流程：
 * 1. 启动时检查远程 version.json（GitHub raw）
 * 2. 比较版本号，如有新版本弹出更新对话框
 * 3. 下载 APK → 自动弹出安装
 *
 * 使用方式（在 MainActivity 启动处调用）：
 *   OtaManager(applicationContext).checkUpdate(onUpdateAvailable = { info -> ... })
 */
class OtaManager(private val context: Context) {

    companion object {
        // GitHub raw 地址 — 已配置完成
        private const val OTA_VERSION_URL =
            "https://raw.githubusercontent.com/wsf8848/XiaoMiFengDebugPro/master/dist/version.json"

        private const val TAG = "OtaManager"
    }

    data class VersionInfo(
        val version: String,      // 新版本号 如 "1.5.0"
        val versionCode: Int,     // 新 versionCode
        val url: String,          // APK 下载地址
        val notes: String,        // 更新说明
        val apkSize: Long = 0     // APK 文件大小（字节）
    )

    /**
     * 检查更新（异步，从网络获取）
     * @param onResult 检查完成回调 (有新版本→VersionInfo, 无更新→null)
     */
    suspend fun check(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val remoteInfo = fetchVersionJson() ?: return@withContext null

            Log.d(TAG, "当前版本: $currentVersion, 远程版本: ${remoteInfo.version}")

            if (compareVersions(remoteInfo.version, currentVersion) > 0) {
                return@withContext remoteInfo
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            null
        }
    }

    /**
     * 下载 APK 并触发安装
     * 使用 Android 系统的 DownloadManager 下载
     */
    fun downloadAndInstall(info: VersionInfo) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(info.url))
                .setTitle("小蜜蜂调试助手Pro 更新")
                .setDescription("正在下载 v${info.version} ...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    info.url.substringAfterLast("/")
                )
                .setMimeType("application/vnd.android.package-archive")

            // 下载完成后自动打开安装
            val downloadId = downloadManager.enqueue(request)

            // 监听下载完成 → 打开安装
            val query = DownloadManager.Query().setFilterById(downloadId)
            Thread {
                var finished = false
                while (!finished) {
                    Thread.sleep(1000)
                    val cursor = downloadManager.query(query)
                    cursor.use {
                        if (it.moveToFirst()) {
                            val status = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                finished = true
                                val uri = it.getString(it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                installApk(Uri.parse(uri))
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                finished = true
                                Log.e(TAG, "下载失败")
                            }
                        }
                    }
                }
            }.apply { isDaemon = true }.start()
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            // 兜底：直接打开浏览器让用户手动下载
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 安装 APK */
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

    /** 从网络获取 version.json */
    private fun fetchVersionJson(): VersionInfo? {
        val url = URL(OTA_VERSION_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "utf-8"))
            val text = reader.readText()
            reader.close()

            val json = org.json.JSONObject(text)
            VersionInfo(
                version = json.getString("version"),
                versionCode = json.getInt("versionCode"),
                url = json.getString("url"),
                notes = json.optString("notes", "有新版本可用"),
                apkSize = json.optLong("apkSize", 0)
            )
        } finally {
            conn.disconnect()
        }
    }

    /** 版本号比较（"1.5.0" > "1.4.0"） */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}
