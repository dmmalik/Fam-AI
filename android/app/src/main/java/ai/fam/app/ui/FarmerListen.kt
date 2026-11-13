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
import java.util.Locale

@Composable
fun FarmerListen(script: String, language: String) {
    val context = LocalContext.current
    val speaker = androidx.compose.runtime.remember(language) { FarmerSpeaker(context, language) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    LaunchedEffect(speaker.ready, speaker.voiceMissing, script) {
        if (speaker.ready && !speaker.voiceMissing && script.isNotBlank()) speaker.speak(script)
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
                    else -> speaker.speak(script)
                }
            },
            enabled = script.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { contentDescription = "listen" },
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
        }
        if (speaker.voiceMissing) {
            Text(stringResource(R.string.listen_no_voice), style = MaterialTheme.typography.bodyLarge)
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
            voiceMissing = !applyLanguage(tts)
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

    private fun applyLanguage(tts: TextToSpeech): Boolean {
        val options = LanguageCatalog.ttsTags(app, language).map { Locale.forLanguageTag(it) }
        for (locale in options) {
            val code = tts.setLanguage(locale)
            if (code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED) return true
        }
        return false
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }
}
