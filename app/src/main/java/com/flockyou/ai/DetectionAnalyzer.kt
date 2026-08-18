package com.flockyou.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.flockyou.R
import com.flockyou.data.*
import com.flockyou.data.model.Detection
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.ThreatLevel
import com.flockyou.data.repository.DetectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.*
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.detection.DetectionRegistry
import com.flockyou.detection.profile.DeviceTypeProfile as CentralizedProfile
import com.flockyou.detection.profile.DeviceTypeProfileRegistry
import com.flockyou.detection.profile.PrivacyImpact
import com.flockyou.detection.profile.Recommendation
import com.flockyou.detection.profile.RecommendationUrgency
import com.flockyou.detection.handler.DeviceTypeProfile as HandlerProfile
import com.flockyou.privilege.PrivilegeMode
import com.flockyou.privilege.PrivilegeModeDetector

/**
 * Result type for progressive analysis pipeline.
 * Allows UI to show immediate rule-based results while LLM analysis runs in background.
 *
 * Usage:
 * ```
 * detectionAnalyzer.analyzeProgressively(detection).collect { result ->
 *     when (result) {
 *         is ProgressiveAnalysisResult.RuleBasedResult -> showQuickResult(result.analysis)
 *         is ProgressiveAnalysisResult.LlmResult -> showFinalResult(result.analysis)
 *         is ProgressiveAnalysisResult.Error -> showError(result.error, result.fallbackAnalysis)
 *     }
 * }
 * ```
 */
sealed class ProgressiveAnalysisResult {
    /**
     * Quick rule-based analysis result (< 10ms).
     * Provides instant feedback while LLM analysis runs in background.
     *
     * @property analysis The quick analysis result
     * @property isComplete False - indicates more detailed analysis is coming
     */
    data class RuleBasedResult(
        val analysis: AiAnalysisResult,
        val isComplete: Boolean = false
    ) : ProgressiveAnalysisResult()

    /**
     * Full LLM analysis result.
     * This is the final, detailed analysis from the on-device LLM.
     *
     * @property analysis The comprehensive LLM analysis result
     * @property isComplete True - this is the final result
     */
    data class LlmResult(
        val analysis: AiAnalysisResult,
        val isComplete: Boolean = true
    ) : ProgressiveAnalysisResult()

    /**
     * Error during analysis with optional fallback.
     *
     * @property error Description of what went wrong
     * @property fallbackAnalysis Rule-based analysis to show if LLM fails
     */
    data class Error(
        val error: String,
        val fallbackAnalysis: AiAnalysisResult?
    ) : ProgressiveAnalysisResult()
}

/**
 * AI-powered detection analyzer using LOCAL ON-DEVICE LLM inference only.
 * No data is ever sent to cloud services - all analysis happens on the device.
 *
 * Features:
 * 1. Multiple selectable on-device LLM models (GGUF format via llama.cpp)
 * 2. Pixel NPU support for Gemini Nano
 * 3. Comprehensive rule-based analysis for all 50+ device types
 * 4. Contextual analysis (location patterns, time correlation, clustering)
 * 5. Batch analysis for surveillance density mapping
 * 6. Structured output parsing for programmatic use
 * 7. Analysis feedback tracking for learning
 * 8. Progressive analysis pipeline for instant user feedback
 */
