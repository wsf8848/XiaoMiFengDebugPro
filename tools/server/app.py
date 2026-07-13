#!/usr/bin/env python3
"""
小蜜蜂调试助手Pro — 授权 & OTA 服务器
Flask + SQLite + 自建服务器部署

API 端点：
  POST /api/activate       激活授权码
  POST /api/verify         静默校验
  GET  /api/version        获取最新版本
  GET  /api/download/<apk> 下载 APK
  POST /api/version/update 发布新版本（需 ADMIN_KEY）
  GET  /api/admin/licenses 查看授权码（需 ADMIN_KEY）
"""

import os, sqlite3, datetime, time
from flask import Flask, request, jsonify, send_from_directory, abort

app = Flask(__name__)

# ─── 配置 ───
BASE_DIR = "/opt/liesun-server"
DB_PATH = os.path.join(BASE_DIR, "licenses.db")
APKS_DIR = os.path.join(BASE_DIR, "apks")
PUBLIC_HOST = "http://43.138.223.90:5000"

# 密钥（发布后务必修改为随机字符串）
APP_KEY = "a87653c3e09fe29c47db52dcd7be3a58"
ADMIN_KEY = "3c1477a8da0c538047babb76ebbfed86"

# ─── 限流 ───
RATE_LIMIT_WINDOW = 10
RATE_LIMIT_MAX = 3
_rate_store = {}

