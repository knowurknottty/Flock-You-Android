from pathlib import Path

vm_path = Path("app/src/main/java/com/flockyou/ui/screens/MainViewModel.kt")
vm = vm_path.read_text()

old_vm_anchor = '''    // Track detection IDs that have been prioritized for enrichment\n    internal val _prioritizedEnrichmentIds = MutableStateFlow<Set<String>>(emptySet())\n    val prioritizedEnrichmentIds: StateFlow<Set<String>> = _prioritizedEnrichmentIds.asStateFlow()\n\n    init {\n'''
new_vm_anchor = '''    // Track detection IDs that have been prioritized for enrichment\n    internal val _prioritizedEnrichmentIds = MutableStateFlow<Set<String>>(emptySet())\n    val prioritizedEnrichmentIds: StateFlow<Set<String>> = _prioritizedEnrichmentIds.asStateFlow()\n\n    // Narrow history query projection: scanner/RF/GNSS/Flipper telemetry updates no longer\n    // re-run the complete detection-history filter/sort path.\n    private val historyQuery: Flow<DetectionHistoryQuery> = _uiState\n        .map(DetectionHistoryQuery::from)\n        .distinctUntilChanged()\n\n    val filteredHistoryDetections: StateFlow<List<Detection>> = combine(\n        repository.allDetections,\n        historyQuery\n    ) { detections, query ->\n        DetectionHistoryPresentationPolicy.filterAndSort(\n            detections = detections,\n            query = query,\n            nowMillis = System.currentTimeMillis()\n        )\n    }\n        .distinctUntilChanged()\n        .stateIn(\n            scope = viewModelScope,\n            started = SharingStarted.WhileSubscribed(5_000),\n            initialValue = emptyList()\n        )\n\n    init {\n'''
if vm.count(old_vm_anchor) != 1:
    raise SystemExit(f"MainViewModel anchor: expected 1 match, found {vm.count(old_vm_anchor)}")
vm = vm.replace(old_vm_anchor, new_vm_anchor, 1)
vm_path.write_text(vm)

screen_path = Path("app/src/main/java/com/flockyou/ui/screens/MainScreen.kt")
screen = screen_path.read_text()

old_screen_state = '''    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n    val prioritizedEnrichmentIds by viewModel.prioritizedEnrichmentIds.collectAsStateWithLifecycle()\n'''
new_screen_state = '''    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n    val filteredHistoryDetections by viewModel.filteredHistoryDetections.collectAsStateWithLifecycle()\n    val prioritizedEnrichmentIds by viewModel.prioritizedEnrichmentIds.collectAsStateWithLifecycle()\n'''
if screen.count(old_screen_state) != 1:
    raise SystemExit(f"MainScreen state anchor: expected 1 match, found {screen.count(old_screen_state)}")
screen = screen.replace(old_screen_state, new_screen_state, 1)

old_hot_call = '''                        // History tab - Detection list with filters\n                        val filteredDetections = viewModel.getFilteredDetections()\n'''
new_hot_call = '''                        // History tab - precomputed narrow projection. Unrelated service telemetry\n                        // can recompose MainScreen without re-filtering and re-sorting the full history.\n                        val filteredDetections = filteredHistoryDetections\n'''
if screen.count(old_hot_call) != 1:
    raise SystemExit(f"MainScreen history hot call: expected 1 match, found {screen.count(old_hot_call)}")
screen = screen.replace(old_hot_call, new_hot_call, 1)
screen_path.write_text(screen)
