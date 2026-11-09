# Charging core companion v9: bounded NEW433 registry append

V9 admits only `circulation_listening`, proposal 433, after the 45 high-power
registrations protected by v8/.68. Its record is `vecmanip`, level 3,
controllable, requiring learned `CIRCULATION_RELIEF` (NEW432) at prerequisite
experience 0, with `anyLearnedSkillLevel = 0`. This companion establishes narrow
registry admission for the unchanged M41/current Charging core differential.
It does not establish NEW433 motion sensing, combat, native runtime, learning
machine behavior, or rendering.

## Immutable lineage

Runner: `scripts/verify-charging-m41-core-v9.py`.
Contract and baseline: `src/test/resources/current-geometry-proof/charging-core/registry-433-v9/`.

The contract retains all 29 predecessor inputs protected by v8, then pins the
unchanged v8 runner, contract, and frozen .67 source: 32 immutable inputs.
There is no pre-existing v8 companion document to add. No predecessor runner,
contract, frozen source, trace driver, or companion document is changed.
The frozen .68 source is copied byte-for-byte from actual revision
`2f0713294144538677712c3ef68115331249fe84`; it also equals the reconstructed
output of v8. The previous contract's source and class pins are carried forward
without recomputation or replacement.

The verified chain is .58 + NEW412 = .59, .59 + NEW413 = .60, .60 + NEW421 =
.61, .61 + NEW422 = .64, .64 + NEW423 = .65, .65 + NEW431 = .67,
.67 + NEW432 = .68, and .68 + only NEW433 = current. Every stage applies
exactly three single-occurrence hunks, validates its fixed source hash,
reverses the hunks, and recovers the prior bytes exactly. The final hunks add
the ID/proposal constants, append one IDS entry, and append one Skill record.
All prior 45 records, constants, ordering, prerequisites, and methods remain
exact. The old compiled registry hash remains chained to the v8 output hash.

Pinned identities:

- V9 contract: `c66c4b2f6ef03ef14d03912901608e1baad9e353ede71ad9e26b0866f4c612b7`
- Frozen .68 source: `489e66aa125c107d88eb8be4fc94dec56aabc247c21c68a0e912e1d81988ada9`
- Frozen .68 class: `d0ad37e7ff1060c01c57e4831cc4a1e79200b952748765889e2836365c8cdb29`
- NEW433 source: `e1b849998fcd32a7efb36d349257d8f0fc6621ab2057e5acd3343d95e05aec69`
- NEW433 class: `160fccc54344762d71b99b3d04ac5bbe1d4d5e7a0d91380012cdbd24418dd301`

JDK 21 evidence compilation uses the unchanged `--release 21 -g -encoding UTF-8`
settings and reproduces both registry hashes. The new evidence class must equal
actual `build/classes/java/main` output byte-for-byte. The positive differential
always executes actual compileJava output; evidence compilation is never used
as the positive subject. There is no refresh, relaxed admission, or skip mode.
The public API comparison adds only `CIRCULATION_LISTENING` and
`CIRCULATION_LISTENING_PROPOSAL`; every previous public signature remains exact.

## Required controls and preserved boundaries

All 541 registry mutants must reject with `REGISTRY_433_NONAPPEND_CHANGE`:

- 276 registration-field mutations: six fields for each of the 45 old records
  and the appended record
- 46 saved-ID constant mutations
- 45 proposal-number mutations; the existing `CHARGE_DECOY` has no proposal
  constant, as already enforced by v8
- 84 individual prerequisite mutations: identity and experience for all 42
  requirements
- 90 adjacent-order mutations: all 45 IDS seams and all 45 registration seams

Every old field and order control is retained in the expanded coverage. The
unchanged v8 runner independently retains all 529 of its registry controls.
The new runner also explicitly checks that the last record has all six expected
fields, including its sole NEW432 prerequisite.

The unchanged executable Charging CP, XP, and callback-order mutants must still
differ from M41. Unknown-dependency, wrong-origin, duplicate-resource, and
wrong-digest loader controls still reject. Reusing a report directory rejects
without replacing prior evidence. Non-registry source/class pins, closed
production class admission, official dependency signatures, original archive,
source and processed classic catalog, classic ordinals, and classic level XP
denominators remain protected by the original checks and trace.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
remain required. V9 does not repair, exclude, or rebaseline them and cannot turn
aggregate `check` into a pass.

## Invocation and initial verification status

After real `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v9.py --jdk .tools/jdk-21.0.12.1+1 \
  --report /tmp/charging-core-registry-v9-fresh
```

`chargingM41CoreProof` selects v9 while retaining every previous dependency.
Its Gradle routing change is only the active companion script path, v8 to v9.
The Python proof starts no Gradle, native server, game, UI, or rendering.

The initial source check passed the eight-stage source lineage and reverse
roundtrips, all 32 immutable predecessor pins, all 32 simulated predecessor
tamper rejections, all 541 registry controls, and the report-directory lifecycle
control. The old v8 runner separately passed its existing 529 controls against
the unchanged .68 source. Direct function checks of v2 through v8 rejected the
NEW433 source with their exact existing nonappend markers.

Both class hashes reproduce using isolated JDK 21 evidence compilation. At the
source-check checkpoint, actual `build/classes/java/main` also matched the
NEW433 class hash. Source checks and class reproduction do not complete the
actual-production Charging differential; that separate execution remains to be
recorded by the integration run. No Gradle or native process was started for
these initial source checks.

Initial evidence is in `/tmp/charging-v9-source-evidence-qzafckka/`:
`charging-v9-source-audit.json`, `registry-class-reproduction.json`, both
registry javac logs, and `catalog-public-api.diff`. These temporary source-check
artifacts are separate from the integration run's actual Charging trace and
full aggregate check results.
