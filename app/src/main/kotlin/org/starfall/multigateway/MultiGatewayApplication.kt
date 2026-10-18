package org.starfall.multigateway

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.starfall.multigateway.shared.storage.preferences.AppLocale
import org.starfall.multigateway.appwiring.AppContainer
import org.starfall.multigateway.shared.files.CrashReportStore
import org.starfall.multigateway.shared.files.CrashRecordingHandler
import java.io.File
import org.starfall.multigateway.home.chat.ui.ChatRenderCaches

class MultiGatewayApplication : Application(), coil3.SingletonImageLoader.Factory {
    override fun newImageLoader(context: android.content.Context): coil3.ImageLoader =
        org.starfall.multigateway.shared.icons.AppImages.createLoader(context)

    val container by lazy { AppContainer(this) }
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
    }
}
