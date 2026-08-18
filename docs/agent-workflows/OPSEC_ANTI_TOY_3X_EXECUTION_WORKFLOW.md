# OPSEC Anti-Toy 3× Recursive Execution Workflow

**Status:** EXECUTION WORKFLOW / REVIEW-READY DESIGN  
**Repository:** `knowurknottty/Flock-You-Android`  
**Branch:** `design/opsec-anti-toy-hardening-r1`  
**Companion authority:** `docs/agent-workflows/OPSEC_ANTI_TOY_HARDENING_WORKFLOW.md`  
**Mission:** Convert the current application from a strong enthusiast/open-source scanner into an evidence-backed field instrument whose scanner, detector, ranging, mapping, storage, runtime, and operator claims can survive hostile technical review — while deliberately laying the trustworthy observation/world-graph foundation later consumed by the Inversion Labs location game.

---

## 0. Core doctrine

This campaign is not a cosmetic hardening pass.

It is a recursive attempt to remove every place where the software behaves like a toy while preserving the useful ambition of the original project.

The governing rules are:

> **No feature becomes more impressive by becoming less truthful.**

> **Observed is not inferred. Inferred is not verified. Verified is not operationally admitted.**

> **A map marker is a claim about the physical world and must carry provenance, time, uncertainty, and admission state.**

> **A scanner is only as good as the raw observations it preserves, the confounders it models, and the false-positive/false-negative evidence it can show.**

> **The future game may consume sanitized world state. It may never mutate scanner truth into game fiction or use game incentives as evidence.**

"Mil-spec" in this workflow means an engineering quality target, not a certification or marketing label. No artifact may claim military certification, military-grade accuracy, or formal compliance unless an independent certification process actually establishes it.

---

## 1. Why 3× recursion

A single audit pass is vulnerable to the same failure modes as a single model answer: anchoring, confirmation bias, code familiarity, optimistic assumptions, and correlated blind spots.

Every domain therefore receives **three complete reasoning passes** with distinct purposes.

### R1 — SOURCE TRUTH / REALITY RECONSTRUCTION

Question:

> What does the repository and platform actually do right now?

R1 inventories source, data flow, hardware/API dependencies, privilege requirements, state ownership, raw observations, derived values, tests, docs, UI claims, and known external standards.

R1 must not begin by proposing fixes.

Outputs:

- actual implementation path;
- actual authority/state owner;
- actual observations available;
- information currently discarded;
- actual user-visible claim;
- tests that really execute the path;
- missing test/measurement boundaries;
- candidate anti-toy findings.

### R2 — ADVERSARIAL FALSIFICATION / FAILURE RECONSTRUCTION

Question:

> Under what realistic conditions does the R1 story become false, misleading, unsafe, stale, brittle, or exploitable?

R2 attacks assumptions rather than implementation style.

It tests or reasons through:

- malformed but valid inputs;
- spoofed/ambiguous observations;
- dense RF environments;
- device heterogeneity;
- multipath/NLOS;
- weak/absent permissions;
- unavailable sensors;
- low RAM / slow CPU;
- process death;
- reboots and upgrades;
- stale persistence;
- race conditions;
- storage exhaustion;
- stale caches;
- privilege differences;
- invalid clocks;
- dependency compromise;
- player/community world-data poisoning;
- map duplication and stale physical infrastructure;
- misleading UI wording;
- false confidence and false precision.

R2 should attempt to disprove R1 conclusions before accepting them.

### R3 — CONVERGENCE / EVIDENCE / OPERATIONAL ADMISSION

Question:

> What is the strongest implementation and claim that the evidence can actually support?

R3 reconciles R1 and R2, validates findings, defines RED conditions, proposes the smallest correct architecture, establishes acceptance evidence, and assigns an admission tier.

R3 must produce one of:

```text
NOT_A_FINDING
VALIDATED_FINDING
ACCEPTED_RISK_BY_OWNER
DEFERRED_BLOCKED
OPERATIONALLY_ADMITTED
```

An agent/model may recommend `ACCEPTED_RISK`; it may not authorize it.

---

## 2. Recursion independence rule

The three passes are sequential in authority but should remain partially independent in cognition.

For each domain:

1. R1 records facts and candidate findings.
2. R2 begins from the same pinned SHA and domain scope, reads R1 facts but is explicitly tasked to falsify them rather than continue R1's proposed narrative.
3. R3 reads both and must resolve disagreements explicitly.

If multiple models/agents are available, use different workers for R1 and R2 where practical.

