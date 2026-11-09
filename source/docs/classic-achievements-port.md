# Classic achievement restoration: source stage

Target: AcademyCraft 1.0.7 commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, LambdaLib 1.2.3, Minecraft 1.21.1 / NeoForge 21.1.252.

This restores all 56 source definitions and their runtime award consumers. It uses ten actual crafted-result conditions, one low-crystal pickup condition, one phase-matter harvest condition, twenty exact-level change conditions, five first-learning conditions, and nineteen manual skill success points. Backing advancement JSON is silent persistence; `minecraft:impossible` alone cannot earn anything. Runtime server events and success-point hooks supply every award, with the original immediate-parent gate and idempotent completion. An event that arrives before its parent is lost, not queued or reconstructed from inventory, mastery, old compatibility markers or a later login.

The original pages are registered in this order:

| Page | Source entries | Source positions and icons |
| --- | ---: | --- |
| AcademyCraft | 12 | Exact `PageDefault` declarations, including the wind fan display/main craft distinction |
| Electromaster | 13 | Exact five-level inner chain and eight outer entries |
| Meltdowner | 11 | Exact five-level inner chain and six outer entries; mine-ray basic learning uses the luck-ray icon |
| Teleporter | 10 | Exact five-level inner chain and five outer entries |
| Vector Manip | 10 | Exact five-level inner chain and five outer entries |

`ClassicAchievementCatalog` and `classic-achievements.json` contain the complete source order, IDs, titles, descriptions, grid coordinates, immediate parents, icons, event kind, condition key and exact level. The standalone source oracle compiles the unchanged original page constructors, achievement subclasses, `DispatcherAch` and `CondItemCrafted`; it compares all 56 registrations and their actual reflected condition fields, rather than deriving expectations from the modern JSON.

## Actual award boundaries

Default entries observe actual NeoForge `PlayerEvent.ItemCraftedEvent` from crafting ResultSlot, `ItemEntityPickupEvent.Post`, and the existing real `MatterUnitHarvestEvent`. Smelting, inventory possession, recipe previews, creative acquisition and machine extraction do not substitute for source craft/pickup events. Matrix-core metadata was one source item with a wildcard condition; the three registered modern core variants are deliberately mapped back to that family. Wind generation awards on crafting the main, while displaying the fan. Capturing phase liquid awards; releasing an empty material does not.

`AbilityProgress` emits transient first-learn and changed-level callbacks. `AbilityStorage` binds them to common NeoForge `SkillLearnEvent`/`LevelChangeEvent`, and the achievement consumer matches the source category, skill and exact new level. Restoring a saved snapshot does not fire events. Existing portable and machine development sessions reach those real mutations, and ordered passive auto-learning uses the same first-learn path. The five learning awards are radiation intensify, light shield, meltdowner, basic mine ray and electron missile. They are not granted merely for using those abilities. Learning a child before its achievement parent remains a source miss; the adapter does not defer it.

The manual ingress follows these source conditions:

| Source achievement | Actual ingress |
| --- | --- |
| electromaster.arc_gen | Cooked-fish spawn branch of a successful water arc, with the existing captured mastery/chance gate; ordinary hits/casts do not count |
| electromaster.attack_creeper | The actual 30% powered-creeper roll after ArcGen/ThunderBolt's damage attempt, including rejected hurt |
| electromaster.mag_movement | Genuine server context termination, including source invalid-initial-target termination |
| electromaster.body_intensify | Successful charge release after applying source buffs/hunger, before its experience award |
| electromaster.mine_detect | Accepted CP-consumed cast after blindness and experience, before local effect/cooldown; original parent required for new awards |
| electromaster.thunder_bolt | Accepted cast after experience and before cooldown, including a miss |
| electromaster.railgun | Actual accepted damage operation before experience/cooldown, including an empty beam |
| electromaster.thunder_clap | Actual discharge after lightning/damage/cooldown/experience |
| meltdowner.jet_engine | Successful mark release after its source debit/trigger setup |
| teleporter.threatening_teleport | Traced entity attack, including rejected damage; an item teleport miss does not count |
| teleporter.critical_attack | Actual rolled critical before passive auto-learning and attack; an early critical before the root is lost |
| teleporter.ignore_barrier | Successful Penetrate/Location operation; Mark and Flashing do not count |
| teleporter.flashing | Actual successful directional hop, not entering the mode |
| teleporter.mastery | Every genuine Mark/Penetrate/Location/Flashing count increment at count >=400; repeated attempts can succeed after a previously missing parent |
| vecmanip.ground_shock | Successful source release/terrain wave after experience/cooldown |
| vecmanip.dir_blast | Successful source release after its experience award |
| vecmanip.storm_wing | Existing ACTIVE context tick before its next consumption; an insufficient-CP tick can still award |
| vecmanip.blood_retro | Successful entity attempt after experience, only when the resulting source float mastery equals 1f |
| vecmanip.vec_reflection | Encountering an eligible, unmarked `Affected` entity before per-entity consumption; successful reflection is not required |

