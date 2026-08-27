# CAPT Bootstrap Packet — Flock-You OPSEC / Anti-Toy Campaign

**Status:** BOOTSTRAP / MORNING HANDOFF  
**Date:** 2026-08-18  
**Repository:** `knowurknottty/Flock-You-Android`  
**Purpose:** Give CAPT a compact, durable authority packet so the first campaign run starts from GitHub truth rather than reconstructing state from chat history.

---

## 0. Authority order

CAPT must use this order:

1. current GitHub repository/PR/branch/commit state;
2. exact committed design/governance documents referenced below;
3. exact CI/device/bench evidence attached to a pinned SHA;
4. issue/PR comments and handoffs;
5. conversation summaries only as leads.

If any lower source disagrees with GitHub/source, GitHub/source wins.

Do not infer that a green CI run resolves an architectural or evidence blocker.

---

## 1. Current repository baseline

At the time this packet was written, `main` was:

`b62e493d070a2c6cf6819bcfd6246e81675c6567`

Before execution, CAPT must re-read `main` and all open PR heads. Do not assume the SHAs below remain current.

---

## 2. Active MAXSTATS R4 lanes

### PR #6 — SYN runtime / lifecycle lane

- PR: `#6 perf: MAXSTATS R4 runtime lifecycle hardening`
- branch: `maxstats/syn-runtime-r4`
- head: `679f4761641376e8a0f3e53ad0d4b20891b0ec6a`
- state at packet creation: OPEN, READY, MERGEABLE, UNMERGED
- exact-head CI: `32100366520` SUCCESS
- all three flavor unit tests/builds + lint passed
- no physical battery/thermal percentages claimed

Key delivered behavior:

- conservative pre-admission BLE policy;
- persisted settings admitted before active scanners;
- watchdog/recovery shares admitted policy;
- restart receiver/job moved into scanning process;
- one-minute exact heartbeat alarm retired;
- detector recovery bounded/keyed/cancelable;
- coherent teardown/re-admission on scan-loop recovery.

Remaining runtime hotspots are explicitly documented in PR #6.

### PR #10 — DeepSeek Pro data/persistence/export lane

- PR: `#10 MAXSTATS R4 Lane C (Pro): persistence + query + security + export`
- branch: `maxstats/pro-data-r4`
- head at packet creation: `9bb0a2f9717cf577e8e03b933b4dd2572ea3e36f`
- state: OPEN, DRAFT, MERGEABLE, UNMERGED
- exact-head CI: `32101206815` SUCCESS

**IMPORTANT: CI green does not make this PR convergence-ready.**

Known semantic blockers at this exact head:

1. `AiSettingsRepository.settingsSnapshot()` can republish a stale hydrated value after a concurrent successful write because hydration and invalidation are not serialized/versioned.
2. export defaults contradict a privacy-first contract if `ExportRequest(format=...)` emits identifiers and effectively precise location by default; sensitive choices must become explicit or safer by default.
3. singleton `SimpleDateFormat` in export filename generation is not thread-safe; use `DateTimeFormatter` or per-call formatting.

Do not consume the Pro settings cache API from Syn/service code until blocker #1 is actually fixed in source with deterministic concurrency regression coverage.

Do not wire Flash export UI until the export privacy contract is stabilized.

### PR #11 — Flash UI lane, taken over by SYN

- PR: `#11 perf(ui): MAXSTATS R4 map/render hardening`
- branch: `maxstats/flash-ui-r4`
- head: `6a5efaf28a3fd9c2617cc3dd4ff00b3183ca44fa`
- state: OPEN, DRAFT, MERGEABLE, UNMERGED
- exact-head CI: `32103253117` SUCCESS

Exact-head substantive gates passed:

- Unit Tests sideload/system/oem;
- Debug Build sideload/system/oem with provenance attestation;
- Lint.

Delivered tranche:

- O(n²) pairwise map clustering → deterministic grid bucket policy;
- raw fractional zoom rebuilds → coarse zoom buckets;
- redundant coordinate passes removed;
- map listener lifecycle cleanup;
- false RSSI-derived “GPS accuracy” circle removed;
- O(n²) related-detection counts → O(n log n) grouped/sliding-window implementation;
- history filter/sort moved to a narrow state projection;
- lifecycle-aware Compose collection on touched high-value surfaces.

