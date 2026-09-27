package com.rakshak.core.ai.llm

import android.content.Context
import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * LlamaAndroidEngine — on-device GGUF LLM inference engine using llama-android (llama.cpp).
 *
 * Implements [LlmEngine] contract for executing GGUF quantized models (e.g. Qwen2.5-0.5B-Instruct-Q4_K_M.gguf)
 * directly on ARM64 Android CPU using native libllama.so / libllama_jni.so.
 */
class LlamaAndroidEngine(
    private val context: Context,
) : LlmEngine {

    private var model: LlamaModel? = null
    private var loadedModelPath: String? = null

    override val isLoaded: Boolean
        get() = model?.isLoaded == true

    @Synchronized
    override fun loadModel(
        modelPath: String,
        nThreads: Int,
        contextSize: Int,
    ): Boolean {
        if (isLoaded && loadedModelPath == modelPath) {
            Log.i(TAG, "Model already loaded: $modelPath")
            return true
        }

        unload()

        return try {
            Log.i(TAG, "Loading on-device GGUF model via llama-android from path: $modelPath")
            val startTime = System.currentTimeMillis()

            val actualFile = resolveModelFile(modelPath)
            if (!actualFile.exists() || actualFile.length() == 0L) {
                Log.e(TAG, "Model file does not exist or is empty: ${actualFile.absolutePath}")
                return false
            }

            val config = LlamaConfig(
                contextSize = contextSize,
                threads = nThreads,
                gpuLayers = 0, // CPU execution
                temperature = 0.1f,
                topP = 0.95f,
                topK = 40,
                seed = 42
            )

            model = runBlocking {
                Llama.loadModel(actualFile.absolutePath, config)
            }
            loadedModelPath = modelPath

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Successfully loaded GGUF model in ${elapsed}ms: ${actualFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GGUF LLM model via llama-android", e)
            unload()
            false
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        stopSequences: List<String>,
    ): LlmGenerationResult? {
        val currentModel = model
        if (currentModel == null || !currentModel.isLoaded) {
            Log.e(TAG, "Cannot generate text: LlamaAndroidEngine model is not loaded")
            return null
        }

        return try {
            Log.i(TAG, "Executing on-device llama-android inference...")
            val startTime = System.currentTimeMillis()

            val result = runBlocking {
                Llama.complete(
                    model = currentModel,
                    prompt = prompt,
                    systemPrompt = "",
                    maxTokens = maxTokens
                )
            }

            val response = result.text.trim()
            val elapsed = System.currentTimeMillis() - startTime

            Log.i(TAG, "llama-android generation finished in ${elapsed}ms (${response.length} chars, ${result.tokensGenerated} tokens)")

            LlmGenerationResult(
                text = response,
                tokenCount = result.tokensGenerated,
                generationTimeMs = elapsed,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during llama-android generation", e)
            null
        }
    }

    @Synchronized
    override fun unload() {
        try {
            model?.let {
                Llama.releaseModel(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LlamaModel instance", e)
        } finally {
            model = null
            loadedModelPath = null
            Log.i(TAG, "LlamaAndroidEngine unloaded")
        }
    }

    private fun resolveModelFile(path: String): File {
        val file = File(path)
        if (file.isAbsolute && file.exists()) {
            return file
        }

        val internalFile = File(context.filesDir, path)
        if (internalFile.exists()) {
            return internalFile
        }

        val assetFileName = if (path.contains("/")) path.substringAfterLast("/") else path
        val destFile = File(context.filesDir, "models/$assetFileName")

        if (!destFile.exists() || destFile.length() == 0L) {
            destFile.parentFile?.mkdirs()
            Log.i(TAG, "Copying GGUF model from assets/$path to ${destFile.absolutePath}...")
            context.assets.open(path).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "GGUF asset copy finished: ${destFile.length()} bytes")
        }

        return destFile
    }

    companion object {
        private const val TAG = "LlamaAndroidEngine"
    }
}
