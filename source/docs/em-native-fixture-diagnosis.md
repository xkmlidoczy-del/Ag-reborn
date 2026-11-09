# Native EM fixture corrections after actual aggregate115 run

Evidence: `docs/runtime-evidence/m08-em-portable-validation.log` (parent-run native server).115 tests completed,113passed, twofailed. Worker made no native/Gradle/client launch.

## Existing preset test had an obsolete port-status expectation

`presets_reject_malformed_duplicate_unlearned_passive_unported_and_raw_cast_packets` explicitly learned `mag_manip` then expected editing it into slot1 to fail. MagManip is now actually implemented/selectable. It cannot continue serving as a learned-but-unported test case.

Narrow fix changes only this fixture's learned skill and rejected request from mag_manip to mine_detect. MineDetect is a genuine Electromaster source catalog skill, still outside the implemented active allowlist, and the level5 fixture continues to isolate unported rejection instead of unlearned/category/level rejection. Production preset dispatch/validation stays unchanged.

## ThunderBolt radius8 fixture used a Float-rounded eye anchor

Original fixture: `double eyeY = 1 + primary.getEyeHeight()`. The int1 promotes to Float because getEyeHeight returns Float. Addition rounds in Float, and assigning to Double happens afterward.

Production endpoint: entity feet have Double world coordinates; `primary.getY() + primary.getEyeHeight()` promotes eyeHeight to Double before addition. At the observed negative world-height anchor, standard1.62f is1.6200000047683716; old local Float addition rounds2.619999885559082. The boundary entity's feet therefore differ inY by-1.1920928955078125e-7. With exact horizontal distance8 its squared distance is64.00000000000001, genuinely outside the sphere.

Canonical LambdaLib1.2.3 WorldUtils.getEntities constructs the bounding cube then ANDs EntitySelectors.within(x,y,z,range). EntitySelectors.within uses entity feet distanceSquared<=rangeSquared, including exact64 and excluding values above64. Production ThunderBoltRules.withinAoe already matches this source rule. Adding an epsilon would change gameplay to accommodate a faulty fixture and is not justified.

Narrow native fix anchors boundary/outside/corner entities directly to the actual Double primary world endpoint. The fixture also asserts exact distanceSquared==64 before expecting boundary selection/damage. Existing assertions still verify just-outside exclusion, cube-corner sphere exclusion, primary exclusion, occluded AOE damage, resources and cooldown. The targetAt helper reuses the fixture's owned spawn/cleanup path and then moves to the explicit world-space point.

## Staged deliverables and verification

- `docs/em-native-fixture-fixes.patch`: two native fixture files only; dry-run passed
- `src/main/java/.../AcademyPresetRuntimeTests.java`: revised full native class, manually compiled
- `src/main/java/.../AcademyThunderBoltRuntimeTests.java`: revised full native class, manually compiled
- `src/test/java/.../ThunderBoltFixturePrecisionRegressionTest.java`:28 passed standalone arithmetic/source-predicate assertions at origins-60,-123,0,80. Reproduces old Float-boundary exclusion and verifies exact boundary, just-outside and cube corner

Native corrected tests remain UNRUN. The parent's actual113/115 evidence predates these changes. No production file, gameplay mechanic, solar registration or Gradle task changed. Apply only the patch, then parent owns serial native rerun. Copying the original earlier integration full-file snapshots now would be wrong; those snapshots were captured before later production integrations.
