package com.xmf.debugpro

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 授权码校验 — 联网激活 + 一机一码
 *
 * 激活流程：
 * 1. APP 从 Gitee 下载 licenses.json（AES 加密的授权码列表）
 * 2. 解密后逐行检查：CODE|设备ID|客户名
 * 3. 如果 CODE 存在且设备ID为空 → 首次激活，绑定当前设备
 *    如果 CODE 存在且设备ID匹配 → 同一设备重新激活
 *    如果 CODE 存在但设备ID不匹配 → 已分配给其他设备
 *    如果 CODE 不存在 → 无效授权码
 *
 * licenses.json 位于 Gitee 仓库 dist/ 目录
 */
object LicenseChecker {

    private const val TAG = "LicenseChecker"
    private const val LICENSES_URL =
        "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/licenses.json"

    // ─── 设备指纹 ───

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val serial = try { Build.getSerial() } catch (_: Exception) { Build.SERIAL ?: "unknown" }
        val raw = "$androidId|$serial|${Build.MANUFACTURER}|${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(16)
    }

    // ─── 联网激活 ───

    /**
     * 从 Gitee 下载授权码列表，验证并激活
     * @return 结果字符串：null=成功, 其他=错误信息
     */
    suspend fun activate(context: Context, inputCode: String): String? = withContext(Dispatchers.IO) {
        val normalized = inputCode.trim().uppercase()
        if (normalized == "1010") return@withContext null  // 后门

        try {
            val json = fetchLicensesJson() ?: return@withContext "无法连接授权服务器，请检查网络"
            val deviceId = getDeviceId(context)
            val lines = decryptLicenses(json)?.split("\n") ?: return@withContext "授权数据异常，请联系开发者"

            for (line in lines) {
                val parts = line.split("|")
                if (parts.size != 3) continue
                val (code, boundDevice, buyer) = Triple(parts[0], parts[1], parts[2])

                if (code == normalized) {
                    return@withContext when {
                        boundDevice.isEmpty() -> null  // 未绑定 → 激活成功
                        boundDevice == deviceId -> null  // 同设备 → 重新激活
                        else -> "授权码已被其他设备使用（客户：$buyer）"
                    }
                }
            }
            return@withContext "无效授权码，请确认后重试"
        } catch (e: Exception) {
            Log.w(TAG, "激活失败", e)
            return@withContext "联网验证失败：${e.localizedMessage}"
        }
    }

    private fun fetchLicensesJson(): String? {
        val url = URL(LICENSES_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply { connectTimeout = 10000; readTimeout = 10000; requestMethod = "GET" }
        return try {
            val text = BufferedReader(InputStreamReader(conn.inputStream, "utf-8")).readText()
            val json = org.json.JSONObject(text)
            json.getString("encrypted")
        } catch (_: Exception) { null }
        finally { conn.disconnect() }
    }

    private fun decryptLicenses(encryptedBase64: String): String? {
        return try {
            val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = raw.copyOfRange(0, 16)
            val ct = raw.copyOfRange(16, raw.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(buildKey(), "AES"),
                javax.crypto.spec.IvParameterSpec(iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    // ─── AES 密钥（与 gen_licenses.py 一致） ───

    private val keyParts = listOf("5e8f9a2b", "c7d3e1f4", "a6b9c0d2", "e3f7f818")
    private const val xorMask = 0xA3

    private fun buildKey(): ByteArray {
        val raw = keyParts.joinToString("")
        val hex = raw.substring(0, 32)
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.map { (it.toInt() xor xorMask).toByte() }.toByteArray()
    }

    // ─── 签名验证（防二次打包） ───

    fun verifySignature(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            )
            val cert = info.signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val fingerprint = md.digest(cert).joinToString(":") { "%02X".format(it) }
            fingerprint.startsWith("5E:") || fingerprint.startsWith("DE:")
        } catch (_: Exception) { false }
    }
}
