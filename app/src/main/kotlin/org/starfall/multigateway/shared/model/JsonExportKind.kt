package org.starfall.multigateway.shared.model

/** The single JSON file used to back up or restore all transferable application data. */
enum class JsonExportKind(val fileName: String) {
    FULL_BACKUP("multigateway-backup.json")
}
