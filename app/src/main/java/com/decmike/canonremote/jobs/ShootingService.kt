package com.decmike.canonremote.jobs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.decmike.canonremote.MainActivity
import com.decmike.canonremote.R
import com.decmike.canonremote.ccapi.Exposure
import com.decmike.canonremote.data.CameraRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui exécute les séquences (intervallomètre, HDR,
 * focus stacking) même écran éteint.
 */
class ShootingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val toRun = pending ?: run { stopSelf(); return START_NOT_STICKY }
        pending = null

        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(toRun.title, "Préparation…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CanonRemote:sequence")
            .apply { acquire(24 * 60 * 60 * 1000L) }

        job?.cancel()
        job = scope.launch {
            val client: com.decmike.canonremote.ccapi.CameraControl? =
                if (pendingBle) com.decmike.canonremote.ble.BleRemote.takeIf { it.isConnected }
                else runCatching { CameraRepository.requireClient() }.getOrNull()
            if (client == null) {
                CameraRepository.publishJob(JobProgress(finished = true, title = toRun.title, error = "Appareil non connecté"))
            } else {
                var lastNotif = 0L
                JobRunner(client) { p ->
                    CameraRepository.publishJob(p)
                    val now = System.currentTimeMillis()
                    if (now - lastNotif > 900 || !p.running) {
                        lastNotif = now
                        val text = buildString {
                            append(if (p.total > 0) "Image ${p.shot}/${p.total}" else "Image ${p.shot}")
                            if (p.phase.isNotBlank()) append(" · ${p.phase}")
                            p.countdownSec?.let { append(" ${Exposure.formatDuration(it.toDouble())}") }
                        }
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIF_ID, notification(p.title, text, p.fraction))
                    }
                }.run(toRun)
            }
        }
        job?.invokeOnCompletion {
            wakeLock?.let { if (it.isHeld) it.release() }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun notification(title: String, text: String, fraction: Float?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ShootingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Arrêter", stop)
            .apply {
                if (fraction != null) setProgress(1000, (fraction * 1000).toInt(), false)
                else setProgress(0, 0, true)
            }
            .build()
    }

    companion object {
        const val CHANNEL = "sequences"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "stop"

        @Volatile private var pending: ShootJob? = null
        @Volatile private var pendingBle = false

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Séquences de prise de vue", NotificationManager.IMPORTANCE_LOW)
            )
        }

        fun start(context: Context, job: ShootJob, bluetooth: Boolean = false) {
            pending = job
            pendingBle = bluetooth
            context.startForegroundService(Intent(context, ShootingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ShootingService::class.java).setAction(ACTION_STOP))
        }
    }
}
