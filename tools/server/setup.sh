#!/bin/bash
# 小蜜蜂调试助手Pro — 服务器一键部署脚本
set -e

SERVER_DIR="/opt/liesun-server"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================"
echo "  小蜜蜂调试助手Pro — 服务器部署"
echo "========================================"

# 1. 创建目录
sudo mkdir -p "$SERVER_DIR/apks" "$SERVER_DIR/logs"
sudo chown -R ubuntu:ubuntu "$SERVER_DIR"

# 2. 复制文件
cp "$SCRIPT_DIR/app.py" "$SERVER_DIR/"
cp "$SCRIPT_DIR/requirements.txt" "$SERVER_DIR/"

# 3. 安装依赖
cd "$SERVER_DIR"
sudo apt update -y
sudo apt install -y python3-pip python3-venv sqlite3

python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 4. 初始化数据库
python3 app.py &

# 5. 创建 systemd 服务
sudo tee /etc/systemd/system/liesun-server.service > /dev/null << 'EOF'
[Unit]
Description=小蜜蜂调试助手Pro 授权 & OTA 服务器
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/liesun-server
ExecStart=/opt/liesun-server/venv/bin/gunicorn -w 2 -b 0.0.0.0:5000 app:app
Restart=always
RestartSec=5
StandardOutput=append:/opt/liesun-server/logs/access.log
StandardError=append:/opt/liesun-server/logs/error.log

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable liesun-server
sudo systemctl start liesun-server

# 6. 开放端口
sudo iptables -I INPUT -p tcp --dport 5000 -j ACCEPT 2>/dev/null || true

echo ""
echo "✅ 部署完成！"
echo "   服务状态: $(sudo systemctl is-active liesun-server)"
echo "   监听端口: 5000"
echo "   日志目录: $SERVER_DIR/logs/"
echo ""
