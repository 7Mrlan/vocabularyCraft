package dev.wordloom.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wordloom.data.local.ListWithCount
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("把 “$word” 加入词表") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (lists.isEmpty()) {
                    Text(
                        "还没有词表，先在下面建一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = wl.inkMuted,
                    )
                } else {
                    lists.forEach { list ->
                        TextButton(
                            onClick = { onPick(list.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${list.name}（${list.wordCount}）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = wl.ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("新建词表") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName) { id -> onPick(id) }
                        }
                    },
                ) { Text("创建并加入") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
