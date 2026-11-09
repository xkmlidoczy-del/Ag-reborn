# Body Intensify client adaptation

## Scope and integration

The staged `ClassicBodyIntensifyEffects` is a `Dist.CLIENT` NeoForge event subscriber. The server owns all buffs, costs, hunger, achievement/experience and cooldowns. This adapter owns only local HUD/audio and observer activation effects.

Forward these CompoundTags from the client packet switch:

- `kind=body_intensify_start`, `entity` as TAG_INT, positive `token` as TAG_LONG, `input` as TAG_LONG
- `kind=body_intensify_end`, the same entity/token/input, `performed` as TAG_BYTE boolean

Call `long inputNonce = startLocal()` on physical key press and include that positive nonce in the authenticated `slot_press_token` action as `"<slot>:<inputNonce>"`. The server's `BodyIntensify.start(player,inputNonce)` echoes it as NBT long `input` on both start and end. `startLocal()` always returns a positive monotonic local nonce; a superseding physical press closes the prior local hold and retires its server token. Call `abortLocal()` on key-abort/GUI/category/ability cancellation and `clear()` on disconnect or other explicit visual-session teardown. Ordinary key release waits for the server's end/performed result and must not call `abortLocal()`. `localTicks()` returns visual elapsed ticks or -1 when absent. No arbitrary charge timeout is imposed by the client.

An explicit clear invalidates already queued callbacks even if the level/player/connection objects remain unchanged. Level/player/connection replacement is independently guarded. Per-entity monotonic-token history suppresses duplicate and stale starts/ends, and local abort tombstones suppress late same-token completion. Observer completion without a start is accepted once. Bounded retained histories/effects protect the client against unbounded remote entity churn.

Local physical-input start/end packets must match the currently expected `input` nonce before they can touch server-token replay history, an active context, sound or HUD. An old start or end cannot close/abort a newer physical press even if its authoritative server token is larger. Abort retires the expected input immediately, including before any server acknowledgement. A matching authoritative end retires that input once. A stale or duplicate end cannot retire a newer press.

Internal/native `start(player)` uses nonce0. A local nonce0 packet is accepted only while no physical-input-driven context has been established in the current visual session. Observers use authoritative entity/server-token history independently and do not require a matching local input nonce. The local input counter remains monotonic across clear while its expected/held/driven session state is reset, avoiding nonce reuse. Explicit clear also invalidates previously queued callbacks through the callback epoch. This closes the former rapid-abort/new-press acknowledgement ambiguity.

## Canonical sources read

AcademyCraft1.0.7:

- `src/main/scala/cn/academy/vanilla/electromaster/skill/BodyIntensify.scala`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/CurrentChargingHUD.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/SubArc2D.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/SubArcHandler2D.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/SubArc.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/SubArcHandler.java`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/ArcFactory.java`
- `src/main/java/cn/academy/vanilla/electromaster/entity/EntityIntensifyEffect.java`
- `src/main/java/cn/academy/vanilla/electromaster/entity/EntitySurroundArc.java`
- `src/main/java/cn/academy/core/Resources.java`, `core/client/sound/FollowEntitySound.java`, `ACSounds.java`

LambdaLib1.2.3:

- `util/generic/RandUtils.java`, `util/helper/GameTimer.java`
- `util/entityx/EntityAdvanced.java`, `EntityX.java`
- `util/client/auxgui/AuxGui.java`, `AuxGuiHandler.java`
- `vis/curve/CubicCurve.java`

`CurrentChargingHUD` uses linear time ratios directly; no CubicCurve is used by Body Intensify. The existing modern `ClassicCubicCurve`, charging/magnetic arc adapters, GUI mesh and render-state patterns were inspected without changing them.

## Exact source inputs retained

- Local-only looping `em.intensify_loop`, FollowEntitySound default volume .5, pitch1, follows caster position; stopped on end or abort
- Performed broadcast: one following `em.intensify_activate` at volume .5, pitch1, plus one activation effect
- Original `textures/effects/em_intensify_mask.png` and ten `textures/effects/arcs/{0..9}.png` sprites
- Held sprite constructor arguments: count `rangei(5,7)`, normalized radial position `[.84,.96)`, size `[25,30)` scaled GUI pixels, life233333, frameRate.3, switchRate0, initially shown
- Blend sprite constructor arguments: successful AND first-person only, count `rangei(10,15)`, radial position `[.6,1)`, size `[35,40)`, life25, frameRate.3, switchRate.2
- All ends clear held sprites before optionally creating the blend sprites
- The screen-space ellipse is centered at half the scaled GUI width/height and scales normalized X/Y offsets separately by those halves
- Held mask alpha `min(activeMillis/500,1)`; blend mask alpha `max(1-blendMillis/200,0)`; black rectangle alpha is .1 times mask alpha
- Sprite alpha remains .3 while held and .4 during blend, independent of mask fade
- HUD disposal is strictly `blendMillis>1000`, so blend sprites can remain after the200ms mask fade
- SubArc/SubArc2D template replacement chance `.5*frameRate`; age increment chance .9; shown-to-hidden chance `.4*switchRate`; hidden-to-shown chance `.3*switchRate`; dead records are removed on their next handler tick
- Activation callback delays/heights: `(0,2)`, `(1,1.8)`, `(3,1.5)`, `(4,1)`, `(6,.5)`, `(7,0)`, `(8,-.1)`
- Each callback uses count `rangei(3,4)`, radius `[.5,.6)`, random angle `[0,2π)`, thin arcs of life3; effect life15; inherited regeneration disabled
- Thin ArcFactory parameters: ten templates, length `[1.5,2)`, width.2, offset.8, passes3, branchFactor.7, widthShrink.9; drawing scale.3; random XYZ rotations, head-yaw-following effect center

