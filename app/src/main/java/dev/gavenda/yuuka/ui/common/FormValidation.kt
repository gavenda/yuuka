package dev.gavenda.yuuka.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/**
 * Field-level validation for a form, reactive as Material 3 describes it: a field says what is wrong beside
 * the field itself, as it goes, and the form cannot be saved while anything is wrong.
 *
 * - Each recomposition works every problem out again from what the fields hold, so [valid] is always current:
 *   bind it to the save button's `enabled` and the button is off from the first moment a field is
 *   unacceptable and on the moment the last one is fixed.
 * - A field shows its error once the person has touched it — typed in it, chosen from it or left it — so a
 *   blank form is not covered in red before anyone has done anything, yet its save button is already off.
 *   The error follows the value and clears the moment it is acceptable.
 *
 * A saved change never waits for the network, so everything the server would refuse for the form's own
 * values has to be answered here rather than arriving later as a snackbar.
 *
 * Each recomposition describes a field with [field] (its key, and its problem right now, or null) and
 * hands it to [YuukaTextField], [DropdownField] or the like; the save button asks [valid] of the same fields.
 */
@Stable
class FormValidation {
    private val touched = mutableStateMapOf<String, Boolean>()

    /** Describes one field for this composition: [problem] is what is wrong with its value now, or null. */
    fun field(key: String, problem: String?): FieldState = FieldState(this, key, problem)

    /** Whether nothing is wrong with any of [fields], touched or not: what a save button's `enabled` is. */
    fun valid(vararg fields: FieldState): Boolean = fields.all { it.problem == null }

    internal fun touch(key: String) {
        touched[key] = true
    }

    internal fun shown(key: String, problem: String?): String? = if (touched[key] == true) problem else null
}

/** One field, as described for a single composition. */
@Stable
class FieldState internal constructor(private val form: FormValidation, val key: String, val problem: String?) {
    /** What to draw beneath the field: its problem, once the field has been touched. */
    val error: String? get() = form.shown(key, problem)

    /** Marks the field as visited. */
    fun touch() = form.touch(key)
}

@Composable
fun rememberFormValidation(): FormValidation = remember { FormValidation() }

/**
 * Wires a control to its [field]: it counts as visited once focus has left it, and tells a screen reader what
 * is wrong with it. Does nothing for a null field.
 */
@Composable
internal fun Modifier.tracked(field: FieldState?): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    if (field == null) return this

    val message = field.error
    return this
        .onFocusChanged { state ->
            if (state.hasFocus) hadFocus = true else if (hadFocus) field.touch()
        }
        .semantics { if (message != null) error(message) }
}
