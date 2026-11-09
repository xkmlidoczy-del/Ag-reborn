# Charging core companion v12: bounded NEW443 registry append

V12 admits only `wind_pressure_lift`, proposal 443, after the 48 registrations
protected by v11/.71. The appended record is `vecmanip`, level 4, controllable,
with the sole prerequisite `MULTI_AXIS_REFLECTION` (NEW442) at experience 0 and
`anyLearnedSkillLevel = 0`. NEW443 movement, learning, input, and rendering are
separate tests; this companion preserves the bounded M41/current Charging proof.

Runner: `scripts/verify-charging-m41-core-v12.py`.
Fixture: `src/test/resources/current-geometry-proof/charging-core/registry-443-v12/`.

## Frozen predecessor and narrow admission

`HighPowerSkills.v71.java` was captured with
`git show 332710a:src/main/java/cn/academy/port/highpower/HighPowerSkills.java`
before the NEW443 registry edit. The full revision is
`332710ac953d4681c2ef03eabb19914057292cd7`; these exact bytes also match v11's
reconstructed output. This is a current predecessor fixture, not a replacement
for any historical source witness or baseline.

V12 retains v11's 40 predecessor inputs and pins the unchanged v11 runner,
contract, frozen .70 source, and companion document, for 44 immutable predecessor
inputs. The complete v2–v11 chain is reconstructed and reversed before applying
three explicit NEW443 hunks: constants, `IDS`, and the final registration. All
48 previous records, prerequisites, constants, order, and methods stay byte
identical. Only `WIND_PRESSURE_LIFT` and `WIND_PRESSURE_LIFT_PROPOSAL` are added
to the public registry API. No baseline hash is refreshed, no exclusion is
expanded, and no automatic blessing or update mode exists.

Pinned identities:

- V12 contract: `3aa4d3597c5d81d4ae8d346fcaae775373e6aa12cb47fee4f35a40dc1100d14f`
- Frozen .71 source: `cb3f225d820e1361510d021a52910f7b5f0ee2c3b329cfabbfd67ab3226b0b46`
- Frozen .71 class: `1f86546001d5a1a8a0c1b68680d861a8602c8c51bfed17715e286850c21850c4`
- NEW443 source: `c7a281fc5d1097ccb0f7f325d40c670535eaaa0faeecd17d02ad99ac8365f12e`
- NEW443 class: `1d3a72038871e082cee6fb084475d0f52542f4bb20b341f4da32393b26a48da5`

The source was constructed from those three explicit hunks and compared with
working production source. Evidence compilation using JDK 21 and `--release 21
-g -encoding UTF-8 -proc:none -implicit:none -sourcepath ''` emits exactly one
class and reproduces the old class pin. The full runner separately requires
the reconstructed class to match actual `build/classes/java/main` bytes. Its
positive executable subject remains actual compileJava output, never this
evidence compilation.

## Negative controls and unchanged scope

All 577 registry mutants must reject with `REGISTRY_443_NONAPPEND_CHANGE`:

- 294 mutations of all six fields in each of 49 records
- 49 saved-ID mutations and 48 proposal-number mutations
- 90 identity/experience mutations of all 45 prerequisites
- 96 mutations of every adjacent order pair in `IDS` and the registration list

Every old registration is covered. The unchanged v11 checker must accept its
exact .71 baseline, detect all 565 existing mutants, and reject NEW443 with
`REGISTRY_442_NONAPPEND_CHANGE`. Each of the 44 predecessor inputs is virtually
tampered with in turn; every attempt must fail its unchanged digest check.
No predecessor file is rewritten by these controls.

The original M41 ZIP, production closure, loader, trace driver, scenario matrix,
classic catalog and ordinals, level XP denominators, non-registry source and
class pins, and executable CP/XP/callback-order mutants remain unchanged.
Unknown-dependency, wrong-origin, duplicate-resource, wrong-digest, final-input
rehash, and fresh-report-directory controls are retained.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
remain separate. This companion does not repair, omit, or rebaseline them and
does not establish an aggregate `check` pass.

The new `WindPressureLiftLearningRegressionTest` checks all 48 predecessor
records, string constants, proposal numbers, and ordinals directly against the
pinned .71 source. It also preserves the original 50 classic catalog bytes,
35 preset identities, all four categories' level 1–5 classic XP denominators,
previous saves and bindings, and all 48 prior node identities, ordinals,
levels, positions and parents across all four category pages. The layout
predecessor is separately frozen from the same .71 revision under
`src/test/resources/highpower-learning/wind-pressure-lift-v72/`. Derived
registration assertions grow only by explicit NEW443 membership. Learning
requires the real LV4/NEW442/Advanced conditions and 176 paid ticks/7040 IF.
The native request codec round-trips `wpl:~zik0zj` with a machine UUID and both
NEW443 actions with maximum authenticated input, under unchanged 32/64 limits.

## Invocation and evidence status

After actual `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v12.py --jdk /path/to/jdk-21 \
  --report /tmp/charging-core-registry-v12-fresh
```

The runner starts no Gradle task, native server, game, UI, or rendering process.
A fresh report path is mandatory; an unsuccessful rerun cannot overwrite or
reuse an earlier report.

On 2026-10-14, the bounded registry-only checks passed: all 577 current mutants,
unchanged v11's 565 mutants and rejection of NEW443, all 44 predecessor digest
and tamper controls, both evidence class reproductions, and stale-report
protection. The new learning test passed 295 checks in an isolated CPU-only
compilation using the existing native dependency classpath. Those checks do
not alone constitute the full actual-compileJava M41 differential; its own
fresh result must establish that execution and every original trace guard.

The subsequent 2026-10-14 full v12 differential passed against actual NEW443
`compileJava` output. All 6,427 trace lines across 437 cases matched the
immutable M41 execution byte for byte. Trace SHA-256 remains
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
The CP, XP, and callback-order executable mutants were detected, all four
loader guards rejected, all 577 current registry mutants rejected, unchanged
v11's 565 controls passed on .71, and all 44 predecessor-tamper controls
rejected. Both registry class reproductions, equality with the actual
compileJava registry class, the 96-input final rehash, and fresh-report
protection passed.

Evidence relative to the source directory:

- Full result: `../verification/v80-wind-pressure-lift/charging-v12-r1/result.json`
- Full log: `../verification/v80-wind-pressure-lift/charging-v12-r1.log`

Future executions must use a fresh report directory and repeat all checks.
This successful bounded differential does not replace the separate historical
geometry guards or claim a complete `check` pass.
