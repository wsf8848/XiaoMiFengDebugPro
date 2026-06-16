package com.xmf.debugpro

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xmf.debugpro.ui.theme.BeigeColors
import com.xmf.debugpro.ui.theme.XiaoMiFengTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice? = null
)

private data class LogEntry(
    val timestamp: String,
    val direction: String,
    val text: String
)

private const val EXPECTED_ACCOUNT = "FYX"
private const val EXPECTED_PASSWORD = "680221"
private const val EXPECTED_CODE = "1010"

private const val PREFS_NAME = "xmf_login"
private const val KEY_ACCOUNT = "account"
private const val KEY_PASSWORD = "password"
private const val KEY_CODE = "code"
private const val KEY_REMEMBER = "remember"
private const val KEY_LAST_DEVICE_NAME = "last_device_name"
private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaoMiFengTheme { AppScreen() }
        }
    }
}

// ─── TTS + 震动 ──────────────────────────────────────────────────────

private class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                ready = true
                pending.forEach { speakNow(it) }
                pending.clear()
            }
        }
    }

    fun speak(text: String) {
        if (ready) speakNow(text) else pending.add(text)
    }

    private fun speakNow(text: String) {
        tts?.let { t ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                @Suppress("DEPRECATION")
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }

    fun destroy() {
        tts?.stop(); tts?.shutdown(); tts = null
    }
}

private fun vibrateShort(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v?.vibrate(150)
        }
    }
}

// ─── App ─────────────────────────────────────────────────────────────

