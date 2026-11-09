# Classic Ability Interferer fidelity and acceptance

This isolated increment targets AcademyCraft **1.0.7**, commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, with LambdaLib **1.2.3**, on Minecraft **1.21.1 / NeoForge 21.1.252**. It has not been promoted by this worker. Only the owner may promote, invoke Gradle, launch native/client processes, operate CUA, or record physical acceptance.

The source Interferer is **unpowered and has no survival recipe or other natural acquisition path**. `ModuleAbility.abilityInterferer` has `@RegBlock` alone, without `@RecipeName`; the complete original `default.recipe` never mentions it. `TileAbilityInterferer` directly extends `TileEntity`, not `TileReceiverBase`, and implements no IF, inventory, generator, wireless or item-energy interface. The source-sanctioned acquisition is the creative catalog, followed by ordinary placement and block drops. The production port therefore registers its genuine block, block item and block entity, but deliberately adds no recipe, IF store, charging slot, wireless tab, frequency endpoint or command-only substitute. Completing the block does not advance the 49-recipe total or demonstrate natural survival progression.

## Packaged source authority

The deliverable includes unchanged AcademyCraft `AbilityInterferer.scala`, `CPData.java`, `ModuleAbility.java`, `ACBlockContainer.java`, `RenderDynamicBlock.java`, `TechUI.scala`, `ClientResources.java`, `page_interfere.xml`, `default.recipe` and upstream README restrictions. It also includes unchanged LambdaLib `TickScheduler.java`, `EntitySelectors.java`, `WorldUtils.java`, `ElementList.java`, `TextBox.java`, `IFont.java`, `TargetPoints.java`, `NBTS11n.java` and MIT license, together with the standard GPLv3 text. Immutable source hashes are checked against a hard-coded manifest digest.

Original on/off PNGs and all 13 GUI PNGs are byte-identical existing production dependencies. Copies in the test fixture make the provenance and tests independently reviewable. The production manifest checks each existing asset hash; these existing files are not overwritten during promotion. No song, cover, media item or third-party font is added. The original GPLv3 and additional upstream restrictions remain in force; this private port makes no redistribution-rights claim.

All witnesses live in `src/test/resources/classic-interferer-source`, so the portable checks work in an assembled or archived project with no `.reference` or `.staging` directories. The temporary verification view actually lacks both directories.

## Preserved gameplay

| Source fact | Actual adapter |
| --- | --- |
| Default disabled, range 10; permitted range 10–100 | Actual BE fields and clamped server changes |
| Range forms an axis-aligned cube centered at block coordinates + 0.5, rather than a sphere | `ClassicInterfererRules.Bounds` and genuine level entity query |
| Scan every 10 enabled ticks; disabled time does not advance that counter | Independent condition-sensitive `Clock` scan counter |
| Sync every 20 world ticks even while disabled, around radius 15 | Independent sync counter and typed `TileSnapshot` sent near the actual block center with radius 15 |
| Scan includes noncreative players whose bounding boxes intersect the cube | Actual server-player entity query; no whitelist lookup |
| Registered predicate checks strict interior of the captured cube using the player's feet position | Strict `>`/`<` X/Y/Z comparisons; exact cube faces release interference |
| Creative exemption and enabled/invalid status are reevaluated by each predicate | Live creative/enabled/native identity checks |
| Sources are named by dimension and coordinates and replace the same name on rescan | Modern dimension resource identity plus exact coordinates; state-owned transient map |
| CPData prunes predicates and updates interference only when the player has a category | Existing `AbilityProgress.tick()` updates the source cache after recovery, with no-category early return preserved |
| Interference gates the shared ability runtime | Genuine existing `AbilityProgress.canUse` and all current skill callers see the resulting `interfering` flag |
| Whitelist is a sorted set exposed in GUI/NBT but never consulted by the effect | Sorted/deduplicated labels only; even the creator remains affected in survival |
| First placement records creator name and adds it to the whitelist once | Actual `BlockItem.place` / `setPlacedBy` bridge |
| Creator neither saved nor synchronized | `placer` remains transient; whitelist survives independently |
| After a reload survival creator cannot configure, while creative players can | The original authorization rule is preserved and verified server-side |
| Enabled/range/whitelist persist; range is narrowed to float | Exact `enabled_`, `range_` float, `whitelist_` compound with original LambdaLib `size` and sorted string-index keys; registry-created fresh tile restores source float quantization |
| No source predicate or timer is persisted | Fresh tile clocks reset to 10/20; ability predicate map is transient |

The captured-bounds behavior is material: with range 100, a player 50 blocks away obtains a predicate bound to that large cube. Shrinking to range 10 excludes them from future smaller scans but does not replace or remove the existing predicate. They remain blocked while inside the captured old cube until they leave it, the block is disabled/invalidated, or another source evaluation invalidates the predicate. This is an original effect quirk, not speculative whitelist behavior.