If one worker performs all three, clear its working hypothesis between passes and require explicit contradiction search.

No pass may silently update the audited SHA.

---

## 3. Campaign authority packet

Before any audit begins, freeze and publish:

```yaml
repository:
audit_branch:
audit_head_sha:
merge_base_sha:
main_sha:
compile_sdk:
target_sdk:
min_sdk:
flavors:
build_variants:
ci_workflows:
known_device_matrix:
known_bench_hardware:
security_policy_chain:
maxstats_prs:
ranging_design_ref:
```

GitHub/source is authority for current repository state.

Conversation memory, README prose, screenshots, model summaries, and previous audit statements are leads only until verified against the pinned source.

---

## 4. Campaign output architecture

The campaign produces six canonical artifacts.

### A. Anti-Toy Findings Ledger

Every candidate and validated issue, including non-security correctness/truth defects.

### B. OPSEC Threat & Failure Model

Assets, trust boundaries, adversaries, failure modes, recovery expectations, privacy impacts.

### C. Scanner / Measurement Evidence Matrix

For every measurement or detector:

```yaml
observable_inputs:
source_api_or_sensor:
preprocessing:
derived_model:
calibration:
uncertainty:
confounders:
false_positive_evidence:
false_negative_evidence:
device_dependencies:
privilege_dependencies:
admission_tier:
```

### D. Spatial World Graph / Mapping Evidence Matrix

For every persistent mapped entity class:

```yaml
entity_type:
source_observation_types:
identity_resolution:
location_geometry:
location_uncertainty:
source_age:
last_verified:
provenance:
confidence:
contradictions:
merge_split_history:
retirement_state:
public_or_sensitive_policy:
game_projection_policy:
```

### E. Capability / Claim / Evidence Matrix

Maps user-facing claims to source + evidence + admission tier.

### F. Exact-SHA Operational Admission Report

States what is admitted, partially admitted, experimental, blocked, or unsupported at one exact source SHA.

---

## 5. Finding taxonomy

Do not force every defect into CVE-style vulnerability language.

Use at minimum:

```text
TOY_HEURISTIC
FALSE_PRECISION
SCIENCE_GAP
MISSING_CALIBRATION
UNSUPPORTED_THREAT_CLAIM
CAPABILITY_OVERCLAIM
OBSERVATION_LOSS
PROVENANCE_GAP
WORLD_GRAPH_POISONING_RISK
SPATIAL_TRUTH_GAP
STALE_MAP_ENTITY
IDENTITY_RESOLUTION_GAP
DUPLICATE_AUTHORITY
DEAD_IMPLEMENTATION
FAIL_OPEN
PRIVACY_LEAK
CRYPTO_LIFECYCLE_GAP
DATA_INTEGRITY_GAP
LIFECYCLE_FRAGILITY
RECOVERY_GAP
UNBOUNDED_RESOURCE
PERFORMANCE_PATHOLOGY
NETWORK_TRUTH_GAP
IPC_BOUNDARY_GAP
PRIVILEGE_GAP
SUPPLY_CHAIN_GAP
BUILD_PROVENANCE_GAP
UX_TRUTHFULNESS
DOCUMENTATION_OVERCLAIM
TEST_THEATER
BENCHMARK_THEATER
GAME_BOUNDARY_LEAK
```

---

## 6. Severity model

Security CVSS may be attached where appropriate, but campaign ordering uses **mission consequence**.

### S4 — STOP-SHIP / OPERATIONAL BLOCKER

Examples:

- sensitive data exposed across an unintended trust boundary;
- encrypted-history claim is false;
- destructive supported upgrade path;
- scanner produces materially fabricated operational measurement;
- critical/high threat label can be generated from evidence known to be insufficient;
- persistent map can be trivially poisoned into high-confidence physical claims;
- build/release provenance cannot establish source-to-artifact identity;
- fail-open privacy mode.

### S3 — HIGH

Meaningful privacy/security/integrity failure, major false-confidence path, serious lifecycle/recovery defect, or world-graph corruption likely to affect operator decisions.

### S2 — MEDIUM

Real correctness/performance/truth weakness with bounded operational effect.

### S1 — LOW / POLISH

Non-critical clarity, maintainability, or minor efficiency issue.

Every finding also records confidence:

```text
LOW
MEDIUM
HIGH
CONFIRMED
```

---

# PART I — "MIL-SPEC" FIELD SCANNER QUALITY PROGRAM

## 7. Scanner architecture target

