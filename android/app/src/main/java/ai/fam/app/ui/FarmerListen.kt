package ai.fam.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.fam.app.R
import ai.fam.app.data.LanguageCatalog
import ai.fam.app.data.speechForFarmer
import java.util.Locale

@Composable
fun FarmerListen(script: String, language: String, farmerName: String = "") {
    val context = LocalContext.current
    val spoken = speechForFarmer(farmerName, script)
    val speaker = androidx.compose.runtime.remember(language) { FarmerSpeaker(context, language) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    LaunchedEffect(speaker.ready, speaker.voiceMissing, spoken) {
        if (speaker.ready && !speaker.voiceMissing && spoken.isNotBlank()) speaker.speak(spoken)
    }
    val label = when {
        speaker.voiceMissing -> R.string.listen_add_voice
        speaker.speaking -> R.string.listen_stop
        else -> R.string.listen
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                when {
                    speaker.speaking -> speaker.stop()
                    speaker.voiceMissing -> openVoiceInstall(context)
                    else -> speaker.speak(spoken)
                }
            },
            enabled = spoken.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { contentDescription = "listen" },
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
        }
        if (speaker.voiceMissing) {
            val missing = if (speaker.needsIndia) R.string.listen_india_missing else R.string.listen_no_voice
            Text(stringResource(missing), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun openVoiceInstall(context: Context) {
    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The written result stays on the page.
    }
}

class FarmerSpeaker(context: Context, private val language: String) {
    private val app = context.applicationContext
    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var voiceMissing by mutableStateOf(false)
        private set
    var needsIndia by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                voiceMissing = true
                ready = true
                return@TextToSpeech
            }
            val applied = applyLanguage(tts)
            needsIndia = wantsIndianVoice() && !applied
            voiceMissing = !applied
            tts.setSpeechRate(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onMain { speaking = true }
                override fun onDone(utteranceId: String?) = onMain { speaking = false }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onMain { speaking = false }
            })
            ready = true
        }
    }

    fun speak(text: String) {
        val tts = engine ?: return
        if (text.isBlank() || voiceMissing) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fam-result")
    }

    fun stop() {
        engine?.stop()
        speaking = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun wantsIndianVoice(): Boolean {
        return LanguageCatalog.ttsTags(app, language).any { it.endsWith("-IN") }
    }

    private fun applyLanguage(tts: TextToSpeech): Boolean {
        val tags = LanguageCatalog.ttsTags(app, language)
        val voices = tts.voices.orEmpty()
        val indian = voices.filter { voice ->
            tags.any { tag ->
                val locale = Locale.forLanguageTag(tag)
                voice.locale.language.equals(locale.language, ignoreCase = true) &&
                    voice.locale.country.equals("IN", ignoreCase = true)
            }
        }
        val chosen = indian.firstOrNull { !it.isNetworkConnectionRequired } ?: indian.firstOrNull()
        if (chosen != null) {
            tts.setLanguage(chosen.locale)
            tts.voice = chosen
            return true
        }
        if (wantsIndianVoice()) return false
        for (tag in tags) {
            val code = tts.setLanguage(Locale.forLanguageTag(tag))
            if (code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED) return true
        }
        return false
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }
}
