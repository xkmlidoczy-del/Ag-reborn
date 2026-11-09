# AcademyCraft 1.0.7 energy unit

## Scope and source

Target: Minecraft 1.21.1 / NeoForge 21.1.252. Canonical source is AcademyCraft1.0.7 with LambdaLib1.2.3 in this checkout. Adapted source retains GPLv3 attribution in new Java headers; the existing LICENSE and NOTICE apply. Three original item PNGs are copied byte-for-byte, with source hashes in the regression resource manifest.

This stage adds the actual `academy:energy_unit` item, not an inert stand-in. Its native imaginary-flux storage is finite and stack-owned: 10,000 IF capacity, 20 IF per charge/pull call, fractional double amounts, source full/empty helpers, source-style visual gauge and tooltip, creative empty/full entries, item-capability extraction/charging, exact three crystal-tier recipes and the authentic energy-converter recipe. It also makes this item a native Current Charging target. It does not implement generators, nodes, wireless networking, converter machines or phase liquid.

Canonical behavior comes from `ModuleEnergy.energyUnit`, `ItemEnergyBase`, `ImagEnergyItem`, `IFItemManager`, `EnergyItemHelper`, `RFSupport`, default.recipe and LambdaLib `RecipeRegistry`. RecipeRegistry returns the mapped Item directly when no `#metadata` was specified, so the converter's `ene_unit` ingredient accepts empty, partly charged and full units. Converter crafting consumes the unit as an ordinary ingredient; it does not return an empty unit or refund stored IF.

## Native energy and compatibility

- Native helper support takes priority over FE. `ClassicEnergyItemHelper.nativeStorage` wraps the existing portable developer (capacity10,000 / bandwidth50) without changing its payload key or defaults, and directly adapts imaginary-flux items
- Unit payload uses the modern CUSTOM_DATA key `academy:imaginary_energy`; missing data reads zero. Setters preserve foreign custom-data fields. Copies and native item serialization own their payload independently. No old1.7.10 world/item migration is claimed
- `charge` returns IF not transferred. Signed negative charge discharges and returns the signed remainder. `pull` returns actual IF removed and drains a partial remainder. `ignoreBandwidth=true` bypasses only the native IF limit, not capacity
- Corrupt nonfinite/negative saved amounts become zero; oversized finite amounts clamp to capacity. Nonfinite requests cannot transfer. Negative pulls cannot create energy. These intentionally harden legacy IFItemManager underflow/nonfinite bugs while preserving meaningful source operations
- Optional NeoForge FE capability converts1IF=4FE. Capacity40,000FE and transfer bandwidth80FE are finite. One receive/extract call moves at most20IF. Simulation is read-only. Integer FE floors sub-FE residues, retains fractional native IF, and never invents fractional FE. This follows the declared RFSupport conversion rather than legacy RF manager arithmetic bugs already documented in `electromaster-current-charging.md`
- Normal unit stacks have maximum count1. Source recipe outputs count2/4 are empty; their shared crafting-result payload cannot receive/extract energy until split into individual units. This prevents a charged multi-count cursor being split into multiple charged units. Corrupt multi-count payloads are emptied by the native load/component-verification hook
- A retained capability becomes unavailable after its backing stack is empty or its count is no longer1. It cannot receive/extract phantom energy or advertise phantom capacity
- FE compatibility does not imply an AcademyCraft generator/network implementation. Existing external FE providers can charge the finite unit; no new power source is invented

## Display and default state

The source maxDamage13 is retained as a gauge. Default DAMAGE13 and absent native payload mean empty. Stored IF drives the property and bar; client rendering cannot change energy. Native writes keep gauge metadata synchronized. Gauge display derives from source `Math.round((1-energy/max)*13)`, with full for damage0–2, half3–10, empty11–13. In particular5,000IF rounds to damage7, not6. Property `academy:energy_unit_level` is empty0/half1/full2 and selects the original three PNG models. Tooltip is source `%.0f/%.0f IF` with stable numeric formatting, e.g. `1235/10000 IF` for1234.625IF.

Legacy freshly crafted stacks had zero NBT energy but default damage0, briefly showing a full icon until IFItemManager first wrote energy. The port deliberately shows empty from creation, as required for a source-safe default. The source's two creative entries are explicit empty/full item helpers; ordinary survival crafting never adds power.

`ItemEnergyBase` and `ACItem` contain no right-click or block-use override. No standalone block energy-injection interaction is fabricated. Source node/machine inventory slots are responsible for exchanging IF; they remain dependencies. New item-capability extraction and the native helper provide real finite interfaces for those future ports.

## Exact survival recipes and acquisition boundary

IDs follow the source-order convention used by the survival foundation:

