# Inversion Labs Sovereign Fork Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the Flock-You fork into an attribution-preserving Inversion Labs derivative with a sharply reduced and hash-governed software supply chain, offline production runtime, and reproducible dependency-controlled builds.

**Architecture:** Keep security-critical foundational primitives only where replacement would increase risk, but place all retained third-party code behind Inversion Labs-controlled interfaces and immutable provenance. Remove runtime remote content/services from the hardened privacy flavor, eliminate mutable build downloads, introduce dependency verification/vaulting, and require CAPT-governed evidence before dependency or model promotion.

**Tech Stack:** Android/Kotlin, Gradle 8/AGP, AndroidX/Compose, MLX-LM-produced edge models, GitHub Actions, Inversion Labs CAPT, SHA-256 provenance, SBOM tooling.

**Spec:** `docs/superpowers/specs/2026-08-23-inversion-labs-sovereign-fork-design.md`

## Global Constraints
- Preserve original author/project attribution and all license obligations.
- Hardened privacy production flavor must have no `INTERNET` permission and no runtime network clients.
- No dynamic/mutable dependency resolution in release builds.
- Every retained third-party artifact requires exact version/revision, SHA-256, license, rationale, transitive closure, and update owner.
- No remote model/OUI/map/signature downloads in the hardened app.
- Do not rewrite cryptographic primitives merely for ownership optics.
- Models enter Android only after governed training/eval/export verification and hash promotion.
- Qwen3.8 is advisory and may be used only through Inversion Labs CAPT.
- Fail closed on dependency, license, CAPT, model, reproducibility, and offline-build gates.
- Do not publish/deploy or disturb unrelated dirty release-workflow files.

---

### Task 1: Establish attribution, license truth, and fork identity

**Files:**
- Create: `NOTICE.md`
- Create or restore: `LICENSE`
- Modify: `README.md`
- Create: `docs/provenance/upstream.json`

**Interfaces:**
- Consumes: upstream repository identity and immutable source commit.
- Produces: authoritative attribution/license record consumed by release checks.

- [ ] Verify the original repository's license at an immutable upstream commit and record repository URL, commit SHA, license type, original author/maintainer, fork point, and verification date in `docs/provenance/upstream.json`.
- [ ] Add the exact upstream license text as required by that license; fail the task if the README claim and upstream file disagree.
- [ ] Add `NOTICE.md` that clearly credits MaxwellDPS/Flock-You-Android and states that Inversion Labs maintains a derivative focused on supply-chain sovereignty and local processing.
- [ ] Update README branding without erasing project lineage or upstream copyright/license notices.
- [ ] Add a test/script `tools/verify_attribution.py` that fails if LICENSE, NOTICE, or required provenance keys disappear.
- [ ] Run the attribution verifier and commit only after it passes.

### Task 2: Freeze the dependency graph and generate a trust manifest

**Files:**
- Create: `security/dependency-policy.json`
- Create: `security/dependency-manifest.json`
- Create: `tools/dependency_inventory.py`
- Modify: `gradle/verification-metadata.xml`
- Modify: Gradle dependency-lock files generated for all release configurations.

**Interfaces:**
- Produces: canonical dependency IDs, hashes, licenses, classifications (`REMOVE`, `REPLACE`, `VENDOR_PIN`, `PLATFORM`) and transitive closure.

- [ ] Implement `tools/dependency_inventory.py` to parse Gradle dependency reports and emit stable JSON records containing group, artifact, version, configuration, direct/transitive status, repository origin when known, and expected digest.
- [ ] Enable Gradle dependency locking for release-relevant configurations and generate locks from the currently verified graph.
- [ ] Generate Gradle dependency verification metadata with SHA-256 checksums and reject unverified artifacts.
- [ ] Populate `security/dependency-policy.json` with explicit classifications for every direct dependency, starting with JitPack artifacts, Google Play Services, ML Kit/AICore, Orbot-related code, OkHttp, Gson, Guava, Hilt, Room, SQLCipher, MediaPipe/LiteRT, AndroidX, and test-only dependencies.
- [ ] Add a CI/local check that fails on a new dependency absent from the policy file or any `+`, `latest`, snapshot, mutable Git branch/tag, or unverified repository source.
- [ ] Commit the frozen graph separately from implementation replacements.

### Task 3: Eliminate mutable build-time network ingress

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `tools/assert_offline_build_inputs.py`

**Interfaces:**
- Consumes: Task 2 dependency policy.
- Produces: release assembly with no mutable content download tasks.

