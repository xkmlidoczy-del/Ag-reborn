# AcademyCraft classic 1.0.7 behavior audit

Audited directly from the authorized extracted source at `.reference/AcademyCraft-1.0.7`. All source paths below are relative to that directory. This is a **source audit**, not a claim that the original mod has been executed in Minecraft. Values are default-configuration values; event hooks and server configuration may change them. No user-computer contents were read and no implementation files were edited for this audit.

## Scope and portability

Four categories exist: `electromaster`, `meltdowner`, `teleporter`, and `vecmanip`. They register in `src/main/java/cn/academy/vanilla/ModuleVanilla.java`. Category string names should be stable persistence keys in the new port. The original category numeric IDs are assigned by annotation-library registration order (`CategoryManager.register`); the source alone does not establish a guaranteed hard-coded category number. Skill numeric IDs **are** established by the order of `Category.addSkill`, starting at zero, including the appended generic passives.

The companion `classic-skills.json` contains all 50 category/skill entries, their string IDs, original per-category numeric indexes, levels, parents, every explicit dependency EXP threshold, controllability, generic/customized-EXP flags, developer tiers, learning stimulations, source paths, and EXP gain descriptions. Generic skills have separate instances and numeric indexes in each category, but original localization/config full names begin with `generic.`.

## Reading the manifest

- EXP is a normalized skill mastery value in [0,1], not an integer XP count
- A dependency `skill ≥ x` means that skill is learned **and** its normalized EXP is at least x; a zero threshold still requires learning
- The first dependency is the tree parent; further dependencies are additional requirements
- Every skill additionally requires player level ≥ its skill level and the minimum developer tier
- Disabled dependencies are omitted by `Skill.setParent` / `addSkillDep`; disabled skills are unavailable for control and excluded from level-progression counts
- `Skill.canControl()` is `isEnabled() && canControl`; generic courses and the three category passives marked below are noncontrollable
- Root means no explicit parent/dependency, rather than permission to ignore level/developer requirements

Core source: `src/main/java/cn/academy/ability/api/{Category.java,CategoryManager.java,Skill.java}`, `src/main/java/cn/academy/ability/api/registry/CategoryRegistration.java`, and `src/main/java/cn/academy/ability/develop/condition/{DevConditionDep.java,DevConditionLevel.java,DevConditionDeveloperType.java,DevConditionAnySkillOfLevel.java}`.

## Exact skill manifests

The numeric index is the original skill ID within its category. The EXP gain column records the direct source expressions and triggering branch; it is affected by the `AbilityContext` EXP multiplier unless the source bypasses that wrapper. Individual execution success/failure behavior is not fully re-audited for non-Railgun skills.

### electromaster

| Index | Skill ID | Level | Control | Parent / other prerequisites | EXP gain |
|---:|---|---:|:---:|---|---|
| 0 | `arc_gen` | 1 | yes | root | Entity hit: lerp(0.0048,0.0072,e); block hit: lerp(0.0018,0.0027,e); miss: 0 |
| 1 | `charging` | 1 | yes | parent: arc_gen ≥ 0.3 | Per charging tick: effective 0.0001, ineffective 0.00003 |
| 2 | `mag_movement` | 2 | yes | parent: arc_gen ≥ 0, also: charging ≥ 0.7 | On termination: max(0.005,0.0011*distanceTraveled) |
| 3 | `mag_manip` | 2 | yes | parent: mag_movement ≥ 0.5 | On successful release/perform: 0.005 |
| 4 | `mine_detect` | 3 | yes | parent: mag_manip ≥ 1 | On successful activation: 0.008 |
| 5 | `body_intensify` | 3 | yes | parent: arc_gen ≥ 1, also: charging ≥ 1 | On successful buff release: 0.01 |
| 6 | `thunder_bolt` | 4 | yes | parent: arc_gen ≥ 0, also: charging ≥ 0.7 | On perform: effective 0.005, ineffective 0.003 |
| 7 | `railgun` | 4 | yes | parent: thunder_bolt ≥ 0.3, also: mag_manip ≥ 1 | On successful resource consumption: shared hitEntity flag true -> 0.01, false -> 0.005; classic flag bug documented in audit |
| 8 | `thunder_clap` | 5 | yes | parent: thunder_bolt ≥ 1 | On perform: 0.003 |
| 9 | `brain_course` | 3 | passive | root; any learned L3 skill | No EXP increment defined |
| 10 | `brain_course_advanced` | 4 | passive | parent: brain_course ≥ 0; any learned L4 skill | No EXP increment defined |
| 11 | `mind_course` | 5 | passive | parent: brain_course_advanced ≥ 0; any learned L5 skill | No EXP increment defined |

### meltdowner