This is a **green performance/truthfulness tranche, not the complete Lane B remit**.

Outstanding Lane B work:

- Superdesign/operator ergonomics;
- capability-adaptive normal vs research/advanced presentation;
- information-architecture review;
- export/share UI after PR #10 contract stabilization;
- emulator QA;
- physical constrained-device profiling;
- future uncertainty-aware range/map integration from PR #13 after implementation.

---

## 3. OPSEC / Anti-Toy design authority

### PR #12

- title: `docs: OPSEC anti-toy 3x operational hardening program`
- branch: `design/opsec-anti-toy-hardening-r1`
- state: OPEN, DRAFT

Authority documents:

1. `docs/agent-workflows/OPSEC_ANTI_TOY_HARDENING_WORKFLOW.md`
2. `docs/agent-workflows/OPSEC_ANTI_TOY_3X_EXECUTION_WORKFLOW.md`
3. `docs/agent-workflows/OPSEC_DONOR_BENCHMARK_SOPHIA_DETECX.md`
4. this bootstrap packet

The campaign is broader than conventional vulnerability scanning. It audits:

- scanner/measurement truth;
- false precision;
- detector evidence tiers;
- ranging/localization;
- mapping/spatial evidence;
- world-graph identity/provenance/freshness;
- runtime/lifecycle/power;
- persistence/crypto/privacy;
- network/IPC/platform exposure;
- AI/LLM authority boundaries;
- build/supply chain;
- UI/public claims;
- constrained-device behavior;
- future game projection integrity.

Every domain uses:

`R1 SOURCE TRUTH → R2 ADVERSARIAL FALSIFICATION → R3 EVIDENCE / ADMISSION`

Every remediation uses:

`P1 DIFF/CONTRACT → P2 FAILURE/ADVERSARIAL → P3 EVIDENCE/CLAIM`

---

## 4. Ranging & Localization design authority

### PR #13

- title: `docs: define evidence-backed ranging and localization plane`
- branch: `design/ranging-localization-plane-r1`
- head at packet creation: `f25f2b2ea70ede1078951cb68422d83c244f315c`
- state: OPEN, DRAFT
- file: `docs/agent-workflows/RANGING_LOCALIZATION_PLANE_SPEC.md`

The spec replaces universal RSSI→meters behavior with:

- richer raw radio observations;
- bounded robust temporal RSSI conditioning;
- calibration hierarchy;
- nullable absolute distance;
- uncertainty intervals;
- proximity trend;
- capability-selected ranging backends;
- optional multi-position localization;
- distinct map semantics for phone location accuracy, target range, inferred emitter region, and verified infrastructure coordinates.

**Review gate:** owner must review the written PR #13 spec before production ranging implementation begins.

After approval, create a TDD implementation plan. Do not skip directly from this packet into source changes.

---

## 5. SØPHIA / Detecx donor benchmark

SØPHIA is treated as a commercial feature/design donor only; no public source repository was identified during review.

High-value ideas absorbed into the anti-toy program:

- environmental baseline;
- anomaly/change detection;
- transient vs recurring/persistent/moved/stale/removed state;
- timeline/debrief;
- independent Wi-Fi/BLE duty controls;
- local/offline field posture;
- structured exports;
- future multi-node sensing concepts.

Marketing claims such as rough distance/risk scoring are **prove-or-downgrade targets**, never implementation authority.

Treasure Chest donor note:

`docs/donors/SOPHIA_DETECX_DONOR_ANALYSIS.md`

---

## 6. Strategic architecture

```text
ANDROID / EXTERNAL SENSOR APIS
        ↓
RAW OBSERVATION PLANE
        ↓
NORMALIZATION / QUALITY
        ↓
DETECTION / CLASSIFICATION
        ↓
CORRELATION / IDENTITY
        ↓
RANGING / LOCALIZATION
        ↓
EVIDENCE / CLAIM
        ↓
TEMPORAL WORLD GRAPH
        ├── OPERATOR MAP / FIELD TOOL
        └── SANITIZED GAME PROJECTION
```

