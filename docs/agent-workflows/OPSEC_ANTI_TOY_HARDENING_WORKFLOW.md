# OPSEC Anti-Toy Hardening & Operational Admission Workflow

**Status:** DESIGN / GOVERNANCE SPEC — REVIEW REQUIRED BEFORE EXECUTION  
**Repository:** `knowurknottty/Flock-You-Android`  
**Branch:** `design/opsec-anti-toy-hardening-r1`  
**Date:** 2026-08-18  
**Purpose:** Audit the complete product for toy-grade assumptions and drive each operationally relevant feature toward evidence-backed, resilient, privacy-preserving, security-hardened behavior.

---

## 0. Mission

Flock-You started as a strong open-source idea. Inversion Labs is not treating it as a novelty scanner, demo, advocacy toy, or collection of impressive heuristics.

The target is an **operationally trustworthy Android field instrument** whose claims, measurements, security properties, lifecycle behavior, and failure modes remain defensible under hostile review.

This workflow therefore audits more than conventional software vulnerabilities.

It must find and classify:

- security vulnerabilities;
- privacy leaks;
- brittle lifecycle behavior;
- false precision;
- unsupported scientific assumptions;
- optimistic capability claims;
- stale or duplicated authorities;
- unsafe fallbacks;
- silent data loss;
- unbounded resource behavior;
- weak recovery semantics;
- insecure build/supply-chain behavior;
- UI claims that outrun evidence;
- documentation claims that outrun implementation;
- code that is technically implemented but has never crossed a meaningful verification boundary.

The central rule:

> **No feature may become more impressive by becoming less truthful.**

---

## 1. What "military-grade" means here

This workflow does **not** use "military-grade" as a marketing claim.

It is an engineering aspiration represented by concrete properties:

- explicit authority and trust boundaries;
- fail-closed behavior where privacy/security requires it;
- graceful degradation where capability absence requires it;
- bounded memory/CPU/wakeup/resource behavior;
- reproducible evidence;
- calibrated measurements;
- explicit uncertainty;
- deterministic and auditable state transitions where practical;
- secure-by-default configuration;
- minimal privileges and minimized sensitive-data collection;
- recovery from process death, restart, partial failure, and corrupted state;
- no hidden network/data behavior;
- no silent destructive fallback;
- strong cryptographic lifecycle semantics;
- supply-chain visibility;
- operational logging without leaking sensitive data;
- independent verification;
- exact-SHA evidence closure;
- claims separated from observations, evidence, verification, and operational admission.

No public-facing or internal artifact should call the product "military-grade" merely because this workflow exists.

A feature earns stronger language only after evidence supports it.

---

## 2. Standards basis

This is a product-specific overlay, not a claim of formal certification.

The workflow should map its controls and evidence to:

### Secure development

- **NIST SP 800-218 SSDF v1.1** — normative secure-development baseline.
- **NIST SP 800-218 Rev. 1 / SSDF v1.2 draft** — forward-looking delta tracker only until final.

### Security/privacy controls and assessment

- **NIST SP 800-53 Rev. 5 / current Release 5.x control catalog** — control taxonomy and security/privacy outcomes.
- **NIST SP 800-53A Rev. 5** — assessment-method discipline.

### Mobile-specific verification

- **OWASP MASVS** — storage, crypto, auth, network, platform, code, resilience, privacy.
- **OWASP MASTG/MASWE** — test techniques and mobile weakness mapping.

### Platform authority

- Android Developers security/privacy guidance.
- Android API contracts and platform security behavior.

### Secure-by-design philosophy

- CISA Secure by Design / Secure by Default guidance.

These standards do not replace repository-specific threat modeling. They prevent the audit from becoming a collection of personal preferences.

---

## 3. Authority model

At the start of every audit round, record:

```text
REPOSITORY
TARGET BRANCH
TARGET HEAD SHA
MERGE BASE
ANDROID COMPILE / TARGET / MIN SDK
BUILD FLAVORS
TEST COUNT / GATES
KNOWN DEVICE TEST MATRIX
KNOWN PRIVILEGE MODES
```