| Index | Skill ID | Level | Control | Parent / other prerequisites | EXP gain |
|---:|---|---:|:---:|---|---|
| 0 | `electron_bomb` | 1 | yes | root | On successful shot: 0.005 |
| 1 | `rad_intensify` | 1 | passive | parent: electron_bomb ≥ 0.5 | Customized display EXP: clamp(0,1,currentMaxCP/eventAdjustedInitCP(level5)); no ordinary use increment |
| 2 | `scatter_bomb` | 2 | yes | parent: electron_bomb ≥ 0.8 | On context termination: 0.001*numberOfBalls |
| 3 | `light_shield` | 2 | yes | parent: electron_bomb ≥ 1 | Per tick: 0.000001; each touch attack: 0.001; handled incoming attack: 0.001 |
| 4 | `meltdowner` | 3 | yes | parent: scatter_bomb ≥ 0.8, also: light_shield ≥ 0.8 | On shot: lerp(0.8,1.2,(min(chargeTicks,40)-20)/20)*0.002 |
| 5 | `mine_ray_basic` | 3 | yes | parent: meltdowner ≥ 0.3 | Per block broken: 0.0005 |
| 6 | `ray_barrage` | 4 | yes | parent: meltdowner ≥ 0.5 | On execute: 0.005 |
| 7 | `jet_engine` | 4 | yes | parent: meltdowner ≥ 1 | On successful destination trigger: 0.004 |
| 8 | `mine_ray_expert` | 4 | yes | parent: mine_ray_basic ≥ 0.8 | Per block broken: 0.0003 |
| 9 | `mine_ray_luck` | 5 | yes | parent: mine_ray_expert ≥ 1 | Per block broken: 0.0003 |
| 10 | `electron_missile` | 5 | yes | parent: jet_engine ≥ 0.3 | Each successful scheduled direct attack using a stored missile: 0.001 |
| 11 | `brain_course` | 3 | passive | root; any learned L3 skill | No EXP increment defined |
| 12 | `brain_course_advanced` | 4 | passive | parent: brain_course ≥ 0; any learned L4 skill | No EXP increment defined |
| 13 | `mind_course` | 5 | passive | parent: brain_course_advanced ≥ 0; any learned L5 skill | No EXP increment defined |

### teleporter

| Index | Skill ID | Level | Control | Parent / other prerequisites | EXP gain |
|---:|---|---:|:---:|---|---|
| 0 | `threatening_teleport` | 1 | yes | root | On successful perform: attacked 0.003, otherwise 0.0006 |
| 1 | `dim_folding_theorem` | 1 | passive | parent: threatening_teleport ≥ 0.2 | On critical TP hit tier i=0..2: (i+1)*0.005; TPSkillHelper adds directly |
| 2 | `penetrate_teleport` | 2 | yes | parent: threatening_teleport ≥ 0.5 | On perform: 0.00014*distance |
| 3 | `mark_teleport` | 2 | yes | parent: threatening_teleport ≥ 0.4 | On successful teleport: 0.00018*distance |
| 4 | `flesh_ripping` | 3 | yes | parent: mark_teleport ≥ 0.5, also: penetrate_teleport ≥ 0.5 | On successful hit: 0.005 |
| 5 | `location_teleport` | 3 | yes | parent: penetrate_teleport ≥ 0.8, also: mark_teleport ≥ 0.8 | On successful teleport: distance>=200 -> 0.03, otherwise 0.015 |
| 6 | `shift_tp` | 4 | yes | parent: location_teleport ≥ 0.5 | On successful placement/attack: (1+targetsInLine)*0.002 |
| 7 | `space_fluct` | 4 | passive | parent: shift_tp ≥ 0 | On critical TP hit: 0.0001; TPSkillHelper adds directly |
| 8 | `flashing` | 5 | yes | parent: shift_tp ≥ 0.8 | Each successful directional teleport: 0.002 |
| 9 | `brain_course` | 3 | passive | root; any learned L3 skill | No EXP increment defined |
| 10 | `brain_course_advanced` | 4 | passive | parent: brain_course ≥ 0; any learned L4 skill | No EXP increment defined |
| 11 | `mind_course` | 5 | passive | parent: brain_course_advanced ≥ 0; any learned L5 skill | No EXP increment defined |

### vecmanip

| Index | Skill ID | Level | Control | Parent / other prerequisites | EXP gain |
|---:|---|---:|:---:|---|---|
| 0 | `dir_shock` | 1 | yes | root | On perform: entity hit 0.0035, otherwise 0.001 |
| 1 | `ground_shock` | 1 | yes | parent: dir_shock ≥ 0 | Each affected entity 0.002 plus successful groundshock 0.001 |
| 2 | `vec_accel` | 2 | yes | parent: dir_shock ≥ 0 | On server perform: 0.002 |
| 3 | `vec_deviation` | 2 | yes | parent: vec_accel ≥ 0 | Reduced incoming damage: damage*0.0006; affected projectile: difficulty*0.001 |
| 4 | `dir_blast` | 3 | yes | parent: ground_shock ≥ 0 | On successful perform: effective 0.0025, ineffective 0.0012 |
| 5 | `storm_wing` | 3 | yes | parent: vec_accel ≥ 0 | Each active server tick: 0.00005 |
| 6 | `blood_retro` | 4 | yes | parent: dir_blast ≥ 0 | On successful hit: 0.002 |
| 7 | `vec_reflection` | 4 | yes | parent: vec_deviation ≥ 0 | Reflected projectile: difficulty*0.0008; handled incoming damage: damage*0.0004 |
| 8 | `plasma_cannon` | 5 | yes | parent: storm_wing ≥ 0 | On server perform: 0.008 |
| 9 | `brain_course` | 3 | passive | root; any learned L3 skill | No EXP increment defined |
| 10 | `brain_course_advanced` | 4 | passive | parent: brain_course ≥ 0; any learned L4 skill | No EXP increment defined |
| 11 | `mind_course` | 5 | passive | parent: brain_course_advanced ≥ 0; any learned L5 skill | No EXP increment defined |

