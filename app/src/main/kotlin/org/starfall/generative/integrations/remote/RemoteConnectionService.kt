package org.starfall.generative.integrations.remote

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.model.RemoteMachine

/**
 * Foreground service that shows one ongoing notification per machine that holds a persistent SSH
 * connection, each with a Disconnect action. It also keeps the process alive, which port forwards
 * need: they live and die with the SSH session.
 *
 * One machine's notification is the foreground-service notification; the others are ordinary
 * ongoing notifications. The service stops itself when the last connection closes.
 */
class RemoteConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var foregroundMachineId: String? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val machineService = (application as? RemoteMachineServiceOwner)?.remoteMachineService
        if (machineService == null) {
            // Nothing to show, but a started foreground service must call startForeground() before it stops.
            enterForeground(PLACEHOLDER_NOTIFICATION_ID, placeholderNotification())
            stopSelf()
            return START_NOT_STICKY
        }
        if (observeJob == null) {
            // Posts the foreground notification synchronously, before the first flow emission.
            render(connectedMachines(machineService))
            observeJob = scope.launch {
                combine(machineService.connectedMachineIds, machineService.state) { ids, state ->
                    state.machines.filter { it.id in ids }
                }.collect { render(it) }
            }
        }
        // Connections belong to this process; after process death there is nothing to restore.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        observeJob?.cancel()
        scope.cancel()
        val manager = NotificationManagerCompat.from(this)
        ownNotificationIds().forEach { manager.cancel(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectedMachines(service: RemoteMachineService): List<RemoteMachine> {
        val ids = service.connectedMachineIds.value
        return service.state.value.machines.filter { it.id in ids }
    }

    @SuppressLint("MissingPermission") // Without POST_NOTIFICATIONS the notification is simply not shown.
    private fun render(machines: List<RemoteMachine>) {
        val manager = NotificationManagerCompat.from(this)
        if (machines.isEmpty()) {
            if (!isForeground) enterForeground(PLACEHOLDER_NOTIFICATION_ID, placeholderNotification())
            ownNotificationIds().forEach { manager.cancel(it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            foregroundMachineId = null
            stopSelf()
            return
        }
        val wanted = machines.map { notificationId(it.id) }.toSet()
        foregroundMachineId?.takeIf { id -> machines.none { it.id == id } }?.let { gone ->
            // Detach first so cancelling the old anchor does not tear down the whole service.
            stopForeground(STOP_FOREGROUND_DETACH)
            manager.cancel(notificationId(gone))
            foregroundMachineId = null
        }
        // Also removes notifications that a previous process left behind (the app was killed while connected).
        ownNotificationIds().filter { it !in wanted }.forEach { manager.cancel(it) }
        val anchor = foregroundMachineId ?: machines.first().id
        machines.forEach { machine ->
            val notification = notificationFor(machine)
            if (machine.id == anchor && foregroundMachineId == null) {
                if (enterForeground(notificationId(machine.id), notification)) foregroundMachineId = machine.id
            } else {
                manager.notify(notificationId(machine.id), notification)
            }
        }
    }

    /** Returns false when the device rejected the foreground start (the service then stops). */
    private fun enterForeground(id: Int, notification: Notification): Boolean = try {
        // The type must be passed explicitly on Android 14+ for a special-use foreground service.
        ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        isForeground = true
        true
    } catch (error: Exception) {
        // Background-start restrictions or a vendor rule. Never let this become an app crash.
        Log.e(TAG, "Unable to enter foreground", error)
        stopSelf()
        false
    }

    private fun notificationFor(machine: RemoteMachine): Notification {
        val name = machine.name.ifBlank { machine.displayHost }
        val text = if (machine.portForwarding.hasActiveForwards) {
            R.string.remote_connection_notification_forwarding
        } else {
            R.string.remote_connection_notification_text
        }
        val disconnect = PendingIntent.getBroadcast(
            this,
            notificationId(machine.id),
            Intent(this, RemoteConnectionActionReceiver::class.java)
                .setAction(ACTION_DISCONNECT)
                // The data URI keeps each machine's PendingIntent distinct even on a requestCode clash.
                .setData(Uri.fromParts("machine", machine.id, null))
                .putExtra(EXTRA_MACHINE_ID, machine.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return baseNotification()
            .setContentTitle(getString(R.string.remote_connection_notification_title, name))
            .setContentText(getString(text))
            .addAction(0, getString(R.string.remote_connection_disconnect), disconnect)
            .build()
    }

    private fun placeholderNotification(): Notification =
        baseNotification().setContentTitle(getString(R.string.remote_connection_channel_name)).build()

    private fun baseNotification(): NotificationCompat.Builder {
        // Resolved at runtime so this layer does not depend on the app's activity class.
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val open = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal_material_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
    }

    /** Ids of this service's notifications that are currently posted (a previous process included). */
    private fun ownNotificationIds(): List<Int> =
        getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
            .filter { it.notification.channelId == CHANNEL_ID }
            .map { it.id }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.remote_connection_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.remote_connection_channel_description) }
        )
    }

    companion object {
        const val ACTION_DISCONNECT = "org.starfall.generative.action.REMOTE_DISCONNECT"
        const val EXTRA_MACHINE_ID = "machine_id"
        private const val TAG = "RemoteConnectionService"
        private const val CHANNEL_ID = "remote_connection_channel"
        private const val PLACEHOLDER_NOTIFICATION_ID = 4557
        private const val NOTIFICATION_ID_BASE = 100_000

        @Volatile private var running = false

        /** Stable per machine, so a refreshed notification replaces its previous one. */
        private fun notificationId(machineId: String): Int = NOTIFICATION_ID_BASE + (machineId.hashCode() and 0x7FFFFF)

        /** Starts the service unless it already runs; it stops itself once nothing is connected. */
        fun start(context: Context) {
            if (running) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, RemoteConnectionService::class.java))
            }.onFailure { Log.w(TAG, "Unable to start the connection notification: ${it.message}") }
        }
    }
}
