package org.starfall.generative.shared.model.remote

import java.io.InputStream
import java.io.OutputStream

/** Cross-layer contract for an interactive remote shell session. */
interface RemoteTerminalSession : AutoCloseable {
    val id: String
    val input: InputStream
    val output: OutputStream
    val isOpen: Boolean
    val exitStatus: Int

    fun resize(columns: Int, rows: Int)
}