## Skill learning and development

`Skill.getLearningStims()` truncates `3 + level² × 0.5` to integer:

| Skill level | Stimulations | Minimum developer |
|---:|---:|---|
| 1 | 3 | PORTABLE |
| 2 | 5 | PORTABLE |
| 3 | 7 | NORMAL |
| 4 | 11 | ADVANCED |
| 5 | 15 | ADVANCED |

A developer's ordinal must be ≥ the required tier's ordinal. In `DeveloperType.java`, portable/normal/advanced have 25/20/15 ticks per stimulation, respectively, and 750/700/600 energy consumption per stimulation. Their storage capacities are 10000/50000/200000. The three generic courses require any learned skill of their own level, in addition to the prior generic course for the latter two.

Generic passive effects:

- `brain_course` (L3): adds 1000 to the `CalcEvent.MaxCP` value
- `brain_course_advanced` (L4): adds 1500 to max CP and 100 to max overload; cumulative with brain course
- `mind_course` (L5): multiplies the CP-recovery event factor by 1.2

Source: `src/main/java/cn/academy/vanilla/generic/skill/{SkillBrainCourse.java,SkillBrainCourseAdvanced.java,SkillMindCourse.java}` and `src/main/java/cn/academy/vanilla/ModuleVanilla.java`.

The Meltdowner passive `rad_intensify` has customized EXP: `clamp(0,1,currentTotalMaxCP / getInitCP(5))`. The denominator is the event-adjusted level-5 max CP, so learned generic CP buffs also affect the denominator. Its radiation multiplier is `lerp(1.4,1.8,passiveExp)`.

Teleporter critical hits try three tiers **in order**, stopping on the first success. With `d=dim_folding_theorem EXP`, `s=space_fluct EXP`, and an unlearned skill's contribution treated as zero:

- First-tier probability = `lerp(.1,.2,d) + lerp(.18,.25,s)`; damage ×1.3
- Second-tier conditional probability = `lerp(.10,.15,s)`; damage ×1.6
- Third-tier conditional probability = `lerp(.01,.03,s)`; damage ×2.6
- A successful tier `i=0..2` adds `(i+1)×.005` dim-folding EXP and `.0001` space-fluct EXP directly through `AbilityData`, bypassing `AbilityContext`'s per-skill EXP multiplier

Source: `src/main/java/cn/academy/vanilla/teleporter/util/TPSkillHelper.java`. These are sequential conditional draws, not independent final tier probabilities.

## CP, overload, and training curves

Primary sources:

- `src/main/resources/assets/academy/config/default.conf`, `ac.ability.data`
- `src/main/java/cn/academy/ability/api/data/CPData.java`
- `src/main/java/cn/academy/ability/api/AbilityContext.java`

### Base values and usage-grown allowances

Arrays index **the player's level directly**, so entry zero is also provided. Default configuration:

| Level | Base CP | Max trained extra CP | Base overload | Max trained extra overload |
|---:|---:|---:|---:|---:|
| 0 | 1800 | 0 | 100 | 0 |
| 1 | 1800 | 900 | 100 | 40 |
| 2 | 2800 | 1000 | 150 | 70 |
| 3 | 4000 | 1500 | 240 | 80 |
| 4 | 5800 | 1700 | 350 | 100 |
| 5 | 8000 | 12000 | 500 | 500 |

`getMaxCP() = maxCP + addMaxCP`; `getMaxOverload() = maxOverload + addMaxOverload`. Here `maxCP` / `maxOverload` are event-adjusted bases after learned passive bonuses, not the trained extras. Generic brain bonuses belong to the base for recovery calculations as well.

On a successful performed action, after the `CalcEvent.SkillPerform` adjustments:

- `addMaxCP ← min(levelExtraCPCap, addMaxCP + CPcost × .0025)`
- `addMaxOverload ← min(levelExtraOverloadCap, addMaxOverload + clamp(0,10,overloadCost × .0058))`
- A creative player pays no resources but **still grows trained CP/overload extras** using the nominal adjusted costs
- Failed ordinary CP consumption adds no overload or trained extras
- `consumeWithForce` consumes even when CP is insufficient, clamping remaining CP to zero; it still adds overload/training

