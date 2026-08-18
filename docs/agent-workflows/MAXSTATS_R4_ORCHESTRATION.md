# MAXSTATS R4 — Three-Agent Optimization Orchestration

## Authority

Repository: `knowurknottty/Flock-You-Android`

Pinned starting point for all three lanes:

`main@9dcde5dc83164a2a625eaec2bca451b46fb9c736`

If `main` moves after a lane begins, do not silently absorb unrelated changes. Complete the lane against the pinned base, document the delta, then rebase only at the convergence gate.

## Mission

Maximize Flock-You's engineering quality, constrained-device efficiency, usability, persistence/query quality, security truthfulness, and test evidence **without gutting detection capability or weakening the project's feasibility model**.

This round optimizes the Android app. It does not build the planned game layer. Game-oriented architectural possibilities may be recorded as deferred notes only when they expose a useful backend boundary; they must not distort the current counter-surveillance product.

## Non-negotiable engineering rules

1. Current repository source is authoritative. Never implement from README claims or conversational memory when source disagrees.
2. No mocks, stubs, fake integrations, placeholder implementations, pseudocode-as-product, or tests that merely assert the implementation constant.
3. Preserve the distinction between observed, inferred, heuristic, and unavailable capability. `FeasibilityLevel` truthfulness is a product invariant.
4. Do not reduce security/privacy guarantees merely to improve benchmarks.
5. Do not add cloud/network dependencies for core scanning or analysis. Any optional network behavior must remain explicit and isolated.
6. No new high-frequency polling loop, unbounded coroutine fan-out, unbounded in-memory collection, or uncontrolled background worker.
7. PERFORMANCE is explicit opt-in. Recovery paths may not silently escalate radio/CPU policy.
8. Preserve persistent-mode versus ephemeral-mode semantics. Ephemeral data stays RAM-only.
9. Passive defensive scanning is the default domain. Do not expand active probing or interference capabilities as part of this optimization campaign.
10. A performance claim requires before/after evidence under the same scenario. Source inspection may justify a claim of "less work" but never an invented CPU, RAM, battery, thermal, or frame-time percentage.

## Five-pass recursion protocol — before code

Every agent MUST complete all five passes internally before modifying production source. The final PR must contain a concise evidence summary of what each pass changed in the plan.

### Pass 1 — Source truth

Inspect the pinned branch and relevant call graph. Identify the real implementation, existing tests, prior optimizations, and current invariants. Reject stale assumptions.

### Pass 2 — Adversarial hotspot search

Search for hidden cost and correctness failure modes: cold-start races, retry/restart escalation, redundant Flow collection, coroutine churn, serialization, binder/broadcast churn, allocations, recomposition blast radius, DB query amplification, transaction overhead, leaks, wakeups, stale caches, false-positive amplification, and accidental capability lies.

### Pass 3 — Cross-system interaction

Reason through lifecycle, concurrency, battery mode, low-RAM behavior, process death/restart, privacy mode, encryption, persistence, Android permission/API differences, and other agents' ownership boundaries. A local optimization that creates a cross-domain regression is a failure.

### Pass 4 — Verification design

Before implementation, specify the failing regression test or measurable baseline, the exact commands/scenarios that will validate the change, and the failure condition that should prevent merge.

### Pass 5 — Minimal synthesis

Choose the smallest coherent change set that eliminates the root cause. Remove speculative refactors, duplicated ownership, and unrelated cleanup. Define explicit non-goals.

## Five-pass recursion protocol — after code

Before declaring the lane complete, recursively audit the implementation five more times.

1. **Diff/minimality audit** — every changed line supports the lane mission; no accidental behavior or generated noise.
2. **Lifecycle/concurrency audit** — cancellation, restart, process death, stale state, threading, backpressure, and failure paths remain correct.
3. **Performance audit** — confirm that optimized work is actually removed/deferred/coalesced; check for new allocations, wakeups, queries, recompositions, or worker churn.
4. **Security/privacy/truth audit** — no weakened encryption, hidden network behavior, broadened active scanning, identifier leakage, or unsupported capability claim.
5. **Verification/handoff audit** — tests and evidence are real, commands/results are recorded, unresolved risks are explicit, and all cross-owner dependencies are handed off rather than edited around.

## Lane A — Syn / Runtime + power + lifecycle

Branch: `maxstats/syn-runtime-r4`

Primary ownership:

- `app/src/main/java/com/flockyou/service/**`
- scanner/service lifecycle and restart policy
- runtime battery/radio policy
- wake locks, watchdogs, scheduling tied directly to scanning service
- pure runtime-policy helpers and their tests

Do not own:

- Compose/UI redesign
- Room schema/DAO/data repository design
- export format backend
- AI settings repository implementation

Required targets, in priority order:

1. Fix BLE watchdog/health recovery so recovery preserves the current scan policy. Remove any implicit aggressive BLE default that can bypass Balanced/Battery Saver.
2. Eliminate the cold-start settings race: scanning must not launch under generic/aggressive in-memory defaults before persisted constrained-device settings are admitted.
3. Audit foreground-service restart, wake-lock, boot/restart helpers, worker cadence, and watchdog paths for accidental reactivation or policy escalation.
4. Audit remaining service hot loops, repeated serialization/broadcasts, redundant collectors, retry storms, and allocations that still matter on constrained hardware.
5. Add regression tests for each root cause. Prefer pure policy tests where they represent real behavior; add lifecycle/integration coverage where policy-only tests would be misleading.

