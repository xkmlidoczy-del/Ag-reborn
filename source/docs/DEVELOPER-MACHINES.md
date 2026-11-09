# AcademyCraft 1.0.7 normal and advanced developers

## Handoff and serial integration

This task touched only `.staging/developer-machines`. Production files/build.gradle, Gradle, native server/client execution and user computers were not operated by this worker. The production owner must integrate and perform serial aggregate/native/client QA.

1. Copy new staged `src/main/java` and `src/main/resources` files into matching production paths. These are new classes/resources, including eight native tests under `gametest`, not replacements for owner files.
2. Apply/review `docs/developer-machines-integration.patch`. It contains narrow changes to DevelopmentController, AcademyNetwork, DeveloperItem, DeveloperScreen and AcademyClient, plus native mining tags and localized names. The full copies in `integration/` are snapshots for API compilation/reference, not instructions to overwrite newer owner work. Preserve any intervening changes. In particular, shared mining tags retain the survival worker's existing values.
3. In the existing AcademyCraft constructor, add `cn.academy.port.machine.MachineDevelopers.register(bus);` alongside the other registration-owner calls, before the event bus is finished registering.
4. Add JavaExec/check tasks for `cn.academy.port.machine.MachineDeveloperRegressionTest`, `cn.academy.port.DeveloperModelRegressionTest`, and `cn.academy.port.client.DeveloperModelEmissionRegressionTest`. The last uses actual renderer buffers and cached game dependencies but no client/OpenGL context. The optional data check is `python3 .staging/developer-machines/scripts/verify-machine-data.py`.
5. Existing `AcademyRuntimeTests` installs `academy:runtime_empty` in memory. All eight `AcademyDeveloperMachineRuntimeTests` use that template in a separate `academy_developer_machines` batch. The eight-cell structure fits within its 8×6×56 footprint. Keep native test class excluded from distributed jar with the existing gametest exclusion.
6. Run all original/merged regression tasks and native GameTests serially, then real client QA of models, cardinal alignment, collisions, item icons, finite charging, developer open/close/reset, resource reload, destruction and persistence. Compilation/headless checks do not establish those runtime results.

The client renderer hooks are included in the patch and detailed in `../RENDERER-INTEGRATION.md`. They run only in the existing Dist.CLIENT mod-bus subscriber, not the common registration owner.

## Exact source geometry, placement and removal

Canonical local references are AcademyCraft 1.0.7 BlockDeveloper, TileDeveloper, DeveloperType, IDeveloper, TileReceiverBase, TileInventory and DeveloperUI/Common.TreeScreen in SkillTree.scala, plus LambdaLib 1.2.3 BlockMulti, ItemBlockMulti, InfoBlockMulti and RenderBlockMulti/Model.

Both machine tiers occupy exactly these eight unrotated NORTH-oriented cells, in the original part order:

0=(0,0,0), 1=(0,1,0), 2=(0,0,1), 3=(0,1,1), 4=(0,2,1), 5=(0,0,2), 6=(0,1,2), 7=(0,2,2)

The top-front cell (0,2,0) is deliberately absent. Each occupied cell retains the source default full-cube collision. Models are separate actual OBJ geometry, not collision-derived cubes. Yaw uses source floor(yaw*4/360+0.5)&3 mapping NORTH,EAST,SOUTH,WEST. Rotation is source NORTH(x,y,z), EAST(-z,y,x), SOUTH(-x,y,-z), WEST(z,y,-x). Facing and part are persisted natively in each BlockState; origin derives from the exact inverse offset and cannot be chosen by a client payload.

Placement validates every cell's replaceability, build height, world border, loaded chunk, player edit permission and entity collision before any write. It places all eight cells transactionally. Failed writes restore every touched state and saved block-entity NBT before returning failure; standard BlockItem consumes no machine on failure and one machine on success. This tightens source ItemBlockMulti's limited replacement/entity checks to modern protections. It does not replace obstructing blocks or force-load remote chunks. Vanilla modern creative consumption, placement sound and advancement handling are retained.

