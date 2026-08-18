from pathlib import Path

path = Path("app/src/main/java/com/flockyou/ui/screens/MainViewModel.kt")
text = path.read_text()

old_collector = '''        // Compute related detection counts when detections change\n        viewModelScope.launch {\n            repository.allDetections.collect { detections ->\n                val counts = computeRelatedCounts(detections)\n                _uiState.update { it.copy(relatedDetectionCounts = counts) }\n            }\n        }\n'''
new_collector = '''        // Compute related detection counts when detections change using the presentation-layer\n        // indexed counter. This preserves the previous semantics without an O(n^2) nested scan\n        // on every Room emission.\n        viewModelScope.launch {\n            repository.allDetections.collect { detections ->\n                val counts = RelatedDetectionCounter.compute(detections)\n                _uiState.update { it.copy(relatedDetectionCounts = counts) }\n            }\n        }\n'''

if text.count(old_collector) != 1:
    raise SystemExit(f"collector: expected exactly 1 match, found {text.count(old_collector)}")
text = text.replace(old_collector, new_collector, 1)

start_marker = '''    // --- Related detection count computation ---\n\n    private fun computeRelatedCounts(detections: List<com.flockyou.data.model.Detection>): Map<String, Int> {'''
end_marker = '''    // --- Enriched data access ---\n'''
start = text.find(start_marker)
end = text.find(end_marker, start)
if start == -1 or end == -1 or end <= start:
    raise SystemExit("related-count method block markers not found")
text = text[:start] + end_marker + text[end + len(end_marker):]

path.write_text(text)
