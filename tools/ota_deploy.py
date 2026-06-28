"""
小蜜蜂调试助手Pro — OTA 版本部署工具
=======================================
使用方法：
  1. 编译 APK 后运行本脚本
  2. 脚本生成 version.json + 组织 GitHub Release
  3. 手动将 APK 上传到 GitHub Releases，version.json 上传到 raw 目录

依赖：pip install requests （可选，用于自动创建 Release）
"""

import json, os, hashlib
from datetime import date

# ═══ 配置区（每次发版修改这里） ═══
VERSION = "1.5.0"                     # 新版本号
VERSION_CODE = 21                     # versionCode
UPDATE_NOTES = "新增：远程OTA升级功能，支持自动检测新版本并下载安装"
APK_PATH = "../dist/小蜜蜂调试助手Pro_v1.5.0.apk"   # APK 路径

# GitHub 仓库信息（改成你自己的仓库）
GITHUB_USER = "wsf8848"
GITHUB_REPO = "XiaoMiFengDebugPro"
GITHUB_RAW_BASE = f"https://raw.githubusercontent.com/{GITHUB_USER}/{GITHUB_REPO}/master"

# ═══ 生成 version.json ═══
apk_filename = f"小蜜蜂调试助手Pro_v{VERSION}.apk"
apk_url = f"https://github.com/{GITHUB_USER}/{GITHUB_REPO}/releases/download/v{VERSION}/{apk_filename}"

# 计算 APK 文件大小
apk_size = os.path.getsize(APK_PATH) if os.path.exists(APK_PATH) else 0

version_info = {
    "version": VERSION,
    "versionCode": VERSION_CODE,
    "url": apk_url,
    "notes": UPDATE_NOTES,
    "date": str(date.today()),
    "apkSize": apk_size,
    "apkName": apk_filename
}

# 写入文件
os.makedirs("../dist", exist_ok=True)
with open("../dist/version.json", "w", encoding="utf-8") as f:
    json.dump(version_info, f, ensure_ascii=False, indent=2)

print(f"✅ version.json 已生成 → dist/version.json")
print(f"   版本：{VERSION} (code={VERSION_CODE})")
print(f"   更新说明：{UPDATE_NOTES}")
print(f"   APK 大小：{apk_size:,} bytes")
print()
print("═" * 50)
print("📋 部署步骤：")
print()
print("1️⃣  GitHub Releases 上传 APK：")
print(f"   https://github.com/{GITHUB_USER}/{GITHUB_REPO}/releases/new")
print(f"   Tag: v{VERSION}")
print(f"   上传：{APK_PATH} → 重命名为 {apk_filename}")
print()
print("2️⃣  上传 version.json：")
print(f"   打开仓库 → 上传 dist/version.json")
print(f"   路径确保可公共访问：")
print(f"   {GITHUB_RAW_BASE}/dist/version.json")
print()
print("3️⃣  在 APP 代码中配置 OTA_URL：")
print(f"   private const val OTA_VERSION_URL = \"{GITHUB_RAW_BASE}/dist/version.json\"")
print()
print("4️⃣  重新编译 APP 发布即可")
