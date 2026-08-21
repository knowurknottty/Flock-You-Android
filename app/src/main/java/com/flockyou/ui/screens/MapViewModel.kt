package com.flockyou.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.service.ScanningServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class MapUiState(
    val showHeatmap: Boolean = false,
    // Filter state (same semantics as MainUiState)
    val filterThreatLevel: ThreatLevel? = null,
    val filterDeviceTypes: Set<DeviceType> = emptySet(),
    val filterMatchAll: Boolean = true,
    val filterProtocols: Set<DetectionProtocol> = emptySet(),
    val filterTimeRange: TimeRange = TimeRange.ALL_TIME,
    val filterCustomStartTime: Long? = null,
    val filterCustomEndTime: Long? = null,
    val filterSignalStrength: Set<SignalStrength> = emptySet(),
    val filterActiveOnly: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: DetectionRepository,
    private val serviceConnection: ScanningServiceConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Cross-process scanning truth mirrored from the singleton IPC connection. */
    val isScanning: StateFlow<Boolean> = serviceConnection.isScanning

    init {
        // AppModule auto-binds the singleton connection; ask for an immediate authoritative sync.
        serviceConnection.requestState()
    }

    val hasAnyDetections: StateFlow<Boolean> = repository.totalDetectionCount
        .map { it > 0 }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * Filtered geolocated detections for the map.
     *
     * The repository already guarantees latitude/longitude are non-null for this stream. Keep the
     * full detection list out of MapUiState so database emissions do not copy a potentially large
     * history into filter/control state before being mapped again.
     */
    val detectionsWithLocation: StateFlow<List<Detection>> = combine(
        repository.detectionsWithLocation,
        _uiState
    ) { detections, state ->
        MapPresentationPolicy.filterDetections(
            detections = detections,
            state = state,
            nowMillis = System.currentTimeMillis()
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun toggleHeatmap() {
        _uiState.update { it.copy(showHeatmap = !it.showHeatmap) }
    }

    fun setThreatFilter(threatLevel: ThreatLevel?) {
        _uiState.update { it.copy(filterThreatLevel = threatLevel) }
    }

    fun toggleDeviceTypeFilter(deviceType: DeviceType) {
        _uiState.update { state ->
            if (deviceType in state.filterDeviceTypes) {
                state.copy(filterDeviceTypes = state.filterDeviceTypes - deviceType)
            } else {
                state.copy(filterDeviceTypes = state.filterDeviceTypes + deviceType)
            }
        }
    }

    fun setFilterMatchAll(matchAll: Boolean) {
        _uiState.update { it.copy(filterMatchAll = matchAll) }
    }

    fun toggleProtocolFilter(protocol: DetectionProtocol) {
        _uiState.update { state ->
            if (protocol in state.filterProtocols) {
                state.copy(filterProtocols = state.filterProtocols - protocol)
            } else {
                state.copy(filterProtocols = state.filterProtocols + protocol)
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _uiState.update {
            if (range != TimeRange.CUSTOM) {
                it.copy(
                    filterTimeRange = range,
                    filterCustomStartTime = null,
                    filterCustomEndTime = null
                )
            } else {
                it.copy(filterTimeRange = range)
            }
        }
    }

    fun setCustomTimeRange(start: Long, end: Long) {
        _uiState.update {
            it.copy(
                filterTimeRange = TimeRange.CUSTOM,
                filterCustomStartTime = start,
                filterCustomEndTime = end
            )
        }
    }

    fun toggleSignalStrengthFilter(strength: SignalStrength) {
        _uiState.update { state ->
            if (strength in state.filterSignalStrength) {
                state.copy(filterSignalStrength = state.filterSignalStrength - strength)
            } else {
                state.copy(filterSignalStrength = state.filterSignalStrength + strength)
            }
        }
    }

    fun setActiveOnly(activeOnly: Boolean) {
        _uiState.update { it.copy(filterActiveOnly = activeOnly) }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                filterThreatLevel = null,
                filterDeviceTypes = emptySet(),
                filterProtocols = emptySet(),
                filterTimeRange = TimeRange.ALL_TIME,
                filterCustomStartTime = null,
                filterCustomEndTime = null,
                filterSignalStrength = emptySet(),
                filterActiveOnly = false
            )
        }
    }

    fun getActiveFilterCount(): Int {
        val state = _uiState.value
        var count = 0
        if (state.filterThreatLevel != null) count++
        if (state.filterDeviceTypes.isNotEmpty()) count += state.filterDeviceTypes.size
        if (state.filterProtocols.isNotEmpty()) count += state.filterProtocols.size
        if (state.filterTimeRange != TimeRange.ALL_TIME) count++
        if (state.filterSignalStrength.isNotEmpty()) count += state.filterSignalStrength.size
        if (state.filterActiveOnly) count++
        return count
    }
}
