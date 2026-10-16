package dev.gavenda.yuuka.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A place in the navigation rail that the screen on show fills — its FAB, its month — mirroring
 * `src/lib/fab.ts` and `src/lib/monthAction.ts`. Screens fill it through [Register]; the rail reads [value].
 *
 * Two screens can be composed at once while one slides in over the other, so a registration is owned: the
 * newest one to arrive holds the slot, later compositions only change what it holds, and leaving clears the
 * slot only if it is still that registration's. The outgoing screen cannot take the incoming one's place.
 */
@Stable
class RailSlot<T : Any> {
    private class Holder<T>(value: T) {
        var value by mutableStateOf(value)
    }

    private var holder by mutableStateOf<Holder<T>?>(null)

    val value: T? get() = holder?.value

    /** Puts [value] in the slot for as long as this is composed. */
    @Composable
    fun Register(value: T) {
        val mine = remember { Holder(value) }
        SideEffect { mine.value = value }
        DisposableEffect(mine) {
            holder = mine
            onDispose { if (holder === mine) holder = null }
        }
    }
}
