# VecManip first skill: Directed Shock (`dir_shock`)

This is the server gameplay adapter for AcademyCraft classic 1.0.7's first
Vector Manipulation skill. It is a **hold-and-release punch**, not an immediate
cast or a ray/beam rendering effect. Its category, tree identity and level come
from the existing `SkillCatalog`: `vecmanip`, `dir_shock`, level 1, controllable,
no parent requirement.

## Audited sources

- AcademyCraft: `.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/vecmanip/skill/DirectedShock.scala`
- Hand animation: the same tree's `src/main/scala/cn/academy/vanilla/vecmanip/client/effect/AnimPresets.scala`
- Attack/consumption wrappers: the same tree's `src/main/java/cn/academy/ability/api/AbilityContext.java`
- LambdaLib dependency pinned by the classic checkout's `build.properties`: 1.2.3
- [LambdaLib 1.2.3 Raytrace](https://github.com/LambdaInnovation/LambdaLib/blob/1.2.3/src/main/java/cn/lambdalib/util/mc/Raytrace.java)
- [LambdaLib 1.2.3 living selector](https://github.com/LambdaInnovation/LambdaLib/blob/1.2.3/src/main/java/cn/lambdalib/util/mc/EntitySelectors.java)
- [LambdaLib 1.2.3 block selector](https://github.com/LambdaInnovation/LambdaLib/blob/1.2.3/src/main/java/cn/lambdalib/util/mc/BlockSelectors.java)
- [LambdaLib 1.2.3 entity coordinates](https://github.com/LambdaInnovation/LambdaLib/blob/1.2.3/src/main/scala/cn/lambdalib/util/mc/RichEntity.scala)
- Modern APIs: `build/moddev/artifacts/neoforge-21.1.252-sources.jar`, especially
  `Level`, `AABB`, `EntityHitResult`, `Entity`, `LivingEntity`, and `ServerPlayer`

LambdaLib's MIT attribution is retained in `docs/LAMBDALIB-LICENSE`. Classic
AcademyCraft source/asset distribution restrictions described by `NOTICE` and
`docs/UPSTREAM-README-1.0.7.md` still apply.

## Default gameplay retained

For normalized mastery `e` from 0 to 1:

| Behavior | Classic value / rule |
|---|---|
| Valid release | Strictly `ticks > 6 && ticks < 50`, therefore 7–49 ticks |
| Preparation timeout | `ticks >= 200` cancels; a 50–199 tick hold is not performable |
| Punch animation tick constant | 6; the local classic tick listener terminates when its punch counter exceeds 6 |
| Range | 3 blocks from the caster's eye |
| CP cost | `lerp(50, 100, e)` |
| Overload | `lerp(18, 12, e)` |
| Raw damage | `lerp(7, 15, e)` captured when preparation begins |
| Hit cooldown | Integer truncation of `lerp(60, 20, e)` before awarding hit mastery |
| Hit mastery | 0.0035 |
| Miss mastery | 0.0010 |
| Miss cooldown | None |
| Failed CP consumption | No hit, mastery, cooldown, or impact sound |
| Strong knockback threshold | `e >= 0.25` before this attempt's mastery award |

Consumption occurs once on accepted release and precedes target tracing. Thus a
valid miss still consumes CP/overload and gains miss mastery. A detected entity
hit earns hit mastery and cooldown even when its armor/invulnerability causes
damage to be rejected. Starting preparation does not reserve or consume CP.
There is no charge-dependent damage multiplier within the accepted timing window.

Shared `AbilityProgress.consume` supplies classic creative handling, resource
recovery delays and maximum-resource training. `AbilityDamage.attack` supplies
the mutable skill attack event, configurable global damage scale/attack-player
switch, normal armor handling, and the classic skill death message. The attack
identity supplied to that helper is `vecmanip.dir_shock`.

### Trace details

The query segment AABB is expanded by 1 block. Candidate living entities and
`EnderDragonPart` hitboxes are expanded by **0.3F**. The closest six-plane hitbox
intercept is chosen, including an exit-plane intersection when preparation is
released with the caster eye already inside that hitbox. An exact boundary
origin or segment-end intersection remains valid. The old squared-delta cutoff
for each plane axis is retained.

Modern `AABB.clip` is not an equivalent replacement for the classic inside-box
case, so `classicIntercept` performs the six-plane selection directly. The
original entity selector's zero-distance sentinel is preserved. The target
selection does not trust any client-provided entity ID.

Block tracing uses modern collision shapes with `COLLIDER` and `Fluid.NONE`,
matching classic `filNormal`'s collision-box / non-liquid filtering intent.
After selecting the closest entity intercept, classic LambdaLib constructed an
entity trace whose hit position was the entity's **feet**, then compared that
feet distance against the block distance. This unusual final test is retained,
including the entity winning an equality. Consequently a target with its feet
farther from the caster than a block can lose that comparison even if part of
its inflated hitbox intersects before the block.

### Knockback order

After the damage attempt, at mastery at least 0.25:

1. Compute `(casterHead - targetHead).normalize()`
2. Replace that direction's y coordinate with **-0.6F**
3. Normalize again and multiply by **-0.7F**
4. Move the target up by 0.1 and **replace** its velocity with that impulse

At every mastery, add
`(targetFeet - casterFeet).normalize() * 0.24` to its resulting velocity. This
second calculation uses the target's feet **after** the optional 0.1 lift. Below
0.25, the second impulse is added to the target's existing post-damage velocity.
Modern `hasImpulse` and `hurtMarked` tracking flags are set explicitly.

## Integration contract

All entry points are static on `cn.academy.port.skill.DirectedShock` and must be
called on the main server thread:

```java
boolean start(ServerPlayer player);
void tick(ServerPlayer player);
boolean release(ServerPlayer player);
void abort(ServerPlayer player);
void clear();
```

- Route `dir_shock` key-down to `start`, and key-up to `release`
- Route key abort / screen interruption / selected ability or preset change to
  `abort`; cancel the hold on explicit category changes or ability deactivation
- Call `tick(player)` from the server player's ordinary post-tick path
- Call `abort` on logout and for both relevant original/replacement player
  identities during cloning; call `clear` on server stop
- Save and sync shared ability state after an input action using the ordinary
  `AbilityStorage.save` / `AcademyNetwork.sync` path
- Do not translate an immediate cast request into an uncharged perform

`start` returns whether a new preparation was accepted. Repeated start messages
cannot reset an existing valid start timestamp. `release` returns whether
consumption succeeded and an attempt occurred; **true includes a miss**.
Invalid timing, replayed release, aborted context, or failed CP returns false.

The server retains world identity, shared state identity, start game time and
initial raw damage. Release removes the context before consumption/effects.
Duration is calculated from server game time. Catalog membership, category,
minimum level, learned mastery, activation, cooldown, overload-fine state,
interference, life/spectator/removal state and finite server eye/look are checked
on start and again at release. Tick cleanup also handles death, category/state
replacement, dimension change and the 200-tick expiry. There is no persisted
hold and no player/target/ticks/mastery input accepted from the client.

### Existing effect payloads

No custom entity or new payload type is required for the server adapter.
`AcademyNetwork.effect` emits:

| Kind | Trigger | Length field |
|---|---|---|
| `dir_shock_prepare` | New accepted preparation | 0 |
| `dir_shock` | Detected entity hit after successful consumption | 3 |
| `dir_shock_abort` | Abort/expiry, rejected release, failed CP, or a paid miss | 0 |

Each packet includes the caster entity ID and server origin/look direction. The
payload currently lacks target identity, so it must **not** be used to repeat
knockback locally. Normal server entity tracking supplies target movement.

## Explicit fidelity differences and remaining work

- Classic timing was checked on the local client's ticker, and its server
  handler accepted the transmitted ticker without checking it. This adapter
  deliberately uses trusted server duration and revalidation. Network latency
  can therefore affect a release near the 7/49-tick boundaries differently
- Dead/removed/spectator candidates are rejected. Explicit skill knockback is
  suppressed for players protected by the port's attack-player switch or
  vanilla PvP/team rules; this closes the legacy pattern of separately moving a
  target even when player damage is disallowed. Detected-hit mastery/cooldown
  classification remains unchanged
- Modern collision/eye/pose representations and entity physics differ from
  Minecraft 1.7.10. The stated formulas are retained, but actual final movement,
  block shape comparisons, armor and damage behavior require an in-game check
- Shared progression currently accumulates doubles instead of the classic
  floats. Formula endpoints, thresholds, cooldown truncation and impulse float
  constants are covered, but bit-identical long-session mastery arithmetic is
  not claimed
- The skill-specific CP, overload, mastery and damage configuration multipliers
  used by classic `AbilityContext`, and interactions with every classic passive
  skill, are not implemented by this adapter; only the available shared port
  pipeline is used
- **No client hand-animation parity is supplied by this server file.** The
  classic local first-person preparation used `CompTransformAnim` and the
  player's hand renderer, with parameter `min(2, elapsedMillis / 150)`. A hit
  switched to a punch animation with parameter `elapsedMillis / 300`, while
  the context lifetime and the punch tick listener controlled its teardown
- Exact modern prepare/punch rendering must adapt the classic `AnimPresets`
  curves, animate the actual first-person hand, and clean up on cancel, context
  finish, dimension/level changes and disconnect. The preparation curves have
  y points `(0,0),(.5,.2),(1,.4)`, x ending at -.02, z ending at -.05 and x
  rotation ending at -20 degrees. Punch y points are `(0,.8),(.5,.75),(1,0)`,
  x points `(0,-.04),(.5,-.04),(1,0)`, z points `(0,0),(.3,-.4),(1,0)`, x
  rotation `(0,-40),(.5,-45),(1,0)` and y rotation `(0,0),(.3,10),(1,0)`
- Impact sound is the original `academy:vecmanip.directed_shock` at volume 0.5,
  on the hit effect only, played once for the caster and nearby clients. A beam,
  charge sprite, generic particle burst or ordinary vanilla swing is not an
  equivalent replacement for the classic hand animation

## Verification

The owned `DirectedShock.java` compiles against the real NeoForge 21.1.252 /
Minecraft 1.21.1 Java 21 classpath. The deterministic
`cn.academy.port.skill.DirectedShockRegressionTest` passes **141 assertions**:

- Costs/damage/range/cooldown at mastery endpoints and intermediate values
- Strict release bounds, preparation timeout and invalid numeric mastery
- Catalog/category/learning/level/activation/interference/overload/cooldown gates
- Paid miss versus hit, insufficient CP, creative consumption, recovery delays,
  pre-mastery-gain cooldown and capped mastery
- Strong knockback threshold/formula, after-lift extra impulse and zero-vector
  behavior
- All six intercept planes, inside-box exit, exact boundary/endpoint, misses,
  axis cutoff and the unusual feet-vs-block distance comparison

It is a plain deterministic Java regression runner. Integration now registers
the `directedShockTest` JavaExec task and includes it in `check`; invoke it with
`scripts/gradle-cloud.sh --no-daemon directedShockTest`. Before that registration
it was run using the same wrapper and a temporary Gradle init script registering
`vectorSkillTest` with `sourceSets.test.runtimeClasspath` and dependency on
`testClasses`.

The subsequent integrated `scripts/gradle-cloud.sh --no-daemon directedShockTest
build` also passed: **441 deterministic assertions across six runners**, and the
mod JAR assembled successfully. This is a build/data/geometry result, not an
in-game acceptance result.

No Minecraft client, dedicated server or GameTest server was started. Actual
packet/input sequencing, world damage/protection, repeated release behavior in
a running server, target movement and first-person/audio appearance have **not**
been runtime-verified. Runtime testing remains pending Minecraft EULA approval.
