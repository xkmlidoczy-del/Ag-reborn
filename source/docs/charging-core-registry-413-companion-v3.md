# Charging core companion v3: bounded NEW413 registry append

This explicit current-version companion admits only the reviewed `braking_touch`
registration, proposal 413, after the previously approved 39 registrations.
It retains the bounded original-M41 Charging differential and does not repair,
exclude, or update any historical Charging, Meltdowner, or ThunderBolt guard.
Those guards and their failure evidence remain separate.

## Immutable predecessors

The new runner is `scripts/verify-charging-m41-core-v3.py`. The existing
`scripts/verify-charging-m41-core.py` remains the exact v2 runner from commit
`7e432baf7d8cc8a2c05b1b3fc4607f215d78d685`, with SHA-256
`f7efad59197bd24c0cf0f911dae503370eadc3490bb5dc0edbbfff9f27793eca`.
Its v1 route, the standalone frozen v1 runner, and every existing Charging core
fixture retain their previous bytes. The v3 contract audits nine predecessor
inputs, including both runners, both contracts, the old registry fixture,
the trace and loader sources, and the prior documentation.

The new `registry-413-v3/HighPowerSkills.v59.java` fixture is extracted byte for
byte from commit `77dfddfa902ae43754da3f3116a5319c619acc6d`. Its identity is also
checked against the exact reconstruction of the frozen `.58` registry using
the unchanged v2 amendment. The `.59` source and compiled class are therefore
the prior v2 outputs, rather than newly accepted baseline hashes:

- `.59` source: `a602710107b954afda265d08392c399c17e53931b76e0a71c91d4e0713997411`
- `.59` class: `4e333d05a58e7a0ee09704e78c4290dda45b96e257dcd466df01cdb736e47362`
- Unchanged v1 contract: `18764bbfd49693e9875928905caafc02016d9d6e499e19a875a8344216d32b3c`
- Unchanged v2 contract: `8c27b2cd7f5251379235d64d45ff4716f7d9e4683f0a6bc0fb7049a5e939936a`

## Exact append and roundtrip

`registry-413-v3/contract-v3.json` permits exactly three single-occurrence edits:

1. Declare `BRAKING_TOUCH = "braking_touch"` and `BRAKING_TOUCH_PROPOSAL = 413`
2. Append `BRAKING_TOUCH` last in `IDS`, after `VECTOR_PARRY`
3. Append `BRAKING_TOUCH` last in `ALL`: level 1, controllable, `vecmanip`,
   learned `DEFLECTION_STRIKE` (411) prerequisite with experience 0, and
   `anyLearnedSkillLevel = 0`

The current source must equal that explicit reconstruction byte for byte.
Applying the three inverse edits must reproduce every `.59` byte. Both forward
and reverse checks also run on the prior `.58` to `.59` reconstruction. Thus the
old 38 registrations and the reviewed 412 registration remain fixed; a hash
replacement alone cannot admit a different registration or unrelated edit.
The registered total changes from 39 to 40.

The new contract SHA-256 is
`2fe2996405e524c7c57c10f5936068ae9eed104d5308725ae75ee522403a4a02`.
Its exact reconstructed output identities are:

- `.60` source: `e2154b17131f24939448295ff4dcbce62a1d4b050315044b4148aa7b7e083bdb`
- `.60` class: `5e2cd57d0aa54db6a793bd6d87c3891ce2c2185e207f3fa69d50eaaf15a27d68`

The runner independently compiles both the frozen `.59` and reconstructed `.60`
registries with JDK 21 and debug information. The first must reproduce the
previous v2 class identity; the second must match the actual `compileJava`
output byte for byte. These are isolated evidence compilations. The positive
Charging execution still loads the actual production build output.

All eleven other source pins and all 31 other class pins in the existing
32-class current closure stay unchanged. The immutable M41 archive, original
sources, original trace, `ChargingCoreTrace.java`, `ChargingCoreLoader.java`,
source and processed `classic-skills.json`, classic record ordinals and ordering,
and level-count denominators remain protected by the existing exact checks.
No NEW413 native behavior is established by this Charging companion.

## Required negative controls

All 52 registry variants must fail with `REGISTRY_413_NONAPPEND_CHANGE`:

- One level mutation for each of the 39 old registrations, including 412
- Old ID ordering, 412 proposal, 412 prerequisite experience, and 412 ID ordering
- 413 proposal, prerequisite identity and experience, category, level,
  controllability, any-learned-level gate, ID ordering, and record ordering

These source-admission controls supplement the unchanged executed Charging CP,
XP, and callback-order mutants, four loader controls, and report-lifecycle
control. They cannot replace the original/current behavioral differential.

## Invocation and Gradle wiring

Run after `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v3.py --report /tmp/charging-core-registry-v3
```

Each report path must be fresh. No refresh or skip mode exists. The v3 runner
does not start Gradle, a game, a native server, or a graphical process.

The only required task wiring change in
`gradle/current-geometry-proof-tests.gradle` is the `chargingM41CoreProof`
script value, from `scripts/verify-charging-m41-core.py` to
`scripts/verify-charging-m41-core-v3.py`. All task dependencies and historical
tasks remain required. The old runners remain callable independently:

```sh
python3 scripts/verify-charging-m41-core.py --report /tmp/charging-core-v2-rejection
python3 scripts/verify-charging-m41-core.py --contract-version 1 --report /tmp/charging-core-v1-rejection
python3 src/test/resources/current-geometry-proof/charging-core/registry-412-v2/verify-charging-m41-core-v1.py --project "$PWD" --report /tmp/charging-core-frozen-v1-rejection
```

On the appended 413 registry, v2 still rejects `REGISTRY_412_NONAPPEND_CHANGE`;
both v1 routes still reject `CURRENT_SOURCE_SNAPSHOT_CHANGED` for
`HighPowerSkills.java`. These are preserved predecessor outcomes.

## Recorded verification

The isolated v3 run passed 437 cases and compared all 6,427 trace lines exactly.
The unchanged trace SHA-256 is
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
Both registry class reproductions passed. All 52 registry mutations, three
executed Charging mutations, four loader guards, and report-lifecycle control
passed. The report records both `BOUNDED_ORIGINAL_M41_CORE_DIFFERENTIAL_PASS`
and `ONLY_REVIEWED_NEW413_APPEND_PROVED`.

Evidence is retained under `../verification/v68-braking-touch/`:

- `charging-core-registry-413-v3-r1-result.json`
- `charging-core-registry-413-v3-r1.log`
- `charging-core-registry-413-v3-r1-evidence.tar.gz`
- `charging-core-registry-413-v2-rejection-r1.log`
- `charging-core-registry-413-v1-rejection-r1.log`
- `charging-core-registry-413-frozen-v1-rejection-r1.log`

This bounded companion pass does not establish aggregate `check` success or
change the status of any historical guard.
