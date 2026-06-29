#!/usr/bin/env python3
"""
小蜜蜂调试助手Pro — 在线授权码管理工具

功能：
1. 生成 200 个授权码，加密后输出 licenses.json（部署到 Gitee）
2. 将授权码分配给客户：python gen_licenses.py --assign 授权码 客户名 设备ID
3. 查看所有授权码状态：python gen_licenses.py --list

licenses.json 部署到 Gitee 的 dist/ 目录，
APP 启动时联网下载，验证授权码是否有效。
"""

import os, json, base64, secrets, hashlib, sys, datetime
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

OUT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LICENSES_PATH = os.path.join(OUT_DIR, "dist", "licenses.json")

# ─── AES 加密密钥（必须与 LicenseChecker.kt 中的 buildKey() 一致） ───
_KEY_PARTS = ["5e8f9a2b", "c7d3e1f4", "a6b9c0d2", "e3f7f818"]
_XOR_MASK = 0xA3

def build_key():
    raw = "".join(_KEY_PARTS)
    key_bytes = bytes.fromhex(raw)[:16]
    return bytes(b ^ _XOR_MASK for b in key_bytes)

def encrypt_data(plaintext: str) -> str:
    key = build_key()
    iv = secrets.token_bytes(16)
    cipher = AES.new(key, AES.MODE_CBC, iv)
    ct = cipher.encrypt(pad(plaintext.encode("utf-8"), AES.block_size))
    return base64.b64encode(iv + ct).decode("ascii")

def decrypt_data(encrypted: str) -> str | None:
    try:
        key = build_key()
        raw = base64.b64decode(encrypted)
        iv, ct = raw[:16], raw[16:]
        cipher = AES.new(key, AES.MODE_CBC, iv)
        return unpad(cipher.decrypt(ct), AES.block_size).decode("utf-8")
    except Exception as e:
        print(f"解密错误: {e}")
        return None

# ─── 授权码格式 ───
def generate_code():
    rand = secrets.token_hex(6).upper()
    return f"{rand[:4]}-{rand[4:8]}-{rand[8:]}"

# ─── licenses.json 格式 ───
# 解密后每行格式: CODE|device_id|buyer_name
# device_id 为空 = 未分配，首次使用的设备自动绑定
# device_id 非空 = 已绑定该设备

def load_licenses():
    if not os.path.exists(LICENSES_PATH):
        return []
    with open(LICENSES_PATH, "r") as f:
        data = json.load(f)
    plain = decrypt_data(data["encrypted"])
    if not plain:
        print("❌ 解密失败，密钥不匹配！")
        return []
    result = []
    for line in plain.strip().split("\n"):
        parts = line.split("|")
        if len(parts) == 3:
            result.append({"code": parts[0], "device": parts[1], "buyer": parts[2]})
    return result

def save_licenses(licenses):
    lines = "\n".join(f"{l['code']}|{l['device']}|{l['buyer']}" for l in licenses)
    encrypted = encrypt_data(lines)
    with open(LICENSES_PATH, "w") as f:
        json.dump({
            "version": 1,
            "updated": str(datetime.date.today()),
            "encrypted": encrypted
        }, f, ensure_ascii=False, indent=2)
    print(f"✅ licenses.json 已更新 → {LICENSES_PATH}")

# ═══ CLI ═══
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法:")
        print("  生成 200 个授权码:  python gen_licenses.py --generate")
        print("  分配给客户:        python gen_licenses.py --assign <授权码> <客户名> [设备ID]")
        print("  查看所有:          python gen_licenses.py --list")
        print("  查看可用:          python gen_licenses.py --avail")
        sys.exit(0)

    cmd = sys.argv[1]

    if cmd == "--generate":
        codes = set()
        licenses = []
        while len(licenses) < 200:
            c = generate_code()
            if c not in codes:
                codes.add(c)
                licenses.append({"code": c, "device": "", "buyer": ""})
        save_licenses(licenses)
        print(f"已生成 {len(licenses)} 个授权码，前 5 个：")
        for l in licenses[:5]:
            print(f"  {l['code']}")

    elif cmd == "--list":
        licenses = load_licenses()
        if not licenses:
            print("暂无数据")
        else:
            print(f"共 {len(licenses)} 个授权码：")
            print(f"{'授权码':<20} {'状态':<12} {'客户':<12} {'设备ID':<20}")
            print("-" * 64)
            for l in licenses:
                status = "已分配" if l["device"] else "未分配"
                print(f"{l['code']:<20} {status:<12} {l['buyer']:<12} {l['device']:<20}")

    elif cmd == "--avail":
        licenses = load_licenses()
        avail = [l for l in licenses if not l["device"]]
        print(f"可用授权码 {len(avail)} 个：")
        for l in avail:
            print(f"  {l['code']}")

    elif cmd == "--assign" and len(sys.argv) >= 4:
        code_to_assign = sys.argv[2].upper()
        buyer = sys.argv[3]
        device_id = sys.argv[4] if len(sys.argv) > 4 else ""
        licenses = load_licenses()
        for l in licenses:
            if l["code"] == code_to_assign:
                if l["device"] and l["device"] != device_id:
                    print(f"❌ 授权码 {code_to_assign} 已被设备 {l['device']} 使用（客户：{l['buyer']}）")
                    sys.exit(1)
                l["device"] = device_id
                l["buyer"] = buyer
                save_licenses(licenses)
                print(f"✅ 授权码 {code_to_assign} 已分配给 {buyer}")
                if device_id:
                    print(f"   设备绑定：{device_id}")
                sys.exit(0)
        print(f"❌ 未找到授权码 {code_to_assign}")

    else:
        print("无效命令")
