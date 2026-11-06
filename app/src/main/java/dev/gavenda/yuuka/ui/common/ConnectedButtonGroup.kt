package dev.gavenda.yuuka.ui.common

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * The Material 3 Expressive connected button group: a full-width row of toggle buttons where
 * exactly one of [options] is selected — the successor to the segmented button row.
 *
 * The end buttons get fully rounded outer corners and the selected one morphs to a pill.
 * Every button takes Material's own toggle colours, as Material 3 Expressive asks of a group: it rests
 * on the surface container and fills with primary when selected. [label] gives each option's text, and
 * [icon], when given, the icon that leads it. An option in [iconOnly] shows its icon alone, is only as wide as
 * that takes, and has its label read out instead. Mirrors `ConnectedButtonGroup.vue`.
 */
@Composable
fun <T> ConnectedButtonGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ((T) -> Int)? = null,
    iconOnly: Set<T> = emptySet(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = (if (option in iconOnly) Modifier else Modifier.weight(1f)).semantics { role = Role.RadioButton },
            ) {
                val vector = icon?.invoke(option)
                if (vector != null && option in iconOnly) {
                    Icon(painterResource(vector), contentDescription = label(option), modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                } else {
                    if (vector != null) {
                        Icon(painterResource(vector), contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(label(option))
                }
            }
        }
    }
}
