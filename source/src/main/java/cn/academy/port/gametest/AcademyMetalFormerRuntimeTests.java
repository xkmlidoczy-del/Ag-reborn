/* AcademyCraft 1.0.7 native Metal Former fixtures. GPLv3; see NOTICE.
 * The parent owns native launches. Direct IF seeds below are finite, declared boundary
 * fixtures; the final acquisition test earns every IF from real native solar ticks. */
package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.former.*;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.solar.ClassicSolarRules;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.wireless.*;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registries, native menus, inventory capabilities, crafting, and cold BE load.
 * Synchronous bounded native ticks isolate source ordering from unrelated world ticks.
 * These are server GameTests, not a client/socket, Anvil/new-JVM, or human-play certificate. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyMetalFormerRuntimeTests {
    private static final String TEMPLATE = "runtime_empty", BATCH = "academy_metal_former";
    private static final BlockPos BASE = new BlockPos(3, 1, 4);
    private static final BlockPos SOLAR = new BlockPos(2, 1, 7), NODE = new BlockPos(5, 1, 7);
    private AcademyMetalFormerRuntimeTests() {}

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value.contains(":") ? value : "academy:" + value);
    }
    private static Item item(String value) {
        var key = id(value);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new AssertionError("Missing real fixture item " + value);
        return BuiltInRegistries.ITEM.get(key);
    }
    private static ItemStack s(Item value) { return new ItemStack(value); }
    private static ItemStack s(String value, int count) { return new ItemStack(item(value), count); }
    private static ItemStack e() { return ItemStack.EMPTY; }
    private static void equal(GameTestHelper h, double expected, double actual, String label) {
        h.assertTrue(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-7, label + ": " + actual);
    }
    private static ClassicMetalFormerBlockEntity former(GameTestHelper h) {
        h.setBlock(BASE, ClassicMetalFormer.BLOCK.get());
        return (ClassicMetalFormerBlockEntity) h.getBlockEntity(BASE);
    }
    private static void tick(GameTestHelper h, ClassicMetalFormerBlockEntity tile, int count) {
        for (int index = 0; index < count; index++)
            ClassicMetalFormerBlockEntity.serverTick(h.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }
    private static void mode(ClassicMetalFormerBlockEntity tile, ClassicMetalFormerWork.Mode requested) {
        for (int index = 0; index < 4 && tile.mode() != requested; index++) tile.cycleMode(1);
        if (tile.mode() != requested) throw new AssertionError("Native source mode did not cycle");
    }
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-Former-Test]"));
        near(p, h.absolutePos(BASE)); p.getAbilities().instabuild = false;
        return p;
    }
    private static void near(ServerPlayer p, BlockPos pos) {
        p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5, 0, 0);
    }
    private static void close(FakePlayer p) {
        p.closeContainer(); DevelopmentController.remove(p); AbilityStorage.remove(p);
    }
    private static void place(GameTestHelper h, ServerPlayer p, ItemStack stack, BlockPos relative) {
        var absolute = h.absolutePos(relative);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        h.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "real BlockItem placement");
        h.assertTrue(stack.isEmpty(), "one actual fixture/crafted block item consumed");
    }
    private static ItemStack craft(GameTestHelper h, String recipe, int width, int height, ItemStack... cells) {
        var holder = h.getLevel().getRecipeManager().byKey(id("classic/" + recipe)).orElseThrow();
        h.assertTrue(holder.value() instanceof CraftingRecipe, "actual source native crafting type " + recipe);
        var nativeRecipe = (CraftingRecipe) holder.value();
        var input = CraftingInput.of(width, height, List.of(cells));
        h.assertTrue(nativeRecipe.matches(input, h.getLevel()), "actual source grid accepted " + recipe);
        var output = nativeRecipe.assemble(input, h.getLevel().registryAccess());
        h.assertFalse(output.isEmpty(), "real native crafted output " + recipe);
        // Finite ingredient accounting: these native source recipes have no remaining items.
        for (var remainder : nativeRecipe.getRemainingItems(input))
            h.assertTrue(remainder.isEmpty(), "no undeclared source recipe refund " + recipe);
        for (var cell : cells) if (!cell.isEmpty()) cell.shrink(1);
        return output;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_registry_cube_four_yaws_and_real_menu(GameTestHelper h) {
        var actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h);
        var p = actors.player();
        try {
            var level = h.getLevel(); var absolute = h.absolutePos(BASE);
            h.assertTrue(BuiltInRegistries.BLOCK.get(id("metal_former")) == ClassicMetalFormer.BLOCK.get(), "actual block registry");
            h.assertTrue(BuiltInRegistries.ITEM.get(id("metal_former")) == ClassicMetalFormer.ITEM.get(), "actual block item registry");
            h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("metal_former")) == ClassicMetalFormer.TILE.get(), "actual block entity registry");
            h.assertTrue(BuiltInRegistries.MENU.get(id("metal_former")) == ClassicMetalFormer.MENU.get(), "actual menu registry");
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                near(p, absolute); p.setYRot(quadrant * 90);
                place(h, p, s(ClassicMetalFormer.ITEM.get()), BASE);
                var tile = (ClassicMetalFormerBlockEntity) level.getBlockEntity(absolute);
                h.assertValueEqual(tile.getBlockState().getValue(ClassicMetalFormerBlock.ROTATION), quadrant, "source yaw metadata");
                h.assertTrue(Block.isShapeFullBlock(tile.getBlockState().getCollisionShape(level, absolute)), "source full cube collision");
                equal(h, 3, tile.getBlockState().getDestroySpeed(level, absolute), "source hardness");
                h.assertFalse(s(Items.WOODEN_PICKAXE).isCorrectToolForDrops(tile.getBlockState()), "source stone-tier harvest rejects wood");
                h.assertTrue(s(Items.STONE_PICKAXE).isCorrectToolForDrops(tile.getBlockState()), "source stone-tier harvest accepts stone");
                equal(h, 0, tile.getEnergy(), "new actual machine starts empty");
                equal(h, 3000, tile.getMaxEnergy(), "source IF capacity");
                equal(h, 50, tile.getBandwidth(), "source IF bandwidth");
                h.assertTrue(level.getCapability(MachineDevelopers.IMAG_FLUX, absolute, Direction.NORTH) == tile, "actual native IF receiver capability");
                p.setShiftKeyDown(true);
                var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
                h.assertFalse(tile.getBlockState().useWithoutItem(level, p, hit).consumesAction(), "source sneaking passes interaction");
                p.setShiftKeyDown(false);
                h.assertTrue(tile.getBlockState().useWithoutItem(level, p, hit).consumesAction(), "native server right-click opens menu");
                h.assertTrue(p.containerMenu instanceof ClassicMetalFormerMenu, "actual ServerPlayer owns opened former menu");
                var menu = (ClassicMetalFormerMenu) p.containerMenu;
                h.assertTrue(menu.tile() == tile && menu.isFor(tile) && menu.sourcePos().equals(absolute), "menu binds actual world tile");
                h.assertValueEqual(menu.slots.size(), 39, "three source machine slots plus36player");
                int[] x = {13, 143, 42}, y = {49, 49, 80};
                for (int slot = 0; slot < 3; slot++) {
                    h.assertValueEqual(menu.slots.get(slot).getContainerSlot(), slot, "source raw/menu slot order");
                    h.assertValueEqual(menu.slots.get(slot).x, x[slot], "source slot X");
                    h.assertValueEqual(menu.slots.get(slot).y, y[slot], "source slot Y");
                }
                p.closeContainer(); level.removeBlock(absolute, false);
            }
            h.succeed();
        } finally { actors.close(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_gui_transfer_mode_buttons_and_reach_fences(GameTestHelper h) {
        var actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h);
        var p = actors.player();
        try {
            var tile = former(h); near(p, tile.getBlockPos());
            var hit = new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()), Direction.NORTH, tile.getBlockPos(), false);
            tile.getBlockState().useWithoutItem(h.getLevel(), p, hit);
            var menu = (ClassicMetalFormerMenu) p.containerMenu;
            h.assertTrue(menu.slots.get(0).mayPlace(s(Items.IRON_INGOT)), "source GUI accepts known input");
            h.assertFalse(menu.slots.get(0).mayPlace(s(Items.DIRT)), "source GUI rejects unknown input");
            h.assertTrue(menu.slots.get(0).mayPlace(s(ClassicMaterials.DATA_CHIP.get())), "GUI checks all modes by item identity");
            h.assertFalse(menu.slots.get(1).mayPlace(s(ClassicMaterials.REINFORCED_IRON_PLATE.get())), "source GUI output rejects placement");
            h.assertTrue(menu.slots.get(2).mayPlace(s(ClassicEnergyItems.ENERGY_UNIT.get())), "native IF battery allowed");
            h.assertTrue(menu.slots.get(2).mayPlace(s(AcademyCraft.DEVELOPER.get())), "source portable IF battery allowed");
            h.assertFalse(menu.slots.get(2).mayPlace(s(Items.DIAMOND)), "unsupported GUI battery rejected");
            p.getInventory().clearContent(); p.getInventory().setItem(0, s(ClassicEnergyItems.ENERGY_UNIT.get()));
            h.assertFalse(menu.quickMoveStack(p, 3).isEmpty(), "real hotbar shift to source battery");
            h.assertTrue(tile.getItem(2).is(ClassicEnergyItems.ENERGY_UNIT.get()), "battery transfer reaches actual raw slot2");
            p.getInventory().setItem(1, s(Items.IRON_INGOT));
            h.assertFalse(menu.quickMoveStack(p, 4).isEmpty(), "real hotbar shift known input");
            h.assertTrue(tile.getItem(0).is(Items.IRON_INGOT), "input transfer reaches actual raw slot0");
            p.getInventory().setItem(2, s(Items.DIRT));
            h.assertTrue(menu.quickMoveStack(p, 5).isEmpty() && p.getInventory().getItem(2).is(Items.DIRT), "unsupported shift stays in player inventory");
            for (int slot : new int[]{0, 2}) h.assertFalse(menu.quickMoveStack(p, slot).isEmpty(), "source machine shift returns actual stack");
            tile.setItem(1, s(ClassicMaterials.REINFORCED_IRON_PLATE.get()));
            h.assertFalse(menu.quickMoveStack(p, 1).isEmpty(), "real output shift extraction");
            h.assertTrue(tile.isEmpty(), "all three source slots transfer out");
            h.assertTrue(menu.clickMenuButton(p, 0) && tile.mode() == ClassicMetalFormerWork.Mode.REFINE, "previous PLATE wraps REFINE");
            h.assertTrue(menu.clickMenuButton(p, 1) && tile.mode() == ClassicMetalFormerWork.Mode.PLATE, "next REFINE wraps PLATE");
            h.assertTrue(menu.clickMenuButton(p, 1) && tile.mode() == ClassicMetalFormerWork.Mode.INCISE, "next source mode");
            h.assertFalse(menu.clickMenuButton(p, 2), "invalid button rejected");
            tile.injectEnergy(123.625); equal(h, 123.625, menu.energy(), "native menu fractional four-word IF data");
            p.closeContainer();
            h.assertFalse(menu.clickMenuButton(p, 1), "closed menu cannot change machine mode");
            tile.getBlockState().useWithoutItem(h.getLevel(), p, hit); var fresh = (ClassicMetalFormerMenu) p.containerMenu;
            p.moveTo(tile.getBlockPos().getX() + 20, tile.getBlockPos().getY(), tile.getBlockPos().getZ(), 0, 0);
            h.assertFalse(fresh.stillValid(p) || fresh.clickMenuButton(p, 1), "out-of-reach mode change rejected");
            h.assertTrue(tile.mode() == ClassicMetalFormerWork.Mode.INCISE, "rejected actions preserve real mode");
            h.succeed();
        } finally { actors.close(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_sided_handlers_preserve_literal_inventory_rules(GameTestHelper h) {
        var tile = former(h); var level = h.getLevel(); var pos = tile.getBlockPos();
        h.assertTrue(Arrays.equals(tile.getSlotsForFace(Direction.UP), new int[]{0}), "source top input only");
        h.assertTrue(Arrays.equals(tile.getSlotsForFace(Direction.DOWN), new int[]{1, 2}), "source bottom output then battery");
        for (var side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
            h.assertTrue(Arrays.equals(tile.getSlotsForFace(side), new int[]{2}), "source horizontal battery only");
        var top = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        var bottom = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        var side = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.NORTH);
        var raw = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        h.assertTrue(top != null && bottom != null && side != null && raw != null, "actual native inventory capability registration");
        h.assertValueEqual(top.getSlots(), 1, "native top slot mapping");
        h.assertValueEqual(bottom.getSlots(), 2, "native bottom slot mapping");
        h.assertValueEqual(side.getSlots(), 1, "native side slot mapping");
        h.assertValueEqual(raw.getSlots(), 3, "native unsided inventory mapping");
        h.assertTrue(top.insertItem(0, s(Items.DIRT), false).isEmpty(), "literal source inherited permissive input insertion");
        h.assertTrue(tile.getItem(0).is(Items.DIRT) && top.extractItem(0, 1, false).isEmpty(), "top extraction denied even for inserted item");
        h.assertTrue(side.insertItem(0, s(Items.DIAMOND), false).isEmpty(), "literal source inherited permissive sided battery insertion");
        h.assertTrue(side.extractItem(0, 1, false).isEmpty(), "horizontal extraction denied");
        h.assertTrue(bottom.insertItem(0, s(Items.COBBLESTONE), true).isEmpty() && tile.getItem(1).isEmpty(), "native bottom insertion simulation has no mutation");
        h.assertTrue(bottom.insertItem(0, s(Items.COBBLESTONE), false).isEmpty(), "literal source bottom output insertion allowed");
        h.assertTrue(bottom.extractItem(0, 1, false).is(Items.COBBLESTONE), "bottom output extraction uses native raw slot1");
        h.assertTrue(bottom.extractItem(1, 1, false).is(Items.DIAMOND), "bottom battery extraction uses native raw slot2");
        equal(h, 0, tile.getEnergy(), "unsupported battery never supplies power");
        tick(h, tile, 10); equal(h, 0, tile.getEnergy(), "unknown input scans cannot invent IF");
        h.assertTrue(tile.getItem(0).is(Items.DIRT) && !tile.isWorking(), "native permissive automation does not invent unsupported recipe");
        level.removeBlock(pos, false);
        h.assertFalse(tile.available(), "removed real receiver becomes unavailable");
        equal(h, 17, tile.injectEnergy(17), "stale receiver rejects IF injection");
        equal(h, 0, tile.pullEnergy(17), "stale receiver cannot supply IF");
        h.assertFalse(tile.canPlaceItemThroughFace(0, s(Items.IRON_INGOT), Direction.UP)
                || tile.canTakeItemThroughFace(1, s(Items.COBBLESTONE), Direction.DOWN), "removed sided endpoint fails closed");
        h.assertTrue(top.insertItem(0, s(Items.IRON_INGOT), false).is(Items.IRON_INGOT), "retained native sided handler cannot insert into removed tile");
        h.succeed();
    }

    // Independent literal source cases, not generated from the port's descriptor table.
    private record SourceJob(ClassicMetalFormerWork.Mode mode, String input, int inputs, String output, int outputs) {}
    private static final List<SourceJob> SOURCE_JOBS = List.of(
            new SourceJob(ClassicMetalFormerWork.Mode.INCISE, "imag_silicon_ingot", 1, "wafer", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.INCISE, "wafer", 1, "imag_silicon_piece", 4),
            new SourceJob(ClassicMetalFormerWork.Mode.ETCH, "data_chip", 1, "calc_chip", 1),
            new SourceJob(ClassicMetalFormerWork.Mode.PLATE, "minecraft:iron_ingot", 1, "reinforced_iron_plate", 1),
            new SourceJob(ClassicMetalFormerWork.Mode.PLATE, "constraint_ingot", 1, "constraint_plate", 1),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "imag_silicon_ore", 1, "imag_silicon_ingot", 4),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "constraint_metal_ore", 1, "constraint_ingot", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "reso_crystal_ore", 1, "reso_crystal", 3),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "crystal_ore", 1, "crystal_low", 4),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:gold_ore", 1, "minecraft:gold_ingot", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:iron_ore", 1, "minecraft:iron_ingot", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:emerald_ore", 1, "minecraft:emerald", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:nether_quartz_ore", 1, "minecraft:quartz", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:diamond_ore", 1, "minecraft:diamond", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:redstone_ore", 1, "minecraft:redstone_block", 1),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:lapis_ore", 1, "minecraft:lapis_lazuli", 12),
            new SourceJob(ClassicMetalFormerWork.Mode.REFINE, "minecraft:coal_ore", 1, "minecraft:coal", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.INCISE, "reinforced_iron_plate", 1, "needle", 6),
            new SourceJob(ClassicMetalFormerWork.Mode.INCISE, "minecraft:rail", 1, "needle", 2),
            new SourceJob(ClassicMetalFormerWork.Mode.PLATE, "reinforced_iron_plate", 2, "coin", 3),
            new SourceJob(ClassicMetalFormerWork.Mode.ETCH, "wafer", 1, "silbarn", 1));

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_all_twenty_one_source_jobs_exact_counts_and_finite_cost(GameTestHelper h) {
        var tile = former(h); var initial = tile.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertValueEqual(ClassicMetalFormerRecipes.builtIns().size(), 21, "source21 unconditional recipes");
        for (var job : SOURCE_JOBS) {
            tile.loadWithComponents(initial.copy(), h.getLevel().registryAccess());
            tile.injectEnergy(3000); // Declared bounded native IF seed for each independent source job.
            mode(tile, job.mode()); tile.setItem(0, s(job.input(), job.inputs() + 1));
            tick(h, tile, 4);
            h.assertFalse(tile.isWorking(), "source no acquisition before fifth idle tick " + job.input());
            equal(h, 3000, tile.getEnergy(), "idle source scan has no IF debit");
            tick(h, tile, 1);
            h.assertTrue(tile.isWorking() && tile.workCounter() == 0, "fifth idle tick acquires real recipe");
            tick(h, tile, 59);
            h.assertTrue(tile.isWorking() && tile.getItem(1).isEmpty(), "source59work ticks produce nothing");
            h.assertValueEqual(tile.getItem(0).getCount(), job.inputs() + 1, "source input consumption is atomic at tick60");
            equal(h, 59D / 60, tile.workProgress(), "source real fractional work progress");
            tick(h, tile, 1);
            h.assertTrue(tile.getItem(1).is(item(job.output())), "genuine native source output " + job.output());
            h.assertValueEqual(tile.getItem(1).getCount(), job.outputs(), "literal source yield " + job.output());
            h.assertValueEqual(tile.getItem(0).getCount(), 1, "literal source input count " + job.input());
            equal(h, 2202, tile.getEnergy(), "real sixty13.3IF debits total798");
            h.assertTrue(!tile.isWorking() && tile.workCounter() == 0 && tile.workProgress() == 0, "source completion resets work/scan counter");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_conditional_tags_furnace_yield_and_modern_refining(GameTestHelper h) {
        var recipes = ClassicMetalFormerRecipes.all(h.getLevel());
        // Modern variants refine like their stone-era source ore.
        refines(h, recipes, Items.DEEPSLATE_IRON_ORE, Items.IRON_INGOT, 2);
        refines(h, recipes, Items.DEEPSLATE_GOLD_ORE, Items.GOLD_INGOT, 2);
        refines(h, recipes, Items.NETHER_GOLD_ORE, Items.GOLD_INGOT, 2);
        refines(h, recipes, Items.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND, 2);
        refines(h, recipes, Items.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE_BLOCK, 1);
        refines(h, recipes, Items.DEEPSLATE_LAPIS_ORE, Items.LAPIS_LAZULI, 12);
        refines(h, recipes, Items.DEEPSLATE_COAL_ORE, Items.COAL, 2);
        refines(h, recipes, Items.DEEPSLATE_EMERALD_ORE, Items.EMERALD, 2);
        // Raw ores triple the furnace yield; raw storage blocks count as nine raw ores.
        refines(h, recipes, Items.RAW_IRON, Items.IRON_INGOT, 3);
        refines(h, recipes, Items.RAW_GOLD, Items.GOLD_INGOT, 3);
        refines(h, recipes, Items.RAW_COPPER, Items.COPPER_INGOT, 3);
        refines(h, recipes, Items.RAW_IRON_BLOCK, Items.IRON_INGOT, 27);
        refines(h, recipes, Items.RAW_GOLD_BLOCK, Items.GOLD_INGOT, 27);
        refines(h, recipes, Items.RAW_COPPER_BLOCK, Items.COPPER_INGOT, 27);
        // Built-in source recipes keep their indexes and yields.
        h.assertValueEqual(ClassicMetalFormerRecipes.find(recipes, s(Items.IRON_ORE), ClassicMetalFormerWork.Mode.REFINE), 10, "source iron recipe index");
        h.assertTrue(ClassicMetalFormerRecipes.find(recipes, s(Items.RAW_IRON), ClassicMetalFormerWork.Mode.PLATE) < 0, "raw ore only refines");
        for (String metal : new String[]{"copper", "tin", "lead", "platinum", "silver", "nickel"}) {
            var ingots = tagged("ingots/" + metal); var ores = tagged("ores/" + metal);
            if (ingots.isEmpty() || ores.isEmpty()) continue; // Source condition: absent dictionary side registers nothing.
            var input = new SingleRecipeInput(s(ores.getFirst()));
            var smelt = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, h.getLevel());
            if (smelt.isEmpty()) continue; // Source condition: first dictionary ore needs a furnace result.
            var result = smelt.get().value().assemble(input, h.getLevel().registryAccess());
            if (result.isEmpty()) continue;
            int expected = result.getCount() < 32 ? result.getCount() * 2 : 64;
            for (var ore : ores) {
                int selected = ClassicMetalFormerRecipes.find(recipes, s(ore), ClassicMetalFormerWork.Mode.REFINE);
                h.assertTrue(selected >= 0, "native conditional tagged ore admitted " + metal);
                var recipe = recipes.get(selected);
                h.assertTrue(recipe.output().is(ingots.getFirst()), "source first tagged ingot output " + metal);
                h.assertValueEqual(recipe.output().getCount(), expected, "source doubled first-ore furnace yield " + metal);
            }
        }
        h.succeed();
    }
    private static void refines(GameTestHelper h, List<ClassicMetalFormerRecipes.Recipe> recipes, Item input, Item output, int count) {
        int selected = ClassicMetalFormerRecipes.find(recipes, s(input), ClassicMetalFormerWork.Mode.REFINE);
        h.assertTrue(selected >= 0, "refine admits " + BuiltInRegistries.ITEM.getKey(input));
        h.assertTrue(recipes.get(selected).output().is(output), "refine output " + BuiltInRegistries.ITEM.getKey(input));
        h.assertValueEqual(recipes.get(selected).output().getCount(), count, "refine yield " + BuiltInRegistries.ITEM.getKey(input));
    }
    private static List<Item> tagged(String value) {
        var result = new ArrayList<Item>();
        BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", value)))
                .ifPresent(holders -> holders.forEach(holder -> result.add(holder.value())));
        return result;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_blocked_output_changed_input_mode_and_partial_power_debits(GameTestHelper h) {
        var tile = former(h); var initial = tile.saveWithFullMetadata(h.getLevel().registryAccess());
        for (String failure : new String[]{"full", "wrong-output", "changed-input", "removed-input", "mode", "too-few-input"}) {
            tile.loadWithComponents(initial.copy(), h.getLevel().registryAccess()); tile.injectEnergy(1000);
            if (failure.equals("too-few-input")) tile.setItem(0, s("reinforced_iron_plate", 2));
            else tile.setItem(0, s(Items.IRON_INGOT));
            tick(h, tile, 6); h.assertTrue(tile.isWorking() && tile.workCounter() == 1, "one real work tick before mutation");
            switch (failure) {
                case "full" -> tile.setItem(1, s("reinforced_iron_plate", 64));
                case "wrong-output" -> tile.setItem(1, s(Items.DIAMOND));
                case "changed-input" -> tile.setItem(0, s(Items.GOLD_INGOT));
                case "removed-input" -> tile.removeItem(0, 1);
                case "mode" -> tile.cycleMode(1);
                case "too-few-input" -> tile.removeItem(0, 1);
                default -> throw new AssertionError(failure);
            }
            h.assertTrue(tile.isActionBlocked(), "actual source action blocked " + failure);
            var input = tile.getItem(0).copy(); var output = tile.getItem(1).copy();
            tick(h, tile, 1);
            equal(h, 973.4, tile.getEnergy(), "blocked attempt still pays13.3IF after first work debit " + failure);
            h.assertTrue(!tile.isWorking() && tile.workCounter() == 0, "blocked source work aborts/reset " + failure);
            h.assertTrue(ItemStack.matches(input, tile.getItem(0)) && ItemStack.matches(output, tile.getItem(1)), "blocked attempt has no input/output transaction " + failure);
        }
        tile.loadWithComponents(initial.copy(), h.getLevel().registryAccess()); tile.injectEnergy(7.125);
        tile.setItem(0, s(Items.IRON_INGOT)); tick(h, tile, 5);
        h.assertTrue(tile.isWorking(), "source scan selects recipe without checking IF");
        tick(h, tile, 1); equal(h, 0, tile.getEnergy(), "source partial7.125IF pull is still consumed");
        h.assertTrue(!tile.isWorking() && tile.getItem(0).is(Items.IRON_INGOT) && tile.getItem(1).isEmpty(), "partial power abort has no free output");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_battery_bandwidth_work_before_refill_and_finite_capacity(GameTestHelper h) {
        var tile = former(h); var initial = tile.saveWithFullMetadata(h.getLevel().registryAccess());
        var unit = s(ClassicEnergyItems.ENERGY_UNIT.get()); ClassicEnergyItemHelper.setEnergy(unit, 123.625);
        tile.setItem(2, unit); tick(h, tile, 1);
        equal(h, 20, tile.getEnergy(), "real source unit bandwidth20");
        equal(h, 103.625, ClassicEnergyItemHelper.getEnergy(unit), "finite unit supplies only accepted20IF");
        tile.loadWithComponents(initial.copy(), h.getLevel().registryAccess());
        var portable = s(AcademyCraft.DEVELOPER.get()); ClassicEnergyItemHelper.setEnergy(portable, 123.625);
        tile.setItem(2, portable); tick(h, tile, 1);
        equal(h, 50, tile.getEnergy(), "source portable and receiver bandwidth50");
        equal(h, 73.625, ClassicEnergyItemHelper.getEnergy(portable), "portable finite debit");
        tile.injectEnergy(2949.5); tick(h, tile, 1);
        equal(h, 3000, tile.getEnergy(), "source native buffer cap3000");
        equal(h, 73.125, ClassicEnergyItemHelper.getEnergy(portable), "only actual0.5capacity accepted from battery");
        tick(h, tile, 1); equal(h, 73.125, ClassicEnergyItemHelper.getEnergy(portable), "full receiver cannot debit battery");
        tile.loadWithComponents(initial.copy(), h.getLevel().registryAccess());
        tile.setItem(0, s(Items.IRON_INGOT)); tick(h, tile, 5);
        var late = s(ClassicEnergyItems.ENERGY_UNIT.get()); ClassicEnergyItemHelper.setEnergy(late, 123.625); tile.setItem(2, late);
        tick(h, tile, 1);
        h.assertTrue(!tile.isWorking() && tile.workCounter() == 0, "source zero-energy work attempt aborts before battery refill");
        equal(h, 20, tile.getEnergy(), "same native tick refills after aborted work");
        equal(h, 103.625, ClassicEnergyItemHelper.getEnergy(late), "late battery debited exactly20");
        tick(h, tile, 5);
        h.assertTrue(tile.isWorking() && tile.workCounter() == 0, "source new acquisition requires five idle ticks after abort");
        tick(h, tile, 1);
        h.assertValueEqual(tile.workCounter(), 1, "refilled job advances only next work tick");
        equal(h, 110.325, tile.getEnergy(), "finite123.625 battery less one13.3work debit");
        equal(h, 0, ClassicEnergyItemHelper.getEnergy(late), "bounded battery really exhausted");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void former_native_cold_block_entity_load_mode_inventory_progress_and_single_loot(GameTestHelper h) {
        var tile = former(h); var level = h.getLevel(); var pos = tile.getBlockPos();
        tile.injectEnergy(2000); tile.pullEnergy(.375);
        mode(tile, ClassicMetalFormerWork.Mode.INCISE); tile.setItem(0, s("wafer", 2));
        var unit = s(ClassicEnergyItems.ENERGY_UNIT.get()); ClassicEnergyItemHelper.setEnergy(unit, 456.375);
        unit.set(DataComponents.CUSTOM_NAME, Component.literal("Declared finite former battery"));
        tick(h, tile, 16); // five acquisition ticks and11real work debits before saving.
        tile.setItem(2, unit); tile.setItem(1, s("imag_silicon_piece", 3));
        equal(h, 11D / 60, tile.workProgress(), "real native pre-save partial work");
        double expected = tile.getEnergy(); var tag = tile.saveWithFullMetadata(level.registryAccess());
        h.assertTrue(tag.getString("id").equals("academy:metal_former") && tag.getInt("mode") == 1, "native persisted identity and source mode");
        h.assertFalse(tag.contains("counter") || tag.contains("current") || tag.contains("working"), "source transient work fields omitted from disk payload");
        var restored = BlockEntity.loadStatic(pos, tile.getBlockState(), tag.copy(), level.registryAccess());
        h.assertTrue(restored instanceof ClassicMetalFormerBlockEntity && restored != tile, "native registry loader constructs a fresh actual BE");
        level.removeBlockEntity(pos); level.setBlockEntity(restored);
        var loaded = (ClassicMetalFormerBlockEntity) restored;
        h.assertTrue(level.getBlockEntity(pos) == loaded && loaded.available(), "fresh native loaded BE installed in actual world");
        equal(h, expected, loaded.getEnergy(), "fractional IF survives genuine new BE load");
        equal(h, 456.375, ClassicEnergyItemHelper.getEnergy(loaded.getItem(2)), "finite native battery components survive load");
        h.assertTrue(loaded.getItem(2).getHoverName().getString().equals("Declared finite former battery"), "unrelated item component persists");
        h.assertTrue(loaded.mode() == ClassicMetalFormerWork.Mode.INCISE && !loaded.isWorking()
                && loaded.workCounter() == 0 && loaded.workProgress() == 0, "source mode persists while work/scan state resets");
        h.assertTrue(loaded.getItem(0).is(item("wafer")) && loaded.getItem(0).getCount() == 2
                && loaded.getItem(1).is(item("imag_silicon_piece")) && loaded.getItem(1).getCount() == 3, "native input/output contents survive");
        equal(h, 0, tile.getEnergy(), "old removed native BE cannot expose retained IF");
        h.assertTrue(level.getCapability(MachineDevelopers.IMAG_FLUX, pos, Direction.UP) == loaded, "native capability resolves replacement BE");
        var savedBattery = loaded.removeItem(2, 1); tick(h, loaded, 5); tick(h, loaded, 59);
        h.assertValueEqual(loaded.getItem(1).getCount(), 3, "cold reload cannot resume old11ticks or complete after59newworkticks");
        tick(h, loaded, 1);
        h.assertValueEqual(loaded.getItem(0).getCount(), 1, "only one new source input consumed");
        h.assertValueEqual(loaded.getItem(1).getCount(), 7, "new complete job merges actual source output");
        equal(h, expected - 798, loaded.getEnergy(), "fresh complete job requires all798IF after reload");
        loaded.setItem(2, savedBattery);
        // Malformed data is an intentional compatibility fence, not an upstream behavior change.
        var malformed = loaded.saveWithFullMetadata(level.registryAccess()); malformed.putDouble("energy", Double.NaN); malformed.putInt("mode", 99);
        var sanitized = (ClassicMetalFormerBlockEntity) BlockEntity.loadStatic(pos, loaded.getBlockState(), malformed, level.registryAccess());
        var sanitizedTag = sanitized.saveWithFullMetadata(level.registryAccess());
        equal(h, 0, sanitizedTag.getDouble("energy"), "malformed persisted IF finite");
        h.assertTrue(sanitized.mode() == ClassicMetalFormerWork.Mode.PLATE, "malformed source mode safely falls back");
        level.destroyBlock(pos, true);
        int machines = 0, wafers = 0, pieces = 0, batteries = 0;
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
            var stack = entity.getItem();
            if (stack.is(ClassicMetalFormer.ITEM.get())) machines += stack.getCount();
            if (stack.is(item("wafer"))) wafers += stack.getCount();
            if (stack.is(item("imag_silicon_piece"))) pieces += stack.getCount();
            if (stack.is(ClassicEnergyItems.ENERGY_UNIT.get())) {
                batteries += stack.getCount(); equal(h, 456.375, ClassicEnergyItemHelper.getEnergy(stack), "actual loot keeps finite battery payload");
            }
        }
        h.assertValueEqual(machines, 1, "one genuine former block loot");
        h.assertValueEqual(wafers, 1, "remaining input drops exactlyonce");
        h.assertValueEqual(pieces, 7, "actual merged output drops exactlyonce");
        h.assertValueEqual(batteries, 1, "one native battery drops exactlyonce");
        h.assertFalse(loaded.available(), "destroyed cold-loaded receiver unavailable");
        equal(h, 23, loaded.injectEnergy(23), "retained destroyed receiver returns all IF");
        h.succeed();
    }

    /** Declared finite source-material grids and vanilla shears are supplied; devices,
     * all IF and both machine products are genuinely crafted/generated/processed here. */
    @GameTest(template = TEMPLATE, batch = "academy_metal_former_acquisition", timeoutTicks = 20)
    public static void former_native_crafted_solar_battery_and_basic_relay_earn_plate_then_needles(GameTestHelper h) {
        var p = player(h); var level = h.getLevel();
        long oldTime = level.getDayTime(); boolean oldRain = level.isRaining(), oldThunder = level.isThundering();
        ClassicWirelessGraph graph = null; ClassicWirelessGraph.Pos nodePos = null, solarPos = null, formerPos = null;
        try {
            var formerItem = craft(h, "metal_former_26", 3, 3,
                    e(), s(Items.SHEARS), e(), s(ClassicMaterials.CALC_CHIP.get()), s(ClassicMaterials.MACHINE_FRAME.get().asItem()), s(ClassicMaterials.CALC_CHIP.get()),
                    s(ClassicMaterials.CONSTRAINT_PLATE.get()), s(ClassicFusion.MATTER_UNIT.get()), s(ClassicMaterials.CONSTRAINT_PLATE.get()));
            h.assertTrue(formerItem.is(ClassicMetalFormer.ITEM.get()) && formerItem.getCount() == 1, "actual source26MetalFormer craft");
            var solarItem = craft(h, "solar_gen_09", 3, 3,
                    s(Items.GLASS_PANE), s(Items.GLASS_PANE), s(Items.GLASS_PANE), e(), s(ClassicMaterials.WAFER.get()), e(),
                    s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()), s(ClassicMaterials.MACHINE_FRAME.get().asItem()), s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()));
            var nodeItem = craft(h, "wireless_node_basic_14", 3, 3,
                    e(), s(ClassicMaterials.CALC_CHIP.get()), e(), s(Items.IRON_INGOT), s(ClassicMaterials.MACHINE_FRAME.get().asItem()), s(Items.IRON_INGOT),
                    s(ClassicMaterials.CRYSTAL_LOW.get()), s(ClassicMaterials.RESO_CRYSTAL.get()), s(ClassicMaterials.CRYSTAL_LOW.get()));
            var unit = craft(h, "energy_unit_18", 3, 3,
                    e(), s(ClassicMaterials.CONSTRAINT_PLATE.get()), e(), s(ClassicMaterials.CONSTRAINT_PLATE.get()), s(ClassicMaterials.CRYSTAL_LOW.get()), s(ClassicMaterials.CONSTRAINT_PLATE.get()),
                    e(), s(ClassicMaterials.DATA_CHIP.get()), e());
            equal(h, 0, ClassicEnergyItemHelper.getEnergy(unit), "actual crafted source battery starts empty");
            place(h, p, formerItem, BASE); place(h, p, solarItem, SOLAR); place(h, p, nodeItem, NODE);
            var tile = (ClassicMetalFormerBlockEntity) h.getBlockEntity(BASE);
            var solar = (ClassicSolarBlockEntity) h.getBlockEntity(SOLAR);
            var node = (ClassicWirelessNodeBlockEntity) h.getBlockEntity(NODE);
            equal(h, 0, tile.getEnergy() + solar.getEnergy() + node.getEnergy(), "all genuine crafted devices start empty");
            for (int y = solar.getBlockPos().getY() + 1; y < level.getMaxBuildHeight(); y++)
                level.setBlock(new BlockPos(solar.getBlockPos().getX(), y, solar.getBlockPos().getZ()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setDayTime(1000); level.setWeatherParameters(24000, 0, false, false);
            h.assertTrue(solar.status() == ClassicSolarRules.Status.STRONG, "declared real visible-sky daylight fixture");
            solar.setItem(0, unit);
            for (int index = 0; index < 334; index++) ClassicSolarBlockEntity.serverTick(level, solar.getBlockPos(), solar.getBlockState(), solar);
            equal(h, 1002, ClassicEnergyItemHelper.getEnergy(unit), "native334solar ticks earn1002finiteIF");
            equal(h, 0, solar.getEnergy(), "native empty source battery accepts each3IF tick");
            tile.setItem(2, solar.removeItem(0, 1)); tile.setItem(0, s(Items.IRON_INGOT));
            tick(h, tile, 65);
            h.assertTrue(tile.getItem(1).is(ClassicMaterials.REINFORCED_IRON_PLATE.get()) && tile.getItem(1).getCount() == 1,
                    "real source battery path earns one plate from one declarediron");
            equal(h, 204, tile.getEnergy(), "generated1002 less first798IFwork");
            equal(h, 0, ClassicEnergyItemHelper.getEnergy(tile.getItem(2)), "actual battery completely debited");
            tile.removeItem(2, 1); tile.setItem(0, tile.removeItem(1, 1)); tile.cycleMode(1);
            graph = ClassicWirelessSavedData.get(level).graph();
            nodePos = ClassicWirelessSavedData.pos(node.getBlockPos()); solarPos = ClassicWirelessSavedData.pos(solar.getBlockPos()); formerPos = ClassicWirelessSavedData.pos(tile.getBlockPos());
            var resolver = new ClassicWirelessSavedData.NativeResolver(level);
            h.assertTrue(resolver.receiver(formerPos) == tile, "real graph resolves native former IF capability");
            h.assertTrue(graph.linkGenerator(nodePos, solarPos, "", true) && graph.linkReceiver(nodePos, formerPos, "", true),
                    "actual source basic relay accepts crafted generator/receiver");
            for (int index = 0; index < 200; index++) {
                ClassicSolarBlockEntity.serverTick(level, solar.getBlockPos(), solar.getBlockState(), solar);
                ClassicWirelessNodeBlockEntity.serverTick(level, node.getBlockPos(), node.getBlockState(), node);
                graph.tick();
            }
            equal(h, 804, tile.getEnergy(), "real source200solar/node ticks deliver600IF to existing204");
            equal(h, 0, solar.getEnergy() + node.getEnergy(), "wireless delivery conserves generated IF");
            tick(h, tile, 65);
            h.assertTrue(tile.getItem(0).isEmpty() && tile.getItem(1).is(item("needle")) && tile.getItem(1).getCount() == 6,
                    "actual earned plate incises into sixsource needles");
            equal(h, 6, tile.getEnergy() + solar.getEnergy() + node.getEnergy(), "all1602generatedIF less two798jobs leaves6");
            level.destroyBlock(tile.getBlockPos(), true); graph.tick();
            h.assertTrue(resolver.receiver(formerPos) == null && graph.nodeForReceiver(formerPos) == null, "removed native former cannot retain graph energy endpoint");
            h.succeed();
        } finally {
            if (graph != null) {
                if (formerPos != null) graph.unlinkReceiver(formerPos);
                if (solarPos != null) graph.unlinkGenerator(solarPos);
            }
            close(p); level.setDayTime(oldTime); level.setWeatherParameters(0, 0, oldRain, oldThunder);
        }
    }
}
