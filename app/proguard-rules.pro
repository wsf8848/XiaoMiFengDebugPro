# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#   小蜜蜂调试助手Pro — ProGuard 混淆规则
#   安全等级：最高（v1.8.0 安全加固版）
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# ─── 优化配置 ───
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-flattenpackagehierarchy
-overloadaggressively
-useuniqueclassmembernames
-dontpreverify

# ─── 移除所有日志（发布版零日志输出） ───
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# ─── 保留 Android 框架入口（必须） ───
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ─── 保留 Compose 运行时（框架反射需要） ───
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── 保留 Kotlin 协程 ───
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ─── BLE API ───
-keep class android.bluetooth.** { *; }

# ─── 保留 R 文件 ───
-keep class **.R
-keep class **.R$* { *; }

# ─── 保留序列化 ───
-keepclassmembers class * implements java.io.Serializable { *; }

# ─── 保留枚举（ProGuard 对枚举特殊处理） ───
-keepclassmembers enum * { *; }

# ─── 保留注解 ───
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# ─── 保留泛型 ───
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute ''

# ─── 输出映射文件（用于反混淆崩溃堆栈） ───
-printmapping build/outputs/mapping/release/mapping.txt

# ─── 避免混淆可能导致的问题 ───
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── 移除无用资源 ───
-assumenoexternalsideeffects class java.lang.String {
    public java.lang.String();
    public java.lang.String(byte[]);
    public java.lang.String(byte[], java.lang.String);
    public java.lang.String(byte[], int, int);
    public java.lang.String(byte[], int, int, java.lang.String);
}

# ─── 反射警告忽略 ───
-dontwarn javax.annotation.**
-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
