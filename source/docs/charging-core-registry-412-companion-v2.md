# Charging core companion v2: bounded NEW412 registry append

This version admits the reviewed `vector_parry` registration, proposal 412,
without updating `charging-core/contract-v1.json`. It is a current-version
companion proof, not a repair of any historical Charging, Meltdowner, or
ThunderBolt guard. The three historical runners, their inputs, expected hashes,
and failure evidence remain separate and unchanged. The initial NEW412 aggregate
failure remains in `../verification/v67-vector-parry/check-full-r1.log`.

## Immutable predecessor and exact change

`src/test/resources/current-geometry-proof/charging-core/registry-412-v2/`
retains the exact old verifier as `verify-charging-m41-core-v1.py`, the `.58`
registry as `HighPowerSkills.v58.java`, and the explicit amendment in
`contract-v2.json`. No input-refresh mode exists. The predecessor is commit
`a5dda77b6b3b5ee4880c5a7d94255a7b12d7e4a1`.

The old registry source SHA-256 remains
`ae786dbafd230eec42c45390cc33bb5778b9c7535a456451d0e00247fdbd2445`.
The old registry class SHA-256 remains
`33ee44e5efebc40930738e8a95376568e58f5855343b7bb30a4785b2481112de`.
The entire unchanged v1 contract is pinned by SHA-256
`18764bbfd49693e9875928905caafc02016d9d6e499e19a875a8344216d32b3c`.

The verifier reconstructs the current source from those old bytes using exactly
three single-occurrence edits:

1. Declare `VECTOR_PARRY = "vector_parry"` and `VECTOR_PARRY_PROPOSAL = 412`
2. Append `VECTOR_PARRY` to `IDS`
3. Append one level-1, controllable `vecmanip` record to `ALL`, requiring learned
   `DEFLECTION_STRIKE` at experience 0, with `anyLearnedSkillLevel = 0`

The first 38 high-power registrations must remain byte-identical; the total
becomes 39. Merely substituting a current-source hash cannot admit another edit.
Every source listed in the v1 `current_sources` map is audited. All eleven other
sources, including `SkillCatalog`, retain their exact v1 bytes.

Only `HighPowerSkills.class` may differ within the 32-class current closure.
The verifier independently compiles both the frozen old registry and the
reconstructed source in temporary evidence directories, using JDK 21 with debug
information. The old output must reproduce the old pinned class bytes, and the
new output must be byte-identical to the actual `compileJava` class. No proof
class replaces the positive production subject or modifies build outputs.

Four required negative controls mutate an existing registration's level, reorder
existing ID entries, change proposal 412, or change the new learned dependency.
Every variant must fail `REGISTRY_412_NONAPPEND_CHANGE`. These failures are
recorded alongside the existing executed Charging CP, XP, ordering, loader, and
report-lifecycle controls.

## Mandatory behavior and catalog evidence

The immutable M41 source archive, original-source pins, original trace hash,
`ChargingCoreTrace.java`, and `ChargingCoreLoader.java` are unchanged. The same
driver still compares every Charging ledger/event trace byte from unmodified
M41 with the actual current compiled closure. The new registration exception
cannot skip or replace that differential.

The source and processed `classic-skills.json` must still equal the immutable
M41 resource byte for byte. This protects all classic records, their ordinals,
and their order. The unchanged driver checks classic skill-count denominators
for each of the four categories at levels 1 through 5. The Charging core source
and class pins remain unchanged. NEW412 high-power behavior is outside this
proof; its implementation and native tests remain separate.

## Run and preserve evidence

Run only after `compileJava` and `processResources` finish. Every report path
must be new. The script does not invoke Gradle or start a game.

```sh
python3 scripts/verify-charging-m41-core.py --report /tmp/charging-core-registry-v2
```

This is the default v2 companion used by the existing Gradle task, with no task
rewiring. Success requires both `BOUNDED_ORIGINAL_M41_CORE_DIFFERENTIAL_PASS`
and `registry_append_proof.result = ONLY_REVIEWED_NEW412_APPEND_PROVED`.
`current_source_audit`, reconstructed source/class hashes, and negative-control
results are retained in the run's `result.json`.

Both routes below retain the original v1 rejection on this appended registry:

```sh
python3 scripts/verify-charging-m41-core.py --contract-version 1 --report /tmp/charging-core-v1-rejection
python3 src/test/resources/current-geometry-proof/charging-core/registry-412-v2/verify-charging-m41-core-v1.py --project "$PWD" --report /tmp/charging-core-frozen-v1-rejection
```

Neither a v2 pass nor those expected v1 rejections change the status of the three
historical geometry guards or establish a passing aggregate `check`.

## Recorded verification

The isolated v2 run passed 437 cases and compared all 6,427 trace lines exactly.
The unchanged trace SHA-256 is
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
Both independently compiled registry versions reproduced their required class
bytes. All four new registry negatives, all three existing executed Charging
mutants, all four loader guards, and the report-lifecycle check passed.

The result and execution log are retained in
`../verification/v67-vector-parry/charging-core-registry-412-v2-r1-result.json`
and `charging-core-registry-412-v2-r1.log` in the same directory. The separate
`charging-core-registry-412-v1-rejection-r1.log` and
`charging-core-registry-412-frozen-v1-rejection-r1.log` each retain the expected
`CURRENT_SOURCE_SNAPSHOT_CHANGED .../HighPowerSkills.java` failure.
