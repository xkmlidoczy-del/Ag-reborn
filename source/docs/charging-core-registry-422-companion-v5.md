# Charging core companion v5: bounded NEW422 registry append

This versioned companion admits only `inertial_anchor`, proposal 422, appended
last after the 41 registrations protected by v4. The new record is `vecmanip`,
level 2, controllable, with learned `VECTOR_PARRY` at prerequisite experience 0
and `anyLearnedSkillLevel = 0`. The original M41 Charging core differential,
trace driver, exact class admission, and all unrelated source/class pins remain
unchanged. This proof does not establish NEW422 gameplay or rendering behavior.

## Immutable lineage and exact changes

The new runner is `scripts/verify-charging-m41-core-v5.py`. Its contract and
baseline live in
`src/test/resources/current-geometry-proof/charging-core/registry-422-v5/`.
No v1/v2/v3/v4 runner, fixture, contract, driver, or companion document was edited.
The v5 contract pins all 13 predecessors protected by v4 plus the v4 runner,
v4 contract, v4 baseline fixture, and v4 companion document: 17 fixed inputs.

`HighPowerSkills.v61.java` is the byte-exact output of the immutable v4
reconstruction and also equals the source at baseline revision
`3124b6e61eb1649168da0675c70f267b0c12df90`. V5 verifies the entire chain:

1. Frozen `.58` plus only NEW412 equals frozen `.59`
2. Frozen `.59` plus only NEW413 equals frozen `.60`
3. Frozen `.60` plus only NEW421 equals frozen `.61`
4. Frozen `.61` plus only NEW422 equals the actual current source

Each stage applies exactly three single-occurrence hunks, checks its pinned
output hash, reverses every hunk, and requires exact recovery of the prior bytes.
For v5 the hunks add only the NEW422 ID/proposal constants, append its final IDS
entry, and append its final Skill record. All 41 previous records, their ordering,
constants, and methods must remain identical. The old class hashes stay chained
to each predecessor contract's reconstructed class hash.

The new fixed identities are:

- V5 contract: `96c52f6ad9b4101af9054c3981cbf2d27879de48116613f3e876776db4a52a82`
- Frozen v4 source: `418c8603673cf25a00cfc441955d7dda9df57364fc7374fe6626f01f298f9068`
- Frozen v4 class: `2685492a2ba0e22e7a216829afc8f0f2f5243af9553969dcbefb2ef870739989`
- NEW422 source: `b8b9ba3ed70b97ad036d158e54417d6810dc8e36aaddced608bf9bbd06954f21`
- NEW422 class: `57ac856a078f7043bf6170b1289319e8f7592ad9544b36d6659424332946e726`

JDK 21 evidence compilation reproduces the old and new registry class hashes
using the prior `--release 21 -g -encoding UTF-8` settings. The new evidence
class must match the actual `build/classes/java/main` class byte for byte.
The positive differential executes that actual production output; it never
substitutes an evidence compilation. There is no refresh, skip, or relaxed mode.

## Mandatory controls

All 73 registry mutations reject with `REGISTRY_422_NONAPPEND_CHANGE`:

- One level mutation for each of the 41 prior registrations, including NEW421
- All 22 prior explicit field/order controls for old registration order, NEW412,
  NEW413, and NEW421, with the new final ID retained in their mutation sites
- Ten NEW422 controls: ID value, proposal number, prerequisite identity,
  prerequisite experience, category, level, controllability, any-learned-level,
  ID order, and record order

The unchanged executable Charging CP, XP, and callback-order mutations must
still differ from M41. The unchanged unknown-dependency, wrong-origin,
duplicate-resource, and wrong-digest loader controls still reject. A reused
report directory is rejected while prior evidence remains intact.

All non-registry source/class pins, the 32-class production closure, official
dependency signatures, immutable original archive, source and processed classic
catalog bytes, classic ordinals, and classic level denominators remain protected
by the original checks and trace. The three separate historical failures remain
unchanged: `classicChargingGeometryTest`, `classicMeltdownerGeometryTest`, and
`classicThunderBoltGeometryTest`. V5 does not repair, exclude, or rebaseline them,
and does not claim aggregate `check` success.

## Invocation and recorded verification

After real `compileJava`, `processResources`, and `writeVfx2NativeClasspath`
outputs exist, choose a fresh report directory:

```sh
python3 scripts/verify-charging-m41-core-v5.py --report /tmp/charging-core-registry-v5
```

The aggregate `chargingM41CoreProof` task selects v5 while retaining its original
dependencies and historical guard tasks. The Python runner starts no Gradle,
native server, game, UI, or rendering process.

The first v5 run passed against actual NEW422 production classes: all 437 cases
and 6,427 trace lines matched M41 byte for byte. Trace SHA-256 remains
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
Both registry reproductions, four-stage source lineage, 17 predecessor hashes,
73 registry controls, three executed Charging mutants, four loader guards, and
the report lifecycle control passed. The report rehashed 69 fixed inputs after
execution and found no changes.

All five predecessor routes were also run against the appended current source.
V4, v3, and v2 retained `REGISTRY_421_NONAPPEND_CHANGE`,
`REGISTRY_413_NONAPPEND_CHANGE`, and `REGISTRY_412_NONAPPEND_CHANGE` respectively.
Both v1 routes retained `CURRENT_SOURCE_SNAPSHOT_CHANGED` for HighPowerSkills.
None wrote a false successful result report.

Evidence is preserved under `../verification/v72-inertial-anchor/`:

- `charging-v5-r1/result.json` and the complete bounded execution evidence
- `charging-v5-r1.log`
- `charging-v5-predecessor-rejections-r1/result.json` and five runner logs

The three historical geometry guard tasks were not launched by this narrow
companion run; their existing separate failures are not converted into passes.
