package com.rakshak.core.classifier

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLiteCrashClassifier — TensorFlow Lite based crash classifier.
 *
 * Loads a .tflite 1D CNN model that classifies sensor windows as:
 *  - 0: Normal activity
 *  - 1: Possible crash/fall
 *
 * Model input:  [1, 100, 6] float tensor
 * Model output: [1, 2] float tensor (probabilities for [normal, crash])
 *
 * Automatically copies the trained .tflite model from APK assets to the app's
 * files directory if not present, ensuring reliable loading across devices.
 *
 * If the model file is not present or fails to load, [isReady] returns false and
 * [classify] returns null, allowing the system to safely fall back to
 * [ThresholdFallbackClassifier].
 */
class TFLiteCrashClassifier(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
) : CrashClassifier {

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    override val isReady: Boolean get() = modelLoaded

    /**
     * Attempt to load the TFLite model.
     * Automatically ensures the asset is present in internal storage before opening.
     */
    fun loadModel(): Boolean {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val modelFile = File(modelsDir, modelFileName)

        // If file does not exist in filesDir, copy it from APK assets
        if (!modelFile.exists()) {
            val assetPath = "models/$modelFileName"
            val copied = copyAssetToFile(assetPath, modelFile)
            if (!copied) {
                Log.w(TAG, "TFLite model asset '$assetPath' not found in APK assets or filesDir")
                return false
            }
        }

        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false) // Verified fallback: CPU execution only
            }
            interpreter = Interpreter(loadMappedFile(modelFile), options)
            modelLoaded = true
            Log.i(TAG, "TFLite model loaded successfully from: ${modelFile.absolutePath}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load TFLite model", e)
            modelLoaded = false
            false
        }
    }

    override fun classify(sensorWindow: FloatArray, sampleCount: Int): CrashClassificationResult? {
        if (!modelLoaded || interpreter == null) return null

        val startTime = System.currentTimeMillis()

        return try {
            val inputBuffer = ByteBuffer.allocateDirect(4 * SensorPreprocessor.INPUT_SIZE)
                .order(ByteOrder.nativeOrder())
            for (value in sensorWindow) {
                inputBuffer.putFloat(value)
            }

            val outputBuffer = ByteBuffer.allocateDirect(4 * 2) // [normal, crash]
                .order(ByteOrder.nativeOrder())

            interpreter?.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val normalProb = outputBuffer.float
            val crashProb = outputBuffer.float

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "TFLite inference: normal=$normalProb crash=$crashProb in ${elapsed}ms")

            CrashClassificationResult(
                predictedClass = if (crashProb > normalProb) 1 else 0,
                crashProbability = crashProb,
                inferenceTimeMs = elapsed,
                classifierType = CLASSIFIER_TYPE,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "TFLite inference failed", e)
            null
        }
    }

    override fun release() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
    }

    private fun copyAssetToFile(assetPath: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied asset '$assetPath' -> '${targetFile.absolutePath}'")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy asset '$assetPath': ${e.message}")
            false
        }
    }

    private fun loadMappedFile(file: File): MappedByteBuffer {
        FileInputStream(file).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }

    companion object {
        private const val TAG = "TFLiteCrashClassifier"
        const val CLASSIFIER_TYPE = "tflite_1d_cnn"
        const val DEFAULT_MODEL_FILE = "crash_classifier.tflite"
    }
}
