package org.starfall.generative.features.about.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import org.starfall.generative.R
import org.starfall.generative.features.about.logic.GitHubReleaseInfo
import org.starfall.generative.features.about.logic.fetchLatestGitHubReleases

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName.orEmpty()
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AboutCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(60.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("◇", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Generative", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_version, versionName, versionCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.settings_universal_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UpdateSection()
        AboutNavigationItem(stringResource(R.string.settings_source_code), "starfall-org/generative", "</>") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/starfall-org/generative")))
        }
        AboutCard {
            Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_license_name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val currentVersion = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<GitHubReleaseInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        if (refreshKey == 0) return@LaunchedEffect
        isChecking = true
        error = null
        runCatching { fetchLatestGitHubReleases() }
            .onSuccess { releases = it }
            .onFailure { error = it.localizedMessage ?: context.getString(R.string.settings_update_error) }
        isChecking = false
    }

    AboutCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_software_update), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            androidx.compose.material3.IconButton(onClick = { refreshKey++ }, enabled = !isChecking) {
                Text("⟳", style = MaterialTheme.typography.titleLarge)
            }
        }
        when {
            isChecking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.settings_checking_releases))
            }
            error != null -> {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.settings_try_again)) }
            }
            releases.isNotEmpty() -> releases.forEachIndexed { index, release ->
                if (index > 0) HorizontalDivider()
                ReleaseEntry(release, updateAvailable = compareVersions(currentVersion, release.tagName) < 0)
            }
            else -> {
                Text(stringResource(R.string.settings_find_latest_release), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.settings_check_for_updates)) }
            }
        }
    }
}

@Composable
private fun ReleaseEntry(latest: GitHubReleaseInfo, updateAvailable: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(latest.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            if (latest.isPrerelease) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.settings_unstable_release), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Text(stringResource(if (updateAvailable) R.string.settings_update_available else R.string.settings_up_to_date, latest.tagName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (latest.body.isNotBlank()) Text(latest.body, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
        if (updateAvailable && latest.apkAssets.isNotEmpty()) {
            latest.apkAssets.forEach { asset ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset.downloadUrl))) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("↓")
                    Spacer(Modifier.size(8.dp))
                    Text(asset.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.pageUrl))) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("↗")
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.settings_open_github_release))
            }
        }
    }
}

// Versions look like "1.0.2-123" (main) or "1.0.2-123dev" (other branches): base version, then the
// commit count. The branch suffix is ignored; the commit count orders builds of the same base version.
private fun compareVersions(current: String, latest: String): Int {
    val pattern = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-(\d+))?""")
    fun parts(value: String): List<Long> {
        val match = pattern.find(value.trim())
        return (1..4).map { match?.groupValues?.getOrNull(it)?.toLongOrNull() ?: 0L }
    }
    val a = parts(current); val b = parts(latest)
    for (index in a.indices) {
        val comparison = a[index].compareTo(b[index])
        if (comparison != 0) return comparison
    }
    return 0
}

@Composable
private fun AboutCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun AboutNavigationItem(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(icon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
