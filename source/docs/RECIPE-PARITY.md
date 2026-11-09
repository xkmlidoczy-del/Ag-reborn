# Recipe/acquisition audit before survival integration

No recipe is implemented in this development slice. Creative/test items are not a completed survival progression path. Do not replace the original machine/material chain with invented cheap crafting recipes.

Canonical sources: AcademyCraft1.0.7 `src/main/resources/assets/academy/recipes/default.recipe`, `src/main/java/cn/academy/vanilla/ModuleVanilla.java`, and `src/main/java/cn/academy/ability/ModuleAbility.java`.

- Needle: Metal Former INCISE mode, one reinforced iron plate→6needles; one rail→2needles
- Coin: Metal Former PLATE mode,2reinforced iron plates→3coins
- Portable developer: data chip / glass pane / calculation chip; brain component / information component / energy-conversion component; constraint plate / low-purity crystal / constraint plate
- Normal developer: original recipes include portable developer or brain/info/conversion components, basic matrix core, bed, piston, normal-purity crystal, machine frame and redstone
- Advanced developer: constraint plates, glowstone, normal developer, improved wireless node, high-purity crystal and resonance crystal
- Magnetic coil: constraint plates, resonance crystals, reinforced iron plates and diamond
- Induction factors: category-specific chest loot entries in mineshaft corridor, desert/jungle temple, stronghold library and dungeon hooks; each source entry has count1 and weight4. This does not directly translate into a modern loot-table probability because the original tables and mechanics differ

Required dependencies before faithful recipes are usable include ores/crystals, reinforced/constraint materials, semiconductor wafers/chips, components, powered Metal Former and machine/energy infrastructure. These are pending. The current factor identity/data/icon/consumption adapter is implemented, but chest-loot generation is not.

Native recipe reload/crafting, powered machine mode/energy behavior, loot distribution and world generation require actual game verification after the launch gate. Static registry/resource references alone are insufficient.
