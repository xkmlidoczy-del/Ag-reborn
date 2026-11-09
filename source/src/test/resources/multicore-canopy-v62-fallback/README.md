# Independent .62 NEW251 fallback proof

From the repository root, with Java 21:

```sh
python3 scripts/verify-multicore-canopy-v62-fallback.py --java-home .tools/jdk-21.0.12.1+1 --report /tmp/multicore-canopy-v62-proof-new
```

The report directory must not exist. `--frozen-only` tests the frozen capture and rendering/cleanup mutation sensitivity without claiming current fallback equivalence. There is no refresh or golden-update mode.

## Frozen input and actual execution

`frozen/` holds nine **complete, unmodified** production files extracted using `git show c1c02209960c329ab7e304562d2f102718791ec5:<path>`: `MulticoreCanopyEffects`, `MulticoreCanopyMesh`, `MulticoreCanopyState`, `FoldedMirrorMesh`, `FoldedMirrorState`, `FoldedMirrorGeometry`, `FoldedMirrorMaterial`, `FoldedMirrorRenderTypes`, and `BeamEtchingMesh`. This is the actual .62 renderer and its production mesh/state/material closure, including the crossed-chunk calculation. Source and asset hashes are pinned in the script and manifests. All ten original texture hashes were checked against that commit's Git objects. Current closure files other than the optional hook in Effects must stay byte-identical.

Each lane compiles into an empty, independent class directory using explicit Java21 source/target, no annotation processors, no implicit compilation, no source lookup, and only the declared sources and hosts. The current closure adds complete CrownMaterial, CrownGeometry, CrownBudget, EmissionGeometry, and HighPowerVisualConfig sources. No host replaces production geometry, clipping, budgeting, receipt validation, lifecycle, shader material uniform selection or render-type selection. Class inventories, every input hash and the runtime/compiler identities are receipted.

The harness invokes the real receive/tick/render methods. Captures contain actual CPU vertex submission order, raw and pose-transformed coordinate float bits, UV float bits, RGBA, render-type/shader/texture/depth/cull/blend/write/output choices, uniform writes, buffer flushes and native-font call arguments. Full accepted receipt/fence/marker/lease/visibility/flash state and input notifications are captured independently. Every render checks state immutability and model-view, pose and shader-color restoration.

The immutable baseline contains 312 cases, 233 nonempty, 227,784 geometry vertices and 573 font calls. Peak geometry is 6,144 vertices, exercising the original 1,536-quad frame cap. The script must reproduce both the immutable case table and decompressed complete-capture hashes before current comparisons begin.

## Coverage

- Ordinary, near-source, transformed-pose, world-border and behind-camera scenes
- DEPLOY, ARMED, WARNING, HELD, FIRE, one/three/six received cores, source ages and orb animation frames
- Every accepted ray outcome (MISS, BLOCKED, REFUSED, DAMAGED, ABSORBED), interpolation endpoints, all seven target statuses and LOST visibility expiry
- ENDED with and without retained accepted FIRE receipts, spent cores, retained duplicate-source tails, expiry at four ticks and nearly transparent final label omission
- Duplicate/repeated FIRE, stale ENDED rejection, two retained flashes, pause/death, stale lease, owner/world/player/connection/camera/input invalidation, queued invalidation and unloaded chunks
- Five owners with the four-visible cap and a saturated 1,536-quad scene
- Exact frozen equality for OFF, forced fallback, missing/corrupt glow, missing/corrupt white, unloaded config, pending reload, stale success after a newer failed reload, successful generation changes midframe and optional availability changing midframe
- Enabled integration must change real submissions, preserve the original vertex sequence as a subsequence, preserve every label call and all receipt state, keep classic-empty cases empty, and respect geometry plus conservative font reservations
- First/partial vertex, typed flush, final cleanup flush and combined vertex/cleanup failures, with exception propagation, state restoration and identical next successful rendering
- Separately compiled coordinate, UV and color mutants must differ from frozen output. Removed resource-hash and stale-reload guards must fail their actual resource lanes. Removing nested-finally cleanup must fail a real throwing final flush

The enabled integration is a bounded CPU smoke check. New crown/emission geometry correctness is exercised separately by dedicated geometry/budget regressions and native acceptance; this differential does not replace them.

## Platform boundaries and limitations

`hosts/` starts from the existing .61 proof's reviewed deterministic CPU hosts. NEW251 adds controlled packet DTOs and eligibility/input notifications, entity position/distance, native-font call capture, affine translation/quaternion rotation, shader registration/uniform capture and explicit original-white/glow resource streams. The host inventory is pinned; unknown, missing or edited hosts are rejected. Runtime inputs are not discovered from the rest of the checkout.

`native-api-review.txt` records JDK21 `javap` declarations from cached NeoForge21.1.252/Minecraft1.21.1 and JOML1.10.5, and `native-api-provenance.json` pins their jar hashes. New Font, Camera, ShaderInstance, Uniform and RegisterShaders call signatures were checked against those declarations. The host implementation is a deliberately bounded source-compatible call surface, **not** a native ABI or runtime substitute. Transport DTO constructors do not validate packets; the actual production receive method and state validator do. The eligibility host is controlled, so this is not a client eligibility or network-protocol test.

The font host records complete draw-call arguments, assumes a deterministic six-unit test glyph width and accounts for the original conservative four-quad reservation. It does not emulate native font glyph tessellation. The shader host records selected shader identity and uniform updates but does not parse GLSL, compile/link a shader or simulate GPU execution. Matrices are deterministic float CPU hosts, not JOML binaries. No native pixel, driver, translucent sorting, resource reload scheduling, authoritative damage or broad gameplay compatibility claim is made. Runtime injection controls callback order explicitly. Source/target21 does not assert `--release 21` API compatibility.