| Recipe | Grid | Yield |
| --- | --- | --- |
| classic/energy_unit_18 | three constraint plates, low crystal, data chip in exact source3×3 grid |1 unit |
| classic/energy_unit_19 | same, normal crystal |2 units |
| classic/energy_unit_20 | same, pure crystal |4 units |
| classic/energy_convert_component_37 | vertical calc_chip / energy_unit / reso_crystal |1 converter |

All non-unit ingredients and converter output are genuinely registered in ClassicMaterials. Modern vanilla ShapedRecipe uses ItemStack.STRICT_CODEC, which rejects a count2/4 result for a maxStack1 item. A narrowly registered academy:energy_unit_shaped serializer preserves vanilla shaped matching/patterns while allowing only the exact empty energy-unit results count1/2/4. It rejects other outputs, counts and component patches; packet transport still uses the native registry-aware item codec. The converter uses the ordinary vanilla shaped serializer. No tags replace source-specific custom ingredients. No crystal upgrade, coin/needle/factor bypass or power-refill recipe is added. Low-purity crystals and base materials already have the source ore/mining/smelting/material crafting path, so this closes the authentic crafting chain to the converter and existing portable-developer recipe. Mid/high-purity crystal acquisition still belongs to Imag Fusor. A complete independently powered survival progression remains unfinished until AcademyCraft generators/networks/machines are implemented; crafting availability is not full survival completion.

## Required fluid dependency remains explicit

ModuleEnergy.init also registers full energy_unit as a1000mB imagProj fluid container and empty energy_unit as its empty container. `ModuleCrafting.fluidImagProj`, phase liquid and original fluid/container infrastructure are unported. This stage does not create a fake fluid or inert fluid-capability shell. That source exchange is missing and must be implemented together with the genuine imagProj fluid and appropriate fluid consumers. The standalone IF/FE item behavior and recipes above are implemented; full source catalog completion is not claimed.

## Integration

Only `.staging/energy-units` was written. The integration owner applies:

1. Copy `src/main/java`, `src/test/java`, `src/test/resources` and `src/main/resources` into production preserving relative paths. These staged main resources contain only the new item textures/models and four new recipes; they do not replace language/atlas files
2. Apply `docs/energy-units-integration.patch` with `patch -p1`. It adds exactly one `ClassicEnergyItems.register(bus)` call, native helper priority in ChargingEnergy, one client setup property registration, three JavaExec regression tasks in `check`, and four locale keys. The patch dry-runs clean against the current machine-integrated production snapshot; if machine integration changes a shared context line, merge the same narrow edits rather than replacing whole integration copies
3. Do not copy `integration/` whole-file compile overlays on top of newer production changes. They are isolated verification inputs. Do not copy `.javac` output
4. The existing shared blocks atlas already maps plural `academy:items/` textures; no new atlas source is necessary
5. Existing jar task excludes all `cn/academy/port/gametest/**` fixtures. Preserve that exclusion
6. Update the survival manifest/document statuses for source recipe ordinals18/19/20/37 and ModuleEnergy.energyUnit when integrating, while retaining the fluid/generator/machine dependency boundary
7. Run aggregate Gradle check/build and native GameTests after the parent's live socket/client process is safely stopped. Do not infer a native pass from headless compilation

## Verification

Cached JDK21 javac compile includes all new runtime/client/native-test classes and the three exact integration overlays against pinned merged MC/NeoForge API. Outputs live only in this staging tree. No Gradle, native server, client, EULA flow, production write, publication or user-computer access was performed.

Passed deterministic checks:

-200,090 finite native IF, signed remainder, corrupt/custom-data, visual rounding and FE conservation assertions
-184 exact recipe, source excerpt/hash, original texture, actual atlas and model-override resource assertions
-15 new energy/common-integration classes cold-linked with all Minecraft/NeoForge/port client namespaces denied
-Existing ChargingEnergyRegressionTest and DeveloperEnergyRegressionTest run against the staged ChargingEnergy overlay to catch regressions

Eight native GameTests are written and compiled, not executed: real registration/default/tooltip/gauge; native fractional energy/copy/capability conservation; real item codec and corrupt component bounds; multiyield, deliberately inflated charged-save normalization and retained-capability safety; three exact native recipes; strict-codec rejection and bounded custom serializer/packet round trips; charge-state-agnostic consumed converter ingredient; native CurrentCharging target/session bandwidth ordering. The charging test uses the native item and actual deterministic session but does not claim real socket/client transport. Runtime model rendering, live inventory/hotbar appearance, physical crafting cursor splitting, third-party capability compatibility and phase-liquid exchange remain unverified.

Verification command: `scripts/verify-energy-units.sh`; evidence: `verification.log`. The script must remain within this stage location unless its relative root contract is deliberately updated.
