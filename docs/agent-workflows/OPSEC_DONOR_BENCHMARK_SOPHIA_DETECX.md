# OPSEC Donor Benchmark — SØPHIA / Detecx

**Status:** NORMATIVE DONOR/BENCHMARK ADDENDUM  
**Campaign:** OPSEC Anti-Toy 3× Operational Hardening  
**Date:** 2026-08-18

## Purpose

SØPHIA/Detecx is a commercial passive signal-awareness product whose public feature set provides useful competitive requirements for the Flock-You Anti-Toy campaign.

This document does **not** authorize source-code reuse.

As of this review, no matching public SØPHIA/Detecx source repository was located. Treat public product behavior and feature descriptions as design/benchmark evidence only.

Public references reviewed:

- https://detecx.io
- https://detecxceo.gumroad.com/l/qospds
- https://detecxceo.gumroad.com/l/smurbl
- https://detecxceo.gumroad.com/l/qpkvm

---

## Public feature set relevant to the campaign

SØPHIA publicly describes:

- passive Wi-Fi observation;
- optional BLE observation;
- local/offline-first operation;
- transient-vs-static device logging;
- live radar/HUD visualization;
- real-time signal events;
- timeline/history;
- manufacturer/OUI classification;
- priority/fleet/infrastructure highlighting;
- environmental baselining and anomaly/change detection;
- CSV/JSON export;
- AI-oriented environmental debrief export;
- cross-time environment comparison;
- signal-density review;
- Android field deployment;
- older material describing rough distance estimation/risk scoring;
- operator/multi-node positioning in some public material.

Marketing claims are not treated as independent verification.

---

# Required Anti-Toy extensions

The campaign must explicitly answer the following in R1/R2/R3.

## 1. Environmental baseline

R1:

- Does Flock-You maintain a meaningful per-environment baseline?
- Which entities/signatures are expected here?
- Are expected presence, time-of-day, signal distribution, and recurrence represented?

R2:

- Can a crowded/new environment make the baseline generate false threat alerts?
- Can stale data make removed devices appear normal forever?
- Can an attacker/community submission poison a baseline?
- Does clock drift or travel corrupt baseline semantics?

R3 target:

```text
CHANGE != THREAT
```

Represent environmental change as evidence first.

Suggested change types:

```text
NEW
MISSING
RETURNED
MOVED
BEHAVIOR_CHANGED
SIGNAL_DISTRIBUTION_CHANGED
PROTOCOL_CHANGED
DENSITY_CHANGED
```

---

## 2. Temporal entity state

Do not stop at binary transient/static.

Target state vocabulary:

```text
UNKNOWN
TRANSIENT
RECURRING
PERSISTENT
MOBILE
MOVED
STALE
REMOVED
DISPUTED
```

State transitions require timestamps, provenance, and confidence.

This becomes authoritative input to both the operator map and future game world graph.

---

## 3. Environmental debrief contract

Create or validate a structured local field debrief suitable for:

- operator review;
- CAPT evidence ingestion;
- optional downstream LLM reasoning;
- session-to-session comparison;
- incident/research archiving.

Conceptual schema:

```yaml
session:
scanner_capabilities:
device_profile:
privacy_mode:
location_policy:
observation_counts:
new_entities:
missing_entities:
changed_entities:
anomaly_candidates:
ranging_estimates:
world_graph_transitions:
scanner_health:
limitations:
provenance:
```

LLM output must never promote itself to scanner evidence or verified world state.

---

## 4. Independent sensor-plane admission

Wi-Fi and BLE should be independently governable where architecture permits.

Audit:

- persisted settings admission before start;
- hidden scan-mode escalation;
- battery/thermal policy;
- lifecycle divergence;
- UI indication of active sensor planes;
- disabled-plane health/watchdog false alarms;
- capability absence.

This is a field-instrument requirement, not merely a battery toggle.

---

## 5. Radar presentation truth

A radar/HUD representation is valuable but must not imply unsupported geometry.

Allowed truthful inputs include:

- signal strength;
- trend;
- observation age;
- protocol;
- detector tier;
- confidence;
- calibrated range interval;
- inferred probability region.

