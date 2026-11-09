package dev.wordloom.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.local.ListWithCount
import dev.wordloom.ui.components.PoolHeader
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

/**
 * Lists get the same navy pool as search, then a stack of index cards: each card
 * carries the list's initial set in serif, so a shelf of lists reads like a set of
 * folders rather than a grey table.
 */
@Composable
fun ListsScreen(
    lists: List<ListWithCount>,
    onBack: () -> Unit,
    onOpen: (ListWithCount) -> Unit,
    onCreate: (String) -> Unit,
) {
    val wl = WordloomTheme.colors
    var showCreate by remember { mutableStateOf(false) }
    val totalWords = lists.sumOf { it.wordCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface),
    ) {
        PoolHeader(height = 200.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.wl_back_knockout),
                        contentDescription = "返回",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                        .clickable { showCreate = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.wl_plus),
                        contentDescription = "新建词表",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "词表",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (lists.isEmpty()) "把查过的词按主题收进来"
                    else "${lists.size} 个词表 · $totalWords 个词",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(46.dp))
        }

        if (lists.isEmpty()) {
            EmptyLists(onCreate = { showCreate = true })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 2.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(lists, key = { _, list -> list.id }) { index, list ->
                    ListCard(
                        list = list,
                        index = index,
                        onClick = { onOpen(list) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        WlSheet(onDismiss = { showCreate = false }) {
            Text(
                text = "新建词表",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "给这组词起个名字，之后随时往里加。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = wl.inkMuted,
            )
            Spacer(Modifier.height(16.dp))
            WlNameField(
                value = name,
                onValueChange = { name = it },
                placeholder = "例如：GRE 高频",
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                WlPillButton(
                    label = "创建",
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreate(name.trim())
                        showCreate = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ListCard(
    list: ListWithCount,
    index: Int,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    // Folders rotate through the same four tones the home cards use.
    val blueTint = if (wl.isDark) wl.surface else Color(0xFFE3EFFB)
    val (monoTint, monoInk) = when (index % 4) {
        0 -> blueTint to (if (wl.isDark) wl.primary else wl.primaryDeep)
        1 -> wl.coralSoft to wl.coral
        2 -> wl.mint to wl.mintInk
        else -> blueTint to wl.action
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(wl.surfaceAlt, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(monoTint, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = list.name.trim().take(1).ifBlank { "·" },
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = monoInk,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = list.name,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = wl.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${list.wordCount} 个词",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = wl.inkMuted,
            )
        }
        Icon(
            painter = painterResource(R.drawable.wl_chevron),
            contentDescription = null,
            tint = wl.inkMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyLists(onCreate: () -> Unit) {
    val wl = WordloomTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(wl.surfaceAlt, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.wl_book),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "还没有词表",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "把查过的词按主题收进来，比如「GRE 高频」「读小说遇到的」。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = wl.inkMuted,
                modifier = Modifier
                    .width(268.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.height(22.dp))
            Surface(
                shape = CircleShape,
                color = wl.primary,
                modifier = Modifier.clickable(onClick = onCreate),
            ) {
                Text(
                    text = "新建第一个词表",
                    style = MaterialTheme.typography.labelLarge,
                    color = wl.onPrimary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
        }
    }
}