Per-skill CP/overload consumption multipliers are applied in `AbilityContext.consume`, before `CPData.perform`; `consumeWithForce` directly delegates without those multipliers. `CalcEvent.SkillPerform` can adjust both costs afterward.

### Recovery formulas

Let `Bcp=maxCP`, `Bo=maxOverload`, `C=currentCP`, `O=currentOverload`. Recovery executes once per Minecraft tick while the player has a category, even if abilities are deactivated. At nominal 20 TPS, multiply these values by 20 for per-second rates.

CP recovery per tick, when its delay is zero:

`CPRecovery = cp_recover_speed × .0003 × Bcp × lerp(1,2,C/Bcp) × CPRecoverSpeedEventFactor`

Default `cp_recover_speed=1`; the factor begins at 1 and can be multiplied by mind course. Recovery uses the **base** Bcp, whereas the final clamp is the **total** max CP. The ratio `C/Bcp` is not clamped, so trained CP above the base leads to more than 2× the low-CP recovery rate.

Overload recovery per tick, when its delay is zero:

`OverloadRecovery = overload_recover_speed × max(.002×Bo, .007×Bo×lerp(1,.5,O/Bo/2)) × OverloadRecoverSpeedEventFactor`

Equivalently before the event factor: `max(.002×Bo, .007×Bo − .00175×O)`. Default speed and event factor are 1. This also uses the event-adjusted **base** Bo rather than total overload capacity.

Each successful noncreative CP consumption, including zero-cost calls, resets the CP-recovery delay to **15 ticks**. Each overload-add call, even amount zero, resets the overload-recovery delay to **32 ticks**. A tick with a positive delay decrements it and does not recover; recovery begins on the subsequent tick when it is already zero. Sustained skills can intentionally pin overload to their startup snapshot and repeatedly reset delays.

### Overload state and lifecycle

- An action does **not** pre-reject because its overload increment would exceed capacity
- Overload clamps to the pre-training total maximum; reaching that value sets `overloadFine=false` and emits `OverloadEvent`
- `canUseAbility = activated && overloadFine && !interfering`
- This lock remains until overload fully returns to zero; dropping below max is insufficient
- `isOverloaded()` is narrower: `!overloadFine && untilOverloadRecover>0`; after that delay the player remains unable to use abilities while recovering
- After adding overload, the successful action grows trained max overload. The lock can therefore remain active even when O becomes less than the newly increased total max
- Recalculating base max values refills total CP and sets overload to zero
- Learning a new skill triggers recalculation/refill, even if the learned skill does not change max values
- A level change clears both trained extras, recalculates the bases, and refills CP/clears overload
- Category change recalculates max values; losing the category also deactivates ability use
- Sleep/wake recovery and death call `recoverAll`; death also deactivates abilities
- `recoverAll` sets overload zero but `overloadFine=false` in this source. The next eligible overload-recovery tick restores the flag; this appears to be a classic lifecycle quirk

`ClientRuntime.java` also aborts pressed-key delegates during cooldown, overload/interference/deactivation, terminal UI, or leaving gameplay. `CPData.perform` itself checks CP rather than all of these gates. A safe authoritative port should explicitly validate the gates server-side.

## Skill EXP and level progression

Source: `src/main/java/cn/academy/ability/api/data/AbilityData.java`.

Ordinary skill EXP is stored as a float per per-category numeric index. `addSkillExp(skill,amount)` on the server first learns that skill if it was not already learned. The addition to its stored EXP is `min(1−currentSkillExp,amount)`, normally capping mastery at 1.

However, **level progress accumulates the requested amount, even when skill mastery was already capped**:

`expAddedThisLevel += amount × category.common.prog_incr_rate × ac.ability.data.prog_incr_rate`

Both configuration multipliers default to 1 in all four categories. Thus replaying mastered skills can still raise the player level. Gains from lower-level skills or passive EXP also accumulate at the current player level. There is no requirement that the EXP-generating skill itself matches the current level.

Let `N` be the number of enabled, controllable skills whose skill level equals the **current player level**:

`threshold = N × (playerLevel==4 ? 1.333 : .666)`

`levelProgress = threshold==0 ? 1 : min(1,expAddedThisLevel/threshold)`

`canLevelUp = playerLevel<5 && levelProgress==1`

The multipliers are literal decimal floats `.666f` and `1.333f`; use those rather than substituting exact fractions 2/3 and 4/3 if matching classic arithmetic is important.

| Category | N at levels 1,2,3,4,5 | Progress thresholds for levels 1→2,2→3,3→4,4→5 |
|---|---|---|
| electromaster | 2,2,2,2,1 | 1.332,1.332,1.332,2.666 |
| meltdowner | 1,2,2,3,2 | .666,1.332,1.332,3.999 |
| teleporter | 1,2,2,1,1 | .666,1.332,1.332,1.333 |
| vecmanip | 2,2,2,2,1 | 1.332,1.332,1.332,2.666 |

