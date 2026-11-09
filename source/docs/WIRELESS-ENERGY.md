# Frozen source-faithful wireless increment

Canonical AcademyCraft1.0.7 / LambdaLib1.2.3 → MC1.21.1 NeoForge21.1.252. All changes stay under `.staging/wireless-energy`; no production mutation or Gradle/game/client launch was performed.

## Promotion

1. Copy top-level `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources` preserving paths. These trees ALREADY contain the frozen graph/client contributions. Do not separately copy nested `graph-stage`/`client-stage`; do not copy `.javac`
2. Apply `docs/wireless-integration.patch` using `patch -p1`. It cleanly dry-runs against current parent-integrated fusion/solar/magnetic/portable production. It adds ONE registration call, SIX test tasks, three UI hooks, solar/Fusor menu position/context accessors, sender-bound machine-session bootstrap/current-node readout, four locale dictionaries and exact harvest tags. Existing portable/fusion tasks remain intact. Never overwrite whole shared files with `integration` compile overlays
3. Top-level `docs/handoff-manifest.json` records promotion paths/sha256. `integration-base-sha256.json` records patch bases. Preserve existing JAR exclusion `cn/academy/port/gametest/**`
4. Parent owns aggregate build/check, seven native GameTests, native world/disk restart and physical client QA. Cached javac compiled all new classes, all integration Java overlays and the opt-in scene

## Implemented outcome

Real three-tier nodes (capacity/bandwidth/range/shared-user capacity): Basic15000/150/9/5, Standard50000/300/12/10, Advanced200000/900/19/20. Native source battery input/output loops use two actual native IF slots, input first, real item bandwidth and finite storage. Nodes are source-functional without a matrix: connect a generator and receiver to one node to move actual stored/generated IF. Normal/Advanced developer slots receive no invented battery drain loop.

Eight-cell2×2×2 source matrix, one constraint plate in each of3slots plus an actual source matrix_core_0/1/2. Working capacity8L, bandwidth60L², range24√L. Atomic source-cell placement/teardown; origin-only control and finite balancing buffer2000IF. All real graph state persists per dimension in native SavedData, with loaded-only capability resolution and no force-loading.

Native connection/inventory UI with original assets, exact source node/matrix slots, owner name/pass/SSID/init/reset, nearby targets, password link and unlink, real device return navigation. Solar/Fusor wireless buttons and Normal/Advanced Current Node region open sender/current-context validated pages. Current menus use fresh nonce+menuId, actual source identity/reach/dimension and persisted UUID ownership. Fictional game-world passwords never appear in public/client snapshots; masked edit fields retain local typing. They do not touch actual OS networking/security/credentials.

Original43assets verified byte-identical; native30node state variants; exact matrix Main212/Core4/Shield22 groups with three source rotating/floating shields, original cardinal pivots/angles and no rescaling. Runtime world mesh/shields/GUI pixel parity still requires parent captures. Explicit packet handling updates client display fields without loading/clearing inventory or exposing private passwords.

Five exact source recipes added: node0/1/2 ordinals14/15/16, matrix27 and now-genuinely-supported Advanced developer46. Modern iron/redstone tags preserve source ore-dictionary compatibility; no substitution recipe, fake energy source or duplicate matrix-core item. Core/crystal-tier/matter/Fusor acquisition uses the now-integrated actual fusion module.

## Authentic availability and proof boundary

Basic-node ingredients all terminate at existing vanilla resources and source low-crystal/resonance ore/frame/chip recipes. Standard/pure-node upgrades require genuine Fusor normal/pure crystals. Matrix is craftable from source frame/redstone/chips/resonance crystals; its inserted low core is genuinely craftable without upgraded crystal tiers. Normal's genuine recipe requires a Fusor-produced Normal crystal and basic matrix core. Advanced's exact source recipe requires a Normal developer, Standard node and Pure crystal, now supplied by actual functional dependencies.

Compiled native progression fixture `AcademyWirelessDeviceRuntimeTests.native_crafted_solar_basic_relay_fusion_normal_and_earned_arc` declares supplied BASE materials/vanilla bits/three phase cells, then uses actual native recipe outputs and BlockItem placement for initially-empty solar/basic node/Fusor. Actual solar ticks charge Fusor through the real graph; real matter-item use collects source phase cells; real Fusor ticks consume3000mB/1452IF to produce a Normal crystal. The genuine Normal recipe consumes that output and creates/places an EMPTY Normal. Actual solar→node delivery generates5880IF, and authenticated real Normal session acquisition105ticks plus Arc learning63ticks consume exactly3675+2205IF, ending0IF with earned category/Arc. No energy/category/mastery setter occurs in this chain.

