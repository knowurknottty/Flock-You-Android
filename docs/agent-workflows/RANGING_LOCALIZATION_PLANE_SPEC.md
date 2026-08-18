# Inversion Labs Ranging & Localization Plane — Design Specification

**Status:** APPROVED ARCHITECTURE / DESIGN AUTHORITY  
**Repository:** `knowurknottty/Flock-You-Android`  
**Branch:** `design/ranging-localization-plane-r1`  
**Date:** 2026-08-18  
**Purpose:** Replace toy-grade RSSI distance guesses with an evidence-backed, uncertainty-aware ranging and localization subsystem that works on constrained Android hardware and can later serve both the operator map and a sanitized game-world backend.

---

## 0. Mission

The current application contains useful radio observations but still includes distance-oriented semantics that are too coarse for an operational field instrument. A universal mapping from RSSI to meters, or from qualitative signal labels to hard physical ranges, is not acceptable as operational truth.

The mission of this plane is:

> Convert raw radio observations into the strongest distance/proximity/localization statement the available evidence can actually support, while preserving provenance, calibration state, uncertainty, hardware limitations, and failure modes.

The subsystem must be useful on older commodity Android devices and become more precise opportunistically when stronger platform/hardware ranging methods are available.

The central rule:

> **If absolute meters are not defensible, return a weaker truthful result instead of inventing precision.**

---

## 1. Non-goals

This design does not:

- promise centimeter-level ranging from passive RSSI;
- assume every BLE/Wi-Fi transmitter reports calibrated TX power;
- assume all Android receivers have identical antenna/front-end behavior;
- assume free-space propagation indoors;
- treat motion/orientation sensors as direct ranging sensors;
- require Android 16 / API 36 for baseline operation;
- require UWB, Wi-Fi RTT, BLE Channel Sounding, or peer cooperation;
- make a rotating MAC/BSSID a durable physical identity;
- let game state, rarity, faction state, sponsorship, or player voting change measurement truth.

---

## 2. Current anti-toy correction

The existing codebase includes two concepts that must be deprecated as physical-distance authorities:

1. signal-strength descriptions that imply fixed distance bands such as “within ~10m” or “within ~25m”;
2. a universal `rssiToDistance()` helper that assumes one reference RSSI / one propagation model and returns coarse meter buckets.

Those may remain temporarily for compatibility only if their wording is removed from operational/UI authority and all new ranging consumers use this plane.

---

## 3. Architecture

```text
ANDROID / EXTERNAL RADIO OBSERVATIONS
        ↓
RAW RADIO OBSERVATION
        ↓
QUALITY / SANITY FILTER
        ↓
TEMPORAL SAMPLE WINDOW
        ↓
ROBUST RSSI CONDITIONING
        ↓
CALIBRATION RESOLUTION
        ↓
RANGING BACKEND SELECTION
        ↓
RANGE DISTRIBUTION / PROXIMITY TREND
        ↓
MULTI-POSITION LOCALIZATION (when applicable)
        ↓
EVIDENCE / CLAIM ADMISSION
        ├── OPERATOR UI / MAP
        └── SANITIZED WORLD-GRAPH / GAME PROJECTION
```

The renderer never owns range truth.

The detector never fabricates range merely because it has an RSSI value.

---

## 4. Core evidence types

### 4.1 `RadioObservation`

Conceptual fields:

```kotlin
data class RadioObservation(
    val observationId: String,
    val source: RadioSource,
    val stableCandidateKey: String?,
    val rawIdentifier: String?,
    val rssiDbm: Int?,
    val txPowerDbm: Int?,
    val txPowerSource: TxPowerSource,
    val frequencyMhz: Int?,
    val primaryPhy: String?,
    val secondaryPhy: String?,
    val advertisingSid: Int?,
    val packetFingerprint: String?,
    val monotonicTimestampNanos: Long?,
    val wallClockTimestampMillis: Long,
    val receiverProfileId: String,
    val phonePosition: GeoObservation?,
    val motionState: MotionState?,
    val orientationState: OrientationState?,
    val qualityFlags: Set<ObservationQualityFlag>
)
```

