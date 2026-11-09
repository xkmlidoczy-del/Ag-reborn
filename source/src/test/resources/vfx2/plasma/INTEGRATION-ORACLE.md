# Plasma VFX2 integration oracle

Run from the source checkout:

```sh
python3 scripts/verify-plasma-vfx2-integration.py --report /tmp/plasma-vfx2-integration.json
```

The verifier uses an existing JDK 21, pinned JOML 1.10.5, and the pinned
Minecraft 1.21.1 / NeoForge 21.1.252 source archive. It launches no Gradle build,
Minecraft client, download, or graphics context. `--joml` and
`--pinned-minecraft-sources` accept explicit existing artifacts.

## Immutable baseline

`baseline-52/ClassicVectorFinalEffects.java.txt` is the exact source from
`fab28e15b6ba5f720c2ffc150302f162851576fb` (.52). Its SHA256 is
`38b5986dae24b6452b71c35629908150fccd7d98401c9928bd2212f3b0874544`.
The script checks the archived bytes and checks the Git object when available.
It also reverses only the reviewed additive VFX2 integration hooks and requires
the result to match the entire .52 source byte for byte. Do not refresh this
fixture or its hash from the current source.

The original timeline, noise, camera transform, shader upload, render types,
three original shader resources, movement source and inherited recording
scaffold are independently pinned. No older regression script is changed.

## Compiled checks

Both effect sources are compiled without edits in separate class directories
with the actual optional helper, material and timeline, actual classic render
math and native JOML matrix/quaternion arithmetic. Minecraft/resource/GL entry
points use recording hosts inherited and extended from the Light Shield oracle.

The differential compares 42 lifecycle/camera frames in each of 12 cases:
OFF, local/global forced fallback, constructor/compile failure, absent resource,
missing/inactive uniform, failed link, mid-upload exception, closed shader,
reload awaiting completion, and unloaded config. It compares raw and transformed
vertex coordinates, all pose entries, UV/color, classic uniform upload order and
float bits, material/flush order, alpha and clock, movement/lifetime state and
active sound object count. Scenarios include pause/resume, READY, release, GO,
actual and stale positions, travel, END, expiration, a rotated view, and world
coordinates near 30 million.

Separate compiled assertions cover accepted/stale token and sequence gates,
finite GO destination, no GO on local release, no END impact cue, cleanup on
replacement/connection change, and pause. Real production submissions verify
invalid/zero alpha uses no budget, four enhanced bodies and a fifth classic
fallback, dense-history output stays within 512 enhanced vertices per frame,
the status counter equals recorded submissions, and near-eye/large-world
geometry remains finite. Two deliberately broken temporary compilations prove
the differential catches classic-alpha drift and an inverted fallback branch.
The optional F2 diagnostic observer is exercised with its flag off and on:
recorded rendering is identical, image/cancellation/destination APIs are trapped,
and effect state, uniforms and geometry remain unchanged by the callback.

## Boundaries

This is CPU recording evidence, not GPU execution or visual acceptance. Shader
loading/link/uniform failures are injected by hosts; GLSL is not executed.
Render-state declarations and defaults are checked against pinned Minecraft
sources, but native driver state, framebuffer pixels, blending/occlusion and
performance are not observed. The HUD overlay is not exercised. Gameplay and
damage are not simulated; only the pinned original `move` method is compiled
through a minimal movement host. Native client captures and gameplay tests
remain separate evidence.
