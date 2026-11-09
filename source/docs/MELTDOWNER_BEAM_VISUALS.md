# Classic Meltdowner and MineRayBasic client adapter

## Delivered verification

Additive isolated client code, a proposed AcademyClient overlay, deterministic standalone tests and original-asset audit. Cached Java21 javac compiles against the repository's Minecraft1.21.1 / NeoForge21.1.252 dependency classpath. The executable regression and asset audit are in `audit/client-verification.log`. No Gradle invocation, Minecraft/game process, local user computer, world, recording, packaging, publication or production/shared-file write was performed.

- `ClassicMeltdownerBeamEffects.java`: client packet/input/session ownership, actual composite glow+inner/outer beam submissions, source local walking speed, following audio and source particles
- `ClassicMeltdownerBeamTimeline.java`: pure source timing, geometry/viewpoint math and UUID/token/particle replay gates
- `MeltdownerBeamVisualRegressionTest.java`: independent source-formula, rotation-matrix, geometry, source-binding and ordering differentials
- `scripts/verify_client_cached.sh`: reproducible cached compile/regression/overlay compile/asset audit
- `scripts/verify_client_assets.py` and `audit/client-original-assets.json`: source byte and decoded-RGBA/Ogg/event audit plus exact production reuse inventory
- `MELTDOWNER_BEAM_CLIENT_HOOKS.md`: agreed payloads, ten narrow hook sites and lifecycle contract

## Source provenance

AcademyCraft classic1.0.7 commit00d19ec0cf538f61c1095c9292f5ee6863db4521: `Meltdowner.scala`/MDContextC, `MineRaysBase.scala`/MRContextC, `MineRayBasic.scala`, `EntityMDRay.java`, `EntityMineRayBasic.java`, `EntityRayBase.java`, `RendererRayComposite.java`, `RendererRayGlow.java`, `RendererRayCylinder.java`, both renderer bases, `MdParticleFactory.java`, `ACSounds.java` and `FollowEntitySound.java`.

LambdaLib1.2.3: `Motion3D`, `ViewOptimize`, `MathUtils`, `RandUtils`, `GameTimer`, `Particle`, `Rigidbody` and `EntityAdvanced`/`EntityX`. Existing starter adapter's cylinder mesh/particle quad, particle fades, pause clock, random walk and physical-input helper are reused rather than duplicated. AcademyCraft-derived material/code remains GPLv3 and LambdaLib-derived algorithms MIT under root NOTICE/license information.

## Deliberately retained source behavior

### Charge

- Client charge ticks increase on source client ticks; local abilities walking speed is literally0.1F-ticks*0.001F, without invented clamping. Termination restores0.1F, rather than saving an arbitrary prior speed. Negative values after100 are kept; source server normally terminates at tick101. This changes only the local Player abilities walking-speed field, with no server attribute or abilities packet; modern server movement reconciliation may differ
- FollowEntitySound `md.md_charge`: volume1, pitch1, nonlooping, follows caster and explicitly stops on end. This is separate from the mining loop
- Original `for(count <- rangei(2,3) to0)` has an empty ascending Scala range. rangei's upper-exclusive result is2; default step+1 cannot reach0. Thus the intended surrounding charge-particle body is dormant. The adapter consumes the source nextInt(1), retains/tests the original radius[.7,1), theta[0,2PI), height[-1.2,0), velocityXZ[-.03,.03),Y[.01,.05) body, and does not invent emissions

### Main and reflected Meltdowner rays

- Life50ticks. First-update `executeAfter(life)` callback executes at source entity update51; this adapter advances tick life separately from pause-aware millisecond render age and retires after that update. Particles may still be emitted on the callback update, matching subclass work after super.onUpdate
- Length ramps200ms; alpha remains1 through1800ms then fades700ms across2500ms life. Width is inherited widthWiggle plus1 until2200ms, then shrinks300ms; it is not the prior small-ray's custom500ms shrink
- Width/glow wiggle both integrate render dt*uniform[-.4,.4)/1000, clamped[0,.1]. Glow modulation=.8*alpha*(.9+glowWiggle)*alpha. The source double alpha multiplication is retained
- Original main composite: inner radius.17 RGBA(216,248,216,230), headFix.98; outer radius.22 RGBA(106,242,106,50), headFix1; glow width1.5/base alpha.8. Actual original `mdray` blend_in/tile/blend_out textures are drawn first, then full inner and outer cylinders
- Reflected composite uses the same source ray lifetime/width/audio behavior, with source projected origin and reflector direction. Main `md.meltdowner` follows caster at volume.5; reflection has no extra perform sound
- Main observer Motion3D source bug adds1.6 onX. Reflected Motion3D.setPosition overwrites it. Both are preserved; no silent correction to+Y
- Each entity update has80% chance of one Md particle, travel[0,10), XYZvelocity[-.03,.03). Source Motion3D(ray,true) includes the vanilla1.7 non-player entity's default1.8F*.85F eye height above ray position

### MineRayBasic

