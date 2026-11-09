# Classic visual migration and fidelity status

This is a **first NeoForge 1.21.1 visual slice**, not a claim of full AcademyCraft
1.0.7 visual parity. The client code compiles against NeoForge **21.1.252**. No
side-by-side classic/modern game screenshot comparison has been performed.

## Provenance and exclusions

`scripts/migrate_assets.py` imports the supplied
`.reference/AcademyCraft-1.0.7` source checkout reproducibly. Each source/destination,
byte count, and SHA-256 is recorded in
`docs/reference-visuals/asset-manifest.json`.

- **624 upstream files are preserved byte-for-byte**
- **564 are runtime resources:** 516 texture PNGs, one texture animation metadata
  file, and 47 non-media sound OGG files
- **60 are reference-only:** 14 OBJ models, 22 LambdaLib GUI XML definitions,
  ten GLSL shader sources, the original sounds JSON, and 13 original Java/Scala
  visual/timing source files
- **Two files are adapted/generated:** modern `sounds.json` with explicit
  `academy:` sound references and obsolete category keys removed; an original
  one-pixel opaque white texture for buffer-rendered cylinder geometry
- **Eleven files are excluded:** the entire `assets/academy/media` tree, including
  songs, covers, configuration and template, plus the three song-specific item
  textures for only_my_railgun, level5_judgelight and sisters_noise

The upstream README and original source copyright headers are preserved. Read
`docs/UPSTREAM-README-1.0.7.md`: it declares GPLv3 **and additional restrictions**,
including a prohibition on selling the mod or its contents. This import does not
resolve the legal compatibility or ownership of every image/audio asset. A rights
review is required before redistribution; do not relabel the import as an
unqualified GPL-only asset pack. No third-party media song or cover is packaged.

Presence in runtime resources means the texture/audio bytes are available; it
does not mean every old item, machine, entity, tutorial, or screen has been ported.
The OBJ, XML and old shader files remain outside the built mod's resource tree.

## Implemented modern rendering

`cn.academy.port.client.ClassicEffects` is registered on the client game event bus
with `@EventBusSubscriber(modid="academy", value=Dist.CLIENT)`. It uses
`RenderLevelStageEvent.Stage.AFTER_PARTICLES`, `VertexConsumer`, and modern
`RenderType` buffers. There are no direct legacy GL calls, GL display lists,
LambdaLib dependencies, or old GLSL programs in the runtime adapter.

The world buffers use the vanilla position/texture/color shader, ordinary alpha
blending, no culling, depth testing with color-only writes, and the particles
target for Fabulous-mode composition. They are privately owned so world rendering
does not flush another mod's pending shared batch. At `AFTER_PARTICLES`, the
original event pose stack is used and only camera position is subtracted; camera
rotation is already on the engine model-view stack.

### Railgun beam

Parameters taken directly from `EntityRailgunFX`, `EntityRayBase`, and the original
composite/glow/cylinder renderers:

| Parameter | Classic value retained |
|---|---|
| Life | 50 ticks expressed as 2500 ms |
| Beam-length blend-in | 150 ms |
| Final alpha fade | last 1000 ms |
| Final width shrink | last 800 ms |
| Inner cylinder radius/color | 0.09; RGBA 241,240,222,200 |
| Outer cylinder radius/color | 0.13; RGBA 236,170,93,60 |
| Cylinder mesh | 12 sides; four sqrt-profile end bands |
| Inner head correction | 0.98 |
| Glow width | 1.1 blocks; cap length also 1.1 |
| Glow end corrections | -0.3 at start; +0.3 at end |
| Beam width wiggle | 0–0.3; maximum change 0.8/second |
| Glow wiggle | 0–0.1; maximum change 0.4/second |
| Glow alpha formula | beam alpha squared × (0.9 + glow wiggle) |

The original `railgun/blend_in.png`, `tile.png` and `blend_out.png` are used without
resampling. Procedural subarcs reuse original `arc/line_segment.png`, 15 template
patterns, three midpoint passes, width 0.3, max offset 0.8, branch probability 0.7,
branch-width multiplier 0.9, template lengths 2–3 blocks, radial offsets 0.1–0.25,
placement every 1–2 blocks, scale 0.3, 0.5 template-switch probability per tick,
and 0.3/0.4 show/hide probabilities. They are cleared at the original 30-tick mark.

### Weak Arc

`addArc` uses 20 cached template patterns, six midpoint passes, width 0.1, max
offset 1.1, branch probability 0.15, length-shrink 0.7, alpha-shrink 0.9 and
branch-width-shrink 0.7. It lasts ten client ticks with original 0.7 template-switch
and 0.1/0.4 show/hide probabilities, truncating the 20-block pattern by segment
start position as the old `draw(length)` path did. The original arc texture is
retained. Damage and reach stay server-authoritative.

