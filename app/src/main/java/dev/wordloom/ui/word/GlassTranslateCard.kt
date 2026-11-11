package dev.wordloom.ui.word

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress

/**
 * The card that opens over a selection.
 *
 * One slab, one clip: shadow, frost, rim and content all belong to the same
 * rounded box, so no square layer can peek out at the corners. The blur is Haze
 * sampling the page in this window; the frost gradient keeps the middle calm so
 * the translation stays readable while the edges stay clear.
 */
@Composable
fun GlassTranslateCard(
    source: String,
    translation: String?,
    failure: String?,
    loading: Boolean,
    hazeState: HazeState,
    onCopy: () -> Unit,
    onCopySource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val glass = HazeStyle(
        backgroundColor = if (wl.isDark) {
            Color(0xFF0B1320).copy(alpha = 0.30f)
        } else {
            Color.White.copy(alpha = 0.30f)
        },
        tint = HazeTint(
            if (wl.isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.18f),
        ),
        blurRadius = 34.dp,
        noiseFactor = 0.035f,
    )
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (wl.isDark) 0.42f else 0.92f),
            Color.White.copy(alpha = 0.08f),
        ),
    )
    val shadowTint = if (wl.isDark) {
        Color.Black.copy(alpha = 0.55f)
    } else {
        Color(0xFF0139A3).copy(alpha = 0.30f)
    }
    val frost = if (wl.isDark) Color(0xFF0B1320) else Color.White

    Box(
        modifier = modifier
            .width(300.dp)
            .shadow(
                elevation = 24.dp,
                shape = shape,
                clip = false,
                ambientColor = shadowTint,
                spotColor = shadowTint,
            )
            .clip(shape)
            .hazeEffect(state = hazeState, style = glass)
            .border(BorderStroke(1.dp, edge), shape),
    ) {
        // Frosted in the middle, liquid at the edges: the text underneath
        // dissolves into a soft shadow where the card is thickest.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to frost.copy(alpha = 0.52f),
                        0.22f to frost.copy(alpha = 0.94f),
                        0.78f to frost.copy(alpha = 0.94f),
                        1f to frost.copy(alpha = 0.58f),
                    ),
                ),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal()
                Spacer(Modifier.size(9.dp))
                Text(
                    text = source,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = wl.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(11.dp))

            when {
                translation != null -> TranslationBody(translation)

                failure != null -> Text(
                    text = failure,
                    fontSize = 13.sp,
                    color = wl.coral,
                )

                loading || translation == null -> Column {
                    Text(
                        text = "翻译中…",
                        fontFamily = SerifFamily,
                        fontSize = 17.sp,
                        color = wl.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "首次使用会下载离线模型",
                        fontSize = 11.sp,
                        color = wl.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(wl.ink.copy(alpha = 0.07f)),
            )
            Spacer(Modifier.height(9.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CopyAction(
                    badge = { SourceBadge() },
                    label = "复制原文",
                    onClick = onCopySource,
                )
                if (translation != null) {
                    CopyAction(
                        badge = { TranslationBadge() },
                        label = "复制译文",
                        onClick = onCopy,
                    )
                }
            }
        }
    }
}

/**
 * Youdao returns one long line for a word ("adj. 明确的…；n. 细节…"). Broken into
 * its parts of speech it becomes a dictionary entry: a narrow label column, and
 * a sense column that keeps a single measure so the eye can run down it.
 */
private val PartOfSpeech = Regex(
    """(adj|adv|n|v|vt|vi|prep|conj|pron|num|art|int|aux|abbr|phr)\.\s*""",
)

private fun senseGroups(text: String): List<Pair<String, String>> {
    val marks = PartOfSpeech.findAll(text).toList()
    if (marks.isEmpty()) return emptyList()
    return marks.mapIndexedNotNull { index, mark ->
        val end = marks.getOrNull(index + 1)?.range?.first ?: text.length
        val body = text.substring(mark.range.last + 1, end)
            .trim()
            .trim('；', ';', '，', ',')
            .trim()
        if (body.isEmpty()) null else mark.groupValues[1] to body
    }
}

@Composable
private fun TranslationBody(translation: String) {
    val wl = WordloomTheme.colors
    val groups = remember(translation) { senseGroups(translation) }

    if (groups.isEmpty()) {
        Text(
            text = translation,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            color = wl.ink,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { (pos, sense) ->
            Row {
                Text(
                    text = pos,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                    lineHeight = 22.sp,
                    color = wl.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.width(30.dp),
                )
                Text(
                    text = sense,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = wl.ink,
                )
            }
        }
    }
}

/**
 * Two copies, two marks. The badge says which side of the glass you are taking:
 * a small EN tile for the source, the coral seal for the translation. Labels keep
 * the verb ("复制…") because that is what the tap does; the badges are what keep
 * the pair from reading as the same button twice.
 */
@Composable
private fun CopyAction(badge: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .wlPress(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        badge()
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 12.5.sp,
            color = WordloomTheme.colors.ink,
        )
    }
}

/**
 * Badge type is centred optically, not by layout: Compose keeps the font's
 * ascent/descent padding unless it is turned off, which pushes small glyphs
 * down and right inside a tiny square.
 */
private fun badgeStyle(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontSize = size,
    lineHeight = size,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun SourceBadge() {
    val wl = WordloomTheme.colors
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(wl.primary.copy(alpha = 0.10f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "EN",
            style = badgeStyle(8.sp),
            color = wl.primary,
        )
    }
}

@Composable
private fun TranslationBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(Color(0xFFC8453E), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "译",
            style = badgeStyle(10.sp).copy(fontFamily = SerifFamily),
            color = Color.White,
        )
    }
}

/** A translator's seal: the one warm mark on an otherwise cool card. */
@Composable
private fun Seal() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(Color(0xFFC8453E), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "译",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.White,
        )
    }
}
