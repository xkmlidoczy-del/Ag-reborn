# Portable Magnetic Hook acceptance

This increment makes the completed Magnetic Hook evidence rerunnable from a normal clean source archive. It supplements the original Hook item/entity/renderer/recipe payload; it does not replace that implementation or its17 native fixtures. No `.reference` or `.staging` directory is needed by any acceptance task.

Run with a JDK21 toolchain:

```
./gradlew classicHookAcceptance
```

The same four finite tasks are dependencies of `check`:

- `classicHookSourceTest`: verifies the packaged fixture hashes, compiles the15 unchanged originals,42 necessary API/world/GL shims and unchanged completed oracle harness into an isolated temporary directory, then executes the original oracle. It checks14,396 assertions, including2,000 source physics updates, six faces, source item/entity lifecycle, persistence/watcher behavior and exact renderer/item GL matrices. It also executes the explicitly repaired original duplicate-return, cancelled-spawn/return and reloaded-flight edge cases
- `classicHookDataTest`: verifies the complete pinned fixture manifest and retained licenses, source declaration41 among49 recipes, actual modern five-plate/three-hook recipe, obtainable reinforced-iron-plate tag membership, source model/texture identity, GUI icon mapping and configurable hook metal membership
- `classicHookVisualTest`: runs the existing Hook native buffer regression with the packaged source meshes supplied through an ordinary test-resource path. It checks689,950 assertions across both616-triangle meshes, six entity orientations and eight item/hand matrices, preserving UVs, packed light/overlay and finite normals
- `classicHookServerLinkTest`: runs the existing cold-link test with all client namespaces denied. Its10 common/fixture classes are linked without game bootstrap

The fixture root is `src/test/resources/classic-hook-source`. The manifest records supplied-source attribution to AcademyCraft1.0.7, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, and LambdaLib1.2.3, plus exact file hashes. This does not newly resolve those Git identities. Original source headers are unchanged. The completed `oracle.SourceOracle` harness and shims are copied byte-for-byte from the earlier passing oracle rather than reconstructed.

The packaged assets are exactly the two original hook OBJs, both hook textures and `default.recipe`. Full GPLv3, verbatim upstream additional notices and the LambdaLib MIT license accompany the witnesses. No songs, third-party cover art or unrelated assets are added. These materials remain test resources; legacy API shims never join main/test compilation or a release mod JAR. The source task places its output first in a platform-parented child classloader and deletes that temporary directory afterward, keeping old1.7 API identities isolated from the port.

Source-world/ray/GL shims are programmable external scaffolding, not a second Hook implementation. The independent oracle proves original source behavior and transformations; it does not prove the old game engine's collision behavior, actual server bootstrap, runtime world persistence, rendered pixels or audio. Buffer emission and cold linking are also finite headless checks. Actual17 native fixtures and client/earned-world/restart/unload/reconnect acceptance remain the owner's serial runtime lane.

The acceptance increment is validated by isolated cached-JDK compilation of a clean source projection containing no private reference/staging directories and no precompiled port classes. Its four entrypoints are executed directly with only freshly compiled port code and official cached dependency JARs. Gradle task execution and native/client/UI acceptance are not run by the staging worker; the owner must verify the task wiring in the serial lane.
