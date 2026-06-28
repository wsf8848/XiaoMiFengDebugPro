#!/usr/bin/env python3
"""
小蜜蜂调试助手Pro - 授权码生成器
生成 200 个唯一授权码，AES 加密后嵌入 APK，同时输出 Excel 表格
"""

import os, json, base64, secrets, hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment

OUT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录
EXCEL_PATH = os.path.join(OUT_DIR, "dist", "授权码列表_v1.5.0.xlsx")
CODES_KT_PATH = os.path.join(OUT_DIR, "app", "src", "main", "java", "com", "xmf", "debugpro", "Codes.kt")

# ─── Step 1: 加密密钥（多段组合 + 混淆） ───
# 实际生产时请更换以下所有字符串！
_KEY_PARTS = [
    "5e8f9a2b", "c7d3e1f4",  # 前半部分
    "a6b9c0d2", "e3f7f818",  # 4 段拼成 16 字节密钥
]
_XOR_MASK  = 0xA3  # 每个字节异或此值

def build_key():
    raw = "".join(_KEY_PARTS)
    key_bytes = bytes.fromhex(raw)[:16]
    # XOR 混淆
    return bytes(b ^ _XOR_MASK for b in key_bytes)

def encrypt_codes(plain_list):
    """AES-128-CBC 加密代码列表"""
    key = build_key()
    iv = secrets.token_bytes(16)
    cipher = AES.new(key, AES.MODE_CBC, iv)
    plaintext = "\n".join(plain_list).encode("utf-8")
    ct = cipher.encrypt(pad(plaintext, AES.block_size))
    # 格式: base64(iv + ciphertext)
    return base64.b64encode(iv + ct).decode("ascii")

# ─── Step 2: 生成 200 个授权码 ───
def generate_code(index):
    """格式: XXXX-XXXX-XXXX (12位十六进制大写)"""
    rand = secrets.token_hex(6).upper()
    return f"{rand[:4]}-{rand[4:8]}-{rand[8:]}"

codes = []
code_set = set()
while len(codes) < 200:
    c = generate_code(len(codes) + 1)
    if c not in code_set:
        code_set.add(c)
        codes.append(c)

print(f"✅ 已生成 {len(codes)} 个不重复授权码")

# ─── Step 3: 输出 Excel ───
os.makedirs(os.path.dirname(EXCEL_PATH), exist_ok=True)
wb = openpyxl.Workbook()
ws = wb.active
ws.title = "授权码列表"

header_font = Font(name="微软雅黑", bold=True, size=12, color="FFFFFF")
header_fill = PatternFill(start_color="2E75B6", end_color="2E75B6", fill_type="solid")
header_align = Alignment(horizontal="center", vertical="center")

ws.column_dimensions["A"].width = 8
ws.column_dimensions["B"].width = 28
ws.column_dimensions["C"].width = 12
ws.column_dimensions["D"].width = 20

for col, title in enumerate(["序号", "授权码", "状态", "备注"], 1):
    cell = ws.cell(row=1, column=col, value=title)
    cell.font = header_font
    cell.fill = header_fill
    cell.alignment = header_align

sold_font = Font(name="微软雅黑", size=11, color="CC0000")
avail_font = Font(name="微软雅黑", size=11, color="006600")
avail_fill = PatternFill(start_color="E8F5E9", end_color="E8F5E9", fill_type="solid")

for i, code in enumerate(codes):
    row = i + 2
    ws.cell(row=row, column=1, value=i + 1).alignment = Alignment(horizontal="center")
    ws.cell(row=row, column=2, value=code).alignment = Alignment(horizontal="center")
    status_cell = ws.cell(row=row, column=3, value="未售")
    status_cell.font = avail_font
    status_cell.fill = avail_fill
    status_cell.alignment = Alignment(horizontal="center")
    ws.cell(row=row, column=4, value="")

wb.save(EXCEL_PATH)
print(f"📊 Excel: {EXCEL_PATH}")

# ─── Step 4: AES 加密并输出 Codes.kt ───
encrypted = encrypt_codes(codes)

# 为了反逆向，把密文分成多段常量（编译期拼接）
segment_size = len(encrypted) // 5
segments = []
for i in range(5):
    start = i * segment_size
    end = start + segment_size if i < 4 else len(encrypted)
    segments.append(encrypted[start:end])

kt_code = f"""/**
 * 授权码加密数据 - 自动生成，请勿手动修改
 * 生成时间: 2026-06-23
 * 数量: 200 个
 */
package com.xmf.debugpro

object Codes {{
    // 分段存储防静态分析
    private val _s0 = "{segments[0]}"
    private val _s1 = "{segments[1]}"
    private val _s2 = "{segments[2]}"
    private val _s3 = "{segments[3]}"
    private val _s4 = "{segments[4]}"

    val encryptedData: String get() = _s0 + _s1 + _s2 + _s3 + _s4
}}
"""

with open(CODES_KT_PATH, "w", encoding="utf-8") as f:
    f.write(kt_code)
print(f"🔐 Codes.kt: {CODES_KT_PATH}")
print()
print("=" * 50)
print("授权码生成完毕！")
print("=" * 50)
print(f"\n前 5 个授权码用于测试：")
for i in range(5):
    print(f"  {i+1}. {codes[i]}")
print(f"\nExcel 表格路径：{EXCEL_PATH}")
print(f"将 Excel 发给客户时，删除或隐藏【序号】列和【备注】列")