Multiple blocks coexist through distinct source IDs. Invalidating one source leaves another live source effective. A player without a category can accumulate a source entry, because the source scan does not require a category; pruning/caching starts when a category becomes available. The port's source map is owned by the current player ability state, rather than a static global store, so no captured player/world predicates are saved or retained across state replacement.

The existing explicit/manual `interfering` flag is preserved when a state has never been managed by machine predicates. This maintains current client snapshot and test/API behavior. Once a machine owns the source cache, pruning its final source clears the flag. It does not rewrite skill-specific source behavior: existing context-activated persistent skills retain their already-implemented semantics.

## Presentation and GUI

The original `RenderDynamicBlock` renders the full six-face vanilla cube with either `ability_interf_off.png` or `ability_interf_on.png`. There is **no source OBJ, rotor, particle effect, sound or standalone model animation** to invent. Both modern models are literal 0–16 full cubes with six uncullable faces, retaining `renderBlockAllFaces`. The block is nonopaque with full collision; original default hardness is zero. The item uses the off cube, as the original item `getIcon(side, meta)` does.

The server publishes the enabled model state only on its 20-tick cycle, with the original periodic typed tile sync driving client updates inside radius 15. Native chunk initialization additionally supplies a current presentation snapshot; this is a modern tracking/lifecycle adaptation, not a new per-tick source channel. The typed control acknowledgments update GUI controls before the next periodic tile synchronization, preserving the source separation between callback-visible UI and the tile values used for subsequent range/whitelist requests.

The screen uses the original 176×187 page, centered inside the 172×187 plain `CGuiScreen` TechUI root. It has one inventory-icon page, no item slots and no wireless tab. The unchanged original parent/background, `ui_interfere`, arrows, add/remove/switch buttons, list background and whitelist icon are bound through existing source-font/canvas infrastructure. Source breathing alpha remains `.675 + (1 + sin(time / 800)) * .5 * .175`. No inventory-container horizontal shift is borrowed from other machines.

Literal controls remain: the unlocalized template typo `Swtich:`, `Range:`, 10-point original system-font path, 10-block range arrows and double range text, enabled switch luminance 1 or 0.6, idle/hover button alpha .7/1, selected/unselected row alpha 1/.7. The source list is sorted, five fully visible 16-pixel rows in an 80-pixel area, with original `ElementList` scrolling. Its equality quirk is retained: exactly five names still allow one downward step, leaving only four visible rows.

Add opens the source-positioned 40×10 textbox. Enter sends the full source-derived set and dismisses; empty Enter only dismisses; losing focus dismisses. Selection/removal and up/down steps match source. Textbox behavior preserves insertion at caret, left/right and mouse caret positioning, Backspace, Delete clearing the whole box, copying the entire content, and the source Ctrl+V quirk that inserts text while retaining the old caret. Source emit includes the final glyph that crosses its width. The caret uses the original two-second blink with one second visible. Escape and native close retire the native menu; resize reinitializes transient focus and releases/reuses the established source font resources. No pause is introduced.

## Explicit modern adaptations

The original trusts client-side GUI opening and its server mutation handlers do not authenticate the sender. The port enforces the original creator-name/creative rule on the actual server before opening and for every mutation. Each menu holds a unique nonce, exact native tile identity, position and monotonic contiguous request sequence. Closed/replaced/stale menus, another sender, out-of-reach/dead/removed/spectator actors, wrong worlds, unloaded chunks and invalid native identity cannot change state. Opening respects `mayInteract`, and source control use consumes the interaction even if access is denied. Placer persistence and whitelist authorization have not been invented.

No client-provided position can trigger chunk loading in these controls. Predicate lifetime additionally ends for an unloaded/replaced tile, a removed player or a different server dimension; this prevents the original captured tile reference from remaining effective across modern unloading/world lifecycle boundaries. The source source ID's legacy numeric dimension is adapted to the modern dimension resource identity.

Transport limits are 128 labels of at most 64 UTF-16 units, with no empty/control labels. The original accepted an unbounded string array; finite bounding limits are a modern packet/NBT safeguard. Labels remain arbitrary ordinary strings, with spaces allowed; no speculative account-name verification or whitelist effect is added. Source normal range changes clamp, while nonfinite packet values are rejected. Missing, malformed or nonfinite saved range normalizes safely to 10 and out-of-bounds saved range clamps to 10–100. NBT label arrays are bounded/sanitized, retaining the original compound `size`/index representation. This does not provide a1.7.10 registry/world importer. The textbox filters control characters during paste and enforces the packet length limit.