The scanner should evolve toward a layered instrument architecture:

```text
ANDROID / EXTERNAL SENSOR APIS
        ↓
RAW OBSERVATION PLANE
        ↓
NORMALIZATION / QUALITY PLANE
        ↓
DETECTION / CLASSIFICATION PLANE
        ↓
CORRELATION / IDENTITY PLANE
        ↓
RANGING / LOCALIZATION PLANE
        ↓
EVIDENCE / CLAIM PLANE
        ↓
WORLD GRAPH
        ↓
MAP / OPERATOR UI
        ↓
SANITIZED GAME PROJECTION
```

No lower layer may derive authority from a higher presentation layer.

---

## 8. Raw observation preservation audit

For each scanner, R1 must answer:

- What does Android/hardware provide?
- What fields are preserved?
- What fields are discarded before evidence capture?
- Which timestamps are wall-clock vs monotonic/controller time?
- Which fields have sentinel/unavailable states?
- Which fields vary by API level, device, build flavor, privilege, or radio chipset?
- Are raw observations retained long enough to reproduce a derived conclusion?

### BLE minimum audit

Evaluate preservation/use of, where available:

- RSSI;
- `ScanResult.txPower`;
- scan-record TX power;
- primary PHY;
- secondary PHY;
- advertising SID;
- data status / truncation;
- periodic interval;
- legacy/extended status;
- connectable state;
- controller observation timestamp;
- service UUIDs;
- service/manufacturer data;
- advertisement fingerprint;
- MAC/address type/rotation behavior where platform exposes enough information;
- packet cadence.

The current Android API exposes RSSI, TX power, PHY, SID, scan record, and a timestamp-since-boot; if the implementation discards useful fields, that is an `OBSERVATION_LOSS` candidate until justified.

### Wi-Fi minimum audit

Evaluate, where API/platform permits:

- RSSI;
- frequency;
- channel/band;
- channel width;
- BSSID;
- SSID;
- capabilities/security;
- timestamp/age;
- Wi-Fi standard / PHY capability where exposed;
- MLO-related data on capable APIs;
- responder/ranging capability;
- scan throttling/platform limitations.

### Cellular minimum audit

Evaluate:

- serving vs neighboring cells;
- RAT;
- identity fields and privilege limitations;
- dBm/RSRP/RSRQ/SINR or technology-specific signal metrics;
- registration state;
- timing/age;
- subscription/SIM context without leaking sensitive identity;
- API-level differences;
- privileged modem/SDM claims separated from ordinary Android capability.

### GNSS minimum audit

Evaluate:

- location estimate;
- horizontal accuracy;
- vertical accuracy where relevant;
- elapsed-realtime age;
- speed/bearing accuracy;
- satellite constellation/count;
- C/N0;
- carrier frequency where available;
- multipath/AGC/data-quality indicators available to the app;
- raw GNSS measurement support and hardware capability;
- spoofing/jamming claims versus actual evidence.

### External RF / Flipper / SDR

Treat external-device observations as untrusted input until normalized and provenance-tagged.

Record:

- hardware identity;
- firmware version when available;
- capture mode;
- timestamp semantics;
- frequency/bandwidth;
- gain/calibration state;
- transport integrity;
- parser bounds;
- provenance.

---

## 9. Scanner quality gates

A detector is not admitted because it compiles or matches a string.

Each operational detector must define:

```text
SIGNAL / OBSERVATION
    ↓
NORMALIZATION
    ↓
FEATURES
    ↓
DETECTOR / RULE / MODEL
    ↓
CONFIDENCE
    ↓
CORROBORATION REQUIREMENTS
    ↓
CLAIM LANGUAGE
```

Mandatory questions:

1. What evidence actually differentiates target from benign lookalikes?
2. What are the strongest known false positives?
3. What are the strongest known false negatives?
4. Which evidence is necessary versus merely supportive?
5. What changes across phone/chipset/region/firmware?
6. Does a vendor/manufacturer OUI imply device function? Usually no.
7. Does signal strength imply distance? Not without a ranging model and uncertainty.
8. Does an anomalous cell imply IMSI catcher? Not by itself.
9. Does RF interference imply jammer? Not by itself.
10. Does an SSID/device name imply identity? Not by itself.

---

## 10. Detector evidence tiers

Use explicit claim tiers.

### D0 — Raw observation

Example: `BLE advertisement observed at -62 dBm`.

### D1 — Pattern match

Example: `Advertisement matches known UUID/name/OUI pattern`.

