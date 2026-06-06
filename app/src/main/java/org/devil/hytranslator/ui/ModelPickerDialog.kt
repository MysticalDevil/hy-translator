package org.devil.hytranslator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.ModelOption

@Composable
fun ModelPickerDialog(
    models: List<ModelOption>,
    currentModel: ModelOption,
    recommendedModel: ModelOption,
    onSelect: (ModelOption) -> Unit,
    onDismiss: () -> Unit,
    onClearAllModels: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 480.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.model_select_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_model_picker),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(models, key = { it.key }) { model ->
                        val isSelected = model.key == currentModel.key
                        val isRecommended = model.key == recommendedModel.key

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold
                                                else FontWeight.Medium,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (isRecommended) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.12f,
                                                ),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.model_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 1.dp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "~${model.memoryRequirementGb}GB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        onClearAllModels()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.model_clear_all),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
