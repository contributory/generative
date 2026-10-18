package org.starfall.multigateway.features.settings.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

/** Shared top app bar for the settings feature screens (from the former SettingsScreen Scaffold). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppbar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
        }
    )
}
