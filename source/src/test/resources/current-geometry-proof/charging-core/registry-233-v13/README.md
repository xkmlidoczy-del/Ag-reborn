# Charging core companion v13: NEW233-only registry append

This version admits only `point_meltdown` (proposal 233) after the 49 registrations
protected by v12. Its sole appended record is Meltdowner LV3, controllable, with
the exact `BEAM_ETCHING` prerequisite at experience zero and no any-learned-level
requirement. The new registration occupies high-power ordinal 49 and combined
catalog ordinal 99. No old record, prerequisite, identifier, proposal, ordering,
method, historical runner, or historical fixture is rewritten.

Runner: `scripts/verify-charging-m41-core-v13.py`.

## Independent predecessor and reviewed patch

`HighPowerSkills.v74.java` is the byte-exact output of:

```sh
git show 9377c15187bba95c5fabdba48a38da170d082318:src/main/java/cn/academy/port/highpower/HighPowerSkills.java
```

The registry was unchanged from .72 through .74; these bytes independently equal
v12's reconstructed output. The frozen file is not derived by deleting the new
record from current production source.

`contract-v13.json` contains exactly three explicit edits: add the NEW233 string
and proposal constants, append its ID to `IDS`, and append its registration to
`ALL`. Each edit has exactly one permitted site. Applying the edits must match
the entire production source byte for byte; reversing them must restore the
frozen predecessor byte for byte. The full v2–v12 reconstruction chain remains
checked before the new edit is admitted. There is no update or refresh mode.

Pinned identities:

- V13 contract: `467e56c0cf9af3924e0c206a9ef1003a0ad0533a8e8f48b0d07c2aea20a7f627`
- Frozen .74 source: `c7a281fc5d1097ccb0f7f325d40c670535eaaa0faeecd17d02ad99ac8365f12e`
- Frozen .74 class: `1d3a72038871e082cee6fb084475d0f52542f4bb20b341f4da32393b26a48da5`
- Reconstructed .75 source: `1d921ba1d78120a30b057b921620e728fe033aa04f664b5d2953d657586f4ad8`
- Reconstructed .75 class: `bc579ece07fa87f7edc3945675aaf8ba11cdb7b85d00962f5da00deeb94d63e2`

JDK 21.0.12.1 evidence compilation with `--release 21 -g -encoding UTF-8
-proc:none -implicit:none -sourcepath ''` reproduces both class pins. The runner
requires the reconstructed class to equal the actual compileJava class. Its
positive executable subject remains actual `build/classes/java/main` output;
the evidence recompilation never replaces it.

## Preserved guards and negative controls

All 589 registry mutations must reject with `REGISTRY_233_NONAPPEND_CHANGE`:

- 300 mutations of all six fields across all 50 records
- 50 saved-ID mutations and 49 proposal-number mutations
- 92 identity/experience mutations across all 46 prerequisites
- 98 mutations of adjacent order pairs in `IDS` and `ALL`

V13 preserves v12's 44 predecessor pins and adds the unchanged v12 runner,
contract, frozen source, and companion document, for 48 immutable predecessor
inputs. Every one is virtually tampered with and must fail its existing digest
check. The unchanged v12 runner must accept the exact .74 registry, detect its
577 existing mutants, and reject current NEW233 with its original
`REGISTRY_443_NONAPPEND_CHANGE` marker. These controls never write predecessors.

All non-registry production source/class pins, the original M41 archive,
execution closure, loader, trace driver, classic catalog bytes, original
ordinals, all four categories' LV1–5 XP denominators, and executable CP/XP/order
mutants remain unchanged. Unknown dependencies, wrong production origin,
duplicate resources, wrong digests, stale report paths, and changed inputs at
the final rehash remain fatal.

The three independent historical failures remain separate and unmodified:
`classicChargingGeometryTest`, `classicMeltdownerGeometryTest`, and
`classicThunderBoltGeometryTest`. This companion neither suppresses them nor
establishes an aggregate `check` pass.

## Observed bounded result

On 2026-10-14 the full v13 differential passed using actual NEW233 compileJava
output. All 6,427 trace lines across 437 cases matched immutable M41 execution
byte for byte. The trace SHA-256 remains
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.

The three executable mutants, four loader guards, 589 registry mutants,
unchanged v12's 577 controls and rejection of NEW233, all 48 predecessor-tamper
controls, both class reproductions, actual-class equality, 100-input final
rehash, and fresh-report protection passed.

Run-local evidence:

- `/tmp/aca-charging-v13-20261014-221653/result.json`
- `/tmp/aca-charging-v13-20261014-221653.log`

Reproduction after actual `compileJava`, `processResources`, and
`writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v13.py --jdk /path/to/jdk-21 \
  --report /tmp/charging-core-registry-v13-fresh
```

Choose a new report directory for every invocation. The runner starts no
Gradle task, native server, game, or client. This is finite classic non-enrolled
Charging core evidence. Native ingress, capability/world adapters, enhanced
transactions, NEW233 obstacle behavior, input, rendering, learning, and shared
resource lifecycle remain separate verification scopes.