def init_db():
    os.makedirs(BASE_DIR, exist_ok=True)
    os.makedirs(APKS_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS licenses (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            code        TEXT    UNIQUE NOT NULL,
            device_id   TEXT    DEFAULT '',
            buyer       TEXT    DEFAULT '',
            activated_at TEXT   DEFAULT '',
            created_at  TEXT    DEFAULT (datetime('now'))
        );
        CREATE INDEX IF NOT EXISTS idx_licenses_code ON licenses(code);
        CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_id);

        CREATE TABLE IF NOT EXISTS versions (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            version_name TEXT    NOT NULL,
            version_code INTEGER NOT NULL DEFAULT 0,
            apk_filename TEXT    NOT NULL DEFAULT '',
            notes        TEXT    DEFAULT '',
            apk_size     INTEGER DEFAULT 0,
            is_current   INTEGER DEFAULT 0,
            created_at   TEXT    DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS request_log (
            id       INTEGER PRIMARY KEY AUTOINCREMENT,
            ip       TEXT,
            endpoint TEXT,
            device   TEXT,
            time     TEXT DEFAULT (datetime('now'))
        );
    """)
    conn.commit()
    conn.close()

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def log_request(endpoint="", device=""):
    ip = request.remote_addr or "unknown"
    conn = get_db()
    conn.execute("INSERT INTO request_log (ip, endpoint, device) VALUES (?,?,?)",
                 (ip, endpoint, device))
    conn.commit()
    conn.close()

def check_rate_limit():
    ip = request.remote_addr or "unknown"
    now = time.time()
    if ip not in _rate_store:
        _rate_store[ip] = []
    _rate_store[ip] = [(ts, a) for ts, a in _rate_store[ip] if now - ts < RATE_LIMIT_WINDOW]
    count = sum(1 for _, a in _rate_store[ip] if a in ("activate",))
    if count >= RATE_LIMIT_MAX:
        return False, "请求过于频繁，请稍后再试"
    _rate_store[ip].append((now, "activate"))
    return True, ""

def verify_app_key():
    key = request.headers.get("X-App-Key", "")
    data = request.get_json(silent=True) or {}
    return key == APP_KEY or data.get("app_key") == APP_KEY

# ═══════════════════════════════════════════
#   API 端点
# ═══════════════════════════════════════════

@app.route("/api/activate", methods=["POST"])
def api_activate():
    if not verify_app_key():
        return jsonify({"success": False, "error": "未授权请求"}), 403

    ok, msg = check_rate_limit()
    if not ok:
        return jsonify({"success": False, "error": msg}), 429

    data = request.get_json(silent=True)
    if not data:
        return jsonify({"success": False, "error": "请求数据为空"}), 400

    code = (data.get("code") or "").strip().upper()
    device_id = (data.get("device_id") or "").strip()
    if not code or len(code) > 20:
        return jsonify({"success": False, "error": "无效授权码格式"}), 400
    if not device_id or len(device_id) > 32:
        return jsonify({"success": False, "error": "无效设备ID"}), 400

    conn = get_db()
    try:
        row = conn.execute("SELECT * FROM licenses WHERE code=?", (code,)).fetchone()
        if not row:
            return jsonify({"success": False, "error": "无效授权码"}), 404

        existing_device = row["device_id"]
        buyer = row["buyer"]

        if existing_device and existing_device != device_id:
            log_request("activate", device_id)
            return jsonify({"success": False, "error": f"授权码已被其他设备使用（客户：{buyer}）"}), 403

        if not existing_device:
            conn.execute("UPDATE licenses SET device_id=?, activated_at=? WHERE code=?",
                         (device_id, datetime.datetime.now().strftime("%Y-%m-%d %H:%M"), code))
            conn.commit()

        log_request("activate", device_id)
        return jsonify({"success": True, "message": "激活成功"})
    finally:
        conn.close()

@app.route("/api/verify", methods=["POST"])
def api_verify():
    if not verify_app_key():
        return jsonify({"success": False, "error": "未授权请求"}), 403

    data = request.get_json(silent=True)
    if not data:
        return jsonify({"success": True, "valid": False, "error": "请求数据为空"}), 400

    device_id = (data.get("device_id") or "").strip()
    saved_code = (data.get("saved_code") or "").strip().upper()
    if not device_id or not saved_code:
        return jsonify({"success": True, "valid": False, "error": "参数不完整"}), 400

    conn = get_db()
    try:
        row = conn.execute("SELECT * FROM licenses WHERE code=?", (saved_code,)).fetchone()
        if not row:
            return jsonify({"success": True, "valid": False, "error": "授权码不存在"})

        if row["device_id"] == device_id:
            return jsonify({"success": True, "valid": True, "message": "授权有效"})
        else:
            return jsonify({"success": True, "valid": False, "error": "设备不匹配"})
    finally:
        conn.close()

@app.route("/api/version", methods=["GET"])
def api_version():
    conn = get_db()
    try:
        row = conn.execute("SELECT * FROM versions WHERE is_current=1 ORDER BY id DESC LIMIT 1").fetchone()
        if not row:
            return jsonify({"success": False, "error": "暂无版本信息"}), 404
        return jsonify({
            "success": True,
            "data": {
                "version": row["version_name"],
                "versionCode": row["version_code"],
                "url": f"{PUBLIC_HOST}/api/download/{row['apk_filename']}",
                "notes": row["notes"] or "",
                "apkSize": row["apk_size"]
            }
        })
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500
    finally:
        conn.close()

@app.route("/api/download/<path:filename>", methods=["GET"])
def api_download(filename):
    if ".." in filename or "/" in filename:
        abort(404)
    return send_from_directory(APKS_DIR, filename, as_attachment=True)

@app.route("/api/version/update", methods=["POST"])
def api_version_update():
    data = request.get_json(silent=True) or {}
    if data.get("admin_key") != ADMIN_KEY:
        return jsonify({"success": False, "error": "管理密钥错误"}), 403

    version = data.get("version", "").strip()
    version_code = data.get("versionCode", 0)
    apk_filename = data.get("apkFilename", "").strip()
    notes = data.get("notes", "").strip()

    if not version or not version_code or not apk_filename:
        return jsonify({"success": False, "error": "参数不完整"}), 400

    apk_path = os.path.join(APKS_DIR, apk_filename)
    apk_size = data.get("apkSize", 0)
    if not apk_size:
        try:
            apk_size = os.path.getsize(apk_path)
        except:
            apk_size = 0

    conn = get_db()
    try:
        conn.execute("UPDATE versions SET is_current=0 WHERE is_current=1")
        conn.execute(
            "INSERT INTO versions (version_name, version_code, apk_filename, apk_size, notes, is_current) VALUES (?,?,?,?,?,1)",
            (version, version_code, apk_filename, apk_size, notes)
        )
        conn.commit()
        return jsonify({"success": True, "message": f"版本 {version} 已发布"})
    finally:
        conn.close()

@app.route("/api/admin/licenses", methods=["GET"])
def api_admin_licenses():
    key = request.args.get("admin_key", "")
    if key != ADMIN_KEY:
        return jsonify({"success": False, "error": "管理密钥错误"}), 403

    conn = get_db()
    try:
        rows = conn.execute("SELECT code, device_id, buyer, created_at, activated_at FROM licenses ORDER BY created_at").fetchall()
        return jsonify({"success": True, "data": [dict(r) for r in rows]})
    finally:
        conn.close()

@app.route("/api/health", methods=["GET"])
def api_health():
    return jsonify({"status": "ok", "time": datetime.datetime.now().isoformat()})

if __name__ == "__main__":
    init_db()
    print(f"✅ 服务器启动: {PUBLIC_HOST}")
    app.run(host="0.0.0.0", port=5000, debug=False)