### D2 — Correlated candidate

Multiple independent characteristics support the candidate.

### D3 — High-confidence family attribution

Strong device-family evidence with documented false-positive controls.

### D4 — Verified physical/entity attribution

Field or external evidence establishes the actual device/entity.

UI wording must match the tier.

---

# PART II — RANGING & LOCALIZATION

## 11. Ranging quality target

The companion Ranging & Localization Plane design remains the authority for exact architecture.

The anti-toy campaign verifies that no code path reintroduces universal RSSI-to-meter fantasy.

A range result should be representable as:

```yaml
estimate_m:
credible_low_m:
credible_high_m:
method:
confidence:
raw_rssi_dbm:
filtered_rssi_dbm:
variance_or_mad:
sample_count:
sample_window_ms:
tx_power_dbm:
tx_power_source:
receiver_calibration:
transmitter_calibration:
path_loss_model:
observation_age_ms:
quality_flags:
```

Distance is nullable.

If absolute range is not defensible, expose proximity/trend only.

---

## 12. Ranging bench campaign

Bench scenarios should include:

- 0.5 m;
- 1 m;
- 2 m;
- 4 m;
- 8 m;
- 16 m where environment permits;
- multiple transmitter families;
- multiple receiver orientations;
- body obstruction;
- LOS;
- NLOS;
- reflective indoor environment;
- outdoor environment;
- stationary source;
- moving receiver;
- moving source where practical;
- dense competing BLE/Wi-Fi environment.

Metrics:

```text
median absolute error
RMSE
P50 / P80 / P95 interval coverage
false-confidence rate
convergence time
trend accuracy
failure-to-estimate rate
```

Interval calibration matters more than impressive decimals.

---

# PART III — MAPPING AS AN EVIDENCE SYSTEM

## 13. Map architecture target

The map is not merely `MapScreen`.

The target architecture is:

```text
OBSERVATIONS
    ↓
SPATIAL EVIDENCE
    ↓
IDENTITY / ENTITY RESOLUTION
    ↓
WORLD GRAPH
    ↓
QUERY / PROJECTION
    ↓
MAP PRESENTATION
```

The map renderer should not own entity truth.

---

## 14. Spatial evidence contract

Every spatial assertion should distinguish at least:

```text
PHONE_POSITION
PHONE_POSITION_UNCERTAINTY
OBSERVATION_POSITION
EMITTER_RANGE_DISTRIBUTION
INFERRED_EMITTER_REGION
KNOWN_INFRASTRUCTURE_COORDINATE
PUBLIC_DATASET_COORDINATE
PLAYER_REPORTED_COORDINATE
VERIFIED_PHYSICAL_COORDINATE
```

Do not draw one uncertainty type as another.

A GPS accuracy circle represents uncertainty in the phone's own location.

A range annulus represents uncertainty in target distance from one observation point.

A multi-position localization region represents inferred target position probability.

A known infrastructure marker represents an entity claim with source/freshness metadata.

---

## 15. World entity model

A future-ready world entity should conceptually preserve:

```yaml
world_entity_id:
entity_type:
entity_family:
operator_or_owner_claim:
physical_geometry:
geometry_uncertainty:
first_observed:
last_observed:
last_field_verified:
status: CANDIDATE|ACTIVE|STALE|MOVED|REMOVED|DISPUTED
observation_refs:
evidence_refs:
claim_refs:
source_datasets:
confidence:
contradictions:
merge_history:
split_history:
visibility_policy:
game_projection_policy:
```

Do not make a rotating MAC/BSSID the primary permanent world identity.

---

## 16. Identity resolution anti-toy audit

R1 inventory:

- current dedup keys;
- current MAC/BSSID assumptions;
- SSID/name assumptions;
- device-type assumptions;
- spatial merge thresholds;
- time windows;
- cross-protocol correlation;
- operator/public dataset joins.

R2 attack:

- randomized MAC;
- shared OUIs;
- identical SSIDs;
- multiple devices on one pole;
- one device exposing multiple radios;
- replaced hardware at same location;
- physically moved infrastructure;
- stale public datasets;
- GPS jitter;
- duplicate player reports;
- malicious/incorrect submissions;
- temporary/mobile infrastructure.

R3 defines entity-resolution confidence and prevents silent destructive merge.

---

## 17. Map freshness / temporal integrity

"Mapped" does not mean "currently exists."

Each persistent entity should support freshness state.

Suggested policy:

