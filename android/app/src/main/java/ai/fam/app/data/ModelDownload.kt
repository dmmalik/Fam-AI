package ai.fam.app.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

enum class NetLink { CHECKING, ONLINE, OFFLINE }

enum class DownloadGate { RUN, WAIT, UNMETERED, BACKGROUND, SAVER }

fun chooseDownload(
    online: Boolean,
    metered: Boolean,
    foreground: Boolean,
    backgroundAllowed: Boolean,
    mobileAllowed: Boolean,
    dataSaver: Boolean,
): DownloadGate {
    if (!online) return DownloadGate.WAIT
    if (metered && !mobileAllowed) return DownloadGate.UNMETERED
    if (!foreground && dataSaver) return DownloadGate.SAVER
    if (!foreground && !backgroundAllowed) return DownloadGate.BACKGROUND
    return DownloadGate.RUN
}

sealed class ModelFetch {
    data class Ready(val name: String, val bytes: Long) : ModelFetch()
    data class Running(val done: Long, val total: Long, val speedBps: Long = 0L) : ModelFetch() {
        val percent: Int? get() = PhoneModelFiles.downloadPercent(done, total)
    }
    data class Waiting(val done: Long) : ModelFetch()
    data class Paused(val done: Long, val gate: DownloadGate) : ModelFetch()
    data class Failed(val done: Long, val offline: Boolean) : ModelFetch()
}

class DownloadPause : IOException("paused")

object ModelDownload {
    private const val TAG = "FamAI"
    private val _state = MutableStateFlow<ModelFetch>(ModelFetch.Waiting(0))
    val state: StateFlow<ModelFetch> = _state
    private val _link = MutableStateFlow(NetLink.CHECKING)
    val link: StateFlow<NetLink> = _link
    private val busy = AtomicBoolean(false)
    private val watching = AtomicBoolean(false)
    private val inForeground = AtomicBoolean(false)
    private val choicesLoaded = AtomicBoolean(false)
    private val hold = AtomicReference<DownloadGate?>(null)
    private val call = AtomicReference<okhttp3.Call?>(null)
    private val _choices = MutableStateFlow(DownloadChoices())
    val choices: StateFlow<DownloadChoices> = _choices

    fun publish(value: ModelFetch) {
        _state.value = value
    }

    fun watch(context: Context) {
        val app = context.applicationContext
        refreshLink(app, resume = false)
        if (!watching.compareAndSet(false, true)) return
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshLink(app, resume = true)
            }