This establishes a reproducible functional recipe/power/progression path when native execution passes, not a mined human playthrough. Supplied fixture inputs are openly declared. The original fully mined base-material→solar/portable route and genuine phase-generation/collection path have separate parent-run evidence; do not infer new native success from cached compilation.

## Opt-in physical single-player scene

Development-only `AcademyWirelessScene`, excluded from shipped JAR. Enable JVM property `-Dacademy.wireless.qa=true` in a disposable client/test world; existing normal gameplay has no command or automatic setup.

- `/academy_wireless_scene setup`: requires operator permission2 and an empty loaded open-sky region. It adds a supplied stone platform, controls clear daytime, places real recipe-derived EMPTY solar/basic node/Fusor/matrix/Normal, seeds3phase cells and gives declared base inputs/real empty containers/core. A Normal crystal is SUPPLIED in this physical-UI fixture, unlike the actual native progression fixture. Existing earned abilities and held portable/payload are preserved. No battery/tank/category/mastery setter. It never clears existing blocks; blocked setup aborts
- `/academy_wireless_scene view solar|node|fusor|matrix|normal|phase_1|phase_2|phase_3|overview`: fixed server teleport/look to remove mouse-look calibration. Phase views aim straight down at each source cell
- `/academy_wireless_scene normal`: additionally crafts an EMPTY Normal using an ACTUALLY PRODUCED Normal crystal in this scene's Fusor output; other base ingredients remain declared supplies. It cannot fabricate the crystal

For physical progression, link solar and Fusor to Basic node via their actual pages; allow Fusor buffer to charge before inserting low crystal because one solar3IF/t cannot continuously cover Fusor12IF/t without saved buffer. Collect phase with real matter units and insert filled containers. Connect the supplied empty Normal via Current Node for UI/power testing, or obtain the second Normal through actual Fusor output. Matrix test: insert3plates+core, INIT a fictional SSID/password, then link node in its wireless page; this changes original top/animation/shields. Scenes contain no automatic links; physical clicks exercise source authentication and setup.

## Intentional source bug/security fixes

- Original WirelessNet used same-sign buffer/node deltas, creating/destroying IF. Adapter uses opposite-sign conserved transfer and includes real buffer in target pool to prevent permanent stranded IF; no new IF appears
- Original deferred relink cleanup could delete a newly reassigned lookup. Modern atomic unique ownership makes repeat links idempotent
- Original VBlocks could force-load chunks. Unloaded links now remain persisted/dormant, with no offline power or loaded-chunk side effects; invalid fully loaded actors are pruned
- Legacy client proxies accepted spoofable player arguments and broadcast plaintext fictional passwords. Real authenticated sender/current menu/nonce/UUID owner and nonsecret snapshots are intentional security corrections
- Matrix/receiver source bandwidth and shared user limits remain; node input and output get SEPARATE full budgets each tick, as source code specifies. Matrix balancing runs before local node connections

Exact source citations and detailed graph adaptations: `graph-stage/docs/SOURCE_CITATIONS.md` and `GRAPH_INTEGRATION.md`. Original UI/model/transform citations: `client-stage/WIRELESS_VISUAL_SOURCE.md`. This documentation is engineering evidence, not a claim of full-mod completion.

## Verification / remaining work

Top-level verification script passed64,494 assertions:60,300graph +246device/recipe +226asset/model +16actualNBT +3,706visual geometry/state.39common/integration classes cold-linked with real client namespaces denied (exact engine-common IMenuProviderExtension exception). Actual compressed-NBT codec write/read across two ordinary fresh Java JVMs passed; this is NOT a Minecraft world restart. All7native fixtures and opt-in scene compiled, NEVER launched here. Parent runtime remains mandatory.

Remaining original scope: other wind/phase generators, frequency-transmitter/terminal app workflows, tutorial/achievement parity, world/client pixel/audio parity and full50skills. Native Current Charging block-manager priority for newly added nodes/receiver-only Fusor is still an identified source-support gap (existing FE compatibility remains); it is unrelated to the genuine solar graph route above. Do not claim complete source energy catalog or full survival/human parity from this increment.