Authority order:

1. exact source at audited SHA;
2. executable tests and generated evidence at that SHA;
3. platform/API contracts;
4. supplied controlled bench/device evidence;
5. governing repository docs;
6. README/public prose;
7. conversation memory.

Conversation memory may help locate work. It never overrides repository state.

No audit may silently change target SHA mid-pass.

If the branch moves, close the current evidence window and begin a new one.

---

## 4. Core state distinctions

Never collapse these states:

```text
IMPLEMENTED
OBSERVED
TESTED
INTEGRATION-VERIFIED
DEVICE-VERIFIED
CALIBRATED
ADVERSARIAL-VERIFIED
CLAIM-SUPPORTED
OPERATIONALLY-ADMITTED
```

Similarly, never collapse:

```text
observation
recorded evidence
inference
correlation
verification
accepted claim
operator decision
```

A unit test passing does not make a physical measurement calibrated.

A successful device run does not prove the security boundary.

A security scan with no findings does not prove scientific correctness.

---

## 5. Anti-toy finding taxonomy

Every finding must use one or more canonical classes.

### Measurement / science

- `FALSE_PRECISION`
- `TOY_HEURISTIC`
- `SCIENCE_GAP`
- `MISSING_CALIBRATION`
- `UNMODELED_UNCERTAINTY`
- `UNSUPPORTED_INFERENCE`
- `STALE_REFERENCE_MODEL`
- `SENSOR_SEMANTICS_ERROR`

### Capability / truth

- `CAPABILITY_OVERCLAIM`
- `UI_TRUTHFULNESS`
- `DOC_CLAIM_MISMATCH`
- `PRIVILEGE_ASSUMPTION`
- `HARDWARE_ASSUMPTION`
- `API_LEVEL_ASSUMPTION`
- `SIMULATED_AS_REAL`

### Runtime / resilience

- `LIFECYCLE_FRAGILITY`
- `RESTART_STORM`
- `RECOVERY_GAP`
- `STALE_STATE`
- `RACE_CONDITION`
- `UNBOUNDED_RESOURCE`
- `POLLING_PATHOLOGY`
- `WAKELOCK_PATHOLOGY`
- `FAIL_OPEN`
- `FAIL_UNDEFINED`

### Data / privacy / security

- `PRIVACY_LEAK`
- `DATA_MINIMIZATION_GAP`
- `CRYPTO_LIFECYCLE_GAP`
- `KEY_MANAGEMENT_GAP`
- `AUTHORIZATION_GAP`
- `IPC_BOUNDARY_GAP`
- `EXPORT_EXPOSURE`
- `LOGGING_EXPOSURE`
- `DESTRUCTIVE_FALLBACK`
- `NETWORK_TRUST_GAP`

### Architecture / maintainability

- `DUPLICATE_AUTHORITY`
- `DEAD_IMPLEMENTATION`
- `STALE_TEST_AUTHORITY`
- `HIDDEN_SIDE_EFFECT`
- `GLOBAL_SINGLETON_HAZARD`
- `UNTRACKED_ASYNC_WORK`
- `CROSS_PROCESS_STATE_ASSUMPTION`

### Build / release / supply chain

- `DEPENDENCY_RISK`
- `UNPINNED_TOOLCHAIN`
- `UNVERIFIED_ARTIFACT`
- `SIGNING_GAP`
- `PROVENANCE_GAP`
- `SECRET_EXPOSURE`
- `UNSAFE_CI_PERMISSION`
- `RELEASE_REPRODUCIBILITY_GAP`

A finding may have multiple classes but one primary class.

---

## 6. Severity and confidence

Severity and confidence are independent.

### Severity

**S0 — informational**  
No meaningful operational consequence; cleanup or documentation quality.

**S1 — low**  
Localized correctness/usability issue; limited operational consequence.

**S2 — moderate**  
Can materially mislead an operator, waste resources, lose data, or degrade capability.

**S3 — high**  
Can create a serious privacy/security failure, materially false threat/measurement output, major reliability failure, or significant data loss.

