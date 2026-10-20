package org.starfall.multigateway.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.icons.IconStore

@Composable
fun FeaturesLogoImage(
    image: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val revision by IconStore.revision.collectAsState()
    val data = remember(image, revision, context) {
        image?.let { IconStore(context).imageData(it, dark = false) }
    }
    if (data != null) {
        AsyncImage(
            model = data,
            contentDescription = stringResource(R.string.app_name),
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(R.drawable.multigateway_logo_light),
            contentDescription = stringResource(R.string.app_name),
            modifier = modifier
        )
    }
}
