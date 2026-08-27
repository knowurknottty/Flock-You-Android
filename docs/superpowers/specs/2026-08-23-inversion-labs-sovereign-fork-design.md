# Inversion Labs Sovereign Fork Design

## Goal
Create an Inversion Labs-maintained derivative of Flock-You that preserves upstream attribution while minimizing supply-chain and runtime trust, with emphasis on preventing npm-style dependency compromise and malicious transitive updates.

## Approved Sovereignty Definition
Use **Option B: source sovereignty with a hard trust policy**. The production privacy build must have no runtime dependency on remote services, no dynamic dependency resolution, no remote model download, and no unpinned third-party code ingress. Foundational primitives may remain when they are audited, vendored or mirror-pinned, hash-verified, license-accounted, and wrapped behind Inversion Labs-controlled interfaces.

## Attribution and Licensing
Preserve the original project name and author attribution in NOTICE/README history, identify Inversion Labs as the maintainer of the derivative, and retain all upstream license obligations. Before redistribution, reconstruct and verify the authoritative upstream license file because the current branch README advertises MIT while the checked-out tree lacks a LICENSE file.

## Threat Model
Primary threat: software-supply-chain compromise comparable to npm ecosystem incidents: dependency takeover, malicious transitive release, repository compromise, poisoned build plugin, mutable artifact, dependency-confusion, build-time downloader, or compromised remote content loaded after install. Secondary threats include silent runtime telemetry or remote code/data substitution, model replacement, malicious map/signature/OUI updates, and build drift between audited source and shipped APK.

## Architecture
The app becomes offline-first and self-contained. Production artifacts contain all models, signatures, OUI data, map data needed for supported offline operation, and fixed documentation/resources. Network-backed features are either removed from the privacy build or moved into a separately permissioned updater/tooling path that never executes inside the production app trust boundary.

Third-party dependencies are classified into four classes: REMOVE, REPLACE, VENDOR/PIN, or PLATFORM. Every retained artifact must have exact version, source URL/repository, immutable revision where available, SHA-256, license, rationale, transitive-dependency inventory, and update-review owner recorded in a machine-readable manifest.

## Build Trust Boundary
Release builds must support an offline verification mode where dependency resolution cannot contact Google Maven, Maven Central, Gradle Plugin Portal, JitPack, GitHub, Hugging Face, IEEE, OpenStreetMap, Tor Project, or any other external host. A checked-in or Inversion Labs-controlled dependency vault/mirror supplies reviewed artifacts by digest. Lockfiles and dependency verification metadata are mandatory. Build tasks that download mutable content are forbidden in release assembly.

## Runtime Trust Boundary
The hardened privacy flavor removes INTERNET permission and all direct HTTP/SOCKS clients. It must not contain runtime URLs for model downloads, OUI refresh, map tiles, Tor/IP checks, DNS probes, GitHub update links, or remote configuration. Any future network-capable companion must be a separate package/process with explicit user action and signed/hash-verified transfer into the offline app.

## Dependency Reduction Priorities
First eliminate the highest-risk avoidable surfaces: JitPack, dynamic build downloads, Google Play Services location, ML Kit/AICore, Orbot coupling, remote OUI/model/map fetches, and unnecessary HTTP stacks. Then replace Gson/Guava/Hilt/other convenience dependencies where the replacement is small and security-positive. Retain Android platform/AndroidX or vetted crypto/database/inference primitives only where rewriting would increase risk; wrap them behind Inversion Labs interfaces and pin/vault their exact source/artifacts.

## AI/Model Boundary
Flock edge models are produced by the separate governed MLX-LM training pipeline. The Android repo consumes only frozen model artifacts with provenance receipts and SHA-256 allowlists. No Android code may fetch or select a model remotely. Model format/runtime support must be architecture-compatible and verified against the frozen evaluation corpus before integration.

## CAPT Governance
Major dependency admission/removal, network-surface changes, updater design, model artifact promotion, and release candidate promotion must pass Inversion Labs CAPT review. Qwen3.8 may be used only through governed CAPT for adversarial review. CAPT/Qwen output is advisory evidence, not authority to bypass tests, hashes, licenses, or fail-closed gates.

## Verification Gates
Required gates include: dependency graph inventory; license inventory; source/artifact hashing; transitive dependency closure; offline Gradle build; network-literal scan; Android manifest permission scan; SBOM generation; reproducibility comparison; unit/instrumented tests; malicious-dependency regression fixtures; model hash verification; APK content inspection; and a final clean-room build with outbound network denied.

## Non-Goals
Do not rewrite cryptographic algorithms, database engines, Android framework code, Kotlin compiler/toolchain, or every AndroidX component merely for ownership optics. Do not publish or deploy during this program. Do not alter unrelated dirty release-workflow files.
