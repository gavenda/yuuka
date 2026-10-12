package dev.gavenda.yuuka.ui.common

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
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
 * A button's place in the row gives it its colour: the first is primary, the second secondary, the
 * third tertiary, and round again. It rests in that role's container and takes the role itself when
 * selected, so a group is the brand's three colours side by side. [label] gives each option's text, and
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
                colors = roleColors(index),
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

/** The colours of the button at [index]: its role's container at rest, the role when checked. Disabled is Material's own. */
@Composable
private fun roleColors(index: Int): ToggleButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> ToggleButtonDefaults.colors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        )
        1 -> ToggleButtonDefaults.colors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            checkedContainerColor = scheme.secondary,
            checkedContentColor = scheme.onSecondary,
        )
        else -> ToggleButtonDefaults.colors(
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            checkedContainerColor = scheme.tertiary,
            checkedContentColor = scheme.onTertiary,
        )
    }
}
