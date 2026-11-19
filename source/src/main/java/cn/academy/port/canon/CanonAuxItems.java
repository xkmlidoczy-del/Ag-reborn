package cn.academy.port.canon;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Auxiliary items for the canon ability systems, in the role the coin and steel needle play for the original four:
 * physical things an esper carries and channels.
 */
public final class CanonAuxItems {
    public static final int FLASK_CHARGES = 4;
    private static final String MARKER_KEY = "academy:float_marker";
    public static final String CRYSTAL_BOOST_LOCKED_KEY = "academy:crystal_boost_locked";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "academy");

    public static final DeferredItem<Item> MENTAL_REMOTE = item("mental_remote", 1);
    public static final DeferredItem<Item> DARK_MATTER_CRYSTAL = ITEMS.register("dark_matter_crystal",
            () -> new DarkMatterCrystalItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> NITROGEN_CANISTER = item("nitrogen_canister", 16);
    public static final DeferredItem<Item> AERO_PRESSURE_CHIP = item("aero_pressure_chip", 16);
    public static final DeferredItem<Item> WATER_FLASK = ITEMS.register("water_flask",
            () -> new WaterFlask(new Item.Properties().durability(FLASK_CHARGES).component(DataComponents.DAMAGE, FLASK_CHARGES)));
    public static final DeferredItem<Item> BUOYANCY_FLOAT = ITEMS.register("buoyancy_float",
            () -> new FloatMarker(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ORIGAMI_PAPER = item("origami_paper", 64);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DarkMatterDuplicationRecipe>> DARK_MATTER_DUPLICATION =
            RECIPE_SERIALIZERS.register("dark_matter_duplication",
                    () -> new SimpleCraftingRecipeSerializer<>(DarkMatterDuplicationRecipe::new));

    private CanonAuxItems() {}

    static class AuxItem extends Item {
        private final String key;
        AuxItem(String key, Properties properties) { super(properties); this.key = key; }
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.academy." + key + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    static final class DarkMatterCrystalItem extends AuxItem {
        DarkMatterCrystalItem(Properties properties) { super("dark_matter_crystal", properties); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
            if (player instanceof ServerPlayer sp) {
                boolean nextLocked = !crystalBoostLocked(sp);
                if (nextLocked) sp.getPersistentData().putBoolean(CRYSTAL_BOOST_LOCKED_KEY, true);
                else sp.getPersistentData().remove(CRYSTAL_BOOST_LOCKED_KEY);
                sp.displayClientMessage(Component.translatable(nextLocked
                        ? "ac.canon.crystal_boost.locked"
                        : "ac.canon.crystal_boost.unlocked").withStyle(nextLocked ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
                level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, nextLocked ? 0.85f : 1.35f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    private static DeferredItem<Item> item(String name, int stack) {
        return ITEMS.register(name, () -> new AuxItem(name, new Item.Properties().stacksTo(stack)));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        bus.addListener(CanonAuxItems::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(MENTAL_REMOTE); event.accept(DARK_MATTER_CRYSTAL); event.accept(NITROGEN_CANISTER); event.accept(AERO_PRESSURE_CHIP);
            event.accept(WATER_FLASK); event.accept(BUOYANCY_FLOAT); event.accept(ORIGAMI_PAPER);
        }
    }

    // ------------------------------------------------------------------ helpers shared by the skill hooks
    /** True when the stack is in either hand. Skills never read or write other inventory slots except through {@link #consume}. */
    public static boolean inHands(ServerPlayer p, DeferredItem<Item> item) {
        return p != null && (p.getMainHandItem().is(item.get()) || p.getOffhandItem().is(item.get()));
    }

    public static boolean inInventory(ServerPlayer p, DeferredItem<Item> item) {
        if (p == null) return false;
        if (inHands(p, item)) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item.get())) return true;
        return false;
    }

    /** Takes one item from the hands, then the rest of the inventory. Creative players keep it. Returns whether one was available. */
    public static boolean consume(ServerPlayer p, DeferredItem<Item> item) {
        if (p == null || item == null) return false;
        if (p.getAbilities().instabuild) return inInventory(p, item) || inHands(p, item);
        for (ItemStack hand : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (hand.is(item.get())) { hand.shrink(1); return true; }
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(item.get())) { stack.shrink(1); return true; }
        }
        return false;
    }

    /** True when the player toggled sneak-use on a Dark Matter Crystal to save crystals for synthesis/crafting. */
    public static boolean crystalBoostLocked(ServerPlayer p) {
        return p != null && p.getPersistentData().getBoolean(CRYSTAL_BOOST_LOCKED_KEY);
    }

    /** Consumes one Dark Matter Crystal for +50% duration boost unless the player locked crystal auto-boost. */
    public static boolean consumeCrystalBoost(ServerPlayer p) {
        return p != null && !crystalBoostLocked(p) && consume(p, DARK_MATTER_CRYSTAL);
    }

    // ------------------------------------------------------------------ water flask: four charges of real or direct-channeled water
    static final class WaterFlask extends AuxItem {
        WaterFlask(Properties properties) { super("water_flask", properties); }
        static int charges(ItemStack s) { return s.is(WATER_FLASK.get()) ? Math.max(0, FLASK_CHARGES - s.getDamageValue()) : 0; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            lines.add(Component.translatable("item.academy.water_flask.charges", charges(stack), FLASK_CHARGES).withStyle(ChatFormatting.AQUA));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK || stack.getDamageValue() == 0) return InteractionResultHolder.pass(stack);
            BlockPos pos = hit.getBlockPos(); var state = level.getBlockState(pos);
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)
                    || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()
                    || !(state.getBlock() instanceof BucketPickup pickup)) return InteractionResultHolder.pass(stack);
            if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
            ItemStack picked = pickup.pickupBlock(player, level, pos, state);
            if (!picked.is(Items.WATER_BUCKET)) return InteractionResultHolder.pass(stack);
            stack.setDamageValue(stack.getDamageValue() - 1);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, .8f, 1.3f);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel(); Player player = context.getPlayer(); ItemStack stack = context.getItemInHand();
            if (player == null || charges(stack) <= 0) return InteractionResult.PASS;
            BlockPos target = context.getClickedPos(); var clicked = level.getBlockState(target);
            BlockPos at = clicked.canBeReplaced() ? target : target.relative(context.getClickedFace());
            var existing = level.getBlockState(at);
            if (!existing.canBeReplaced() || level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at).isSource()) return InteractionResult.PASS;
            if (!level.mayInteract(player, at) || !player.mayUseItemAt(at, context.getClickedFace(), stack)) return InteractionResult.PASS;
            if (level.dimensionType().ultraWarm()) {
                level.playSound(player, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .5f, 2.6f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (level.isClientSide) return InteractionResult.SUCCESS;
            level.setBlock(at, Blocks.WATER.defaultBlockState(), 11);
            level.gameEvent(player, GameEvent.FLUID_PLACE, at);
            level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, .8f, 1.3f);
            if (!player.getAbilities().instabuild) stack.setDamageValue(stack.getDamageValue() + 1);
            return InteractionResult.CONSUME;
        }
    }

    /** True when the player carries a Water Flask with at least 1 stored water charge (or holds one in Creative). */
    public static boolean hasChargedWaterFlask(ServerPlayer p) {
        if (p == null) return false;
        for (ItemStack hand : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (hand.is(WATER_FLASK.get()) && (p.getAbilities().instabuild || WaterFlask.charges(hand) > 0)) return true;
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack at = inv.getItem(i);
            if (at.is(WATER_FLASK.get()) && (p.getAbilities().instabuild || WaterFlask.charges(at) > 0)) return true;
        }
        return false;
    }

    /** Spends 1 charge from a carried Water Flask when Hydro Hand channels water without a world water block nearby. */
    public static boolean spendWaterFlaskCharge(ServerPlayer p) {
        if (p == null) return false;
        for (ItemStack hand : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (hand.is(WATER_FLASK.get()) && (p.getAbilities().instabuild || WaterFlask.charges(hand) > 0)) {
                if (!p.getAbilities().instabuild) hand.setDamageValue(Math.min(FLASK_CHARGES, hand.getDamageValue() + 1));
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.55f, 1.25f);
                return true;
            }
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack at = inv.getItem(i);
            if (at.is(WATER_FLASK.get()) && (p.getAbilities().instabuild || WaterFlask.charges(at) > 0)) {
                if (!p.getAbilities().instabuild) at.setDamageValue(Math.min(FLASK_CHARGES, at.getDamageValue() + 1));
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.55f, 1.25f);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ buoyancy float: one marker per player
    /** Right-click a block to set the marker; use it in the air while sneaking to clear. Stored with the player, so it survives restarts. */
    static final class FloatMarker extends AuxItem {
        FloatMarker(Properties properties) { super("buoyancy_float", properties); }
        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (!(context.getPlayer() instanceof ServerPlayer p)) return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            BlockPos at = context.getClickedPos().relative(context.getClickedFace());
            if (!p.serverLevel().mayInteract(p, at) || p.position().distanceToSqr(Vec3.atCenterOf(at)) > MARKER_PLACE_RANGE * MARKER_PLACE_RANGE) return InteractionResult.FAIL;
            CompoundTag tag = new CompoundTag(); tag.putString("dim", p.serverLevel().dimension().location().toString());
            tag.putInt("x", at.getX()); tag.putInt("y", at.getY()); tag.putInt("z", at.getZ());
            p.getPersistentData().put(MARKER_KEY, tag);
            p.displayClientMessage(Component.translatable("ac.canon.float_marker.set", at.getX(), at.getY(), at.getZ()), true);
            CanonVfx.emit(p, "float_field", p.getEyePosition(), Vec3.atCenterOf(at), CanonVfx.Phase.START, 40, 1.6);
            return InteractionResult.CONSUME;
        }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isShiftKeyDown() || !(player instanceof ServerPlayer p) || !p.getPersistentData().contains(MARKER_KEY)) return InteractionResultHolder.pass(stack);
            CompoundTag old = p.getPersistentData().getCompound(MARKER_KEY);
            Vec3 at = new Vec3(old.getInt("x") + .5, old.getInt("y") + .5, old.getInt("z") + .5);
            p.getPersistentData().remove(MARKER_KEY);
            p.displayClientMessage(Component.translatable("ac.canon.float_marker.cleared"), true);
            CanonVfx.emit(p, "float_field", at, at, CanonVfx.Phase.END, 12, 1.2);
            return InteractionResultHolder.consume(stack);
        }
    }

    public static final double MARKER_PLACE_RANGE = 8, MARKER_REACH = 28;

    /** The player's marker centre when it is in this dimension and within reach, else null. Field skills fall back to the caster. */
    public static Vec3 floatMarker(ServerPlayer p) {
        if (p == null || !p.getPersistentData().contains(MARKER_KEY) || !inInventory(p, BUOYANCY_FLOAT)) return null;
        CompoundTag tag = p.getPersistentData().getCompound(MARKER_KEY);
        if (!tag.getString("dim").equals(p.serverLevel().dimension().location().toString())) return null;
        Vec3 at = new Vec3(tag.getInt("x") + .5, tag.getInt("y") + .5, tag.getInt("z") + .5);
        return at.distanceToSqr(p.position()) <= MARKER_REACH * MARKER_REACH ? at : null;
    }

    /** A paper skill's material sheet: vanilla paper or origami paper. */
    public static boolean isSheet(ItemStack stack) {
        return stack != null && (stack.is(Items.PAPER) || stack.is(ORIGAMI_PAPER.get()));
    }

    /** Dedicated Origami Paper halves multi-sheet consumption (minimum 1) and reinforces constructs. */
    public static boolean isOrigami(ItemStack stack) {
        return stack != null && stack.is(ORIGAMI_PAPER.get());
    }

    public static int sheetCost(ItemStack stack, int baseCost) {
        if (baseCost <= 1) return baseCost;
        return isOrigami(stack) ? Math.max(1, (baseCost + 1) / 2) : baseCost;
    }
}
