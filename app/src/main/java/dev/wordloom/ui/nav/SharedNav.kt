@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package dev.wordloom.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Navigation's AnimatedContent scope and the shared-element scope, made reachable
 * from inside screens so a word can travel from a search row into the word page
 * without threading two parameters through every composable.
 */
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
