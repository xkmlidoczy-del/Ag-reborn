# Classic Railgun coin path

## Baseline and scope

This adapter follows the local, pinned AcademyCraft **1.0.7** `ItemCoin`,
`EntityCoinThrowing`, `RendererCoinThrowing` and `Railgun.scala`, plus the exact
LambdaLib **1.2.3** `Rigidbody`, `RenderUtils` and `GameTimer` sources. Original
copyright and project distribution terms remain in `NOTICE`/`LICENSE`.

The coin is a server-owned logical toss and a client-only visual simulation.
It is not a registered Minecraft entity. This follows the original intent:
legacy right-click spawned a local client coin and a server coin separately;
the network-spawned client copy immediately died rather than driving the local
animation. The port deliberately removes that redundant synchronized entity.

No Minecraft client, server, GameTest server or EULA acceptance was run to verify
this path. Compile/standalone physics assertions are not runtime visual QA.

## Server contract and integration

All `CoinTosses` operations run on the logical server thread:

- Register `AcademyCraft.COIN` as `new CoinItem(new Item.Properties())` and add
  the existing front texture to the generated item model/translation as needed
- `toss(ServerPlayer, InteractionHand)` reads the actual held coin, actual feet
  height and vertical velocity. It declines spectators, dead/removed players,
  invalid numeric motion and a second in-flight toss. Survival consumes one
  item immediately; creative creates no refundable escrow
- `tick(ServerPlayer)` belongs in `PlayerTickEvent.Post`. At most one physics
  step occurs for each world game tick. Invalid/dead/dimension-changed owners
  return their escrow; a saved orphan escrow is also returned on the next tick
- Handle an explicit `coin_attempt` network request containing only the server
  toss token as a decimal long. Parse failures are ignored. Call
  `attempt(player, token)`; never trust a supplied entity ID, progress, time,
  height, velocity, success flag, item, or CP cost. A stale token cannot consume
  a newly tossed coin. The overload `attempt(player)` is for trusted server-side
  callers and should not replace token validation in the network handler
- `attempt` returns true only after a single, eligible late judgement has
  removed the coin and deleted its refundable escrow. Then call the ordinary
  Railgun performance code **without requiring or consuming iron ammunition**.
  Apply the ordinary ability/CP/damage/experience/cooldown handling there
- Preserve **coin consumption before CP consumption**. Insufficient CP after a
  successful judgement spends the coin without producing a beam, as classic did
- A first early or ineligible attempt returns false and marks the QTE judged.
  Further attempts cannot succeed, but the physical toss continues and returns
  its coin. `hasPendingAttempt(player)` is only a convenience; the judgement
  method still validates state itself
- `abort(player)` is physical lifecycle cleanup for logout, death/clone and
  dimension transitions. Call it for the **original** player before replacing
  a clone and before its inventory/persistent data is saved. Do not call it on
  normal Railgun key-up or the generic input `abort`: legacy key cancellation
  canceled iron charging, not the tossed physical coin
- `clear()` refunds all outstanding escrows. Call it in `ServerStoppingEvent`
  before shutdown saves, with a final `ServerStoppedEvent` cleanup if desired

The Railgun eligibility latch is armed at toss only for an enabled, learned,
level-valid, controllable Electromaster Railgun, then checked again at judgement.
The modern adapter has a fixed skill key instead of the original preset delegate
lookup. One judgement per toss closes legacy client-trust/replay vulnerabilities.

## Trusted trajectory

`CoinTosses.Trajectory` is a pure common-side simulator with no client classes:

1. Start at actual player feet Y with velocity `0.92 + player.motionY`
2. Each tick subtract `0.06`, multiply by LambdaLib's default `linearDrag=1.0`,
   then move Y. There is no horizontal motion; visual X/Z follow the player
3. Keep the maximum Y reached. Returning condition is
   `(coinY < playerY && velocityY < 0) || age > 120`
4. Rising progress is `(0.92 - velocityY) / 0.92 * 0.5`; descending progress is
   `min(1, 0.5 + (maximumY - coinY)/(maximumY - initialY) * 0.5)`
5. QTE firing requires strict `progress > 0.7`. The ready hint uses inclusive
   `progress >= 0.6`, matching classic's `progress < 0.6 ? CHARGE : ACTIVE`

The exact initial-height float cast, zero-initialized maximum height, negative
rising progress for upward-moving players, and zero-denominator NaN are retained.
NaN cannot pass the strict comparison. The classic zero maximum means negative-Y
modern terrain has a different descending window from positive-Y terrain; this
is documented rather than silently normalized away.

