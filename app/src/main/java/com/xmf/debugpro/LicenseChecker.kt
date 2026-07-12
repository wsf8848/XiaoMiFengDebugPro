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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 授权码校验 — 联网激活 + 一机一码（带自动绑定回写）
 *
 * 激活流程：
 * 1. APP 从 Gitee 下载 licenses.json（AES 加密）
 * 2. 解密后逐行匹配授权码
 * 3. 匹配成功且未绑定 → 自动通过 Gitee API 将设备ID写回仓库
 * 4. 之后任何人用同一码 → 设备ID不匹配 → 拒绝
 *
 * 安全：
 * - Gitee 令牌分段 + XOR 混淆
 * - AES 密钥分段 + XOR 混淆
 * - 签名校验防二次打包
 * - 设备指纹 7 维特征
 */
object LicenseChecker {

    private const val TAG = "LC"
    private const val LICENSES_URL =
        "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/licenses.json"

    // ─── Gitee API 令牌（分段 + XOR 混淆） ───
    // 令牌：a50a4b9869e1255d2dc8859f800e2222
    private val tokenParts = listOf("a50a4b98", "69e1255d", "2dc8859f", "800e2222")
    private const val tokenXorMask = 0x5C
    private const val GITEE_OWNER = "jiang-yimingouu"
    private const val GITEE_REPO = "xiao-mi-feng-debug-pro"
    private const val GITEE_FILE_PATH = "dist/licenses.json"

    private fun buildToken(): String {
        val raw = tokenParts.joinToString("")
        return raw.map { (it.code xor tokenXorMask).toChar() }.joinToString("")
    }

    // ─── 设备指纹 ───
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "u"
        val serial = try { Build.getSerial() } catch (_: Exception) { Build.SERIAL ?: "u" }
        val raw = "$androidId|$serial|${Build.BOARD}|${Build.BOOTLOADER}|${Build.FINGERPRINT}|${Build.MANUFACTURER}|${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(16)
    }

    // ─── 联网激活（带自动绑定回写） ───
    suspend fun activate(context: Context, inputCode: String): String? = withContext(Dispatchers.IO) {
        val normalized = inputCode.trim().uppercase()
        try {
            val deviceId = getDeviceId(context)
            // 获取 licenses.json（通过 Gitee API 获取 SHA 和内容）
            val (sha, encrypted) = fetchLicensesWithSha() ?: return@withContext "无法连接授权服务器"
            val lines = decryptLicenses(encrypted)?.split("\n") ?: return@withContext "授权数据异常"

            val newLines = mutableListOf<String>()
            var found = false
            var boundToDevice = false

            for (line in lines) {
                val parts = line.split("|")
                if (parts.size != 3) { newLines.add(line); continue }
                val (code, boundDevice, buyer) = Triple(parts[0], parts[1], parts[2])

                if (code == normalized) {
                    found = true
                    when {
                        boundDevice.isEmpty() -> {
                            // 未绑定 → 绑定当前设备并写回
                            newLines.add("$normalized|$deviceId|$buyer")
                            boundToDevice = true
                        }
                        boundDevice == deviceId -> newLines.add(line) // 不变
                        else -> { newLines.add(line); return@withContext "授权码已被其他设备使用（客户：$buyer）" }
                    }
                } else {
                    newLines.add(line)
                }
            }

            if (!found) return@withContext "无效授权码"

            // 需要写回 Gitee
            if (boundToDevice) {
                try {
                    val newEncrypted = encryptLicenses(newLines.joinToString("\n"))
                    pushToGitee(sha, newEncrypted, "激活绑定 $normalized")
                } catch (e: Exception) {
                    Log.w(TAG, "写回失败但激活成功", e)
                    // 写回失败但激活成功（下次静默校验会重试绑定）
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "激活失败", e)
            return@withContext "联网验证失败：${e.localizedMessage}"
        }
    }

    // ─── 静默授权校验 ───
    suspend fun verifyCurrentDevice(context: Context, savedCode: String): String? {
        if (savedCode.isEmpty()) return "未找到授权记录"
        return activate(context, savedCode)
    }

    // ─── Gitee API 获取 licenses.json（含 SHA） ───
    private fun fetchLicensesWithSha(): Pair<String, String>? {
        val token = buildToken()
        val apiUrl = "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/contents/$GITEE_FILE_PATH?access_token=$token"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply { connectTimeout = 15000; readTimeout = 15000; requestMethod = "GET" }
        return try {
            val text = BufferedReader(InputStreamReader(conn.inputStream, "utf-8")).readText()
            val json = org.json.JSONObject(text)
            val sha = json.getString("sha")
            // content 是 base64 编码的
            val content = json.getString("content").replace("\n", "").replace("\r", "")
            Pair(sha, content)
        } catch (e: Exception) { Log.w(TAG, "API GET 失败", e); null }
        finally { conn.disconnect() }
    }

    // ─── Gitee API 推送更新 ───
    private fun pushToGitee(sha: String, newEncryptedBase64: String, msg: String): Boolean {
        val token = buildToken()
        val apiUrl = "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/contents/$GITEE_FILE_PATH"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 15000; readTimeout = 15000
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json;charset=UTF-8")
        }
        return try {
            val body = org.json.JSONObject().apply {
                put("access_token", token)
                put("content", newEncryptedBase64)
                put("sha", sha)
                put("message", msg)
            }.toString()
            val writer = OutputStreamWriter(conn.outputStream, "utf-8")
            writer.write(body); writer.flush(); writer.close()
            val code = conn.responseCode
            Log.d(TAG, "API PUT $code: $msg")
            code in 200..299
        } catch (e: Exception) { Log.w(TAG, "API PUT 失败", e); false }
        finally { conn.disconnect() }
    }

    // ─── AES 加密 ───
    private fun buildKey(): ByteArray {
        val parts = listOf("5e8f9a2b", "c7d3e1f4", "a6b9c0d2", "e3f7f818")
        val raw = parts.joinToString("")
        val bytes = raw.substring(0, 32).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.map { (it.toInt() xor 0xA3).toByte() }.toByteArray()
    }

    private fun encryptLicenses(plaintext: String): String {
        val key = buildKey()
        val iv = java.security.SecureRandom().generateSeed(16)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.IvParameterSpec(iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decryptLicenses(encryptedBase64: String): String? = try {
        val key = buildKey()
        val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.IvParameterSpec(raw.copyOfRange(0, 16)))
        String(cipher.doFinal(raw.copyOfRange(16, raw.size)), Charsets.UTF_8)
    } catch (_: Exception) { null }

    // ─── 签名验证 ───
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