**Important executable-source detail:** LambdaLib `rangei(from,to)` uses `nextInt(to-from)`, so the upper endpoint is excluded. The actual counts are5–6 held,10–14 blend, and exactly3 arcs per activation wave. This deliberately preserves the library implementation rather than interpreting the written bounds as inclusive.

## Modern renderer adaptations and limits

The world effect renders source-defined callback delays as first-client-tick indices0..14. The source EntityX callback values are retained; legacy world scheduling, frame latency and its exact handling of zero-delay entity callbacks are not claimed identical.

Modern explicit feet coordinates replace the1.7 local-player `posY-1.6` convention. This uses the contemporary entity position consistently for local players and observers. The renderer follows interpolated caster position/head yaw.

CPU arc ribbons reuse the established `ClassicArcGeometry` source-derived midpoint/branch generator. That generator bounds branch processing to the next pass and has different random draws and branch ordering from legacy ArcFactory's mutation during iteration. Ribbon normals are stable rather than the legacy display-list construction's additional random15-degree normal rotation. These are implementation/geometry adaptations, not exact random geometry parity.

World drawing occurs at `AFTER_PARTICLES`, with a private unsorted buffer, original SRC_ALPHA/ONE_MINUS_SRC_ALPHA blend factors, unlit/fog-free position-texture-color shader, depth test enabled and depth writes disabled. The event model-view matrix is explicitly installed, then restored. The particles output target follows the current Fast/Fancy/Fabulous stage convention.

The charging HUD draws at `RenderGuiEvent.Post` with HIGH priority before the port's NORMAL-priority ability HUD. This is a modern background-HUD ordering adaptation; the legacy AuxGui hook was at the EXPERIENCE overlay stage. The supplied GuiGraphics transform is retained and outstanding GUI batches are flushed before immediate meshes.

MC1.21.1 `NO_DEPTH_TEST` is a no-op. GUI drawing explicitly calls `RenderSystem.disableDepthTest()` and disables depth writes. A scoped snapshot restores actual prior depth/cull/blend enablement, depth function/write mask, separate blend factors, shader, shader texture0 and shader color. No shared render-type implementation is changed.

## Verification

- Cached-classpath javac of the staged timeline/adapter and existing arc geometry: passed against Minecraft1.21.1/NeoForge21.1.252
- `BodyIntensifyVisualRegressionTest`:175,799 deterministic assertions passed after the nonce revision
- Regression covers source parameters, seeded sprite generation differential, original upper-exclusive integer ranges, timeline thresholds, disposal boundary, frame/lifetime/flicker choices, key-abort/duplicate/stale/broadcast-only token cases, bounded history, paused clocks, typed packet/session guards and actual renderer state/stage wiring
- Pure nonce regression covers internal nonce0, positive monotonic physical presses, abort-before-ack, rapid abort/new press, old completion unable to retire new input, old high server tokens unable to poison new replay history, observer independence, session clear/nonreuse and10,000 rapid-press/abort cycles
- No Gradle task, Minecraft client/server, saves, live GPU lane or user computer was used
- Rendered-pixel parity, audible parity and multiplayer live observer behavior remain unverified

Focused reproduction from the repository root:

```sh
.tools/jdk-21.0.12.1+1/bin/javac -proc:none -encoding UTF-8 -d .staging/electromaster-final-skills/body-classes \
  .staging/electromaster-final-skills/src/main/java/cn/academy/port/client/ClassicBodyIntensifyTimeline.java \
  .staging/electromaster-final-skills/src/test/java/cn/academy/port/client/BodyIntensifyVisualRegressionTest.java
.tools/jdk-21.0.12.1+1/bin/java \
  -Dacademy.body.sourceRoot=.staging/electromaster-final-skills/src/main/java \
  -cp .staging/electromaster-final-skills/body-classes cn.academy.port.client.BodyIntensifyVisualRegressionTest
```

The main integration owner is responsible for aggregate build/server checks and any authorized live visual/audible validation.

## Integrated alpha-cutoff correction

The combined staging lane binds `ClassicSkillAlphaShader.get()` for HUD immediate meshes only. Its registered `classic_skill_alpha` fragment performs the source texture × vertex color × color modulator blend without discarding low-alpha fragments. Cached1.21.1 `position_tex_color.fsh` discards alpha<.1, which would hide the black mask (source alpha<=.1) and truncate the200/500ms fades. Source `CurrentChargingHUD.draw` explicitly disables GL_ALPHA_TEST. World arc material continues to use the ordinary current shader, independently from this HUD correction. New shader JSON/fragment are additive resources; actual GPU compilation/loading has not been exercised by this staging worker.