The current genuine ingress for every listed source condition exists in the promoted main modules. None of the 56 achievements depends on the still-absent Interferer, Cat Engine, RF bridge or Magnetic Hook. This source-stage statement is not a claim that every route has been naturally earned in a live singleplayer session.

## Required modern adaptations

1. The original initial `DevelopActionLevel` calls `AbilityData.setCategory`, directly sets level 1, and posts only `CategoryChangeEvent`. `DispatcherAch` listens only to `LevelChangeEvent`; this leaves the four level-1 roots naturally unreachable in that source flow. The existing modern genuine development flow already does `selectCategory` followed by `setLevel(1)`. Its completed 0-to-1 mutation now emits the exact level event, making the four chains naturally obtainable. There is no retrospective level scan or automatic parent award. This is an explicit obtainability repair, approved for this port.
2. The source used removed Minecraft Achievement/Forge AchievementPage APIs. Vanilla UUID-backed advancement storage now persists the achievements. All 56 backing definitions are displayless; the original bitmap page/toast owns presentation, so there are no duplicate vanilla tabs or paper substitute icons.
3. Existing `academy:electromaster/mine_detect` / criterion `cast` completions are retained. Existing completed saves are not revoked when the original parent is added. New casts respect the source parent. No ancestor achievements are synthesized.
4. The original pages inherited the vanilla 1.7.10 cave background and normal achievement frame; AcademyCraft supplies no custom page background. A dedicated client screen reachable through the AcademyCraft button in vanilla Advancements preserves the five-page cycle, exact 24px grid positions, 22px frame size, 16px original inventory icons, translated titles/descriptions, parent connections, locked/earned display and tooltips. Its reconstructed frame and cave motif reference the installed 1.21.1 block textures. These host-version visuals and the added entry button/zoom/navigation are explicit adaptations, not pixel-equivalence evidence for the old Mojang GUI atlas.
5. Source `ItemAchievement`'s mutable metadata-index texture list is replaced by trusted catalog texture paths rendered directly. The missing dummy/logo registry items are not invented to close inventory counts. No reward item or recipe is added.
6. Modern accepted-world/target/finite-value/thread/session gates are retained. Existing teleport-counter saturation is retained and made consistent across count helpers. Counter/compatibility facts are copied on player clone; advancement awards use vanilla persistence and UUID lifecycle. Saved facts never generate achievements.

## Verification and acceptance qualifications

`scripts/verify-stage.py` assembles a fresh root from current main plus guarded shared hunks and stage files, without `.reference` or `.staging` in that root. It uses cached JDK 21 and the official merged NeoForge jar, compiles the full assembled current source tree, compiles the unchanged original achievement sources independently with boundary-only stand-ins, and runs two finite standalone Java checks. The tests compare every original registration/condition/parent/layout, original bitmap bytes, source translations in all four production locales, matrix-core and wind distinctions, lost-before-parent/idempotent rules, mutation event boundaries and portable resources. A fresh common classloader actively denies all client/render namespaces while loading the common achievement module, progression and method signatures.

Nine compiled native fixtures cover the 56 actual registered backing holders/parents, repeat gates and vanilla save/reload; genuine portable development completion and passive first-learning with explicit skill-prerequisite setup; actual terminal ResultSlot consumption; native MineDetect cast/parent/replay conditions; legacy accepted MineDetect migration; authenticated Arc water/fish vs ordinary cast; native entity-hit ThreateningTeleport with protected damage and an unqueued first critical; clone counter/marker handling; and actual insufficient-CP reflection eligibility before its debit. Their fixture grants/mastery/ingredients are explicitly setup, not a claim of naturally earning every prerequisite. They are compiled but not executed in this isolated worker.

No Gradle, native GameTest server, normal server, client, GUI, framebuffer capture or CUA process was run here. The source visual oracle verifies definitions/assets, not final rendered pixels. The owner's serial lane must promote the guarded payload, run the nine native fixtures, and check all five live pages, original icon textures/toasts, navigation/reopen/resize, initial natural level acquisition, fish/critical source ordering, and restart/death persistence before claiming client or end-to-end singleplayer acceptance.

Archived unchanged AcademyCraft/LambdaLib witnesses, exact referenced assets and licensing live under `src/test/resources/classic-achievements`; the verifier needs only these archived files and the assembled root. `META-INF/ACADEMY-ACHIEVEMENTS-NOTICE.txt` explains provenance and rendering adaptation. See the stage `PROMOTION-MANIFEST.json`, `audit/shared-hunks.json`, `audit/verification.json`, and javac/oracle/test logs for the exact frozen inputs and qualifications.
