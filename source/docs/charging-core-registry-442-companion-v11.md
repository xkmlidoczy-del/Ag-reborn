# Charging core companion v11: bounded NEW442 registry append

V11 admits only `multi_axis_reflection`, proposal 442, after the 47 registrations
protected by v10/.70. The appended record is `vecmanip`, level 4, controllable,
with the sole prerequisite `VECTOR_CAGE` (NEW441) at experience 0 and
`anyLearnedSkillLevel = 0`. NEW442 combat, learning, input, and rendering are
separate tests; this companion preserves the bounded M41/current Charging proof.

Runner: `scripts/verify-charging-m41-core-v11.py`.
Fixture: `src/test/resources/current-geometry-proof/charging-core/registry-442-v11/`.

## Frozen lineage and narrow admission

`HighPowerSkills.v70.java` is byte-identical to revision
`3f7f5e90b8929c2d3f9a0b3b82a146c9afba415c` and to v10's reconstructed output.
V11 pins v10's 36 predecessor inputs and the unchanged v10 runner, contract,
frozen .69 source, and companion document, for 40 immutable predecessor inputs.
The complete chain from v2 through v10 is reconstructed and reversed before
applying the three NEW442 hunks: constants, `IDS`, and the last registration.
All 47 previous records, prerequisites, constants, order, and methods stay
byte-identical. Only `MULTI_AXIS_REFLECTION` and
`MULTI_AXIS_REFLECTION_PROPOSAL` are added to the public registry API.

Pinned identities:

- V11 contract: `0925462e4eb2d60ba5b06ddc892881ce60f798b213e5093b896165897b842ca5`
- Frozen .70 source: `cb8d95d4f919e09589db90dc528abea499e9492383095469f0f9d9ab09052bf2`
- Frozen .70 class: `69905c8379438a353da14123fb9a0e9914785c64818d5c2622021f0dd607339b`
- NEW442 source: `cb3f225d820e1361510d021a52910f7b5f0ee2c3b329cfabbfd67ab3226b0b46`
- NEW442 class: `1f86546001d5a1a8a0c1b68680d861a8602c8c51bfed17715e286850c21850c4`

The new source was constructed from the explicit three hunks and then compared
with the working source. The class pin was reproduced from that reconstructed
source using only v1-pinned `SkillCatalog`, `Skill`, `Requirement`,
`HighPowerCategories`, and `AbilityProgress` signatures. Both evidence
compilations use JDK 21 with `--release 21 -g -encoding UTF-8 -proc:none
-implicit:none -sourcepath ''`, emit exactly one class, and reproduce the old
class pin. The runner requires the new evidence class to match actual
`build/classes/java/main` bytes before executing that actual compileJava output.
There is no refresh, rebaseline, relaxed admission, or skip mode.

## Controls and unchanged scope

All 565 registry mutants must reject with `REGISTRY_442_NONAPPEND_CHANGE`:

- 288 mutations of all six fields in each of 48 records
- 48 saved-ID mutations and 47 proposal-number mutations
- 88 identity/experience mutations of all 44 prerequisites
- 94 mutations of every adjacent order pair in `IDS` and the registration list

Every old registration is covered. The unchanged v10 checker must still accept
its exact .70 baseline, detect all its 553 existing mutants, and reject NEW442
with `REGISTRY_441_NONAPPEND_CHANGE`. Each of the 40 predecessor inputs is
virtually tampered with in turn; every attempt must fail the original digest
check without rewriting any file.

The original M41 ZIP, production closure, loader, trace driver, scenario matrix,
classic catalog and ordinals, level XP denominators, non-registry source and
class pins, and executable CP/XP/callback-order mutants are unchanged. Unknown
dependency, wrong-origin, duplicate-resource, wrong-digest, final input rehash,
and report-directory lifecycle checks remain enforced.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
remain unchanged. This companion does not repair, omit, or rebaseline them,
and does not establish an aggregate `check` pass.

## Invocation

After actual `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v11.py --jdk /path/to/jdk-21 \
  --report /tmp/charging-core-registry-v11-fresh
```

The runner starts no Gradle task, native server, game, UI, or rendering process.
Its positive subject is always actual compileJava output, and a report path
must be fresh so a failed rerun cannot overwrite prior evidence.

The 2026-10-14 full bounded differential passed against actual NEW442
compileJava output. All 6,427 trace lines across 437 cases matched byte-for-byte;
trace SHA-256 remains
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
The three executable Charging mutants were detected, all four loader guards
rejected, all 565 current registry mutants rejected, unchanged v10's 553
controls passed on .70, and all 40 predecessor-tamper controls rejected.
Source lineage, both class reproductions, actual class equality, final input
rehash, and stale-report protection passed.

Evidence relative to the source directory:

- Full result: `../verification/v79-multi-axis-reflection/charging-v11/result.json`
- Full run log: `../verification/v79-multi-axis-reflection/charging-v11.log`

Future executions must use a fresh report directory and repeat all checks.
