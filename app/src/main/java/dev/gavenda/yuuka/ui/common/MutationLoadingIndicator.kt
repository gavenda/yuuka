package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Material 3 Expressive shape-morphing indicator, sized to stand in for a button's or icon button's label while a mutation is in flight. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MutationLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
) {
    LoadingIndicator(modifier = modifier.size(size), color = color)
}
