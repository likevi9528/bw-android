package com.x8bit.bitwarden.ui.vault.feature.item

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/**
 * Update Text UI common for all item types.
 */
@Composable
fun VaultItemUpdateText(
    header: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { },
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
