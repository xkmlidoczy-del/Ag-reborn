# Standalone source-checkpoint recovery

This is a narrow test/provenance correction for the private classic1.0.7 -> Minecraft1.21.1 / NeoForge21.1.252 port. No gameplay, renderer, shaders, production media, native fixtures, Gradle configuration or already-frozen stage is changed. The full port and original visual/audio parity remain unfinished.

## Recovery contract

The source ZIP intentionally excludes the complete `.reference` upstream trees and toolchains. Three production regressions previously opened those trees or an old stage at runtime:

- `MeltdownerStarterRegressionTest`: Motion3D, ScatterBomb and LightShield source-sensitive checks
- `MeltdownerStarterVisualRegressionTest`: RenderMdShield, ScatterBomb, Motion3D, shader materials and actual modern renderer wiring
- `MineDetectDataRegressionTest`: retained original texture/audio byte identity, sound registration, advancement and localization

The first two now load four exact, small canonical source files from `src/test/resources/cn/academy/port/source-checkpoint/`. Their original GPLv3/MIT copyright/license headers are retained byte-for-byte. Total upstream source text is21,020bytes. No whole upstream tree, binary source archive or media is bundled. The existing root `NOTICE`, `LICENSE` and LambdaLib MIT notice remain authoritative, including upstream additional restrictions.

`CheckpointSourceFixtures` pins every original file SHA256 in executable test code. Missing, empty, unlisted or altered resources throw an assertion error; there is no alternate filesystem search, fallback, optional skip or hash regeneration during regression execution. The provenance manifest lists canonical paths/hashes for review only. The Java resource package is test-only and does not enter the production mod JAR.

## Current modern source remains current

The visual source-binding assertions continue to read the actual modern `ClassicMeltdownerStarterEffects.java` file, which is already present in the source ZIP. Default source root is `src/main/java`; direct standalone execution from a different working directory can set `-Dacademy.md.sourceRoot=/absolute/path/to/src/main/java`.

No frozen duplicate of modern renderer source is bundled. A modern wiring change remains visible to the existing source-sensitive assertions. The fresh-/tmp proof copies exactly the CURRENT renderer Java source into that temporary `src/main/java` path and checks that its bytes equal production before execution. Changing or omitting that copied current file causes the visual regression to fail. This source-root dependency is distinct from the removed upstream-tree dependency and is explicit, required and fail-closed.

Shaders are read from the actual production classpath resources. Their existing strictGT0/GT.05 fragment assertions remain unchanged. No shader or game-asset copies are added by this recovery.

## MineDetect identity audit without duplicate media

An independently pinned original-source text audit records the canonical SHA256 and byte length of `mineview.png` and `minedetect.ogg`. Both were computed from the audited classic1.0.7 originals during preparation. The audit itself has an executable pinned SHA256.

The existing literal canonical-hash check remains. The previous comparison against a second external upstream binary copy is replaced by matching the independently audited canonical SHA256 AND exact byte length. This preserves the byte-identity/provenance check without shipping duplicate binary media or depending on the upstream tree. It is a cryptographic identity verification, not a second raw `Arrays.equals` read. A changed byte, missing media resource, altered audit or changed length fails. All10existing MineDetect assertions, including sound registration, impossible advancement criterion, original advancement title/description and both locale keys, remain.

Production media, sounds.json, advancement and locale dictionaries are loaded from the actual classpath. A configured staging-resource-root filesystem override is no longer needed; checks examine the resources that the normal Java build places on its runtime classpath.

## Verification actually executed

Command: `python3 .staging/meltdowner-starter-recovery/scripts/verify_cached.py` in the current dot cloud checkout. This uses cached JDK21 javac and existing pinned NeoForge dependency jars; it runs no Gradle, Minecraft, server, world or client process. Generated classes and logs remain in this new recovery stage.

From a fresh `/tmp/academy-md-recovery-*` working directory containing NO `.reference` directory:

- 1,006,051 original starter gameplay/source assertions passed
- 3,922,900 original visual/source assertions passed
- All10MineDetect canonical data/advancement/localization assertions passed
- 20additional fail-closed fixture/resource integrity checks passed

Six external negative probes actually exited unsuccessfully for the expected reason: omitted canonical fixtures, a corrupted canonical byte, an empty canonical resource, altered current renderer wiring, missing current renderer source, and missing production media. These are execution proofs, not comments or skipped branches.

The exact current-renderer SHA256 and temporary directory are recorded in `verification.log`. Canonical resource hashes and the three-test narrow patch are in `promotion-manifest.json`. `source-checkpoint-recovery.patch` dry-runs against current production. No production or earlier-stage file was edited by this worker.

## Promotion and pending work

Copy only the two additive test Java helpers, five test resources and this document listed in the promotion manifest. Apply only the exact three production-test diffs. Standard Java test-resource processing already puts these resources on the test runtime classpath; no new Gradle task or runtime registration is required. If the recorded production-test baseline hashes changed, rebase the narrow patch instead of overwriting them.

The main lane still owns applying the correction, full Gradle check/build and final fresh source-ZIP restore verification. The cached proof does not claim a fully restored build, game startup, native gameplay execution, rendered pixels or audible parity. It only establishes that the corrected regression paths no longer need an excluded upstream tree, while their existing assertions remain active.
