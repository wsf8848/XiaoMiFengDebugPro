package com.xmf.debugpro

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 授权码校验 — 服务器 API 版
 *
 * 激活流程：
 * 1. APP 发送授权码 + 设备指纹到自建服务器
 * 2. 服务器验证授权码 → 首次自动绑定设备 → 写回数据库
 * 3. 后续其他设备使用同一码 → 设备ID不匹配 → 拒绝
 *
 * 安全：
 * - APP_KEY 头部验证（防未授权请求）
 * - 设备指纹 7 维特征
 * - 签名校验防二次打包
 */
object LicenseChecker {

    private const val TAG = "LC"
    private const val SERVER_BASE = "http://43.138.223.90:5000"
    private const val API_ACTIVATE = "$SERVER_BASE/api/activate"
    private const val API_VERIFY = "$SERVER_BASE/api/verify"
    private const val APP_KEY = "a87653c3e09fe29c47db52dcd7be3a58"

    // ─── 设备指纹 ───
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "u"
        val serial = try { Build.getSerial() } catch (_: Exception) { Build.SERIAL ?: "u" }
        val raw = "$androidId|$serial|${Build.BOARD}|${Build.BOOTLOADER}|${Build.FINGERPRINT}|${Build.MANUFACTURER}|${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(16)
    }

    // ─── 联网激活 ───
    suspend fun activate(context: Context, inputCode: String): String? = withContext(Dispatchers.IO) {
        val normalized = inputCode.trim().uppercase()
        try {
            val deviceId = getDeviceId(context)
            val jsonBody = org.json.JSONObject().apply {
                put("code", normalized)
                put("device_id", deviceId)
            }.toString()

            val url = URL(API_ACTIVATE)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 15000; readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("X-App-Key", APP_KEY)
            }
            val writer = OutputStreamWriter(conn.outputStream, "utf-8")
            writer.write(jsonBody); writer.flush(); writer.close()

            val code = conn.responseCode
            val reader = BufferedReader(InputStreamReader(
                if (code in 200..299) conn.inputStream else conn.errorStream, "utf-8"
            ))
            val resp = reader.readText(); reader.close(); conn.disconnect()

            val json = org.json.JSONObject(resp)
            if (json.optBoolean("success", false)) {
                return@withContext null  // 激活成功
            } else {
                return@withContext json.optString("error", "激活失败")
            }
        } catch (e: Exception) {
            Log.w(TAG, "激活失败", e)
            return@withContext "无法连接授权服务器，请检查网络"
        }
    }

    // ─── 静默授权校验 ───
    suspend fun verifyCurrentDevice(context: Context, savedCode: String): String? = withContext(Dispatchers.IO) {
        if (savedCode.isEmpty()) return@withContext "未找到授权记录"
        try {
            val deviceId = getDeviceId(context)
            val jsonBody = org.json.JSONObject().apply {
                put("device_id", deviceId)
                put("saved_code", savedCode)
            }.toString()

            val url = URL(API_VERIFY)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 15000; readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("X-App-Key", APP_KEY)
            }
            val writer = OutputStreamWriter(conn.outputStream, "utf-8")
            writer.write(jsonBody); writer.flush(); writer.close()

            val code = conn.responseCode
            val reader = BufferedReader(InputStreamReader(
                if (code in 200..299) conn.inputStream else conn.errorStream, "utf-8"
            ))
            val resp = reader.readText(); reader.close(); conn.disconnect()

            val json = org.json.JSONObject(resp)
            return@withContext if (json.optBoolean("valid", false)) null else json.optString("error", "授权无效")
        } catch (e: Exception) {
            Log.w(TAG, "静默校验失败", e)
            return@withContext "无法连接授权服务器，请检查网络"
        }
    }

    // ─── 签名验证（防二次打包） ───
    fun verifySignature(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            )
            val cert = info.signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val fp = md.digest(cert).joinToString(":") { "%02X".format(it) }
            fp.startsWith("5E:") || fp.startsWith("DE:")
        } catch (_: Exception) { false }
    }
}
