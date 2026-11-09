# Charging core companion v6: bounded NEW423 registry append

V6 admits only `yaw_palm`, proposal 423, after the 42 high-power registrations
protected by v5/.64. Its record is `vecmanip`, level 2, controllable, requiring
learned `BRAKING_TOUCH` at prerequisite experience 0, with
`anyLearnedSkillLevel = 0`. This companion establishes the narrow registry
admission for the unchanged M41/current Charging core differential. It does not
establish NEW423 combat, native contact, rendering, or packaging behavior.

## Immutable lineage

Runner: `scripts/verify-charging-m41-core-v6.py`.
Contract and baseline: `src/test/resources/current-geometry-proof/charging-core/registry-423-v6/`.

The contract pins all 17 predecessor inputs protected by v5, plus the unchanged
v5 runner, contract, frozen baseline, and companion document: 21 immutable
inputs. No predecessor runner, contract, source fixture, trace driver, or
companion document is changed. The frozen .64 source also equals git revision
`f4fe792242f1f8e84d1235d42b0ee375f8368ebb`.

The verified chain is .58 + NEW412 = .59, .59 + NEW413 = .60, .60 + NEW421 =
.61, .61 + NEW422 = .64, and .64 + only NEW423 = current source. Every stage
applies exactly three single-occurrence hunks, validates its fixed source
hash, reverses the hunks, and recovers the prior bytes exactly. The final hunks
add the ID/proposal constants, append one IDS entry, and append one Skill
record. All prior 42 records, constants, ordering, and methods remain exact.

Pinned identities:

- V6 contract: `4af2bdfdc3ee5142d5b68e6be0527f3232fc5937aa8b4eb70b6122cb407d5091`
- Frozen .64 source: `b8b9ba3ed70b97ad036d158e54417d6810dc8e36aaddced608bf9bbd06954f21`
- Frozen .64 class: `57ac856a078f7043bf6170b1289319e8f7592ad9544b36d6659424332946e726`
- NEW423 source: `2015d442417b24bb774574839ad903d2f6c5e5d75d9f39202752ac0f6fc76968`
- NEW423 class: `f4101cd82bc638a01a6701f9cdc1a80219b685cc2a5f787cc459089e891c44d6`

JDK 21 evidence compilation uses the unchanged `--release 21 -g -encoding
UTF-8` settings and reproduces old and new registry hashes. The new evidence
class must equal actual `build/classes/java/main` output byte for byte. The
positive differential always executes actual compileJava output. Evidence
compilation is never substituted as the positive subject. There is no refresh,
relaxed admission, or skip mode.

## Required negative controls and preserved boundaries

All 84 registry mutants must reject with `REGISTRY_423_NONAPPEND_CHANGE`:

- A level mutation for each of the 42 old registrations
- All 32 prior explicit registration/NEW412/NEW413/NEW421/NEW422 field and order controls
- Ten NEW423 controls: saved ID, proposal, prerequisite identity, prerequisite
  experience, category, level, controllability, any-learned-level, IDS order,
  and record order

The unchanged Charging CP, XP, and callback-order executable mutants must
still differ from M41. Unknown dependency, wrong origin, duplicate resource,
and wrong digest loader controls still reject. Reusing a report directory
rejects without replacing prior evidence. Non-registry source/class pins,
closed production class admission, dependency signatures, original archive,
source and processed classic catalog, classic ordinals, and classic level
XP denominators remain protected by the original checks and trace.

The separate historical `classicChargingGeometryTest`,
`classicMeltdownerGeometryTest`, and `classicThunderBoltGeometryTest` failures
are retained. V6 does not repair, exclude, or rebaseline them and cannot make
aggregate `check` a pass.

## Invocation and verification status

After real `compileJava`, `processResources`, and `writeVfx2NativeClasspath`:

```sh
python3 scripts/verify-charging-m41-core-v6.py --report /tmp/charging-core-registry-v6-fresh
```

`chargingM41CoreProof` selects v6 and retains the previous task dependencies.
The Python proof starts no Gradle, native server, game, UI, or rendering.

Initial CPU source verification passed the complete source lineage and reverse
roundtrip, 21 immutable predecessor pins, all 84 registry negative controls,
and the report-directory lifecycle guard. The new class pin was produced by
isolated evidence-only javac; this is not a production differential result.
All six predecessor routes were also executed against the appended current
source. V5/v4/v3/v2 retained their exact nonappend rejections; both v1 routes
retained `CURRENT_SOURCE_SNAPSHOT_CHANGED`. No rejected route wrote a success
report. These logs and source-control evidence are preserved at
`../verification/v73-yaw-palm/learning-cpu/charging-predecessor-rejections/`
and `../verification/v73-yaw-palm/learning-cpu/charging-v6-source-audit.json`.
The same directory contains `learning-result.json`, `javac.log`, source hashes,
and logs for all 20 passing CPU learning/progression/Foundation suites.
These use an isolated class overlay and are separate from production compilation.

The full standalone v6 differential then passed against the actual NEW423
compileJava output: all 437 cases and 6,427 trace lines match M41 byte for byte.
Trace SHA-256 remains
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
Both registry class reproductions, the complete five-stage source lineage,
21 predecessor pins, 84 registry controls, three executed Charging mutants,
four loader guards, and report lifecycle control passed. All 73 fixed inputs
were rehashed unchanged after execution.

The complete report is
`../verification/v73-yaw-palm/charging-v6-current/result.json`, with command
output in `../verification/v73-yaw-palm/charging-v6-current.log` and complete
run evidence beneath `charging-v6-current/run-qvpuwpv3/`. This standalone
Python invocation started no Gradle or native server. It retains the narrow
classic non-enrolled core scope and does not replace native Charging, NEW423
combat, or historical geometry acceptance.
