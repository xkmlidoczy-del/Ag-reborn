# Charging core companion v4: bounded NEW421 registry append

This versioned companion admits only `shear_windblade`, proposal 421, appended
after the approved 40 registrations in TEST0.60. It retains the original-M41
Charging core differential, exact class admission, and unchanged trace driver.
It does not establish NEW421 gameplay behavior or aggregate `check` success.

The three historical failures remain separate and unchanged:
`classicChargingGeometryTest`, `classicMeltdownerGeometryTest`, and
`classicThunderBoltGeometryTest`. No historical guard is repaired, excluded,
rebaselined, or relaxed by this companion.

## Immutable predecessors and real baseline

The new runner is `scripts/verify-charging-m41-core-v4.py`. Its contract is
`src/test/resources/current-geometry-proof/charging-core/registry-421-v4/contract-v4.json`.
All v1/v2/v3 runners, contracts, fixtures, and prior companion documentation keep
their exact bytes. The v4 contract audits 13 predecessor inputs, including the
v3 runner, v3 contract, `.59` fixture, and v3 documentation in addition to all
nine inputs already protected by v3.

The new `registry-421-v4/HighPowerSkills.v60.java` fixture was extracted with
`git show 318d2a8fb1d7827af029fda38edb0a60a4fd6f05:src/main/java/cn/academy/port/highpower/HighPowerSkills.java`.
This is the real TEST0.60 baseline, with 40 records ending in `BRAKING_TOUCH`
(413). The runner also requires the full pinned source chain:

1. The v1 `.58` source plus only the v2 NEW412 edits equals the frozen `.59` fixture
2. The frozen `.59` source plus only the v3 NEW413 edits equals the frozen `.60` fixture
3. The frozen `.60` source plus only the v4 NEW421 edits equals the current source

Every stage applies exactly three single-occurrence hunks, checks the pinned
output hash, reverses all hunks, and requires byte-exact recovery of its input.
The `.59` and `.60` class identities also remain linked to the unchanged
predecessor contracts' reconstructed class identities.

Key immutable identities:

- v1 contract: `18764bbfd49693e9875928905caafc02016d9d6e499e19a875a8344216d32b3c`
- v2 contract: `8c27b2cd7f5251379235d64d45ff4716f7d9e4683f0a6bc0fb7049a5e939936a`
- v3 contract: `2fe2996405e524c7c57c10f5936068ae9eed104d5308725ae75ee522403a4a02`
- v3 runner: `e714e97ea1e055dbc56eef32dc9fe4a4859431810c6fd6547f7cb2e4bbbc8f2d`
- `.60` source: `e2154b17131f24939448295ff4dcbce62a1d4b050315044b4148aa7b7e083bdb`
- `.60` class: `5e2cd57d0aa54db6a793bd6d87c3891ce2c2185e207f3fa69d50eaaf15a27d68`

## Exact append and output identities

The only approved edits are:

1. Declare `SHEAR_WINDBLADE = "shear_windblade"` and `SHEAR_WINDBLADE_PROPOSAL = 421`
2. Append `SHEAR_WINDBLADE` last in `IDS`, after `BRAKING_TOUCH`
3. Append its record last in `ALL`: `vecmanip`, level 2, controllable, learned
   `DEFLECTION_STRIKE` (411) with prerequisite experience 0, and
   `anyLearnedSkillLevel = 0`

The total changes from 40 to 41. No old registration, constant, method, or other
source byte can change. The new immutable contract and reconstructed identities
are:

- v4 contract: `5864314d211186bb93dce10a7bd9bda6ec73e971807e90a7afa184c6213652a8`
- `.61` source: `418c8603673cf25a00cfc441955d7dda9df57364fc7374fe6626f01f298f9068`
- `.61` class: `2685492a2ba0e22e7a216829afc8f0f2f5243af9553969dcbefb2ef870739989`

