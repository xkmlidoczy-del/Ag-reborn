# Wireless client stage: AcademyCraft1.0.7 → NeoForge1.21.1

All implementation and copied assets are staged under `.staging/wireless-energy/client-stage`. No production files changed, Gradle/Minecraft processes launched, user-computer files read, publication or push performed.

## Deliverable

- `src/main/java/cn/academy/port/client/ClassicWirelessObj.java`: immutable named OBJ-group adapter over the existing tested `ClassicDeveloperObj`; keeps its flat face normals, V flip and .0005 UV inset without changing the shared parser. Retains source `Main`, `Core`, `Shield`, `Duplicate01`, `Duplicate02` groups but renderer emits only the original three selected groups
- `ClassicWirelessVisualRules.java`: source node energy texture thresholds, linked top/bottom, original cardinal matrix pivots and rotations, three-shield phase/hover equations, source breathing/histogram policy
- `ClassicWirelessNodeAnimation.java`: exact source state resets and one-frame advance per render, including long-frame behavior
- `ClassicWirelessModels.java`, `ClassicMatrixRenderer.java`: resource-reload-safe original matrix OBJ; origin-only Main/Core, three Shield instances only with exactly three plates, conservative full hover/rotation bounds
- `ClassicWirelessClock.java`: pause-aware client clock, maintained by client ticks
- `ClassicWirelessScreen.java`: real native inventory plus source artwork/TechUI page tabs, histogram/owner metadata, native EditBox text editing drawn with existing source AWT font, masked local passwords, connected/available lists, per-target password entry, link/unlink, refresh, node name/password, matrix SSID/password/init/reset, host return. Requests carry current menu ID and UUID token; server snapshots are presentation only. Wireless pages suppress inventory rendering, pointer slot actions, hotbar/drop shortcuts and typing leaks. Native source slots remain the parent's authoritative menu implementation
- `ClassicWirelessClient.java`: client-only screen/renderer/resource registration and UUID/menu-checked snapshot bridge. `openSolar`/`openMachine` preserve server source/session validation
- `ClassicWirelessTabButton.java`: original scaled source icon for solar's playable wireless entry, using native keyboard/narration/button behavior
- `src/main/resources/assets/academy`: 43 byte-identical source model/texture/XML assets, 30 tier/charge/link node models and matching blockstates, original node inventory presentation (top1/side1), empty origin-BER matrix world model and original 2D block-texture matrix inventory item
- `integration/solar-screen.patch`, `integration/developer-screen.patch`, `integration/fusor-screen.patch`, `integration/fusor-menu.patch`: narrow existing-screen hooks. The developer patch adds `origin` and optional `node_name` snapshot fields, wires its existing Current Node click region to the real wireless menu, and preserves existing AWT/glyph ownership code
- `integration/language-additions.json`: only new native UI labels for four existing language dictionaries; merge into their existing JSON, do not replace dictionaries

## Canonical source citations

Canonical baseline is checked-out `.reference/AcademyCraft-1.0.7`, README's upstream commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`; preserve root LICENSE/NOTICE.

