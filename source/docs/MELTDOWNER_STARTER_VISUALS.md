# ScatterBomb and LightShield client adapter

## Delivered and verified

This is an isolated staged implementation for NeoForge21.1.252 / Minecraft1.21.1. No shared production code/resources, other stages, worlds or recordings were written. No Gradle/client launch, runtime capture, push or publication was performed.

- `ClassicMeltdownerStarterEffects`: client-only packet lifecycle, local input correlation, owned following sounds, original-style orb/shield/ray/particle rendering
- `ClassicMeltdownerStarterTimeline`: pure source render parameters, geometry, random-frame state, token/nonces and pause clock
- `MeltdownerStarterVisualRegressionTest`: independent source-formula/geometry/state-machine differential checks and renderer source bindings
- `verify_client_cached.sh`: cached Java21 compilation against the repository's NeoForge21.1.252 / Minecraft1.21.1 dependency classpath and executable regressions
- `verify_client_assets.py`: original-byte/decoded-RGBA/material/source checks and production reuse/addition manifest
- `MELTDOWNER_CLIENT_HOOKS.md`: narrow proposed AcademyClient edits. This worker did not apply them

The successful command and assertion count are in `audit/client-verification.log`. MOD-bus API deprecation warnings are existing modern API warnings, not compilation errors. Tests verify mathematical decisions, material inputs and source assets; they do not execute Minecraft render/audio/network events or prove rendered-pixel parity.

## Original source provenance

AcademyCraft1.0.7:

- `vanilla/meltdowner/skill/ScatterBomb.scala`: ball-list synchronization, terminal rays with `viewOptimize=false`, caster-identity Y correction
- `vanilla/meltdowner/skill/LightShield.scala`, `LSContextC`: shield create/dispose, startup/loop, 30%-per-client-tick particles
- `vanilla/meltdowner/entity/EntityMdBall.java`: synchronized fixed offsets and2333333-tick life, render wiggle, alpha/size, render-relative position correction, asymmetric RenderIcon billboards
- `vanilla/meltdowner/entity/EntityMdShield.java`, `client/render/RenderMdShield.java`: one-block-forward placement, head yaw/pitch, centered textured quad, rapid rotation and startup size
- `vanilla/meltdowner/entity/EntityMdRaySmall.java`, `core/entity/EntityRayBase.java`:700ms ray,200ms length ramp,400ms alpha fade,500ms width shrink, inherited glow wiggle and particles/sound
- `core/client/render/ray/RendererRayComposite`, `RendererRayGlow`, `RendererRayCylinder`: glow then inner/outer cylinders, three glow texture sections, source caps/materials
- `vanilla/meltdowner/client/render/MdParticleFactory`, `core/client/sound/ACSounds`, `FollowEntitySound`: source particle and following-sound parameters

LambdaLib1.2.3:

- `Motion3D`: normalized head-yaw/pitch facing, eye-height placement, unexpected observer `px +=1.6`
- `RenderIcon`, `MeshUtils.createBillboard`, `Sprite` and `RenderParticle`: source billboard positions/UVs and alpha-test material thresholds
- `Particle`, `Rigidbody`: five-tick fade-in, life/20-tick fade-out, constant motion with zero default gravity and unit drag
- `MathUtils`, `RandUtils`, `GameTimer`: float/double interpolation order, upper-exclusive integer range and pause-aware timing

AcademyCraft-derived code/assets retain the root GPLv3/additional notices. LambdaLib-derived algorithms retain its MIT notice. Original texture/ability sounds only; no media/song assets or rights were added.

## Exact source parameters and deliberate quirks

### Scatter orbs

- Seven server-synchronized indices, source life2333333ticks. Caster translation is followed; spawn-yaw world-space offset never rotates with subsequent yaw
- `EntityMdBall` server offset carries+1.6Y. `sourceBallOffset` explicitly models local-identity-1.6 correction, including local third-person identity. `modernBallOffset` restores the legacy local-player eye/feet convention into modern uniform feet coordinates. This produces the same modern feet+server-offset anchoring used by the current ElectronBomb adapter, rather than lowering the local orb by1.6blocks
- Five original core textures and glow; glow size.7, core.5. RenderIcon quad Y is[-.25,.75]times size, not centered
- Alpha/size source branch order is preserved, including float interpolation and short-life branch overlap; long held alpha.6 and size1
- Alpha wiggle initial.8, acceleration change probability3/8 and range[-4,4), dt/1000 integration clamped[0,1]. Texture change probability2/8, index[0,5). Per-orb gate/index RNG is separate from the shared range RNG, matching source roles
- Surround jitter uses the original float phase and Minecraft-style65536-entry float sine/cosine lookup: X.03sin(age/300), Z.03cos(age/300), Y.04cos(phase*1.4+PI/3.5)
- Glow alpha is sourceAlpha*(.3+.7wiggle); core sourceAlpha*(.8+.2wiggle). Render-tick positioning follows latest caster translation, not an independently drifting orb

