# ThunderClap client adapter

## Integration contract

- Promote `src/main/java/cn/academy/port/client/ClassicThunderClapEffects.java` and `ClassicThunderClapTimeline.java` from this staging tree. No production/shared Java, Gradle configuration, Minecraft launch, existing client process, or save was changed by this visual implementation.
- Route `thunder_clap_start` and `thunder_clap_end` from the client packet switch to `ClassicThunderClapEffects.receive`. Both require `entity` as an NBT int and `token` as a positive NBT long. For the local caster both also require `input` as an NBT long, echoing the authenticated client press nonce; remote observers use server-token guards alone. Server tokens increase across accepted contexts and are reset only with a server/session restart.
- Call `startLocal()` on the physical ThunderClap press and send its positive return value with the authenticated `slot_press_token` action as `<slot>:<positive-client-nonce>`. The server `ThunderClap.start(player,inputNonce)` echoes this nonce on both start and end. The return value is0 if no valid player/world/screen state permits a press; do not send a physical-input nonce0 request. This records input acceptance only; it does not predict effects, damage, expenditure, sound, or movement slowdown. A fresh valid press defensively cancels any prior local hold
- The native/internal `ThunderClap.start(player)` path uses nonce0. Local nonce0 packets are accepted only in non-input-driven mode. An internal nonce0 completion or cleanup before any physical press preserves that mode; after starting physical input, only positive correlated nonces are permitted until session clear
- Call `abortLocal()` on key release, key abort, delegate replacement, preset edits/switches, ability/category changes, and activation cancellation. Call `clear()` for explicit client session cleanup. Screen cancellation is also independently detected even while paused.
- Release cancels. Only an authoritative server end/discharge can represent the source automatic shot at tick60, or the source CP failure exactly at tick40. The visual adapter itself never creates a shot or synthesizes a timeout.
- `abortLocal()` removes only the active local mark/hold and retains its surround tail. `clear()` removes all contexts and tails. Both restore speed on the owned old LocalPlayer object, including player replacement, disconnect, respawn, or world changes.

## Canonical baseline

The reference is `.reference/AcademyCraft-1.0.7`, particularly:

- `src/main/scala/cn/academy/vanilla/electromaster/skill/ThunderClap.scala`
- `src/main/java/cn/academy/vanilla/electromaster/entity/EntitySurroundArc.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/SubArc.java`, `SubArcHandler.java`, and `ArcFactory.java`
- `src/main/java/cn/academy/core/client/render/CubePointFactory.java`
- `src/main/java/cn/academy/vanilla/generic/entity/EntityRippleMark.java`
- `src/main/java/cn/academy/vanilla/generic/client/render/RippleMarkRender.java`

LambdaLib1.2.3 `GameTimer`, `Raytrace`, and `SimpleMaterial` supply the timer, block-only aim, and legacy material conventions. Preserve the repository GPLv3/MIT notices and upstream restrictions already documented in NOTICE; this is not a publication grant.

## Preserved source parameters and lifecycle

- BOLD surround: five subarcs; ten pre-generated templates; random length in `[3.5,4.5)`; three midpoint passes; width `.35`, offset `1.2`, branch factor `.45`, width shrink `.9`, rendered scale `.3`
- Surround generation uses the six equally selected cube faces, X/Z-centered and Y spanning `[0,height]`; dimensions are captured caster width/height multiplied by `1.3`. Head yaw follows each frame
- SubArc replacement probability `.5*.6`, show/hide probabilities `.3*.7` and `.4*.7`, age advance probability `.9`, lifetime30; dead arcs are removed on the following tick and all five regenerate only after the list is empty
- The source surround entity's natural lifetime100 is retained independently of the server context. Termination cannot revive an expired surround. A live surround remains ten client ticks after termination
- Local-only gray ripple RGBA `(.8,.8,.8,.7)`, three horizontal textured quads with the original UV order, cycle3600ms and phase offsets0/+1200/+2400ms. Height is phase×`.0003`, size is `1.9→1.4`, fade-in/out each1600ms with a400ms plateau
- Aim is recomputed from the caster's current eye/head direction every active client tick, range40, solid-block raytrace, no entities, no fluid hit. It falls back to the40-block endpoint
- Local speed follows `.1-(.1-.001)/60*ticks`, clamped to`.001`. It is immediately restored to`.1` at every end/cancellation. In1.21.1 Player.getSpeed reads MOVEMENT_SPEED, so the modern adapter mirrors the capability value onto the **local** attribute base and preserves sprint/potion modifiers. No speed/ability packet or server attribute write occurs
- No custom sound is added: ThunderClap's canonical client context has none. The accepted server discharge's vanilla LightningBolt is responsible for lightning/thunder audiovisuals