Disallowed shortcut:

```text
RSSI bucket -> precise radial meters
```

The Ranging & Localization Plane remains authority for absolute spatial estimates.

---

## 6. Priority/fleet/infrastructure classification

Manufacturer/OUI/broadcast metadata can support a candidate but is not automatically functional attribution.

Use detector tiers:

```text
D0 raw observation
D1 pattern match
D2 correlated candidate
D3 high-confidence family attribution
D4 verified physical/entity attribution
```

R2 must specifically test benign hardware sharing vendors/chipsets with surveillance/fleet hardware.

---

## 7. Session comparison / environmental change engine

The hardened backend should eventually answer deterministically:

- what appeared since session A;
- what disappeared;
- what moved;
- what changed behavior;
- what became stale;
- what gained/lost verification;
- what changed only because scanner capability changed.

This is a key world-graph primitive for the future game because world changes can become discovery/events without making raw player data the product.

---

## 8. Distance/risk claims — outperform, do not imitate

Older public SØPHIA material describes distance estimation and risk scoring.

Treat these as competitive requirements to exceed, not authority for implementation.

Distance:

- calibrated model;
- uncertainty interval;
- sample quality;
- TX-power provenance where available;
- receiver/transmitter profile;
- physical bench evidence;
- fallback to relative trend/proximity when absolute distance is unsupported.

Risk:

Separate at least:

```text
observation confidence
identity confidence
behavioral anomaly
persistence/following evidence
technical capability
potential impact
operator concern
```

Do not hide these dimensions behind one impressive but uninterpretable score.

---

## 9. Multi-node future-readiness

Do not implement distributed sensing merely because a competitor markets nodes.

But avoid architecture that makes later cooperative evidence impossible.

Future node contract must account for:

- authenticated node identity;
- observation provenance;
- clock/time uncertainty;
- node location uncertainty;
- device calibration differences;
- untrusted peer observations;
- bounded sync;
- privacy policy;
- conflict preservation;
- optional local/offline federation.

This becomes valuable for both cooperative field work and future game parties.

---

# Borrow / outperform / reject matrix

| Donor concept | Decision | Inversion standard |
|---|---|---|
| passive Wi-Fi/BLE | KEEP / benchmark | richer raw observation preservation |
| transient/static | OUTPERFORM | temporal state machine + uncertainty |
| baseline/anomaly | BORROW | change != threat; provenance/confounders |
| radar HUD | BORROW UX | no fabricated geometry |
| timeline | BORROW/extend | observation + entity-state + verification history |
| OUI/manufacturer | KEEP/constrain | pattern match is not functional identity |
| priority/fleet | BORROW need | D0-D4 evidence tiers |
| independent BLE | BORROW | governed sensor loadout |
| CSV/JSON | KEEP/extend | privacy-aware deterministic export |
| AI debrief | BORROW | structured evidence; LLM downstream only |
| local/offline | KEEP/prove | explicit network manifest/runtime evidence |
| distance | OUTPERFORM | calibrated probabilistic ranging |
| risk score | DECOMPOSE | inspectable evidence dimensions |
| multi-node | RESEARCH | authenticated evidence federation |

---

## Game-backend consequence

The most strategically valuable donor idea is **environment-over-time**, not the radar skin.

A hardened temporal world graph gives the future game:

```text
known persistent infrastructure -> seeded boss
new verified infrastructure -> first-discovery event
moved infrastructure -> migration/update event
removed infrastructure -> retirement event
new recurring signature -> emerging encounter candidate
density shift -> environmental event
```

The game may consume these sanitized transitions.

Game rewards, faction state, sponsorship, player votes, and encounter rarity remain unable to mutate scanner evidence authority.

---

## Campaign rule

During full R1/R2/R3 execution, compare each SØPHIA capability against actual Flock-You source before creating work.

For every donor capability choose exactly one:

```text
ALREADY_STRONGER
PRESENT_BUT_TOY_GRADE
MISSING_AND_VALUABLE
NOT_APPLICABLE
REJECT
```

Only `PRESENT_BUT_TOY_GRADE` and `MISSING_AND_VALUABLE` enter the remediation queue.