**S4 — critical**  
Realistic path to severe confidentiality/integrity failure, dangerous operator conclusion, persistent destructive behavior, compromise of sensitive evidence, or broad production failure.

### Confidence

- `HYPOTHESIS`
- `SOURCE_SUPPORTED`
- `REPRODUCED`
- `TEST_PINNED`
- `DEVICE_OR_BENCH_VERIFIED`

A severe hypothesis remains important but is not presented as a reproduced defect.

---

## 7. Canonical finding record

Every finding must record:

```yaml
id:
title:
primary_class:
secondary_classes: []
severity:
confidence:
status:

first_seen_sha:
validated_sha:
affected_paths: []
affected_build_modes: []
affected_android_versions: []
affected_hardware_capabilities: []

operator_consequence:
security_privacy_consequence:
scientific_measurement_consequence:

source_evidence: []
runtime_evidence: []
external_authority: []

root_cause:
red_condition:
fix_boundary:
forbidden_shortcuts: []
acceptance_gate: []

owner_lane:
cross_lane_handoffs: []
residual_risk:
```

No finding closes with `fixed=true` and no acceptance evidence.

---

## 8. Repository-wide audit domains

The repository must be decomposed into explicit audit domains so no agent can claim "full audit" after reading only attractive files.

### Domain A — Radio and detection science

Audit:

- BLE;
- Wi-Fi;
- cellular;
- RF;
- ultrasonic;
- GNSS;
- satellite;
- external/Flipper integrations;
- device/signature matching;
- advertising-rate logic;
- tracker-following logic;
- signal strength semantics;
- correlation rules.

Questions:

- What is directly observed?
- What is inferred?
- What assumptions convert observation into a label?
- Are thresholds sourced, calibrated, or arbitrary?
- Can ordinary consumer behavior mimic the signature?
- What is the false-positive/false-negative model?
- Does protocol/device identity survive address rotation correctly?
- Are impossible claims being made from commodity Android APIs?

### Domain B — Ranging and localization

Audit all distance/location semantics.

Required outcome:

- remove universal hard-coded RSSI-to-meter claims;
- preserve raw observation provenance;
- calibrated hierarchical ranging;
- explicit uncertainty;
- truthful fallback to relative proximity when meters are not defensible;
- actual phone-location accuracy distinct from target-range uncertainty;
- capability-adaptive precision backends;
- controlled bench validation.

This domain is governed by the separate Ranging & Localization Plane architecture.

### Domain C — Threat scoring, confidence, and evidence

Audit:

- likelihood versus impact;
- confidence calculations;
- cross-protocol correlation;
- repeated-observation logic;
- escalation/de-escalation;
- stalking/following claims;
- "confirmed" terminology;
- LLM-generated interpretation boundaries.

Hard rule:

> Threat severity, detection confidence, game rarity, and physical proximity are separate variables.

### Domain D — Runtime, lifecycle, power, and resilience

Audit:

- service startup;
- persisted-settings admission;
- process boundaries;
- restart mechanisms;
- watchdogs;
- exact alarms;
- WorkManager/JobScheduler;
- wake locks;
- scanner state;
- battery modes;
- background behavior;
- cancellation;
- delayed jobs;
- process death;
- boot behavior;
- low-memory recovery;
- configuration/permission changes.

Search specifically for duplicated restart authorities and process-local state used as cross-process truth.

### Domain E — Persistence, crypto, privacy, and wipe

Audit:

- SQLCipher configuration;
- key generation/storage/wrapping;
- crypto-erasure;
- ephemeral mode;
- migration behavior;
- destructive fallbacks;
- retention;
- caching;
- export;
- raw identifiers;
- location precision;
- database backups;
- forensic residue;
- logs.

No flash overwrite claim may outrun actual flash/UFS semantics.

### Domain F — Network, IPC, inter-app, and external integrations

Audit:

- every network endpoint;
- TLS assumptions;
- certificate behavior;
- Tor/Orbot integration;
- model downloads;
- IP/DNS checks;
- map tiles;
- GitHub/OUI fetches;
- intents;
- FileProvider;
- Binder/socket/IPC;
- exported components;
- permissions;
- URI grants;
- external storage.

