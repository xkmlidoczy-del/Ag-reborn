# Clean test compilation: explicit oracle support imports

The executable original RangedRayDamage test wrapper imports BeamOracleSupport
contract stubs. Later AdvancedMine test fixtures introduced top-level Motion3D
and RandUtils in the same test package. Java gives these same-package classes
priority over wildcard imports. Incremental/cached compilation had retained an
older correct RangedRayDamage class and masked the source ambiguity.

A fresh full compile first rejected Motion3D's incompatible entity/vector types.
After that name was bound explicitly, the literal ray differential exposed the
second name: RandUtils had resolved to the separate random generator rather than
the deterministic Beam contract used by that oracle. Comparing actual compiled
class references confirmed both causes.

The wrapper now explicitly imports BeamOracleSupport.Motion3D and
BeamOracleSupport.RandUtils. The upstream algorithm, arithmetic, original fixture
bytes, Beam support behavior and production gameplay are unchanged. No assertion
was relaxed. Each oracle still uses its own original contract.

Independent m16 source restore passes `clean build --offline --no-build-cache`
with all128 ordinary tasks plus clean, in58 seconds. Its gameplay JAR is byte
identical to the previously native-tested m16 artifact. Independent m15 restore
passes the same forced compilation with all109 ordinary tasks plus clean, in54
seconds. Its existing gameplay JAR is likewise byte identical. Cached official
Minecraft/NeoForge dependency artifacts remain available; the Gradle build-output
cache is disabled and every project Java source is compiled afresh.

The repaired m15 source package preserves its27-skill checkpoint and existing
gameplay JAR. M16 preserves its35 integrated active paths and256 passing native
tests. Neither checkpoint establishes a complete mod, a complete natural
survival playthrough or full audiovisual fidelity.