Level progress never auto-upgrades the level. Completing a `DevelopActionLevel` calls `setLevel(level+1)`, which resets `expAddedThisLevel=0` and fires the CP recalculation event. A new category initializes a level-zero player to level 1; removing a category sets their level to 0. Setting a different non-null category preserves an already-positive level. `setCategory` clears learned skills/mastery but does not explicitly clear `expAddedThisLevel` in this version.

Level-development stimulations are `5×(currentLevel+1)`: first awakening at level 0 is 5 stims; upgrades from levels1,2,3,4 are10,15,20,25. `LearningHelper.canLevelUp` takes a developer type argument but does not use it; it accepts players without a category or `AbilityData.canLevelUp()`. Skill learning still has the developer-tier condition described above.

Cooldown source: `src/main/java/cn/academy/ability/api/cooldown/CooldownData.java`. Cooldowns decrement per tick, are removed at zero, clear on category change/death, and synchronize every15 ticks. Reapplying an existing cooldown preserves the larger remaining/max value rather than shortening it.
## Railgun: authoritative skill behavior

Primary source: `src/main/scala/cn/academy/vanilla/electromaster/skill/Railgun.scala`.

### Identity and learning

- String skill ID: `railgun`; full name `electromaster.railgun`
- Original per-category numeric index:7; required skill/player level:4
- Parent:`thunder_bolt` learned at EXP≥.3
- Additional dependency:`mag_manip` learned at EXP≥1
- ADVANCED developer;11 learning stimulations
- The source registers the Scala singleton on the Forge event bus to respond to coin throws

### Input path A: thrown coin QTE

Supporting sources: `src/main/java/cn/academy/vanilla/electromaster/{item/ItemCoin.java,entity/EntityCoinThrowing.java,event/CoinThrowEvent.java}`.

1. Right-click the dedicated `coin` item. A player can have only one active thrown coin; if one already exists, right-click does nothing
2. Separate coin entities spawn on client/server for appearance; `CoinThrowEvent` is posted on both sides. Noncreative inventory loses one coin at throw time; creative does not
3. If abilities can currently be used and the current preset contains Railgun, the client Railgun delegate is informed of the coin, and the server sends the charge-hand effect to clients within30 blocks
4. Informing a new live coin cancels an in-progress ingot charge via `onKeyAbort`
5. A Railgun key press with a stored coin fires only if **coin progress >.7**. Regardless of successful timing, it clears the local coin reference, preventing a second QTE judgement on that throw
6. On an accepted press, the server marks the coin dead **before** attempting CP consumption; a CP-failed shot therefore still loses the coin
7. The preset HUD delegate shows CHARGE while coin progress<.6 and ACTIVE at progress≥.6. Consequently the display becomes ready before the strict>.7 firing threshold