Generate a **Network Contact Manifest** listing every intentional external endpoint and reason.

Any undeclared contact is a finding.

### Domain G — UI, capability truthfulness, and operator ergonomics

Audit:

- unavailable controls;
- fake precision;
- stale state;
- normal vs advanced disclosure;
- error presentation;
- evidence/provenance display;
- location/range semantics;
- threat labels;
- destructive-action confirmation;
- lifecycle-aware collection;
- large-list/map behavior;
- accessibility.

Normal mode may simplify detail. It may not simplify away truth.

### Domain H — Build, CI, dependencies, signing, and supply chain

Audit:

- Gradle wrapper/toolchain;
- plugin versions;
- dependency inventory;
- transitive SDK behavior;
- flavor differences;
- system/OEM privilege changes;
- build reproducibility;
- artifact hashes;
- signing assumptions;
- CI permissions;
- GitHub Actions pinning;
- provenance/attestation;
- secrets;
- release-only code paths.

Generate an SBOM where tooling supports it.

### Domain I — Recovery, backup, incident response, and forensics

Audit:

- database corruption;
- key loss;
- partial migrations;
- failed export;
- interrupted nuke;
- failed startup;
- unavailable radios;
- revoked permissions;
- service crashes;
- app upgrade/downgrade;
- stale configuration;
- malformed external input.

Define what operators can recover, what is intentionally unrecoverable, and what evidence survives each event.

### Domain J — Documentation, marketing, and public claims

Audit every public-facing assertion against source and evidence.

Examples:

- "no network calls";
- "secure erase";
- "encrypted";
- "detects IMSI catchers";
- "within N meters";
- "works in background";
- "no tracking";
- "offline";
- "anonymous";
- "military grade".

Every absolute claim must be either:

1. supported;
2. narrowed;
3. qualified;
4. removed.

---

## 9. Anti-toy search patterns

Static searches are not sufficient, but they are useful discovery accelerators.

Search globally for suspicious constructs and language:

### Placeholder / simulation

```text
TODO
FIXME
HACK
TEMP
placeholder
mock
fake
simulate
simulation
stub
not implemented
future
rough estimate
approximation
best effort
```

Context decides whether each is a finding.

### False precision / unsupported inference

```text
accuracy
precise
confirmed
exact
meters
within
likely
always
never
100%
confidence
military
secure erase
anonymous
no network
```

### Fragile runtime behavior

```text
GlobalScope
CoroutineScope(
launch {
delay(
Timer
Handler.postDelayed
while (true)
while(isActive)
AlarmManager
setExact
WakeLock
START_STICKY
fallbackToDestructiveMigration
catch (e: Exception)
catch (_: Exception)
```

Every asynchronous path must answer: who owns it, who cancels it, how many can exist, and what happens after process death?

### Dangerous platform/data surfaces

```text
android:exported="true"
FileProvider
content://
file://
MODE_WORLD
externalFiles
Environment.
Log.
println(
SharedPreferences
DataStore
KeyStore
Cipher
MessageDigest
WebView
addJavascriptInterface
Intent(
startActivity
sendBroadcast
```

The presence of a construct is not itself a defect. It marks an audit surface.

---

## 10. Five recursive pre-fix passes

Every domain performs all five passes before significant fixes.

### Pass 1 — Source truth

Inventory actual implementation, call graph, configuration, tests, API gates, privilege requirements, and user-visible claims.

Output:

- implementation map;
- authority map;
- unverified assumptions;
- candidate findings.

### Pass 2 — Adversarial failure

Attempt to break assumptions using realistic failure conditions:

- malformed input;
- stale input;
- duplicate input;
- adversarial-but-valid radio environments;
- process death;
- concurrency;
- low memory;
- storage full;
- permission changes;
- clock/time changes;
- no network;
- hostile network;
- missing sensors;
- missing privilege;
- device/OEM variance.

### Pass 3 — Cross-system consequences

Trace findings across boundaries.

Examples:

```text
wrong RSSI semantics
    -> wrong range
    -> wrong UI circle
    -> wrong operator conclusion
    -> wrong threat interpretation
```

or:

```text
stale settings cache
    -> privacy mode transition missed
    -> persistence occurs unexpectedly
    -> export contains data operator expected to remain ephemeral
```

### Pass 4 — Verification-first design

For each accepted finding, define the RED condition before choosing the fix.

Preferred order:

1. reproduce;
2. pin with test/evidence;
3. implement smallest root-cause correction;
4. rerun narrow verification;
5. rerun cross-system verification.

### Pass 5 — Minimal synthesis

Choose the smallest coherent fix that removes the root cause without weakening capability for benchmark optics.

Forbidden:

- disabling detectors to reduce battery use;
- suppressing warnings rather than fixing state;
- widening uncertainty only to hide a bad estimator;
- deleting evidence to make tests pass;
- converting a security failure into silent fallback;
- adding polling to solve state propagation;
- moving work to another thread without bounding it;
- marketing-language edits as substitutes for code fixes when code is wrong.

---

## 11. Five recursive post-fix passes

### Post 1 — Diff/minimality

- exact changed paths;
- ownership boundaries;
- unexpected generated files;
- temporary tooling removed;
- no hidden cross-domain edits.

### Post 2 — Lifecycle/concurrency/state

Re-evaluate:

- cancellation;
- process death;
- duplicate jobs;
- stale cache;
- races;
- restart behavior;
- state ownership.

### Post 3 — Performance/resource behavior

Verify the fix did not introduce:

- polling;
- unbounded queues;
- per-packet coroutines;
- unlimited histories;
- high-frequency disk reads;
- unnecessary wakeups;
- whole-list copies;
- quadratic hot loops.

### Post 4 — Security/privacy/truthfulness

Verify:

- capability was not silently weakened;
- privacy boundaries remain intact;
- exports/logs remain safe;
- UI language matches evidence;
- no new network contact;
- no privilege escalation;
- no secret/identifier leakage.

### Post 5 — Verification/admission

Run the strongest applicable evidence gates and update finding state.

A finding cannot move directly from `SOURCE_SUPPORTED` to `OPERATIONALLY_ADMITTED`.

---

## 12. Verification pyramid

### Level 0 — static/source

- compile/static analysis;
- lint;
- dependency inspection;
- manifest review;
- source-based proofs.

### Level 1 — unit

- deterministic logic;
- boundary conditions;
- property tests where useful;
- malformed/hostile input.

### Level 2 — integration

- Room/DataStore;
- service/repository boundaries;
- IPC;
- migrations;
- crypto lifecycle;
- export round trips;
- process/lifecycle simulation where tooling permits.

### Level 3 — emulator

Use for:

- UI behavior;
- lifecycle;
- permission flow;
- navigation;
- configuration changes;
- process recreation;
- integration behavior.

Do **not** use emulator data as battery/radio/thermal truth.

### Level 4 — physical device

Use controlled scenarios for:

- battery;
- wakeups;
- thermal behavior;
- real radio scan cadence;
- memory pressure;
- OEM behavior;
- background restrictions;
- scanner latency.

### Level 5 — calibrated bench / field

Required for physical claims such as:

- ranging;
- RF thresholds;
- detection sensitivity;
- false-positive/negative characterization;
- GNSS anomaly behavior;
- ultrasonic detection behavior.

### Level 6 — adversarial / red-team

Attempt realistic abuse against owned/authorized test environments and synthetic captures.

### Level 7 — independent operational review

Independent reviewer checks:

- evidence completeness;
- residual risk;
- claim wording;
- exact SHA;
- test provenance;
- unresolved blockers.

---

## 13. Measurement admission standard

Any numerical or categorical measurement used operationally must define:

```text
observable inputs
preprocessing
model / equation / classifier
calibration source
uncertainty
sample count
observation age
hardware/API dependencies
known confounders
fallback behavior
```

If those are unavailable, the system should expose a weaker truthful output.

Example:

