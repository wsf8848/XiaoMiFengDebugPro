package com.xmf.debugpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BeigeColors {
    val background = Color(0xFFF8F0D8)
    val surface = Color(0xFFFFFAEE)
    val card = Color(0xFFFFF6E3)
    val text = Color(0xFF5C4724)
    val primary = Color(0xFFFFB625)
    val primaryDark = Color(0xFFE69814)
    val topBar = Color(0xFFFF8A00)
    val hint = Color(0xFFB7A88A)
    val border = Color(0xFFF0DEB5)
    val offline = Color(0xFFD64545)
}

private val LightColors = lightColorScheme(
    primary = BeigeColors.primary,
    onPrimary = Color.White,
    secondary = BeigeColors.primaryDark,
    background = BeigeColors.background,
    onBackground = BeigeColors.text,
    surface = BeigeColors.surface,
    onSurface = BeigeColors.text
)

@Composable
fun XiaoMiFengTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
