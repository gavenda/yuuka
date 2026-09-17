package dev.gavenda.yuuka.ui.common

/** The loading/error shape every screen's view model carries, mirroring each Pinia store's own `loading`/`error` refs. */
sealed interface ScreenStatus {
    data object Idle : ScreenStatus
    data object Loading : ScreenStatus
    data class Error(val message: String) : ScreenStatus
}