```text
BAD:
"Distance: 4.2 m"

ACCEPTABLE WHEN UNCALIBRATED:
"Nearby — signal strengthening"

BETTER WHEN CALIBRATED:
"Estimated 4 m; likely range 2–7 m; medium confidence"
```

Calibration quality is itself evidence.

---

## 14. Security invariants

The workflow should eventually promote a reviewed set of invariants into repository `SECURITY.md` after owner approval.

Candidate invariants:

1. Ephemeral mode does not intentionally persist detection history.
2. Persistent history remains encrypted at rest using the admitted database/key architecture.
3. Destructive migration is never an implicit upgrade recovery path.
4. Nuke prioritizes cryptographic key destruction; overwrite behavior is never represented as guaranteed flash sanitization.
5. Sensitive exports are user-initiated and local by default.
6. Export location precision and identifier inclusion are explicit policy choices.
7. No undeclared network endpoints exist.
8. Scanner drivers cannot silently grant themselves additional Android privilege.
9. UI state cannot manufacture runtime authority.
10. LLM output cannot manufacture verified detection evidence.
11. Cross-process liveness is not inferred from process-local static state.
12. Release artifacts correspond to reviewed source and build provenance.

These are candidate policy statements, not yet owner-approved scanner exclusions or accepted-risk decisions.

---

## 15. Privacy invariants

1. Collect the minimum sensitive data required for enabled features.
2. Precise location is not collected merely because coarse location would suffice.
3. Background location is justified per feature and platform requirements.
4. Sensitive identifiers are not logged casually.
5. Export/share grants are scoped and revocable where platform semantics permit.
6. Player/game data, if later integrated, remains architecturally separable from surveillance-world evidence.
7. No analytics/SDK is admitted without explicit data-flow review.
8. Third-party SDK behavior counts as product behavior.

---

## 16. Deep security scan lane

Conventional security review remains mandatory but is only one lane.

When executing the workflow in a full development environment, run a repository-wide deep security scan using the dedicated Codex Security deep-scan workflow when available.

Expected outputs:

- threat map;
- validated vulnerabilities;
- attack-path reasoning;
- coverage artifact;
- canonical findings;
- sealed report.

Do not treat "0 scanner findings" as closure of the Anti-Toy workflow. A security scanner will not necessarily flag bad ranging science, fake accuracy, misleading UI, dead feature authorities, or unsupported public claims.

---

## 17. Multi-lane execution architecture

After inventory, implementation work should be divided into non-overlapping lanes.

Suggested R1 lanes:

### Lane A — Measurement / detection / ranging

Owns:

- detection science;
- signal semantics;
- ranging/localization;
- calibration;
- false-positive/negative evidence;
- threat-evidence boundaries where measurement-specific.

### Lane B — Runtime / resilience / platform lifecycle

Owns:

- services;
- scanner lifecycle;
- watchdog/recovery;
- power;
- process boundaries;
- wakeups;
- cancellation;
- capability admission.

### Lane C — Data / crypto / privacy

Owns:

- repository/database;
- DataStore;
- migrations;
- retention;
- crypto/key lifecycle;
- export backend;
- ephemeral semantics.

### Lane D — Network / IPC / build / supply chain

Owns:

- network endpoints;
- TLS;
- Tor/external integrations;
- Android component exposure;
- CI;
- dependencies;
- build provenance;
- release/signing posture.

### Lane E — UI / operator truth / docs

Owns:

- capability-aware UI;
- evidence/uncertainty presentation;
- accessibility;
- public claims;
- README/docs accuracy;
- user-facing destructive/privacy controls.

### Integrator / judge lane

Owns no feature implementation by default.

Responsibilities:

- finding ledger;
- ownership arbitration;
- cross-lane handoffs;
- duplicate authority detection;
- evidence review;
- final exact-SHA closure;
- claim/admission matrix.

No lane may fix a foreign-domain problem by silently editing foreign-owned architecture.

---

## 18. Cross-lane handoff contract

Every handoff records:

```yaml
from_lane:
to_lane:
finding_id:
why_cross_lane:
required_contract:
source_paths:
consumer_paths:
red_condition:
acceptance_evidence:
blocking: true|false
```

