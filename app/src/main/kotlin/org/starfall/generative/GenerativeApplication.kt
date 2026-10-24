package org.starfall.generative

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.starfall.generative.shared.storage.preferences.AppLocale
import org.starfall.generative.appwiring.AppContainer
import org.starfall.generative.integrations.remote.RemoteConnectionService
import org.starfall.generative.integrations.remote.RemoteMachineService
import org.starfall.generative.integrations.remote.RemoteMachineServiceOwner
import org.starfall.generative.shared.files.CrashReportStore
import org.starfall.generative.shared.files.CrashRecordingHandler
import java.io.File
import org.starfall.generative.home.chat.ui.ChatRenderCaches

class GenerativeApplication : Application(), RemoteMachineServiceOwner, coil3.SingletonImageLoader.Factory {
    override fun newImageLoader(context: android.content.Context): coil3.ImageLoader =
        org.starfall.generative.shared.icons.AppImages.createLoader(context)

    val container by lazy { AppContainer(this) }

    /** Shared with the SFTP DocumentsProvider, which Android may create before the UI. */
    override val remoteMachineService: RemoteMachineService get() = container.remoteMachineService
    internal val crashReports by lazy { CrashReportStore(File(filesDir, "last_crash.json")) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashRecordingHandler(crashReports, Thread.getDefaultUncaughtExceptionHandler())
        )
        // The chat render caches are a convenience only: give the RAM back as soon as Android asks.
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = ChatRenderCaches.onTrimMemory(level)
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = ChatRenderCaches.clearAll()
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        })
        applicationScope.launch { container.defaultDataInitializer.initialize() }
        // Off the main thread: the first access builds the machine store. One notification per
        // connected machine (with a Disconnect button) is owned by RemoteConnectionService.
        applicationScope.launch(Dispatchers.Default) {
            remoteMachineService.connectedMachineIds.collect { ids ->
                if (ids.isNotEmpty()) RemoteConnectionService.start(this@GenerativeApplication)
            }
        }
    }
}