Control callbacks use acknowledged authoritative values instead of reproducing the original repeated-inflight toggle closure's possible double-toggle race. The range and whitelist operation base still comes from the 20-tick authoritative tile snapshot. Initial server-authenticated menu snapshots replace dependence on a potentially unsynchronized client tile. These adaptations are separated from preserved numerical, scan, cube, ownership and persistence facts.

All common block/tile/menu/protocol classes link with client packages explicitly denied. Client consumers live in a `Dist.CLIENT` mod-bus subscriber; the common network classes contain only harmless consumer placeholders. No common-side class references Minecraft client classes.

## Executed and unexecuted verification

The worker ran only cached official JDK21/NeoForge API `javac` and finite portable Java tests. Current verification compiles the entire current production Java tree together with all staged additions and staged portable test mains; the exact current source count is recorded in the frozen verification report. All portable suites pass:

- `ClassicInterfererSourceOracleTest`: **264,309** unchanged-original scheduler/list differential and immutable source witness checks
- `ClassicInterfererDataRegressionTest`: **42,019** strict cube, state-owned sources, real ability gate, finite input, actual unchanged wireless graph and client-denied common-link checks
- `ClassicInterfererVisualRegressionTest`: **85** original asset/model/layout/textbox checks

The original unchanged LambdaLib scheduler and ElementList are compiled against separate minimal old-API stubs and exercised independently. The original Scala Interferer itself is a packaged immutable witness; **it has not been compiled or executed** in this worker. The strict-cube numerical oracle and source contract checks are independent semantic/witness checks, not a claim of whole original-mod execution.

Ten ordinary `academy_interferer` native GameTests compile, but **none was run by the worker**. They cover:

1. Registered creative-supplied block item, genuine placement consumption, source creator/list initialization, no recipe result, one native drop
2. Real ten-enabled-tick acquisition, creator/whitelist ineffectiveness, genuine existing ArcGen denial without CP debit and restored cast after disable
3. Strict cube faces, diagonal cube corner and live creative exception
4. Captured old range after shrink and paused/resumed scan counter
5. Multiple named sources, no-category source state and invalidated native tile lifetime
6. Real server menu, sender, creator, nonce, contiguous sequence, reach, close/reopen and exact tile identity guards
7. Sorted/deduplicated arbitrary labels, whitelist-independent permission, transport limits and source range clamps
8. Genuine registry NBT fresh tile, float quantization, omitted creator, transient clocks, creative recovery and malformed range
9. Actual IF/FE/item-handler capability absence, real current graph/wireless-menu rejection, genuinely installed terminal/app and authorized node Frequency rejection, unchanged finite battery after using it on controls
10. Full source collision/zero hardness and independent 20-tick on/off presentation timing

One separate `academy_interferer_restart` fixture also compiles. Its owner-only seed/verify mode requires a dedicated `academy-interferer-restart-world`, property `academy.interferer.restart=seed|verify`, and two distinct JVMs. Seed creates one actual remote, naturally nonticking BE at `(1540,80,1540)` with enabled true, range `88.125f`, transient creator `InterfererSeed` and sorted `Alpha / InterfererSeed / zeta` labels. Graceful seed shutdown must write the native Anvil region. Verify reads bounded actual `region/r.3.3.mca` bytes **before loading the chunk**, checks exact disk payload including vanilla `keepPacked=false`, then checks the genuine cold registry tile, omitted creator, fresh clocks and absence of both IF roles. It refuses occupied cells, existing seed region/marker, reused JVM or wrong world. The marker is evidence of prepared input only, never accepted as persistence proof.

## Owner acceptance still required

Promote only frozen additive files and exact unique shared hunks after checking baseline/dependency hashes. Keep Wind, Hook and achievement changes; full shared shadows are review aids, not replacement files. Run the normal owner aggregate Gradle check and native namespace, then the separate-JVM Anvil fixture if accepting cold persistence.

Physical cloud-client acceptance remains open. Verify creative acquisition/place/one drop; original off/on cube through the 20-tick cycle; GUI button hitboxes and source page/icon/text at relevant scales and EN/ZH; toggle and repeated inflight controls; min/max and repeated range arrows; sorted list/add/empty submit/caret/paste/Delete/remove/up/down/five-row equality; focus loss/Escape/close/reopen/resize/resource reload; actual creator-survival blocking, strict boundaries, cube corners, two sources, creative exception, shrink-capture behavior and genuine bound-ability cancellation/gates. Save and launch a fresh client to verify source float fields survive while survival creator access is intentionally lost and creative access remains. Declare all supplied fixture resources/category/skills rather than calling that natural progression. Neither compiled tests nor portable checks prove rendered pixels, actual client input, natural acquisition or multiplayer network behavior.