Not every protocol populates every field.

Unavailable is a valid state.

### 4.2 `GeoObservation`

```kotlin
data class GeoObservation(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float?,
    val elapsedRealtimeNanos: Long?,
    val source: GeoSource
)
```

Phone-location uncertainty must never be confused with target-range uncertainty.

### 4.3 `RangeEstimate`

```kotlin
data class RangeEstimate(
    val subjectKey: String,
    val estimateMeters: Double?,
    val credibleLowMeters: Double?,
    val credibleHighMeters: Double?,
    val method: RangingMethod,
    val confidence: RangingConfidence,
    val trend: RangeTrend,
    val rawRssiDbm: Int?,
    val filteredRssiDbm: Double?,
    val rssiMadDb: Double?,
    val sampleCount: Int,
    val sampleWindowMillis: Long,
    val txPowerDbm: Int?,
    val txPowerSource: TxPowerSource,
    val referenceRssi1mDbm: Double?,
    val pathLossExponent: Double?,
    val receiverCalibrationId: String?,
    val transmitterCalibrationId: String?,
    val observationAgeMillis: Long,
    val qualityFlags: Set<RangingQualityFlag>
)
```

`estimateMeters` is intentionally nullable.

### 4.4 `LocalizedEmitterEstimate`

```kotlin
data class LocalizedEmitterEstimate(
    val subjectKey: String,
    val centroid: GeoPoint?,
    val confidenceRegion: GeoRegion?,
    val method: LocalizationMethod,
    val observationCount: Int,
    val distinctVantageCount: Int,
    val confidence: RangingConfidence,
    val fitResidual: Double?,
    val qualityFlags: Set<LocalizationQualityFlag>
)
```

---

## 5. Raw BLE observation preservation

The BLE ingestion path should preserve, when platform/API provides them:

- RSSI;
- `ScanResult.txPower`;
- scan-record TX power;
- primary PHY;
- secondary PHY;
- advertising SID;
- legacy/extended-advertising status;
- connectable state;
- data-status/truncation information;
- periodic interval where meaningful;
- controller/elapsed timestamp;
- service UUIDs;
- manufacturer/service payload fingerprint;
- packet cadence;
- receiver device/build profile;
- current phone location + real location accuracy;
- optional motion/orientation state.

Important fields may be absent on older APIs/hardware; absence changes confidence rather than breaking scanning.

---

## 6. RSSI conditioning pipeline

The baseline passive ranging backend should use a bounded temporal window.

Recommended sequence:

```text
raw RSSI
  → sentinel/range validation
  → Hampel/MAD-style impulsive outlier rejection
  → recency weighting
  → adaptive state-space filtering
  → filtered RSSI + robust spread estimate
```

The system should preserve raw samples required for reproducibility while exposing derived filtered state separately.

### Requirements

- bounded per-subject sample windows;
- no unbounded history in the hot path;
- no coroutine-per-advertisement fanout;
- old observations age out deterministically;
- filter responsiveness increases during coherent motion/signal change;
- smoothing increases when receiver/source appears stationary;
- filter output always carries sample count and dispersion.

A single RSSI sample may support qualitative proximity but normally should not produce a high-confidence physical distance.

---

## 7. Calibration hierarchy

Calibration is resolved from strongest to weakest source:

```text
C0 exact emitter + receiver calibration
C1 known emitter family + receiver calibration
C2 protocol-defined calibrated reference power + receiver calibration
C3 advertised TX power + receiver calibration
C4 receiver-only profile + environmental prior
C5 generic fallback prior
C6 no defensible absolute calibration
```

Confidence and interval width must degrade as the hierarchy falls.

At `C6`, absolute range should normally become null and the system returns proximity/trend only.

### Receiver profile

A receiver profile should preserve at least:

```yaml
profile_id:
device_manufacturer:
device_model:
android_build_fingerprint:
radio_or_chipset_hint:
calibration_version:
reference_offsets:
variance_model:
orientation_notes:
environment_profiles:
created_at:
validated_at:
```

