# HUD/developer font texture ownership fix

Parent live client QA observed HUD R/F labels turning into missing-texture purple/black quads after closing the developer GUI, while source LMB image assets remained correct.

Confirmed cause: ClassicHudFont's instance-local `textureSerial` gave every instance the same global TextureManager IDs, `academy:dynamic/classic_hud_font_0`, `_1`, and so on. Developer glyph registration replaced textures that the HUD cache still referenced. Developer removal correctly called its own font's release, but those colliding IDs also removed the HUD's cached resources.

## Narrow integration

Apply `classic-font-ownership-fix.patch` to production `src/main/java/cn/academy/port/client/ClassicHudFont.java`, or review the staged full snapshot in `integration/cn/academy/port/client/ClassicHudFont.java`. No Screen, HUD layout, glyph rasterization, width metrics, texture filtering, font preferences or machine/network behavior changes.

Each font now receives a process-unique monotonically assigned owner number from AtomicLong. Texture IDs include that owner and its own monotonically increasing glyph serial: `academy:dynamic/classic_hud_font_<owner>_<serial>`. Normal and bold maps within one font still share its serial. Clearing/reusing a font keeps increasing that serial. A separately reopened screen receives another owner. Release delegates to the same cache iteration with TextureManager::release; the extracted disposer seam allows headless verification of the actual release code.

A fresh client restart is needed for the currently running game because its existing HUD cache already points at deleted pre-fix keys. Merely closing/reopening another popup cannot repair those earlier cached IDs.

## Verification

`ClassicFontTextureOwnershipRegressionTest` directly exercises actual ClassicHudFont allocation, actual normal/bold Glyph caches, and actual release implementation through reflection, with a fake registered-texture map and no Minecraft/OpenGL initialization.

1172 assertions pass, including:

- Simultaneous HUD and developer R/F glyphs get different global IDs
- Fifty new-screen close/reopen cycles never replace or remove HUD textures
- Both normal and bold caches release only their owner's keys
- Double close has no extra release effect
- Reusing the same font after release does not reuse old keys
- HUD preference/reload release leaves another screen's glyphs intact
- Releasing another screen does not remove newly regenerated HUD glyphs
- All manager keys are eventually released by the correct owner

Cached API javac passes against JDK21/Minecraft1.21.1/NeoForge21.1.252. Existing620UI assertions, nine independent source tests and50skill/77asset source integrity checks still pass. Log: `font-ownership-verification.log`.

This verifies identifier/cache/release ownership, not live GPU rendering. Parent must restart its existing client, reopen/close the developer repeatedly, check HUD R/F text before/after and F3+T reload, and retain the actual source GUI screenshot. No production edits, game launch, Gradle or computer UI was performed by this worker.
