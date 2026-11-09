# Narrow exact m13 integration

Promotion is parent-owned. The manifest enumerates additive main/test Java and complete pinned canonical source/test resources. Only those source/resource files are new production inputs. No original media or sounds.json copy is needed. Additive destinations must not already exist.

`integration/advanced-mining-m13.patch` is reconstructed from seven frozen exact baselines and proposed after snapshots:

1. AcademyGameplay: two token start routes, ordinary hold start/release/abort, tick and all existing lifecycle sites
2. PresetSkills: exactly Expert/Luck IDs appended; common SkillAvailability dynamically includes them
3. AcademyClient: six acknowledged effect kinds appended to existing beam packet switch. All current owns/input/end/clear/HUD hooks automatically cover the new variants
4. ClassicMeltdownerBeamEffects: independent contexts, inputs and token histories; exact composites, startup media and Luck flight-only texture
5. MeltdownerBeamSupport: ephemeral full-tier Fortune0/III loot tool and protection-preserving native break path. Basic's existing break method is unchanged
6. MeltdownerBeamLearningRegressionTest: replace a closed-world old.size()==15 assertion with containment of those same15 paths, allowing later additive implementations without weakening preservation checks
7. build.gradle: six ordinary JavaExec checks attached to test/check; no dependency/toolchain/run configuration change

Use `scripts/check_integration.py` to verify every exact before/after/additive/media/patch hash. It performs no writes. If current production differs from a baseline (including another worker's additions), rebase the narrow hunks against the live source. Never overwrite a whole snapshot or apply both sibling worker snapshots as replacement files. In particular remaining Meltdowner combat also changes AcademyGameplay, PresetSkills, AcademyClient and build.gradle. Keep both sets of additions.

The new test resources are ordinary src/test/resources, never main mod resources; they contain original copyright headers, GPLv3 text/upstream README restrictions and LambdaLib MIT notice. New tests require no .reference/.staging tree at execution. Optional oracleSourceRoot is a diagnostic compile-input pin, not a runtime requirement. `audit/no-reference-recovery.log` proves execution from an empty cwd with no source-tree path property; `audit/missing-fixture-recovery.log` proves missing mandatory canonical resource fails instead of skipping.

`verify_cached.sh` uses this workspace's pinned official Java21 and existing Minecraft1.21.1/NeoForge21.1.252 classpath for isolated javac only. Its .reference media check is development audit, not a Gradle/runtime prerequisite. No Gradle, game/server/client/world process or user computer is run by this worker.
