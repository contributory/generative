package org.starfall.generative.features.home.chat.logic.markdown

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Shared budget for visible and speculative Markdown/TeX work, including suspending library parsers. */
internal object MarkdownRenderWork {
    private val permit = Semaphore(1)

    suspend fun <T> run(work: suspend () -> T): T = permit.withPermit {
        withContext(Dispatchers.Default) { work() }
    }
}