Profiles are local product calibration data, not marketing labels.

---

## 8. Passive path-loss model

The baseline physical model may use the log-distance form:

```text
RSSI(d) = A - 10 n log10(d)
```

Where:

- `A` = calibrated/reference RSSI at 1 meter;
- `n` = path-loss exponent;
- `d` = distance in meters.

Neither `A` nor `n` should be universally hard-coded as operational truth.

When uncertainty parameters exist, the output must be a distribution/interval, not only a scalar.

---

## 9. Trend estimation

Relative trend is often more reliable than absolute meters.

`RangeTrend`:

```text
APPROACHING
RECEDING
STABLE
OSCILLATING
UNKNOWN
```

Trend should use time-ordered filtered signal and, where available, receiver motion/orientation context.

Examples:

- coherent RSSI strengthening during translation supports `APPROACHING`;
- large RSSI variation during rotation without meaningful translation should increase orientation/body-shadow flags rather than imply source movement;
- insufficient window length returns `UNKNOWN`.

---

## 10. Motion and orientation

Accelerometer/rotation-vector information is contextual evidence only.

It may help classify:

- receiver stationary vs translating;
- receiver rotating;
- likely body-shadow/orientation effects;
- whether a multi-position sample actually came from a distinct physical vantage point.

It may not directly convert into distance.

No motion sensor available → ranging still works with wider uncertainty.

---

## 11. Multi-position localization

For a source judged sufficiently stationary, multiple geographically separated observations may be combined.

Each observation contributes:

```text
phone position distribution
× range distribution
× timestamp
× subject identity confidence
```

The localizer solves for the source location that best explains the observation set using a robust nonlinear method.

### Admission requirements

- minimum number of distinct vantage points;
- minimum spatial separation between vantage points;
- phone position accuracy below a configurable maximum;
- source stationarity evidence;
- identity confidence high enough that observations likely refer to the same physical emitter;
- outlier-resistant fitting;
- confidence region, never merely one marker;
- residual/fit quality surfaced.

If stationarity fails, multi-position localization returns unavailable rather than forcing a fit.

---

## 12. Ranging backend interface

Conceptual interface:

```kotlin
interface RangingBackend {
    val method: RangingMethod
    fun supports(context: RangingContext): Boolean
    suspend fun estimate(context: RangingContext): RangeEstimate
}
```

Recommended backends:

```text
PassiveBleRssiBackend          baseline / API 26+
PassiveWifiRssiBackend         future
AndroidBleRssiBackend          future platform adapter
BluetoothChannelSounding       supported peer/hardware only
WifiRttBackend                 responder-capable APs only
UwbBackend                     supported cooperative peers only
ExternalRadioBackend           Flipper / SDR / calibrated hardware
```

A backend selector chooses the strongest supported truthful method.

No new precision backend may silently replace a stronger existing estimate with a weaker one.

---

## 13. API / device strategy

Current project minimum remains compatible with older Android hardware.

### Baseline

- passive BLE RSSI ranging works without API-36 ranging framework;
- baseline design must remain useful on the older Moto-class test device;
- no new mandatory radio hardware dependency.

### Future adapters

When compile SDK and runtime support are deliberately raised, add capability-gated adapters for newer platform ranging APIs.

Those adapters must preserve the same `RangeEstimate` contract so UI/world-graph consumers do not care which backend produced the estimate.

---

## 14. Map semantics

The map must render different uncertainty types differently.

### Phone position

Real Android location accuracy → phone-location uncertainty circle.

### One-vantage range

`RangeEstimate` → annulus or probabilistic radial region around the observation point.

### Multi-position localization

`LocalizedEmitterEstimate` → probability/confidence region.

### Known infrastructure

A persistent known/verified infrastructure coordinate → entity marker with source/freshness/provenance state.

Never use target RSSI category as a GPS-accuracy circle.

---

## 15. World-graph integration

Ranging/localization does not create durable world truth by itself.

It contributes evidence to entity state:

```text
RAW OBSERVATION
  → RANGE / LOCATION EVIDENCE
  → IDENTITY / ENTITY RESOLUTION
  → CLAIM EVALUATION
  → WORLD ENTITY UPDATE
```

