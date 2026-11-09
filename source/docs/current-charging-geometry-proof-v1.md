# Current Charging CPU geometry contract v1

Run after the existing `compileJava` and `processResources` outputs exist:

```sh
python3 scripts/verify-current-charging-geometry.py --report /tmp/current-charging-proof-v1
```

The report directory must be new. Optional `--project`, `--original-source`,
`--java-home`, and `--cache-dir` relocate inputs; their admitted contents remain
pinned. The verifier itself does not run Gradle, compile production, alter a
production class, launch a client, or rewrite an existing test or fixture.

## What passes

The positive subject is the twelve actual classes under
`build/classes/java/main`: `ClassicChargingEffects` and its four nested classes,
`ClassicChargingTimeline` and `Tokens`, and `ClassicArcGeometry` and its four
nested classes. The original M41 harness is compiled unchanged. No game-class
host is compiled or substituted.

The unchanged harness executes 84 scenes across beam, thin surround, normal
surround, six directions, pose rotations, clipping, and four camera positions.
It compares 74,880 coordinates, checks attributes and RNG/state stability, and
produces 6,240 near vertices. The original 224,976-byte near output must have
SHA-256 `1d5deaa48a5428da53205ce785878a340037392c92b0d06ecb843d35a981c1a7`.
The separate camera-motion check must retain all sixteen steps.

The independently recorded original M41 source ZIP digest
`8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b`
anchors the unchanged old runners, fixture indexes, fixture files, original
eleven API-class fingerprints, and the JOML archive fingerprint. All eleven APIs
are defined and checked for their actual `CodeSource` and unique class resource.
The current manifest additionally pins 1,104 individual production/API/host
classes, their selected archive members (including multi-release members),
eleven host resources, the JDK, and proof support sources. Additional host pins
establish current-version repeatability, not historical behavioral identity.

A platform-parent-only loader admits this explicit closure. It checks bytes
before definition, preserves the actual directory/archive `CodeSource`, checks
the unique selected resource, and rejects unknown production or host classes.
All read sources, class files, fixtures, archives, and JDK inputs are hashed again
before success; the twelve-class inventory must remain unchanged.

All ten executable negative controls are required: changed coordinate, UV and
RNG bytecode, wrong renderer origin, wrong API origin with identical API bytes,
stale class bytes, stale pin, duplicate subject, unknown production dependency,
and unknown host dependency. Coordinate and UV mutations must complete the
frozen scene matrix before their camera-motion/golden comparisons fail. The RNG
mutation must reach the original harness's RNG assertion. No mutation can earn a
passing result or change the actual compileJava output.

## Required separation from historical and gameplay admission

`PASS_CURRENT_CONTRACT_ONLY` is the sole success marker. The original merged
archive guard remains failed and is reported as `FAIL_UNCHANGED`; this proof
does not explain the archive difference or repair historical admission.

Six of the seven original Charging protected-logic source guards still match.
`CurrentCharging.java` has the separately admitted HighPowerCharging integration
digest `942952e7f8948c3ddc8b541294a029d481f81f76d417cfc103e4bca0dc9c2096`,
which differs from the original guard. Both digests and that historical failure
remain explicit in the report. This exception does not execute or prove the
changed gameplay logic. Any combined current-version Gradle task must require
the separate CurrentCharging core regression and native-entry proofs as well
as this geometry proof. The old Charging runner must continue running unchanged.

No first-person/event execution, game bootstrap, GPU appearance, sound,
native-client behavior, server behavior, original procedural renderer equality,
or full `check` pass is established here. Changing the current contract requires
a newly reviewed version; there is no automatic fingerprint-refresh mode.