- [ ] Write a failing check that detects JitPack and release tasks that open network URLs, including `updateOuiDatabase`.
- [ ] Remove JitPack from release dependency resolution after replacing/vaulting its artifacts.
- [ ] Remove the automatic OUI network update from all release assembly dependency chains; bundled OUI data must be promoted separately by hash.
- [ ] Forbid Gradle tasks used by release assembly from using `URL`, `HttpURLConnection`, curl/wget, git clone, package installers, or other network fetch mechanisms.
- [ ] Run a release task graph inspection and verify it contains no downloader/update task.
- [ ] Verify `./gradlew --offline` can resolve the frozen build from the approved local cache/vault before proceeding.

### Task 4: Create the Inversion dependency vault and admission workflow

**Files:**
- Create: `vendor/README.md`
- Create: `vendor/manifest.json`
- Create: `tools/vendor_verify.py`
- Create: `docs/workflows/dependency-admission.md`

**Interfaces:**
- Produces: immutable local artifact/source vault with CAPT-reviewable admission records.

- [ ] Define one manifest record per vendored artifact: upstream URL, immutable source revision, artifact filename, SHA-256, license, transitive inputs, build recipe if source-built, reason retained, and CAPT receipt reference.
- [ ] Implement `vendor_verify.py` to hash every vendored file and fail on missing, extra, or changed bytes.
- [ ] Define admission as: acquire source/artifact outside release build -> hash -> license review -> dependency diff -> CAPT/Qwen adversarial review -> local test build -> manifest update -> human approval -> commit.
- [ ] Ensure release builds consume only repository-local or Inversion-controlled verified artifacts, never an unreviewed upstream mutable endpoint.
- [ ] Add a negative test that mutates one vendored byte and proves verification fails.

### Task 5: Define and enforce the hardened offline privacy flavor

**Files:**
- Modify: `app/build.gradle.kts`
- Add flavor/source-set manifest under `app/src/privacy/AndroidManifest.xml`
- Create: `tools/verify_privacy_apk.py`

**Interfaces:**
- Produces: a production privacy APK incapable of ordinary Internet networking by manifest capability.

- [ ] Add a `privacy`-hardened production variant or equivalent source-set boundary without breaking existing development/system/OEM modes.
- [ ] Remove `android.permission.INTERNET` from the hardened manifest using manifest merge rules.
- [ ] Remove package visibility and UX flows whose only purpose is Orbot installation/launch from the hardened flavor.
- [ ] Implement APK verification that inspects the merged manifest and fails if INTERNET is present or any known network service component is packaged.
- [ ] Add an instrumented/runtime test proving attempts to create outbound app networking fail in the privacy build.
- [ ] Preserve other sensor permissions only where tied to an implemented detection feature and document their purpose.

### Task 6: Remove runtime remote service/content dependencies

**Files:**
- Modify/remove: `app/src/main/java/com/flockyou/network/*`
- Modify: `app/src/main/java/com/flockyou/config/NetworkConfig.kt`
- Modify: `app/src/main/java/com/flockyou/data/oui/OuiDownloader.kt`
- Modify: AI settings/download code
- Modify: map configuration/code
- Create: `tools/scan_runtime_network_surface.py`

**Interfaces:**
- Produces: zero remote model/OUI/map/Tor/IP/DNS/update endpoints in hardened runtime.

- [ ] Add a scanner that flags `http://`, `https://`, SOCKS/proxy construction, `HttpURLConnection`, OkHttp client creation, WebView remote loads, and Android intents opening dependency/update URLs in production source/resources.
- [ ] Replace OUI downloading with read-only bundled OUI database loading plus provenance/version display.
- [ ] Replace remote model download UI with bundled/imported model verification against an allowlisted SHA-256 manifest.
- [ ] Replace remote map tiles with an offline map provider or make maps unavailable until an approved offline pack is imported and verified.
- [ ] Remove Tor/IP/DNS connectivity-test logic from hardened runtime rather than silently falling back to direct networking.
- [ ] Remove GitHub/update links from executable update flows; static attribution/resource text may remain if it cannot trigger hidden fetches.
- [ ] Make the runtime-network scanner a blocking verification gate.

### Task 7: Replace high-risk convenience dependencies in descending value order

**Files:**
- Modify Gradle dependencies and focused app modules per replacement.
- Create focused internal packages under `com.inversionlabs.flock.*` where replacements are security-positive.

**Interfaces:**
- Produces: smaller third-party runtime closure without replacing primitives that are safer retained.