@Composable
private fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val logEntries = remember { mutableStateListOf<LogEntry>() }

    var isLoggedIn by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var rememberPassword by rememberSaveable { mutableStateOf(false) }
    var rememberCode by rememberSaveable { mutableStateOf(false) }
    var sendHex by rememberSaveable { mutableStateOf(true) }
    var receiveHex by rememberSaveable { mutableStateOf(false) }
    var sendInput by rememberSaveable { mutableStateOf("") }
    var loginError by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }
    var permissionRequestTrigger by rememberSaveable { mutableStateOf(0) }
    var latestMessage by rememberSaveable { mutableStateOf("等待连接数据...") }
    var connectedName by rememberSaveable { mutableStateOf("未连接") }
    var connectedAddress by rememberSaveable { mutableStateOf("--") }
    var welcomePlayed by rememberSaveable { mutableStateOf(false) }
    var searchJustFinished by rememberSaveable { mutableStateOf(false) }

    val scanDevices = remember { mutableStateListOf<BleDeviceItem>() }
    val prefs: SharedPreferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val bleManager = remember { BleHelper(context) }
    val tts = remember { TtsHelper(context) }

    val doVibrate = { vibrateShort(context) }
    val doSpeak: (String) -> Unit = { tts.speak(it) }

    val appendLog: (direction: String, text: String) -> Unit = { direction, msg ->
        logEntries.add(LogEntry(
            timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            direction = direction, text = msg
        ))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            isScanning = true
        } else {
            latestMessage = "蓝牙权限未授权，无法搜索设备。"
        }
    }

    // ── 开机：欢迎 + 读取登录 + 读取上次连接设备 ──
    LaunchedEffect(Unit) {
        val savedAccount = prefs.getString(KEY_ACCOUNT, "") ?: ""
        val savedPassword = prefs.getString(KEY_PASSWORD, "") ?: ""
        val savedCode = prefs.getString(KEY_CODE, "") ?: ""
        val savedRemember = prefs.getBoolean(KEY_REMEMBER, false)
        if (savedRemember && savedAccount == EXPECTED_ACCOUNT && savedPassword == EXPECTED_PASSWORD && savedCode == EXPECTED_CODE) {
            account = savedAccount; password = savedPassword; code = savedCode
            rememberPassword = true; rememberCode = true
            isLoggedIn = true
            latestMessage = "自动登录成功，主页面已打开。"
        } else if (savedRemember) {
            account = savedAccount; password = savedPassword; code = savedCode
            rememberPassword = savedPassword.isNotBlank(); rememberCode = savedCode.isNotBlank()
        }
        // 读取上次连接的设备
        val lastName = prefs.getString(KEY_LAST_DEVICE_NAME, "") ?: ""
        val lastAddr = prefs.getString(KEY_LAST_DEVICE_ADDRESS, "") ?: ""
        if (lastName.isNotBlank()) {
            connectedName = lastName; connectedAddress = lastAddr
        }
        loaded = true
    }

    // 欢迎语音（延迟等 TTS 初始化完成）
    LaunchedEffect(loaded, isLoggedIn) {
        if (!welcomePlayed) {
            delay(500)
            doSpeak("欢迎使用小蜜蜂调试助手"); doVibrate()
            welcomePlayed = true
        }
    }

    LaunchedEffect(permissionRequestTrigger) {
        if (permissionRequestTrigger > 0) permissionLauncher.launch(requiredBluetoothPermissions())
    }

    // 扫描逻辑
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanDevices.clear()
            searchJustFinished = false
            latestMessage = "正在搜索附近蓝牙设备..."
            doSpeak("正在搜索蓝牙设备"); doVibrate()
            bleManager.startScan(
                onDeviceFound = { item ->
                    val idx = scanDevices.indexOfFirst { it.address == item.address }
                    if (idx >= 0) scanDevices[idx] = item else scanDevices.add(item)
                },
                onFinished = {
                    isScanning = false
                    searchJustFinished = true
                    if (scanDevices.isEmpty()) {
                        latestMessage = "未发现设备，请确认设备已上电并靠近手机。"
                        doSpeak("未发现蓝牙设备"); doVibrate()
                    } else {
                        latestMessage = "搜索完成，共发现 ${scanDevices.size} 台设备。"
                        doSpeak("搜索完成"); doVibrate()
                    }
                }
            )
        } else {
            bleManager.stopScan()
        }
    }

    // "搜索完成" 3 秒后自动复位
    LaunchedEffect(searchJustFinished) {
        if (searchJustFinished) { delay(3000); searchJustFinished = false }
    }

    DisposableEffect(Unit) {
        onDispose { bleManager.stopScan(); tts.destroy() }
    }

    // ── 界面 ──
    MaterialTheme {
        Scaffold(containerColor = BeigeColors.background) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).background(BeigeColors.background)
            ) {
                if (isLoggedIn) {
                    val onDeviceConnect: (BleDeviceItem) -> Unit = { item ->
                        connectedName = item.name; connectedAddress = item.address
                        isScanning = false; isConnecting = false
                        appendLog("SYS", "已连接 ${item.name} (${item.address})")
                        latestMessage = "连接成功：${item.name}"
                        doSpeak("连接成功${item.name}"); doVibrate()
                        // 保存设备记忆
                        prefs.edit()
                            .putString(KEY_LAST_DEVICE_NAME, item.name)
                            .putString(KEY_LAST_DEVICE_ADDRESS, item.address)
                            .apply()
                        scope.launch { drawerState.close() }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            DrawerPage(
                                connectedName = connectedName,
                                connectedAddress = connectedAddress,
                                devices = scanDevices,
                                isScanning = isScanning,
                                isConnecting = isConnecting,
                                isConnected = connectedName != "未连接",
                                searchJustFinished = searchJustFinished,
                                onSearchClick = {
                                    if (isScanning) {
                                        // 搜索中点击 → 打断搜索
                                        isScanning = false
                                        searchJustFinished = true
                                        latestMessage = "搜索已手动停止。"
                                        doSpeak("搜索已停止"); doVibrate()
                                    } else if (hasBluetoothPermissions(context)) {
                                        isScanning = true
                                    } else {
                                        permissionRequestTrigger += 1
                                    }
                                },
                                onDeviceClick = onDeviceConnect,
                                onBackClick = {
                                    doSpeak("返回主页面"); doVibrate()
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        MainPage(
                            logEntries = logEntries,
                            latestMessage = latestMessage,
                            receiveHex = receiveHex,
                            onReceiveHexChange = {
                                receiveHex = it
                                val mode = if (it) "HEX" else "UTF8"
                                doSpeak("接收模式$mode"); doVibrate()
                            },
                            sendHex = sendHex,
                            onSendHexChange = {
                                sendHex = it
                                val mode = if (it) "HEX" else "UTF8"
                                doSpeak("发送模式$mode"); doVibrate()
                            },
                            sendInput = sendInput,
                            onSendInputChange = { sendInput = it },
                            connectedName = connectedName,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSendClick = {
                                if (connectedName == "未连接") {
                                    latestMessage = "发送失败：当前未连接蓝牙设备。"
                                } else if (sendHex && parseHexInput(sendInput) == null) {
                                    latestMessage = "发送失败：HEX 格式错误，请使用 AA 55 01 格式。"
                                } else {
                                    appendLog("TX", if (sendHex) sendInput else "\"$sendInput\"")
                                    latestMessage = "已发送指令。"
                                }
                            },
                            onClearLog = { logEntries.clear(); latestMessage = "接收区已清空。" }
                        )
                    }
                } else if (loaded) {
                    LoginPage(
                        account = account, password = password, code = code,
                        rememberPassword = rememberPassword, rememberCode = rememberCode,
                        loginError = loginError,
                        onAccountChange = { account = it; loginError = "" },
                        onPasswordChange = { password = it; loginError = "" },
                        onCodeChange = { code = it; loginError = "" },
                        onRememberPasswordChange = { rememberPassword = it },
                        onRememberCodeChange = { rememberCode = it },
                        onLogin = {
                            when {
                                account != EXPECTED_ACCOUNT -> { loginError = "账号错误：请输入正确账号 FYX"; latestMessage = loginError }
                                password != EXPECTED_PASSWORD -> { loginError = "密码错误：请重新输入登录密码"; latestMessage = loginError }
                                code != EXPECTED_CODE -> { loginError = "授权码错误：请联系开发工程师"; latestMessage = loginError }
                                else -> {
                                    loginError = ""; latestMessage = "登录成功，收发数据显示区已准备就绪。"
                                    isLoggedIn = true; doSpeak("登录成功"); doVibrate()
                                    prefs.edit()
                                        .putString(KEY_ACCOUNT, account)
                                        .putBoolean(KEY_REMEMBER, rememberPassword || rememberCode)
                                        .also { e ->
                                            if (rememberPassword) e.putString(KEY_PASSWORD, password)
                                            if (rememberCode) e.putString(KEY_CODE, code)
                                        }.apply()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── 登录页 ─────────────────────────────────────────────────────────

@Composable
private fun LoginPage(
    account: String, password: String, code: String,
    rememberPassword: Boolean, rememberCode: Boolean, loginError: String,
    onAccountChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit, onRememberPasswordChange: (Boolean) -> Unit,
    onRememberCodeChange: (Boolean) -> Unit, onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BeigeColors.background).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("欢迎使用小蜜蜂调试助手", style = MaterialTheme.typography.headlineSmall, color = BeigeColors.text, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)).background(BeigeColors.surface), contentAlignment = Alignment.Center) { Text("🐝", style = MaterialTheme.typography.displayLarge) }
        Spacer(modifier = Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BeigeColors.card), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(account, onAccountChange, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(password, onPasswordChange, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(code, onCodeChange, Modifier.fillMaxWidth(), label = { Text("授权码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.height(10.dp))
                RowCheckBox("记住密码", rememberPassword, onRememberPasswordChange)
                RowCheckBox("记住授权码", rememberCode, onRememberCodeChange)
                if (loginError.isNotBlank()) { Spacer(modifier = Modifier.height(12.dp)); Text(loginError, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium) }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = onLogin, Modifier.fillMaxWidth()) { Text("登录") }
            }
        }
    }
}

@Composable
private fun RowCheckBox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onCheckedChange); Text(label, color = BeigeColors.text) }
}

// ─── 主页面 ─────────────────────────────────────────────────────────

@Composable
private fun MainPage(
    logEntries: List<LogEntry>, latestMessage: String,
    receiveHex: Boolean, onReceiveHexChange: (Boolean) -> Unit,
    sendHex: Boolean, onSendHexChange: (Boolean) -> Unit,
    sendInput: String, onSendInputChange: (String) -> Unit,
    connectedName: String, onMenuClick: () -> Unit,
    onSendClick: () -> Unit, onClearLog: () -> Unit
) {
    val logListState = rememberLazyListState()
    LaunchedEffect(logEntries.size) { if (logEntries.isNotEmpty()) logListState.animateScrollToItem(logEntries.lastIndex) }

    Column(Modifier.fillMaxSize().background(BeigeColors.background)) {
        // 顶栏
        Row(Modifier.fillMaxWidth().background(BeigeColors.topBar).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("☰", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { onMenuClick() })
            Text("小蜜蜂调试助手", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = if (connectedName != "未连接") Color(0xFF2E7D32) else BeigeColors.offline, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp)); Text(connectedName, color = Color(0xFFFFD9D9), style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            // 收发日志标题
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("收发日志", style = MaterialTheme.typography.titleSmall, color = BeigeColors.text, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (receiveHex) "HEX" else "UTF-8", color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    CompactSwitch(receiveHex, onReceiveHexChange)
                    Spacer(Modifier.width(14.dp))
                    // 清空按钮（高亮）
                    Text("清空", color = Color.White, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clickable { onClearLog() }
                            .background(Color(0xFFC28B4C), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFA07030), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // 日志框
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = BeigeColors.surface), shape = RoundedCornerShape(0.dp)) {
                Box(Modifier.fillMaxSize().border(1.dp, BeigeColors.border).padding(horizontal = 10.dp, vertical = 10.dp)) {
                    if (logEntries.isEmpty()) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无收发数据", color = BeigeColors.hint, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
                            Spacer(Modifier.height(6.dp)); Text(latestMessage, color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), state = logListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(logEntries) { e -> Text("[${e.timestamp}] ${e.direction}: ${e.text}", color = BeigeColors.text, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 发送模式
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("发送模式", color = BeigeColors.text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp)); Text(if (sendHex) "HEX" else "UTF-8", color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp)); CompactSwitch(sendHex, onSendHexChange)
                }
            }
            Spacer(Modifier.height(8.dp))
            // 输入发送
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(sendInput, onSendInputChange, Modifier.weight(1f).height(50.dp),
                    placeholder = { Text(if (sendHex) "输入HEX，如 AA 55 01" else "输入文本数据", color = BeigeColors.hint, fontSize = 13.sp) },
                    singleLine = true, shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
                Button(onClick = onSendClick, Modifier.height(50.dp).width(100.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeigeColors.primary)) { Text("▶ 发送", color = Color.White, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange, Modifier.size(width = 46.dp, height = 28.dp),
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BeigeColors.primary,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE9DDB8)))
}

// ─── 侧滑栏 ─────────────────────────────────────────────────────────

@Composable
private fun DrawerPage(
    connectedName: String, connectedAddress: String,
    devices: List<BleDeviceItem>,
    isScanning: Boolean, isConnecting: Boolean, isConnected: Boolean,
    searchJustFinished: Boolean,
    onSearchClick: () -> Unit, onDeviceClick: (BleDeviceItem) -> Unit, onBackClick: () -> Unit
) {
    val buttonText = when {
        isConnecting -> "连接中..."
        isScanning -> "搜索中...（点击停止）"
        searchJustFinished -> "搜索完成"
        isConnected -> "设备已连接"
        else -> "⌁  搜索蓝牙设备"
    }

    ModalDrawerSheet(Modifier.fillMaxHeight(), drawerContainerColor = BeigeColors.background) {
        Column(Modifier.fillMaxSize()) {
            // 顶部横幅 — 紧凑
            Column(Modifier.fillMaxWidth().background(BeigeColors.topBar).padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("小蜜蜂调试助手", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("BLE串口调试终端", color = Color(0xFFFFE2B0), style = MaterialTheme.typography.bodySmall)
                Text("v1.0.9", color = Color(0xFFFFE2B0), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))

            // 「返回主页面」— 紧凑
            Button(onClick = onBackClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(36.dp),
                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB89B5E))) {
                Text("← 返回主页面", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))

            // 连接状态 — 紧凑
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("连接状态", color = BeigeColors.text, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = if (isConnected) Color(0xFF2E7D32) else BeigeColors.offline, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(connectedName, color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp)); Text(connectedAddress, color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(14.dp))

            // 搜索按钮 — 紧凑
            Button(onClick = onSearchClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected && !isScanning && !searchJustFinished) Color(0xFF2E7D32) else BeigeColors.primary)) {
                Text(buttonText, color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))

            // 设备列表标题
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("附近设备", color = BeigeColors.text, style = MaterialTheme.typography.bodyMedium)
                Text("${devices.size} 台", color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp)); HorizontalDivider(color = BeigeColors.border)

            if (devices.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(if (isConnected) "设备已连接，可点上方按钮重新搜索"
                    else "未发现设备",
                        color = BeigeColors.hint, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { item ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable { onDeviceClick(item) },
                            colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = BeigeColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(item.address, color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${item.rssi} dBm", color = BeigeColors.hint, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── 工具函数 ────────────────────────────────────────────────────────

private fun requiredBluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
}

private fun hasBluetoothPermissions(context: Context): Boolean =
    requiredBluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun parseHexInput(input: String): ByteArray? {
    val n = input.trim().replace("\n", " ").replace("\r", " ")
    if (n.isBlank()) return byteArrayOf()
    return try { n.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray() } catch (_: Exception) { null }
}

// ─── BLE 扫描 ───────────────────────────────────────────────────────

private class BleHelper(private val context: Context) {
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutRunnable: Runnable? = null

    @SuppressLint("MissingPermission")
    fun startScan(onDeviceFound: (BleDeviceItem) -> Unit, onFinished: () -> Unit) {
        stopScan()
        val scanner = adapter?.bluetoothLeScanner ?: run { onFinished(); return }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device ?: return
                onDeviceFound(BleDeviceItem(d.name ?: "未知蓝牙设备", d.address, result.rssi, d))
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { r -> r.device?.let { onDeviceFound(BleDeviceItem(it.name ?: "未知蓝牙设备", it.address, r.rssi, it)) } }
            }
            override fun onScanFailed(@Suppress("UNUSED_PARAMETER") errorCode: Int) { onFinished() }
        }
        scanCallback = callback
        try { scanner.startScan(callback) } catch (_: Exception) { onFinished(); return }
        scanTimeoutRunnable = Runnable { stopScan(); onFinished() }.also { mainHandler.postDelayed(it, 10_000) }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }; scanTimeoutRunnable = null
        scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }; scanCallback = null
    }
}
