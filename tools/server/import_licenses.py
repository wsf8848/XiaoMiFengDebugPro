#!/usr/bin/env python3
"""
从 Gitee 导入已有授权码到服务器数据库
用法: python import_licenses.py
"""
import json, base64, sqlite3, os, sys
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from urllib.request import urlopen

# ─── 配置 ───
GITEE_RAW_URL = "https://gitee.com/jiang-yimingouu/xiao-mi-feng-debug-pro/raw/master/dist/licenses.json"
DB_PATH = os.path.join(os.path.dirname(__file__) or ".", "licenses.db")

# AES 密钥（与 LicenseChecker.kt 的 buildKey() 一致）
_KEY = bytes([0x5e^0xA3, 0x8f^0xA3, 0x9a^0xA3, 0x2b^0xA3,
              0xc7^0xA3, 0xd3^0xA3, 0xe1^0xA3, 0xf4^0xA3,
              0xa6^0xA3, 0xb9^0xA3, 0xc0^0xA3, 0xd2^0xA3,
              0xe3^0xA3, 0xf7^0xA3, 0xf8^0xA3, 0x18^0xA3])

def decrypt_data(enc_b64):
    raw = base64.b64decode(enc_b64)
    cipher = AES.new(_KEY, AES.MODE_CBC, raw[:16])
    return unpad(cipher.decrypt(raw[16:]), AES.block_size).decode()

def fetch_licenses():
    print(f"📥 从 Gitee 下载授权码...")
    resp = urlopen(GITEE_RAW_URL, timeout=15)
    data = json.loads(resp.read().decode())
    encrypted = data["encrypted"]
    plain = decrypt_data(encrypted)
    licenses = []
    for line in plain.strip().split("\n"):
        parts = line.split("|", 2)
        if len(parts) == 3:
            licenses.append({"code": parts[0], "device": parts[1], "buyer": parts[2]})
    print(f"   共 {len(licenses)} 个授权码")
    return licenses

def import_to_db(licenses):
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS licenses (
            code TEXT PRIMARY KEY,
            device_id TEXT DEFAULT '',
            buyer TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now','localtime'))
        );
        CREATE TABLE IF NOT EXISTS versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            version TEXT NOT NULL,
            version_code INTEGER NOT NULL,
            apk_filename TEXT NOT NULL,
            apk_size INTEGER DEFAULT 0,
            notes TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now','localtime')),
            is_current INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS request_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            action TEXT, code TEXT, device_id TEXT, ip TEXT,
            created_at TEXT DEFAULT (datetime('now','localtime'))
        );
    """)
    imported = 0
    for lic in licenses:
        try:
            c.execute(
                "INSERT OR REPLACE INTO licenses (code, device_id, buyer) VALUES (?,?,?)",
                (lic["code"], lic["device"], lic["buyer"])
            )
            imported += 1
        except Exception as e:
            print(f"   ⚠️ 导入失败 {lic['code']}: {e}")
    conn.commit()
    conn.close()
    print(f"   ✅ 成功导入 {imported}/{len(licenses)} 个授权码")

if __name__ == "__main__":
    print("=" * 50)
    print("  授权码导入工具 → SQLite")
    print("=" * 50)
    licenses = fetch_licenses()
    import_to_db(licenses)
    print(f"\n📊 数据库路径: {os.path.abspath(DB_PATH)}")
    print("  完成！")