```text
CURRENTLY_VERIFIED
RECENTLY_OBSERVED
STALE
DISPUTED
REPORTED_REMOVED
REMOVED_VERIFIED
MOVED
REPLACED
```

The game backend particularly depends on this distinction because seeded bosses from public datasets must not be represented as physically current without freshness evidence.

---

## 18. Map privacy / sensitivity review

Not every observable device should become a public persistent POI.

Policy must distinguish:

- fixed public/commercial/civic infrastructure;
- transient devices;
- personal trackers;
- private residences;
- sensitive personal devices;
- lawfully public but safety-sensitive locations;
- user-private observations.

Game projection defaults should favor infrastructure and aggregated/sanitized world entities, not personal-device tracking.

---

# PART IV — WORLD GRAPH AS FUTURE GAME BACKEND

## 19. Strategic boundary

The anti-toy hardening campaign should deliberately produce a backend architecture usable by the future game **without turning the scanner into the game**.

Preferred boundary:

```text
FIELD INSTRUMENT
    raw observations
    measurements
    detections
    evidence
        ↓
WORLD GRAPH
    durable physical entities
    provenance
    confidence
    temporal state
        ↓
SANITIZED GAME PROJECTION
    encounter seed
    public geometry
    rarity/content metadata
    verification tier
    freshness
        ↓
GAME ENGINE
```

---

## 20. Game projection contract

A future game consumer should never require raw sensitive scanner state by default.

Conceptual projection:

```yaml
public_world_entity_id:
encounter_archetype:
coarse_or_public_geometry:
entity_class:
verification_tier:
freshness_tier:
rarity_seed:
research_completion:
public_evidence_summary:
allowed_game_actions:
```

Exclude or transform:

- raw personal MAC/BSSID identifiers;
- private precise player trails;
- unreviewed private-residence data;
- raw sensitive logs;
- secret/private evidence;
- device data unnecessary for gameplay.

---

## 21. World graph poisoning threat model

Treat community/player data as attacker-controlled input.

Threats include:

- fake infrastructure submissions;
- duplicate farming;
- coordinate spoofing;
- stale dataset resurrection;
- false operator attribution;
- evidence laundering;
- coordinated faction manipulation;
- first-discovery fraud;
- vandalism/retirement fraud;
- malicious image/text payloads;
- mass submission resource exhaustion.

Required controls should eventually include:

- observation provenance;
- duplicate detection;
- confidence accumulation;
- contradiction preservation;
- bounded review queues;
- rate limiting;
- durable moderation/audit history;
- no one-shot promotion from player claim to verified world entity;
- separation of game rewards from claim authority.

---

## 22. Game/scanner anti-corruption rule

Game mechanics must not be able to modify measurement truth.

Examples:

- rarity cannot increase detection confidence;
- faction control cannot change physical attribution;
- player votes cannot override contradictory sensor evidence without a governed review process;
- boss difficulty cannot equal threat severity;
- spawn desirability cannot change geographic truth;
- sponsorship cannot erase verified public-world facts;
- monetization cannot change evidence state.

---

# PART V — RUNTIME / OPSEC / SECURITY

## 23. Runtime recursion domain

R1 reconstructs:

- foreground service lifecycle;
- scanner start/stop;
- wake locks;
- watchdogs;
- process boundaries;
- settings admission;
- background behavior;
- radio mode policy;
- worker/job/alarm scheduling;
- cancellation;
- recovery.

R2 attacks:

- rapid start/stop;
- process kill during admission;
- service kill;
- permission revocation;
- Bluetooth/Wi-Fi off/on;
- battery saver;
- thermal throttling;
- background restrictions;
- restart loops;
- simultaneous watchdog recovery;
- cross-process static state;
- delayed coroutine resurrection.

R3 admits only behavior proven across relevant failure cases.

---

## 24. Data / crypto / privacy recursion domain

Audit:

- Room schema/migrations;
- SQLCipher usage;
- key wrapping;
- Keystore lifecycle;
- passphrase handling;
- ephemeral mode;
- retention;
- nuke/crypto-erasure;
- exports;
- FileProvider grants;
- logs;
- DataStore;
- caches;
- backups;
- crash artifacts;
- screenshots/recents where sensitive;
- external storage;
- clipboard behavior.

Explicitly distinguish:

```text
encrypted at rest
key destroyed
overwrite attempted
cryptographic erasure
forensic impossibility
```

Do not collapse them into "secure delete."

---

## 25. Network / IPC / platform boundary domain

