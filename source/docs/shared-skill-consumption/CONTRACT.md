# Shared consumption and configuration contract

Canonical baseline: AcademyCraft tag1.0.7, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`; LambdaLib1.2.3. Exact original bytes, hashes, README licensing notice, GPLv3 text and LambdaLib MIT license are packaged beside these tests. The supplied reference tree has no Git metadata; the commit is the project's pinned reference contract, not a new independently resolved Git object.

## Source calculation order

`AbilityContext.java:93–105,145–150` distinguishes three calls:

- `canConsumeCP(rawCP)` asks `creative || currentCP >= rawCP`; it does not scale or emit an event
- `consume(overload, CP)` applies per-skill float coefficients first, then calls `CPData.perform`
- `consumeWithForce(overload, CP)` bypasses those coefficients, then calls `CPData.performWithForce`

`CPData.java:291–321` posts `CalcEvent.SkillPerform` synchronously before the insufficient-CP check and creative branch. Public mutable float CP and overload fields determine debit and training. Constructor order is overload, CP; the classic event has only player metadata and is noncancelable. A listener exception propagates. There are no built-in SkillPerform mutation subscribers in the pinned AcademyCraft/LambdaLib trees.

Successful noncreative order is: event → CP debit/floor and CP recovery delay → overload clamp to the current pre-training cap and overload delay → OverloadEvent if equal to that cap → overloadFine=false after the callback → additional maxCP training → additional maxOverload training. Overload listeners see the old fine flag and no capacity training yet. An already-full cap may emit another event with zero overload. Force floors actual CP at zero but trains the complete event-result cost. Creative posts the same calculation event, changes no resources or delays, and still trains both capacities. The actual player creative flag is read after the cost event, so a listener may change which branch runs; diagnostic metadata records the initial flag. Normal failure leaves resources, delays and training unchanged after the calculation event.

Training defaults are CP cost×.0025, overload cost×.0058 clamped per call to[0,10], then capped by the player's level-specific additional capacity. Source values and arithmetic are floats. Source does not reject negative/NaN/infinite amounts, has no transaction rollback or reentry guard, and does not check activation, category, overload lock, interference or learned state inside CPData.perform.

`OverloadEvent` documents server-only use, but its posting code does not check side; CPData permits client simulation. The modern event transport runs on ServerPlayer/server thread. Direct common-ledger hooks can still be used for independent simulation. This lane adds no client cost prediction.

Forge's external priority/tie implementation is not contained in the two snapshots. The mapped NeoForge bus was tested directly for synchronous highest-to-lowest mutation and event identity; equal-priority third-party listener ordering is left to that bus.

## Source configuration quirks

There is no global CP/overload action-cost multiplier. `calc_global.damage_scale` affects damage only. Global `data` controls recovery rates/delays, capacity bases/caps/training, and level progress. Category `common.prog_incr_rate` multiplies awarded progress after the global per-skill EXP stage.

`Skill.java:199–214` contains intentional compatibility bugs:

- Missing requested optional float key returns1
- If `cp_consume_speed` or `overload_consume_speed` exists, its numeric value is ignored and `damage_scale` is read
- A present requested key without `damage_scale` in the resolved config throws
- EXP checks `overload_incr_speed`; documented `exp_incr_speed` is ignored
- That EXP getter also reads `damage_scale`

Arc Gen's canonical default config contains CP/overload optional keys and damage_scale1. Those presence defaults are inherited even when a user overrides only Arc Gen damage_scale. Other skills' empty default blocks do not gain optional-key presence merely because a configurable list exists.

NeoForge config exposes `classicData.<source_key>`, category progress under `classicCategoryProgress.<category>`, and optional numeric entries under `classicSkillOverrides`, e.g. `teleporter.flashing.damage_scale=2`, `teleporter.flashing.cp_consume_speed=99`. This makes normal Flash CP double because the source reads damage_scale; force and raw affordability remain unchanged. Changing only a cost key without its source-required damage_scale retains the source lookup failure. Existing damageScale is the global damage setting; configured attacks use the source global×skill×raw float order. Default attack arithmetic stays compatible with the existing double-backed port.

## Shared modern API

`SkillConsumption` has no Minecraft, bus, transport or client dependency. Immutable config snapshots clone arrays/maps. A mutable Request carries float costs, plus diagnostic skill/force/creative metadata. `AbilityProgress` supplies:

- `consumeSkill(skill, CP, overload, creative)` for normal source-scaled consumption
- `consumeWithForceSkill(skill, CP, overload, creative)` for raw force consumption
- `canPerform(rawCP, creative)` for the nonemitting, nonscaling source precheck
- Optional per-operation liveness/protection predicates evaluated after SkillPerform and before debit

The old `consume(CP, overload, creative)` is retained as a generic raw CPData-style entry. Every mapped production/frozen skill ingress supplies an explicit ID. The compatibility force helper overload remains available for existing pure callers, while all real teleporter force callers supply their ID.

AbilityStorage installs transient server hooks on retrieval, never persists hooks, and uses configured caps/delays when decoding server saves. SkillPerformEvent and AbilityOverloadEvent deliberately do not implement ICancellableEvent. AbilityConsumption carries costs through NeoForge without importing client classes.

Direct common perform reentry preserves source nested-before-outer ordering. During synchronous consumption, native start/wire admission is fenced; terminal operations and persistent vector damage paths have focused liveness checks. This prevents recursively installed constructor Holds, double JetEngine release, recursive vector incoming damage, stale vector entity changes, emptied ElectronMissile ball access, stale teleport companions, and stale placement/item use. A separate JetEngine release reservation avoids treating a pending debit as an already-started flight or changing gravity during callback abort.

Teleporter critical passives use direct AbilityData.addSkillExp in TPSkillHelper, so their raw awards bypass per-skill EXP coefficients while retaining category/global progress. The port helper uses addExperienceRaw for this mapped distinction. RadiationIntensify displayed mastery reads the configured level-five initial CP denominator.
