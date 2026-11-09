# Charging core companion v10: bounded NEW441 registry append

V10 admits only `vector_cage`, proposal 441, after the 46 high-power registrations
protected by v9/.69. Its record is `vecmanip`, level 4, controllable, with exactly
one prerequisite, learned `TRAJECTORY_RETURN` (NEW431) at experience 0, and
`anyLearnedSkillLevel = 0`. This proves narrow registry admission for the unchanged
M41/current Charging core differential. NEW441 combat, native integration,
learning, input, and rendering remain separate tests.

## Immutable lineage and exact append

Runner: `scripts/verify-charging-m41-core-v10.py`.
Fixture: `src/test/resources/current-geometry-proof/charging-core/registry-441-v10/`.

`HighPowerSkills.v69.java` is copied byte-for-byte from revision
`e4694b54ee36156ac578a87b13b76a71112aff63`. Its bytes also equal the reconstructed
output of v9. The v9 source and class hashes carry forward unchanged. The v10
contract pins all 32 predecessors protected by v9, plus the unchanged v9 runner,
contract, frozen .68 source, and companion document: 36 immutable predecessor
inputs. No old runner, contract, fixture, trace driver, or document is rewritten.

The runner checks the complete nine-stage lineage: .58 + NEW412 = .59,
.59 + NEW413 = .60, .60 + NEW421 = .61, .61 + NEW422 = .64,
.64 + NEW423 = .65, .65 + NEW431 = .67, .67 + NEW432 = .68,
.68 + NEW433 = .69, and .69 + only NEW441 = .70. Each stage applies three
single-occurrence hunks, checks its pinned reconstructed digest, then reverses
all three hunks to recover the exact baseline bytes.

The only approved .69 changes add the two NEW441 constants, append `VECTOR_CAGE`
to `IDS`, and append its one registration. Every prior registration, constant,
order, prerequisite, and method remains byte-exact. The only additional public
API members are `VECTOR_CAGE` and `VECTOR_CAGE_PROPOSAL`.

Pinned identities:

- V10 contract: `25ac6b0f2044ec7601a0d5ae98af33340fcfc23de3462701eba2a481e34f6f1a`
- Frozen .69 source: `e1b849998fcd32a7efb36d349257d8f0fc6621ab2057e5acd3343d95e05aec69`
- Frozen .69 class: `160fccc54344762d71b99b3d04ac5bbe1d4d5e7a0d91380012cdbd24418dd301`
- NEW441 source: `cb8d95d4f919e09589db90dc528abea499e9492383095469f0f9d9ab09052bf2`
- NEW441 class: `69905c8379438a353da14123fb9a0e9914785c64818d5c2622021f0dd607339b`

The new source was constructed from those explicit hunks, then checked against
the working source; an arbitrary working file was not used to establish the
approved patch. The class digest was obtained by isolated JDK 21 compilation
with `--release 21 -g -encoding UTF-8 -proc:none -implicit:none -sourcepath ''`.
The initial compile classpath contained only the existing v1-pinned signatures
for `SkillCatalog`, its `Skill` and `Requirement` records, `HighPowerCategories`,
and `AbilityProgress`. Both evidence compilations emitted exactly one class,
`HighPowerSkills.class`. The old class reproduced its existing v9 pin, and the
new evidence class matched actual `build/classes/java/main` byte-for-byte.

The runner reproduces both class digests and requires its new evidence class to
equal actual compileJava output. The positive differential executes actual
compileJava output, never the evidence recompile. There is no refresh, relaxed
admission, skip, or baseline-update mode.

## Negative controls and preserved boundaries

All 553 registry mutants reject with `REGISTRY_441_NONAPPEND_CHANGE`:

- 282 registration-field mutations: all six fields in each of 47 records
- 47 saved-ID constant mutations
- 46 proposal-number mutations; the existing `CHARGE_DECOY` has no proposal constant
- 86 individual prerequisite mutations: identity and experience for all 43 requirements
- 92 order mutations: every adjacent pair in `IDS` and the registration list

These controls include every field and order seam of all 46 old registrations.
The unchanged v9 runner separately passes its existing 541 controls on .69 and
rejects the NEW441 source. Unchanged v2 through v8 also reject NEW441 with their
original nonappend markers. All 36 simulated predecessor-tamper controls reject.

Executable Charging CP, XP, and callback-order mutants still differ from M41.
Unknown dependency, wrong origin, duplicate resource, and wrong digest loader
controls reject. A reused report directory rejects without replacing prior
evidence. Non-registry source/class pins, the production closure, official
dependency signatures, original archive, classic catalog/ordinals, and classic
level XP denominators retain their original checks.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
remain unchanged. This companion does not repair, exclude, or rebaseline them,
and its pass does not establish an aggregate `check` pass.

## Invocation and verified result

After actual `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v10.py --jdk /path/to/jdk-21 \
  --report /tmp/charging-core-registry-v10-fresh
```

The `chargingM41CoreProof` task should select the v10 script while retaining its
existing dependencies. This worker did not change the Gradle task. The Python
runner starts no Gradle, native server, game, UI, or rendering process.

The 2026-10-14 bounded differential passed against actual compileJava output:
all 6,427 original/current trace lines across 437 cases matched byte-for-byte;
trace SHA-256 is `baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
The three executable behavior mutants
were detected, all four loader guards rejected, all 553 registry mutants
rejected, and all 88 pinned run inputs were rehashed unchanged. Source lineage,
predecessor tampering, previous-runner rejection, class reproduction, public API,
and report-directory lifecycle checks also passed.

Initial evidence:

- Full bounded differential: `/tmp/charging-v10-core-proof-20261014-0927/result.json`
- Full run log: `/tmp/charging-v10-core-proof-20261014-0927.log`
- Isolated source/class/API audit: `/tmp/charging-v10-source-evidence-qtj812u7/`

The temporary artifacts record this run; future executions must use a fresh
report directory and repeat all admission and runtime checks.
