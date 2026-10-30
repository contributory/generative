package org.starfall.generative.extensions.explorer.logic

/** Opaque source identity: the extension does not know which backend owns it. */
data class ExplorerLocation(
    val sourceId: String = "",
    val path: String = "",
    val label: String = ""
) {
    val isHome: Boolean get() = sourceId.isEmpty()

    fun parent(): ExplorerLocation = when {
        path.isNotEmpty() -> copy(path = path.substringBeforeLast('/', ""))
        else -> ExplorerLocation()
    }
}

data class ExplorerItem(
    val name: String,
    val location: ExplorerLocation,
    val directory: Boolean,
    val size: Long = 0L,
    val modified: Long = 0L
)

/** Host-implemented storage operations; file viewing is a separate host callback. */
interface ExplorerRepository {
    suspend fun list(location: ExplorerLocation): List<ExplorerItem>
    suspend fun createFolder(parent: ExplorerLocation, name: String)
    suspend fun rename(item: ExplorerItem, newName: String)
    suspend fun delete(item: ExplorerItem)
}

/** Injectable labels let hosts localize the component without importing app resources. */
data class ExplorerLabels(
    val title: String = "Files",
    val back: String = "Back",
    val newFolder: String = "New folder",
    val changeLayout: String = "Change layout",
    val refresh: String = "Refresh",
    val search: String = "Search files",
    val closeSearch: String = "Close search",
    val operationFailed: String = "File operation failed",
    val empty: String = "No files",
    val moreActions: (String) -> String = { "More actions for $it" },
    val rename: String = "Rename",
    val delete: String = "Delete",
    val name: String = "Name",
    val save: String = "Save",
    val cancel: String = "Cancel",
    val deleteConfirm: (String) -> String = { "Delete $it?" }
)