@Singleton
class DetectionAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val detectionRepository: DetectionRepository,
    private val geminiNanoClient: GeminiNanoClient,
    private val mediaPipeLlmClient: MediaPipeLlmClient,
    private val falsePositiveAnalyzer: FalsePositiveAnalyzer,
    private val llmEngineManager: LlmEngineManager,
    private val detectionRegistry: DetectionRegistry,
    private val feedbackTracker: AnalysisFeedbackTracker,
    private val ruleBasedAnalyzer: RuleBasedAnalyzer,
    private val enrichedDataCache: EnrichedDataCache
) {
    companion object {
        private const val TAG = "DetectionAnalyzer"
        private const val MAX_FEEDBACK_HISTORY_SIZE = 500
        private const val DOWNLOAD_TIMEOUT_SECONDS = 300L
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Default)

    private val _modelStatus = MutableStateFlow<AiModelStatus>(AiModelStatus.NotDownloaded)
    val modelStatus: StateFlow<AiModelStatus> = _modelStatus.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Thread-safe analysis cache with semantic similarity support
    internal val analysisCache = Collections.synchronizedMap(mutableMapOf<String, CacheEntry>())
    internal val cacheMutex = Mutex()

    // Fast-path cache for quick lookups by device type + detection method + protocol
    internal val fastPathCache = java.util.concurrent.ConcurrentHashMap<String, CachedAnalysis>()

    // Cache statistics tracking
    internal val cacheStats = CacheStats()

    // Semantic cache settings
    private val semanticCacheEnabled = true

    // Efficient feedback storage with O(1) removal from front
    private val feedbackHistory = ArrayDeque<AnalysisFeedback>(MAX_FEEDBACK_HISTORY_SIZE)
    private val feedbackMutex = Mutex()

    // Reusable HTTP client with connection pooling for model downloads
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(maxIdleConnections = 2, keepAliveDuration = 5, TimeUnit.MINUTES))
            .build()
    }

    // Privilege mode for feasibility-aware analysis
    private val privilegeMode: PrivilegeMode = PrivilegeModeDetector.detect(context)

    // Thread-safe model state using atomic references
    private val currentModelRef = AtomicReference(AiModel.RULE_BASED)
    private val isModelLoadedRef = AtomicBoolean(false)
    private val geminiNanoInitializedRef = AtomicBoolean(false)
    private val modelStateMutex = Mutex()

    // Current model accessor (for backward compatibility)
    private var currentModel: AiModel
        get() = currentModelRef.get()
        set(value) = currentModelRef.set(value)

    private var isModelLoaded: Boolean
        get() = isModelLoadedRef.get()
        set(value) = isModelLoadedRef.set(value)

    private var geminiNanoInitialized: Boolean
        get() = geminiNanoInitializedRef.get()
        set(value) = geminiNanoInitializedRef.set(value)

    private var currentInferenceConfig: InferenceConfig = InferenceConfig(
        maxTokens = 1024,  // Increased to handle larger prompts (input + output combined)
        temperature = 0.7f,
        useGpuAcceleration = true,
        useNpuAcceleration = true
    )

    // Current analysis job for cancellation support
    private var currentAnalysisJob: Job? = null

    // Device capabilities
    private val deviceInfo: DeviceCapabilities by lazy { detectDeviceCapabilities() }

    // Initialize FP analyzer with lazy MediaPipe init callback
    init {
        falsePositiveAnalyzer.setLazyInitCallback {
            initializeMediaPipeForFpAnalysis()
        }
        // Set privilege mode on DescriptionGenerator for feasibility-aware confidence adjustments
        DescriptionGenerator.privilegeMode = privilegeMode
    }

    // Mutex for analysis to prevent concurrent analysis operations
    private val analysisMutex = Mutex()

    data class DeviceCapabilities(
        val isPixel8OrNewer: Boolean,
        val hasNpu: Boolean,
        val hasAiCore: Boolean,
        val availableRamMb: Long,
        val supportedModels: List<AiModel>
    )

    private fun detectDeviceCapabilities(): DeviceCapabilities {
        val isPixel8OrNewer = Build.MODEL.lowercase().let { model ->
            model.contains("pixel 8") || model.contains("pixel 9") ||
            model.contains("pixel 10") || model.contains("pixel 11") ||
            model.contains("pixel fold") || model.contains("pixel tablet")
        }

        // NPU available on Pixel 8+ with Tensor G3/G4/G5
        val hasNpu = isPixel8OrNewer && Build.VERSION.SDK_INT >= 34

        // Check if AICore is available for Gemini Nano
        val hasAiCore = try {
            context.packageManager.getPackageInfo("com.google.android.aicore", 0)
            true
        } catch (e: Exception) {
            false
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        // Use totalMem instead of availMem for model compatibility - availMem fluctuates,
        // but we want to show models the device CAN run, not just what fits right now
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        val supportedModels = AiModel.getAvailableModels(isPixel8OrNewer, hasNpu, totalRamMb)

        return DeviceCapabilities(isPixel8OrNewer, hasNpu, hasAiCore, totalRamMb, supportedModels)
    }

    /**
     * Get device capabilities for UI
     */
    fun getDeviceCapabilities(): DeviceCapabilities = deviceInfo

    /**
     * Get current inference configuration for model calls
     */
    fun getInferenceConfig(): InferenceConfig = currentInferenceConfig

    /**
     * Initialize the selected AI model for inference.
     * Uses mutex to prevent race conditions during concurrent initialization attempts.
     *
     * The initialization follows a smart fallback chain:
     * 1. ML Kit GenAI (Gemini Nano) - Alpha API, Pixel 8+ only, best quality
     * 2. MediaPipe LLM (Gemma models) - Stable API, works on most devices
     * 3. Rule-based analysis - Always available fallback
     */
    suspend fun initializeModel(): Boolean = modelStateMutex.withLock {
        Log.i(TAG, "=== initializeModel START ===")
        withContext(Dispatchers.IO) {
            try {
                val settings = aiSettingsRepository.settingsSnapshot()
                Log.d(TAG, "initializeModel settings: enabled=${settings.enabled}, selectedModel=${settings.selectedModel}")

                if (!settings.enabled) {
                    Log.w(TAG, "AI analysis is disabled in settings - returning false")
                    return@withContext false
                }

                _modelStatus.value = AiModelStatus.Initializing
                // Reset model state before initialization
                isModelLoaded = false
                geminiNanoInitialized = false

                // Read the model from settings - this is the source of truth
                // settings.selectedModel contains the model ID that was saved after download
                val modelFromSettings = AiModel.fromId(settings.selectedModel)
                Log.d(TAG, "Model from settings: ${modelFromSettings.id} (${modelFromSettings.displayName})")
                Log.d(TAG, "Current in-memory model: ${currentModel.id}")

                // Always use the model from settings as the source of truth
                currentModel = modelFromSettings

                // Load inference configuration from settings
                currentInferenceConfig = InferenceConfig.fromSettings(settings)
                Log.d(TAG, "Initializing model: ${currentModel.displayName} (${currentModel.id})")
                Log.d(TAG, "Inference config: maxTokens=${currentInferenceConfig.maxTokens}, " +
                    "temp=${currentInferenceConfig.temperature}, " +
                    "gpu=${currentInferenceConfig.useGpuAcceleration}, " +
                    "npu=${currentInferenceConfig.useNpuAcceleration}")

                // Use LlmEngineManager for smart fallback chain initialization
                val initialized = llmEngineManager.initialize(modelFromSettings, settings)

                if (initialized) {
                    // Update local state based on engine manager result
                    val activeEngine = llmEngineManager.activeEngine.value
                    Log.d(TAG, "LlmEngineManager initialized with engine: $activeEngine")

                    when (activeEngine) {
                        LlmEngine.GEMINI_NANO -> {
                            geminiNanoInitialized = true
                            isModelLoaded = true
                            currentModel = AiModel.GEMINI_NANO
                        }
                        LlmEngine.MEDIAPIPE -> {
                            isModelLoaded = true
                            // Keep the model from settings if it's a MediaPipe model
                            if (currentModel.modelFormat != ModelFormat.TASK) {
                                currentModel = AiModel.GEMMA3_1B // Default to smallest
                            }
                        }
                        LlmEngine.RULE_BASED -> {
                            isModelLoaded = true
                            currentModel = AiModel.RULE_BASED
                        }
                    }

                    // Check if user wanted a specific model but got rule-based fallback
                    val wantedGeminiNano = modelFromSettings == AiModel.GEMINI_NANO
                    val gotRuleBasedFallback = activeEngine == LlmEngine.RULE_BASED

                    if (wantedGeminiNano && gotRuleBasedFallback) {
                        // User wanted Gemini Nano but it's not available - check why
                        val nanoStatus = geminiNanoClient.getStatus()
                        Log.w(TAG, "User wanted Gemini Nano but got rule-based fallback. Nano status: $nanoStatus")
                        when (nanoStatus) {
                            is GeminiNanoStatus.NeedsDownload -> {
                                _modelStatus.value = AiModelStatus.NotDownloaded
                                Log.i(TAG, "Gemini Nano needs download - showing NotDownloaded status")
                            }
                            is GeminiNanoStatus.Downloading -> {
                                _modelStatus.value = AiModelStatus.Downloading((nanoStatus as GeminiNanoStatus.Downloading).progress)
                            }
                            is GeminiNanoStatus.Error -> {
                                _modelStatus.value = AiModelStatus.Error((nanoStatus as GeminiNanoStatus.Error).message)
                            }
                            else -> {
                                // Nano might be initializing or in some other state
                                _modelStatus.value = AiModelStatus.Ready
                            }
                        }
                    } else {
                        _modelStatus.value = AiModelStatus.Ready
                    }
                    Log.i(TAG, "Model initialized successfully via LlmEngineManager: $activeEngine, model=${currentModel.displayName}")
                    return@withContext true
                }

                // Fall back to rule-based only if initialization failed (should not happen)
                Log.w(TAG, "LlmEngineManager initialization failed, falling back to rule-based")
                currentModel = AiModel.RULE_BASED
                isModelLoaded = true
                _modelStatus.value = AiModelStatus.Ready
                Log.i(TAG, "Now using rule-based analysis as fallback")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AI model", e)
                // Reset model state on error to ensure consistent state
                isModelLoaded = false
                geminiNanoInitialized = false
                currentModel = AiModel.RULE_BASED
                _modelStatus.value = AiModelStatus.Error(e.message ?: "Unknown error")
                false
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun tryInitializeGeminiNano(settings: AiSettings): Boolean {
        Log.d(TAG, "Attempting to initialize Gemini Nano via ML Kit GenAI...")

        // First check device support
        if (!geminiNanoClient.isDeviceSupported()) {
            Log.d(TAG, "Device does not support Gemini Nano (requires Pixel 8+ with Android 14+)")
            return false
        }

        // Initialize via GeminiNanoClient (uses ML Kit GenAI)
        val initialized = geminiNanoClient.initialize()

        if (initialized) {
            geminiNanoInitialized = true
            isModelLoaded = true
            Log.i(TAG, "Gemini Nano initialized successfully via ML Kit GenAI")
            return true
        }

        // Check if download is needed
        val status = geminiNanoClient.getStatus()
        Log.w(TAG, "Gemini Nano initialization status: $status")

        when (status) {
            is GeminiNanoStatus.NeedsDownload -> {
                Log.i(TAG, "Gemini Nano model needs to be downloaded first")
                _modelStatus.value = AiModelStatus.NotDownloaded
            }
            is GeminiNanoStatus.Downloading -> {
                Log.i(TAG, "Gemini Nano model is currently downloading")
                _modelStatus.value = AiModelStatus.Downloading(0)
            }
            is GeminiNanoStatus.NotSupported -> {
                Log.w(TAG, "Gemini Nano is not supported on this device")
                _modelStatus.value = AiModelStatus.Error("Gemini Nano not supported on this device")
            }
            is GeminiNanoStatus.Error -> {
                Log.e(TAG, "Gemini Nano error: ${status.message}")
                _modelStatus.value = AiModelStatus.Error(status.message)
            }
            else -> {
                Log.w(TAG, "Unexpected Gemini Nano status: $status")
            }
        }

        return false
    }

    /**
     * Download the Gemini Nano model via ML Kit GenAI.
     * This is only needed on Pixel 8+ devices with AICore support.
     */
    suspend fun downloadGeminiNanoModel(onProgress: (Int) -> Unit): Boolean {
        if (!geminiNanoClient.isDeviceSupported()) {
            Log.w(TAG, "Cannot download Gemini Nano - device not supported")
            return false
        }

        Log.i(TAG, "Starting Gemini Nano model download...")
        val success = geminiNanoClient.downloadModel(onProgress)

        if (success) {
            Log.i(TAG, "Gemini Nano download completed, initializing...")
            // After download, initialize the model
            return geminiNanoClient.initialize()
        }

        return false
    }

    private suspend fun tryInitializeMediaPipeModel(settings: AiSettings): Boolean {
        val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
        // Get the correct file extension based on model's download URL
        val fileExtension = AiModel.getFileExtension(currentModel)
        val modelFile = File(modelDir, "${currentModel.id}$fileExtension")

        // Also check for alternate extension if primary doesn't exist
        val alternateFile = if (fileExtension == ".task") {
            File(modelDir, "${currentModel.id}.bin")
        } else {
            File(modelDir, "${currentModel.id}.task")
        }

        Log.d(TAG, "Looking for model files:")
        Log.d(TAG, "  Primary: ${modelFile.absolutePath} (exists=${modelFile.exists()}, size=${if (modelFile.exists()) modelFile.length() else 0})")
        Log.d(TAG, "  Alternate: ${alternateFile.absolutePath} (exists=${alternateFile.exists()}, size=${if (alternateFile.exists()) alternateFile.length() else 0})")

        // Also list all files in the model directory for debugging
        val allFiles = modelDir.listFiles()
        Log.d(TAG, "  All files in model dir: ${allFiles?.map { "${it.name} (${it.length() / 1024 / 1024}MB)" } ?: "none"}")

        val actualModelFile = when {
            modelFile.exists() && modelFile.length() > 1000 -> modelFile
            alternateFile.exists() && alternateFile.length() > 1000 -> alternateFile
            else -> {
                Log.w(TAG, "Model file not found!")
                Log.w(TAG, "  Expected: ${modelFile.absolutePath}")
                Log.w(TAG, "  Or: ${alternateFile.absolutePath}")
                Log.w(TAG, "  To use this model, download it first.")
                return false
            }
        }

        Log.i(TAG, "Found model file: ${actualModelFile.absolutePath} (${actualModelFile.length() / 1024 / 1024} MB)")
        Log.d(TAG, "Initializing MediaPipe LLM client...")

        // Initialize the MediaPipe LLM client with the model
        val config = InferenceConfig.fromSettings(settings)
        val success = mediaPipeLlmClient.initialize(actualModelFile, config)

        if (success) {
            isModelLoaded = true
            Log.i(TAG, "MediaPipe model initialized successfully!")
            Log.i(TAG, "  Model: ${currentModel.displayName}")
            Log.i(TAG, "  File: ${actualModelFile.name}")
            Log.i(TAG, "  isModelLoaded: $isModelLoaded")
            Log.i(TAG, "  mediaPipeLlmClient.isReady(): ${mediaPipeLlmClient.isReady()}")
        } else {
            Log.e(TAG, "Failed to initialize MediaPipe model!")
            Log.e(TAG, "  Status: ${mediaPipeLlmClient.getStatus()}")
        }

        return success
    }

    /**
     * Initialize MediaPipe specifically for FP analysis.
     * This is called lazily when FP analysis needs LLM but MediaPipe isn't ready
     * (e.g., when main model is GeminiNano or rule-based).
     *
     * Tries to find and load any available MediaPipe-compatible model,
     * preferring smaller models (Gemma 1B) for faster FP analysis.
     */
    private suspend fun initializeMediaPipeForFpAnalysis(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Attempting lazy MediaPipe initialization for FP analysis")

        // If MediaPipe is already ready, nothing to do
        if (mediaPipeLlmClient.isReady()) {
            Log.d(TAG, "MediaPipe already ready for FP analysis")
            return@withContext true
        }

        val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
        val allFiles = modelDir.listFiles() ?: emptyArray()

        // Find available MediaPipe-compatible models (prefer smaller ones)
        val mediaPipeModels = listOf(
            AiModel.GEMMA3_1B,    // Smallest, fastest - preferred for FP
            AiModel.GEMMA_2B_CPU, // Fallback - CPU version
            AiModel.GEMMA_2B_GPU  // Fallback - GPU version
        )

        for (model in mediaPipeModels) {
            val fileExtension = AiModel.getFileExtension(model)
            val modelFile = File(modelDir, "${model.id}$fileExtension")
            val alternateFile = if (fileExtension == ".task") {
                File(modelDir, "${model.id}.bin")
            } else {
                File(modelDir, "${model.id}.task")
            }

            val actualFile = when {
                modelFile.exists() && modelFile.length() > 1000 -> modelFile
                alternateFile.exists() && alternateFile.length() > 1000 -> alternateFile
                else -> null
            }

            if (actualFile != null) {
                Log.i(TAG, "Found MediaPipe model for FP analysis: ${actualFile.name}")
                val settings = aiSettingsRepository.settingsSnapshot()
                val config = InferenceConfig.fromSettings(settings)

                val success = mediaPipeLlmClient.initialize(actualFile, config)
                if (success) {
                    Log.i(TAG, "MediaPipe initialized successfully for FP analysis using ${model.displayName}")
                    return@withContext true
                } else {
                    Log.w(TAG, "Failed to initialize ${model.displayName} for FP analysis")
                }
            }
        }

        // Check if any .task or .bin file exists that we could try
        val anyModelFile = allFiles.firstOrNull {
            (it.name.endsWith(".task") || it.name.endsWith(".bin")) && it.length() > 1000
        }

        if (anyModelFile != null) {
            Log.i(TAG, "Found generic model file for FP analysis: ${anyModelFile.name}")
            val settings = aiSettingsRepository.settingsSnapshot()
            val config = InferenceConfig.fromSettings(settings)

            val success = mediaPipeLlmClient.initialize(anyModelFile, config)
            if (success) {
                Log.i(TAG, "MediaPipe initialized with generic model for FP analysis")
                return@withContext true
            }
        }

        Log.w(TAG, "No MediaPipe-compatible model found for FP analysis. " +
            "Download a Gemma model to enable LLM-enhanced FP detection.")
        return@withContext false
    }

    // ==================== CORE ANALYSIS PIPELINE ====================

    /**
     * Analyze a single detection with full context.
     * Supports cancellation - call cancelAnalysis() to abort.
     * Uses mutex to prevent concurrent analysis operations which could overload the model.
     */
    suspend fun analyzeDetection(detection: Detection): AiAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "=== analyzeDetection START ===")
        Log.d(TAG, "Detection: ${detection.id} (${detection.deviceType})")

        // Lazy initialization: ensure model is loaded before analysis
        // This handles cases where model was downloaded but not yet initialized
        val settings = aiSettingsRepository.settingsSnapshot()
        val modelFromSettings = AiModel.fromId(settings.selectedModel)

        Log.d(TAG, "Settings check:")
        Log.d(TAG, "  - enabled: ${settings.enabled}")
        Log.d(TAG, "  - analyzeDetections: ${settings.analyzeDetections}")
        Log.d(TAG, "  - selectedModel: ${settings.selectedModel}")
        Log.d(TAG, "  - modelFromSettings: ${modelFromSettings.id} (${modelFromSettings.displayName})")
        Log.d(TAG, "  - currentModel (in-memory): ${currentModel.id}")
        Log.d(TAG, "  - isModelLoaded: $isModelLoaded")
        Log.d(TAG, "  - mediaPipeLlmClient.isReady(): ${mediaPipeLlmClient.isReady()}")

        if (settings.enabled && modelFromSettings != AiModel.RULE_BASED && !isModelLoaded) {
            Log.i(TAG, "Model not loaded, attempting lazy initialization for: ${modelFromSettings.displayName}")
            val initialized = initializeModel()
            Log.d(TAG, "Lazy initialization result: $initialized")
            Log.d(TAG, "After init - isModelLoaded: $isModelLoaded, mediaPipeReady: ${mediaPipeLlmClient.isReady()}")
            if (!initialized) {
                Log.w(TAG, "Lazy initialization failed, will use rule-based fallback")
            }
        } else {
            Log.d(TAG, "Skipping lazy init: enabled=${settings.enabled}, modelFromSettings=${modelFromSettings.id}, isModelLoaded=$isModelLoaded")
        }

        // FAST-PATH: Try fast-path cache first (O(1) lookup by device type + method + protocol)
        val fastPathResult = tryFastPathCache(detection, fastPathCache, cacheStats)
        if (fastPathResult != null) {
            return@withContext fastPathResult.copy(
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Check if already analyzing - return early with informative message
        if (_isAnalyzing.value) {
            Log.d(TAG, "Analysis already in progress, checking cache for: ${detection.id}")
            // Try to serve from cache even if analysis is in progress (reuse settings from above)
            val cacheKey = "${detection.id}_${detection.deviceType}_${detection.threatLevel}_ctx${settings.enableContextualAnalysis}"
            val cached = analysisCache[cacheKey]
            val expiryMs = getCacheExpiryForDevice(detection.deviceType)
            if (cached != null && System.currentTimeMillis() - cached.timestamp < expiryMs) {
                cacheStats.hits++
                return@withContext cached.result.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Try to acquire the mutex, or return immediately if another analysis is running
        // Track lock ownership to ensure safe unlock in finally block
        var acquiredLock = false
        try {
            acquiredLock = analysisMutex.tryLock()
            if (!acquiredLock) {
                Log.d(TAG, "Analysis mutex busy, returning busy response for: ${detection.id}")
                return@withContext AiAnalysisResult(
                    success = false,
                    error = "Another analysis is in progress. Please wait.",
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Store reference to current job for cancellation support
            currentAnalysisJob = coroutineContext[Job]

            // Settings already loaded above for lazy initialization
            if (!settings.enabled || !settings.analyzeDetections) {
                Log.w(TAG, "Returning 'AI analysis is disabled': enabled=${settings.enabled}, analyzeDetections=${settings.analyzeDetections}")
                return@withContext AiAnalysisResult(
                    success = false,
                    error = "AI analysis is disabled"
                )
            }

            // Check for cancellation before expensive operations
            coroutineContext.ensureActive()

            // Check cache first before setting analyzing state (include contextual flag and model to avoid serving stale results)
            val cacheKey = "${detection.id}_${detection.deviceType}_${detection.threatLevel}_ctx${settings.enableContextualAnalysis}_${currentModel.id}"
            val cacheExpiryMs = getCacheExpiryForDevice(detection.deviceType)
            val cached = cacheMutex.withLock {
                val entry = analysisCache[cacheKey]
                if (entry != null && System.currentTimeMillis() - entry.timestamp < entry.expiryMs) {
                    entry.result
                } else null
            }
            if (cached != null) {
                cacheStats.hits++
                return@withContext cached.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Try semantic cache lookup for similar detections (when exact cache misses)
            val similarCached = findSimilarCachedResult(detection, analysisCache, cacheStats, semanticCacheEnabled)
            if (similarCached != null) {
                // Statistics already tracked in findSimilarCachedResult
                return@withContext similarCached.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Cache miss - track it
            cacheStats.misses++

            _isAnalyzing.value = true

            // Check for cancellation before gathering context
            coroutineContext.ensureActive()

            // Get contextual data if enabled
            val contextualInsights = if (settings.enableContextualAnalysis) {
                gatherContextualInsights(detection, detectionRepository)
            } else null

            // Check for cancellation before generating analysis
            coroutineContext.ensureActive()

            // Generate analysis
            val result = generateAnalysis(detection, contextualInsights, settings)

            // Check for cancellation before FP analysis
            coroutineContext.ensureActive()

            // Run false positive analysis if enabled
            val fpResult = if (settings.enableFalsePositiveFiltering) {
                val contextInfo = buildFpContextInfo(detection, contextualInsights)
                falsePositiveAnalyzer.analyzeForFalsePositive(detection, contextInfo)
            } else null

            // Apply feedback-based adjustments if enabled
            val feedbackAdjustedResult = if (settings.trackAnalysisFeedback) {
                adjustAnalysisWithFeedback(result, detection, feedbackTracker)
            } else {
                result
            }

            val processingTime = System.currentTimeMillis() - startTime
            val finalResult = feedbackAdjustedResult.copy(
                processingTimeMs = processingTime,
                isFalsePositive = fpResult?.isFalsePositive ?: false,
                falsePositiveConfidence = fpResult?.confidence ?: 0f,
                falsePositiveBanner = fpResult?.bannerMessage,
                falsePositiveReasons = fpResult?.allReasons?.map { it.description } ?: emptyList()
            )

            // Check for cancellation before caching
            coroutineContext.ensureActive()

            // Cache result with variable expiry based on device type
            if (finalResult.success) {
                val deviceExpiryMs = getCacheExpiryForDevice(detection.deviceType)
                cacheMutex.withLock {
                    pruneCache(analysisCache, fastPathCache)
                    analysisCache[cacheKey] = CacheEntry(
                        result = finalResult,
                        timestamp = System.currentTimeMillis(),
                        detection = detection,
                        expiryMs = deviceExpiryMs
                    )
                }
                // Also add to fast-path cache for quick lookups
                addToFastPathCache(detection, finalResult, fastPathCache)
            }

            finalResult
        } catch (e: CancellationException) {
            Log.d(TAG, "Analysis cancelled for detection: ${detection.id}")
            AiAnalysisResult(
                success = false,
                error = "Analysis cancelled",
                processingTimeMs = System.currentTimeMillis() - startTime,
                wasCancelled = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing detection", e)
            AiAnalysisResult(
                success = false,
                error = e.message ?: "Analysis failed",
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        } finally {
            _isAnalyzing.value = false
            currentAnalysisJob = null
            // Only unlock if we successfully acquired the lock
            if (acquiredLock) {
                analysisMutex.unlock()
            }
        }
    }

    /**
     * Cancel any ongoing analysis operation.
     * Safe to call even if no analysis is in progress.
     * Note: The mutex will be properly released by the coroutine's finally block
     * when the cancellation is processed.
     */
    fun cancelAnalysis() {
        currentAnalysisJob?.cancel()
        currentAnalysisJob = null
        _isAnalyzing.value = false
        // Note: We don't manually unlock the mutex here because:
        // 1. We can't safely check if WE own the lock (isLocked is not public API)
        // 2. The coroutine's finally block will handle proper unlock when cancelled
        Log.d(TAG, "Analysis cancellation requested")
    }

    // ==================== PROGRESSIVE ANALYSIS ====================

    /**
     * Analyze a detection progressively, providing instant feedback while LLM analysis runs.
     *
     * This method implements a progressive analysis pipeline:
     * 1. Check cache first - instant return if there's a valid cached result
     * 2. Emit rule-based result immediately (< 10ms) for instant user feedback
     * 3. Launch LLM analysis in background
     * 4. Emit LLM result when complete (replaces rule-based result in UI)
     *
     * @param detection The detection to analyze
     * @return Flow of progressive analysis results
     */
    fun analyzeProgressively(detection: Detection): Flow<ProgressiveAnalysisResult> = flow {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "=== analyzeProgressively START for ${detection.id} (${detection.deviceType}) ===")

        // Load settings once for the entire pipeline
        val settings = aiSettingsRepository.settingsSnapshot()

        // Step 1: Check cache first (instant return if hit)
        val cachedResult = tryGetCachedResult(detection, settings)
        if (cachedResult != null) {
            Log.d(TAG, "Cache hit for progressive analysis - returning cached LLM result")
            emit(ProgressiveAnalysisResult.LlmResult(
                analysis = cachedResult.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime
                ),
                isComplete = true
            ))
            return@flow
        }

        // Step 2: Emit rule-based result immediately for instant user feedback
        Log.d(TAG, "Emitting quick rule-based result...")
        val ruleBasedStartTime = System.currentTimeMillis()
        val quickResult = ruleBasedAnalyzer.analyzeQuick(detection)
        val ruleBasedTime = System.currentTimeMillis() - ruleBasedStartTime
        Log.d(TAG, "Rule-based analysis completed in ${ruleBasedTime}ms")

        emit(ProgressiveAnalysisResult.RuleBasedResult(
            analysis = quickResult.copy(
                processingTimeMs = ruleBasedTime,
                modelUsed = "rule-based-progressive"
            ),
            isComplete = false
        ))

        // Step 3: Check if AI analysis is enabled
        if (!settings.enabled || !settings.analyzeDetections) {
            Log.d(TAG, "AI analysis disabled - rule-based result is final")
            // Re-emit as complete since no LLM analysis will follow
            emit(ProgressiveAnalysisResult.RuleBasedResult(
                analysis = quickResult.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    modelUsed = "rule-based"
                ),
                isComplete = true
            ))
            return@flow
        }

        // Step 4: Check if we should use rule-based only model
        val modelFromSettings = AiModel.fromId(settings.selectedModel)
        if (modelFromSettings == AiModel.RULE_BASED) {
            Log.d(TAG, "Rule-based model selected - rule-based result is final")
            emit(ProgressiveAnalysisResult.RuleBasedResult(
                analysis = quickResult.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    modelUsed = "rule-based"
                ),
                isComplete = true
            ))
            return@flow
        }

        // Step 5: Launch LLM analysis
        Log.d(TAG, "Starting LLM analysis in background...")
        try {
            val llmResult = withContext(Dispatchers.IO) {
                analyzeDetection(detection)
            }

            if (llmResult.success) {
                Log.i(TAG, "LLM analysis completed successfully in ${llmResult.processingTimeMs}ms")
                emit(ProgressiveAnalysisResult.LlmResult(
                    analysis = llmResult.copy(
                        processingTimeMs = System.currentTimeMillis() - startTime
                    ),
                    isComplete = true
                ))
            } else {
                // LLM failed - emit error with rule-based fallback
                Log.w(TAG, "LLM analysis failed: ${llmResult.error}")
                emit(ProgressiveAnalysisResult.Error(
                    error = llmResult.error ?: "LLM analysis failed",
                    fallbackAnalysis = quickResult.copy(
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        modelUsed = "rule-based-fallback"
                    )
                ))
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Progressive analysis cancelled")
            emit(ProgressiveAnalysisResult.Error(
                error = "Analysis cancelled",
                fallbackAnalysis = quickResult
            ))
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM analysis", e)
            emit(ProgressiveAnalysisResult.Error(
                error = e.message ?: "Unknown error during analysis",
                fallbackAnalysis = quickResult.copy(
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    modelUsed = "rule-based-error-fallback"
                )
            ))
        }
    }

    /**
     * Try to get a cached result for the detection.
     * Returns null if no valid cache entry exists.
     */
    private suspend fun tryGetCachedResult(detection: Detection, settings: AiSettings): AiAnalysisResult? {
        // Try fast-path cache first
        val fastPathResult = tryFastPathCache(detection, fastPathCache, cacheStats)
        if (fastPathResult != null) {
            cacheStats.hits++
            cacheStats.fastPathHits++
            return fastPathResult
        }

        // Try semantic cache
        val cacheKey = "${detection.id}_${detection.deviceType}_${detection.threatLevel}_ctx${settings.enableContextualAnalysis}_${currentModel.id}"
        val cached = cacheMutex.withLock {
            val entry = analysisCache[cacheKey]
            if (entry != null && System.currentTimeMillis() - entry.timestamp < entry.expiryMs) {
                entry.result
            } else null
        }

        if (cached != null) {
            cacheStats.hits++
            return cached
        }

        // Try semantic similarity cache for similar detections
        val similarCached = findSimilarCachedResult(detection, analysisCache, cacheStats, semanticCacheEnabled)
        if (similarCached != null) {
            // Statistics already tracked in findSimilarCachedResult
            return similarCached
        }

        cacheStats.misses++
        return null
    }

    // ==================== FEEDBACK & FALSE POSITIVE ====================

    /**
     * Record user feedback for a detection analysis.
     * Call this when users take actions on detections to improve future analysis.
     *
     * @param detection The detection the user interacted with
     * @param action The type of action taken (DISMISSED, INVESTIGATED, MARKED_FALSE_POSITIVE, etc.)
     * @param analysis Optional analysis result if one was shown
     */
    suspend fun recordUserFeedback(
        detection: Detection,
        action: UserAction,
        analysis: AiAnalysisResult? = null
    ) {
        try {
            feedbackTracker.recordFeedback(detection, action, analysis)
            Log.d(TAG, "Recorded user feedback: $action for ${detection.deviceType}")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording user feedback", e)
        }
    }

    /**
     * Get the confidence adjustment factor for a device type based on feedback history.
     * Returns a multiplier (0.5-1.2) to apply to confidence scores.
     *
     * @param deviceType The device type to check
     * @return Confidence multiplier
     */
    suspend fun getConfidenceAdjustmentForDeviceType(deviceType: DeviceType): Float {
        return feedbackTracker.getConfidenceAdjustment(deviceType)
    }

    /**
     * Get aggregated feedback statistics for a device type.
     * Useful for displaying historical accuracy in UI.
     *
     * @param deviceType The device type to get stats for
     * @return Flow of FeedbackStats
     */
    fun getFeedbackStatsForDeviceType(deviceType: DeviceType) =
        feedbackTracker.getFeedbackStats(deviceType)

    /**
     * Get overall accuracy statistics across all device types.
     * Useful for displaying in settings/about screen.
     */
    suspend fun getOverallAccuracyStats() = feedbackTracker.getOverallAccuracyStats()

    /**
     * Get the feedback tracker for direct access if needed.
     */
    fun getFeedbackTracker(): AnalysisFeedbackTracker = feedbackTracker

    /**
     * Check a single detection for false positive likelihood.
     * Returns the FP result with banner message if applicable.
     */
    suspend fun checkForFalsePositive(detection: Detection): FalsePositiveResult {
        return falsePositiveAnalyzer.analyzeForFalsePositive(detection)
    }

    /**
     * Filter a list of detections, removing likely false positives.
     * Returns filtered results with FP explanations.
     *
     * @param detections List of detections to filter
     * @param confidenceThreshold Minimum FP confidence to filter (0.0-1.0, default 0.6)
     */
    suspend fun filterFalsePositives(
        detections: List<Detection>,
        confidenceThreshold: Float = 0.6f
    ): FilteredDetections {
        return falsePositiveAnalyzer.filterFalsePositives(
            detections = detections,
            threshold = confidenceThreshold
        )
    }

    /**
     * Batch analyze detections for false positives.
     * Returns a map of detection ID to FP result.
     */
    suspend fun batchCheckFalsePositives(
        detections: List<Detection>
    ): Map<String, FalsePositiveResult> {
        return falsePositiveAnalyzer.analyzeMultiple(detections)
    }

    /**
     * Get the false positive analyzer for direct access if needed.
     */
    fun getFalsePositiveAnalyzer(): FalsePositiveAnalyzer = falsePositiveAnalyzer

    // ==================== ANALYSIS GENERATION ====================

    @Suppress("UNUSED_PARAMETER")
    private suspend fun generateAnalysis(
        detection: Detection,
        contextualInsights: ContextualInsights?,
        settings: AiSettings // Reserved for future use with inference configuration
    ): AiAnalysisResult {
        Log.i(TAG, "=== generateAnalysis START ===")
        Log.d(TAG, "State at generateAnalysis:")
        Log.d(TAG, "  - currentModel: ${currentModel.id} (${currentModel.displayName})")
        Log.d(TAG, "  - activeEngine: ${llmEngineManager.activeEngine.value}")
        Log.d(TAG, "  - isModelLoaded: $isModelLoaded")
        Log.d(TAG, "  - geminiNanoClient.isReady(): ${geminiNanoClient.isReady()}")
        Log.d(TAG, "  - mediaPipeLlmClient.isReady(): ${mediaPipeLlmClient.isReady()}")

        // Sync active engine to best available before analysis
        llmEngineManager.syncActiveEngine()

        // Use LlmEngineManager for analysis with automatic fallback
        val activeEngine = llmEngineManager.activeEngine.value
        Log.d(TAG, "Using LlmEngineManager with active engine: $activeEngine")

        // Check if any LLM engine is ready (not just the active one)
        // This allows fallback to work even if the primary engine is rule-based
        val geminiReady = llmEngineManager.isEngineReady(LlmEngine.GEMINI_NANO)
        val mediaPipeReady = llmEngineManager.isEngineReady(LlmEngine.MEDIAPIPE)
        val anyLlmReady = geminiReady || mediaPipeReady
        Log.d(TAG, "  geminiReady=$geminiReady, mediaPipeReady=$mediaPipeReady, anyLlmReady=$anyLlmReady")

        // Try LLM if active engine is LLM OR if any LLM engine is ready
        // The LlmEngineManager will handle the fallback chain internally
        val shouldTryLlm = (activeEngine != LlmEngine.RULE_BASED) || anyLlmReady

        if (shouldTryLlm) {
            // Retrieve enriched heuristics data for this detection if available
            val enrichedData = enrichedDataCache.get(detection.id)
            Log.d(TAG, "Attempting LLM analysis (activeEngine=$activeEngine, anyLlmReady=$anyLlmReady, hasEnrichedData=${enrichedData != null})")
            val llmResult = llmEngineManager.analyzeDetection(detection, enrichedData, privilegeMode)

            if (llmResult.success) {
                Log.i(TAG, "LLM analysis succeeded! Model: ${llmResult.modelUsed}, Response length: ${llmResult.analysis?.length ?: 0}")
                // Enhance with contextual insights if available
                return if (contextualInsights != null) {
                    llmResult.copy(
                        analysis = buildString {
                            append(llmResult.analysis ?: "")
                            appendLine()
                            appendLine("### Contextual Analysis")
                            contextualInsights.locationPattern?.let { appendLine("- Location: $it") }
                            contextualInsights.timePattern?.let { appendLine("- Time Pattern: $it") }
                            contextualInsights.clusterInfo?.let { appendLine("- Cluster: $it") }
                            contextualInsights.historicalContext?.let { appendLine("- History: $it") }
                        },
                        structuredData = buildStructuredData(detection, contextualInsights, detectionRegistry)
                    )
                } else {
                    llmResult.copy(structuredData = buildStructuredData(detection, null, detectionRegistry))
                }
            }

            // Log fallback reason
            Log.w(TAG, "LLM analysis failed (${llmResult.error}), using rule-based fallback")
        } else {
            Log.d(TAG, "No LLM engines available, using rule-based analysis")
        }

        // Use comprehensive rule-based analysis as fallback
        return generateRuleBasedAnalysis(detection, contextualInsights, detectionRegistry)
    }

    // ==================== CROSS-DETECTION PATTERN RECOGNITION ====================

    /**
     * Analyze patterns across multiple detections to identify coordinated surveillance,
     * following patterns, timing correlations, and geographic clustering.
     *
     * @param timeWindowMs Time window to consider (default: 1 hour)
     * @return List of identified patterns with confidence scores
     */
    suspend fun analyzePatterns(
        timeWindowMs: Long = 3600000L // 1 hour default
    ): List<PatternInsight> {
        return analyzeDetectionPatterns(timeWindowMs, detectionRepository, mediaPipeLlmClient)
    }

    // ==================== USER-FRIENDLY EXPLANATION ====================

    /**
     * Generate user-friendly explanation for a detection at the specified level.
     * Available levels: SIMPLE (for non-technical users), STANDARD, TECHNICAL.
     */
    suspend fun generateUserFriendlyExplanation(
        detection: Detection,
        level: PromptTemplates.ExplanationLevel = PromptTemplates.ExplanationLevel.STANDARD
    ): UserFriendlyExplanation {
        return generateUserFriendlyExplanationInternal(detection, level, mediaPipeLlmClient, detectionRegistry)
    }

    // ==================== BATCH ANALYSIS ====================

    /**
     * Batch analysis for surveillance density mapping.
     */
    suspend fun performBatchAnalysis(
        detections: List<Detection>
    ): BatchAnalysisResult {
        return performBatchAnalysisInternal(detections, aiSettingsRepository)
    }

    // ==================== THREAT ASSESSMENT ====================

    /**
     * Generate threat assessment for environment.
     */
    suspend fun generateThreatAssessment(
        detections: List<Detection>,
        criticalCount: Int,
        highCount: Int,
        mediumCount: Int,
        lowCount: Int
    ): AiAnalysisResult {
        return generateThreatAssessmentInternal(
            detections, criticalCount, highCount, mediumCount, lowCount,
            aiSettingsRepository, currentModel.id
        ) { _isAnalyzing.value = it }
    }

    // ==================== FEEDBACK RECORDING ====================

    /**
     * Record user feedback for analysis improvement.
     * Uses ArrayDeque for O(1) removal from front during pruning.
     */
    suspend fun recordFeedback(feedback: AnalysisFeedback) {
        feedbackMutex.withLock {
            feedbackHistory.addLast(feedback)
            // Efficient O(1) pruning with ArrayDeque
            while (feedbackHistory.size > MAX_FEEDBACK_HISTORY_SIZE) {
                feedbackHistory.removeFirst()
            }
        }
        // In production, this could update local preference weights
        Log.d(TAG, "Recorded feedback: ${feedback.feedbackType} for ${feedback.detectionId}")
    }

    // ==================== MODEL DOWNLOAD & MANAGEMENT ====================

    /**
     * Download selected model with retry logic and resumable download support.
     * Progress callbacks are dispatched to the Main thread for UI safety.
     */
    suspend fun downloadModel(
        modelId: String = currentModel.id,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        // Wrap progress callback to ensure it runs on Main thread for UI safety
        val safeProgress: suspend (Int) -> Unit = { progress ->
            withContext(Dispatchers.Main) {
                onProgress(progress)
            }
        }

        try {
            val model = AiModel.fromId(modelId)

            if (model == AiModel.RULE_BASED) {
                // No download needed
                safeProgress(100)
                return@withContext true
            }

            if (model == AiModel.GEMINI_NANO) {
                // Gemini Nano is managed by Google Play Services via ML Kit GenAI
                // Download and initialize the model
                _modelStatus.value = AiModelStatus.Downloading(0)
                val success = downloadGeminiNanoModel { progress ->
                    kotlinx.coroutines.runBlocking {
                        safeProgress(progress)
                    }
                    _modelStatus.value = AiModelStatus.Downloading(progress)
                }

                if (success) {
                    geminiNanoInitialized = true
                    isModelLoaded = true
                    _modelStatus.value = AiModelStatus.Ready
                    aiSettingsRepository.setEnabled(true)
                    aiSettingsRepository.setSelectedModel(model.id)
                    Log.i(TAG, "Gemini Nano download and initialization completed")
                } else {
                    _modelStatus.value = AiModelStatus.Error("Failed to download Gemini Nano model")
                    Log.w(TAG, "Gemini Nano download/init failed")
                }
                return@withContext success
            }

            // Get download URL via NetworkConfig for OEM customization support
            val downloadUrl = AiModel.getDownloadUrl(model)
            if (downloadUrl == null) {
                // Model requires manual download (e.g., from Kaggle)
                Log.w(TAG, "Model ${model.id} requires manual download. ${AiModel.getDownloadInstructions(model)}")
                _modelStatus.value = AiModelStatus.Error("Model requires manual download from Kaggle. See instructions in app.")
                return@withContext false
            }

            _modelStatus.value = AiModelStatus.Downloading(0)
            safeProgress(0)

            val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
            // Use the appropriate file extension based on model format
            val fileExtension = AiModel.getFileExtension(model)
            val modelFile = File(modelDir, "${model.id}$fileExtension")
            val tempFile = File(modelDir, "${model.id}$fileExtension.tmp")

            // Get HF token from settings for authenticated downloads
            val hfToken = aiSettingsRepository.settingsSnapshot().huggingFaceToken.takeIf { it.isNotBlank() }

            // Retry logic with exponential backoff
            var lastException: Exception? = null
            repeat(MAX_DOWNLOAD_RETRIES) { attempt ->
                try {
                    val success = downloadWithResume(downloadUrl, tempFile, modelFile, model.sizeMb * 1024 * 1024, safeProgress, hfToken)
                    if (success) {
                        // Update settings - enable AI and set model
                        aiSettingsRepository.setEnabled(true)  // Enable AI so initializeModel() works
                        aiSettingsRepository.setSelectedModel(model.id)
                        aiSettingsRepository.setModelDownloaded(true, modelFile.length() / (1024 * 1024))
                        currentModel = model
                        Log.i(TAG, "Model download completed, selected model: ${model.displayName}")
                        return@withContext true
                    }
                } catch (e: IOException) {
                    lastException = e
                    Log.w(TAG, "Download attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < MAX_DOWNLOAD_RETRIES - 1) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attempt) // Exponential backoff
                        Log.d(TAG, "Retrying in ${delayMs}ms...")
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }

            throw lastException ?: Exception("Download failed after $MAX_DOWNLOAD_RETRIES attempts")
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            _modelStatus.value = AiModelStatus.Error(e.message ?: "Download failed")
            false
        }
    }

    /**
     * Download with resume support for interrupted downloads.
     * Progress callback is invoked from IO thread but should be safe (wrapped by caller).
     * @param hfToken Optional Hugging Face token for authenticated downloads
     */
    private suspend fun downloadWithResume(
        downloadUrl: String,
        tempFile: File,
        finalFile: File,
        expectedSize: Long,
        onProgress: suspend (Int) -> Unit,
        hfToken: String? = null
    ): Boolean {
        // Check for existing partial download
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", context.getString(R.string.user_agent))

        // Add Hugging Face authorization header if token is provided
        if (!hfToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $hfToken")
            Log.d(TAG, "Using Hugging Face token for authenticated download")
        }

        // Add Range header for resume if we have partial data
        if (existingBytes > 0 && existingBytes < expectedSize) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            Log.d(TAG, "Resuming download from byte $existingBytes")
        }

        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            // Handle resume response (206) or fresh download (200)
            if (!response.isSuccessful && response.code != 206) {
                // If resume fails with 416 (Range Not Satisfiable), start fresh
                if (response.code == 416) {
                    tempFile.delete()
                    return downloadWithResume(downloadUrl, tempFile, finalFile, expectedSize, onProgress)
                }
                // Handle authentication errors (shouldn't happen with public repos)
                if (response.code == 401 || response.code == 403) {
                    throw IOException("Download failed: Authentication required (HTTP ${response.code}). Try using 'Import Model' to load a manually downloaded file.")
                }
                throw IOException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response")
            val contentLength = body.contentLength()
            val totalSize = if (response.code == 206) existingBytes + contentLength else contentLength

            // Use append mode for resume, otherwise create fresh
            val appendMode = response.code == 206
            var lastProgressUpdate = 0L
            FileOutputStream(tempFile, appendMode).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = existingBytes
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalSize > 0) {
                            ((downloadedBytes * 100) / totalSize).toInt().coerceIn(0, 99)
                        } else {
                            ((downloadedBytes / (expectedSize.toDouble())) * 100).toInt().coerceIn(0, 99)
                        }

                        // Throttle progress updates to avoid overwhelming the UI (max once per 100ms)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 100) {
                            _modelStatus.value = AiModelStatus.Downloading(progress)
                            onProgress(progress)
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            // Rename temp file to final file atomically
            if (tempFile.renameTo(finalFile)) {
                _modelStatus.value = AiModelStatus.Ready
                onProgress(100)
                Log.i(TAG, "Model downloaded: ${finalFile.name} (${finalFile.length() / 1024 / 1024} MB)")
                return true
            } else {
                throw IOException("Failed to rename temp file to final file")
            }
        }
    }

    /**
     * Import a model file from a Uri (e.g., from file picker).
     * Copies the file to the app's internal storage and sets it as the selected model.
     */
    suspend fun importModel(
        uri: android.net.Uri,
        modelId: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = AiModel.fromId(modelId)
            if (model == AiModel.RULE_BASED || model == AiModel.GEMINI_NANO) {
                Log.w(TAG, "Cannot import ${model.id} - not a file-based model")
                return@withContext false
            }

            _modelStatus.value = AiModelStatus.Downloading(0)
            onProgress(0)

            val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
            val fileExtension = AiModel.getFileExtension(model)
            val modelFile = File(modelDir, "${model.id}$fileExtension")

            // Copy from Uri to internal storage
            context.contentResolver.openInputStream(uri)?.use { input ->
                val fileSize = input.available().toLong().coerceAtLeast(1L)
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = ((totalRead * 100) / fileSize).toInt().coerceIn(0, 99)
                        _modelStatus.value = AiModelStatus.Downloading(progress)
                        onProgress(progress)
                    }
                }
            } ?: run {
                _modelStatus.value = AiModelStatus.Error("Could not open file")
                return@withContext false
            }

            // Verify the file was copied successfully
            if (!modelFile.exists() || modelFile.length() < 1000) {
                _modelStatus.value = AiModelStatus.Error("File copy failed or file too small")
                return@withContext false
            }

            Log.i(TAG, "Model imported: ${modelFile.name} (${modelFile.length() / 1024 / 1024} MB)")

            // Update settings - enable AI and set model
            aiSettingsRepository.setEnabled(true)  // Enable AI so initializeModel() works
            aiSettingsRepository.setModelDownloaded(true, modelFile.length() / (1024 * 1024))
            aiSettingsRepository.setSelectedModel(modelId)
            currentModel = model
            _modelStatus.value = AiModelStatus.Ready
            onProgress(100)

            // Initialize the model (AI is now enabled)
            initializeModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error importing model", e)
            _modelStatus.value = AiModelStatus.Error("Import failed: ${e.message}")
            false
        }
    }

    /**
     * Get the path to the models directory for manual placement.
     */
    fun getModelsDirectory(): File {
        return context.getDir("ai_models", Context.MODE_PRIVATE)
    }

    /**
     * Get the name of the currently active LLM engine.
     */
    val activeEngineName: StateFlow<String> = llmEngineManager.activeEngine
        .map { it.displayName }
        .stateIn(scope, SharingStarted.Eagerly, llmEngineManager.activeEngine.value.displayName)

    /**
     * Cancel an ongoing model download.
     */
    fun cancelDownload() {
        geminiNanoClient.cancelDownload()
        // Note: MediaPipe downloads are handled separately via HTTP client
        // For now, we can cancel Gemini Nano downloads
        Log.i(TAG, "Download cancellation requested")
    }

    /**
     * Get the set of downloaded model IDs.
     */
    suspend fun getDownloadedModelIds(): Set<String> = withContext(Dispatchers.IO) {
        val downloadedModels = mutableSetOf<String>()

        // Rule-based is always "downloaded"
        downloadedModels.add(AiModel.RULE_BASED.id)

        // Check Gemini Nano availability
        if (geminiNanoClient.isReady() || geminiNanoClient.getStatus() == GeminiNanoStatus.Ready) {
            downloadedModels.add(AiModel.GEMINI_NANO.id)
        }

        // Check for downloaded MediaPipe models
        val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
        AiModel.entries.filter { it.modelFormat == ModelFormat.TASK }.forEach { model ->
            val taskFile = File(modelDir, "${model.id}.task")
            val binFile = File(modelDir, "${model.id}.bin")
            if ((taskFile.exists() && taskFile.length() > 10_000_000) ||
                (binFile.exists() && binFile.length() > 10_000_000)) {
                downloadedModels.add(model.id)
            }
        }

        downloadedModels
    }

    /**
     * Get storage info for a downloaded model.
     * Returns a human-readable string like "529 MB" or null if not downloaded.
     */
    fun getModelStorageInfo(modelId: String): String? {
        val model = AiModel.fromId(modelId)

        when (model.modelFormat) {
            ModelFormat.NONE -> return "Built-in"
            ModelFormat.MLKIT_GENAI, ModelFormat.AICORE -> {
                return if (geminiNanoClient.isReady()) "Managed by AICore" else null
            }
            ModelFormat.TASK -> {
                val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
                val taskFile = File(modelDir, "${model.id}.task")
                val binFile = File(modelDir, "${model.id}.bin")

                val file = when {
                    taskFile.exists() -> taskFile
                    binFile.exists() -> binFile
                    else -> return null
                }

                val sizeMb = file.length() / (1024 * 1024)
                return "$sizeMb MB"
            }
        }
    }

    /**
     * Delete downloaded model.
     */
    suspend fun deleteModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
            modelDir.listFiles()?.forEach { it.delete() }

            aiSettingsRepository.setModelDownloaded(false, 0)
            aiSettingsRepository.setSelectedModel("rule-based")
            currentModel = AiModel.RULE_BASED
            isModelLoaded = true
            _modelStatus.value = AiModelStatus.Ready
            analysisCache.clear()

            Log.i(TAG, "Model deleted, using rule-based analysis")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            false
        }
    }

    /**
     * Switch to a different model.
     * Clears the analysis cache to prevent stale results from the previous model.
     */
    suspend fun selectModel(modelId: String): Boolean = modelStateMutex.withLock {
        val model = AiModel.fromId(modelId)

        // Clear cache when switching models to prevent serving stale results
        analysisCache.clear()
        Log.d(TAG, "Cleared analysis cache for model switch to: ${model.displayName}")

        if (model == AiModel.RULE_BASED) {
            currentModel = model
            isModelLoaded = true
            aiSettingsRepository.setSelectedModel(modelId)
            _modelStatus.value = AiModelStatus.Ready
            return@withLock true
        }

        // Gemini Nano is managed by Google Play Services, no file check needed
        if (model == AiModel.GEMINI_NANO) {
            if (!deviceInfo.isPixel8OrNewer || !deviceInfo.hasNpu) {
                Log.w(TAG, "Device does not support Gemini Nano")
                return@withLock false
            }
            currentModel = model
            aiSettingsRepository.setSelectedModel(modelId)
            return@withLock initializeModel()
        }

        // Check if model file exists (check both .task and .bin extensions)
        val modelDir = context.getDir("ai_models", Context.MODE_PRIVATE)
        val fileExtension = AiModel.getFileExtension(model)
        val modelFile = File(modelDir, "${model.id}$fileExtension")

        // Also check alternate extension
        val alternateExtension = if (fileExtension == ".task") ".bin" else ".task"
        val alternateFile = File(modelDir, "${model.id}$alternateExtension")

        val actualFile = when {
            modelFile.exists() && modelFile.length() > 1000 -> modelFile
            alternateFile.exists() && alternateFile.length() > 1000 -> alternateFile
            else -> null
        }

        return@withLock if (actualFile != null) {
            currentModel = model
            aiSettingsRepository.setSelectedModel(modelId)
            initializeModel()
        } else {
            Log.d(TAG, "Model file not found: ${modelFile.absolutePath} or ${alternateFile.absolutePath}")
            false // Need to download first
        }
    }

    // ==================== CACHE API ====================

    fun clearCache() {
        analysisCache.clear()
        fastPathCache.clear()
        cacheStats.reset()
        Log.d(TAG, "All caches cleared")
    }

    suspend fun isAvailable(): Boolean {
        val settings = aiSettingsRepository.settingsSnapshot()
        return settings.enabled
    }

    /**
     * Get current cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats = cacheStats.copy()

    /**
     * Reset cache statistics.
     */
    fun resetCacheStats() {
        cacheStats.reset()
    }

    // ==================== MODEL WARM-UP ====================

    /**
     * Warm up the LLM model with a simple query to reduce first-inference latency.
     * Call this after model initialization for better user experience.
     */
    suspend fun warmUpModel(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting model warm-up...")

        if (currentModel == AiModel.GEMINI_NANO && geminiNanoClient.isReady()) {
            // Gemini Nano doesn't need warm-up (always ready via AICore)
            Log.d(TAG, "Gemini Nano ready (no warm-up needed)")
            return@withContext true
        }

        if (!mediaPipeLlmClient.isReady()) {
            Log.w(TAG, "MediaPipe LLM not ready for warm-up")
            return@withContext false
        }

        try {
            val warmupPrompt = """<start_of_turn>user
What is an IMSI catcher? Reply in one sentence.
<end_of_turn>
<start_of_turn>model
"""
            val startTime = System.currentTimeMillis()
            val response = mediaPipeLlmClient.generateResponse(warmupPrompt)
            val duration = System.currentTimeMillis() - startTime

            if (response != null) {
                Log.i(TAG, "Model warm-up completed in ${duration}ms")
                return@withContext true
            } else {
                Log.w(TAG, "Model warm-up returned null response")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model warm-up failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Warm up the analysis cache with pre-populated common patterns.
     * Call this during app startup to improve cache hit rates for common device types.
     * This method:
     * 1. Pre-populates the fast-path cache with analysis for common benign devices
     * 2. Optionally warms up the LLM model
     */
    suspend fun warmUpCache(): Unit = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting cache warm-up...")
        val startTime = System.currentTimeMillis()

        try {
            // Pre-populate common patterns
            prepopulateCommonPatterns(fastPathCache)

            // Also warm up the model if needed
            warmUpModel()

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Cache warm-up completed in ${duration}ms. Fast-path cache size: ${fastPathCache.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Cache warm-up failed: ${e.message}")
        }
    }

    /**
     * Check if the model is warmed up and ready for fast inference.
     */
    fun isModelWarmedUp(): Boolean {
        return when {
            currentModel == AiModel.GEMINI_NANO -> geminiNanoClient.isReady()
            currentModel == AiModel.RULE_BASED -> true
            else -> mediaPipeLlmClient.isReady()
        }
    }

    /**
     * Get estimated inference time based on model and device capabilities.
     */
    fun getEstimatedInferenceTimeMs(): Long {
        return when (currentModel) {
            AiModel.GEMINI_NANO -> 500L  // NPU accelerated
            AiModel.RULE_BASED -> 10L    // No LLM, instant
            else -> if (deviceInfo.hasNpu || deviceInfo.availableRamMb > 8000) {
                2000L  // Good hardware
            } else {
                5000L  // Standard hardware
            }
        }
    }

    // ==================== CLEANUP ====================

    /**
     * Clean up all resources held by the DetectionAnalyzer.
     * Call this when the component is being destroyed to prevent memory leaks.
     */
    suspend fun cleanup() {
        scopeJob.cancel()
        cancelAnalysis()
        falsePositiveAnalyzer.clearLazyInitCallback()
        cacheMutex.withLock {
            analysisCache.clear()
        }
        fastPathCache.clear()
        cacheStats.reset()
        feedbackMutex.withLock {
            feedbackHistory.clear()
        }
        llmEngineManager.cleanup()
        isModelLoaded = false
        geminiNanoInitialized = false
        Log.d(TAG, "DetectionAnalyzer cleanup completed")
    }

    /**
     * Synchronous cleanup for non-suspend contexts (e.g., onDestroy).
     * Marks state as cleaned up immediately.
     */
    fun cleanupSync() {
        cancelAnalysis()
        falsePositiveAnalyzer.clearLazyInitCallback()
        analysisCache.clear()
        feedbackHistory.clear()
        llmEngineManager.cleanupSync()
        isModelLoaded = false
        geminiNanoInitialized = false
        Log.d(TAG, "DetectionAnalyzer sync cleanup completed")
    }

    // ==================== GEMINI NANO DIAGNOSTICS ====================

    /**
     * Get detailed diagnostics for Gemini Nano / AICore troubleshooting.
     * Returns comprehensive information about device support, AICore status, and model availability.
     */
    suspend fun getGeminiNanoDiagnostics(): GeminiNanoDiagnostics {
        return geminiNanoClient.getDiagnostics()
    }

    /**
     * Get the current Gemini Nano status.
     */
    fun getGeminiNanoStatus(): GeminiNanoStatus {
        return geminiNanoClient.getStatus()
    }

    /**
     * Get a user-friendly status message for Gemini Nano.
     */
    fun getGeminiNanoStatusMessage(): String {
        return geminiNanoClient.getStatusMessage()
    }

    /**
     * Force retry Gemini Nano model download and initialization.
     * Use this when the user wants to retry after a failed download/initialization.
     *
     * @param onProgress Callback for download progress updates (0-100)
     * @return true if Gemini Nano is ready for inference after this call
     */
    suspend fun forceRetryGeminiNano(onProgress: (Int) -> Unit = {}): Boolean {
        Log.i(TAG, "Force retry Gemini Nano requested")

        val result = geminiNanoClient.forceRetryDownload(onProgress)

        if (result) {
            // Update local state
            geminiNanoInitialized = true
            isModelLoaded = true
            currentModel = AiModel.GEMINI_NANO
            _modelStatus.value = AiModelStatus.Ready

            // Save the model selection
            aiSettingsRepository.setSelectedModel(AiModel.GEMINI_NANO.id)
            aiSettingsRepository.setEnabled(true)

            Log.i(TAG, "Gemini Nano force retry succeeded!")
        } else {
            Log.w(TAG, "Gemini Nano force retry failed")
        }

        return result
    }

    /**
     * Check if Gemini Nano is supported on this device.
     */
    fun isGeminiNanoSupported(): Boolean {
        return geminiNanoClient.isDeviceSupported()
    }

    /**
     * Check if AICore service is available on this device.
     */
    suspend fun isAiCoreAvailable(): Boolean {
        return geminiNanoClient.isAiCoreAvailable()
    }

    /**
     * Expose Gemini Nano model status flow for UI observation.
     */
    val geminiNanoModelStatus: StateFlow<GeminiNanoStatus> = geminiNanoClient.modelStatus

    /**
     * Expose Gemini Nano download progress flow for UI observation.
     */
    val geminiNanoDownloadProgress: StateFlow<Int> = geminiNanoClient.downloadProgress

    /**
     * Reset MediaPipe health tracking and crash counter.
     * Call this when the user wants to re-enable MediaPipe after crashes disabled it.
     */
    suspend fun resetMediaPipeHealth() {
        mediaPipeLlmClient.resetCrashTracking()
        mediaPipeLlmClient.resetGpuFailure()
        llmEngineManager.resetEngineHealth(LlmEngine.MEDIAPIPE)
        Log.i(TAG, "MediaPipe health and crash tracking reset")
    }
}
