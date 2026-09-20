package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind

data class CategoryGroup(val parent: Category, val children: List<Category>)

/**
 * Groups a flat list into pickable options: each parent followed by its own
 * children. Choosing a parent or a child are both single choices, which is
 * what keeps a transaction from carrying two categories at once. Mirrors
 * `ledger.ts`'s `groupForPicker`.
 */
fun groupForPicker(categories: List<Category>): List<CategoryGroup> {
    val parents = categories.filter { it.parentId == null && !it.archived }
    return parents.map { parent -> CategoryGroup(parent, categories.filter { it.parentId == parent.id && !it.archived }) }
}

fun expenseCategories(categories: List<Category>) = categories.filter { it.kind == CategoryKind.expense }

fun incomeCategories(categories: List<Category>) = categories.filter { it.kind == CategoryKind.income }

fun transferCategories(categories: List<Category>) = categories.filter { it.kind == CategoryKind.transfer }
