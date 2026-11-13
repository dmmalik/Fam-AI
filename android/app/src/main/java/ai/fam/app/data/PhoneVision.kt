package ai.fam.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

data class PhoneVisionResult(
    val reply: VisionReply,
    val modelId: String,
    val modelVersion: String,
)

class PhoneVision(context: Context) {
    private val app = context.applicationContext
    private val lock = Any()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var modelId: String = ""
    private var modelVersion: String = ""
    private var failedKey: String? = null

    fun locate(): File? = locateModel(app)

    fun ask(jpeg: File, labels: List<LabelInfo>, language: String, crop: String?): PhoneVisionResult? {
        val file = locate() ?: return null
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        return try {
            synchronized(lock) {
                if (failedKey == key && engine == null) return null
                val ready = try {
                    ensure(file)
                } catch (exc: Throwable) {
                    if (exc is CancellationException) throw exc
                    Log.e(TAG, "gemma load failed", exc)
                    closeEngine()
                    failedKey = key
                    return null
                }
                val reply = generate(ready, jpeg, labels, language, crop)
                PhoneVisionResult(reply, modelId, modelVersion)
            }
        } catch (exc: Throwable) {
            if (exc is CancellationException) throw exc
            Log.e(TAG, "gemma reply failed", exc)
            null
        }
    }

    private fun ensure(file: File): Engine {
        val current = engine
        if (current != null && current.isInitialized() && loadedPath == file.absolutePath) {
            return current
        }
        closeEngine()
        val opened = open(file)
        modelId = PhoneModelFiles.modelId(file.name)
        modelVersion = shortHash(file)
        loadedPath = file.absolutePath
        failedKey = null
        engine = opened
        return opened
    }

    private fun open(file: File): Engine {
        val cache = File(app.cacheDir, "litertlm").apply { mkdirs() }
        val attempts = listOf(
            Backend.GPU() to Backend.GPU(),
            Backend.CPU() to Backend.CPU(),
        )
        var last: Throwable? = null
        for ((main, vision) in attempts) {
            val created = Engine(
                EngineConfig(
                    modelPath = file.absolutePath,
                    backend = main,
                    visionBackend = vision,
                    maxNumImages = 1,
                    maxNumTokens = 4096,
                    cacheDir = cache.absolutePath,
                ),
            )
            try {
                created.initialize()
                Log.i(TAG, "gemma loaded on ${main.name}")
                return created
            } catch (exc: Throwable) {
                last = exc
                Log.w(TAG, "gemma ${main.name} failed", exc)
                try {
                    created.close()
                } catch (closeError: Throwable) {
                    Log.w(TAG, "gemma close failed", closeError)
                }
            }
        }
        throw last ?: IllegalStateException("gemma")
    }

    private fun generate(
        ready: Engine,
        jpeg: File,
        labels: List<LabelInfo>,
        language: String,
        crop: String?,
    ): VisionReply {
        val png = pngBytes(jpeg)
        val prompt = VisionReplyParser.prompt(labels, language, crop)
        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "You look at one crop photo, name the plant and the disease or pest, and write a short reply the grower can use. Answer with JSON only.",
            ),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.8, temperature = 0.2, seed = 1),
            maxOutputToken = VisionReplyParser.MAX_OUTPUT_TOKENS,
            thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
        )
        val conversation: Conversation = ready.createConversation(config)
        try {
            val message = conversation.sendMessage(
                Contents.of(
                    Content.Text(prompt),
                    Content.ImageBytes(png),
                ),
            )
            val reply = VisionReplyParser.parse(messageText(message), labels)
                ?: throw IllegalStateException("gemma json")
            return reply
        } finally {
            conversation.close()
        }
    }

    private fun closeEngine() {
        try {
            engine?.close()
        } catch (exc: Throwable) {
            Log.w(TAG, "gemma close failed", exc)
        }
        engine = null
        loadedPath = null
    }

    companion object {
        const val BACKEND = "on-device gemma4"
        const val ADVICE_SOURCE = "gemma"
        private const val TAG = "FamAI"

        fun modelDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

        fun locateModel(context: Context): File? {
            val internal = modelDir(context)
            val external = context.getExternalFilesDir(null)?.let { root ->
                File(root, "models").apply { mkdirs() }
            }
            val files = listOfNotNull(internal, external).flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile && file.length() > 0L && !file.name.endsWith(PhoneModelFiles.PARTIAL_SUFFIX)
                }.orEmpty()
            }
            val name = PhoneModelFiles.pick(files.map { it.name }) ?: return null
            val matches = files.filter { it.name == name }
            return matches.firstOrNull { it.parentFile?.absolutePath == internal.absolutePath }
                ?: matches.firstOrNull()
        }

        private fun messageText(message: Message): String {
            val text = message.contents.contents.mapNotNull { item ->
                (item as? Content.Text)?.text
            }.joinToString("\n")
            return text.ifBlank { message.toString() }
        }

        private fun pngBytes(jpeg: File): ByteArray {
            val bitmap = BitmapFactory.decodeFile(jpeg.absolutePath)
                ?: throw IllegalStateException("gemma image")
            try {
                val stream = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("gemma png")
                }
                return stream.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }

        private fun shortHash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
