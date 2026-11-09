# Classic passive and generic skill stage

This is a frozen private proposal. Nothing here has been promoted to production. The parent owns Gradle, native server/client execution, recordings and promotion.

## Inventory and source audit

The classic registry contains 50 category entries, not 50 active actions: 35 controllable active skills, 3 category-specific passive skills, and 12 generic instances (3 courses copied independently into each of the 4 categories). There are 41 distinct skill IDs. This stage does not claim the whole mod port is complete.

The 6 distinct passive/generic IDs were already in SkillAvailability.PASSIVES. All 15 source GUI nodes, positions, icons, development tiers, parents, mastery prerequisites and generic any-learned-level conditions were already present. No new action is added to PresetSkills.IMPLEMENTED, and none of the passives can become a preset action.

| Skill | Category instances | Level / minimum developer | Source prerequisite | GUI x,y | Learned effect |
|---|---:|---|---|---|---|
| rad_intensify | Meltdowner | 1 / Portable | electron_bomb >= .5f | 35,75 | customized mastery from current maximum CP / calculated level-5 initial CP; 1.4f–1.8f radiation damage multiplier |
| dim_folding_theorem | Teleporter | 1 / Portable | threatening_teleport >= .2f | 50,75 | first sequential critical probability .1f–.2f |
| space_fluct | Teleporter | 4 / Advanced | learned shift_tp, zero mastery allowed | 160,80 | adds .18f–.25f first-tier probability and enables .10f–.15f / .01f–.03f later rolls |
| brain_course | All 4 | 3 / Normal | any learned skill of level 3 | 30,110 | +1000 calculated raw maximum CP |
| brain_course_advanced | All 4 | 4 / Advanced | learned brain_course, any learned skill of level 4 | 115,110 | +1500 CP and +100 overload |
| mind_course | All 4 | 5 / Advanced | learned brain_course_advanced, any learned skill of level 5 | 205,110 | multiplies CP recovery coefficient by 1.2f |

## Corrections in this stage

- Restore common mutable, noncancelable MaxCP, MaxOverload, CPRecoverSpeed and OverloadRecoverSpeed events. Generic course listeners use actual NeoForge NORMAL priority, so higher/lower priority extensions see source ordering
- Cache calculated raw maxima as CPData does, recalculate on learning and category/level changes, keep training separately, and preserve calculated maxima across cold NBT using schema 3. Older schema 2 saves still decode. Transient callbacks are not persisted
- Restore source float radiation mastery and rate, float target mark storage, and the source 20-block client sync. Preserve the original quirk: the server marks the attacked target; the client sync marks the caster and emits that caster's radiation particles
- Restore source float critical probabilities, strict comparisons, multipliers, cumulative passive mastery and full level-progress awards after saturation. A successful critical hit still auto-learns the other passive, including level-4 Space Fluctuation, without consulting the developer tree
- Emit ThreateningTeleport's missing common critical event. Retain existing FleshRipping/ShiftTeleport event paths and source achievement marker adaptation; critical client events are delivered to the caster, matching TPSkillHelper.sendTo(player)
- Apply source float prerequisite comparison only to the 15 passive/generic learning entries. Keep existing active skill algorithms and learning comparisons intact
- Retain malformed-data guards and bounded packet/client particle limits

Existing active skill damage/cost/hold/tracing algorithms and CP consumption pipeline are retained. Learned passives use the source float calculations. The previously measured general double-ledger versus classic float-ledger rounding difference remains outside this stage; this is not a claim of converting every active algorithm or the entire ledger.

## Evidence

24 unchanged AcademyCraft/LambdaLib source witnesses are bundled, including the 6 passive/generic definitions, category registrations, ModuleVanilla, CPData, AbilityData, MDDamageHelper, TPSkillHelper, CalcEvent and the particle/math sources. The oracle compiles the actual unchanged generic Java classes with a minimal old API fixture, and executes extracted unchanged Java critical/recovery/math methods. Radiation's Scala expressions are checked against the unchanged witness and evaluated independently with float arithmetic. The oracle does not call port helpers to calculate expected results.

Seven existing runtime assets (six icons and the radiation particle texture) are SHA-256 checked against original source bytes. No new media has been downloaded or substituted.

The cached official JDK 21 / Minecraft 1.21.1 NeoForge 21.1.252 classpath compiles the additive files, all shared proposals, existing affected regression mains, and the six native fixtures. The focused source differential contains 250,040 assertions across 25,000 seeded scenarios. Learning tests exercise all 15 entries across Portable/Normal/Advanced with actual finite IF costs and completion gates.

The six native fixtures are compiled only, not executed: actual portable/normal/advanced learning ingress, ordered native course calculations, native radiation damage and rejected-hit marking, and native ThreateningTeleport critical events/auto-learning. Native execution, live visual QA, aggregate Gradle checks and restart-process proof remain for the parent.

## Promotion contract

Use docs/common-base-manifest.json and docs/integration.patch. Apply only the exact narrow hunks after checking every current shared-file SHA-256; integration/src is a compile shadow, not a replacement instruction. Add only the files listed in docs/promotion-manifest.json. Never copy audit class files, original-source compiler fixtures into production main sources, or stage scratch scripts over production files.

Run scripts/check_manifest.py before promotion. It checks immutable staged artifacts, current base hashes and a fresh patch replay. If the parent legitimately changes a shared file, reconcile those specific hunks rather than blindly replacing the file. The 18 existing shared files have before/after copies and SHA-256 values.
