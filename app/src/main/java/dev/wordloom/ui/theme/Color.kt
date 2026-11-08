package dev.wordloom.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens measured from the reference UI (see docs/DESIGN.md).
 * These are plain colour values, not brand assets.
 */
@Immutable
data class WlColors(
    val primary: Color,
    val primaryBright: Color,
    val primaryDeep: Color,
    val ink: Color,
    val inkMuted: Color,
    val coral: Color,
    val coralSoft: Color,
    val mint: Color,
    val mintInk: Color,
    val action: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val onPrimary: Color,
    /** True for the dark set — screens that override brand colours need to know. */
    val isDark: Boolean = false,
)

val LightWlColors = WlColors(
    primary = Color(0xFF005AFB),
    primaryBright = Color(0xFF189FFE),
    primaryDeep = Color(0xFF0139A3),
    ink = Color(0xFF162941),
    inkMuted = Color(0xFF5B6B82),
    coral = Color(0xFFF06989),
    coralSoft = Color(0xFFFFE5E9),
    mint = Color(0xFFD3FFF7),
    mintInk = Color(0xFF0E7A63),
    action = Color(0xFF43AA01),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF3F5F9),
    outline = Color(0xFFDCE3ED),
    onPrimary = Color(0xFFFFFFFF),
)

val DarkWlColors = WlColors(
    primary = Color(0xFF4EA9FE),
    primaryBright = Color(0xFF189FFE),
    primaryDeep = Color(0xFF0B2A5B),
    ink = Color(0xFFE9EFF7),
    inkMuted = Color(0xFF9AA9BE),
    coral = Color(0xFFF38EA5),
    coralSoft = Color(0xFF3A1D26),
    mint = Color(0xFF10312C),
    mintInk = Color(0xFF7FE3CB),
    action = Color(0xFF61C42A),
    surface = Color(0xFF0B1320),
    surfaceAlt = Color(0xFF162941),
    outline = Color(0xFF25364D),
    onPrimary = Color(0xFF04101F),
    isDark = true,
)
