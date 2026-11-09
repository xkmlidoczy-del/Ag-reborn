# Meltdowner / MineRayBasic verification

Private staging-only classic1.0.7 -> Minecraft1.21.1 / NeoForge21.1.252. No production promotion, Gradle invocation, game/client/server startup, runtime recording, world writes or save migration was done by this lane. Full port remains unfinished.

## Executed

`bash .staging/meltdowner-beams/scripts/verify_cached.sh` uses the cached pinned NeoForge classpath and JDK21.0.12.1+1. All outputs are isolated under this stage. See `verification.log`.

-8,903,446 exact captured-Float formulas,20/40/101charge boundaries, failedCP/timeout/abort, overloadfloor, original mining acquisition/progress/order, sentinel particles, harvest rejection, metadata-position bug, negativehardness, charge/headgeometry, attenuation and lattice/Plotter/terrainquirk assertions
-1,761 comparisons against the entire UNCHANGED upstream executable RangedRayDamage class body in test-side1.7stubs: forty head-angle samples, ordinary/reflect/unbreakable/permission/config scenarios, exact entity transaction identities/rawFloatdamage and terrain probe multisets
-331 canonical prerequisite/minimumNormal/revalidation/realfiniteMachineDeveloperEnergy/TPS+1/zero-mastery/preset-binding checks. Insufficient Normal pulls leave34IF intact, as its real atomic battery does
-21common classes cold-linked with all Minecraft/NeoForge/port client namespaces denied; no game bootstrap
-5,673,307 source visual parameter/geometry/curve/lifetime/wiggle/particle/coordinate/sound/token/nonce/session differential assertions
-46source asset/material/event assertions,11original media files byte-identical and decoded/headersverified; all four sound events and white/cutoff05 materials already integrated and reused
-134unchanged classic preset/corruptsave/editor/activation/delegate assertions
-Cached Java compilation of all additive common/client/native-test files, four exact shared-file review overlays and the unchanged common SkillAvailability source
-Manifest SHA256/patch reconstruction/narrow baseline/full older dispatch preservation/shared learning-registry/GameTest mod-JAR exclusion checks

Compilation yields two already-existing AcademyClient MOD-bus removal warnings plus the native sound bridge's deprecated API note. No compilation errors. Native fixtures compile only and must not be called passed.

## Ten native fixtures compiled ONLY

`AcademyMeltdownerBeamRuntimeTests` generates a64x8x64empty template to contain the50unit terrain footprint. Owned fake players, native living targets, event hooks and developer processes are cleaned after pass/failure/timeout/rerun. Native fake player packets are discarded; these tests cannot prove real sockets/client playback. Declared combat skill states/prerequisite mastery and a finite machine battery are fixture inputs, not a survival playthrough or production skill/power grant.

1. Twenty actual world ticks produce one native wide-beam transaction through a bedrock wall, captured CP/damage/cooldown, same-tick and release replay guards
2. Nineteen-tick key-up and ordinary key-abort never shoot/train/install cooldown
3. Strict101tick timeout consumes all101CPticks without a shot or EXP/cooldown
4. First canceled reflection event suppresses original forward targets; reflector's real head ray10damages one secondary receiver at source reflected damage and still awards full-shot EXP/cooldown
5. Real stone requires one acquisition plus eight novice.2hardness decrements, yields native cobblestone, pays9ticksCP and applies40tick release cooldown
6. Failed CP progress tick still breaks dirt, trains levelEXP and applies master20cooldown
7. Obsidian harvest3rejects; source protection repeatedly cancels acquisition; bedrock FloatMAX remains unbroken
8. Original captured-dirt-hardness quirk breaks a later bedrock replacement at those same coordinates without rechecking harvest/hardness
9. Authenticated slot/preset/input-nonce ingress, unrelated-page abort, nonce retirement, lifecycle cleanup and nonfinite aim validation
10. Physical8cell Normal machine session genuinely learns EACH target from declared parent prerequisites via actual authenticated machine_learn and147actual process ticks, debits5145finiteIF, leaves mastery0 and requires explicit preset binding. Neither target is directly learned/granted by the fixture

## Main lane still required

- Apply only the manifest's additive files and reconstructed four-file patch to matching baselines. Rebase if a baseline changed; do not overwrite production snapshots blindly
- Full Gradle compile/test/check/build on the integrated tree, then execute all ten native fixtures plus existing full suite in a disposable GameTest world
- Final packaged dedicated-server startup/class linkage; cold reflection alone is not startup
- Actual survival-source earning of prerequisites and crafting/charging/using the retained physical Normal developer,147tick finite learning for both targets, genuine preset input. Fixture-seeded prerequisites/finite battery are not mined survival proof
- Real client/server20/40/100/101charge, premature input, keyabort, blockray/entitythroughwall, firstreflection, basicmining/harvest/failedCP/replacement, localwalking-speed reset, death/dimension/rejoin/rebind/earlyrelease and packet-delivery timing
- Main/reflected composite and continuous mining visual first/thirdperson/observer screenshots and shader/material/audio playback; original1.7.10 versus modern1.21.1 captures
- External multiplayer, loaded/unloaded chunk/protection/loot/break-hook integration and real latency/lag ordering

The exact modern metadata/loot/tier/RNG/context/entity/render/audio adaptations and unresolved rendered/audio parity are described in MELTDOWNER_BEAMS_FIDELITY.md and MELTDOWNER_BEAM_VISUALS.md. The stage never asserts a full-category/full-port completion.

## Recovery without the upstream working copy

The ray oracle and new visual source-binding checks require no `.reference` tree at Gradle execution. Four exact upstream text fixtures plus a GPL/upstream-restrictions notice are ordinary `src/test/resources/classic-oracles/meltdowner-beams` resources. Canonical SHA256s are verified before use; missing/corrupt fixtures fail tests rather than skip the oracle. Generated ray test Java preserves the entire class body and executes against test-only1.7stubs. Test resources are never included in the main mod JAR. The visual adapter source binding uses its stage Java source when retained, otherwise integrated production `src/main/java`; it requires a normal source checkout, not a source-free binary JAR.

`verify_cached.sh` additionally depends on this development workspace's cached pinned Java/NeoForge classpath and asset audit working-copy `.reference` sources/media. That diagnostic script is not the recovery build entrypoint. Ordinary Gradle tasks use the configured Java21/toolchain/dependency downloads and bundled test resources. `prepare_source_oracle.py` can regenerate the identical test Java from the mandatory canonical fixture when upstream is absent. `check_integration.py` always verifies the bundled original hash/class body; a present `.reference` copy is checked additionally. Media provenance script `verify_client_assets.py` still requires original source/media working copy and is not silently skipped in the cached full audit. These requirements do not bring any licensed songs into checkpoints.

Recovery execution evidence: both new source-oracle and visual regressions passed from a fresh `/tmp` working directory containing no `.reference` tree, using the ordinary bundled test resources. Removing the canonical ray resource caused the expected mandatory-fixture AssertionError before running the oracle. Logs are `audit/no-reference-recovery.log` and `audit/missing-fixture-recovery.log`. No game or Gradle process was used.
