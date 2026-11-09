# Native pause-menu backdrop blending repair

## Diagnosis

The ordinary Minecraft1.21.1 pause screen does not reuse one cached world frame. `Minecraft.runTick` clears the display color/depth every render frame (`Minecraft.java:1184`), and `LevelRenderer.java:947–950` clears the bound world target before sky/terrain drawing. Normal main-loop frames call `GameRenderer.render` with world rendering enabled (`Minecraft.java:796–807,1195`). `GameRenderer.java:1022–1024` renders the level before the GUI; it does not exclude paused frames. Pausing freezes the timer (`Minecraft.java:1228–1236`), not this rendering path.

The observed blackout instead follows a blend-state leak from the Academy HUD into Minecraft's native menu blur:

1. `ClassicHudCanvas.begin` enables blending and calls `RenderSystem.defaultBlendFunc`. Official `RenderSystem.java:677–683` uses SRC_ALPHA / ONE_MINUS_SRC_ALPHA for RGB, but ONE / ZERO for alpha. Thus the full-screen category-mask draw preserves intended world RGB under transparent texels while replacing framebuffer alpha with the mask's source alpha. The new no-cutoff mask writes alpha zero at its transparent center.
2. Current `ClassicHudCanvas.end` resets shader/color/depth/cull but leaves blending enabled. `AcademyClient.hud` runs it in `RenderGuiEvent.Post`, before GameRenderer draws the native screen (`GameRenderer.java:1073,1092`; `GuiLayerManager.java:54–61`).
3. Native `PauseScreen.renderBackground` delegates to `Screen.renderBackground`. `Screen.java:362–371` invokes the menu blur. `GameRenderer.processBlurEffect` executes the blur PostChain (`GameRenderer.java:352–357`).
4. Official `shaders/post/blur.json` has six main→swap→main box-blur passes. `shaders/program/box_blur.json` requests an opaque ONE/ZERO blend; `EffectInstance.java:250–253` applies its BlendMode. However official `BlendMode.java:43–61` only reapplies GL state when the blend descriptor differs from static `lastApplied`. An equal opaque descriptor on the next pause frame skips disableBlend even though the HUD has directly enabled blending in the meantime. Modern ordinary ShaderInstance does not update this EffectInstance-specific cache.
5. Each `PostPass` clears its output target before drawing (`PostPass.java:77–86`), and box_blur preserves sampled alpha. With leaked SRC_ALPHA blending, low-alpha world RGB is multiplied by that alpha on each of the six passes. Zero-alpha center becomes black; alpha26/255 corners shrink below one millionth of the original RGB after six passes. Higher-alpha HUD artwork remains visible.

This explains both observations: old alpha-discarded central pixels retained world alpha and remained visible while drawn vignette corners went black; the corrected no-cutoff vignette writes even its transparent center's alpha, exposing blackout across the world. Escape immediately restores normal rendering because gameplay no longer executes the native menu blur. The cause is not accumulated mask blending over a cached paused image.

## Narrow staged change

Only rendering behavior change: `ClassicHudCanvas.end` explicitly calls `RenderSystem.disableBlend()`. This matches ordinary vanilla GUI teardown (`GuiGraphics.java:613–631`, `Gui.java:1145–1154`) and ensures native opaque blur receives the expected disabled-blend state even if its descriptor cache skips reapplication.

The shared canvas is used by ability HUD, deviation overlays and the developer/wireless GUI adapters. Their own canvas begin enables blending for their drawing. Subsequent vanilla draws establish their needed blend state. The repair does not hide the HUD while a screen is open, change the vignette, alter pause behavior or change the earlier frozen alpha/filter repair artifacts.

A one-line `build.gradle` addition wires `classicPauseBlendTest` into ordinary aggregate check.

## Meaningful isolated regression

`ClassicPauseBlendRegressionTest` reads the actual compiled Minecraft1.21.1 BlendMode class from its ordinary dependency classpath (SHA256 `281e5fb3d059662f861f1038b4327c78d87d3f860cf2da05e20036203addecb6`). An ASM remap gives it a private isolated name and redirects only its RenderSystem calls to recording hooks. The actual constructor, equality/cache logic and apply bytecode execute unchanged. No Minecraft source/binary is bundled in the deliverable and no game or GL context is initialized.

The test proves the direct-enableBlend/cached-opaque failure using that actual logic, models the documented framebuffer blend equation over six cleared targets, reproduces center/corner blackout and high-alpha HUD survival, and verifies explicit teardown prevents attenuation at six mask alpha values. It also checks the actual compiled canvas end unconditionally disables blending.

Cached JDK21 compilation against official Minecraft1.21.1/NeoForge21.1.252 APIs passed. The regression passed 50 checks; empty-directory classpath recovery also passed. A separately compiled legacy canvas with disableBlend removed failed closed. See `verification.log` and `scripts/verify_cached.py`.

No production edits, Gradle, Minecraft/server/client launches, GL contexts, CUA operations or original frozen-artifact changes were performed by this worker. Mathematical/cache regression is not proof of actual rebuilt menu rendering; main owns that verification.

## Next actual check

After integration/rebuild, open the native pause menu with abilities active and leave it open for several seconds, then Escape and repeat once. The blurred world should remain visible and stable on the first and repeated openings; gameplay should immediately restore the same smooth vignette. Verify one deactivate/reactivate cycle afterward and briefly open/close the retained developer GUI because it shares the canvas teardown.

This does not establish complete original visual parity or original trilinear mask minification. Those qualifications from the earlier repair remain.