Coin physics values: initial upward velocity +=.92 (plus the player's existing vertical velocity); gravity .06; size .2×.2; X/Z track the player's position. The throw normally finishes when it falls below the player's Y while descending, or ticksExisted>120. Maximum height is tracked for progress:

- Ascending: `progress=(.92−motionY)/.92×.5`
- Descending: `progress=min(1,.5 + (maximumY−currentY)/(maximumY−initialPlayerY)×.5)`

Do not replace that expression with linear elapsed time if faithful QTE behavior is required. A normal finished throw returns a coin to hand/inventory or drops it nearby; Railgun's `setDead()` consumes it without that return flow. Heads-or-tails chat is optional and disabled by default.

### Input path B: iron charge

- Accepted ammunition is exactly vanilla iron ingot or the item form of vanilla iron block; no ore-dictionary/tag matching
- Without a stored coin, Railgun key-down with accepted ammunition creates a local hand effect and sets `chargeTicks=20`
- Each held-key tick decrements the counter; at zero it sends the server item-perform message. This is20 subsequent held ticks, nominally1 second after key-down
- Key-up or abort sets the counter to−1, cancelling the charge
- After firing, the counter becomes negative on subsequent ticks; it does not automatically repeat the shot while held
- The server re-checks the current held item. One item is removed in noncreative mode **before** attempting CP consumption; an empty stack clears the held slot
- The hand-effect start for this path is local only in the source; it is not broadcast like the coin-throw hand effect
- The effect is not explicitly cancelled by early key release; the render hook expires by its own1.6-second timer

### Successful server perform constants

For captured pre-shot skill EXP `e`, `lerp(a,b,e)=a+(b−a)e`:

| Parameter | e=0 | e=1 | Formula / units |
|---|---:|---:|---|
| CP consumption |200|450|200+250e |
| Overload increment |180|120|180−60e |
| Starting damage |60|110|60+50e, before helper/event/config adjustments |
| Terrain-destruction energy |900|2000|900+1100e |
| Cooldown |300|160|integer truncation of300−140e, ticks |
| Initial displayed beam length |45|45|blocks |
| Damage-helper transverse range |2|2|blocks |
| Damage-helper forward maxIncrement |50|50|inherited helper default |

CP and overload are consumed once. Only successful consumption performs damage, triggers Railgun achievement, adds skill EXP, sets cooldown, and broadcasts the primary beam within20 blocks. The old shot captures `e` before adding EXP, so its cooldown uses the pre-shot mastery.

EXP branch in the exact source: `.01` when singleton `hitEntity` is true; otherwise `.005`. **Ordinary primary-beam hits never set that flag.** Only an entity hit by the server reflected-ray branch sets it true, and it is never reset for subsequent shots or players. This is a classic shared-state bug, not reliable per-shot hit detection. An intentional fix should use a per-shot actual-hit result and document the departure.

### Primary damage and terrain behavior

Source: `src/main/java/cn/academy/core/util/RangedRayDamage.java` and `src/main/java/cn/academy/ability/api/AbilityContext.java`.

Railgun uses `new RangedRayDamage.Reflectible(ctx,2,energy,callback)` without changing helper defaults:

- Ray motion is initialized from the player's true-mode view motion and moved .1 forward
- Helper starts with `maxIncrement=50`, `dropProb=.05`, and entity selector excluding the shooter
- Entities are selected within an enclosing forward AABB, additionally constrained by a perpendicular cross-product distance `< range×1.2` (Railgun:2.4)
- Candidates are sorted by squared distance from shooter; each is attacked until one reflects the ray
- The helper's damage falloff is **perpendicular distance**, despite its comment describing distance falloff: `startDamage × lerp(1,.2,min(50,perpendicularDistance)/50)`
- Therefore it is not a linear decrease along the45/50-block beam axis. In the2.4-block transverse target band damage is about96.16%–100% of starting damage before event/config adjustments
- Main visual beam length45 does not constrain the helper's inherited50 forward extent
- If a candidate reflects, the helper stops attacking farther candidates and limits block destruction by the reflector's squared distance from shooter
- If block-breaking is allowed, a radial transverse sample grid uses step .9 and radius jitter×random(.9,1.1); energy is divided among sampled lines, with each line receiving×random(.95,1.05)
- Each line plots up to `maxIncrement+1` block steps while energy remains; hardness is subtracted on destruction, negative hardness is treated as233333
- `BlockDestroyEvent` may veto a block; insufficient energy or a veto ends that line
- Breakable blocks drop with5% probability. There is also a5% chance per step to try an adjacent block, and stochastic block-break sound within the first20 plotted steps
- The `HashSet<int[]>` sample collection uses Java array identity rather than value equality; duplicate grid block coordinates are not actually deduplicated. Coordinate initialization casts to integer, which truncates rather than floors for negative world positions

`AbilityContext.attack` applies `CalcEvent.SkillAttack`, global/per-skill damage scaling, the ability PVP switch, and protection rules for paintings/item frames. The classic beam attacks all selected entities, rather than only living entities. Destruction is conditional on skill/global block-destruction permission and cancellation events; a port should preserve external protection hooks.

### Reflection

- `AbilityContext.attackReflect` posts cancellable `ReflectEvent`; cancellation invokes the callback instead of ordinary damage
- Reflection callback performs the reflected server ray, shortens the primary **visual** length to `min(45,distance(shooter,reflector))`, and sends a reflect visual message
- The reflected server ray uses `Raytrace.traceLiving(reflector,15)`; one entity hit receives fixed raw damage14 via the original shooter's Railgun `AbilityContext`
- That successful reflected hit sets the shared `hitEntity=true` flag discussed above
- Reflected visuals broadcast within20 blocks of the original shooter
- Client constructs a15-block `EntityRailgunFX` associated with the shooter, moves its origin along the shooter's view direction by shooter-to-reflector distance, then changes its yaw to reflector head yaw and pitch to reflector pitch
- The callback also calls `NetworkMessage.sendToServer` using the reflect channel from its server perform path, while the only matching Railgun reflect listener in this source is CLIENT-only; treat this as a suspicious redundant/mismatched-side call, not required new-port functionality

The old Railgun server message handlers do not themselves re-check learned skill, ability-active state, preset membership, or cooldown. These checks are largely client-runtime assumptions in classic code. A modern port should use authoritative server validation and atomic ammunition/resource consumption rather than porting those trust assumptions.

## Railgun: entity, render, hand-effect timing

Exact sources:

- `src/main/java/cn/academy/vanilla/electromaster/entity/EntityRailgunFX.java`
- `src/main/java/cn/academy/core/entity/EntityRayBase.java`
- `src/main/java/cn/academy/core/client/render/ray/{RendererRayComposite.java,RendererRayGlow.java,RendererRayCylinder.java}`
- `src/main/java/cn/academy/vanilla/electromaster/client/effect/{RailgunHandEffect.java,SubArcHandler.java,SubArc.java,ArcFactory.java}`
- `src/main/java/cn/academy/core/Resources.java`

### Beam lifecycle

`EntityRailgunFX` is a **client-only visual entity**. It starts from player true-mode motion, ignores frustum checks, and uses render pass1. `EntityRayBase.onFirstUpdate` schedules death after `life=50` ticks; animation curves use `GameTimer` elapsed milliseconds separately. At nominal20 TPS the visual lifetime is2500ms. The exact values:

| Property | Value | Meaning |
|---|---:|---|
| life |50 ticks|scheduled entity lifetime |
| blendInTime |150ms|beam length grows linearly from0 to supplied length |
| blendOutTime |1000ms|alpha fades during final1000ms |
| widthShrinkTime |800ms|base width shrinks during final800ms |
| widthWiggleRadius |.3|nonnegative width variation range |
| maxWiggleSpeed |.8|per-second random-walk maximum |
| inherited glowWiggleRadius |.1|glow variation range |
| inherited maxGlowWiggleSpeed |.4|glow random-walk maximum |

For elapsed milliseconds `t`:

- `renderLength = min(1,t/150) × suppliedLength`
- `alpha = 1` through1500ms, then `1−(t−1500)/1000` until2500ms
- `baseWidthFactor = 1` through1700ms, then `1−(t−1700)/800` until2500ms
- `widthFactor = baseWidthFactor + widthWiggle`
- Width random walk adds `deltaSeconds × random(−.8,.8)` per render tick and clamps to[0,.3]
- Glow random walk uses speed .4 and clamps to[0,.1]
- `getGlowAlpha = (.9+glowWiggle) × alpha`
- The glow renderer multiplies both `getAlpha()` and `getGlowAlpha()`, producing `(.9+glowWiggle) × alpha²` for its opacity; cylinder opacity uses alpha once

**800ms is a shrink duration, not a start delay.** The beam begins shrinking at1700ms. Arcs disappear earlier, at entity tick30 (nominal1500ms), coincident with the alpha fade's start.

The first visual update plays `academy:em.railgun` at volume .5/pitch1. The sound originates at the visual entity rather than a server sound call.

### Composite beam appearance

Render order is procedural sub-arcs first, then the composite glow and two cylinders. Classic uses:

- Glow material set `railgun`; width1.1; start offset−.3; end offset+.3
- Inner cylinder RGBA(241,240,222,200), width.09
- Outer cylinder RGBA(236,170,93,60), width.13
- Composite cylinder inner headFix .98
- Cylinder mesh has12 radial divisions; end-cap profile is a square-root curve discretized into4 segments
- Glow is billboard geometry with separate blend-in, tile, blend-out textures
- Beam and hand rendering skip shadow passes

Required glow assets:

`src/main/resources/assets/academy/textures/effects/railgun/blend_in.png`

`src/main/resources/assets/academy/textures/effects/railgun/tile.png`

`src/main/resources/assets/academy/textures/effects/railgun/blend_out.png`

Do not confuse these three actual composite textures with the unrelated flat legacy files `textures/effects/railgun.png` and `railgun_fade.png` also present in the repository. `Resources.getRayTextures("railgun")` resolves the three subdirectory textures.

### Procedural sub-arcs

The entity builds15 reusable arc templates with `ArcFactory` settings:

- widthShrink .9
- maxOffset .8
- passes3
- width .3
- branchFactor .7
- each template length random2–3

Arc origins start at axial distance1 and continue through supplied length with random1–2 axial spacing. Radial distance is random .1–.25 and polar angle random0–2π. Each offset is rotated by entity pitch and `(270−yaw)` before storage. The reflected visual constructs these offsets from the shooter's original orientation before changing its entity orientation, a possible classic reflection-arc alignment inconsistency.

`SubArcHandler` renders each arc at scale .3 with random Euler orientation, centered on its template length. It disables depth writes during arc drawing. Defaults `frameRate=1`, `switchRate=1`. Every tick, each `SubArc`:

- Has50% chance to switch template
- Has90% chance to increment its internal age; its nominal age limit is30
- If currently visible, has40% chance to become hidden
- If currently hidden, has30% chance to become visible

The entity explicitly clears **all** sub-arcs at tick30, so stochastic internal age is not the final Railgun arc expiry bound. The renderer also constructs a separate15-template array, but its `arcs` field is not used by Railgun's actual `drawAll` path.

### Charging hand effect

`RailgunHandEffect` resolves40 textures with `Resources.getEffectSeq("arc_burst",40)`, paths `academy:textures/effects/arc_burst/0.png` through`39.png`.

- Frame duration40ms, total1600ms; frame=floor(elapsed/40)
- At elapsed≥1600ms it disposes the render hook
- Mesh is a billboard with coordinates−1,−1 to1,1
- First-person translation(.26,−.12,−.24), scale(.4,.4,1)
- Third-person translation(0,.2,−1), rotation about X by negative **local client player's pitch**; it does not read the rendered target player's pitch
- Alpha test threshold0; standard source-alpha/one-minus-source-alpha blending; culling disabled while drawing; restore alpha threshold.1/culling afterward
- Uses `ShaderSimple`; no additional procedural arcs are created by the hand hook itself

The40ms texture animation is real-time rather than an integer game-tick animation. Rendering fidelity should therefore retain an elapsed-time frame progression, while input/cooldown/resource authority stays tick-based.

## Arc Generation constants for first playable implementation

Source: `src/main/scala/cn/academy/vanilla/electromaster/skill/ArcGen.scala`; helper `src/main/java/cn/academy/vanilla/electromaster/skill/EMDamageHelper.java`.

At pre-activation mastery `e`:

- Damage `lerp(5,9,e)`
- Range `lerp(6,15,e)`
- CP `lerp(30,70,e)`
- Overload `lerp(18,11,e)`
- Cooldown integer `lerp(15,5,currentEXP)` ticks; it is evaluated after the EXP addition, unlike Railgun's captured pre-shot EXP
- Entity-hit EXP `lerp(.0048,.0072,e)`; block-hit EXP `lerp(.0018,.0027,e)`; no ray hit gives **no EXP**, though resources/cooldown are spent
- Non-stationary-water block hit: chance `lerp(0,.6,e)` to ignite air directly above hit block
- Stationary-water block hit: cooked fish chance .1 only when EXP>.5, otherwise0
- The ray filter admits stationary/flowing water as well as normal blocks. Only exact stationary water matches the fish branch; flowing water uses the other block branch
- `canStunEnemy = e>=1` is declared but unused in this skill; no stun is implemented by `EMDamageHelper`
- `EMDamageHelper.attack` applies the ordinary skill attack and, on a creeper target, independently has30% chance to mark the creeper powered and trigger the associated achievement

## Classic quirks to distinguish from intended design

These are factual source observations and recommended boundaries for the port; they are not claims that all of the quirks should be reproduced:

1. Railgun shared `hitEntity` state is never reset and is not set by ordinary primary hits. Use per-shot hit accounting if correcting it
2. Railgun consumes ammunition before CP success, and relies on client-side permission/cooldown gating. Prefer authoritative validation and atomic commit
3. Beam visible length45 differs from damage-helper forward extent50; helper falloff is transverse rather than axial. Decide deliberately which semantics to preserve
4. Dependency/level/extra-cap arithmetic is exact enough to preserve without those trust or rendering bugs
5. `Skill.getOptionalFloat(path,fallback)` checks the requested path but then always reads `damage_scale`. A non-damage multiplier override may therefore read the wrong value or fail if `damage_scale` is absent
6. `Skill.getExpIncrSpeed()` requests `overload_incr_speed`, whereas default-config documentation/examples use `exp_incr_speed`. With untouched defaults the fallback remains1, but custom EXP configuration is not reliable
7. `CooldownData.toID` is `ctrl.getControlID() << 2 + id`; Java precedence interprets it as shifting by `(2+id)`, rather than packing `(controlID<<2)+id`
8. `CPData.setCP` clamps against `maxCP` base, not `getMaxCP()` total; direct setters can discard trained allowance
9. `recoverAll()` sets `overloadFine=false` until a later eligible recovery tick, despite zero overload
10. `AbilityData.setCategory` clears learned/mastery data but does not explicitly reset accrued level progress and preserves positive level when switching directly between non-null categories
11. Several sustained skills add EXP before checking failed resource consumption; their manifest strings describe the source branch, not an assurance of all-or-nothing transactions
12. Teleporter helper direct EXP additions can learn passive skills through `AbilityData.addSkillExp`, bypassing developer learning checks; ordinary developer learning should continue to honor the manifest

## Suggested verification assertions

- Four string category IDs;50 manifest entries; EM12, MD14, TP12, VM12 total including courses
- Original Railgun skill numeric index7, level4, thunder_bolt≥.3 and mag_manip≥1
- EM/VM controllable per-level counts[2,2,2,2,1], MD[1,2,2,3,2], TP[1,2,2,1,1]
- Fully mastered skill can still contribute requested EXP to player-level progress
- Level4 progress denominator uses1.333×count and all other levels use.666×count
- Iron charge requires20 held ticks; early key-up cancels; only one shot per held-key activation
- Coin firing uses strict>.7 even though HUD becomes ACTIVE at≥.6
- At e0/e1 Railgun uses exact CP200/450, overload180/120, starting damage60/110, energy900/2000, cooldown300/160
- CP depletion delays recovery15 ticks; overload delays32 ticks; overload lock releases only when overload reaches0
- Railgun render length reaches full size at150ms, arc clear tick30, fade begins1500ms, width shrink begins1700ms, death50 ticks
- Hand frame0 at t0, frame39 at t1560–1599ms, dispose at≥1600ms
- Skills, developer tiers, dependencies and actual dispatch coverage are distinct: registering a skill manifest alone does not implement its gameplay behavior
