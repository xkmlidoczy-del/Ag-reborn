# Charging core companion v7: bounded NEW431 registry append

V7 admits only `trajectory_return`, proposal 431, after the 43 high-power
registrations protected by v6/.65. Its record is `vecmanip`, level 3,
controllable, requiring learned `YAW_PALM` at prerequisite experience 0, with
`anyLearnedSkillLevel = 0`. This companion establishes narrow registry admission
for the unchanged M41/current Charging core differential. It does not establish
NEW431 combat, native arrow contact, learning-machine runtime, or rendering.

## Immutable lineage

Runner: `scripts/verify-charging-m41-core-v7.py`.
Contract and baseline: `src/test/resources/current-geometry-proof/charging-core/registry-431-v7/`.

The contract pins all 21 predecessor inputs protected by v6 plus the unchanged
v6 runner, contract, frozen baseline, and companion document: 25 immutable
inputs. No predecessor runner, contract, source fixture, trace driver, or
companion document is changed. The frozen .65 source is copied byte-for-byte
from revision `744acf03501c6f79b7c62ec2d056046d02334917` and equals the
reconstructed output of v6.

The verified chain is .58 + NEW412 = .59, .59 + NEW413 = .60, .60 + NEW421 =
.61, .61 + NEW422 = .64, .64 + NEW423 = .65, and .65 + only NEW431 = current.
Every stage applies exactly three single-occurrence hunks, validates its fixed
source hash, reverses the hunks, and recovers the prior bytes exactly. The final
hunks add the ID/proposal constants, append one IDS entry, and append one Skill
record. All prior 43 records, constants, ordering, and methods remain exact.
The old compiled registry hash remains chained to the v6 output hash.

Pinned identities:

- V7 contract: `e03244a09ed7454f4c86aa34a108a377f5b363187b206d1ac690989b4d48890f`
- Frozen .65 source: `2015d442417b24bb774574839ad903d2f6c5e5d75d9f39202752ac0f6fc76968`
- Frozen .65 class: `f4101cd82bc638a01a6701f9cdc1a80219b685cc2a5f787cc459089e891c44d6`
- NEW431 source: `b4e2fb4463f114594fe8db395f92dcc981d144b8eed0712ed5dfd59ea94b0f93`
- NEW431 class: `089b46759f042699d8dd66fa4d4305ecdd1c0b04afe1da73b881212902323177`

JDK 21 evidence compilation uses the unchanged `--release 21 -g -encoding UTF-8`
settings and reproduces both registry hashes. The new evidence class must equal
actual `build/classes/java/main` output byte-for-byte. The positive differential
always executes actual compileJava output. Evidence compilation is never
substituted as the positive subject. There is no refresh, relaxed admission,
or skip mode.

## Required controls and preserved boundaries

All 95 registry mutants must reject with `REGISTRY_431_NONAPPEND_CHANGE`:

- One level mutation for each of the 43 old registrations
- All 42 prior explicit registration/NEW412/NEW413/NEW421/NEW422/NEW423 field
  and order controls, retaining the new final entry at each mutation site
- Ten NEW431 controls: saved ID, proposal, prerequisite identity, prerequisite
  experience, category, level, controllability, any-learned-level, IDS order,
  and record order

The unchanged executable Charging CP, XP, and callback-order mutants must still
differ from M41. Unknown-dependency, wrong-origin, duplicate-resource, and
wrong-digest loader controls still reject. Reusing a report directory rejects
without replacing prior evidence. Non-registry source/class pins, closed
production class admission, official dependency signatures, original archive,
source and processed classic catalog, classic ordinals, and classic level XP
denominators remain protected by the original checks and trace.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
are retained. V7 does not repair, exclude, or rebaseline them and cannot turn
aggregate `check` into a pass.

## Invocation and verification status

After real `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v7.py --report /tmp/charging-core-registry-v7-fresh
```

`chargingM41CoreProof` selects v7 while retaining its previous dependencies.
The Python proof starts no Gradle, native server, game, UI, or rendering.

The initial independent CPU check passed the six-stage source lineage and
reverse roundtrips, all 25 immutable predecessor pins, all 95 registry tamper
controls, and report-directory lifecycle control. Both class hashes reproduce
using isolated JDK 21 evidence compilation. These are source/reproduction
checks, not a completed actual-production Charging differential.

All seven predecessor entry routes were executed against current appended
source. V6/v5/v4/v3/v2 retain their exact nonappend rejections; both v1 routes
retain `CURRENT_SOURCE_SNAPSHOT_CHANGED`. None wrote a successful result.
Initial evidence is in `build/trajectory-return-learning-isolated/`:
`charging-v7-source-audit.json`, `registry-v65-javac.log`, and
`charging-predecessor-rejections/results.json` with individual runner logs.
The same directory contains passing CPU learning results from the isolated
class overlay. A later actual compileJava differential must be recorded
separately before claiming the complete Charging proof passes.
