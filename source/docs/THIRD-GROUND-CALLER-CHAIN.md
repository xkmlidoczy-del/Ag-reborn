# Complete portable third-person and dropped caller chains

## Narrow integration

Runtime delta is only `ClassicPortableContextBridge.java`: existing renderer already calls this bridge before the unchanged canonical local matrix. Copy that updated class, update `ClassicPortableContextBridgeRegressionTest`, and add `ClassicPortableCallerChainRegressionTest` to aggregate checks. The narrow existing-file delta is `portable-third-ground-caller.patch`. New test is supplied as a complete file, not hidden in that delta. Source hashes: `caller-chain-source-sha256.txt`; latest cached run: `caller-chain-verification.log`.

First-person arithmetic remains unchanged. Standard contexts remain identity. Canonical `ClassicPortableTransform.java` remains SHA256 `c2b2d98034581decc1827d221f4ecbe5c4c8d7a1cd1f124513c9b170d9c9adfe`. No production source, model/glyph ownership, assets, GUI, energy payload/capabilities, solar registration, tooltip or gauge was edited by this worker.

## Primary evidence

Official Mojang1.7.10 client SHA1 `e80d9b3bf5085002218d4be59e668bac718abbc6` was previously verified against its official version metadata and inspected read-only. Official [FML SRG mapping](https://raw.githubusercontent.com/MinecraftForge/FML/1.7.10/conf/joined.srg) identifies `bop` as RenderPlayer, `bny` as RenderItem and `adb` as Item. [Official method names](https://raw.githubusercontent.com/MinecraftForge/FML/1.7.10/conf/methods.csv) identify full3D/rotateAround/renderEquippedItems.

Canonical ItemDeveloper sets full3D=true. ItemEnergyBase and ACItem do not override rotation/use actions. Vanilla Item's g() (shouldRotateAroundWhenRendering) returns false. Therefore normal portable uses the full3D, non-rotating, non-block, non-blocking branch.

The [official RenderPlayer Forge patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/patches/minecraft/net/minecraft/client/renderer/entity/RenderPlayer.java.patch) retains the arm attachment and selects the portable's branch. Verified vanilla bytecode then supplies post-arm translation/scale/rotations. [Official ForgeHooksClient](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/client/ForgeHooksClient.java) wraps EQUIPPED with the same non-block equipped helper used for first-person. The source LambdaLib renderer only enables entity rotation/bobbing helpers, so neither equipped-block nor block3D special branches apply.

The [official RenderItem Forge patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/patches/minecraft/net/minecraft/client/renderer/entity/RenderItem.java.patch) dispatches its custom entity hook after world position+bob. The non-block hook applies source spin and scale.5. Modern cached ItemEntityRenderer instead adds .25×ground metadata Y before spin; current portable wrapper explicitly exposes identity metadata with Y=1. A further verified origin difference is essential: old EntityItem sets yOffset=height/2=.125, and its Entity bounding-box code subtracts that from posY. Modern EntityDimensions uses posY as boxMinY. For equal physical box placement, source posY is therefore .125 higher than modern posY.

Offsets and arithmetic are recorded in `pipeline-evidence/third-ground-contract.json`; diagnostic bytecode/mappings are in the non-distributed `.pipeline-cache`.

## Exact third-person bridge

Let arm attachment be A. Existing modern suffix after A is:

`N = Rx(-90) × Ry180 × T(1/16,.125,-.625)`

Legacy portable suffix after that arm is:

`L = T(-1/16,7/16,1/16) × T(0,3/16,0) × S(.625,-.625,.625) × Rx(-100) × Ry45`

Shared Forge non-block equipped helper is H, established in FIRST-PERSON-BRIDGE.md. Unchanged canonical third-person local matrix is C. The new bridge is exactly:

`B = inverse(N) × L × H`

Thus native `A × N × B × C` equals source `A × L × H × C`. This restores placement/orientation/outer scale without manual hand offsets or changes to AcademyCraft's source model matrix.

Modern left hand did not exist in1.7.10. Both native suffix and canonical local adaptation are mirror-conjugated consistently, so bridge-left is `mirrorX × B × mirrorX`. It produces one mirrored complete legacy composition rather than double-mirroring geometry.

## Exact dropped bridge

Use a common physical item bounding-box base, rather than assuming old and new posY denote the same point. Official old xk sets yOffset=height/2=.125; sa.b(DDD) makes boxMinY=posY-yOffset for a normal item. Modern Entity.makeBoundingBox delegates to EntityDimensions, which makes boxMinY=posY.

Source caller is therefore `T(boxBase + .125Y + bob) × Ry(spin) × S.5 × Cground`. Current native caller is `T(boxBase + bob + .25Y) × Ry(spin)` followed by the centered callback. ItemRenderer's -.5 centering is already canceled separately.

The final bridge is `T(0,.125-.25,0) × S.5`, or `T(0,-.125,0) × S.5`. Vertical translation commutes with Y-spin, so this gives the source world/bob/spin/helper chain at equal physical placement. The initial -.25 cancellation was only valid for equal supplied posY; the final -.125 term additionally restores old item-origin semantics. This is a source/API origin correction, not an aesthetic ground lift. Entity position, bounds, physics, inventory and energy are not mutated.

Current portable physical ground metadata Y=1, modern fixed.25-height bounding-box base and the original half-height origin are asserted. A future metadata change cannot silently invalidate this cancellation.

## Verification and exact remaining limits

- Cached JDK21/API compilation passes against Minecraft1.21.1/NeoForge21.1.252
- ClassicPortableCallerChainRegressionTest:172743assertions pass
  - Full post-arm matrix equivalence across26 right/left supplied arm poses
  - Real source OBJ vertices emitted through actual native PoseStack/VertexConsumer helper, with correct transformed positions/unit normals/UVs/packed light/overlay
  -39dropped age/partial-tick samples at equal physical bounding-box placement, with source origin/bob/spin and native lift cancellation
  - Mirroring, unchanged standard contexts and identity metadata assumption
- First-person bridge test:3446assertions pass. Prior identity assertions for third/ground were intentionally replaced with the new full-chain tests; first-person composition/projection results are unchanged
- Existing22028mesh/context assertions and60038finite IF display assertions still pass; original assets and charge properties remain verified

This establishes source caller arithmetic for normal player third-person after its supplied native arm attachment and normal single-count dropped portable. It does not establish live GL/translucent lighting, actual hand/ground pixels, animation quality, pickup state or reload behavior; parent owns those serial checks and meaningful raw recordings.

Native1.21.1 body/limb animation, crouch/swim/young/slim-arm model and camera are deliberately retained. Their arm attachment can differ from1.7.10, so complete player pose or screenshot pixel parity is not claimed. Portable has no blocking use action, so no old shield/blocking suffix was introduced. A malformed multi-count portable entity can pass through modern multiple-item spreading differently than legacy custom rendering; normal source max-stack-size1 path is covered. FIXED/HEAD/NONE remain standard modern geometry and have not undergone their own old caller audit. The original source mesh can still overlap the floor at some source bob phases; this correction reproduces its source chain and does not conceal that with an aesthetic adjustment.

Parent recording should combine meaningful first-person settled/swing, third-person walking/crouch/left-hand, normal world drop spin/bob and pickup, GUI2D charge/finite IF continuity, then reload. Save major-milestone raw recordings; no extra CUA screenshots or runtime was operated by this worker.
