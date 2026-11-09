# Narrow Meltdowner/MineRayBasic client integration

Worker changes remain wholly inside `.staging/meltdowner-beams`. Parent owns production/integration promotion and the overall patch/manifest.

The proposed additive adapter is `cn.academy.port.client.ClassicMeltdownerBeamEffects` plus its source math `ClassicMeltdownerBeamTimeline`. Both use the established starter timeline's tested cylinder/particle geometry, nonce/pause/wiggle helpers and `ClassicMeltdownerStarterEffects.CutoffShader` getter/registration. No new render/event registration hook is needed: the effects class subscribes itself on the client event bus.

## Proposed AcademyClient sites

An exact baseline-preserving overlay is under `client-integration/src/main/java/cn/academy/port/client/AcademyClient.java`; its diff is `client-integration/academy-client-beam-hooks.patch`. This is a proposal, not an applied production change. `client-integration/hooks-check.json` captures the read-only baseline SHA256 and site counts.

1. Add one seven-kind switch arm forwarding to `ClassicMeltdownerBeamEffects.receive(t)`
2. Add one physical-press hook after existing nonce hooks: if this adapter owns the skill, obtain `inputNonce=ClassicMeltdownerBeamEffects.startLocal(delegateSkills[slot])`; existing `slot_press_token` dispatch carries it
3. Add five `endLocal(delegateSkills[slot])` calls guarded by `owns` beside the existing starter calls: reset, active abort, key/preset remap, transition abort, ordinary release
4. Add `ClassicMeltdownerBeamEffects.clear()` beside both existing starter clear calls: session replacement and local death
5. Add one HUD ownership branch using this adapter's `delegateActive(skill)` for ordinary ACTIVE/IDLE. Original SingleKeyDelegate has no new 20-tick CHARGE/ready state

No extra packet action, release charge calculation, authoritative ability-state write, or shader registration is introduced in AcademyClient. `localTicks` is diagnostic only. Server context owns its charge/use authorization and terminal packets.

## Agreed payload contract

All seven kinds require exact primitive NBT: `entity` INT, `entity_uuid` valid UUID INT_ARRAY, `token` LONG>0, `input` LONG>=0. Local physical presses use the returned positive input nonce; explicitly internal/source-style starts may use0. Observer contexts retain the same input as start. Tokens monotonically advance per caster/context; end must retain its start token/input.

- `meltdowner_start`, `meltdowner_end`: common fields only
- `meltdowner_ray`: common fields; `index` INT0; `charge` INT20..40; `x/y/z` DOUBLE server eye-origin; `dx/dy/dz` DOUBLE finite nonzero head direction; `length` DOUBLE0..30. Default30, clipped by first reflector's source Euclidean feet distance. A zero length is allowed and uses source setFromTo's zero-delta rotation behavior
- `meltdowner_reflection`: common fields; `index` INT1; finite DOUBLE `x/y/z`, `dx/dy/dz`; `length` DOUBLE exactly10. Server origin is casterEye+normalize(casterLook)*distance(casterEye,reflectorEye), and direction is reflector head look. Client adds no observer+X origin correction, because source Motion3D.setPosition overwrites it
- `mine_ray_basic_start`, `mine_ray_basic_end`: common fields only. Client follows loaded caster/head pose using original basic composite beam. Source visual base distance15 differs from gameplay mining range10
- `mine_ray_basic_particles`: common fields; monotonic nonnegative `index` INT; `x/y/z` INT. Preserve literal sentinel(-1,-1,-1); it intentionally emits particles near that block coordinate. No hit-validity boolean may suppress it

The packet is copied before cross-thread scheduling. The callback captures world/local-player/connection/clear epoch. Caster UUID+loaded Player object, nonce, token, current context, per-ray index and mining-particle sequence are checked before effects/audio. Every local effect kind is nonce fenced; observers must match the start's stored nonce. Stale/end-before-start/duplicate start events cannot resurrect a context. An invalid departed UUID context is retired before a new loaded identity is allowed to start. A still-valid different identity cannot be modified by an old UUID packet.

Local release/abort hides held charge/mining effects immediately. A matching late start after release is hidden, but may accept its authorized terminal Meltdowner rays without replaying charge/audio/slowdown. New press invalidates older pending acknowledgements. End stops charge/mining sound and restores original local walking speed; accepted discharge/reflection rays finish their own source lifetime. World/connection/player replacement, death or explicit clear retires all owned effects/audio and queued callbacks.