- `src/main/java/cn/academy/energy/block/BlockNode.java:67–78,94–111`: two top textures, five side textures per tier, inventory top enabled/side1, in-world round(4*energy/max) side selection and actual connected top/bottom
- `src/main/java/cn/academy/energy/block/TileNode.java:67–81,120–169`: owner, periodic rendering/state sync, tier-derived capacity/range/energy; native code must maintain these source-derived values
- `src/main/java/cn/academy/energy/client/render/block/RenderMatrix.java:28–63`: original model and texture, always Main/Core, only all-three plates draw three Shield copies; rotation=(time/20)%360+120i; vertical=.1*sin(time/900+40i), where40i is radians and no OBJ scaling is applied
- `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/multiblock/RenderBlockMulti.java:29–47`: only loaded origin renders, translate pivot-offset plus rotated center, apply cardinal source rotation
- `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/multiblock/BlockMulti.java:125–145,162–164,271–272`: cardinal pivots after center(1,0,1) are NORTH(1,0,1), SOUTH(0,0,0), WEST(1,0,0), EAST(0,0,1); source rotation NORTH180/SOUTH0/WEST−90/EAST90 (do not mis-index ForgeDirection ordinal)
- `src/main/java/cn/academy/energy/block/BlockMatrix.java:35–58`: original eight-cell structure and rotation center(1,0,1)
- `src/main/java/cn/academy/energy/block/ContainerNode.java:40–51`: recharge/discharge slots(42,10)/(42,80)
- `src/main/java/cn/academy/energy/block/ContainerMatrix.java:37–54`: plates(78,11)/(53,60)/(104,60), core(78,36)
- `src/main/java/cn/academy/core/container/TechUIContainer.java:25–43`: player hotbar y163; inventory rows y105/123/141; 18-pixel x pitch, starting6
- `src/main/scala/cn/academy/energy/client/ui/GuiNode.scala:30–81,105–125`: source linked8×800ms/unlinked2×3000ms state animation, effect region(42,35.5) size186×75 at.5scale, energy+capacity info, owner-only edits
- `src/main/scala/cn/academy/crafting/client/ui/GuiImagFusor.scala:18–48`: actual source inventory and wireless user pages plus energy/liquid display; narrow fusor patches preserve all existing inventory slots/progress rendering
- `src/main/scala/cn/academy/energy/client/ui/GuiMatrix.scala:25–89`: actual entry is `GuiMatrix2`, inventory and capacity/owner/range/bandwidth, SSID/password initialized/edit/INIT paths
- `src/main/scala/cn/academy/core/client/ui/TechUI.scala:160–194,202–219,226–302,446–452,541–778`: source page placement/tint, breathing alpha=.675+((1+sin(time/800))*.5)*.175, histogram colors/3% clamp, 100-pixel animated sidebar, inventory active only on inventory page, connected/available/rebuild/link/unlink/password clearing behavior
- `src/main/resources/assets/academy/guis/rework/page_wireless.xml:1`, `page_inv.xml:1`, `pageselect.xml:1`: original176×187 panels, connected row, seven visible16-pixel rows, target password/key/link placement and original page-button scale
- `src/main/resources/assets/academy/models/matrix.obj:379,658,669,694,719`: source Main212/Core4/Shield22 triangles and duplicate Shield groups22 each; originals preserved but duplicate groups deliberately excluded from emission
- `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/util/helper/GameTimer.java:36–69`: client pause-aware clock with recurring tick update

## Intentional native/security adaptations

Parent authenticated protocol validates sender, current menu/session UUID, source reachability and owner; never copies the source's trust in an arbitrary player argument. No existing network or node password is transmitted to the client; the password fields are blank local replacements or authentication attempts. `matrix_reset`, refresh, explicit host return and repeated Escape fallback are requested modern usability extensions. Node dynamic source TESR is represented by state-selected native cube models, retaining source texture/charge/link policy. Matrix's generated inventory item uses the original block icon because the source block has normal 2D item registration; actual world rendering uses the original OBJ.

The parent integrates menu/source positions and machine `origin`/`node_name`, registration and graph/contracts. Crystal fusor has a narrow staged screen tab and source-position constructor/accessor patch; the parent owns authenticated open_receiver and actual fusor-menu host return. All names/passwords are fictional game-world state, not network/OS credentials.

## Verification performed

- Pure JDK21 parser/geometry/timing test: **3,706 assertions passed**, including source named group triangle counts, immutable map, malformed OBJ rejection, relative and multigroup indices, V/inset/flat-normal behavior, all node round boundaries, exactly-three plates, all four matrix pivot/rotations, shield phase/height bounds, linked/unlinked state reset and long-frame behavior
- Python resource/action check: **43 original byte hashes**, **30 native node variants**, source enabled-top/side1 item states, valid JSON, no duplicate groups emitted, origin guard and every required native UI action passed
- Cached NeoForge21.1.252/Minecraft1.21.1 **native API javac passed against explicit menu/protocol/tile contract stubs**. This checks actual GUI/render/event/packet API names but is not full common integration. Only existing deprecated event-bus annotation warnings remain
- Actual staged common-contract integration: **cached javac passed** for every client class and all three patch-applied existing screens plus the patched Fusor menu against the parent compiled `.staging/wireless-energy/.javac/main` classes; no contract stubs in this check. See `tests/integrated-api-javac.log`
- Test commands: `tests/check-native-api.sh`; pure `javac` with `ClassicDeveloperObj`, `ClassicWirelessObj`, `ClassicWirelessVisualRules`, `ClassicWirelessNodeAnimation`, `tests/ClassicWirelessVisualTest`; run its main with assertions; `python3 tests/verify_assets.py <repository-root>`
- No Gradle, game/client/server launch, runtime GUI interaction, actual FPS rendering, visual capture or comparative visual parity test performed. These are staged source-derived implementations, **not a claim of runtime or visual parity**