A world entity should preserve:

- estimate method;
- location uncertainty;
- supporting observations;
- calibration profile/version;
- age;
- contradictions;
- last field verification.

---

## 16. Game boundary

The future game may consume a sanitized projection such as:

```yaml
public_world_entity_id:
coarse_or_public_geometry:
range_or_search_region:
verification_tier:
freshness:
encounter_archetype:
```

The game should not receive raw personal MAC/BSSID identifiers or private precise player trails by default.

Game mechanics cannot change ranging confidence or world evidence state.

---

## 17. UI contract

### Normal mode

Examples:

```text
~4 m away
Likely range: 2–7 m
Medium confidence · getting closer
```

When absolute distance is not defensible:

```text
Nearby — absolute range uncertain
Signal strengthening
```

### Research mode

May expose:

```text
raw RSSI
filtered RSSI
MAD / variance
sample count
sample window
TX power + source
PHY
calibration profile
reference RSSI
path-loss exponent
range interval
quality flags
observation age
backend/method
```

UI precision must match evidence precision.

---

## 18. Quality flags

At minimum support flags conceptually equivalent to:

```text
INSUFFICIENT_SAMPLES
TX_POWER_UNAVAILABLE
GENERIC_CALIBRATION
UNCALIBRATED_RECEIVER
HIGH_RSSI_VARIANCE
MULTIPATH_POSSIBLE
NLOS_POSSIBLE
BODY_SHADOW_POSSIBLE
RECEIVER_MOVING
SOURCE_MOVING
SOURCE_STATIONARITY_UNCERTAIN
STALE_OBSERVATION
LOCATION_ACCURACY_POOR
IDENTITY_UNCERTAIN
OUTLIERS_REJECTED
BACKEND_DEGRADED
```

Flags are evidence metadata, not warnings to scare normal users.

---

## 19. Calibration Lab

Advanced/Research mode should eventually include a controlled calibration workflow.

Suggested measured distances:

```text
0.5 m
1 m
2 m
4 m
8 m
16 m when practical
```

Collect across:

- multiple receiver orientations;
- multiple transmitter orientations;
- LOS;
- body obstruction;
- common indoor reflective setting;
- outdoor setting where practical.

Fit receiver offset/reference RSSI, variance model, and path-loss parameters.

Calibration artifacts must be versioned and invalidatable after major OS/device/radio changes.

---

## 20. Bench validation

Required metrics:

```text
median absolute error
RMSE
P50 interval coverage
P80 interval coverage
P95 interval coverage
false-confidence rate
convergence time
trend correctness
failure-to-estimate rate
```

The primary anti-theater metric is calibration of uncertainty:

> When an 80% interval is claimed, controlled ground truth should fall inside that interval approximately 80% of the time for the validated regime.

If a regime cannot satisfy that requirement, narrow the claim or downgrade to qualitative proximity/trend.

---

## 21. Density / resource constraints

The passive engine must remain bounded under dense radio conditions.

Requirements:

- bounded subject registry;
- bounded samples per subject;
- deterministic aging/eviction;
- coalesced downstream updates;
- no per-packet expensive nonlinear localization;
- localization triggered only when useful criteria are met;
- no new mandatory high-frequency sensor loops;
- no expensive model load for ordinary ranging;
- constrained-device profiling before numerical performance claims.

---

## 22. Persistence policy

Raw/high-rate sample retention should be configurable and privacy-conscious.

Suggested separation:

```text
HOT WINDOW        RAM-only bounded samples for filtering
EVIDENCE SUMMARY  persisted only when needed for admitted detection/entity evidence
CALIBRATION       versioned local profile
WORLD GRAPH       durable entity-level spatial evidence
```

Ephemeral mode must preserve its RAM-only semantics.

---

## 23. Failure behavior

The engine must degrade explicitly under:

- missing TX power;
- insufficient samples;
- poor location accuracy;
- unavailable motion sensors;
- rapidly moving source;
- randomized identity;
- app backgrounding;
- Bluetooth toggled;
- process restart;
- device low memory;
- stale calibration;
- calibration profile mismatch;
- corrupted calibration store.