            override fun onLost(network: Network) {
                refreshLink(app, resume = false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                refreshLink(app, resume = true)
            }
        })
        refreshLink(app, resume = false)
    }

    fun onlineNow(context: Context): Boolean = readLink(context) == NetLink.ONLINE

    fun dataSaverOn(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    fun foreground(context: Context, visible: Boolean) {
        inForeground.set(visible)
        if (visible) {
            start(context)
            return
        }
        val gate = currentGate(context)
        if (gate == DownloadGate.BACKGROUND || gate == DownloadGate.SAVER || gate == DownloadGate.UNMETERED) {
            requestPause(context, gate)
        }
    }

    fun setBackground(context: Context, allowed: Boolean) {
        updateChoices(context) { it.copy(background = allowed) }
    }

    fun setMobile(context: Context, allowed: Boolean) {
        updateChoices(context) { it.copy(mobile = allowed) }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        watch(app)
        loadChoices(app)
        val existing = PhoneVision.locateModel(app)
        if (existing != null) {
            _state.value = ModelFetch.Ready(existing.name, existing.length())
            return
        }
        if (busy.get()) return
        when (val gate = currentGate(app)) {
            DownloadGate.RUN -> launch(app)
            DownloadGate.WAIT -> _state.value = ModelFetch.Waiting(savedBytes(app))
            else -> _state.value = ModelFetch.Paused(savedBytes(app), gate)
        }
    }

    fun retry(context: Context) {
        start(context)
    }

    fun restart(context: Context) {
        val app = context.applicationContext
        if (busy.get()) return
        val partial = File(PhoneVision.modelDir(app), PhoneModelFiles.E4B + PhoneModelFiles.PARTIAL_SUFFIX)
        if (partial.exists()) partial.delete()
        start(app)
    }

    fun keepHold(gate: DownloadGate) {
        hold.set(gate)
    }

    fun bindCall(active: okhttp3.Call) {
        call.set(active)
    }

    fun unbindCall(active: okhttp3.Call) {
        call.compareAndSet(active, null)
    }

    fun currentGate(context: Context): DownloadGate {
        loadChoices(context)
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        return chooseDownload(
            online = readLink(context) == NetLink.ONLINE,
            metered = manager?.isActiveNetworkMetered == true,
            foreground = inForeground.get(),
            backgroundAllowed = _choices.value.background,
            mobileAllowed = _choices.value.mobile,
            dataSaver = dataSaverOn(context),
        )
    }

    fun settle(context: Context, exc: Throwable) {
        val reason = hold.getAndSet(null)
        if (reason != null && reason != DownloadGate.RUN && reason != DownloadGate.WAIT) {
            publish(ModelFetch.Paused(savedBytes(context), reason))
            return
        }
        fail(context, exc)
    }

    fun markBusy(): Boolean = busy.compareAndSet(false, true)

    fun clearBusy() {
        busy.set(false)
    }

    fun fail(context: Context, exc: Throwable) {
        Log.e(TAG, "model download failed", exc)
        val offline = linkError(exc) || !onlineNow(context)
        publish(ModelFetch.Failed(savedBytes(context), offline))
    }

    private fun downloadInApp(app: Context) {
        if (!markBusy()) return
        thread(name = "gemma-download", isDaemon = true) {
            try {
                ModelFetcher(app) { _, _, _ -> }.run()
            } catch (exc: Exception) {
                settle(app, exc)
            } finally {
                clearBusy()
            }
        }
    }

    private fun savedBytes(context: Context): Long {
        val partial = File(PhoneVision.modelDir(context), PhoneModelFiles.E4B + PhoneModelFiles.PARTIAL_SUFFIX)
        return if (partial.exists()) partial.length() else 0L
    }

    private fun refreshLink(context: Context, resume: Boolean) {
        val next = readLink(context)
        val becameOnline = next == NetLink.ONLINE && _link.value != NetLink.ONLINE
        _link.value = next
        if (next == NetLink.ONLINE) ShareSync.flush(context)
        if (!resume || !becameOnline || busy.get()) return
        val now = _state.value
        val waiting = now is ModelFetch.Waiting ||
            now is ModelFetch.Paused ||
            (now is ModelFetch.Failed && now.offline)
        if (waiting) start(context)
    }

    private fun readLink(context: Context): NetLink {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return NetLink.OFFLINE
        val network = manager.activeNetwork ?: return NetLink.OFFLINE
        val caps = manager.getNetworkCapabilities(network) ?: return NetLink.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetLink.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetLink.CHECKING
        return NetLink.ONLINE
    }

    private fun linkError(exc: Throwable): Boolean {
        var current: Throwable? = exc
        while (current != null) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is NoRouteToHostException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun launch(app: Context) {
        hold.set(null)
        _state.value = ModelFetch.Running(savedBytes(app), -1L)
        try {
            ContextCompat.startForegroundService(app, Intent(app, ModelFetchService::class.java))
        } catch (exc: Exception) {
            Log.e(TAG, "foreground download blocked", exc)
            if (inForeground.get()) {
                downloadInApp(app)
            } else {
                _state.value = ModelFetch.Paused(savedBytes(app), DownloadGate.BACKGROUND)
            }
        }
    }

    private fun requestPause(context: Context, gate: DownloadGate) {
        if (PhoneVision.locateModel(context) != null) return
        hold.set(gate)
        call.get()?.cancel()
        if (!busy.get()) {
            _state.value = ModelFetch.Paused(savedBytes(context), gate)
        }
    }

    private fun loadChoices(context: Context) {
        if (!choicesLoaded.compareAndSet(false, true)) return
        _choices.value = DownloadChoicesStore(context).read()
    }

    private fun updateChoices(context: Context, change: (DownloadChoices) -> DownloadChoices) {
        loadChoices(context)
        val next = change(_choices.value)
        _choices.value = next
        DownloadChoicesStore(context).write(next)
        val gate = currentGate(context)
        if (gate == DownloadGate.RUN) {
            start(context)
        } else if (gate != DownloadGate.WAIT && busy.get()) {
            requestPause(context, gate)
        } else if (gate != DownloadGate.WAIT && _state.value is ModelFetch.Running) {
            requestPause(context, gate)
        }
    }
}

class ModelFetcher(
    private val context: Context,
    private val onProgress: (Long, Long, Long) -> Unit,
) {
    fun run() {
        val ready = PhoneVision.locateModel(context)
        if (ready != null) {
            ModelDownload.publish(ModelFetch.Ready(ready.name, ready.length()))
            return
        }
        val partial = File(PhoneVision.modelDir(context), PhoneModelFiles.E4B + PhoneModelFiles.PARTIAL_SUFFIX)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val offset = if (partial.exists()) partial.length() else 0L
            val request = Request.Builder()
                .url(PhoneModelFiles.E4B_URL)
                .header("User-Agent", "FamAI")
                .apply {
                    if (offset > 0L) header("Range", "bytes=$offset-")
                }
                .build()
            guard()
            val active = client.newCall(request)
            ModelDownload.bindCall(active)
            try {
                active.execute().use { response ->
                    when {
                        response.code == 416 -> partial.delete()
                        response.code == 200 && offset > 0L -> {
                            write(response, partial, append = false)
                            return
                        }
                        response.isSuccessful -> {
                            write(response, partial, append = offset > 0L && response.code == 206)
                            return
                        }
                        else -> throw IOException("model http ${response.code}")
                    }
                }
            } finally {
                ModelDownload.unbindCall(active)
            }
        }
        throw IOException("model range")
    }

    private fun write(response: okhttp3.Response, partial: File, append: Boolean) {
        val body = response.body ?: throw IOException("empty model")
        val length = body.contentLength()
        val ranged = PhoneModelFiles.rangeTotal(response.header("Content-Range"))
        val total = when {
            ranged != null -> ranged
            append && length > 0L -> partial.length() + length
            length > 0L -> length
            else -> -1L
        }
        var written = if (append) partial.length() else 0L
        var checked = append
        var lastBucket = -1L
        var lastPercent = -2
        var originBytes = written
        var originNanos = System.nanoTime()
        var shownSpeed = 0L
        var lastPublishNanos = originNanos
        fun report(done: Long) {
            val now = System.nanoTime()
            val elapsed = now - originNanos
            val gained = done - originBytes
            if (elapsed >= 1_000_000_000L) {
                shownSpeed = PhoneModelFiles.speedBps(gained, elapsed) ?: shownSpeed
                originBytes = done
                originNanos = now
            }
            val bucket = done / 1_000_000L
            val percent = PhoneModelFiles.downloadPercent(done, total) ?: -1
            val due = now - lastPublishNanos >= 1_000_000_000L
            if (!due && bucket == lastBucket && percent == lastPercent) return
            lastBucket = bucket
            lastPercent = percent
            lastPublishNanos = now
            onProgress(done, total, shownSpeed)
            ModelDownload.publish(ModelFetch.Running(done, total, shownSpeed))
        }
        report(written)
        body.byteStream().use { input ->
            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(256 * 1024)
                var held = ByteArray(0)
                while (true) {
                    guard()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (!checked) {
                        held += buffer.copyOf(count)
                        if (held.size < 8) continue
                        if (!held.decodeToString(0, 8).startsWith("LITERTLM")) {
                            output.close()
                            partial.delete()
                            throw IOException("model page")
                        }
                        output.write(held)
                        written += held.size
                        checked = true
                        held = ByteArray(0)
                        report(written)
                        continue
                    }
                    output.write(buffer, 0, count)
                    written += count
                    report(written)
                }
                try {
                    output.fd.sync()
                } catch (_: Exception) {
                }
            }
        }
        if (partial.length() < MIN_BYTES) {
            partial.delete()
            throw IOException("short model")
        }
        val finished = File(partial.parentFile, PhoneModelFiles.E4B)
        if (finished.exists()) finished.delete()
        if (!partial.renameTo(finished)) {
            partial.copyTo(finished, overwrite = true)
            partial.delete()
        }
        ModelDownload.publish(ModelFetch.Ready(finished.name, finished.length()))
    }

    private fun guard() {
        val gate = ModelDownload.currentGate(context)
        if (gate == DownloadGate.RUN || gate == DownloadGate.WAIT) return
        ModelDownload.keepHold(gate)
        throw DownloadPause()
    }

    private companion object {
        const val MIN_BYTES = 100_000_000L
    }
}
