package dev.gavenda.yuuka.ui

import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        val stack = state.backStacks[route]
        when {
            // The top-level route on show, picked again, goes back to its root: Settings from a screen
            // it opened is the way back to it.
            route == state.topLevelRoute -> stack?.subList(1, stack.size)?.clear()
            // Another top-level route: just switch to it, its history as it was left.
            stack != null -> state.topLevelRoute = route
            // Already on show: with Settings and Account types side by side, the row that opened it is still there to press.
            state.currentRoute == route -> Unit
            else -> state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
