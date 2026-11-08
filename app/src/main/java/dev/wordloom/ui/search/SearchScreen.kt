package dev.wordloom.ui.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wordloom.data.model.Suggestion
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun SearchScreen(
    suggestions: List<Suggestion>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val wl = WordloomTheme.colors
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = wl.ink)
            }
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("搜索单词", color = wl.inkMuted) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = wl.primary)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = wl.surfaceAlt,
                    unfocusedContainerColor = wl.surfaceAlt,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) onSubmit(query)
                }),
            )
        }

        Spacer(Modifier.height(6.dp))

        if (suggestions.isEmpty()) {
            Text(
                text = "输入英文单词开始查询。查过的词会缓存在本机，可离线再看。",
                style = MaterialTheme.typography.bodyMedium,
                color = wl.inkMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(suggestions, key = { it.word }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubmit(item.word) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = wl.ink,
                    )
                    if (item.definition.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = wl.inkMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = wl.outline)
                }
            }
        }
    }
}

@Composable
fun SearchEmptyHint(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.Transparent) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text("没有找到结果", color = WordloomTheme.colors.inkMuted)
        }
    }
}
