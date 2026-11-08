package dev.wordloom.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.local.ListWithCount
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun ListsScreen(
    lists: List<ListWithCount>,
    onBack: () -> Unit,
    onOpen: (ListWithCount) -> Unit,
    onCreate: (String) -> Unit,
) {
    val wl = WordloomTheme.colors
    var showCreate by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = wl.ink)
            }
            Text("词表", style = MaterialTheme.typography.titleLarge, color = wl.ink)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) {
                Text("新建", color = wl.primary)
            }
            Spacer(Modifier.width(6.dp))
        }

        if (lists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有词表", style = MaterialTheme.typography.titleMedium, color = wl.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "把查过的词按主题收进来，比如「GRE 高频」「读小说遇到的」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = wl.inkMuted,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(shape = RoundedCornerShape(50), color = wl.primary) {
                        Text(
                            "新建第一个词表",
                            style = MaterialTheme.typography.labelLarge,
                            color = wl.onPrimary,
                            modifier = Modifier
                                .clickable { showCreate = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = wl.surfaceAlt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(list) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = wl.ink,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${list.wordCount} 个词",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = wl.inkMuted,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = wl.inkMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建词表") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("例如：GRE 高频") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name)
                            showCreate = false
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
}