- Continuous life233333ticks/11666650ms;200ms length ramp,400ms alpha tail and300ms inherited width shrink are retained. A normal session ends by immediate ray.setDead and loop stop, so no invented400ms release fade
- Original `mdray_small` composite: inner.03, outer.045, glow.3/base alpha.5, same cylinder colors/headFixes. Its width wiggle is active, unlike EntityMdRaySmall's different overridden shrink. Visual endpoint is Motion3D(player,true).move(15), while gameplay trace range is10
- Every source tick follows caster feet/head direction. Literal source start=player.posY+(local?0:1.6), end=player.posY+eyeHeight+look*15 plus observer1.6X. Pure `mineSourceEndpoints` retains that exact formula; `mineEndpoints` explicitly bridges legacy local player position/eye convention to modern uniform feet/eye coordinates. The actual geometric length is recalculated by setFromTo and can differ slightly from15 due start/end offsets
- View optimization remains true. First-person offset(-.05,-.25,.2), third/observer(.15,-.8,.23), rotated aroundY by270-source ray yaw. Both local third-person and remote views use the source third-person fix. Glow and cylinder originally evaluate float radians in different orders; both are retained
- Glow hand fix adjusts only start, leaving end unchanged. First-person side vector is normalized(0,1,-.5); observer side=(origin-camera)cross source ray direction. Glow head/tail segment lengths remain base glow width even on short ramps. Cylinder transforms orient toward end-minus-fixedStart, but retain source getLength rather than resizing to the adjusted vector
- Source entity yaw/pitch are float setFromTo rotations, then Motion3D reconstructs direction through Minecraft-style65536-entry float sine/cosine lookup. Pure source direction and view-fix computations preserve this roundtrip
-50% source chance of one traveling Md particle per update with same[0,10), XYZ[-.03,.03) parameters
- Following startup `md.mine_basic_startup` volume.4, nonlooping; loop `md.mine_loop` volume.3, repeat=true, zero delay, pitch1. End stops only the owned loop; startup completes naturally. Session teardown/death/replacement stops all owned following sounds
- Block-particle message emits3 particles: rangei(2,3)=2 then Scala inclusive0to2. Coordinates=blockXYZ+uniform[-.2,1.2), velocityXYZ[-.06,.06). Literal sentinel(-1,-1,-1) is emitted after source reset/break when sent; it is not filtered. `needRigidbody=false` plus custom Rigidbody gravity.01, entitySel/blockFil=null, unit drag: gravity is subtracted BEFORE displacement, no collision

### Composite meshes/materials and Md particles

- Reuse exact12 angular subdivisions, four sqrt-radius cap slices and108quads per cylinder. Whole front cap precedes body and mirroredZ rear cap; source rear cap extends by radius. No approximate camera-facing line replaces the composite
- Each texture/glow/inner/outer submission is flushed in source layer order. Unlit ordinary alpha blending, source depth test/write, no-cull glow/particles, cull cylinders, fixed original UVs and saved/restored shader-color state are explicit
- Glow/particle strict alpha>0.05 uses the already integrated source-equivalent cutoff05 shader; cylinders use restored source alpha>=0.1 through vanilla position_tex_color. Same registered shader is reused, with no second registration
- MdParticle lifetime25..54, source float size[.05,.07), alpha[.3,.6), fade-in5ticks/fade-out20ticks and zero-alpha final alive tick. Traveling particles retain ordinary zero-gravity/unit-drag motion; block particles retain custom gravity behavior

## Asset reuse

All11 copied original files are byte-identical:7 texture PNGs (both three-part beam sets plus md_particle),4 Ogg/Vorbis sounds (charge/perform/mine startup/loop). Decoded source RGBA pixels, dimensions/alpha ranges, Ogg channels/sample-rate headers and all4 sound-event paths are audited. Production already contains identical versions of all11 assets and all4 events, so integration reuses them. Existing white cylinder texture and cutoff05 shader/json are reused. No new shader/media resources or song assets are required.

## Verification coverage and limits

Standalone tests exhaust dense ray curve ages/terminal boundaries; all source walking-speed ticks0..150; yaw/pitch grids for local/observer and first/third-person; short/zero/full beam segments; literal glRotateY*glRotateZ cylinder transforms; all vertices/caps for both beam radii and headFixes; deterministic random walks; dormant charge RNG consumption; inclusive mining particle counts; sentinel coordinates and gravity order; all particle fades; all720start/end permutations; UUID replacement separation; fail-closed4096history; input early release/new press/stale end/teardown and pause/backward clock. Source bindings connect tested math/materials to actual renderer/audio/lifecycle code. The AcademyClient overlay is also compiled.

These are source-differential parameter/geometry/state tests, not rendered-pixel or audible parity. Minecraft/GPU, shader mods, client/server packet events, actual viewpoints, movement reconciliation, real mining/reflection playthroughs and audio loop playback were not run. The uniform-feet bridge, authoritative server geometry, modern tick/render scheduling, visibility/session ownership safeguards and defensive caps are explicit adaptations. Modern vertex RGBA8 quantization, framebuffer/color-space/target behavior and unavailable legacy shadow-pass hooks may differ. Existing cutoff-shader registration failure still falls back with its existing visual-fidelity warning. Global unrelated original-game RNG interleaving cannot be reproduced by an isolated adapter.

Recovery: source-binding originals are mandatory pinned canonical classpath test resources, with the exact GPL headers and test-fixture NOTICE. Missing/corrupt resources fail closed. No external `.reference` source tree or media is needed by the new Gradle visual regression itself; the separate original-asset audit script still requires its audited upstream working copy.
