# GroundShock: source semantics, modern adapter, verification

Baseline: AcademyCraft **1.0.7** `Groundshock.scala`, `Plotter.java`, `SmokeEffect.scala`, `AbilityContext`, `Skill.SingleKeyDelegate`, `AbilityPipeline`, `CatVecManip`; LambdaLib **1.2.3** `MathUtils`, `RandUtils`, `EntitySelectors`, and the 1.7 Minecraft `Vec3`/MathHelper behavior. This stage ports the level-1 VecManip skill `vecmanip.ground_shock`, not the remaining VecManip skills or shared legacy achievement/configuration systems.

## Hold and resource lifecycle

The server owns the caster, progress object, world, start game tick, and context token. Press is free and captures every source parameter, including a single random launch speed. The skill can prepare while airborne, must be grounded at release, accepts **five or more server world ticks**, and has **no maximum hold lifetime**. Early release, failed resources, stale ability state, death/removal/spectator state, dimension changes, category/deactivation/interference/overload lock, preset/UI aborts and restart cancel the transient hold without a discharge. Release removes the hold before any spend or wave, so repeat releases cannot replay it. Current head aim and current caster position determine the wave when released; these are never supplied by the client.

Source float interpolation captures the following at context creation, for mastery 0→1:

- Initial energy **60→120**, damage **4→6**, CP **80→150**, overload **15→10**
- Plotter iterations **10→25**, cooldown **80→40**, both truncated to integers
- Mastery clearing drop chance **0.3→1**
- Entity y velocity **random [0.6,0.9) × (0.8→1.3)**, overwriting y while preserving current x/z

Successful consumption applies shared creative/resource recovery-delay/training rules. CP failure or airborne release adds neither cooldown nor EXP. A successful empty-ground perform still spends, adds final **0.001f EXP**, sets the captured cooldown and plays its sound/slash. Each unique affected living entity or dragon part adds **0.002f EXP** even if a damage hook rejects the hit. Actual GroundShock damage bypasses armor, matching `AbilityContext.attack`; native PvP/team protection is respected for damage and extra impulse.

## Wave algorithm and retained quirks

`GroundShockWave` is a dependency-free simulation with a server-world adapter. Player coordinates and all offset-cell coordinates use **truncation toward zero**, not floor. The unmodified classic Plotter starts one step ahead, at `(playerX.toInt, playerY.toInt-1, playerZ.toInt)`, with `(look.x,0,look.z)` slope.

The normalized 3D look remains 3D outside Plotter. Its lateral copy is rotated by **90 radians**, because the original `Vec3.rotateAroundY(90)` API accepted radians. This intentionally retains the legacy MathHelper sine/cosine LUT and float index rounding. The five offset/probability pairs are center/1, ±rot/0.7, ±2rot/0.3. The look's y component remains in those lateral offsets, so pitch and truncation can change sampled rows; it is not silently flattened or normalized again.

Each sampled nonair cell is visited only once and recorded for visuals. Stone converts to cobblestone and subtracts 0.4 energy; grass converts to dirt and subtracts 0.2; farmland subtracts 0.1 and other blocks 0.5. On a 0.3 random success, the source attempts to break **the central Plotter ground cell**, including when the newly processed sample was lateral. Targets overlap each recorded cell's asymmetric box `[-.2,+1.4] × y[-.2,+2.2]`; living entities and dragon parts are included, the caster is excluded, and a target is hit/launched only once. Each target subtracts one energy.

After **every** offset, even air/probability/duplicate skips, the same **central three blocks above ground** get break-with-force attempts. All five offsets repeat those attempts. Energy exhaustion stops only the next outer Plotter iteration, not the remaining offsets of the current iteration. A break checks protection, nonnegative hardness, remaining energy, and the farmland/liquid exceptions; successful breaks subtract current hardness, remove the block with no drops during the wave, and play the block's break sound at volume 0.5. Stone conversion precedes the central hardness query, so converted cobblestone hardness applies.

After the wave, energy becomes unlimited. The exact mastery gate uses the **current source-float mastery after per-entity EXP**, before final perform EXP. This allows a near-mastered skill to unlock its clearing during this same perform via entity hits; reaching mastery only from final 0.001 EXP does not clear until a later use. It checks the exclusive-upper **10×2×10** range x `[x0-5,x0+5)`, y `[y0-1,y0+1)`, z `[z0-5,z0+5)`. Only hardness ≤0.6 enters the normal break-with-force logic, so farmland, liquids and negative-hardness blocks still survive. Drops use the **captured** mastery drop rate.

## Deliberate modern adaptations and unported dependencies

