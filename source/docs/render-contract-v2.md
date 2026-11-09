# Versioned render contract V2

This contract requires three fresh, separate compiled checks before it reports
PASS. It does not restore the missing historical Git commit, change runtime Java,
or treat archived rendering as evidence of current rendering.

1. **Archival Railgun replay:** all thirteen frozen .35 classes compile against
   the patch-reconstructed pre-.31 `ClassicEffects` source. The complete frozen
   grand integration suite, preview suite, native configuration check, 29 Java
   mutation controls, and eight shader-profile controls run unchanged.
2. **Current Railgun differential:** the unchanged near-eye integration runner
   independently compiles the current render classes and all thirteen .35
   reference classes. Its current OFF/failure comparisons, nine Java mutations,
   and five GLSL mutations must pass. The actual pinned Minecraft `getBuffer`
   method and native vanilla shader witnesses are mandatory. Its optional modeled
   buffer fallback or `NOT_RUN` native shader result cannot satisfy V2.
3. **Current Light Shield integration:** the full existing functional, preview,
   native JOML, native configuration, and mutation checks run against current
   Shield classes. All 48 historical requirements remain: 46 unchanged live
   files and the same two original .31 integration fixtures. Only the previously
   reviewed `ClassicEffects` and `RailgunVfx2` current pins advance to .36.

The .36 label identifies the reviewed render revision; it is independent of the
current mod artifact version. Later gameplay work does not silently refresh any
accepted render hash.

## Preserved evidence and explicit loading changes

The five original integration runners and three historical guard scripts are
verified against fixed SHA256 values before use. No old runner or existing .31
or .35 fixture is edited. The .35 manifest is the original thirteen-entry
`BASELINE` mapping in the hash-verified near-eye runner.

`verify-railgun-grand-archive-v1.py` loads the hash-verified frozen grand runner
and permits exactly four complete, unique text substitutions in memory:

- Read the thirteen Java sources from the existing pinned .35 fixtures
- Read reconstructed pre-.31 `ClassicEffects` from its independently pinned file
- Replace the three unavailable Git guard acquisitions with explicit archived
  guard SHA256 and current-byte comparisons
- Compile the archived .35 configuration class for the native config oracle

Every other byte of the runner source is preserved. Reversing those four
substitutions must reproduce the frozen runner exactly. All three Java harnesses
and the native configuration harness are independently compared unchanged. The
three archived guard copies were also compared byte-for-byte with the separately
restored `baseline-source` archive when added. Neither a fake Git command nor a
fabricated Git ref or commit is used.

`verify-light-shield-render-v2.py` permits exactly six unique substitutions in
the hash-verified frozen Shield runner: two reviewed current hashes, two task
route assertions, and two corresponding report paths. The original historical
manifest and the two-file exception scope must remain identical. Its two Java
harnesses and native configuration harness remain identical. The existing six
production mutations and three contract mutations still execute.

The reviewed current SHA256 values are:

- `ClassicEffects.java`: `e9cfa0d45a688f886c52f4c8190cd06a28d3866373e2b6aa72f40442e998e5b7`
- `RailgunVfx2.java`: `9961844c08f827dff49cf1e7bb1047e9cc2fe92888ec0ec1f032ccf61ba8cb6c`

Their .35 intermediate fixtures remain independently required, with SHA256
`3870996a872c8f072508eb96850bd1cd90cc3693c7991084ccc73912465dfd9a`
and `4a8174c27d710c6765b99b050fb5538f867053dfabd85f9d8ed5b74a0dc7b1c9`,
respectively. No blanket current-hash refresh or new exception is allowed.

## Exact patch reconstruction

The new `src/test/resources/vfx2/render-contract-v2/` directory contains the exact
supplied `0011-vfx2-railgun-reference.patch`, reconstructed preimage, three archived
guards, and explicit provenance metadata. The verifier applies only the
`ClassicEffects.java` section; it does not apply the complete patch to production.

- Patch SHA256: `2b47ceffbf61bd2b7a5e9b0cb3e6c38df5b163ef287b9640a027a1793637e1a6`
- Existing .31 input: 34,385 bytes; SHA256 `96ac659d49b4ccddea08ada95670e198c3a83f088f7d5f5764bb4e0ebb359ab4`; blob `f907f44716a97986df3f246bd3abbb4125dc4efd`
- Reconstructed pre-.31 output: 23,227 bytes; SHA256 `106e7670ef6d7ba1b21f4acdc4d95fa9fd08eae1ea6da2062b1044c3a9a0438f`; blob `0beeb102eef36326d76dd74c4afdec66b1c62aff`

