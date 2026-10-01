package com.arcana.service.ai.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.arcana.service.ai.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a model download alive when the app is left or the screen sleeps. The download itself
 * lives in [ModelStore]; this only holds the phone awake, keeps Wi-Fi up, and shows progress. It
 * stops itself when nothing is downloading.
 */
@AndroidEntryPoint
class ModelDownloadService : LifecycleService() {
    @Inject lateinit var store: ModelStore

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false

    @OptIn(FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (started) return START_NOT_STICKY
        started = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build(null, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // Started while the app was not in front. The download still runs; it just is not protected.
            Log.w(TAG, "Could not go foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arcana:download").apply { acquire(MAX_MS) }
        @Suppress("DEPRECATION") // The newer constant trades throughput for latency, the wrong way round for a download.
        wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "arcana:download").apply { acquire() }

        val downloads = store.states.map { states ->
            states.values.filter { it is ModelStore.State.Downloading || it is ModelStore.State.Verifying }
        }
        lifecycleScope.launch {
            downloads.collectLatest { active ->
                if (active.isEmpty()) {
                    release()
                    ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        lifecycleScope.launch {
            // Once a second is plenty for a progress bar, and the system throttles faster updates anyway.
            downloads.sample(1000).collectLatest { active ->
                val running = active.filterIsInstance<ModelStore.State.Downloading>()
                if (active.isEmpty()) return@collectLatest
                val done = running.sumOf { it.doneBytes }
                val total = running.sumOf { it.totalBytes }
                val notification = if (running.isEmpty()) build("Checking the file", null)
                else build("${done / MB} of ${total / MB} MB", if (total > 0) done.toFloat() / total else null)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 gives this kind of service six hours a day. The download carries on next time.
    override fun onTimeout(startId: Int, fgsType: Int) {
        release()
        stopSelf()
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    /** @param fraction 0 to 1, or null when there is nothing to measure yet */
    private fun build(text: String?, fraction: Float?): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arcana)
            .setContentTitle("Downloading the reading model")
            .setContentText(text)
            .setProgress(1000, ((fraction ?: 0f) * 1000).toInt(), fraction == null)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "ModelDownload"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION_ID = 41
        private const val MAX_MS = 3 * 60 * 60 * 1000L
        private const val MB = 1024 * 1024L

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start", it) }
        }
    }
}
