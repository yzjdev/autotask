package com.yzjdev.autogkd.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 自定义品牌配色:多彩配色方案 —— 紫罗兰主色、青色辅色、玫红三级色,
 * 替代动态取色,保证不同壁纸/主题下应用观感一致且活泼。
 */

// ---- 浅色 ----
private val LightPrimary = Color(0xFF6750A4)          // 紫罗兰
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFEADDFF)
private val LightOnPrimaryContainer = Color(0xFF21005D)
private val LightSecondary = Color(0xFF00838F)        // 青色
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFB3EBF2)
private val LightOnSecondaryContainer = Color(0xFF002022)
private val LightTertiary = Color(0xFFC2185B)         // 玫红
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFD8E4)
private val LightOnTertiaryContainer = Color(0xFF3E001D)
private val LightBackground = Color(0xFFFFFBFE)
private val LightOnBackground = Color(0xFF1C1B1F)
private val LightSurface = Color(0xFFFFFBFE)
private val LightOnSurface = Color(0xFF1C1B1F)
private val LightSurfaceVariant = Color(0xFFE7E0EC)
private val LightOnSurfaceVariant = Color(0xFF49454F)
private val LightOutline = Color(0xFF79747E)
private val LightOutlineVariant = Color(0xFFCAC4D0)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

// ---- 深色 ----
private val DarkPrimary = Color(0xFFD0BCFF)           // 亮紫
private val DarkOnPrimary = Color(0xFF381E72)
private val DarkPrimaryContainer = Color(0xFF4F378B)
private val DarkOnPrimaryContainer = Color(0xFFEADDFF)
private val DarkSecondary = Color(0xFF4DD0E1)         // 亮青
private val DarkOnSecondary = Color(0xFF00363A)
private val DarkSecondaryContainer = Color(0xFF004F55)
private val DarkOnSecondaryContainer = Color(0xFFB3EBF2)
private val DarkTertiary = Color(0xFFFFB0C8)          // 粉
private val DarkOnTertiary = Color(0xFF650033)
private val DarkTertiaryContainer = Color(0xFF8E004A)
private val DarkOnTertiaryContainer = Color(0xFFFFD8E4)
private val DarkBackground = Color(0xFF1C1B1F)
private val DarkOnBackground = Color(0xFFE6E1E5)
private val DarkSurface = Color(0xFF1C1B1F)
private val DarkOnSurface = Color(0xFFE6E1E5)
private val DarkSurfaceVariant = Color(0xFF49454F)
private val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
private val DarkOutline = Color(0xFF938F99)
private val DarkOutlineVariant = Color(0xFF49454F)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

val BrandLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

val BrandDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)