LambdaLib stored motion handlers in a HashMap, so its relative `KeepPosition`
and `Rigidbody` iteration order was not a contractual guarantee. The adapter
uses deterministic gravity/movement, then maximum/return evaluation. Rigidbody's
ray trace only posted a `CollideEvent`; this coin registered no collision handler,
so solids do not stop its flight in either implementation.

For a stationary player at Y=64, the first step reaches Y=64.86, peak Y=70.6,
ready first occurs at tick 22, late firing at tick 25, and physical return at tick
30. Jumping/falling changes that window through actual server motion/height.

## Inventory, save and entity consequences

Refund prefers an empty main hand, then a matching non-full main-hand stack,
then another matching inventory stack/free slot, then a dropped `ItemEntity` at
player feet +0.6. Item components from the actually consumed coin are retained.
For dead/removed players the escrow is dropped rather than placed into an old
inventory which has already been dropped/replaced. Creative tosses never refund;
switching game modes cannot turn a free toss into a paid coin.

Survival escrow is stored under mod-owned player persistent NBT alongside the
inventory. A normal restart **returns** a saved in-flight coin on next tick;
trajectory, axis, token and QTE are transient and do not resume. Graceful logout,
clone and shutdown cleanup must precede player saves. The top-level escrow is
not intentionally copied to a respawned player: abort/drop the original first.
As with Minecraft inventory generally, a process crash can roll back to the
last player save; no cross-file transactional/crash-durability claim is made.
The original saved entity discarded itself and spawned a fresh coin on load,
including a creative duplication risk; that entity-load behavior is not copied.

There is no targetable coin hitbox, entity tracking ID, entity collision event,
NBT entity reload or late-watcher tracking sync. Only players near the toss
receive the visual packet. A player entering range during its short flight may
miss it. These are explicit logical-entity limitations, not completed runtime
equivalence claims. The optional heads-or-tails chat configuration is not wired.

## Client contract and rendering

Forward `coin_toss` and `coin_end` `AcademyNetwork.ClientData` tags to
`ClassicCoinEffects.receive(tag)` from client code. No common/dedicated-server
initialization may reference `ClassicCoinEffects`.

The server broadcasts its entity ID, toss token, actual feet origin/vertical
motion, random axis `(0.1+random, random, random)`, and QTE eligibility. This is
emitted privately from `CoinTosses` using the existing `ClientData` carrier, so
`AcademyNetwork.effect` need not be expanded. End packets match both player and
token, preventing an old end from removing a newer visual.

On each new Railgun key-down, call `ClassicCoinEffects.attempt()` first. If its
`OptionalLong` is present, send `coin_attempt` with that token and do not start
iron charging for the same press. It marks the local QTE judged even if pressed
early and sends the early attempt so the server records it. Subsequent key-downs
may follow the ordinary iron path while that judged coin is still falling.
`hasPendingAttempt()`, `ready()` and `progress()` are UI-only hints. A self-
subscribing HUD displays the original Railgun skill icon, configured key and
CHARGE/ACTIVE phase while a usable coin judgement is pending. Release or
screen input cancellation may cancel iron charging without refunding the toss.

The self-subscribing client renderer uses modern PoseStack/buffer/shader APIs,
the original 32x32 front/back textures, culling and alpha-tested texture shader.
It reconstructs the two faces at Z=±0.0625 and the original 32 doubled vertical
extrusion strips, then the original 0.3 scale, yaw/body transform and
`(-0.63,-0.60,0.30)` offset. As in classic, the local player's relative X/Z are
zeroed, including third-person; remote visuals follow interpolated player X/Z.
Rotation is around the randomized axis and the item center, using precisely
`(gameMillis % 150) * 360 / 300`, including its half-turn/reset cadence. Timing
and simulation freeze while a single-player world is paused. World changes and
invalid owners clear visual state; completed visual records do not persist.

Client simulation starts at packet receipt and server progress remains trusted.
Latency can shift the hint/window, and world/hand occlusion, third-person camera
placement, shaders, dimensions and inventory/lifecycle behavior still require
in-game QA after EULA/startup authorization.

## Standalone checks

`src/test/java/cn/academy/port/CoinTossRegressionTest.java` verifies gravity-before-
movement, drag, peak/descending progress, first-ready/fire/return ticks, early and
successful replay rejection, eligibility, rising player motion, strict height and
120-tick boundaries, negative height and NaN behavior. Run it with the test/main
runtime classpath after `testClasses`; it does not bootstrap or launch Minecraft.

Inventory refund, restart escrow, token routing, server/client sided loading and
the render result still require dedicated runtime checks. Gradle build wiring
may add the standalone regression task to `check`.
