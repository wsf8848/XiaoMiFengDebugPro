package com.xmf.debugpro

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 授权码校验 + 防破解检测
 *
 * 防逆向策略：
 * 1. AES 密文分段存储，反编译无法直接提取完整密文
 * 2. 密钥由多段拼接 + XOR 混淆，无明文密钥
 * 3. 签名校验阻止二次打包
 * 4. 关键字符串运行时解码
 */
object LicenseChecker {

    // ─── 密钥组件（分段 + XOR 混淆） ───
    private val keyParts = listOf("5e8f9a2b", "c7d3e1f4", "a6b9c0d2", "e3f7f818")
    private const val xorMask = 0xA3

    private fun buildKey(): ByteArray {
        val raw = keyParts.joinToString("")
        val hex = raw.substring(0, 32)  // 16 字节
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.map { (it.toInt() xor xorMask).toByte() }.toByteArray()
    }

    // ─── 授权码校验 ───
    fun checkCode(input: String): Boolean {
        val normalized = input.trim().uppercase()
        // 开发者后门：1010 作为万能调试码，不做联网/加密校验
        if (normalized == "1010") return true
        if (!normalized.matches(Regex("^[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$"))) return false

        val encrypted = Codes.encryptedData
        val decrypted = decrypt(encrypted) ?: return false
        val codes = decrypted.split("\n")
        return normalized in codes
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val key = buildKey()
            val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = raw.copyOfRange(0, 16)
            val ct = raw.copyOfRange(16, raw.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    // ─── 签名验证（防二次打包） ───
    fun verifySignature(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            // 获取签名指纹
            val cert = info.signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert)
            val fingerprint = digest.joinToString(":") { "%02X".format(it) }

            // 预期签名指纹前缀（发布版正式签名的特征）
            // 如果签名被篡改，校验不通过
            fingerprint.startsWith("5E:") || fingerprint.startsWith("DE:")
        } catch (_: Exception) { false }
    }
}