A handoff is not complete because it appears in a PR description.

The receiving lane must either:

- accept and implement;
- reject with evidence;
- narrow the contract;
- explicitly defer with residual-risk status.

---

## 19. Findings ledger statuses

Canonical status progression:

```text
CANDIDATE
    -> VALIDATED
    -> TEST_PINNED
    -> IN_PROGRESS
    -> FIXED_SOURCE
    -> VERIFIED
    -> OPERATIONALLY_ADMITTED
```

Alternate terminal states:

```text
NOT_A_FINDING
DUPLICATE
ACCEPTED_RISK
DEFERRED_BLOCKED
OUT_OF_SCOPE_CONFIRMED
```

`ACCEPTED_RISK` and `OUT_OF_SCOPE_CONFIRMED` require explicit owner authority.

Agents/models cannot self-authorize those states.

---

## 20. Operational admission ladder

Features should carry an admission tier:

### T0 — Concept

Claim exists; implementation/evidence absent or incomplete.

### T1 — Source implemented

Implementation exists and compiles.

### T2 — Unit verified

Core deterministic logic has meaningful tests.

### T3 — Integration verified

Major internal boundaries tested.

### T4 — Device verified

Real Android hardware behavior demonstrated for applicable claims.

### T5 — Calibrated / bench verified

Physical/measurement claim tested against controlled ground truth.

### T6 — Adversarial verified

Relevant hostile/failure conditions tested.

### T7 — Operationally admitted

Independent evidence review supports the exact user-facing claim at the exact SHA.

The UI/docs may expose a feature below T7, but wording must match its actual tier.

---

## 21. Mandatory artifacts

The full campaign produces four canonical artifacts.

### 1. Anti-Toy Findings Ledger

Every validated and unresolved finding.

### 2. OPSEC Threat & Failure Model

Assets, adversaries, trust boundaries, failure modes, privacy consequences, recovery expectations.

### 3. Capability / Claim / Evidence Matrix

For every major user-visible capability:

```text
capability
required hardware/API/privilege
implementation path
claim wording
admission tier
evidence
known limitations
```

### 4. Operational Admission Report

At a pinned SHA:

- admitted features;
- partially admitted features;
- blocked features;
- unresolved S3/S4 findings;
- quantitative claims supported/unsupported;
- device/bench coverage;
- security coverage;
- residual risks;
- release recommendation.

---

## 22. Stop-ship conditions

Unless explicitly overridden by the owner with documented risk acceptance, do not call a release operationally admitted while any of the following remain:

- open S4 finding;
- reproduced high-impact privacy persistence violation;
- destructive migration/data-loss path under supported upgrade;
- key-management path that can silently expose persisted sensitive data;
- unreviewed exported Android component exposing sensitive operations/data;
- known arbitrary network contact inconsistent with documented product behavior;
- user-facing physical measurement known to be materially fabricated or mislabeled;
- critical threat label produced from evidence known to be insufficient;
- build artifact cannot be tied back to reviewed source;
- hidden test/benchmark configuration disables core detection functionality;
- exact-head CI required for the release is red.

S3 findings require explicit disposition and release rationale.

---

## 23. Performance truth rules

Never claim:

- battery improvement percentage;
- CPU improvement percentage;
- RAM improvement percentage;
- thermal improvement;
- radio efficiency improvement;
- detection-latency improvement;

without comparable before/after evidence under controlled scenarios.

Source-level statements such as "removed an O(n^2) loop" or "removed a recurring one-minute exact alarm" are acceptable when directly supported by source.

Do not translate them into numerical device claims without measurement.

---

## 24. Failure injection matrix

Each applicable subsystem should be tested against a subset of:

```text
permission revoked while active
Bluetooth toggled off/on
Wi-Fi unavailable
location unavailable
GPS degraded
network removed
Tor unavailable
storage full
low memory
process killed
service killed
reboot
upgrade
unsupported downgrade
corrupt database
missing key
export destination failure
malformed scan data
rapid duplicate advertisements
extreme device density
clock jump
configuration rotation
background restriction
battery saver
thermal throttling
slow device
no NFC
missing microphone
missing privileged API
```

