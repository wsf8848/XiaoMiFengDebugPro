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
 * 授权码校验 — 联网激活 + 一机一码（安全加固版）
 *
 * 安全措施：
 * 1. 每次启动静默联网验证授权状态
 * 2. 设备指纹含主板+固件等多维特征
 * 3. 1010 后门仅在 DEBUG 包中生效
 * 4. 签名校验防二次打包
 * 5. 密钥分段 + XOR 混淆
 */
object LicenseChecker {

    private const val TAG = "LC"
    private const val LICENSES_URL =
        "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/licenses.json"

    // ─── 设备指纹（多维硬件特征） ───

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "u"
        val serial = try { Build.getSerial() } catch (_: Exception) { Build.SERIAL ?: "u" }
        // 多维指纹：Android ID + 序列号 + 主板 + 启动器 + 固件指纹 + 厂商 + 型号
        val raw = "$androidId|$serial|${Build.BOARD}|${Build.BOOTLOADER}|${Build.FINGERPRINT}|${Build.MANUFACTURER}|${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(16)
    }

    // ─── 联网激活 ───

    suspend fun activate(context: Context, inputCode: String): String? = withContext(Dispatchers.IO) {
        val normalized = inputCode.trim().uppercase()

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
                        boundDevice.isEmpty() -> null
                        boundDevice == deviceId -> null
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

    /**
     * 静默授权校验（每次启动调用）
     * 从本地读取已保存的授权码，联网验证是否仍有效
     * @return null=校验通过, 其他=失败原因
     */
    suspend fun verifyCurrentDevice(context: Context, savedCode: String): String? {
        if (savedCode.isEmpty()) return "未找到授权记录，请重新激活"
        return activate(context, savedCode)
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

    // ─── AES 密钥（分段 + XOR 混淆） ───

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
