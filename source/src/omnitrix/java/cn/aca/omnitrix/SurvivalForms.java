package cn.aca.omnitrix;

import cn.aca.omnitrix.mixin.DispenserAccess;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class SurvivalForms {
    private record Spike(UUID owner, long expires) {}
    private record Burst(BlockPos pos, long at, int left) {}
    private static final class LevelAbilities {
        final Map<BlockPos, Spike> spikes = new HashMap<>();
        final Map<BlockPos, Long> dispensers = new HashMap<>();
        final ArrayDeque<Burst> shots = new ArrayDeque<>();
    }
    private static final Map<ServerLevel, LevelAbilities> LEVELS = new WeakHashMap<>();
    private static final String CART_POWER = "aca_omnitrix_upgrade_until";

    private SurvivalForms() {}

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOW, SurvivalForms::onBlock);
        bus.addListener(EventPriority.LOW, SurvivalForms::onEntity);
        bus.addListener(EventPriority.LOW, SurvivalForms::mineSpeed);
        bus.addListener(EventPriority.LOW, SurvivalForms::harvest);
        bus.addListener(SurvivalForms::levelTick);
    }

    public static boolean serverClassic(Player player, Form form) {
        if (!(player instanceof ServerPlayer) || !player.hasData(Omnitrix.WRIST)) return false;
        Wrist wrist = Omnitrix.wrist(player);
        return wrist.kind() == Kind.CLASSIC && wrist.stage() == Wrist.Stage.ACTIVE && wrist.form() == form;
    }

    static void tick(ServerPlayer player, Wrist wrist, long now) {
        if (wrist.form == Form.HEATBLAST && player.isInWater()) {
            wrist.until = Math.max(now + 1, wrist.until - 3);
            if (now % 8 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + .65,
                        player.getZ(), 13, .35, .63, .35, .025);
                if (now % 16 == 0) WristService.announce(player, SoundEvents.FIRE_EXTINGUISH, .84f);
                wrist.revision++;
                WristNetwork.sync(player);
            }
        }
        if (wrist.form == Form.RIPJAWS) {
            if (player.isInWaterOrRain()) {
                wrist.dryTicks = 0;
                if (player.isEyeInFluid(FluidTags.WATER) && now % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, false, false));
                }
            } else if (++wrist.dryTicks > 600 && wrist.dryTicks % 20 == 0) {
                player.hurt(player.damageSources().dryOut(), 2);
            } else if (wrist.dryTicks == 600) WristService.tell(player, "dry_out");
        }
    }

    private static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (!canModify(player, pos)) return;
        BlockState state = level.getBlockState(pos);
        boolean handled = false;
        if (serverClassic(player, Form.HEATBLAST) && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            handled = smelt(player, pos, state, event.getFace());
        } else if (serverClassic(player, Form.DIAMONDHEAD) && state.is(BlockTags.ANVIL)) {
            handled = repairAnvil(player, event, state);
        } else if (serverClassic(player, Form.UPGRADE) && state.is(Blocks.DISPENSER) && player.getMainHandItem().isEmpty()) {
            LevelAbilities data = data(level);
            data.dispensers.put(pos.immutable(), level.getGameTime() + 600);
            level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + .5, pos.getY() + .65, pos.getZ() + .5, 14, .3, .3, .3, .04);
            WristService.tell(player, "overloaded");
            handled = true;
        }
        if (handled) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean smelt(ServerPlayer player, BlockPos pos, BlockState state, Direction face) {
        ServerLevel level = player.serverLevel();
        ItemStack item = player.getOffhandItem();
        boolean held = item.is(Tags.Items.RAW_MATERIALS) || item.is(Tags.Items.ORES)
                || item.is(Tags.Items.FOODS_RAW_MEAT) || item.is(Tags.Items.FOODS_RAW_FISH);
        if (!held && !state.is(Tags.Blocks.ORES)) return false;
        var input = new SingleRecipeInput(held ? item.copyWithCount(1) : new ItemStack(state.getBlock().asItem()));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
        if (recipe.isEmpty()) return false;
        if (!held) {
            if (!(player.gameMode.isSurvival() || player.isCreative()) || level.getBlockEntity(pos) != null) return false;
            var breaking = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breaking).isCanceled() || !level.removeBlock(pos, false)) return false;
        } else if (!player.getAbilities().instabuild) item.shrink(1);
        ItemStack output = recipe.get().value().assemble(input, level.registryAccess());
        if (!player.getInventory().add(output)) player.drop(output, false);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + .5, pos.getY() + .6, pos.getZ() + .5, 15, .28, .19, .28, .045);
        WristService.announce(player, SoundEvents.FIRECHARGE_USE, 1.1f);
        return true;
    }

    private static boolean repairAnvil(ServerPlayer player, PlayerInteractEvent.RightClickBlock event, BlockState state) {
        if (!player.getMainHandItem().is(Items.AMETHYST_SHARD)) return false;
        Block repaired = state.is(Blocks.DAMAGED_ANVIL) ? Blocks.CHIPPED_ANVIL : state.is(Blocks.CHIPPED_ANVIL) ? Blocks.ANVIL : null;
        if (repaired == null || !replace(player, event.getPos(), repaired.defaultBlockState().setValue(AnvilBlock.FACING,
                state.getValue(AnvilBlock.FACING)), event.getFace())) return false;
        spendShard(player);
        return true;
    }

    private static void onEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (serverClassic(player, Form.DIAMONDHEAD) && event.getTarget() instanceof IronGolem golem
                && player.getMainHandItem().is(Items.AMETHYST_SHARD) && golem.getHealth() < golem.getMaxHealth()) {
            golem.heal(25);
            spendShard(player);
        } else if (serverClassic(player, Form.UPGRADE) && player.getMainHandItem().isEmpty()
                && event.getTarget() instanceof AbstractMinecart cart && cart.getMinecartType() == AbstractMinecart.Type.RIDEABLE
                && (!cart.isVehicle() || cart.hasPassenger(player)) && canModify(player, cart.blockPosition())) {
            cart.getPersistentData().putLong(CART_POWER, player.serverLevel().getGameTime() + 600);
            if (!cart.hasPassenger(player)) player.startRiding(cart);
            WristService.tell(player, "assimilated");
        } else return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static boolean mayRideMachine(Player player, AbstractMinecart cart) {
        return serverClassic(player, Form.UPGRADE) && cart.getPersistentData().getLong(CART_POWER) > cart.level().getGameTime();
    }

    public static void accelerateCart(AbstractMinecart cart, BlockPos pos) {
        if (!(cart.getFirstPassenger() instanceof ServerPlayer player) || !mayRideMachine(player, cart)) return;
        Level level = cart.level();
        BlockState current = level.getBlockState(pos);
        if (!current.is(Blocks.RAIL)) return;
        RailShape axis = current.getValue(RailBlock.SHAPE);
        if (axis != RailShape.EAST_WEST && axis != RailShape.NORTH_SOUTH) return;
        Direction forward = axis == RailShape.EAST_WEST ? Direction.EAST : Direction.SOUTH;
        Vec3 velocity = cart.getDeltaMovement();
        double travel = forward.getStepX() * velocity.x + forward.getStepZ() * velocity.z;
        if (Math.abs(travel) < .04) travel = player.getLookAngle().dot(Vec3.atLowerCornerOf(forward.getNormal()));
        Direction toward = travel < 0 ? forward.getOpposite() : forward;
        // Slow before curves, hills and junctions so the vanilla rail solver can steer the cart.
        for (int distance = 1; distance <= 2; distance++) {
            BlockState ahead = level.getBlockState(pos.relative(toward, distance));
            if (!ahead.is(Blocks.RAIL) || ahead.getValue(RailBlock.SHAPE) != axis) {
                cart.setDeltaMovement(velocity.multiply(.33, 1, .33));
                return;
            }
        }
        double speed = Math.max(.18, Math.min(1.6, Math.abs(travel) + .21));
        cart.setDeltaMovement(toward.getStepX() * speed, 0, toward.getStepZ() * speed);
    }

    public static boolean fastRail(AbstractMinecart cart, BlockPos pos) {
        if (!(cart.getFirstPassenger() instanceof ServerPlayer player) || !mayRideMachine(player, cart)) return false;
        if (!cart.level().getBlockState(pos).is(Blocks.RAIL)) return false;
        return cart.getDeltaMovement().horizontalDistance() > .40;
    }

    public static void queueDispenses(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.DISPENSER)) return;
        LevelAbilities data = LEVELS.get(level);
        if (data != null && data.dispensers.getOrDefault(pos, 0L) > level.getGameTime()) {
            data.shots.add(new Burst(pos.immutable(), level.getGameTime() + 1, 2));
        }
    }

    private static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelAbilities data = LEVELS.get(level);
        if (data == null) return;
        long now = level.getGameTime();
        for (int i = data.shots.size(); i > 0; i--) {
            Burst shot = data.shots.removeFirst();
            if (shot.at > now) data.shots.addLast(shot);
            else if (level.hasChunkAt(shot.pos)) {
                BlockState dispenser = level.getBlockState(shot.pos);
                if (dispenser.is(Blocks.DISPENSER) && data.dispensers.getOrDefault(shot.pos, 0L) > now) {
                    ((DispenserAccess)dispenser.getBlock()).omnitrix$dispense(level, dispenser, shot.pos);
                    if (shot.left > 1) data.shots.addLast(new Burst(shot.pos, now + 1, shot.left - 1));
                }
            }
        }
        if (now % 20 == 0) {
            data.spikes.values().removeIf(spike -> spike.expires <= now);
            data.dispensers.values().removeIf(until -> until <= now);
        }
    }

    static void leaveSpike(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isEmptyBlock(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) return;
        if (replace(player, pos, Omnitrix.BONE_SPIKE.get().defaultBlockState(), Direction.UP)) {
            level.scheduleTick(pos, Omnitrix.BONE_SPIKE.get(), 100);
            data(level).spikes.put(pos.immutable(), new Spike(player.getUUID(), level.getGameTime() + 100));
        }
    }

    static ServerPlayer spikeOwner(ServerLevel level, BlockPos pos) {
        LevelAbilities data = LEVELS.get(level);
        Spike spike = data == null ? null : data.spikes.get(pos);
        if (spike == null || spike.expires <= level.getGameTime()) return null;
        var player = level.getServer().getPlayerList().getPlayer(spike.owner);
        return player != null && player.serverLevel() == level ? player : null;
    }

    private static LevelAbilities data(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new LevelAbilities()); }

    private static boolean replace(ServerPlayer player, BlockPos pos, BlockState replacement, Direction face) {
        ServerLevel level = player.serverLevel();
        if (!canModify(player, pos)) return false;
        var before = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, replacement, Block.UPDATE_CLIENTS)) return false;
        if (EventHooks.onBlockPlace(player, before, face == null ? Direction.UP : face)) {
            before.restore(Block.UPDATE_CLIENTS);
            return false;
        }
        level.updateNeighborsAt(pos, replacement.getBlock());
        return true;
    }

    private static void spendShard(ServerPlayer player) {
        if (!player.getAbilities().instabuild) player.getMainHandItem().shrink(1);
        WristService.announce(player, SoundEvents.AMETHYST_BLOCK_CHIME, .8f);
    }

    private static boolean canModify(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos) && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static void mineSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().getMainHandItem().isEmpty() && canMine(event.getEntity(), event.getState()) && event.getNewSpeed() > 0) {
            float speed = new ItemStack(Items.NETHERITE_PICKAXE).getDestroySpeed(event.getState());
            event.setNewSpeed(event.getNewSpeed() * speed);
        }
    }

    private static void harvest(PlayerEvent.HarvestCheck event) {
        if (event.getEntity().getMainHandItem().isEmpty() && canMine(event.getEntity(), event.getTargetBlock())) {
            event.setCanHarvest(new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(event.getTargetBlock()));
        }
    }

    private static boolean canMine(Player player, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && isClassic(player, Form.DIAMONDHEAD);
    }

    public static boolean isClassic(Player player, Form form) {
        if (!player.level().isClientSide) return serverClassic(player, form);
        return WristNetwork.client != null && WristNetwork.client.isClassic(player, form);
    }

    public static boolean surfaceSprint(Player player) {
        return player.isSprinting() && !player.isShiftKeyDown() && !player.isUnderWater() && isClassic(player, Form.XLR8);
    }
}
