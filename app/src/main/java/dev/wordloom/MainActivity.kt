package dev.wordloom

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        val prefs = getSharedPreferences("wordloom", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", "system") ?: "system"
        val dark = when (savedMode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        // The platform window paints before Compose does (first frame, rotation,
        // process restore). Match the reader's saved mode, not just the system's,
        // so the fallback never flashes the opposite colour.
        window.setBackgroundDrawable(
            ColorDrawable(if (dark) 0xFF0B1320.toInt() else 0xFFFFFFFF.toInt()),
        )

        enableEdgeToEdge()
        hideStatusBar()

        val container = (application as WordloomApp).container

        setContent {
            // The brand mark in the header toggles this. "system" is only the
            // starting point; once the reader picks a side, we remember it.
            var mode by remember { mutableStateOf(savedMode) }
            val dark = when (mode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            WordloomTheme(darkTheme = dark) {
                val factory = remember {
                    RepositoryFactory(
                        container.repository,
                        container.wordBooks,
                        container.bookSelection,
                        container.audioCache,
                        container.speech,
                    )
                }
                val vm: WordloomViewModel = viewModel(factory = factory)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System UI can come back after the keyboard, a permission dialog or a
        // trip to the background; put the top edge back into the page.
        if (hasFocus) hideStatusBar()
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private class RepositoryFactory(
    private val repository: WordRepository,
    private val wordBooks: dev.wordloom.data.wordbook.WordBookStore,
    private val bookSelection: dev.wordloom.data.wordbook.WordBookSelection,
    private val audioCache: dev.wordloom.data.audio.AudioCache,
    private val speech: dev.wordloom.data.tts.SpeechService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WordloomViewModel(repository, wordBooks, bookSelection, audioCache, speech) as T
}