Handoff rule: if the best fix requires changing `AiSettingsRepository`, DAO/schema, or UI state, write a HANDOFF to the owning lane instead of editing it.

## Lane B — DeepSeek V4 Flash / UI + render + map ergonomics

Branch: `maxstats/flash-ui-r4`

Primary ownership:

- `app/src/main/java/com/flockyou/ui/**`
- Compose render/state shape that is strictly presentation-facing
- `MainViewModel*` only for UI projection/state ownership
- existing osmdroid map presentation and export/share UI once a backend contract exists
- UI tests and emulator QA artifacts

Do not own:

- scanner/service runtime policy
- Room schema/DAO/repository internals
- encryption/key management
- export serialization backend

Required targets:

1. Profile and reduce recomposition blast radius, especially the oversized main state and large screens. Split/project state only where evidence shows it reduces unrelated invalidation.
2. Audit Flow collection lifecycle, stable keys/models, large Lazy collections, expensive derived values, map marker/cluster rebuild behavior, and continuous animation.
3. Apply a Superdesign pass to hierarchy, density, interaction states, discoverability, and operator ergonomics. Improve the existing design language rather than replacing it with a generic template.
4. Preserve capability-adaptive behavior: basic phones get a calm honest surface; advanced hardware/privilege exposes more instrumentation without dead controls.
5. Preferred information architecture to evaluate against current code: `NOW -> DEVICES -> HISTORY -> MAP -> ANALYSIS`, with advanced tools grouped under `TOOLS / SENSORS / RF / FLIPPER / DIAGNOSTICS`. Do not force this exact navigation if source-level evidence identifies a better minimal migration.
6. Research/Advanced mode may expose RSSI, UUID/BSSID, MCC/MNC/TAC, GNSS metrics, confidence components, correlation/provenance, scanner health, and feasibility. Normal mode must remain legible.
7. When the Pro lane provides an export contract, expose an explicit privacy-aware export/share surface. No hidden uploads.
8. Emulator QA scenarios and frame/jank evidence are mandatory before performance claims.

## Lane C — DeepSeek V4 Pro / persistence + query + security + export

Branch: `maxstats/pro-data-r4`

Primary ownership:

- `app/src/main/java/com/flockyou/data/**`
- `app/src/main/java/com/flockyou/data/repository/**`
- Room DAO/schema/migrations/indexes
- settings repository caching, including AI settings
- persistence/data-retention behavior
- encryption/key-management truthfulness within the existing architecture
- local export serialization backend and data-domain tests

Do not own:

- Compose UI
- scanner/service runtime policy
- radio scanning behavior

Required targets:

1. Audit DAO hot queries, unbounded Flow materialization, indexes, sort/filter patterns, spatial-history reads, transaction granularity, retention/downsampling, and SQLCipher startup/read/write overhead.
2. Remove per-detection settings-storage reads used only as policy gates. Prefer a repository-owned hot/cached settings snapshot when lifecycle and consistency are correct; do not create duplicated independently-stale caches.
3. Preserve encrypted persistent history and RAM-only ephemeral mode exactly.
4. Audit nuke/data-wipe claims. Treat cryptographic key destruction/crypto-erasure as the primary flash-storage guarantee; do not overclaim multi-pass overwrite on flash/UFS.
5. Current app has an in-app osmdroid map but no verified KML/GPX/GeoJSON/Google-Maps export. Implement a local export backend if it remains absent after source verification.
6. Minimum export formats: KML and GeoJSON. GPX and CSV are optional only if they have clear user value and test coverage.
7. Export must support user-selected time range, protocol/threat filters, explicit location precision control, identifier redaction, and local-only generation. It must not silently upload or contact Google.
8. Add round-trip/golden-format tests, migration tests for any schema change, and privacy tests proving redaction/precision behavior.

## Integration order

All lanes start from the pinned base independently.

Preferred convergence order:

1. Pro data contract/cache/export backend, if it changes APIs consumed by another lane.
2. Syn runtime after rebasing onto accepted Pro changes only where necessary.
3. Flash UI last so it consumes final backend contracts and avoids designing against transient APIs.

If lanes are truly independent after review, merge order may change. Do not manufacture dependencies merely to preserve the preferred order.

## Pull request contract

Each lane opens a **draft PR** immediately after its first test/evidence commit. It becomes ready only when:

- five pre-code passes are documented concisely;
- implementation is complete;
- five post-code audits are documented concisely;
- relevant unit/instrumentation tests pass;
- supported build flavors affected by the change compile;
- lint/static analysis applicable to the touched area passes;
- evidence requirements in `MAXSTATS_ANDROID_PERF_EVIDENCE.md` are satisfied or explicitly marked as physical-device follow-up;
- `MAXSTATS_HANDOFF_CONTRACT.md` is completed in the PR body;
- no foreign-domain files were changed without an explicit handoff and re-scoping decision.

## Convergence gate

After all three lane PRs are independently green:

1. Compare all three diffs against the pinned base.
2. Resolve API overlap by ownership, not by whichever model edited last.
3. Run full supported CI on the converged head.
4. Run emulator QA on cold launch, scan lifecycle, navigation, history, map, background/resume, and privacy-mode flows.
5. Run physical constrained-device profiling before claiming real battery/thermal improvement.
6. Only then declare MAXSTATS R4 complete.
