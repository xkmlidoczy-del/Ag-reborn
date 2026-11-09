# NeoForge server runtime verification

## Current runtime result (2026-10-01 UTC, m05)

- Isolated cloud checkout; Minecraft 1.21.1, NeoForge 21.1.252, Java 21
- Full `scripts/gradle-cloud.sh build --console=plain`: **PASS**, 12,534 deterministic state/math/data/resource assertions plus 65 common-class cold-link checks
- **45 required native academy GameTests executed and passed**, including 11 Current Charging tests; log `runtime-evidence/m05-native-gametest.log`
- **Two actual Minecraft JVM processes passed seed + verify native disk restart tests**, distinct PIDs 190 and 466; native PlayerList/playerdata save/load, full ability/inventory/components, finite energy, graceful-stop escrow refund, bounded recovery/cooldown ticks; log `runtime-evidence/m05-disk-restart.log`
- User-approved EULA agreement was exercised at startup; it is no longer a pending blocker
- Actual cloud client startup/world/input/resource/same-JVM reopen smoke passed earlier; see `client-runtime-smoke.md`. **Current Charging has not yet been live-client tested**
- Real client socket/codec/handshake, external multiplayer, packaged production installation, hard crash/power loss, audible audio and original 1.7.10 visual parity remain **unverified**
- Source and evidence are checkpoint results for implemented slices, not a full-port completion claim

## Historical test implementation (34-test baseline)

`src/main/java/cn/academy/port/gametest/AcademyRuntimeTests.java` retains its 14 required native GameTests. `AcademyFirstSkillRuntimeTests.java` adds 20, bringing the source/compiled suite to 34 required tests. NeoForge's supported `@GameTestHolder("academy")` scan registers them. `@PrefixGameTestTemplate(false)` resolves the in-memory `academy:runtime_empty` template; a logical-server `LevelEvent.Load` subscriber installs it before test structures are prepared. No gameplay code, build configuration, or normal gameplay resource was changed for test registration.

The original suite and most new fixtures use separate NeoForge `FakePlayer` instances with unique UUIDs, a real `ServerLevel`, real item stacks, actual world entities/blocks, and the existing authoritative `AcademyGameplay.request` handler. Explicit `PlayerTickEvent.Post` posts exercise the registered NeoForge gameplay subscriber at exact simulated player-tick boundaries. Cleanup posts `PlayerLoggedOutEvent` to evict state and cancel charge contexts.

The new delayed-skill tests schedule their checks using `GameTestHelper.onEachTick`, observe consecutive changes of the real `ServerLevel.getGameTime()`, and post one fixture player-tick event per observed world tick. They never change the world clock or simulate an ElectronBomb, coin, or DirectedShock delay by looping many events in one world tick. World targets have AI/gravity disabled to keep their test geometry stable while retaining native entity ticking and damage events. Native pass/failure/timeout listeners remove transient contexts and narrowly scoped test event subscribers, and discard owned targets by identity even if knockback moved them outside the template.

Three coin request tests use the packaged `GameTestHelper.makeMockServerPlayerInLevel()` / `EmbeddedChannel` fixture. This registers a mock player with the real player list so the normal nearby-player `PacketDistributor` broadcast reaches a recipient. A capture-only listener reads the actual server-issued `coin_toss` token, then the test submits that token through `AcademyGameplay.request`. No private toss counter is read or guessed. Fixture cleanup removes the mock player and releases the native embedded channel. This captures payload objects; it does not exercise a real network client.

The original developer test now follows the timed, main-hand-only portable process: a finite 10,000 IF test battery, real induction-factor inventory consumption, 130 real world ticks for category acquisition, and another 78 for first-skill learning. Higher-tier generic passives use explicitly learned fixture state after testing portable-tier rejection, since normal/advanced developer devices are not implemented.

## Required coverage

| Test | Assertion |
|---|---|
| Arc entity hit | 5 damage; 30 CP; 18 overload; recovery delays; .0048 XP; training gains; updated cooldown; request saves player state |
| Arc block occlusion | Solid stone prevents damage to an entity behind it; block-hit .0018 XP; normal costs |
| Arc miss | Trained 70 CP / 11 overload / 5-tick cooldown; no XP from a miss |
| Arc rejection gates | Inactive, interference, overload lock, cooldown, unlearned skill, and insufficient CP reject without resource changes |
| Arc recovery and cooldown | Reject one tick early; 15-tick miss cooldown; CP resumes after its 15-tick delay |
| Developer requests | Main-hand portable required; offhand rejected; empty item energy defaults; arbitrary instant category selectors ignored; real factor chooses valid category; finite 3900 IF / 130-tick acquisition and 2340 IF / 78-tick ArcGen learning; level/skill prerequisites; unsupported skill rejected; completion persists; activation is server-controlled |
| Player NBT persistence | Full player save tag, compressed NBT binary write/read, logout cache eviction, and a new same-UUID player preserve all progress fields |
| Respawn copy | Real NeoForge death-clone event preserves progression and cooldown, recovers resources, and deactivates |
| Railgun ingot | Exactly 20 post-tick events; duplicate charge does not reset timer; one ammo; 200 CP / 180 overload / .005 XP / 300-tick cooldown and successful reuse after recovery; beam attenuation and radius; stone destruction; bedrock stops the ray |
| Railgun iron block | Same charge and release behavior for iron blocks |
| Railgun insufficient CP | Legacy order consumes ammunition before CP failure; no CP debit, overload, XP, or cooldown |
| Railgun abort/item switch | Abort and unsupported/missing ammunition cancel rather than pause a charge |
| Railgun activation/logout | Deactivation and logout cancel outstanding charge without firing |
| Creative casts | CP/overload and Railgun ammunition are preserved; experience and cooldown still apply |

