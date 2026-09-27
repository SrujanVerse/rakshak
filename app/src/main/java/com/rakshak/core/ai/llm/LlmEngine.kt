package com.rakshak.core.ai.llm

/**
 * LlmEngine — abstraction for on-device LLM inference.
 *
 * This interface decouples the report generator from the specific LLM runtime.
 * Implementations:
 *  - Future: LlamaCppEngine (JNI bridge to llama.cpp for GGUF models)
 *  - Future: MediaPipeLlmEngine (Google MediaPipe/LiteRT)
 *
 * Contract:
 *  - Implementations MUST be thread-safe.
 *  - [generate] MUST respect the timeout or return within a bounded time.
 *  - [loadModel] may be called at any time; double-load must be a no-op.
 *  - [unload] may be called at any time; double-unload must be a no-op.
 */
interface LlmEngine {

    /** True if a model is loaded and ready for inference. */
    val isLoaded: Boolean

    /**
     * Load a GGUF model from the given file path.
     *
     * @param modelPath Absolute path to the .gguf model file
     * @param nThreads Number of CPU threads to use (default: 4)
     * @param contextSize Maximum context window in tokens (default: 1024)
     * @return true if model loaded successfully, false otherwise
     */
    fun loadModel(
        modelPath: String,
        nThreads: Int = 4,
        contextSize: Int = 1024,
    ): Boolean

    /**
     * Generate text from a prompt.
     *
     * @param prompt The full prompt string (including system/user/assistant markers)
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Sampling temperature (0.0 = greedy, 1.0 = creative)
     * @param stopSequences Strings that stop generation when emitted
     * @return Generated text, or null on failure
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 128,
        temperature: Float = 0.1f,
        stopSequences: List<String> = listOf("<|im_end|>", "<|endoftext|>"),
    ): LlmGenerationResult?

    /** Unload the model and free memory. */
    fun unload()
}

/**
 * Result of a single LLM generation call.
 */
data class LlmGenerationResult(
    val text: String,
    val tokenCount: Int,
    val generationTimeMs: Long,
)
