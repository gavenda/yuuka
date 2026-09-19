package dev.gavenda.yuuka.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.domain.nextColor
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** A list long enough to lose something in is searched rather than scrolled; the search box only shows from here. */
const val SEARCH_FROM = 8

data class TagsUiState(val tags: List<Tag> = emptyList(), val search: String = "") {
    val showSearch: Boolean get() = tags.size >= SEARCH_FROM

    val visible: List<Tag>
        get() {
            val needle = search.trim()
            return if (needle.isEmpty()) tags else tags.filter { it.name.contains(needle, ignoreCase = true) }
        }
}

/** Mirrors `TagsView.vue`. */
class TagsViewModel(private val ledgerRepository: LedgerRepository) : ViewModel() {
    private val search = MutableStateFlow("")

    val uiState: StateFlow<TagsUiState> = combine(ledgerRepository.tags, search, ::TagsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    /** Counts move whenever a transaction is saved, so the screen asks again each time it is shown. */
    fun refresh() {
        viewModelScope.launch { runCatching { ledgerRepository.refreshTags() } }
    }

    fun setSearch(text: String) {
        search.value = text
    }

    /** The slot a new tag should take, so defaults spread across the palette. */
    fun nextColor(): String = nextColor(uiState.value.tags.size)

    suspend fun createTag(name: String, color: String) = ledgerRepository.createTag(name, color)

    suspend fun updateTag(id: String, name: String, color: String) = ledgerRepository.updateTag(id, name, color)

    suspend fun deleteTag(id: String) = ledgerRepository.deleteTag(id)
}
