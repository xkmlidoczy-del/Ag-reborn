# Portable first-person framing: missing caller basis

## Observed failure

Parent supplied untouched F2 capture `run-client-visual-m08/screenshots/2026-10-01_00.33.07.png`. Actual pixels were viewed: most portable hardware is beyond the right edge, leaving only its left side and part of the painted screen. GUI source charge icon/bar are intact. This is a real physical-context regression, not an icon/payload issue.

## Primary evidence and cause

Official Forge1.7.10's non-block `renderEquippedItem` branch applies five additional operations before the mod's callback: translation(0,-.3,0), scale1.5, Y50 rotation, Z335 rotation and translation(-.9375,-.0625,0). LambdaLib only opts into entity rotation/bobbing helpers, so it uses that equipped branch. The initial modern BEWLR omitted this surrounding basis. See [official Forge source, method lines196–218](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/client/ForgeHooksClient.java) and [the official patch inserting its callback](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/patches/minecraft/net/minecraft/client/renderer/ItemRenderer.java.patch).

The official Mojang1.7.10 client was downloaded read-only from its verified [version metadata](https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json). Client SHA1 is `e80d9b3bf5085002218d4be59e668bac718abbc6`, matching the manifest. `javap -c -p` inspection of ItemRenderer's obfuscated `bly` class confirms normal first-person base Y45, swing Y/Z/X, then scale.4 before item rendering. Bytecode offsets1252/1302/1314/1326/1339 capture those operations. No Minecraft class was executed or game launched.

The cached1.21.1 ItemInHandRenderer has the same settled(.56,-.52,-.72) hand translation and corresponding swing terms, but its attack matrix ends with inverse Y45. The vanilla model's normal display transform ordinarily provides its new basis; our custom model correctly bypasses the generated icon transform but failed to replace it with the old caller basis.

## Narrow correction

Add `ClassicPortableContextBridge.java`, copy the updated `ClassicPortableRenderer.java`, and optionally apply the renderer-only `portable-first-person-bridge.patch`. Add `ClassicPortableContextBridgeRegressionTest` to the parent aggregate checks. Source hashes are in `bridge-source-sha256.txt`.

The new first-person bridge is:

`Ry45 × S.4 × T(0,-.3,0) × S1.5 × Ry50 × Rz335 × T(-.9375,-.0625,0)`

It is applied after canceling modern ItemRenderer's -.5 centering and before the unchanged canonical AcademyCraft/LambdaLib matrix. Modern native swing ends with Ry(-45), which cancels the bridge's leading Ry45; the resulting full composition equals the original normal first-person pipeline, including swing.

`ClassicPortableTransform.java` remains byte-identical with SHA256 `c2b2d98034581decc1827d221f4ecbe5c4c8d7a1cd1f124513c9b170d9c9adfe`. No source scale6/1/16, source equip offset/angles, geometry, texture, energy, tooltip, gauge, icon, GUI, registration or packet is changed. No manual camera offset, custom viewport fit, FOV-dependent shrink or aesthetic adjustment was invented.

The left-hand bridge is mirrored by conjugation because the existing source-local adaptation already mirrors its matrix. This mirrors the entire right-hand composition exactly once. Third-person, ground, fixed/head/none bridges are identity in this increment, keeping those existing contexts unchanged while first-person is independently validated.

## Verification and remaining limits

- Cached API javac passes
- New bridge test:3510assertions pass across101swing samples, exact old-vs-modern matrix composition, left-hand composition and identity for other contexts
- Existing22,028mesh/context assertions and60,038finite IF display assertions still pass
- Actual OBJ projection at screenshot aspect1180×812/FOV70 reproduces previous gross horizontal bounds0.753..2.648 in normalized device coordinates; corrected bounds are0.280..1.246
- The source pipeline still permits some right/bottom edge clipping at this aspect/FOV. The corrected projected body occupies most of the available right-hand area, rather than a narrow edge. This is analytical evidence of the original pipeline's framing, not a claim that a1.7.10 client screenshot was captured
- Parent must validate fresh in-game first-person pixels, settled/swing/equip transitions, modern left hand, and context interleaving. A bridge with most device still cropped in actual pixels is not a finished visual result
- Primary Forge inspection also reveals a source entity-helper scale.5 outside the portable's local ground matrix. The current separate native ground adapter has not yet been compared against that whole legacy caller chain. This first-person-only patch does not silently change dropped/third-person sizes; those outer-pipeline comparisons remain explicit visual QA work

`docs/pipeline-evidence/framing-contract.json` records primary URLs, source arithmetic and offsets. `.pipeline-cache` contains read-only diagnostic binaries/disassembly; do not copy that cache into resources, the mod jar or distributed source/docs deliverables. No production edits, Gradle, runtime or computer UI was operated by this worker.
