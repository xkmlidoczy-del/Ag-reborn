# Migration and fidelity checklist

This checklist distinguishes executable gameplay from cataloguing and original assets from modern rendering. A successful build is not a full-port or gameplay-parity claim.

## Verified development baseline

- [x] Isolated new cloud workspace; no user's local computer or unrelated repo touched
- [x] Canonical AcademyCraft 1.0.7 source, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`
- [x] Java21, Gradle8.10.2 with official SHA256, ModDevGradle2.0.148, NeoForge21.1.252 pinned
- [x] Full Minecraft/NeoForge source pipeline and project compile
- [x] Mod JAR generation and deterministic progression/catalog/NBT/timing tests
- [x] Native GameTestServer runtime: 76 required world tests executed and passed after user-approved EULA
- [x] Native on-disk ability/inventory/finite-energy and coin graceful refund reload across two distinct JVMs
- [ ] Real standalone dedicated-server socket/client multiplayer and packaged production-install smoke
- [x] Bounded actual client startup/world/input/resource/same-JVM reopen smoke and screenshots
- [ ] Current Charging live-client test, audio playback and classic side-by-side captures

## Core

- [x] Four stable category IDs and50 exact category/skill entries, learning dependencies and ordering
- [x] CP/overload resource rules, regeneration delay/rates, usage growth, recovery lockout, cooldowns
- [x] Classic level-progress counts, raw mastery progress, upgrade reset
- [x] Generic Brain Course (+1000CP), Advanced Brain Course (+1500CP/+100overload), Mind Course (+20%CP recovery)
- [x] Server validation, NBT persistence and side-separated packets/client code
- [x] Finite portable developer energy/item capability, induction factors, source stimulation/action engine and main-hand restrictions
- [x] Development item, HUD and key controls for testing
- [x] Portable source timing/energy/completion validation; three tier constants, reset-action engine
- [x] Four source presets, server-owned bindings, source controls/editor and persisted cooldown maxima
- [x] Source-textured CP/key HUD actual default-scale client observation
- [ ] Normal/advanced developer machines, wireless charging networks, complete original GUI layouts
- [ ] Config-driven event/pipeline API compatibility, other passives, interference sources
- [ ] Survival recipes, ores, progression devices, guide/tutorial/media systems and achievements
- [ ] Existing1.7.10 world/save migration; unprovided Windows800MB archive has not been read

## Gameplay

- [x] Arc Generation server logic and weak-arc modern rendering adapter
- [x] Electron Bomb orb/ray, Directed Shock original curves, Teleporter marker/trail/critical visual adapters
- [ ] First-skill live behavior/visual parity verification
- [x] Railgun iron item20-tick charge, cost/damage/mastery/cooldown and modern beam/charge adapter
- [x] Railgun logical coin physics/return/singleQTE and client coin adapter, token validation/refund escrow
- [x] Reflect-event hook, reflected ray length/damage and per-shotEXP branch
- [ ] Native coin entity/multiplayer/hard-crash parity, Vector Reflection ability, original block-ray randomness and achievements
- [x] Current Charging held item/block-mode server path, finite IF/FE adapter and source-style client visuals
- [ ] Remaining Electromaster skills
- [x] Electron Bomb first-skill server costs/launch/delayed collision logic
- [x] Radiation Intensify server marks/customized mastery
- [ ] Remaining Meltdowner skills and full visual/runtime equivalence
- [x] Threatening Teleport first-skill server behavior/needle/critical passives
- [x] Marker/trail and modern critical event/formula adapters
- [ ] Remaining Teleporter skills and full marker/animation/runtime equivalence
- [x] Directed Shock first-skill server hold/trace/impulse logic
- [x] Source CubicCurve prepare/punch hand animation adapter
- [ ] Remaining Vector skills/reflection and full hand-attachment parity

## Assets and rendering

- [x] Original non-song textures and effects audio retained byte-for-byte with SHA256 manifest
- [x] Railgun40frame×40ms charge sequence;2.5s beam;150ms extension;last800ms shrink;last1000msfade
- [x] Modern buffers/shaders used instead of immediate OpenGL/display lists
- [x] Original procedural arc parameters re-expressed in bounded CPU geometry
- [x] Original OBJ/GLSL/XML and render-source references preserved for later adapters
- [ ] Original models, shaders and LambdaLib GUI definitions wired to modern runtime systems
- [ ] First/third-person hand and arc geometry pixel parity; current attachment/branching are approximations
- [ ] Shader/resource-pack/graphics-mode interoperability, interrupted/cancel/repeated-cast client checks

## Distribution and provenance

- [x] Original copyright, README terms, standardGPLv3 text and MDK notices preserved
- [x] Third-party media songs/covers/song-specific item textures excluded from packaged outputs
- [x] New source code remains provided with development artifacts
- [ ] Rights to any future third-party soundtrack redistribution resolved
- [ ] No public upload/push/release has been requested or performed

See `classic-behavior-audit.md`, `classic-skills.json`, `visual-fidelity.md` and current runtime test report for exact facts and caveats. `.reference`, caches and downloaded toolchains must be excluded from deliverable archives.


## M07 single-player developer and energy increment

See m07-single-player-checkpoint.md for actual integrated behavior, native/client evidence, the fixed font-owner regression and explicit natural-survival boundary. All92native tests passed; full302,273source/state assertions and115common coldlinks passed. This remains7integrated active paths/28unported, not7naturally obtainable skills. Generator power and missing Railgun prerequisites are the next blocking dependencies.
