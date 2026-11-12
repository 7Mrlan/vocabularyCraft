package dev.wordloom.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import dev.wordloom.data.wordbook.AutoBook
import dev.wordloom.data.wordbook.WordBook
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap

enum class CoverArt { Star, Chevrons, Peak, Sprout, Ribbon, Waves }

data class BookCoverSpec(
    val id: String,
    /** Two lines are allowed; the deck keeps them aligned. */
    val title: String,
    val kicker: String,
    val subtitle: String,
    val count: String,
    val countNote: String,
    val top: Color,
    val bottom: Color,
    val art: CoverArt,
)

/**
 * The shelf, in reading order. Copy and colour are fixed per volume; the words
 * behind each cover come from its asset file, keyed by [id].
 */
val Bookshelf: List<BookCoverSpec> = listOf(
    BookCoverSpec(
        id = WordBook.Core.name,
        title = "通用\n核心",
        kicker = "词书 · 第一卷",
        subtitle = "日常与考试通用",
        count = WordBook.Core.count.toString(),
        countNote = "词 · 词频排序",
        top = Color(0xFF2ACB9E),
        bottom = Color(0xFF0FA57F),
        art = CoverArt.Star,
    ),
    BookCoverSpec(
        id = WordBook.Cet.name,
        title = "四六级\n核心",
        kicker = "词书 · 第二卷",
        subtitle = "CET-4/6 大纲裁剪",
        count = WordBook.Cet.count.toString(),
        countNote = "词 · CET 4/6",
        top = Color(0xFFFF9648),
        bottom = Color(0xFFF2671B),
        art = CoverArt.Chevrons,
    ),
    BookCoverSpec(
        id = WordBook.Kaoyan.name,
        title = "考研\n核心",
        kicker = "词书 · 第三卷",
        subtitle = "考研大纲裁剪",
        count = WordBook.Kaoyan.count.toString(),
        countNote = "词 · 考研大纲",
        top = Color(0xFF8A79FF),
        bottom = Color(0xFF5B45E8),
        art = CoverArt.Peak,
    ),
    // The three books that write themselves. Their counts are filled in at
    // runtime from the reader's own history.
    BookCoverSpec(
        id = AutoBook.Looked.id,
        title = "查过\n的词",
        kicker = AutoBook.Looked.kicker,
        subtitle = AutoBook.Looked.subtitle,
        count = "0",
        countNote = AutoBook.Looked.countNote,
        top = Color(0xFF4EA9FE),
        bottom = Color(0xFF005AFB),
        art = CoverArt.Waves,
    ),
    BookCoverSpec(
        id = AutoBook.Wrong.id,
        title = "错词\n本",
        kicker = AutoBook.Wrong.kicker,
        subtitle = AutoBook.Wrong.subtitle,
        count = "0",
        countNote = AutoBook.Wrong.countNote,
        top = Color(0xFFFF7E90),
        bottom = Color(0xFFEC4A63),
        art = CoverArt.Sprout,
    ),
    BookCoverSpec(
        id = AutoBook.Saved.id,
        title = "收藏\n夹",
        kicker = AutoBook.Saved.kicker,
        subtitle = AutoBook.Saved.subtitle,
        count = "0",
        countNote = AutoBook.Saved.countNote,
        top = Color(0xFF3E5A86),
        bottom = Color(0xFF1B2C4A),
        art = CoverArt.Ribbon,
    ),
)

/**
 * A book cover drawn entirely with Canvas: one bright field, one large white
 * illustration bleeding off the right edge, and type in white. The three layers
 * (field, art, type) are separate so the deck can slide them against each other
 * when the card tilts — that tiny parallax is what makes it read as an object.
 */
@Composable
fun BookCover(
    spec: BookCoverSpec,
    modifier: Modifier = Modifier,
    inReading: Boolean = false,
    artParallax: Float = 0f,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = Color(0xFF0139A3).copy(alpha = 0.16f),
                spotColor = Color(0xFF0139A3).copy(alpha = 0.22f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(spec.top, spec.bottom))),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = artParallax * 10f * density },
        ) {
            // page edge, a lighter slip on the left
            drawRoundRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(0f, 10.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height - 20.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
            // top-left light
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.9f, size.height * 0.7f),
                ),
            )
            drawCoverArt(spec.art)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 15.dp),
        ) {
            Text(
                text = spec.kicker,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 9.5.sp,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = spec.title,
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = spec.subtitle,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.38f)),
            )
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = spec.count,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = spec.countNote,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }

        if (inReading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 15.dp, end = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "在读",
                    color = Color.White,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

/** The illustration sits in a 238-unit box, anchored bottom-right and cropped. */
private fun DrawScope.drawCoverArt(art: CoverArt) {
    val scaleFactor = size.width / 238f
    translate(left = size.width * 0.16f, top = size.height * 0.40f) {
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            when (art) {
                CoverArt.Star -> starArt()
                CoverArt.Chevrons -> chevronsArt()
                CoverArt.Peak -> peakArt()
                CoverArt.Sprout -> sproutArt()
                CoverArt.Ribbon -> ribbonArt()
                CoverArt.Waves -> wavesArt()
            }
        }
    }
}