- All mutations, **including stone/grass conversion**, honor `AcademyConfig.DESTROY_BLOCKS`, loaded-chunk/build-height checks, `ServerLevel.mayInteract` and cancelable NeoForge `BlockEvent.BreakEvent`. The classic conversion branch bypassed its block protection hook; reproducing that bypass would violate protected terrain. Protected samples retain source wave energy/affected-target classification, but do not change terrain
- World access never forces a new chunk to load. Build-height/unloaded cells are treated as air and cannot mutate; current modern block hardness, sound and loot tables replace 1.7 block APIs. Source blocks mapped here are modern `STONE`, `GRASS_BLOCK`, `FARMLAND`, `COBBLESTONE`, `DIRT`
- Repeated source air-set/air-sound no-ops preserve simulation/random ordering but do not create modern air updates or sound spam
- Finite direction and an exact zero-horizontal direction guard prevent source Plotter exceptions. GroundShock does not impose an invented healthy hold timeout
- Legacy per-skill HOCON multipliers, `CalcEvent.SkillPerform` customization, global dimension-destruction whitelists, and the complete achievement/advancement tree remain shared unported dependencies. This skill uses source defaults and the existing modern damage-scale hook. `triggerAchievement` does not yet award a corresponding modern advancement
- The source applied launch even when generic player damage was disabled. The adapter deliberately respects player/PvP/team protection for both damage and launch, while retaining affected-target EXP classification

## Actual client adapter

`ClassicGroundShockEffects` implements real modern client hooks, not a packet-only placeholder. The local physical press immediately begins the source upward pitch curve: ticks 1–3 ramp to -0.2°/tick, ticks 4–20 stay at -0.2°, ticks 21–25 ramp to zero, and a held context remains alive thereafter. Successful perform independently adds **+3.4° over four client ticks**, giving +13.6° total. Local release/cancel stops uplift while preserving an already committed slash. Server tokens bind acknowledgements without restarting the local curve; duplicate, stale/reordered or aborted tokens cannot replay sound/particles/slash. Level/player/connection replacement clears all owned effects, and pause does not advance their clock/tick motion.

The original `vecmanip.groundshock` asset follows the caster at **volume 2**. Each affected xyz cell emits **4–7 current-block digging particles** at source position/velocity ranges and has a **0.5 chance** of original `effects/smokes.png` smoke. The 2×2 atlas frame, size 1, white alpha blend, source position/velocity ranges and no gravity/drag/collision are preserved. Smoke fades according to `(elapsed seconds)/(random [0.5,0.7))`, is fully invisible after twice that modifier, and retains its **four-second** lifetime. The source's unused smoke rotation remains unused. Modern TerrainParticle initialization, sound attenuation, camera pose conventions and translucent buffers replace obsolete 1.7 APIs; live audiovisual parity remains unverified.

## Verification at implementation handoff

- Cached JDK **21.0.12.1** javac against pinned **MC1.21.1 / NeoForge21.1.252** compiled new core/server/client adapter sources and narrow integration reference copies without modifying production/build.gradle
- **159** deterministic source-order/formula/resource/terrain/target/energy/mastery-coordinate/cooldown-maximum regression checks passed, including the preset-stage `AbilityProgress.setCooldown` API and positive/negative/zero source-coordinate fixture alignment
- **99** dependency-free camera/smoke/token/pause/lifetime regression checks passed
- **18 new native GameTests** compile and exercise actual `ServerLevel` game-time advancement and registered gameplay post-tick/request/lifecycle handlers after owner integration. They cover tick4/5, resource/airborne/creative gates, captured parameters and current release aim, terrain/protection, unique/rejected hits, live/final mastery boundaries, exact25 iterations, lifecycle/state gates and a 200-world-tick hold. Mastery fixtures use an isolated 40×6×56 template and scoped listeners. FakePlayer discards packets; these tests do not verify wire transport or render/audio

Not run at this handoff: full Gradle check/build, native GameTest execution, physical live-client input, screenshots, audible playback and side-by-side 1.0.7 visual comparison. The parent integrates, launches and reports those stages separately. See staging `INTEGRATION.md` for exact owner hooks and check-task registration.

## First native launch diagnosis (m06)

The integration owner's merged build passed, but its first actual 68-test native run exposed nine GroundShock fixture failures. Native structures were at large negative X/Z, while the fixtures assumed `origin+3.5` truncated to `origin+3`; the source deliberately truncates toward zero, so it actually selected `origin+4`. The wave was beside its arranged ground strip, and the same off-by-one shifted mastery clearing. Some target tests happened to pass because a 30%-probability outer lateral sample reached the misplaced strip; the setup was therefore stochastic. Head yaw0 was already the correct world+Z direction.

Only the fixture placement was corrected: choose a signed half-cell around the intended absolute integer anchor, with negative anchors using `anchor-.5` and nonnegative anchors `anchor+.5`. It now asserts truncated absolute XYZ, actual head/pitch direction, and the first classic Plotter cell against the arranged ground cell. All gameplay/resource/block/target assertions remain unchanged. The deterministic regression adds 36 checks across positive, negative, zero and mixed-sign anchors, including first-cell conversion, unique hits and mastery clear boundaries. Runtime truncation/geometry was not changed. The integration owner's second native run remains the validation stage for this correction.
