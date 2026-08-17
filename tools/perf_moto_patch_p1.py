#!/usr/bin/env python3
"""P1 constrained-device optimization: make the AI dependency graph genuinely lazy."""
from pathlib import Path


def exact(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:60]!r}")


# Application process: injecting DetectionAnalyzer eagerly constructs its large graph
# (Gemini/MediaPipe clients, LLM manager, analyzers, caches) before AI settings are read.
path = "app/src/main/java/com/flockyou/FlockYouApplication.kt"
exact(path, "import javax.inject.Inject\n", "import dagger.Lazy\nimport javax.inject.Inject\n")
exact(
    path,
    """    @Inject
    lateinit var detectionAnalyzer: DetectionAnalyzer""",
    """    @Inject
    lateinit var detectionAnalyzer: Lazy<DetectionAnalyzer>""",
)
exact(
    path,
    """            detectionAnalyzer.initializeModel()""",
    """            detectionAnalyzer.get().initializeModel()""",
)

# Home ViewModel: preserve all existing call sites through a lazy accessor, so merely
# opening the normal UI does not instantiate the AI graph unless an AI path is used.
path = "app/src/main/java/com/flockyou/ui/screens/MainViewModel.kt"
exact(path, "import javax.inject.Inject\n", "import dagger.Lazy\nimport javax.inject.Inject\n")
exact(
    path,
    """    internal val detectionAnalyzer: com.flockyou.ai.DetectionAnalyzer,
    internal val crossDomainAnalyzer:""",
    """    internal val detectionAnalyzerLazy: Lazy<com.flockyou.ai.DetectionAnalyzer>,
    internal val crossDomainAnalyzer:""",
)
exact(
    path,
    """    internal val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
""",
    """    internal val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Keep existing extension/call sites source-compatible while deferring the
    // heavyweight AI graph until an AI feature actually asks for it.
    internal val detectionAnalyzer: com.flockyou.ai.DetectionAnalyzer
        get() = detectionAnalyzerLazy.get()
""",
)

print("P1 lazy-AI optimization applied")