No failure should silently convert a low-confidence estimate into a confident generic number.

---

## 24. Security / privacy

Ranging is sensitive because it can increase physical-location inference.

Requirements:

- no public persistence of personal-device precise localization by default;
- no game projection of sensitive personal trackers by default;
- world-graph visibility policy applies to localized entities;
- exports require explicit privacy choices;
- research-mode raw identifiers remain subject to existing privacy policy;
- logs should not casually contain precise private coordinates + persistent identifiers.

---

## 25. Test strategy

### Unit

- outlier rejection;
- filter stability/responsiveness;
- calibration hierarchy;
- range interval construction;
- nullable-distance downgrade;
- trend classification;
- backend selection;
- bounded sample registry;
- localization geometry on synthetic ground truth;
- quality-flag propagation.

### Integration

- BLE `ScanResult` → `RadioObservation`;
- range estimate → evidence pipeline;
- range estimate → map projection;
- calibration profile load/invalidation;
- process restart with clean recovery.

### Device

- real BLE scans on constrained Moto-class device;
- API-level capability differences;
- orientation/motion behavior;
- background/resume behavior;
- resource characterization.

### Bench

Controlled ground-truth campaign from Section 20.

---

## 26. Admission ladder

```text
R0 CONCEPT
R1 SOURCE IMPLEMENTED
R2 UNIT VERIFIED
R3 INTEGRATION VERIFIED
R4 DEVICE VERIFIED
R5 CALIBRATED / BENCH VERIFIED
R6 ADVERSARIAL VERIFIED
R7 OPERATIONALLY ADMITTED
```

A UI may expose a result below R7 only with wording matching the current evidence tier.

---

## 27. First implementation slice

Phase 1 should deliberately be narrow and valuable:

1. preserve richer BLE observation metadata;
2. introduce `RadioObservation` and `RangeEstimate` contracts;
3. implement bounded robust RSSI conditioning;
4. implement calibration hierarchy;
5. implement passive BLE ranging with nullable absolute distance;
6. implement trend classification;
7. remove distance-bearing authority from legacy `SignalStrength` descriptions and `rssiToDistance()` consumers;
8. expose research diagnostics without fake precision;
9. add controlled unit/synthetic tests;
10. prepare bench capture/export format.

Multi-position localization follows after Phase 1 range evidence is stable.

---

## 28. Dependency / ownership boundaries

This design intersects several MAXSTATS lanes but should be implemented only after deliberate convergence/freeze.

Expected ownership split:

- scanner ingestion/service: runtime/scanner owner;
- ranging core/model: dedicated ranging lane;
- persistence/calibration store: data owner;
- map rendering: UI owner;
- world-graph/entity integration: mapping/world-graph owner;
- game projection: future backend boundary.

Do not silently solve cross-lane dependencies by editing foreign domains.

---

## 29. Acceptance criteria

The first ranging plane implementation is acceptable when:

- no operational UI path equates a qualitative signal category with fixed meters;
- no operational path uses the legacy universal `rssiToDistance()` as authoritative range;
- passive BLE estimates carry sample count, method, calibration source, uncertainty, and quality flags;
- absolute distance can be unavailable;
- trend works independently of absolute-distance admission;
- BLE metadata needed by the design is preserved where platform supplies it;
- hot-path storage is bounded;
- relevant tests pass on all supported build flavors;
- physical-device behavior is demonstrated;
- no numerical accuracy claim is made before controlled bench evidence;
- map semantics distinguish phone accuracy, range annulus, and inferred target region;
- game/world-graph consumers receive sanitized evidence rather than raw UI guesses.

---

## 30. Final doctrine

> **Distance is an estimate, not a decoration.**

> **Uncertainty is part of the measurement.**

> **A weaker truthful result is better than a stronger fictional one.**

> **The phone is a moving sensor platform, not a magic rangefinder.**

> **The same evidence discipline that makes the field tool credible is what makes the future game-world backend trustworthy.**