### Small rays

- The server packet supplies actual origin/direction/length; no client aim or damage is authored. Source local/observer Y conventions are already bridged by that authoritative modern feet-coordinate geometry
- `viewOptimize=false`: no hand/first-person start offset is added. Source glow side is `(rayOrigin-camera) cross direction`, not a first-person hand direction
- Life14ticks / visual700ms; length ramp200ms; alpha remains1 through300ms then fades400ms; width remains1 through200ms then shrinks500ms. The subclass intentionally ignores inherited width wiggle, but its RNG consumption remains preserved
- Glow width.3 and base alpha.5; inherited glow wiggle[0,.1] makes glowAlpha=.5*alpha*(.9+wiggle)*alpha. Squared alpha is intentional
- Three texture sections: front/tail lengths remain.3 even when ramped ray length is<.6, so the middle section may run backward. No invented clamping removes that source quirk
- Inner radius.03, RGBA(216,248,216,230), headFix.98; outer radius.045, RGBA(106,242,106,50), headFix1
- Twelve angular divisions, four square-root-radius head slices; source body starts at radius and ends at ray length, and rear cap extends by radius with mirroredZ. All108quads per cylinder and every vertex are differentially checked. Draw order is entire front cap, entire body, entire rear cap; effects/layers are flushed in source order instead of globally resorted
- One source Md particle emission per ray update, distance[0,10), velocityXYZ[-.015,.015). `md.ray_small` plays at origin with MASTER volume.5, pitch1

### Light shield

- Flat centered MeshUtils quad, not OBJ. Source renderer uses only `textures/effects/mdshield.png`; the source tree contains no shield OBJ/MTL. `textures/effects/md_shield/0.png` is retained as an unused provenance asset, not used as a replacement
- Placement is eye position plus head-facing direction*1 minus.5Y. Observer Motion3D's+1.6X bug is preserved for shield and particles. Head yaw and pitch orient the shield; it does not become a camera-facing billboard
- RotationSpeed lerpf(.8,2,min(ticks/30,1)) DEGREES PER MILLISECOND, without a hidden/1000 factor. Rotation subtracts360once when>=360; modulo is not substituted, including long-frame rotations that remain>360
- Size1.8*lerpf(.2,1,min(ticks/15,1)). The source computes min(ticks/6,1) alpha but never binds it; this adapter keeps that computation unused and renders white with full modulation. Source shield UV bottom0/top1 is retained, distinct from conventional orb/particle UVs
- Following `md.shield_startup` volume.5, pitch1; following `md.shield_loop` source default volume1, pitch1, looping with zero delay. Ordinary end stops only the owned loop; startup continues naturally. Leave/death/replacement stops all owned following sounds
- 30% chance per client tick of one Md particle at eye+look*1 with uniformXYZ[-.5,.5), velocityX/Z[-.02,.02), Y[-.01,.05)

### Particles and render materials

- MdParticle life `rangei(25,55)` is25–54, fadeOut20ticks, alpha[.3,.6), size float[.05,.07), fadeIn5ticks, constant source motion. The life+20tick particle remains alive at zero alpha and disappears on the following tick
- Particle quad is centered, conventional UVs, distinct from the shield's flipped source UVs
- Source strict alpha material is explicit: balls `GL_GREATER0`; shield/glow/particles `GL_GREATER.05`; cylinders inherit the glow renderer's restored `GL_GEQUAL.1`
- New gt0 and cutoff05 fragment shaders preserve equality behavior. Reusing the existing no-cutoff shader alone would allow transparent texels to write depth, so it is only the gt0 failure fallback. Vanilla position-tex-color's<.1 discard is used for the restored cylinder material
- Source depth test/write, ordinary alpha blend, unlit texture/color, no-cull billboard versus culled cylinder, and ordered layer submissions are explicit modern render state. Shader-color modulation is saved/restored. No GL compatibility display lists/reflection are used
- A+1 offset is used only for last-render/wiggle clock sentinels; relative age remains unchanged, avoiding a fabricated second first-frame when the pause clock begins at0

