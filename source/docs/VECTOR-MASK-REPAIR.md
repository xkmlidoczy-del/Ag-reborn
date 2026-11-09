# Vector sky halo review

## Finding

The large persistent circle is the ability HUD's full-screen category vignette, with a **port alpha-cutoff defect**. It is not the world sky dome, a cloud boundary, or an inactive VecDeviation ripple. Vector's original black edge shading is intended; the abruptly clear central disk is not.

This conclusion uses actual saved screenshot pixels, original texture bytes, original rendering instructions, and the cached Minecraft 1.21.1 fragment shader, rather than texture resemblance alone.

## Concrete source evidence

- Original `.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/vecmanip/CatVecManip.scala:11` specifies `0xff000000`, an opaque black category style.
- Original `.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/client/ui/BackgroundMask.java:60` chooses that category style whenever `CPData.isActivated()`. Learned/bound skills are irrelevant. Lines 86–91 explicitly disable alpha testing, tint and draw `effects/screen_mask` over the full screen, then restore alpha testing.
- LambdaLib `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/util/client/auxgui/AuxGuiHandler.java:75` supplies SRC_ALPHA / ONE_MINUS_SRC_ALPHA blending for this HUD pass.
- Port `ClassicHudTimeline.java:102` correctly uses `(0,0,0,1)` for Vector, but `ClassicAbilityHud.java:55` supplies no explicit shader to `ClassicHudCanvas.rect`. `ClassicHudCanvas.java:51` therefore selects Minecraft `position_tex_color`.
- Official cached resource jar `/workspace/shared/academycraft-milestones/20261001-m13/source-restore-validation/build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar`, entry `assets/minecraft/shaders/core/position_tex_color.fsh`, says `if (color.a < 0.1) { discard; }` before ColorModulator. This is not equivalent to the original disabled alpha test.
- Original and production `screen_mask.png` are byte-identical: SHA256 `c69c1cc1a96aced517335f648c69d9c17320b879fe310325192661299c48bbf6`, dimensions 512×288, alpha range 0–77. At full Vector activation, texels below 26/255 disappear entirely instead of applying subtle black shading.
- `ClassicVectorStarterEffects.java:130` returns immediately from its GUI ripple callback whenever `deviation==null`; the observed halo's contour instead matches the category mask threshold.

## Pixel match

`pixel-evidence.json` records eight measured rows in the saved screenshot and recorded video frame. Every largest measured transition near the predicted alpha=0.1 contour lands within one screen pixel of that contour. The encoded frame is resized/interpolated, so its jump is spread across neighboring pixels.

In screenshot `2026-10-01_15.18.41.png` (1180×812), row 391:

- Screen x105 samples mask x45/y138, alpha26. RGB=(163,188,229)
- Screen x106 samples mask x46/y138, alpha24. Vanilla shader discards it, leaving RGB=(181,209,255)

The black blend prediction at alpha26 is approximately `(181,209,255) × (1−26/255) = (162.5,187.7,229)`, matching the actual pixel. At alpha24 the original no-cutoff behavior would remain near `(164,189,231)`, instead of jumping immediately to the unmasked sky.

Rows 162,243,324,391 in this screenshot predict first discarded pixels 162,129,111,106, matching the observed boundary at exactly those positions. Thus the entire visible arc follows a known texture/shader equation, not unknown world geometry.

## Projection and state analysis

`ClassicHudCanvas` does not modify projection matrices, model-view matrices, fog, or camera state. It draws using the current GuiGraphics pose. Its end routine resets shader/color and enables depth/cull; it is not a general snapshot/restore of every GL state, but that does not explain this artifact.

Cached official `GameRenderer.java:1024` renders the world before installing the orthographic GUI projection at lines 1039–1052. `GameRenderer.java:1265` resets the world perspective on the next world render. `LevelRenderer.renderSky` explicitly draws its sky buffer using the supplied world matrix/projection and selects its sky shader/color. Vector's world effects are at AFTER_PARTICLES, before the full-screen GUI mask. The measured vignette contour is sufficient to identify this case without asserting that every render adapter has perfect state restoration.

## Staged narrow correction

The gameplay/render replacement is only `src/main/java/cn/academy/port/client/ClassicAbilityHud.java`. It selects the existing `ClassicSkillAlphaShader.get()` for the full-screen category mask. That existing shader multiplies texture, vertex tint and ColorModulator without discarding fragments.

The additive `screen_mask.png.mcmeta` selects `texture.blur=true` and `texture.clamp=true`. Official Minecraft `SimpleTexture.java:32–41` reads these flags, then lines 51–53 pass them to `NativeImage.upload` with mipmaps disabled. Official `NativeImage.java:167–175` maps blur to GL_LINEAR (9729) for magnification and non-mipmapped minification; lines 470–473 map clamp to GL_CLAMP_TO_EDGE (33071). Metadata supplies linear magnification and modern edge clamping; it cannot supply/generated trilinear mipmaps.

A one-line staged `build.gradle` map addition wires `classicVectorMaskTest` into the normal aggregate `check`. The additive regression uses the actual official metadata serializer, compiled mask draw instructions, original texture pixels, and mandatory SHA256-pinned noticed canonical witnesses. No original checkout is required at regression runtime.

`mask-no-cutoff.patch` retains the focused call-site diff. `repair.patch` is the complete promotion patch, and `promotion-manifest.json` records exact shared before/after hashes, additive hashes and unchanged reused-material dependencies. Production source was checked unchanged after staging.

Cached isolated JDK21 compilation of the staged GUI source against actual Minecraft1.21.1/NeoForge21.1.252 passed. The regression passed 63 checks and rejected four material mutants. A separately compiled legacy shader-route mutant and a classpath missing the metadata also failed closed. Empty-working-directory recovery passed with only explicit classpath resources. See `verification.log`; `scripts/verify_cached.py` reproduces this preparation without Gradle, Minecraft/server/client launches, GL contexts, CUA operations, publishing or pushes.

## Qualification and next actual check

This corrects a demonstrated alpha-test mismatch; it does not establish full original visual parity. Original `ClientResources.java:97–103` uses GL_LINEAR magnification, trilinear mipmaps and legacy GL_CLAMP. The repair metadata corrects magnification from nearest to linear and selects modern GL_CLAMP_TO_EDGE. It does not generate mipmaps or select GL_LINEAR_MIPMAP_LINEAR, and modern edge clamp is a documented substitution for legacy clamp. Original trilinear minification remains pending; the existing `ClassicDeveloperTextures` loader offers a source-informed pattern for later exact mipmap handling.

After integrating and rebuilding, do one stationary A/B capture in the earned Vector world, with VecDeviation inactive: activate abilities, wait over one second and capture the same clear-sky view; deactivate abilities, wait over one second and capture it again. Active should retain a smooth intended black vignette with no alpha=0.1 arc; inactive should show no category vignette. Re-enable once to check the fade returns without a sharp moving contour. This validates the actual draw path and registration/fallback behavior; pixel diagnosis alone cannot claim that rebuilt rendering has passed.