- [ ] Remove Google Play Services location by using Android platform location APIs behind an Inversion Labs `LocationSource` interface; preserve behavior with side-by-side tests.
- [ ] Remove ML Kit/AICore/Gemini Nano integration from hardened builds; local Flock model inference is the only AI path.
- [ ] Remove Orbot coupling and the Tor HTTP client from hardened builds.
- [ ] Replace the JitPack USB serial dependency by either a source-vendored pinned implementation or a small Inversion-owned CDC subset after protocol tests prove parity.
- [ ] Evaluate Gson, Guava, and Hilt with measured replacement cost; replace only when the internal implementation is materially smaller/easier to audit.
- [ ] Keep AndroidX/Room/SQLCipher/inference primitives where replacement would increase risk, but pin/vault, wrap, and test them.
- [ ] After each removal, regenerate dependency manifest and require the closure to shrink or justify any increase.

### Task 8: Lock edge-model ingestion to governed artifacts

**Files:**
- Create: `app/src/main/assets/models/model-manifest.json`
- Create: `app/src/main/java/com/inversionlabs/flock/model/ModelArtifactVerifier.kt`
- Add tests for model digest/provenance checks.

**Interfaces:**
- Consumes: governed training receipt + architecture-compatible exported model.
- Produces: verified local model handle or fail-closed rejection.

- [ ] Define manifest fields: model ID, parent IDs/revisions, training receipt hash, export receipt hash, format, quantization, file SHA-256, eval-set ID/hash, minimum acceptance metrics, and runtime compatibility version.
- [ ] Verify the model file digest before inference initialization.
- [ ] Reject missing, extra, modified, unapproved, or architecture-incompatible model artifacts.
- [ ] Ensure no model selector can resolve a URL or remote repository.
- [ ] Add corruption and substitution regression tests.

### Task 9: Add SBOM, license, and malicious-supply-chain regression gates

**Files:**
- Create: `.github/workflows/sovereignty-gates.yml`
- Create: `tools/check_licenses.py`
- Create: `tools/check_supply_chain_policy.py`

**Interfaces:**
- Produces: release-blocking evidence bundle.

- [ ] Generate a machine-readable SBOM from the resolved dependency graph and packaged native libraries.
- [ ] Fail on unknown/incompatible licenses or dependency artifacts absent from the trust manifest.
- [ ] Add fixtures proving the gate catches: substituted artifact digest, unexpected transitive dependency, dynamic version, added repository, JitPack reintroduction, runtime URL, INTERNET permission, mutable model, and release-task downloader.
- [ ] Upload only evidence artifacts needed for review; never publish APK/model artifacts from this workflow.
- [ ] Record source commit, tool versions, dependency-manifest hash, SBOM hash, APK hash, and test summaries in a signed/hashable receipt JSON.

### Task 10: Reproducible offline release-candidate verification

**Files:**
- Create: `tools/repro_build.sh`
- Create: `docs/release/sovereign-release-gate.md`

**Interfaces:**
- Consumes: Tasks 1-9.
- Produces: candidate build evidence, not deployment.

- [ ] Build the same hardened release candidate twice from clean directories with outbound network denied and the approved dependency vault mounted read-only.
- [ ] Normalize only documented nondeterministic signing/container metadata; compare package content hashes and explain every permitted difference.
- [ ] Inspect APK contents for unexpected `.so`, remote configuration, certificates, URLs, model files, and dependency metadata.
- [ ] Run full unit/instrumented/security regression suites against the candidate.
- [ ] Submit dependency manifest, SBOM, diff from upstream, APK inspection, and release receipt through Inversion Labs CAPT with governed Qwen3.8 adversarial review.
- [ ] Fail closed on unresolved CAPT findings or reproducibility mismatch. Do not publish/deploy.

### Task 11: Progressive Inversion Labs ownership program

**Files:**
- Create: `docs/security/dependency-burn-down.md`

**Interfaces:**
- Produces: prioritized roadmap rather than an unsafe rewrite mandate.

- [ ] Rank retained third-party components by exploitability, privilege, update frequency, maintainer concentration, transitive size, native-code exposure, and replacement complexity.
- [ ] For each candidate, choose `retain+audit`, `vendor source`, or `replace internally`; explicitly forbid rewrite-by-default for cryptography/storage/runtime primitives.
- [ ] Require behavior/security benchmarks before replacing a mature dependency.
- [ ] Track dependency-count and transitive-count reductions release over release, with security rationale rather than ownership percentage as the success metric.

## Completion Gate
The sovereignty program is not complete until the hardened privacy APK builds with networking denied, contains no INTERNET permission or remote content path, resolves no mutable dependency, verifies every retained artifact by hash/license/provenance, accepts only governed model artifacts, reproduces from a clean offline build, and passes CAPT/Qwen adversarial review plus independent automated checks.