## Modern rendering adaptations and limits

These are modern CPU `ClassicArcGeometry` ribbons, sharing the production charging adapter's procedural conventions. Geometry randomness, branch processing and ribbon-normal construction are intentionally not pixel-identical to the old display lists. No original1.7.10 capture comparison or pixel-parity claim is made.

The ripple is rendered at `AFTER_LEVEL` into the main target, after Fabulous compositing, with the event camera model-view explicitly installed; its PoseStack at that stage is identity. All positions subtract the camera in double precision before float mesh submission. Arcs are depth-tested, unlit, double-sided and alpha blended without depth writes; the ripple is unlit, double-sided and alpha blended **without depth test or writes**, matching RippleMarkRender. Source GL alpha-test removal is represented by modern texture alpha/blending. In1.21.1 the stock `NO_DEPTH_TEST` shard(function519) performs no disable call, so the ripple uses explicit RenderSystem depth setup/clear. Both passes explicitly bind the position/texture/color shader and texture; color, shader, texture0 and model-view are restored, with the expected AFTER_LEVEL depth/cull/blend baseline reinstated.

Modern feet coordinates replace the1.7 local-player posY−1.6 workaround. The initial mark uses current block-only aim immediately rather than the legacy context's uninitialized(0,0,0) spawn position; thereafter it follows tick updates with normal render interpolation. Transparent-scene ordering and exact old GL behavior still require the main lane's live client recordings.

Packet data is copied; identity checks reject callbacks across replaced world/player/connection. Explicit clear invalidates queued callbacks. Positive monotonic token history rejects duplicate/replayed starts, stale ends cannot close a different context, and end-before-start tombstones suppress reordered starts. Client nonces increase across physical presses and are never reset by clear/session replacement; `abortLocal()` retires the expected nonce. The echoed positive nonce must match the **current local physical press** before either a start or an end can change local token history, context, speed, mark, or input state. An old acknowledgement arriving only after abort/new-press is therefore rejected even when its callback generation is fresh; an old end cannot retire or close the newer press. Generation checks additionally reject a start callback already queued before cancellation. The local active-context end also requires both its stored input nonce and server token. Remote observers retain server-token guards without a dependency on this client's physical input. Main input/server cancellation must continue to send the matching end for accepted contexts.

## Verification

Pure JDK21 check, without Minecraft classes or a game launch:

```
mkdir -p .staging/electromaster-final-skills/.thunder-clap-visual-javac
.tools/jdk-21.0.12.1+1/bin/javac \
  -d .staging/electromaster-final-skills/.thunder-clap-visual-javac \
  .staging/electromaster-final-skills/src/main/java/cn/academy/port/client/ClassicThunderClapTimeline.java \
  .staging/electromaster-final-skills/src/test/java/cn/academy/port/ThunderClapVisualRegressionTest.java
.tools/jdk-21.0.12.1+1/bin/java \
  -cp .staging/electromaster-final-skills/.thunder-clap-visual-javac \
  cn.academy.port.ThunderClapVisualRegressionTest
```

Result: `PASS 35861 thunder-clap visual assertions (no game launch)`. Checks cover source constants, all three phases at each millisecond in a full3600ms cycle, speed curve, local/observer distinction, tick60 pending-end behavior, immediate mark/speed cleanup, ten-tick tails, independent100-tick surround lifetime, duplicate/reordered/stale token sequences, abort/queued-start/reactivation, nonce0 internal contexts,1,024 rapid abort/repress sequences with old starts and ends, nonce monotonicity across session clear, and paused/backward clocks.

Both client Java files also compiled successfully using JDK21 against the existing cached NeoForge classpath, with all output confined to `.staging/electromaster-final-skills/.thunder-clap-visual-javac`. No Gradle task or game was launched. These checks do not establish native rendering, actual movement physics, audio playback, multiplayer, or visual parity. The main integration lane owns common/integration compilation and live recordings.

## Integrated ripple alpha-cutoff correction

The combined lane uses the new client-registered `ClassicSkillAlphaShader` for RIPPLE only, preserving source RippleMarkRender's GL_GREATER threshold0 and every low-alpha fade sample. The stock1.21.1 position_tex_color fragment discards alpha<.1 and therefore cannot reproduce these fades alone. Zero-alpha fragments are inert under the no-depth-write source alpha blend, so the dedicated fragment needs no discard. BOLD arcs retain their ordinary source-derived material separately. Actual GPU shader compilation/loading remains to be verified on the main live lane.
