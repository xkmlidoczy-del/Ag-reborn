# Optional Railgun first-person visibility

This is an opt-in usability change to the restored .35 prototype, not a claim of
classic visual fidelity or complete visual acceptance. The original 1.1-block
glow and both cylinders rendered without face culling can cover the view close to the eye.
Their vertices, origin, radii, textures, UVs, colors, vertex alpha, sparks and
2,500 ms lifetime remain unchanged. No hit, damage, sound, event or server/network
definition is changed.

Enable with `/aca_vfx2 near_eye on` while Railgun quality is LOW or MEDIUM. Inspect
with `/aca_vfx2 near_eye status`; disable with `/aca_vfx2 near_eye off`. The client
setting `vfx2.nearEyeRailgunVisibility` defaults to false. Quality OFF wins.

Only a live beam whose existing packet caster ID resolves to the exact current
local player can receive the material. The beam records that player object,
world, connection and input-session epoch. All must still match when rendered.
Both the Minecraft camera entity and the actual render-event camera must be that
player; the view must be first person and not detached, dead, removed or spectator.
An unknown caster, the old ownerless API, another player's shot, a replay sample,
a replaced session and third-person/observer views retain their prior rendering.
Camera proximity alone grants no eligibility.

The material only changes fragment alpha. CPU world-camera subtraction remains in
double precision. The vertex shader derives positive view depth from the same
ModelViewMat transform used for projection, without subtracting the camera or
applying the pose again. Perspective-correct fragment interpolation feeds a cubic
smoothstep. Fragments at/below 0.12 blocks contribute zero; the directional core
recovers fully by 0.80 blocks and the broad glow by 1.60 blocks. These provisional
thresholds passed a bounded native first-person image review; central bloom remains. Both the curve and its first derivative are
continuous, so there is no camera-distance admission boundary requiring hysteresis.
No whole beam or primitive is culled, shortened or moved. A very short beam may
have its unsafe core fragments completely attenuated; its original longer glow
caps and spark rendering are retained. Multiple shots can still overlap visibly
beyond the unsafe region; mathematical alpha bounds alone do not prove readability.

The original texture/vertex alpha cutoff occurs before this new fade. There is no
second cutoff to turn it into a hard edge. RGB and the original world render state
are preserved, including depth testing, no culling, alpha blending, color-only
writes, particle target and sorting. No fog, full-screen pass, new dependency or
texture is introduced. On eligible shots this safety material takes precedence
over MEDIUM's optional inner-core material; existing optional overlays retain
their existing safety rules. Neither material is claimed to match original pixels.

Both near-eye shaders must load before either is used. Registration clears old
references and guards deferred callbacks by generation. A missing/link-failed
shader, missing required depth-range uniform or failed buffer acquisition falls back to the original material; live
render-type shader suppliers also fall back during an incomplete reload.
`-Dacademy.railgun.nearEye.forceFallback=true` exercises shader fallback, as does
the existing global `-Dacademy.vfx2.forceFallback=true` switch.

Validation includes the production pure ownership/depth policy, 64-shot alpha
composition at unsafe depths and a separate .35 recording differential for actual
rendered attributes, contexts, failure/reload behavior and large coordinates.
Recording and math checks do not replace native first-person, third-person,
observer, short-shot, yaw/pitch, multi-shot and resource-reload image checks.

## Native .36 checkpoint evidence

A source-free packaged development client (not a retail launcher) at normal 20 TPS
showed peripheral sky/floor and the outside of a yellow-wool target frame more
clearly with the option enabled. The center remains luminous. Separate genuine
casts preserve independent native arc phases; the original terrain backend also
removed some marker blocks. This is not an exact world/pixel or live-enemy test.
F3+T succeeds and material-ready stays true. Later casts consume ordinary ammo,
but the delayed third-person/post-reload screenshots missed the effect window.
Their full appearance remains pending; do not substitute recording tests for it.
Save/re-entry preserves health, abilities, inventory and classic progression exactly.
The client was closed with this optional setting off. English HUD width overflow
is a separate known issue. Audio, multiplayer, retail launch, broad camera/stacked
GPU coverage, full motion and performance acceptance remain outside this evidence.
