# 小蜜蜂调试助手Pro - ProGuard 混淆规则

# ─── 保留核心入口 ───
-keep class com.xmf.debugpro.** { *; }

# ─── Compose 相关 ───
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── 授权码模块（防混淆） ───
-keep class com.xmf.debugpro.Codes { *; }
-keep class com.xmf.debugpro.LicenseChecker { *; }

# ─── 保留 Activity 入口 ───
-keep class * extends android.app.Activity { *; }

# ─── 移除无用日志 ───
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ─── BLE 相关 ───
-keep class android.bluetooth.** { *; }

# ─── 保留序列化 ───
-keepclassmembers class * implements java.io.Serializable { *; }

# ─── Kotlin 协程 ───
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
