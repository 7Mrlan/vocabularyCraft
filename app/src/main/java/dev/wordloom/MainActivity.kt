package dev.wordloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
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
            WordloomTheme {
                val vm: WordloomViewModel = viewModel(factory = remember { RepositoryFactory(repository) })
                AppNav(vm)
            }
        }
    }
}

private class RepositoryFactory(private val repository: WordRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WordloomViewModel(repository) as T
}