The scanner is the field instrument.

The world graph is the durable physical-world model.

The game is a downstream consumer.

No game state, rarity, faction control, sponsorship, reward, or vote may increase detection/ranging/entity confidence or erase contradictory evidence.

---

## 7. Game-backend architectural intent

The hardening campaign indirectly builds the future game's backend by making these concepts real now:

- durable physical entity identity independent of rotating identifiers;
- location uncertainty;
- provenance;
- first/last observed;
- field verification;
- recurring/transient/persistent/moved/removed/stale state;
- contradiction retention;
- public/sensitive visibility policy;
- sanitized world projection.

The future game should consume something like:

```yaml
public_world_entity_id:
entity_class:
public_or_coarse_geometry:
verification_tier:
freshness_tier:
encounter_seed:
public_evidence_summary:
```

It should not require raw personal identifiers or precise private player trails.

---

## 8. CAPT first-run mission

Suggested mission:

**Flock-You OPSEC Anti-Toy Operational Hardening**

Do not begin by editing source.

First CAPT run should:

1. re-read current GitHub main + PR #6/#10/#11/#12/#13 state;
2. reconcile any changed SHAs;
3. confirm PR #10 blockers against current source;
4. confirm PR #11 exact-head CI and remaining UI remit;
5. present PR #13 written ranging spec for owner review;
6. present PR #12 stop-ship/admission policy for owner review;
7. converge or explicitly freeze MAXSTATS R4;
8. only then select the exact Anti-Toy campaign baseline SHA;
9. generate Phase 0 inventory artifacts;
10. start R1 discovery across all 22 domains.

---

## 9. Required Phase 0 outputs

Before remediation:

- exact audited SHA;
- source/package inventory;
- permissions/components manifest;
- endpoint/network manifest;
- feature/capability inventory;
- public/UI claim inventory;
- Anti-Toy Findings Ledger;
- OPSEC Threat & Failure Model;
- Scanner / Measurement Evidence Matrix;
- World Graph / Mapping Evidence Matrix;
- Capability / Claim / Evidence Matrix;
- device/bench gap list;
- game-backend readiness gap list;
- prioritized S4/S3/S2 queue.

Do not let an attractive individual code fix replace whole-repository discovery.

---

## 10. Hard rules for CAPT

1. GitHub/source authority over conversation memory.
2. No silent merge of MAXSTATS lanes.
3. No accepted-risk or out-of-scope state without owner authority.
4. No `military-grade` certification claim from internal hardening tags.
5. No numerical performance claim without comparable measurements.
6. No physical ranging accuracy claim without device/bench evidence.
7. No security-scanner zero-findings result closes the Anti-Toy campaign.
8. No game incentive modifies evidence authority.
9. No map marker becomes verified physical truth from one weak input.
10. No model-generated prose becomes evidence.
11. No one-pass self-review is sufficient where 3× recursion is required.
12. Do not manufacture completion because CI is green.

---

## 11. Screenshot-friendly CAPT states

For the planned governance demonstration, useful screens/states to capture are those that truthfully show:

- mission authority + pinned repository SHA;
- task decomposition by audit domain;
- evidence attached to a finding;
- R1/R2 disagreement before R3 resolution;
- claim/evidence/admission distinction;
- blocked transition because evidence is insufficient;
- exact-head verification record;
- cross-model/cross-lane handoff with preserved authority;
- an admitted claim after evidence clears its gate.

Do not stage fake findings or fake successful verification for screenshots. Use genuine campaign state.

---

## 12. Morning decision points

Owner review required for:

1. PR #13 written Ranging & Localization Plane spec;
2. PR #12 stop-ship/admission policy;
3. disposition/order for completing PR #11's remaining UI remit;
4. Pro PR #10 corrected head once blockers are fixed;
5. MAXSTATS convergence/freeze strategy;
6. exact Anti-Toy campaign baseline.

Until those are resolved, design/governance work may continue but broad production remediation should remain gated.