## Packet and interrupted-input behavior

All payloads are copied before scheduling, with exact primitive tags, finite vectors, positive tokens, finite bounded ray length, source life and bounded ball indices validated. The queued callback captures ClientLevel, local player object, connection and explicit-clear epoch. Caster context identity is checked against the currently loaded Player object, including replacement/reused IDs, removal/death and spectator state.

- Local press returns a monotonically increasing nonce. New presses supersede older pending acknowledgements before token history can be changed
- Ordinary local release/key-abort immediately hides held visuals/stops shield loop. A matching late start may create a hidden context solely to accept its server terminal rays; it cannot replay balls, shield or startup/loop
- Token start/end histories reject duplicates, older starts, end-before-start replay and delayed starts after termination. History never evicts tombstones; at4096distinct caster IDs it fails closed until session teardown rather than resurrecting old tokens
- Ball/ray records require the current acknowledged token. Duplicate ball indices and ray indices are rejected; a ray retires its matching ball. End removes the held context while already accepted700ms rays finish
- Local end completes only the matching nonce. Newer physical input/context cannot be ended by an old acknowledgement
- World, connection, player identity, leave, death and explicit clear retire all owned visuals/audio/pending callbacks. Pause freezes visual time and client-tick particles
- Context cap128per skill, ray cap256, particle cap4096, following-startup cap128 are defensive modern limits
- Delegate ACTIVE includes a pending held physical context before server acknowledgement and clears on authoritative termination even if the key remains physically down. There is no invented CHARGE/ready20 HUD state; elapsed local ticks are not authority

## Differential test coverage and limits

The Java regression exhausts source branch/timing boundaries, long/short ball lifetimes, dense jitter ages, all shield startup ticks, long-frame single-wrap rotation, local/observer offsets over dense yaw/pitch grids, all ray milliseconds and every cap/body vertex at both source radii/headFixes,10000seeded random-frame steps, all particle lifetimes/fades, all720start/end permutations for three tokens, early release/newpress/stale-end/session nonce behavior, history-cap fail-closed and pause/backward-clock behavior. It checks the renderer binds tested parameters/materials and contains no common gameplay/network mutation.

The asset regression compares all15original asset files byte-for-byte and original decoded RGBA pixel data/alpha ranges, validates Ogg/Vorbis channel/sample-rate headers and event paths, checks white-material bytes and shader structure, and verifies source's absent shield-model/flat-quad topology. Production assets and all three sound events are already identical: reuse them. The manifest separately labels the four new shader resources as additions.

Unverified/explicit adaptation limits:

- Minecraft render/event/audio execution, client network ordering, real first/third-person screenshots, shaders-mod compatibility, audible looping and GPU framebuffer output were NOT run
- Modern uniform-feet/eye coordinate bridge replaces1.7's local-player convention; the explicit source correction and modern mapping are tested, but screenshots are needed before claiming viewpoint parity
- Authoritative server Scatter endpoints replace independently randomized client terminal endpoints from source `Motion3D(player,5,true)`; observers receive coherent server geometry rather than independent cosmetic random aim
- Original entity/render scheduling is replaced by modern post-tick/render-stage scheduling; millisecond ray lifetime may differ from tick-only disposal during lag. Source formulas and emission ranges are retained; source/entity callback scheduling has not been runtime replayed
- Source shield's unused alpha is preserved, but accidental inherited legacy GL current-color state is replaced by explicit white modulation and state restoration. This prevents unrelated modern renderers from contaminating the effect
- Modern vertex colors quantize toRGBA8; source uniforms/current-color and legacy float shaders may differ in small alpha/color details. Modern interpolation, target/fog/color-space handling and lack of legacy shadow-pass hooks can change rendered pixels
- Shader registration failure logs an explicit parity limitation. The fallback cannot promise strict cutoff/depth behavior
- Original random roles/distributions and source algorithms are preserved; the original globally interleaved game's RNG stream is not reproduced across unrelated effects

These are source-derived adapters and strong deterministic regressions, not a claim that a full runnable client or original render/audio parity has already passed.
