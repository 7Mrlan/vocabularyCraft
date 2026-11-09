package dev.wordloom

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wordloom.data.WordRepository
import dev.wordloom.ui.AppNav
import dev.wordloom.ui.WordloomViewModel
import dev.wordloom.ui.theme.WordloomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as WordloomApp).container.repository

        setContent {
            val context = LocalContext.current
            // The brand mark in the header toggles this. "system" is only the
            // starting point; once the reader picks a side, we remember it.
            val prefs = remember {
                context.getSharedPreferences("wordloom", Context.MODE_PRIVATE)
            }
            var mode by remember {
                mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
            }
            val dark = when (mode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            WordloomTheme(darkTheme = dark) {
                val vm: WordloomViewModel = viewModel(factory = remember { RepositoryFactory(repository) })
                AppNav(
                    viewModel = vm,
                    onToggleTheme = {
                        val next = if (dark) "light" else "dark"
                        mode = next
                        prefs.edit().putString("theme_mode", next).apply()
                    },
                )
            }
        }
    }
}

private class RepositoryFactory(private val repository: WordRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WordloomViewModel(repository) as T
}
