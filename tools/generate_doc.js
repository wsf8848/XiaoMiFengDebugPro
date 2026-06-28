const fs = require("fs");
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        Header, Footer, AlignmentType, LevelFormat,
        TableOfContents, HeadingLevel, BorderStyle, WidthType, ShadingType,
        PageNumber, PageBreak } = require("docx");

const C = { primary: "2E75B6", secondary: "5B9BD5", accent: "C28B4C",
  light: "D5E8F0", dark: "1F3864", header: "3A86FF", green: "2E7D32", red: "C62828" };
const bd = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const bds = { top: bd, bottom: bd, left: bd, right: bd };
const cm = { top: 80, bottom: 80, left: 120, right: 120 };

function h1(t) { return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: t, bold: true, font: "Microsoft YaHei", size: 32 })] }); }
function h2(t) { return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text: t, bold: true, font: "Microsoft YaHei", size: 26 })] }); }
function p(arr) {
  const runs = typeof arr === "string" ? [new TextRun({ text: arr, font: "Microsoft YaHei", size: 21 })] : arr.map(t => typeof t === "string" ? new TextRun({ text: t, font: "Microsoft YaHei", size: 21 }) : new TextRun({ font: "Microsoft YaHei", size: 21, ...t }));
  return new Paragraph({ spacing: { after: 120, before: 60 }, children: runs });
}
function pb() { return new Paragraph({ children: [new PageBreak()] }); }
function tr(cells, shade) {
  return new TableRow({ children: cells.map(c => new TableCell({ borders: bds, width: { size: c.w, type: WidthType.DXA }, margins: cm,
    shading: shade ? { fill: shade, type: ShadingType.CLEAR } : undefined,
    children: [new Paragraph({ spacing: { after: 20 }, children: [new TextRun({ text: c.t, font: "Microsoft YaHei", size: 18, bold: c.b||false, color: c.c||"000000" })] })] })) });
}