Inventory every endpoint and Android component.

Required manifest:

```yaml
endpoint:
purpose:
trigger:
data_sent:
data_received:
user_visible:
required_for_core_scanning:
privacy_policy:
TLS_or_transport:
```

Components:

```text
activities
services
receivers
providers
intent filters
permissions
exported state
custom permissions
binder/socket IPC
FileProvider paths
```

No undeclared network behavior is acceptable.

---

## 26. Supply chain / build provenance domain

Audit:

- Gradle plugins;
- Maven dependencies;
- pinned versions;
- transitive dependencies;
- repository sources;
- GitHub Actions permissions;
- third-party actions pinned by SHA where appropriate;
- secret exposure;
- signing process;
- artifact provenance;
- SBOM capability;
- dependency vulnerability monitoring;
- reproducibility/traceability;
- release source SHA.

"CI green" is not equivalent to supply-chain integrity.

---

# PART VI — UI / OPERATOR TRUTH

## 27. UI claim recursion

Every operational label is a claim.

Audit strings such as:

```text
confirmed
accurate
nearby
within X meters
GPS accuracy
IMSI catcher
jammer
spoofing
tracking you
securely erased
offline
anonymous
no network calls
real time
background
military grade
```

R1 finds where the string is produced.

R2 identifies circumstances where it becomes false.

R3 chooses exact wording supported by current evidence tier.

---

## 28. Normal versus Research mode

Normal mode should be calm, actionable, and honest.

Research mode may expose:

- raw RSSI;
- filtered RSSI;
- PHY;
- TX power;
- sample count;
- calibration source;
- location accuracy;
- range interval;
- observation age;
- detector tier;
- provenance;
- confidence components;
- scanner health;
- capability limitations;
- contradictions.

Advanced information is not permission to invent precision.

---

# PART VII — PERFORMANCE / CONSTRAINED DEVICE PROGRAM

## 29. Constrained-device doctrine

The older Moto-class device is a first-class test target because it exposes architecture that accidentally assumes flagship resources.

Audit:

- allocations per packet;
- coroutine creation per packet;
- unbounded collections;
- nested scans;
- full-list copies;
- Room query materialization;
- Compose recomposition scope;
- map overlay rebuilds;
- worker/alarm frequency;
- wake locks;
- sensor duty cycle;
- model loading;
- AI hot paths;
- export memory behavior.

Source-level complexity improvements may be claimed directly.

Numerical battery/CPU/RAM/thermal improvements require controlled measurements.

---

## 30. Density stress scenarios

Because the field instrument and future game may encounter crowds/dense infrastructure, include synthetic/controlled high-density scenarios:

```text
1 device
10 devices
100 devices
500 devices where synthetic replay permits
1,000+ historical map entities
10,000+ historical detections
rapid duplicate advertisements
mixed BLE + Wi-Fi + cellular events
map pan/zoom under large entity set
background/resume under load
```

The purpose is capacity characterization, not disruption of third-party systems.

---

# PART VIII — TEST / EVIDENCE ANTI-THEATER

## 31. Test theater rules

Reject tests that only:

- assert constants;
- instantiate data classes without exercising behavior;
- reproduce implementation logic inside expected-value code;
- test mocks while never touching the production path;
- prove a method returns its own hard-coded value;
- call a fake implementation instead of the real adapter;
- skip supported flavors without explanation;
- use disabled production features to produce benchmark results.

For each major capability, ask:

> What failure would this test catch that would matter to the operator?

---

## 32. Evidence ladder

Use:

```text
E0 SOURCE INSPECTION
E1 UNIT TEST
E2 INTEGRATION TEST
E3 EMULATOR / PLATFORM BEHAVIOR
E4 PHYSICAL DEVICE
E5 CONTROLLED BENCH / GROUND TRUTH
E6 ADVERSARIAL / FAILURE INJECTION
E7 INDEPENDENT EXACT-SHA REVIEW
```

No evidence level implies a higher one.

---

# PART IX — EXECUTION MECHANICS

## 33. Phase 0 — Freeze

Before discovery:

- converge or explicitly freeze MAXSTATS lanes;
- select exact audit SHA;
- preserve open PR state;
- record test baseline;
- record device/bench availability;
- create campaign tracking issue;
- create canonical findings ledger branch/path;
- do not change production code yet.

---

## 34. Phase 1 — Full repository R1

Run R1 across these domains:

1. BLE scanner;
2. Wi-Fi scanner;
3. cellular scanner;
4. GNSS;
5. RF/SDR/Flipper;
6. ultrasonic/audio;
7. detection/classification;
8. tracker-following/correlation;
9. ranging/localization;
10. map/spatial evidence;
11. identity/world graph;
12. runtime/lifecycle/power;
13. Room/repository/data;
14. crypto/key lifecycle;
15. export/share;
16. network/Tor/external services;
17. IPC/Android components;
18. AI/LLM integration;
19. UI/operator claims;
20. build/release/supply chain;
21. docs/public claims;
22. future game projection boundary.

Each domain produces candidate findings only.

---

## 35. Phase 2 — Full repository R2

Repeat all 22 domains adversarially.

Do not merely review R1 findings.

Search independently for missed failure modes and specifically attempt to disprove:

- capability claims;
- measurement claims;
- security claims;
- privacy claims;
- map/world identity claims;
- lifecycle assumptions;
- backend/game separation assumptions.

---

## 36. Phase 3 — Full repository R3

Reconcile R1/R2.

For every finding:

```yaml
id:
title:
taxonomy:
severity:
confidence:
source_paths:
claim_affected:
operator_consequence:
game_backend_consequence:
reproduction_or_reasoning:
red_test_or_probe:
owner_lane:
dependencies:
acceptance_evidence:
```

Prioritize S4 → S3 → S2 → S1.

---

## 37. GitHub tracking contract

Every validated S4/S3/S2 finding gets its own GitHub issue unless multiple findings are inseparable manifestations of one root cause.

Issue title prefix:

```text
[ANTI-TOY][S4]
[ANTI-TOY][S3]
[ANTI-TOY][S2]
```

Issue body includes:

- exact audited SHA;
- R1 facts;
- R2 falsification;
- R3 conclusion;
- affected paths;
- RED condition;
- acceptance gate;
- cross-lane dependencies;
- device/bench requirement;
- claim wording impact;
- game/world-graph impact where applicable.

---

## 38. Remediation protocol — one finding at a time

For each issue:

1. create dedicated branch from the declared campaign baseline or approved dependency head;
2. write the RED test/probe first where practical;
3. prove RED is behaviorally meaningful;
4. implement smallest correct fix;
5. run focused tests;
6. run affected flavor tests/build/lint;
7. execute required device/bench checks;
8. perform 3× post-code recursion;
9. update issue evidence;
10. open PR;
11. independent review;
12. merge only after gate satisfied;
13. update campaign baseline deliberately;
14. continue to next prioritized issue.

No stopping merely because one impressive fix landed.

---

## 39. Post-code 3× recursion

Every remediation PR receives three post-code passes.

### P1 — DIFF / CONTRACT

- minimality;
- ownership;
- API compatibility;
- authority boundaries;
- test relevance;
- no foreign-domain accidental changes.

### P2 — FAILURE / ADVERSARIAL

- races;
- lifecycle;
- malformed input;
- permissions;
- low resources;
- stale state;
- security/privacy;
- world graph poisoning;
- map truth;
- game boundary leakage.

### P3 — EVIDENCE / CLAIM

- exact-head CI;
- physical/bench evidence if needed;
- claim wording;
- uncertainty;
- docs/UI consistency;
- residual risk;
- admission tier.

---

## 40. Campaign parallelism

Parallelism is permitted only for genuinely non-overlapping lanes.

Recommended lanes after R3:

```text
A  Scanner / detection science
B  Ranging / localization / spatial evidence
C  Runtime / lifecycle / power
D  Data / crypto / privacy
E  Network / IPC / build / supply chain
F  Mapping / world graph / provenance
G  UI / operator truth / docs
H  Game projection / backend boundary
```

No lane may silently edit another lane's owned files to unblock itself.

Cross-lane needs become explicit handoffs.

---

# PART X — ADMISSION GATES

## 41. Scanner operational admission

Do not call the scanner operationally trustworthy until:

- major scanner inputs are inventoried and important observation loss is resolved or justified;
- detector claims have evidence tiers;
- known high-impact false positives are controlled;
- hardware/API limitations are surfaced;
- runtime survives relevant failure injection;
- sensitive persistence behavior is verified;
- exact-head supported flavors are green;
- constrained-device operation is characterized;
- open S4 issues are zero;
- S3 residuals have explicit disposition.

---

## 42. Mapping/world-graph admission

Do not call a mapped entity verified merely because:

- it exists in a public dataset;
- a player reports it;
- a MAC/BSSID appears nearby;
- a camera-like object is visible;
- a model predicts the class;
- an old coordinate existed historically.

Verified mapping requires source/provenance/freshness appropriate to the claim.

---

## 43. Game-backend readiness admission

The hardened scanner may be considered **game-backend ready** when:

1. world entities are distinct from raw observations;
2. entity identity is not bound blindly to rotating identifiers;
3. spatial uncertainty is represented;
4. freshness/retirement exists;
5. provenance survives entity updates;
6. contradictions survive rather than being overwritten;
7. public/sensitive visibility policy exists;
8. sanitized game projection can be generated without raw personal identifiers;
9. game state cannot mutate evidence authority;
10. community input has an attacker-controlled ingestion design;
11. first-discovery rewards cannot directly grant verification authority;
12. map queries scale to expected game-region density.

This does **not** mean the game itself is implemented.

---

## 44. "Mil-spec" aspirational quality gate

For internal planning, a feature may be tagged `MILSPEC_TARGET_MET` only when all applicable conditions are satisfied:

```text
explicit threat/failure model
bounded resource behavior
secure-by-default policy
fail-closed privacy/security where appropriate
graceful capability degradation
reproducible tests
physical-device evidence where platform behavior matters
bench/calibration evidence where physical measurement matters
adversarial/failure injection
independent review
exact-SHA artifact provenance
truthful operator wording
known residual risk documented
```

This tag is internal engineering shorthand only.

It is not a certification and is not public marketing language.

---

# PART XI — CAPT HANDOFF

## 45. CAPT campaign mapping

When CAPT is used on the stabilized codebase, map this workflow directly into governance state.

```text
MISSION
    Flock-You OPSEC Anti-Toy Operational Hardening

SUBMISSIONS / TASKS
    one domain recursion
    one validated finding
    one remediation
    one verification
    one admission decision

EVIDENCE
    source excerpts
    test runs
    CI runs
    screenshots
    physical device measurements
    bench captures
    standards references
    public dataset provenance

CLAIMS
    scanner claims
    detector claims
    distance claims
    map/entity claims
    security/privacy claims
    release claims

VERIFICATION DOMAINS
    source correctness
    execution integrity
    security/privacy
    physical measurement
    spatial/world-graph integrity
    performance
    game projection integrity
```

CAPT should preserve durable campaign state so no model has to remember which finding, SHA, evidence tier, or admission state is authoritative.

---

## 46. Required campaign kickoff artifacts

Before remediation begins, produce:

1. `ANTI_TOY_FINDINGS_LEDGER.md` or machine-readable equivalent;
2. `OPSEC_THREAT_FAILURE_MODEL.md`;
3. `SCANNER_MEASUREMENT_EVIDENCE_MATRIX.md`;
4. `WORLD_GRAPH_MAPPING_EVIDENCE_MATRIX.md`;
5. `CAPABILITY_CLAIM_EVIDENCE_MATRIX.md`;
6. `CAMPAIGN_EXECUTION_QUEUE.md`;
7. one GitHub campaign tracking issue linking all findings;
8. exact-SHA baseline evidence;
9. device/bench gaps list;
10. explicit game-backend readiness gaps list.

---

## 47. Final closure

The campaign closes only at a pinned SHA after:

- all S4 findings are closed;
- S3 findings are closed or explicitly owner-accepted with rationale;
- scanner measurement claims match evidence;
- ranging claims match calibration state;
- map entities preserve provenance, uncertainty, and freshness;
- privacy/crypto/network behavior matches operator claims;
- supported build flavors pass exact-head CI;
- physical-device checks are complete for relevant platform claims;
- bench checks are complete for admitted physical measurements;
- deep mobile/security review is complete;
- public docs/UI are reconciled;
- game projection boundary is reviewed;
- final Operational Admission Report is generated.

The resulting product should be substantially more real than theater because every important claim has been forced through source truth, adversarial falsification, and evidence-backed convergence.

---

## 48. Immediate next execution order

After owner review of this workflow:

1. finish/converge current MAXSTATS R4 work;
2. finalize and link the Ranging & Localization Plane design;
3. pin the anti-toy campaign baseline SHA;
4. execute Phase 0 inventory;
5. run R1 over all 22 domains;
6. run independent R2 over all 22 domains;
7. reconcile with R3;
8. create GitHub findings/issues;
9. execute S4/S3 fixes one at a time;
10. continue recursively through scanner, mapping/world graph, runtime, privacy/security, and backend-readiness admission.
