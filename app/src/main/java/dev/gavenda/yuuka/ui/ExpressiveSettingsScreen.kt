package dev.gavenda.yuuka.ui
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class Account(val name: String, val email: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveLargeSettingsScreen() {
    // Generates a mock list of 15 accounts
    val heavyAccountList = remember {
        List(15) { index -> Account("Profile User ${index + 1}", "user${index + 1}@enterprise.com") }
    }
    var selectedAccount by remember { mutableStateOf(heavyAccountList.first()) }
    var isSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(text = "Accounts & Identity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // 1. The Launcher Row: Acts as a clean preview block on the main screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(28.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { isSheetOpen = true }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Default Account", style = MaterialTheme.typography.titleMedium)
                        Text(selectedAccount.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 2. High-Capacity Sheet Layer
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // Elevated depth background
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Select Default Account",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // LazyColumn isolates rendering to only items visible on screen for performance
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false), // Allows sheet to snap size naturally up to a max threshold
                        verticalArrangement = Arrangement.spacedBy(4.dp) // Segmented Gap representation
                    ) {
                        itemsIndexed(heavyAccountList) { index, account ->
                            val isSelected = account == selectedAccount

                            // Dynamic calculations formatting specific container positions
                            val position = when {
                                index == 0 -> ItemPosition.Top
                                index == heavyAccountList.lastIndex -> ItemPosition.Bottom
                                else -> ItemPosition.Middle
                            }

                            ExpressiveModalSelectionItem(
                                icon = Icons.Default.AccountCircle,
                                title = account.name,
                                subtitle = account.email,
                                isSelected = isSelected,
                                position = position,
                                onClick = {
                                    selectedAccount = account
                                    isSheetOpen = false // Clean automatic dismiss logic on pick
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveModalSelectionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    position: ItemPosition,
    onClick: () -> Unit
) {
    // Expressive morphing calculations mapping pill sizes
    val cornerRounding = if (isSelected) 24.dp else when (position) {
        ItemPosition.Top, ItemPosition.Bottom -> 20.dp
        ItemPosition.Middle -> 4.dp
        ItemPosition.Single -> 24.dp
    }

    val itemShape = when (position) {
        ItemPosition.Top -> RoundedCornerShape(topStart = cornerRounding, topEnd = cornerRounding, bottomStart = 4.dp, bottomEnd = 4.dp)
        ItemPosition.Bottom -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = cornerRounding, bottomEnd = cornerRounding)
        ItemPosition.Middle -> RoundedCornerShape(cornerRounding)
        ItemPosition.Single -> RoundedCornerShape(24.dp)
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "BgAnim"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

enum class ItemPosition { Top, Middle, Bottom, Single }
