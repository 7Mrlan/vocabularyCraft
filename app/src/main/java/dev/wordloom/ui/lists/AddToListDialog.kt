package dev.wordloom.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.local.ListWithCount
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun AddToListDialog(
    word: String,
    lists: List<ListWithCount>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String, (Long) -> Unit) -> Unit,
) {
    val wl = WordloomTheme.colors
    var newName by remember { mutableStateOf("") }

    WlSheet(onDismiss = onDismiss) {
        Text(
            text = "加入词表",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "“$word”",
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            color = wl.inkMuted,
        )
        Spacer(Modifier.height(16.dp))

        if (lists.isEmpty()) {
            Text(
                text = "还没有词表，在下面新建一个。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = wl.inkMuted,
            )
        } else {
            lists.forEach { list ->
                ListPickRow(list = list, onClick = { onPick(list.id) })
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = wl.outline.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            WlNameField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "新建词表…",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            WlPillButton(
                label = "创建",
                enabled = newName.isNotBlank(),
                onClick = { onCreate(newName.trim()) { id -> onPick(id) } },
            )
        }
    }
}

@Composable
private fun ListPickRow(list: ListWithCount, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(wl.surfaceAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = list.name.trim().take(1).ifBlank { "·" },
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (wl.isDark) wl.primary else wl.primaryDeep,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = list.name,
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            color = wl.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${list.wordCount}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = wl.inkMuted,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.wl_chevron),
            contentDescription = null,
            tint = wl.inkMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A quiet input: no box outline, just a tonal field with a serif-adjacent voice. */
@Composable
fun WlNameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = SansFamily,
            fontSize = 16.sp,
            color = wl.ink,
        ),
        cursorBrush = SolidColor(wl.primary),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wl.surfaceAlt, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = wl.inkMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun WlPillButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Surface(
        shape = CircleShape,
        color = if (enabled) wl.primary else wl.primary.copy(alpha = 0.35f),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = wl.onPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }
}
