package ai.fam.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ai.fam.app.R
import kotlin.concurrent.thread

class ModelFetchService : Service() {
    private var lastNote = ""

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val notification = note(0L, -1L, 0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTE_ID, notification)
        }
        if (!ModelDownload.markBusy()) return START_NOT_STICKY
        thread(name = "gemma-download", isDaemon = true) {
            try {
                ModelFetcher(applicationContext) { done, total, speed ->
                    val text = progressText(done, total, speed)
                    if (text == lastNote) return@ModelFetcher
                    lastNote = text
                    manager().notify(NOTE_ID, note(done, total, speed))
                }.run()
            } catch (exc: Exception) {
                ModelDownload.settle(applicationContext, exc)
            } finally {
                ModelDownload.clearBusy()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun manager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    private fun progressText(done: Long, total: Long, speedBps: Long): String {
        val percent = PhoneModelFiles.downloadPercent(done, total)
        val amount = if (percent == null) {
            getString(R.string.settings_model_amount_unknown, PhoneModelFiles.sizeText(done))
        } else {
            getString(
                R.string.settings_model_amount,
                PhoneModelFiles.sizeText(done),
                PhoneModelFiles.sizeText(total),
                percent,
            )
        }
        val speed = if (speedBps <= 0L) {
            getString(R.string.settings_model_speed_wait)
        } else {
            getString(R.string.settings_model_speed, PhoneModelFiles.speedText(speedBps))
        }
        return "$amount\n$speed"
    }

    private fun note(done: Long, total: Long, speedBps: Long): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                getString(R.string.model_download_title),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = getString(R.string.model_download_channel)
            manager().createNotificationChannel(channel)
        }
        val percent = PhoneModelFiles.downloadPercent(done, total)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.model_download_title))
            .setContentText(progressText(done, total, speedBps))
            .setStyle(Notification.BigTextStyle().bigText(progressText(done, total, speedBps)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    private companion object {
        const val CHANNEL = "fam_model"
        const val NOTE_ID = 41
    }
}