Removing any matching part tears down the whole matching structure once, drops the origin's two-slot inventory once, releases its transient GUI occupancy, and invalidates retained energy access. Vanilla's initial destroyed cell is the sole full machine-item drop; the seven cleanup cells suppress drops. Creative/direct replace operations retain native drop behavior. Identity checks prevent destroying an unrelated replacement. Both tiers use source hardness4 and pickaxe harvest2, represented by needs_iron_tool/mineable/pickaxe tags. Pistons cannot move individual cells.

Consistency checks never force-load missing neighbor chunks. Loaded orphan parts remove themselves; a loaded origin whose loaded eight-cell footprint is inconsistent removes itself. Unlike regular mining, a silent direct replacement/corruption does not create a bonus machine-item refund. An unloaded neighbor by itself is not treated as corruption.

## Exact source finite energy and development

| Tier | Capacity IF | Bandwidth IF | GUI syncRate | Declared TPS | CPS | Actual ticks/stimulation | Actual IF/stimulation |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| NORMAL | 50000 | 100 | .7 | 20 | 700 | 21 | 735 |
| ADVANCED | 200000 | 300 | 1 | 15 | 600 | 16 | 640 |

Source DevelopData drains CPS/TPS per tick and completes after ++tick>TPS, so TPS+1 and actual consumption are preserved. Existing DevelopmentProcess was reused without modifying portable behavior. Skill tiers remain portable for levels1–2, normal for level3, advanced for level4–5; category acquisition/level advancement has no minimum developer tier. Advanced reset retains real magnetic-coil/induction-factor requirements, level>=3, source level*10 stimulations, loss of one level, and clearing entire factor/held-coil slots on completion.

Source TileDeveloper.tryPullEnergy is an atomic precheck: insufficient energy leaves the remaining IF intact. PortableDevData drains its remainder when underpowered. The two behaviors remain distinct. New machines begin empty. No implicit refill, creative free energy, CP-to-IF battery, infinite battery, generator, development-specific shortcut or machine crafting substitution was added.

Origin energy is finite double-valued IF and persists as NBT energy; invalid/nonfinite/negative/over-capacity data is sanitized. The two source inventory slots persist natively. User, nonce and in-progress development are intentionally transient, matching source TileDeveloper's unsaved user and DevelopData's lack of NBT storage. Breaking a machine does not return its battery charge in the block item, matching the source uncharged ItemBlockMulti drop.

`MachineDevelopers.IMAG_FLUX` is a sided native `ImagFluxReceiver` block capability. Every part resolves to the same loaded valid origin receiver. Source injectEnergy returns the unaccepted amount, pullEnergy returns actual removed energy, and getRequiredEnergy/getBandwidth expose source arithmetic. Like TileReceiverBase, direct IF injection itself is capacity-limited, while the future wireless network must enforce getBandwidth when distributing energy.

`Capabilities.EnergyStorage.BLOCK` provides a receive-only finite FE compatibility adapter, using classic RFSupport 1IF=4RF/FE. All faces/parts share the same origin's per-game-tick bandwidth budget: NORMAL400FE/tick, ADVANCED1200FE/tick. Simulation makes no changes; quarter-IF fractions survive; saturation never creates energy. Retained capability objects recheck validity and cannot charge/drain removed or incomplete machines. It does not extract through FE because the source object is a receiver.

This FE adapter is explicitly partial compatibility until the original generators, energy units, wireless nodes, matrix and their graph/distribution semantics are ported. It is not claimed to be the original wireless energy network or an original RF converter block. Survival crafting of the prerequisite power ecosystem is still incomplete.

## Occupancy, GUI close, lifecycle and security

Original BlockDeveloper activation is non-sneaking and only proceeds if its tile reports no user; TileDeveloper.use redirects a subpart to its origin and opens DeveloperUI. TileDeveloper itself allows replacing a user if called directly, and its original block test reads the clicked subpart's user, permitting a stale/subpart occupancy hole. The modern adapter intentionally uses exclusive authoritative origin occupancy for every part, rather than recreating that takeover bug.

Original DeveloperUI.onGuiClosed calls tile.onGuiClosed, which sends unuse; it does not abort DevelopData. That behavior is preserved: closing the modern machine screen frees occupancy and its existing development can finish using finite machine energy. A subsequent user can occupy the same finite battery, as in source. There is no forced teleport, seating, pose change or player reposition code in the original developer, source GUI, or LambdaLib multiblock; none was invented here.