Expected behavior must be documented before calling the failure test "pass."

---

## 25. Device matrix

At minimum maintain:

### Constrained commodity Android

The project's low-end/older Moto-class target is valuable because it exposes bad resource assumptions.

### Modern mainstream Android

Validates current API behavior and modern background/privacy restrictions.

### Privileged/system/OEM target when available

Validates features whose semantics materially differ under privileged installation.

Do not hard-code behavior to device marketing names when capability detection can decide correctly.

---

## 26. ClaimGuard-style public-claim audit

Every public statement becomes a proposition to verify.

For each claim:

```yaml
claim:
surface: README|UI|docs|store|website
scope:
required_evidence:
actual_evidence:
status: SUPPORTED|NARROW|QUALIFY|REMOVE
```

Especially audit absolutes:

```text
no network calls
never uploads
anonymous
encrypted
securely erased
fully offline
all detections
accurate distance
real time
background continuously
military grade
```

The campaign is complete only when code and public description describe the same product.

---

## 27. Campaign sequencing

### Phase 0 — Freeze and inventory

- choose audited SHA;
- inventory source tree;
- inventory build flavors;
- inventory permissions/components/endpoints;
- inventory current tests;
- inventory claims;
- establish device matrix;
- establish security policy draft questions.

### Phase 1 — Independent discovery

Run domain audits independently enough to reduce correlated blind spots.

No fixes yet except critical containment if actively dangerous.

### Phase 2 — Reconcile findings

- deduplicate;
- validate;
- assign severity/confidence;
- assign lane;
- define RED conditions;
- order dependencies.

### Phase 3 — S4/S3 hardening

Fix critical/high findings first.

### Phase 4 — Measurement/truth hardening

Eliminate false precision, unsupported threat claims, and uncalibrated outputs.

### Phase 5 — Resilience/performance hardening

Fix lifecycle, resource, recovery, and constrained-device issues.

### Phase 6 — UX/public truth convergence

Update UI/docs only after underlying contracts stabilize.

### Phase 7 — Full verification

- CI;
- emulator;
- physical device;
- controlled bench;
- deep security scan;
- adversarial scenarios;
- claim audit.

### Phase 8 — Exact-SHA admission closure

Generate canonical artifacts and release recommendation.

---

## 28. CAPT integration target

When CAPT is stable enough to run this campaign, CAPT should own the durable governance state rather than leaving it in model memory.

Suggested mapping:

```text
MISSION
    OPSEC / Anti-Toy Hardening

TASKS
    domain audits
    finding validation
    remediation
    verification
    admission

EVIDENCE
    source excerpts
    tests
    CI runs
    device measurements
    bench captures
    external standards

CLAIMS
    feature claims
    public claims
    admission claims

VERIFICATION
    test domain
    measurement domain
    security domain
    release domain
```

The model's job becomes reasoning and engineering.

CAPT's job becomes preserving authority, state, evidence, transitions, handoffs, and claim discipline.

---

## 29. Required first execution output

Before changing production code under this workflow, the first execution should produce:

1. exact audited SHA;
2. full source/package inventory;
3. permissions/components/network endpoint manifest;
4. feature/capability inventory;
5. claim inventory;
6. first-pass findings ledger;
7. threat/failure model draft;
8. lane assignment;
9. prioritized S4/S3/S2 queue;
10. explicit list of areas where device/bench evidence is still absent.

This prevents the campaign from immediately disappearing into attractive code fixes while leaving whole surfaces unaudited.

---

## 30. Review gate

This document defines the proposed workflow only.

Before execution:

- owner reviews scope;
- owner confirms or revises stop-ship policy;
- owner confirms intended operational threat model;
- root/nested `SECURITY.md` policy is reviewed separately before any scanner exclusions or accepted-risk decisions are created;
- Ranging & Localization Plane design is linked as the authority for distance/localization hardening;
- MAXSTATS current branches are converged or explicitly frozen so this campaign begins from a deliberate baseline.

After approval, create the execution plan and begin Phase 0 from a pinned repository SHA.
