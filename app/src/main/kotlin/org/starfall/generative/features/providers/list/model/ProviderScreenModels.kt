package org.starfall.generative.features.providers.list.model

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/** A tile being dragged. [fromFolderId] is set while it is still a member of the open folder container. */
internal data class ProviderDragSession(
    val key: String,
    val size: IntSize,
    val grab: Offset,
    val fromFolderId: String? = null,
    val folderTile: Boolean = fromFolderId != null,
    val isGrid: Boolean = true
)

/** Where the drag surface and the folder container sit; read from gesture callbacks, never from composition. */
internal class ProviderDragSurfaces {
    var rootGrid: LazyGridState? = null
    var folderGrid: LazyGridState? = null
    var boxOrigin = Offset.Zero
    var panelBounds: Rect? = null
    var folderGridOrigin = Offset.Zero
    var layoutGate = 0
}