The source has no explicit GUI distance close or process distance-abort rule. Its open message broadcasts within10 blocks and energy sync within15; those are network recipient radii, not permission/reach rules. Modern machine actions require an authenticated sender-bound random nonce, the current origin occupant, same dimension, live/non-spectator player, intact loaded structure and native block reach of at least one of its eight source cells. Unreachable GUI occupancy is released on the20-tick source sync cadence. These are deliberate modern packet/session security adaptations. Existing development is not distance-aborted merely because occupancy ended, preserving the source process behavior.

The client never supplies a position, energy value, tier, player ID or machine object. It sends its current opaque token, and a skill ID only for machine_learn; unported skill IDs remain rejected just as the existing portable wire ingress rejects them. Fake or another sender's token, stale GUI close after reopen, malformed values and remote requests are rejected. Opening a portable screen releases any prior machine GUI occupancy. Existing DevelopmentController.remove/clear integration covers death, logout, dimension change, clone and server shutdown. Removed/unloaded machine references fail their next finite energy pull safely.

## Temporary GUI boundary and separate faithful GUI requirements

The machine-aware DeveloperScreen is a working temporary integration, not a recreation of original DeveloperUI. It shows real machine IF/capacity/tier, uses the existing server-authoritative learning/acquisition/upgrade flow, adds source-valid advanced reset while holding a coil, receives idle machine energy updates, and releases occupancy on every screen removal/replacement. It reuses the current ported skill list rather than exposing unimplemented abilities.

A separate faithful GUI worker still needs the source page_developer.xml layout, full left/right panels, ability-tree node positions/edges, radial skill/progress shaders and all source textures, skill popups and descriptions, exact prerequisite detail widgets, GUI SyncRate display, animated covers/console states, acquisition/reset/upgrading confirmations, source immediate rebuild events, Escape closing a nested link page first, and source font/interaction behavior. The wireless node-name/link-page flow requires the real wireless network/UI and remains deferred. Native two-slot inventory exists as in source but no unrelated new inventory screen was invented. Pixel parity of original GUI is not claimed.

## Exact deferred tier recipes

Source default.recipe ordinals44,45,46 are preserved in docs/deferred-machine-recipes.json with source aliases, 3×3 geometry and yield1. The crystal-fusion increment implements both exact Normal recipes: original ItemMatrixCore is a source-inert crafting item with three variants, so a wireless matrix machine is not a crafting dependency. Genuine source phase acquisition and Imag Fusor now provide normal-purity crystals. Advanced crafting still requires the unported functional standard wireless node tier1. Normal power remains a separate boundary: original TileDeveloper does not pull energy items, so genuine solar power delivery still needs the real wireless matrix/node graph. No substitute recipe, fake battery slot or invented adjacency power link is added. See CRYSTAL-FUSION.md.

## Validation and remaining launch boundaries

`scripts/verify-developer-machines.sh` performs cached JDK21/API javac only, then deterministic tests. Last complete run is in verification.log:

- All new runtime classes, copied narrow integration and eight native GameTests compile against NeoForge21.1.252/Minecraft1.21.1
- MachineDeveloperRegressionTest: 353 assertions pass
- DeveloperModelRegressionTest: 10289 assertions pass
- DeveloperModelEmissionRegressionTest: 68048 assertions pass
- Machine data schema/source-prerequisite checks: 39 assertions pass

Native GameTests compiled but have not been run. They cover eight-cell/cardinal collision placement/removal, obstruction/entity collision refunds, finite IF/FE capabilities on every part and retained-reference invalidation, NBT energy/inventory/user boundaries, sender/nonce/exclusivity/reach/stale-close checks, source GUI-close continuation, atomic machine vs portable remainder drain, exact TPS+1 consumption, once-only machine/inventory drops, and real advanced reset factor/coil consumption.

Headless geometry and actual vertex emission are verified; live lighting/shader/translucency, client/server wire delivery, in-game placement and model alignment, F3+T resource reload, unload/reload/restart, explosion/native destruction order and GUI interaction remain for the production owner's aggregate/native/client QA. Modern shaders/lighting cannot be claimed pixel-identical to1.7.10 fixed-function OpenGL. Original meshes/textures/icons are present, not placeholders.
