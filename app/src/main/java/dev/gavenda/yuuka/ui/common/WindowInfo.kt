package dev.gavenda.yuuka.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/** True on landscape phones and tablets, where rows have room to spare and hero figures no longer need to be as large. */
@Composable
@ReadOnlyComposable
fun rememberIsWideLayout(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
}