const doc = new Document({
  styles: { default: { document: { run: { font: "Microsoft YaHei", size: 20 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Microsoft YaHei", color: C.dark }, paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: "Microsoft YaHei", color: C.primary }, paragraph: { spacing: { before: 240, after: 160 }, outlineLevel: 1 } },
    ] },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: C.primary, space: 4 } },
      children: [new TextRun({ text: "小蜜蜂调试助手Pro v1.5.0 使用说明", font: "Microsoft YaHei", size: 16, color: C.secondary })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      border: { top: { style: BorderStyle.SINGLE, size: 6, color: C.primary, space: 4 } },
      children: [new TextRun({ text: "第 ", font: "Microsoft YaHei", size: 16, color: C.secondary }),
        new TextRun({ children: [PageNumber.CURRENT], font: "Microsoft YaHei", size: 16, color: C.secondary }),
        new TextRun({ text: " 页", font: "Microsoft YaHei", size: 16, color: C.secondary })] })] }) },
    children: [
      // Cover - original spacing
      new Paragraph({ spacing: { before: 4000 } }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: "小蜜蜂调试助手Pro", font: "Microsoft YaHei", size: 52, bold: true, color: C.dark })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: "BLE 串口调试终端", font: "Microsoft YaHei", size: 32, color: C.primary })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 }, children: [new TextRun({ text: "使用说明文档 v1.5.0", font: "Microsoft YaHei", size: 28, color: C.secondary })] }),
      new Paragraph({ spacing: { before: 2000 } }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [new TextRun({ text: "适用平台：Android 8.0+", font: "Microsoft YaHei", size: 21 })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [new TextRun({ text: "软件作者：伍圣锋", font: "Microsoft YaHei", size: 21 })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "联系方式：554805466@qq.com", font: "Microsoft YaHei", size: 21 })] }),
      pb(),

      // TOC
      h1("目录"),
      new TableOfContents("目录", { hyperlink: true, headingStyleRange: "1-2" }),
      pb(),

      // Chapter 1
      h1("一、软件概述"),
      h2("1.1 软件简介"),
      p("小蜜蜂调试助手Pro是一款面向嵌入式开发者的蓝牙串口调试工具，运行于 Android 平台。它通过低功耗蓝牙（BLE）与嵌入式设备建立无线串口通信，支持数据收发、协议解析、远程控制等功能，是硬件调试和开发过程中不可或缺的助手工具。"),
      p("本软件专为高校电子类学生、硬件爱好者和嵌入式开发工程师设计，旨在提供一款操作直观、功能全面、稳定可靠的移动端调试工具。"),

      h2("1.2 主要功能"),
      new Table({ width: { size: 9626, type: WidthType.DXA }, columnWidths: [2400, 7226], rows: [
        tr([{ t: "功能模块", w: 2400, b: true, c: "FFFFFF" }], C.dark),
        tr([{ t: "登录系统", w: 2400 }, { t: "账号密码+授权码三重验证，支持自动登录", w: 7226 }], C.light),
        tr([{ t: "BLE 扫描连接", w: 2400 }, { t: "搜索附近蓝牙设备，一键连接/断开，自动重连", w: 7226 }]),
        tr([{ t: "原始数据收发", w: 2400 }, { t: "串口数据收发日志，支持 HEX/UTF-8 切换，定时发送", w: 7226 }], C.light),
        tr([{ t: "摇杆控制", w: 2400 }, { t: "360° 8 方向摇杆，自动发送控制指令", w: 7226 }]),
        tr([{ t: "功能按键", w: 2400 }, { t: "15 个可自定义的功能按键，支持长按重命名", w: 7226 }], C.light),
        tr([{ t: "自定义协议", w: 2400 }, { t: "解析双包头协议数据，实时显示解析结果", w: 7226 }]),
        tr([{ t: "语音&震动", w: 2400 }, { t: "全程语音提示与震动反馈，操作更直观", w: 7226 }], C.light),
      ]}),
      pb(),

      // Chapter 2
      h1("二、快速开始"),
      h2("2.1 安装与权限"),
      p("在 Android 手机上打开 APK 文件即可安装。首次运行时会请求蓝牙扫描、蓝牙连接和位置权限。若拒绝则蓝牙搜索功能无法使用。"),
      h2("2.2 登录系统"),
      p("首次使用需输入：账号 FYX、密码 680221、授权码 1010。勾选「下次自动登录」可跳过后续登录步骤。"),
      pb(),

      // Chapter 3
      h1("三、蓝牙连接"),
      h2("3.1 搜索设备"),
      p("在侧滑栏中点击「搜索蓝牙设备」按钮开始扫描，可随时中断。确保目标设备已上电并处于可广播状态。"),
      h2("3.2 连接和断开"),
      p("在列表中选中设备后点击「连接」按钮。如需配对码则输入后连接。点击「断开设备」断开连接。软件会自动保存上次连接设备信息用于下次自动重连。"),
      pb(),

      // Chapter 4
      h1("四、原始数据模式"),
      p("核心功能页面，展示所有蓝牙收发数据日志。支持 HEX/UTF-8 显示切换、数据手动发送与定时发送（10ms/500ms/1s 间隔可选）。发送的数据以绿色显示，接收数据以黑色显示。"),
      pb(),

      // Chapter 5
      h1("五、摇杆控制模式"),
      p("居中显示圆形摇杆，手指拖动 360° 自由控制，松手自动回中。内部划分 8 个扇形区域（不显示），对应指令："),
      p("前推(0x51) 右上(0x52) 右转(0x53) 右下(0x54) 后拉(0x55) 左下(0x56) 左转(0x57) 左上(0x58) 居中(0x5A)"),
      p("前进/右转/后退/左转/停车有语音播报。摇杆上方显示实时数据：左轮速度、陀螺仪角度、右轮速度、电量、模式、状态。"),
      pb(),

      // Chapter 6
      h1("六、功能按键模式"),
      p("15 个彩色按键（5行×3列），每个按键按下发送固定 HEX 指令（按键一 0x60 至按键十五 0x6E）。长按可自定义名称，修改后自动保存。"),
      pb(),

      // Chapter 7
      h1("七、自定义协议模式"),
      p("解析格式 0x2C 0x12 + N 字节数据 + 0x5B 的蓝牙接收数据，实时显示解析结果。适用于传感器数据上报等场景。"),
      pb(),

      // Chapter 8
      h1("八、语音与震动"),
      new Table({ width: { size: 9626, type: WidthType.DXA }, columnWidths: [2800, 6826], rows: [
        tr([{ t: "操作场景", w: 2800, b: true, c: "FFFFFF" }], C.dark),
        tr([{ t: "打开应用", w: 2800 }, { t: "欢迎使用小蜜蜂调试助手", w: 6826 }], C.light),
        tr([{ t: "登录成功/失败", w: 2800 }, { t: "登录成功 / 账号错误 / 密码错误 / 授权码错误", w: 6826 }]),
        tr([{ t: "搜索/连接", w: 2800 }, { t: "正在搜索 / 搜索完成 / 连接成功 / 设备已断开", w: 6826 }], C.light),
        tr([{ t: "数据操作", w: 2800 }, { t: "已发送 / 已清空接收区", w: 6826 }]),
        tr([{ t: "交互切换", w: 2800 }, { t: "Tab 切换 / 摇杆方向 / 按键点击，均播报对应名称", w: 6826 }], C.light),
      ]}),
      pb(),

      // Chapter 9
      h1("九、常见问题"),
      h2("9.1 无法搜索到设备"),
      p("检查设备已上电、蓝牙已开启、已授予位置权限、靠近设备、重启蓝牙。"),
      h2("9.2 连接/发送失败"),
      p("设备可能已被其他手机连接、超出范围、配对码错误、未连接设备或 HEX 格式不正确。"),
      h2("9.3 数据不刷新"),
      p("v1.5.0 已修复并发多协议包显示不刷新的问题，请升级到最新版本。"),
      pb(),

      // Chapter 10
      h1("十、维护与反馈"),
      p("当前版本：v1.5.0（versionCode=22）。问题反馈：554805466@qq.com。邮件请注明手机型号和 Android 版本。"),

      // 版本更新记录
      h1("附：版本更新记录"),
      new Table({ width: { size: 9626, type: WidthType.DXA }, columnWidths: [1600, 2000, 6026], rows: [
        tr([{ t: "版本", w: 1600, b: true, c: "FFFFFF" }, { t: "日期", w: 2000, b: true, c: "FFFFFF" }, { t: "更新内容", w: 6026, b: true, c: "FFFFFF" }], C.dark),
        tr([{ t: "v1.0.0–v1.0.5", w: 1600 }, { t: "2026-06-14", w: 2000 }, { t: "初始开发：登录、BLE 扫描、收发日志基础功能", w: 6026 }], C.light),
        tr([{ t: "v1.0.6–v1.0.9", w: 1600 }, { t: "2026-06-15", w: 2000 }, { t: "UI 优化：侧滑栏紧凑化、按钮高亮、TTS 语音+震动", w: 6026 }]),
        tr([{ t: "v1.1.0–v1.1.4", w: 1600 }, { t: "2026-06-16", w: 2000 }, { t: "真实 BLE GATT 连接、Tab 多页面、摇杆控制、功能按键", w: 6026 }], C.light),
        tr([{ t: "v1.2.0–v1.2.2", w: 1600 }, { t: "2026-06-16", w: 2000 }, { t: "定时发送、按键指令、8 区域摇杆、功能按键 HEX 发送", w: 6026 }]),
        tr([{ t: "v1.3.0", w: 1600 }, { t: "2026-06-16", w: 2000 }, { t: "功能按键发送 HEX 指令、定时发送完善", w: 6026 }], C.light),
        tr([{ t: "v1.4.0", w: 1600 }, { t: "2026-06-17", w: 2000 }, { t: "自动登录、按键重命名、自定义协议页、作者信息", w: 6026 }]),
        tr([{ t: "v1.5.0", w: 1600 }, { t: "2026-06-24", w: 2000 }, { t: "摇杆实时数据显示面板、多协议并发解析修复、bleDataSeq 强制刷新", w: 6026 }], C.light),
      ]}),
      new Paragraph({ spacing: { before: 300 } }),
      new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "— 全文完 —", font: "Microsoft YaHei", size: 20, color: C.secondary })] }),
    ]
  }]
});

Packer.toBuffer(doc).then(buf => {
  const out = "C:/Users/55480/Desktop/My_Project/安卓串口助手开发（小蜜蜂Pro）/dist/小蜜蜂调试助手Pro使用说明文档.docx";
  fs.writeFileSync(out, buf);
  console.log("OK: " + fs.statSync(out).size + " bytes");
});
