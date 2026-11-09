# .54 reliability evidence: historical guards and current companions

This is an evidence-only follow-up to delivered .54. It does not change a
production mechanic, renderer, texture, or the delivered JAR. The three historical
tasks remain required by `check`, with their original runners, source/asset
guards, frozen matrices, and expected hashes. They still fail. A current
companion pass is not an old-task pass or a green aggregate result.

## Why the historical tasks fail

All three first refuse the merged Minecraft/NeoForge API archive. Original M41
expects `59a14efc6e5c163fe7e1374b3cf66ace00dd20f1b847ae18dd5a08aee186d0c7`;
the current local build supplies
`87794ad77a8887b2eec81450c89637cf9db3c64ce5a11310f926af74fa07da59`.
The exact historical merged artifact is unavailable in the retained source
archive. It cannot be reconstructed by declaring a new expected hash. Current
artifact differences include access-transformed class bytes, not only ZIP dates.
The eleven individually protected API classes and the JOML archive still match
their original fingerprints; this does not prove all transitive behavior equal.

After that first gate, Charging also has an intentional source identity change
in `CurrentCharging`: the authorized enhanced transaction branch. Its classic
renderer and geometry remain protected. Meltdowner has intentional optional
Light Shield and first-person near-eye integration changes in
`ClassicMeltdownerStarter` and `ClassicMeltdownerBeamEffects`. Its historical
closed class list and reflective overload selection cannot admit those new
dependencies/signatures. ThunderBolt's later protected sources remain unchanged.
Restoring all historical source bytes would remove authorized functionality;
moving hooks elsewhere merely to evade a hash is not a behavioral repair.

## Separate current proofs

`currentThunderBoltGeometryProof`, `currentChargingGeometryProof`, and
`currentMeltdownerGeometryProof` execute actual current compileJava class
resources against the immutable original matrix outputs. They explicitly pin
current additional dependencies, exact API/signature/origin boundaries, and
reject undeclared dependencies. Executed coordinate, UV, RNG, and related
negative controls demonstrate that admission alone cannot produce a pass.
Current dependency fingerprints establish repeatability, not provenance for
the unavailable complete historical runtime. Each companion has its own scope
document and machine-readable result.

Charging additionally requires `chargingM41CoreProof`: ten original M41 source
files compile without edits and execute in an isolated loader against current
core behavior. `runChargingCompanionGameTest` separately tests positive-token
enhanced ingress and unchanged-M41/current native entry traces. Those entry
traces share current native dependencies and therefore do not establish a
whole-M41 server equivalence claim. Original classic callback cancellation
quirks are preserved; enhanced atomic cancellation is tested as a distinct
authorized contract. `currentChargingCompleteProof` runs all three layers.

Reports use fresh directories so a failed rerun cannot advertise stale success.
The original three tasks are executed explicitly as well as retained in `check`.
No expected historical hash, frozen matrix output, or old test exclusion changes.

## Limits

CPU geometry, exact state witnesses, native entry tests, and GPU appearance are
different evidence. These companions do not prove complete visual fidelity,
audio, multiplayer, default-hardware performance, or all possible native hook
combinations. The .54 client and GPU evidence remains separately labelled.
No new functional version is warranted solely by these additional tests.
