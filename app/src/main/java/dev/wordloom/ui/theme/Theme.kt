package dev.wordloom.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalWlColors = staticCompositionLocalOf { LightWlColors }

object WordloomTheme {
    val colors: WlColors
        @Composable get() = LocalWlColors.current
}

@Composable
fun WordloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val wl = if (darkTheme) DarkWlColors else LightWlColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = wl.primary,
            onPrimary = wl.onPrimary,
            background = wl.surface,
            onBackground = wl.ink,
            surface = wl.surface,
            onSurface = wl.ink,
            surfaceVariant = wl.surfaceAlt,
            onSurfaceVariant = wl.inkMuted,
            outline = wl.outline,
            secondary = wl.coral,
            tertiary = wl.action,
        )
    } else {
        lightColorScheme(
            primary = wl.primary,
            onPrimary = wl.onPrimary,
            background = wl.surface,
            onBackground = wl.ink,
            surface = wl.surface,
            onSurface = wl.ink,
            surfaceVariant = wl.surfaceAlt,
            onSurfaceVariant = wl.inkMuted,
            outline = wl.outline,
            secondary = wl.coral,
            tertiary = wl.action,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalWlColors provides wl) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WlTypography,
            content = content,
        )
    }
}

val Transparent = Color(0x00000000)