## Additional required coverage (20 tests)

| Test | Native path and assertions |
|---|---|
| ElectronBomb activation / firing aim | Cast request pays 35 CP / 16 overload / .005 XP immediately; rejects duplicate; no early hit; delayed shot uses current head aim after 18 actual world ticks; fires once |
| ElectronBomb world occlusion | Real stone wall occludes delayed shot; activation XP remains; blocks are retained |
| ElectronBomb trained delay | Pre-increment .8 mastery pays 71 CP / 13.6 overload / 12-tick cooldown; deals 10.8 damage after exactly 3 actual world ticks |
| ElectronBomb lifecycle | Accepted delayed shot survives ordinary deactivation/input abort; real logout event cancels another shot |
| ThreateningTeleport held release | No hold cost/automatic shot; current held needle at release does 4.5 damage through wall; 35 CP / 18 overload / .003 XP / 30-tick cooldown; current item consumed once; replay rejected |
| ThreateningTeleport miss | Last held stone is consumed and survives as one actual world ItemEntity; paid miss earns .0006 XP and cooldown |
| ThreateningTeleport cancellations | Empty-hand post-tick cancels context; explicit abort is free; insufficient CP preserves items/resources and spawns no drops |
| DirectedShock world hold / captured damage | Duplicate start cannot reset timestamp; 7-tick release uses start-captured damage with current-mastery costs; native strong impulse/tracking flags; hit XP/cooldown; replay rejected |
| DirectedShock strict boundaries | Separate real-world contexts reject releases at exactly 6 and 50 ticks without costs, XP, or cooldown |
| DirectedShock world miss | Real wall occludes target; miss costs 50 CP / 18 overload, earns .001 XP, and has no cooldown |
| Held-skill lifecycle events | Deactivation clears held DirectedShock; logout clears held ThreateningTeleport; no late spend or item consumption |
| Coin trusted late request | Real CoinItem use, nearby-player token broadcast capture, saved single-coin escrow; stale/malformed token rejected; actual late request fires world beam without iron; normal costs/XP/cooldown; escrow consumed; replay rejected |
| Coin early request / physical return | Early judgement is spent once; same-world-tick duplicate events cannot advance coin physics; later replay cannot succeed; actual fall refunds exactly one coin |
| Coin accepted QTE / failed CP | Trusted late request consumes coin escrow before Railgun CP failure; no beam costs/XP/cooldown and no refund |
| Coin lifecycle / restart-escrow seam | Real logout event refunds coin; orphaned persistent escrow restores one coin once; foreign item cannot be injected. This is an escrow-recovery seam, not an executed server restart |
| Railgun reflection event | Real registered SkillReflectEvent cancellation protects reflector, redirects according to its head aim for 14 damage, stops original beam, consumes normal ammo, and awards .01 reflected-hit XP |
| Mutable skill attack event | Real registered SkillAttackEvent reports qualified skill ID; changing amount to 2 affects native LivingEntity damage |
| Generic passive tier / recovery | All four categories reject portable requests for eligible higher-tier passives; explicitly learned fixture passives raise CP to 10,500 / overload to 600; actual player post-tick applies 1.2x recovery |
| Radiation mark world damage / expiry | Actual ElectronBomb impact damages before stamping a 60-tick CP-derived mark; native LivingIncomingDamageEvent multiplies subsequent damage; actual living-entity pre-ticks decrement mark; expired mark stops multiplication |
| Completed-sleep event | Only correct PlayerWakeUpEvent flags refill CP/clear overload; activation, delays, cooldown remain; recovery is saved and overload unlock waits for post-tick behavior |

## Verification boundaries

- The compiled suite is not an execution pass. Runtime pass/fail totals will be recorded after authorized execution
- FakePlayer discards outbound packets. The native mock-player coin fixture captures selected server-side payload objects only. Neither fixture proves client-to-server wire transport, codec interoperability with a real client, rendering, or UI input
- Player ticks are deliberately posted through the event bus because NeoForge FakePlayer does not tick itself. New delayed tests also require actual consecutive world ticks. The mock-player fixture can receive ordinary world tick activity as well; coin physics rejects duplicate same-time updates. None of this proves a real connected client's cadence
- The NBT persistence test includes native full-player serialization and binary compression roundtrip, but does not verify operating-system disk persistence, clean server restart, or a real multiplayer login. The coin mock login is an in-process test fixture, not a multiplayer connection
- Probabilistic Creeper charging, water fish drops, fire placement, Teleport critical rolls, and its 30% hit-drop roll are not covered by these deterministic world tests
- Normal/advanced developer machines are unavailable; learned generic/radiation passive setup does not prove a device acquisition workflow
- Real-client visual effects and a packaged external Minecraft installation remain separate smoke-test stages

## Reproduction after agreement

Run from the checkout root:

```sh
scripts/gradle-cloud.sh runGameTestServer --console=plain
```

The NeoForge development run automatically selects the GameTest server. Expected success is a zero exit status and `All 34 required tests passed :)` in its log. Inspect the actual test count and errors; a Gradle success with zero executed tests is not sufficient.