private fun DrawScope.starArt() {
    drawCircle(Color.White.copy(alpha = 0.14f), radius = 66f, center = Offset(128f, 112f))
    drawPath(
        path = Path().apply {
            moveTo(156f, 10f); lineTo(178f, 82f); lineTo(250f, 104f); lineTo(178f, 126f)
            lineTo(156f, 198f); lineTo(134f, 126f); lineTo(62f, 104f); lineTo(134f, 82f)
            close()
        },
        color = Color.White,
    )
    drawPath(
        path = Path().apply {
            moveTo(92f, 54f); lineTo(102f, 88f); lineTo(136f, 98f); lineTo(102f, 108f)
            lineTo(92f, 142f); lineTo(82f, 108f); lineTo(48f, 98f); lineTo(82f, 88f)
            close()
        },
        color = Color.White.copy(alpha = 0.50f),
    )
}

private fun DrawScope.chevronsArt() {
    drawPath(
        path = Path().apply { moveTo(26f, 182f); lineTo(104f, 108f); lineTo(170f, 174f); close() },
        color = Color.White.copy(alpha = 0.16f),
    )
    drawPath(
        path = Path().apply {
            moveTo(54f, 138f); lineTo(122f, 70f); lineTo(190f, 138f)
            lineTo(172f, 156f); lineTo(122f, 106f); lineTo(72f, 156f); close()
        },
        color = Color.White,
    )
    drawPath(
        path = Path().apply {
            moveTo(82f, 100f); lineTo(138f, 44f); lineTo(194f, 100f)
            lineTo(178f, 116f); lineTo(138f, 76f); lineTo(98f, 116f); close()
        },
        color = Color.White.copy(alpha = 0.80f),
    )
    drawPath(
        path = Path().apply {
            moveTo(104f, 64f); lineTo(146f, 22f); lineTo(188f, 64f)
            lineTo(174f, 78f); lineTo(146f, 50f); lineTo(118f, 78f); close()
        },
        color = Color.White.copy(alpha = 0.55f),
    )
}

private fun DrawScope.peakArt() {
    drawCircle(Color.White.copy(alpha = 0.16f), radius = 60f, center = Offset(152f, 94f))
    val moon = Path().apply { addOval(Rect(106f, 50f, 198f, 142f)) }
    val bite = Path().apply { addOval(Rect(124f, 34f, 216f, 126f)) }
    drawPath(Path.combine(PathOperation.Difference, moon, bite), color = Color.White)
    drawPath(
        path = Path().apply {
            moveTo(16f, 194f); lineTo(94f, 84f); lineTo(132f, 138f)
            lineTo(156f, 106f); lineTo(220f, 194f); close()
        },
        color = Color.White.copy(alpha = 0.95f),
    )
    drawPath(
        path = Path().apply { moveTo(94f, 84f); lineTo(112f, 108f); lineTo(86f, 108f); close() },
        color = Color.White.copy(alpha = 0.45f),
    )
}

private fun DrawScope.sproutArt() {
    drawLine(
        color = Color.White,
        start = Offset(118f, 202f),
        end = Offset(118f, 98f),
        strokeWidth = 11f,
    )
    drawPath(
        path = Path().apply {
            moveTo(118f, 120f)
            cubicTo(118f, 78f, 88f, 52f, 42f, 46f)
            cubicTo(38f, 94f, 70f, 120f, 118f, 120f)
            close()
        },
        color = Color.White,
    )
    drawPath(
        path = Path().apply {
            moveTo(120f, 154f)
            cubicTo(120f, 118f, 148f, 96f, 192f, 92f)
            cubicTo(194f, 132f, 166f, 154f, 120f, 154f)
            close()
        },
        color = Color.White.copy(alpha = 0.80f),
    )
    drawCircle(Color.White.copy(alpha = 0.20f), radius = 17f, center = Offset(118f, 202f))
}

private fun DrawScope.ribbonArt() {
    drawCircle(Color.White.copy(alpha = 0.12f), radius = 74f, center = Offset(150f, 120f))
    drawPath(
        path = Path().apply {
            moveTo(112f, 24f); lineTo(206f, 24f); lineTo(206f, 206f)
            lineTo(159f, 164f); lineTo(112f, 206f); close()
        },
        color = Color.White,
    )
    drawPath(
        path = Path().apply {
            moveTo(66f, 74f); lineTo(74f, 102f); lineTo(102f, 110f); lineTo(74f, 118f)
            lineTo(66f, 146f); lineTo(58f, 118f); lineTo(30f, 110f); lineTo(58f, 102f); close()
        },
        color = Color.White.copy(alpha = 0.55f),
    )
}

private fun DrawScope.wavesArt() {
    val center = Offset(150f, 202f)
    listOf(52f, 100f, 148f).forEachIndexed { index, radius ->
        drawArc(
            color = Color.White.copy(alpha = 0.9f - index * 0.28f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 16f, cap = StrokeCap.Round),
        )
    }
}