Exactly eleven reverse hunks must match their declared line positions, with zero
offset and zero fuzz. Applying all eleven forward hunks must reproduce every
byte of the existing .31 input. SHA256, lengths, Git blob IDs, hunk counts, and
the complete metadata object are checked on each run. A matching Git blob ID
is a content identity, not evidence that the original commit is available.

The original `ec0c40bab302b878f3cf14df0540f83f61c184c7` Git oracle is reported
separately as `NOT_RUN`, with `missingGit` when that commit is absent. The archive
report uses `archivalSourceHashes`, `actualArchivalClasses`, and
`currentProductionCoverage: false`; it does not relabel those inputs as current
production or as a successful original Git differential.

## Task routing and reports

`renderContractV2Test` is the single Exec task. It writes the complete nested
report to `build/vfx2/render-contract-v2.json` and requires
`writeVfx2NativeClasspath`. The existing `railgunVfx2IntegrationTest` and
`lightShieldVfx2IntegrationTest` names are explicit lifecycle aliases requiring
that complete execution; Light Shield still depends on Railgun. `check` requires
all three task names. The exact route is audited, with no skip, exclusion, or
successful empty integration replacement. A single Gradle task graph executes
the expensive suite once.

For an isolated run with already available Java 21 and native dependencies:

```sh
python3 scripts/verify-render-contract-v2.py \
  --native-config-classpath build/vfx2/native-classpath.txt \
  --report build/vfx2/render-contract-v2.json
```

`JAVA_HOME` can select Java 21. The native source archive defaults to
`build/moddev/artifacts/neoforge-21.1.252-sources.jar`; use
`--pinned-minecraft-sources` to select another existing copy. The native vanilla
client JAR can be supplied with `--vanilla-client-jar`. Missing native sources,
JOML, native configuration dependencies, or vanilla shader witnesses fail closed.
No script launches Gradle or downloads dependencies.

The orchestrator runs every child anew in a temporary report directory. A missing
child, failed child, wrong contract version, missing native witness, incomplete
mutation set, or nonzero exit cannot pass. Thirty-two isolated input-corruption
controls cover the patch, .31 input, preimage, every .35 graph file, every frozen
runner/guard, each archived guard, both reviewed integrations, metadata, and
aggregate route, including an appended named-task override that would disable
execution. Separate result controls reject omitted/changed near-eye
coverage, failed or `NOT_RUN` children, modeled buffers, missing native witnesses,
missing mutations, and a nonzero child that leaves a superficially passing report.

## Scope and remaining acceptance

The isolated Java 21 run on 2026-10-10 passed with exit code 0. Its nested report
records archival Railgun 28,818 recording assertions, 6,130 preview assertions,
5,774 grand assertions, and 69 native configuration assertions; current Railgun
1,298,426 assertions across 472 scenes; and current Shield 955 recording, 102
preview, and 47 native configuration assertions. All original mutation sets,
31 input-corruption controls, and nine result/exit controls passed. The command,
exit status, full output, and report were captured under
`verification/v45-recovery-registers/render-contract-v2*` in the enclosing restored
checkpoint. A subsequent read-only review identified an untested appended-task
override; the route audit was hardened and all 32 input-corruption controls then
passed separately. This did not change any compiled stage or Gradle wiring. The
isolated run and the additional route checks launched no Gradle process.

These checks compile real renderer Java against recording client/resource/GL
adapters. Native Java JOML arithmetic and NeoForge/Night Config behavior are
exercised, and the pinned Minecraft/vanilla source witnesses are verified. This
does not launch Minecraft, compile GLSL on a GPU, establish native engine event
timing, or establish first-person visual quality, occlusion, performance, or
resource-reload timing. Existing deterministic comparisons suppress random spark
lists while separately retaining their production source guards.

`classicChargingGeometryTest`, `classicMeltdownerGeometryTest`, and
`classicThunderBoltGeometryTest` remain unchanged and required. V2 success cannot
be reported as their success or as a full aggregate pass. Their explicit results
and the unexcluded aggregate result must be reported separately.