JDK 21 evidence compilations use the same `--release 21 -g -encoding UTF-8`
settings as the prior companion. The frozen `.60` class must reproduce its
unchanged v3 identity. The reconstructed `.61` class must match the actual
`build/classes/java/main` production class byte for byte. The runner executes
the actual production output as its positive candidate; evidence compilations
are never substituted for it.

All eleven other v1 source pins and all 31 other production class pins stay
unchanged. The 32-class production closure is unchanged, including the rejection
of unexpected inner/companion classes. Original M41 archive and source bytes,
official dependency classes, source and processed `classic-skills.json`, classic
ordinals, record ordering, and classic level-count denominators remain protected
by the original exact checks. There is no hash-refresh, skip, or relaxed mode.

## Required controls and bounded behavior

All 62 registry mutations must reject with `REGISTRY_421_NONAPPEND_CHANGE`:

- One level mutation for each of the 40 old registrations, including 412 and 413
- The 13 explicit predecessor controls from v3: old ID ordering, 412 proposal,
  412 prerequisite experience, 412 ID ordering, and all nine 413 field/order controls
- Nine NEW421 controls: proposal, prerequisite identity and experience, category,
  level 2, controllability, any-learned-level gate, ID order, and record order

The unchanged executable Charging CP, XP, and callback-order mutations must
still differ from the original M41 trace. All four loader guard controls and
the fresh-report-directory lifecycle control remain mandatory. Registry source
controls supplement these executable checks and the full positive differential.

## Invocation and predecessor outcomes

After producing the real `compileJava`, `processResources`, and
`writeVfx2NativeClasspath` outputs, run with a fresh report directory:

```sh
python3 scripts/verify-charging-m41-core-v4.py --report /tmp/charging-core-registry-v4
```

The runner starts no Gradle task, game, native server, UI, or graphics process.
The aggregate `chargingM41CoreProof` script can select this new runner while
retaining every existing task dependency and historical guard.

The predecessor runners remain independently callable and must keep rejecting
the appended NEW421 registry:

```sh
python3 scripts/verify-charging-m41-core-v3.py --report /tmp/charging-core-v3-rejection
python3 scripts/verify-charging-m41-core.py --report /tmp/charging-core-v2-rejection
python3 scripts/verify-charging-m41-core.py --contract-version 1 --report /tmp/charging-core-v1-rejection
python3 src/test/resources/current-geometry-proof/charging-core/registry-412-v2/verify-charging-m41-core-v1.py --project "$PWD" --report /tmp/charging-core-frozen-v1-rejection
```

The expected v3 and v2 markers are `REGISTRY_413_NONAPPEND_CHANGE` and
`REGISTRY_412_NONAPPEND_CHANGE`, respectively. Both v1 routes must still reject
`CURRENT_SOURCE_SNAPSHOT_CHANGED` for `HighPowerSkills.java`.

## Recorded verification

The full bounded differential passed against the actual production `.61` class:
437 cases and all 6,427 trace lines matched byte for byte. The unchanged trace
SHA-256 is `baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
The report records both `BOUNDED_ORIGINAL_M41_CORE_DIFFERENTIAL_PASS` and
`ONLY_REVIEWED_NEW421_APPEND_PROVED`.

Both registry class reproductions, the three-stage source reconstruction chain,
all 13 predecessor hashes, all 62 registry controls, three executed Charging
mutants, four loader guards, and the report lifecycle control passed. All 65
fixed inputs were rehashed unchanged at the end of execution. The unchanged
v3, v2, and both v1 runner invocations retained their expected rejection markers
and created no false successful result report.

Evidence is retained under `../verification/v69-shear-windblade/`:

- `charging-v4-r1/result.json` and its complete per-run evidence directory
- `charging-v4-r1.log`
- `charging-v4-predecessor-rejections-r1/result.json`
- `charging-v4-predecessor-rejections-r1/{v3,v2,v1,frozen-v1}.log`

This companion pass does not change the status of the three historical guards
or claim complete native NEW421 behavior or aggregate `check` success.
