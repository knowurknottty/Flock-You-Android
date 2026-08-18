# MAXSTATS Android Performance Evidence Gate

This document defines the evidence required before an optimization lane may claim measured improvement.

## Core rule

A source-level observation such as "five polling loops were removed" may be claimed directly from the diff. A numerical claim about CPU, memory, GC, battery, thermal state, wakeups, launch time, frame time, or jank requires a before/after measurement using the same scenario, build flavor, device class, and collection method.

Emulator evidence is valuable for repeatability and UI regressions. It is **not** authoritative for real battery or thermal behavior. Final constrained-device battery/thermal claims require a physical Android device.

## Reference build and scenarios

Record for every evidence bundle:

- repository SHA;
- build flavor and build type;
- Android version/API;
- emulator profile or physical device model;
- app data state: fresh install, migrated database, or seeded historical database;
- privacy mode and battery-adaptive mode;
- enabled scanners/subsystems;
- test duration and environmental conditions relevant to radio/location behavior.

### P0 — Cold launch to steady scan

1. Force-stop application.
2. Capture baseline memory/process state.
3. Launch app.
4. Start/restore scanning through the normal user path.
5. Continue until all enabled subsystems reach steady state.
6. Capture startup trace, service transitions, memory, GC, worker activity, and errors.

Look for settings races, duplicate collector startup, unnecessary graph construction, foreground-service churn, and initial scan-policy escalation.

### P1 — BLE-dense burst

Exercise a deterministic BLE-heavy test source or controlled emulator/test-mode equivalent without changing production scan semantics.

Measure:

- result-processing throughput/backpressure;
- coroutine/thread count;
- allocation/GC behavior;
- dropped/coalesced work where observable;
- UI responsiveness while detections arrive;
- watchdog/recovery behavior.

### P2 — Wi-Fi cadence

Run long enough to observe multiple scheduled Wi-Fi scans under the chosen adaptive mode.

Verify:

- requested cadence matches policy;
- no duplicate successful-scan accounting;
- no hidden fast loop after failure/restart;
- wakeup/background work is bounded.

### P3 — Background, screen lock, and resume

1. Start scanning.
2. Background the UI while service remains in its intended state.
3. Lock/unlock where the test environment permits.
4. Resume the UI.
5. Repeat process death/recreation separately where feasible.

Verify collector cancellation/restart, IPC subscriptions, worker behavior, stale state, duplicate notifications, and unexpected scanner restarts.

### P4 — History and map stress

Use a realistic large detection history containing geolocated and non-geolocated records.

Exercise:

- history scrolling/filtering;
- map open/close;
- zoom/pan;
- marker/cluster transitions;
- detection detail open/close;
- filter changes;
- export UI if present.

Measure frame timing/jank, recomposition or render evidence available to the environment, allocation pressure, query latency, and map-overlay rebuild behavior.

### P5 — Persistence/query stress

Against an encrypted persistent database:

- cold DB open;
- recent detections query;
- full-history or bounded-history query used by UI;
- protocol/threat filtering;
- geolocated detection query;
- insert/update hot path;
- retention/downsampling;
- export snapshot generation.

Do not disable SQLCipher or weaken encryption to manufacture benchmark gains.

### P6 — Ephemeral mode

Repeat representative detection and map/history flows in ephemeral mode. Verify that no historical detection database writes occur and that process/service restart clears ephemeral detections as designed.

## Evidence tools

Use the strongest tools available in the execution environment. Recommended Android evidence sources include:

### Perfetto / System Trace

Use for scheduler, CPU slices, binder activity, wakeups, main-thread stalls, service/work transitions, and system-level timing. Keep scenarios bounded and label before/after traces unambiguously.

### `dumpsys meminfo`

Capture comparable snapshots at minimum:

- before launch;
- steady scan;
- after stress scenario;
- after returning to idle.

Report PSS/RSS/heap values with context rather than cherry-picking one number.

### Frame/jank metrics

Use `dumpsys gfxinfo`, FrameMetrics, Compose/JankStats or equivalent supported instrumentation for UI comparisons. Report distributions/counts where possible rather than one best frame.

### Simpleperf

Use when CPU attribution requires sampled stack evidence beyond Perfetto. Do not infer method-level CPU cost from source size alone.

### Logcat and WorkManager evidence

Capture service lifecycle, scan policy transitions, watchdog recovery, WorkManager scheduling, errors, and unexpected retries. Debug logging itself can perturb results; use consistent logging configuration across comparisons.

### Database timing

Use controlled instrumentation/unit benchmarks around repository/DAO operations. Record dataset size and encryption state. Never compare encrypted before to unencrypted after.

### Physical-device battery and thermal evidence

For final Moto/constrained-device validation:

- same physical device;
- same build flavor and settings;
- comparable starting charge and charging state;
- fixed scenario duration;
- screen state documented;
- radios/location documented;
- battery percentage/current evidence available to the device;
- temperature/thermal status captured consistently;
- repeat enough runs to avoid declaring noise a win.

Do not translate one short run into a daily battery-life claim.

## Regression admission rules

A change fails the performance gate if it:

- improves one metric by disabling or materially degrading expected detection without an explicit product decision;
- causes scanner policy escalation after recovery;
- introduces an unbounded collection, worker, coroutine, thread, queue, cache, or polling loop;
- increases crashes/ANRs or breaks lifecycle behavior;
- weakens encryption/privacy;
- increases false capability claims;
- changes persistent/ephemeral semantics unexpectedly;
- produces a numerical claim without comparable before/after evidence.

## Evidence bundle in each PR

Every lane PR should include:

```text
PERFORMANCE EVIDENCE
Branch / SHA:
Build flavor:
Environment:
Scenario(s):
Baseline SHA:
Baseline measurements:
Candidate measurements:
Observed delta:
What source change plausibly explains the delta:
Correctness/regression tests:
Known measurement limitations:
Physical-device follow-up required: yes/no
```

If a lane has only source-level/CI evidence and no valid performance runtime, state exactly that. "Not measured yet" is preferred to fabricated precision.