### Hand charge

All **40 original `arc_burst/0.png`–`39.png` frames** are used directly at
**40 ms/frame** (1600 ms total). The animation stops rather than looping. The
first-person main-hand adapter uses original translation `(0.26,-0.12,-0.24)`
and scale `(0.4,0.4,1)`, mirrored for a left main arm. It adds geometry through
`RenderHandEvent` without canceling vanilla hands or the held item. Remote/third-
person charge is a camera-facing world billboard near the entity's hand vicinity.

## Client call contract

Call these **only from client code**, such as a client payload receiver:

```java
ClassicEffects.addRailgun(origin, direction, length);
ClassicEffects.addArc(origin, direction, length);
ClassicEffects.addCharge(entityId);
```

The entry points normalize direction, reject null/nonfinite/zero-direction inputs,
and marshal work onto the client thread. The railgun length is bounded to 256
blocks and Weak Arc to its original 20-block template extent; outstanding beams
and arcs are capped at 64 each, charges at 128 entity IDs. These are visual safety
bounds, not gameplay reach changes. A charge received again restarts its animation.
Effects are discarded on disconnect or level/dimension replacement. Millisecond
animations stop advancing during an integrated-server pause.

These methods **do not play sounds or apply gameplay effects**. The payload handler
is responsible for sound playback (`academy:em.railgun` / `academy:em.arc_weak`,
original volume 0.5) and must avoid duplicate playback.

All Minecraft client references are confined to the client package. Dedicated
server/common initialization must not load or invoke these classes.

## Deliberate differences and unfinished fidelity work

- The midpoint geometry adapter processes new branches on the next pass. The
  original mutated its branch/path collections during iteration and baked random
  ribbon rotations into GL display lists. This safer CPU implementation keeps the
  original controls and textures but is not byte- or pixel-identical geometry
- The old LambdaLib `ViewOptimize` first-person origin correction and player arm
  render hooks have not been reproduced exactly. Third-person charge attachment
  is a hand-vicinity billboard, not a skeleton-attached effect
- Beam cylinder emission uses a modern unlit textured buffer rather than the old
  ShaderNotex path; modern depth/transparency sorting can differ
- GLSL masks, CP HUD compositing and screen XML are preserved as references, not
  interpreted. Any provisional modern HUD/developer screen is a functional adapter
  and should not be presented as a replica of the classic interface
- Other ability effects, custom machine/entity OBJ rendering, shader packs,
  accessibility scaling and graphics-mode interoperability still require testing

## Verification

Checks completed for this slice:

1. `scripts/gradle-cloud.sh compileJava --no-daemon`: passed against pinned
   Minecraft/NeoForge APIs; no error from the visual classes
2. `python scripts/migrate_assets.py --check`: all 624 exact-byte imports,
   two adapted/generated files, 40 charge frames and exclusion guards verified
3. PNG decode/structure validation: all 517 packaged texture PNGs passed Pillow's
   image validation
4. Pure `ClassicEffectTimeline` boundary assertions: beam extension/fade/shrink
   boundaries and frame transitions 0/39/40/1599/1600 ms checked independently

Not yet verified: rendered appearance, depth occlusion and hand alignment in a
running client, side-by-side classic screenshot parity, Fast/Fancy/Fabulous
composition, reload behavior, third-person/spectator views, dimension changes,
and a dedicated-server runtime launch. Compilation is not a visual parity test.

Before claiming parity, capture the same scene, camera, FOV, GUI scale and effect
ages in 1.0.7 and 1.21.1. Compare beam onset (0/75/150 ms), steady beam, 1500/1700/
2100/2499 ms decay, charge frames 0/10/20/39, both main arms, and first-/third-
person views. Test a beam directed along the camera axis, vertical beams, opaque
block occlusion, multiple simultaneous effects, pause/resume and reconnect.

## API sources inspected

The exact downloaded source artifact is
`build/moddev/artifacts/neoforge-21.1.252-sources.jar` (including Minecraft sources).
The official NeoForge 1.21.1 sources/patches were also inspected before coding:

- [RenderLevelStageEvent](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/event/RenderLevelStageEvent.java)
- [RenderHandEvent](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/event/RenderHandEvent.java)
- [ClientTickEvent](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/event/ClientTickEvent.java)
- [LevelRenderer event-stage patch](https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/client/renderer/LevelRenderer.java.patch)
- [NeoForgeRenderTypes](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/NeoForgeRenderTypes.java)

The source checks confirm the timing/event APIs and framebuffer stage. They do
not establish the rendered appearance of this implementation.
