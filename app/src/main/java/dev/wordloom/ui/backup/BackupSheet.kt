package dev.wordloom.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

/**
 * Two rows and a footnote. The file itself is written through the system picker,
 * so nothing leaves the device and no storage permission is needed.
 */
@Composable
fun BackupSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val wl = WordloomTheme.colors
    WlSheet(onDismiss = onDismiss) {
        Text(
            text = "备份与恢复",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "复习进度、词表和查过的词，存成一个文件。",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = wl.inkMuted,
        )
        Spacer(Modifier.height(18.dp))

        ActionRow(
            title = "导出到文件",
            subtitle = "存进「文件」App，换机时再导回来",
            enabled = !busy,
            onClick = onExport,
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(
            title = "从文件恢复",
            subtitle = "合并导入，不会覆盖现有数据",
            enabled = !busy,
            onClick = onImport,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (busy) "正在处理…" else "文件只在本机读写，不上传任何地方。",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = wl.inkMuted,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = SerifFamily,
                fontSize = 17.sp,
                color = if (enabled) wl.ink else wl.inkMuted,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = wl.inkMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            painter = painterResource(R.drawable.wl_chevron),
            contentDescription = null,
            tint = wl.inkMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}
