package ai.fam.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

sealed class PackFetch {
    data object Missing : PackFetch()
    data object NeedsPi : PackFetch()
    data object Waiting : PackFetch()
    data class Paused(val gate: DownloadGate) : PackFetch()
    data class Running(val done: Long, val total: Long) : PackFetch()
    data class Ready(val bytes: Long) : PackFetch()
    data class Failed(val needsPi: Boolean) : PackFetch()
}

object LanguagePackDownload {
    private val states = MutableStateFlow<Map<String, PackFetch>>(emptyMap())
    val state: StateFlow<Map<String, PackFetch>> = states
    private val busy = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun refresh(context: Context) {
        val store = LanguagePackStore(context)
        val known = store.installedCodes().associateWith { code ->
            val bytes = store.file(code)?.length() ?: 0L
            PackFetch.Ready(bytes) as PackFetch
        }
        states.value = known
    }

    fun status(code: String): PackFetch = states.value[code] ?: PackFetch.Missing

    fun start(context: Context, code: String, origin: String, token: String) {
        val app = context.applicationContext
        if (code in STORED_LANGUAGES) return
        if (token.isBlank() || PiAddress.parse(origin) == null) {
            put(code, PackFetch.NeedsPi)
            return
        }
        val gate = gate(app)
        when (gate) {
            DownloadGate.WAIT -> {
                put(code, PackFetch.Waiting)
                return
            }
            DownloadGate.UNMETERED, DownloadGate.BACKGROUND, DownloadGate.SAVER -> {
                put(code, PackFetch.Paused(gate))
                return
            }
            DownloadGate.RUN -> Unit
        }
        if (!busy.compareAndSet(false, true)) return
        put(code, PackFetch.Running(0L, -1L))
        thread(name = "language-pack", isDaemon = true) {
            try {
                val raw = fetch(origin, token, code) { done, total ->
                    put(code, PackFetch.Running(done, total))
                }
                val kb = KnowledgeBase.load(app.assets)
                val saved = LanguagePackStore(app).save(code, raw, kb)
                if (saved == null) {
                    put(code, PackFetch.Failed(needsPi = false))
                } else {
                    put(code, PackFetch.Ready(raw.toByteArray(Charsets.UTF_8).size.toLong()))
                }
            } catch (exc: Exception) {
                val needsPi = exc.message == "needs-pi" || exc is java.io.IOException
                put(code, PackFetch.Failed(needsPi = needsPi))
            } finally {
                busy.set(false)
            }
        }
    }

    fun remove(context: Context, code: String) {
        LanguagePackStore(context).remove(code)
        val next = states.value.toMutableMap()
        next.remove(code)
        states.value = next
    }

    fun gate(context: Context): DownloadGate {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val caps = network?.let { manager.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val choices = DownloadChoicesStore(context).read()
        return chooseDownload(
            online = connected,
            metered = manager?.isActiveNetworkMetered == true,
            foreground = true,
            backgroundAllowed = choices.background,
            mobileAllowed = choices.mobile,
            dataSaver = ModelDownload.dataSaverOn(context),
        )
    }

    private fun fetch(origin: String, token: String, code: String, onProgress: (Long, Long) -> Unit): String {
        val request = Request.Builder()
            .url("$origin/v1/language-pack/$code")
            .header("Authorization", "Bearer $token")
            .header("Cache-Control", "no-store")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 404) throw IllegalStateException("needs-pi")
            if (!response.isSuccessful) throw IllegalStateException("needs-pi")
            val body = response.body ?: throw IllegalStateException("needs-pi")
            val total = body.contentLength()
            val bytes = body.byteStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var done = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    done += count
                    onProgress(done, total)
                }
                out.toByteArray()
            }
            val text = bytes.toString(Charsets.UTF_8)
            if (!text.trim().startsWith("{")) throw IllegalStateException("bad-pack")
            return text
        }
    }

    private fun put(code: String, value: PackFetch) {
        states.value = states.value.toMutableMap().apply { this[code] = value }
    }
}
