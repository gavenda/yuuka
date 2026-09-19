package dev.gavenda.yuuka.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.domain.nextColor
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryFamily(val parent: Category, val children: List<Category>)

data class CategorySection(
    val key: String,
    val title: String,
    val description: String,
    val kind: CategoryKind,
    val appliesTo: CategoryScope,
    val families: List<CategoryFamily>,
)

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val showArchived: Boolean = false,
) {
    val archivedCount: Int get() = categories.count { it.archived }

    /**
     * Three sections, each a list of top-level categories with their own
     * children beneath. Cashflow is separated out because those categories
     * belong to transfers rather than to spending. Mirrors `CategoriesView.vue`.
     */
    val sections: List<CategorySection>
        get() {
            val visible = categories.filter { showArchived || !it.archived }

            fun familiesFor(match: (Category) -> Boolean) = visible
                .filter { it.parentId == null && match(it) }
                .map { parent -> CategoryFamily(parent, visible.filter { it.parentId == parent.id }) }

            return listOf(
                CategorySection(
                    "expense",
                    "Expense",
                    "What you spend on.",
                    CategoryKind.expense,
                    CategoryScope.standard,
                    familiesFor { it.appliesTo == CategoryScope.standard && it.kind == CategoryKind.expense },
                ),
                CategorySection(
                    "income",
                    "Income",
                    "What you earn.",
                    CategoryKind.income,
                    CategoryScope.standard,
                    familiesFor { it.appliesTo == CategoryScope.standard && it.kind == CategoryKind.income },
                ),
                CategorySection(
                    "cashflow",
                    "Cashflow",
                    "For transfers between your own accounts — investments, savings, debt repayment.",
                    CategoryKind.expense,
                    CategoryScope.transfer,
                    familiesFor { it.appliesTo == CategoryScope.transfer },
                ),
            )
        }
}

/** Mirrors `CategoriesView.vue`. */
class CategoriesViewModel(private val ledgerRepository: LedgerRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<CategoriesUiState> = combine(ledgerRepository.categories, ledgerRepository.tags, showArchived, ::CategoriesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshCategories() } }
        viewModelScope.launch { runCatching { ledgerRepository.refreshTags() } }
    }

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    /** Parents the new category could be nested under, matching the chosen section. */
    fun parentOptionsFor(kind: CategoryKind, appliesTo: CategoryScope): List<Category> =
        uiState.value.categories.filter { it.parentId == null && !it.archived && it.appliesTo == appliesTo && it.kind == kind }

    /** The slot a new category in this section should take, so defaults spread across the palette. */
    fun nextColorFor(kind: CategoryKind, appliesTo: CategoryScope): String =
        nextColor(uiState.value.categories.count { it.appliesTo == appliesTo && it.kind == kind })

    suspend fun createCategory(name: String, kind: CategoryKind, appliesTo: CategoryScope, color: String, parentId: String?) =
        ledgerRepository.createCategory(name, kind, appliesTo, color, parentId)

    /** A child inherits its parent's kind and scope, so only these three fields are ever sent when editing. */
    suspend fun updateCategory(id: String, name: String, kind: CategoryKind, color: String) = ledgerRepository.updateCategory(id, name, kind, color)

    suspend fun setArchived(id: String, archived: Boolean) = ledgerRepository.setCategoryArchived(id, archived)

    suspend fun deleteCategory(id: String) = ledgerRepository.deleteCategory(id)

    /** The slot a new tag should take, so defaults spread across the palette. */
    fun nextTagColor(): String = nextColor(uiState.value.tags.size)

    suspend fun createTag(name: String, color: String) = ledgerRepository.createTag(name, color)

    suspend fun updateTag(id: String, name: String, color: String) = ledgerRepository.updateTag(id, name, color)

    suspend fun deleteTag(id: String) = ledgerRepository.deleteTag(id)
}
