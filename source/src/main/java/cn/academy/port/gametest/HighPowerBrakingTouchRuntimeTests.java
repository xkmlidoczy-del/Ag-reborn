package cn.academy.port.gametest;

import cn.academy.port.highpower.core.BrakingTouchRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW413: connected authenticated input, native Q and projectiles, and actual accepted landing packets. */
@GameTestHolder("academy_braking_touch_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerBrakingTouchRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_braking_touch", ID = "braking_touch";
    private HighPowerBrakingTouchRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {32, 10, 32}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_braking_touch_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void real_q_brakes_once_without_inventory_native_state_or_progression_changes(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.getInventory().setItem(p.getInventory().selected, new ItemStack(Items.IRON_INGOT, 9));
            h.assertTrue(p.drop(false), "actual native Q event drops exactly one inventory item");
            ItemEntity item = f.findQ(p, 1); f.aim(p, item, 1.4);
            item.setPickUpDelay(73); item.fallDistance = 4; item.lifespan = 4321;
            Vec3 inertia = new Vec3(.14, -.23, .31); item.setDeltaMovement(inertia);
            CompoundTag state = nativeItem(item), inventory = inventory(p);
            CompoundTag combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
            double cp = HighPowerResources.cp(p), xp = AbilityStorage.get(p).levelExperience;
            BigDecimal q = HighPowerGrowth.values(p).position(), earned = HighPowerCombat.earnedGrowthPosition(p);
            h.assertTrue(f.start(p), "real certified ordinary Q is selected by server eye ray");
            vector(h, item.getDeltaMovement(), Vec3.ZERO, "one native velocity write brakes all existing motion");
            h.assertTrue(nativeItem(item).equals(state), "stack count, owner, thrower, delay, age, lifespan, fall and collision state remain native");
            h.assertTrue(inventory(p).equals(inventory) && p.getInventory().getSelected().getCount() == 8, "braking never retrieves or duplicates the Q item");
            h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)), "braking does not spend S or alter HP");
            near(h, HighPowerResources.cp(p), cp - 10, "LV1 pays one approved CP debit");
            eq(h, HighPowerGrowth.values(p).position(), q, "self braking cannot grow q");
            eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "self braking cannot certify Q");
            h.assertTrue(AbilityStorage.get(p).exp(ID) == 0 && AbilityStorage.get(p).levelExperience == xp, "no skill or level XP from self toss");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void gravity_resumes_and_same_native_entity_cannot_be_braked_again_after_receipt_expires(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            ItemEntity item = f.toss(p, 3); h.assertTrue(f.start(p), "first real item brake accepted");
            f.release(p); item.tick();
            h.assertTrue(item.getDeltaMovement().y < 0 && !item.isNoGravity() && !item.noPhysics, "next actual ItemEntity.tick restores ordinary gravity");
            Vec3 inherited = item.getDeltaMovement(); BrakingTouch.tick(p); BrakingTouch.tick(p);
            vector(h, item.getDeltaMovement(), inherited, "receipt and repeated controller ticks cannot hover the item");
            h.runAfterDelay(13, () -> {
                f.aim(p, item, 1.4); item.setDeltaMovement(.1, -.1, .1); Vec3 before = item.getDeltaMovement(); double cp = HighPowerResources.cp(p);
                h.assertFalse(f.start(p), "same exact entity is spent even after cooldown and receipt expiry");
                vector(h, item.getDeltaMovement(), before, "spent item keeps subsequently acquired native motion");
                near(h, HighPowerResources.cp(p), cp, "once-per-entity refusal is free"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fresh_exact_owned_snowball_keeps_owner_identity_and_native_flight(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            Snowball snow = f.snowball(p, p); Vec3 position = snow.position(); UUID identity = snow.getUUID(); int ticks = snow.tickCount;
            snow.setDeltaMovement(.2, -.15, .3); double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "exact fresh server joined owner snowball is admitted");
            vector(h, snow.getDeltaMovement(), Vec3.ZERO, "snowball receives one zero delta");
            h.assertTrue(snow.getOwner() == p && snow.getUUID().equals(identity) && snow.position().equals(position)
                    && snow.tickCount == ticks && !snow.isNoGravity() && !snow.noPhysics, "native owner, identity, position, age and physics stay unchanged");
            near(h, HighPowerResources.cp(p), cp - 10, "snowball costs one object brake");
            snow.tick(); h.assertTrue(snow.getDeltaMovement().y < 0 && !snow.isRemoved(), "ordinary projectile gravity resumes on real tick"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unknown_and_foreign_ownership_cannot_be_repaired_by_vanilla_owner_fields(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), other = f.player(22, 8, 1);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            ItemEntity raw = f.raw(p, new ItemStack(Items.IRON_INGOT, 2)); raw.setThrower(p); raw.setTarget(p.getUUID());
            h.assertFalse(f.start(p), "unknown raw item cannot gain Q proof from native owner fields"); raw.discard();
            ItemEntity foreign = f.toss(other, 2); f.aim(p, foreign, 1.4); foreign.setThrower(p); foreign.setTarget(p.getUUID());
            h.assertFalse(f.start(p), "foreign native Q proof cannot be laundered by changing target and thrower"); foreign.discard();
            Snowball projectile = f.snowball(p, other); projectile.setOwner(p);
            h.assertFalse(f.start(p), "post-join owner replacement cannot launder a foreign projectile"); projectile.discard();
            Snowball unknown = new Snowball(EntityType.SNOWBALL, h.getLevel()); f.aim(p, unknown, 1.4); f.entity(unknown); unknown.setOwner(p);
            h.assertFalse(f.start(p), "post-join assignment cannot backfill missing server projectile origin");
            near(h, HighPowerResources.cp(p), cp, "all unknown ownership refusals remain free"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void decorated_container_protected_immovable_and_foreign_pickup_items_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            for (int mode = 0; mode < 7; mode++) {
                ItemEntity item = f.toss(p, 2);
                switch (mode) {
                    case 0 -> item.getItem().set(DataComponents.CUSTOM_NAME, Component.literal("unreviewed"));
                    case 1 -> item.getItem().set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
                    case 2 -> item.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                    case 3 -> item.getPersistentData().putBoolean("academy:protected", true);
                    case 4 -> item.getPersistentData().putBoolean("academy:immovable", true);
                    case 5 -> item.setTarget(UUID.randomUUID());
                    default -> item.setNoGravity(true);
                }
                Vec3 motion = item.getDeltaMovement(); h.assertFalse(f.start(p), "closed ordinary native item contract refuses variant " + mode);
                vector(h, item.getDeltaMovement(), motion, "refusal does not mutate item " + mode); item.discard();
            }
            near(h, HighPowerResources.cp(p), cp, "unsupported item variations cost no CP"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void arrows_eggs_and_projectile_subclasses_are_not_ordinary_snowballs(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            List<Entity> excluded = List.of(new Arrow(h.getLevel(), p, new ItemStack(Items.ARROW), null),
                    new ThrownEgg(h.getLevel(), p), new Snowball(h.getLevel(), p) {});
            for (Entity entity : excluded) {
                f.aim(p, entity, 1.4); entity.setDeltaMovement(.1, -.2, .3); f.entity(entity); Vec3 motion = entity.getDeltaMovement();
                h.assertFalse(f.start(p), "unreviewed exact projectile class cannot borrow snowball admission");
                vector(h, entity.getDeltaMovement(), motion, "unreviewed native projectile keeps its motion"); entity.discard();
            }
            near(h, HighPowerResources.cp(p), cp, "unsupported projectiles are free refusals"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nearest_actual_body_is_selected_and_ineligible_foreground_body_blocks(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), blocked = f.player(22, 8, 1);
        f.ready(() -> {
            ItemEntity near = f.toss(p, 2), far = f.toss(p, 3); f.aim(p, near, .8); f.aim(p, far, 1.7);
            Vec3 farMotion = far.getDeltaMovement(); h.assertTrue(f.start(p), "nearest actual native body is selected");
            vector(h, near.getDeltaMovement(), Vec3.ZERO, "nearest body receives the brake"); vector(h, far.getDeltaMovement(), farMotion, "farther eligible body is untouched");
            ItemEntity target = f.toss(blocked, 2), unowned = f.raw(blocked, new ItemStack(Items.DIRT)); f.aim(blocked, unowned, .7);
            double cp = HighPowerResources.cp(blocked); Vec3 motion = target.getDeltaMovement();
            h.assertFalse(f.start(blocked), "an ineligible foreground body blocks selection behind it");
            vector(h, target.getDeltaMovement(), motion, "ray cannot skip through unowned objects"); near(h, HighPowerResources.cp(blocked), cp, "occluded body refusal is free"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void reach_boundary_uses_closed_body_contact_without_range_inflation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] p = {f.player(6, 8, 1), f.player(16, 8, 1), f.player(26, 8, 1)};
        f.ready(() -> {
            double[] ranges = {BrakingTouchRules.RANGE-.0001, BrakingTouchRules.RANGE, BrakingTouchRules.RANGE+.0001};
            for (int i = 0; i < p.length; i++) {
                ItemEntity item = f.toss(p[i], 2); f.aim(p[i], item, ranges[i]); double cp = HighPowerResources.cp(p[i]); Vec3 motion = item.getDeltaMovement();
                h.assertTrue(f.start(p[i]) == (i < 2), "closed real body contact boundary at " + ranges[i]);
                vector(h, item.getDeltaMovement(), i < 2 ? Vec3.ZERO : motion, "only body at or within reach can change velocity");
                near(h, HighPowerResources.cp(p[i]), cp - (i < 2 ? 10 : 0), "range refusal cannot debit");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void solid_wall_and_off_ray_body_do_not_expand_the_touch_ray(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), off = f.player(22, 8, 1);
        f.ready(() -> {
            ItemEntity item = f.toss(p, 2); f.aim(p, item, 1.7);
            BlockPos wall = BlockPos.containing(p.getEyePosition().add(0, 0, 1)); h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            double cp = HighPowerResources.cp(p); h.assertFalse(f.start(p), "native solid collision precedes eligible item"); near(h, HighPowerResources.cp(p), cp, "wall refusal is free");
            ItemEntity side = f.toss(off, 2); side.setPos(side.position().add(.126, 0, 0));
            h.assertFalse(f.start(off), "body just outside exact ray cannot be selected using broad cone or inflated box"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void authenticated_input_replay_wrong_actor_epoch_and_pre_start_abort_are_fenced(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            ItemEntity item = f.toss(p, 2); long epoch = f.epoch(p); double cp = HighPowerResources.cp(p);
            h.assertFalse(BrakingTouch.start(p, new HighPowerInput.Input(0, 1, p.getId() + 1, epoch).wire()), "wrong native actor cannot start");
            h.assertFalse(BrakingTouch.start(p, new HighPowerInput.Input(0, 1, p.getId(), epoch + 1).wire()), "wrong owner epoch cannot start");
            String first = f.wire(p); BrakingTouch.abort(p, first);
            h.assertFalse(BrakingTouch.start(p, first), "authenticated cancel-before-start retires that exact input");
            String next = f.wire(p); h.assertTrue(BrakingTouch.start(p, next), "new input reaches server-selected native body");
            h.assertFalse(BrakingTouch.start(p, next), "same input replay cannot repeat a native brake");
            near(h, HighPowerResources.cp(p), cp - 10, "all malformed and replay inputs leave only one debit");
            vector(h, item.getDeltaMovement(), Vec3.ZERO, "single authenticated commit owns one zero delta"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void five_cost_anchors_apply_existing_shared_cp_and_four_tick_minimum(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[5];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(5 + i * 6, 8, i + 1);
        f.ready(() -> {
            double[] cost = {10, 5, 2, .5, .1}; int[] cooldown = {10, 5, 4, 4, 4};
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; f.toss(p, 2); double cp = HighPowerResources.cp(p), classic = AbilityStorage.get(p).cp;
                h.assertTrue(f.start(p), "actual object commit at approved q anchor " + (i + 1));
                near(h, HighPowerResources.cp(p), cp - cost[i], "approved q cost anchor"); near(h, AbilityStorage.get(p).cp, classic, "no duplicate classic CP debit");
                h.assertTrue(AbilityStorage.get(p).cooldowns.getOrDefault(ID, 0) == cooldown[i], "approved cooldown keeps four-tick physical minimum");
                h.assertFalse(f.start(p), "fresh input cannot bypass existing physical cooldown");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void insufficient_and_reserved_shared_cp_cannot_partially_brake_or_claim_target(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), reserved = f.player(22, 8, 1);
        f.ready(() -> {
            ItemEntity item = f.toss(p, 2); Vec3 motion = item.getDeltaMovement(); f.cp(p, 9.999);
            h.assertFalse(f.start(p), "partial CP cannot pay one indivisible object brake");
            vector(h, item.getDeltaMovement(), motion, "insufficient CP never changes target"); near(h, HighPowerResources.cp(p), 9.999, "failed payment cannot partially debit");
            f.cp(p, 10); h.assertTrue(f.start(p), "failed affordability cannot mark the entity spent"); near(h, HighPowerResources.cp(p), 0, "exact boundary pays once");
            ItemEntity held = f.toss(reserved, 2); Vec3 heldMotion = held.getDeltaMovement(); double cp = HighPowerResources.cp(reserved);
            try (var reservation = HighPowerResources.reserve(reserved, ID, cp - 5)) {
                h.assertTrue(reservation != null, "actual shared ledger reservation created");
                h.assertFalse(f.start(reserved), "reserved CP cannot be spent by braking"); vector(h, held.getDeltaMovement(), heldMotion, "reservation refusal preserves native object");
                near(h, HighPowerResources.cp(reserved), cp, "reservation and refusal do not debit the ledger");
            }
            h.assertTrue(f.start(reserved), "releasing a reservation admits the still-unspent native entity"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void same_entity_spent_marker_survives_native_item_and_player_reload(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            ItemEntity item = f.toss(p, 2); h.assertTrue(f.start(p), "original certified entity pays once");
            CompoundTag savedItem = item.saveWithoutId(new CompoundTag()); UUID uuid = item.getUUID(); f.reload(p); item.load(savedItem);
            h.assertTrue(item.getUUID().equals(uuid), "full native item NBT reload retains original identity");
            h.runAfterDelay(13, () -> {
                f.aim(p, item, 1.4); item.setDeltaMovement(.1, -.2, .3); double cp = HighPowerResources.cp(p); Vec3 motion = item.getDeltaMovement();
                h.assertFalse(f.start(p), "native item and owner reload cannot reset once-per-entity fence");
                vector(h, item.getDeltaMovement(), motion, "reloaded entity keeps new motion"); near(h, HighPowerResources.cp(p), cp, "reload refusal is free"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void crouch_arms_immediately_releases_free_and_expires_without_physics_writes(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), released = f.player(22, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); released.setShiftKeyDown(true); double cp = HighPowerResources.cp(p), otherCp = HighPowerResources.cp(released);
            h.assertTrue(f.start(p) && f.start(released), "crouch input arms immediately without charging period");
            h.assertTrue(BrakingTouch.active(p), "newly armed landing is live"); near(h, HighPowerResources.cp(p), cp, "arming is free before a true landing");
            released.fallDistance = 12; released.setDeltaMovement(.2, -.3, .4); Vec3 velocity = released.getDeltaMovement();
            f.release(released); h.assertFalse(BrakingTouch.active(released), "matching release retires unspent landing arm");
            vector(h, released.getDeltaMovement(), velocity, "release preserves full player momentum"); near(h, released.fallDistance, 12, "release never clears accumulated fall distance");
            near(h, HighPowerResources.cp(released), otherCp, "unspent release remains free");
            h.runAfterDelay(BrakingTouchRules.ARM_TICKS - 1, () -> h.assertTrue(BrakingTouch.active(p), "held arm remains eligible through its final tick"));
            h.runAfterDelay(BrakingTouchRules.ARM_TICKS, () -> { BrakingTouch.tick(p); h.assertFalse(BrakingTouch.active(p), "the exact deadline expires the arm without a contact"); h.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void preset_revision_interference_category_and_owner_epoch_loss_retire_unspent_arm(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] p = new ServerPlayer[4];
        for (int i = 0; i < p.length; i++) p[i] = f.player(6 + i * 8, 8, 1);
        f.ready(() -> {
            for (int i = 0; i < p.length; i++) {
                p[i].setShiftKeyDown(true); h.assertTrue(f.start(p[i]), "genuine crouch arm before lifecycle mutation"); double cp = HighPowerResources.cp(p[i]);
                p[i].fallDistance = 11; p[i].setDeltaMovement(.1, -.2, .3); Vec3 motion = p[i].getDeltaMovement();
                switch (i) {
                    case 0 -> AbilityStorage.get(p[i]).presets.switchTo(1);
                    case 1 -> AbilityStorage.get(p[i]).interfering = true;
                    case 2 -> AbilityStorage.get(p[i]).changeCategoryClassic(HighPowerCategories.TELEPORTER);
                    default -> LegacySingleKeyOwnerEpoch.forget(p[i]);
                }
                BrakingTouch.tick(p[i]); h.assertFalse(BrakingTouch.active(p[i]), "lifecycle mutation retires unspent arm " + i);
                vector(h, p[i].getDeltaMovement(), motion, "lifecycle cleanup preserves player momentum"); near(h, p[i].fallDistance, 11, "lifecycle cleanup preserves fall distance");
                near(h, p[i].getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"), cp, "lifecycle cleanup cannot debit or refill shared CP");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_fall_calls_events_and_damage_sources_never_mint_landing_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real armed input before synthetic calls");
            double cp = HighPowerResources.cp(p); BigDecimal before = hp(p); float fall = p.fallDistance;
            near(h, BrakingTouch.reduceLanding(p, 8, 1), 8, "public adapter cannot invent an opaque native contact");
            LivingFallEvent synthetic = new LivingFallEvent(p, 8, 1); NeoForge.EVENT_BUS.post(synthetic);
            near(h, synthetic.getDistance(), 8, "posting a fall event does not grant native landing authority");
            h.assertTrue(p.causeFallDamage(8, 1, p.damageSources().fall()), "direct native causeFallDamage still performs normal fall damage");
            eq(h, hp(p), before.subtract(BigDecimal.valueOf(5)), "direct causeFallDamage cannot borrow the landing arm");
            p.invulnerableTime = 0; p.fallDistance = 8; p.doCheckFallDamage(0, -.25, 0, true);
            eq(h, hp(p), before.subtract(BigDecimal.TEN), "direct native doCheckFallDamage is still synthetic without accepted packet scope");
            near(h, HighPowerResources.cp(p), cp, "synthetic native entry points cannot spend braking CP");
            h.assertTrue(BrakingTouch.active(p), "synthetic callbacks do not consume the actual held landing arm");
            p.fallDistance = fall; h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void accepted_native_landing_reduces_one_distance_and_preserves_native_momentum_and_fall_reset(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real input arms one immediate landing");
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), nativeHealth = hp(control), shield = HighPowerCombat.shield(p);
            f.land(control, 8); f.land(p, 8);
            eq(h, nativeHealth.subtract(hp(control)), BigDecimal.valueOf(5), "paired genuine vanilla landing computes eight minus native safe three");
            eq(h, health.subtract(hp(p)), BigDecimal.valueOf(2), "one accepted landing calculates min-three-metre distance reduction before native damage");
            near(h, HighPowerResources.cp(p), cp - 10, "one genuine contact pays one approved CP cost");
            eq(h, HighPowerCombat.shield(p), shield, "landing never spends or refills base shield");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "native packet gravity and collision own exactly the same player momentum");
            near(h, p.fallDistance, control.fallDistance, "only vanilla landing clears accumulated fall distance");
            double paid = HighPowerResources.cp(p); BigDecimal after = hp(p); p.invulnerableTime = 0;
            f.land(p, 8); eq(h, after.subtract(hp(p)), BigDecimal.valueOf(5), "second genuine contact has no remaining mitigation authority");
            near(h, HighPowerResources.cp(p), paid, "same held input cannot pay or mitigate twice");
            h.assertTrue(AbilityStorage.get(p).exp(ID) == 0, "self landing grants no mastery"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fractional_horizontal_crouched_landing_preserves_native_body_and_admits_one_mitigation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            for (ServerPlayer player : List.of(p, control)) { player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING); }
            h.assertTrue(f.start(p), "real crouched input arms before a fractional horizontal landing");
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), nativeHealth = hp(control), shield = HighPowerCombat.shield(p);
            int[] falls = new int[2]; float[] distances = new float[2];
            f.hook(new Object() { @SubscribeEvent public void fall(LivingFallEvent event) {
                int index = event.getEntity() == p ? 0 : event.getEntity() == control ? 1 : -1;
                if (index < 0) return; falls[index]++; distances[index] = event.getDistance();
            } });
            for (ServerPlayer player : List.of(control, p)) {
                Vec3 target = f.anchors.get(player).add(.0872307, 0, .0449289);
                Vec3 origin = target.add(-.0024, .25, .07123), movement = target.subtract(origin);
                player.setNoGravity(false); player.setPos(origin); player.setOnGround(false);
                player.fallDistance = 8; player.setDeltaMovement(movement); player.connection.resetPosition();
                h.assertTrue(player.getPose() == Pose.CROUCHING && movement.x > 0 && movement.z < 0 && movement.y < 0,
                        "declared fixture has an actual crouched body and nonzero horizontal descending movement");
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true));
                h.assertTrue(player.position().equals(target) && player.onGround() && player.getPose() == Pose.CROUCHING,
                        "real movement packet accepts exact requested fractional supported position and preserves crouched pose");
            }
            BigDecimal originalDamage = nativeHealth.subtract(hp(control)), reducedDamage = health.subtract(hp(p));
            h.assertTrue(falls[0] == 1 && falls[1] == 1 && distances[0] == 8 && distances[1] == 8,
                    "both actual horizontal landings dispatch one unchanged original-distance native fall event");
            eq(h, originalDamage, BigDecimal.valueOf(5), "fractional horizontal native control keeps five fall damage");
            eq(h, reducedDamage, BigDecimal.valueOf(2), "same accepted crouched horizontal contact receives exactly one bounded distance mitigation");
            h.assertTrue(originalDamage.subtract(reducedDamage).signum() > 0
                    && originalDamage.subtract(reducedDamage).compareTo(BigDecimal.valueOf(3)) <= 0,
                    "actual native benefit remains positive and within the approved three-metre allowance");
            near(h, HighPowerResources.cp(p), cp - 10, "one genuine fractional contact pays once");
            eq(h, HighPowerCombat.shield(p), shield, "fractional contact cannot spend or refill S");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "braking preserves the paired native horizontal momentum and collision response");
            near(h, p.fallDistance, control.fallDistance, "native landing alone owns the same accumulated fall reset");
            System.out.println("NEW413_FRACTIONAL_HORIZONTAL_NATIVE originalDamage=" + originalDamage + " reducedDamage=" + reducedDamage
                    + " cpDebit=" + (cp - HighPowerResources.cp(p)) + " pose=" + p.getPose() + " motion=" + p.getDeltaMovement());
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unsupported_airborne_start_with_stale_ground_flag_accepts_the_real_crouched_landing(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            final float distance = 5.656571F;
            for (ServerPlayer player : List.of(p, control)) { player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING); }
            h.assertTrue(f.start(p), "real crouched input arms before the observed stale-ground-flag landing");
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), nativeHealth = hp(control), shield = HighPowerCombat.shield(p);
            int[] falls = new int[2]; float[] measured = new float[2];
            f.hook(new Object() { @SubscribeEvent public void fall(LivingFallEvent event) {
                int index = event.getEntity() == p ? 0 : event.getEntity() == control ? 1 : -1;
                if (index < 0) return; falls[index]++; measured[index] = event.getDistance();
            } });
            for (ServerPlayer player : List.of(control, p)) {
                Vec3 target = f.anchors.get(player).add(.0872307, 0, .0449289);
                Vec3 origin = target.add(0, .5956327336298841, .06135128527546385);
                player.setNoGravity(false); player.setPos(origin); player.setOnGround(true);
                player.fallDistance = distance; player.setDeltaMovement(0, -.0784000015258789, 0); player.connection.resetPosition();
                var body = player.getBoundingBox();
                var feet = new net.minecraft.world.phys.AABB(body.minX, body.minY - 1e-6, body.minZ, body.maxX, body.minY, body.maxZ);
                h.assertTrue(player.onGround() && h.getLevel().findSupportingBlock(player, feet).isEmpty(),
                        "real native support query proves airborne start despite the stale onGround boolean");
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true));
                h.assertTrue(player.position().equals(target) && player.onGround() && player.getPose() == Pose.CROUCHING,
                        "actual accepted packet reaches the supported crouched floor position");
            }
            h.assertTrue(falls[0] == 1 && falls[1] == 1 && measured[0] == distance && measured[1] == distance,
                    "both accepted native contacts carry the original observed finite fall distance exactly once");
            eq(h, nativeHealth.subtract(hp(control)), BigDecimal.valueOf(3), "paired ordinary native contact still calculates three damage");
            eq(h, hp(p), health, "half-distance mitigation makes this actual native landing harmless");
            near(h, HighPowerResources.cp(p), cp - 10, "one real supported landing pays once despite stale initial ground flag");
            eq(h, HighPowerCombat.shield(p), shield, "actual contact leaves base S unchanged");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "landing proof never rewrites native velocity");
            near(h, p.fallDistance, control.fallDistance, "only native landing performs the paired fall-distance reset");
            System.out.println("NEW413_STALE_GROUND_NATIVE observedFall=" + distance + " controlDamage=" + nativeHealth.subtract(hp(control))
                    + " reducedDamage=" + health.subtract(hp(p)) + " cpDebit=" + (cp - HighPowerResources.cp(p)));
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fake_ground_stationary_support_no_descent_and_native_correction_never_authorize_landing(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] players = {f.player(8, 8, 1), f.player(22, 8, 1), f.player(8, 18, 1),
                f.player(22, 18, 1), f.player(8, 26, 1), f.player(22, 26, 1)};
        f.ready(() -> {
            for (int mode = 0; mode < players.length; mode++) {
                ServerPlayer p = players[mode]; p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING);
                h.assertTrue(f.start(p), "real free arm before adversarial native movement case " + mode);
                Vec3 floor = f.anchors.get(p), origin, target;
                if (mode == 0) { target = floor.add(0, 3, 0); origin = target.add(0, .25, 0); }
                else if (mode == 1) { target = floor; origin = floor; }
                else if (mode == 2) {
                    target = floor; origin = target.add(-1.3, 0, 0);
                    for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(origin.x - .31, floor.y - .1, origin.z - .31),
                            BlockPos.containing(origin.x + .31, floor.y - .1, origin.z + .31)))
                        h.getLevel().setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
                } else if (mode == 3) {
                    origin = floor.add(0, .5956327336298841, 0); target = floor.add(1.3, 0, 0);
                    BlockPos obstruction = BlockPos.containing(target);
                    h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                    h.getLevel().setBlockAndUpdate(obstruction.above(), Blocks.STONE.defaultBlockState());
                } else {
                    h.getLevel().setBlockAndUpdate(BlockPos.containing(floor), (mode == 4 ? Blocks.STONE_SLAB : Blocks.SLIME_BLOCK).defaultBlockState());
                    origin = floor.add(0, mode == 4 ? .5 : 1, 0); target = floor.add(1.3, 0, 0);
                }
                p.setNoGravity(false); p.setPos(origin); p.setOnGround(mode != 1 && mode < 4); p.fallDistance = 8;
                p.setDeltaMovement(target.subtract(origin)); p.connection.resetPosition(); double cp = HighPowerResources.cp(p);
                var before = p.getBoundingBox(); var feet = new net.minecraft.world.phys.AABB(before.minX, before.minY - 1e-6, before.minZ, before.maxX, before.minY, before.maxZ);
                h.assertTrue(h.getLevel().findSupportingBlock(p, feet).isPresent() == (mode == 1 || mode >= 4),
                        "declared initial support distinguishes stationary ground from unsupported starting points " + mode);
                if (mode == 1 || mode >= 4) h.assertFalse(p.onGround(), "false onGround flag cannot hide real initial support, including slabs and slime");
                p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true));
                if (mode == 3) h.assertTrue(p.position().equals(origin) && !p.position().equals(target), "real native collision rejection corrects the packet back to original position");
                else h.assertTrue(p.position().equals(target), "non-correction fixture follows the real accepted movement branch " + mode);
                if (mode == 0) {
                    var body = p.getBoundingBox(); var endFeet = new net.minecraft.world.phys.AABB(body.minX, body.minY - 1e-6, body.minZ, body.maxX, body.minY, body.maxZ);
                    h.assertTrue(h.getLevel().findSupportingBlock(p, endFeet).isEmpty(), "fake client onGround has no real support at the accepted endpoint");
                }
                if (mode == 2) h.assertTrue(target.y == origin.y && target.x > origin.x, "accepted horizontal transfer onto support contains no downward displacement");
                near(h, HighPowerResources.cp(p), cp, "unsupported, supported-at-origin, non-descending or corrected native packet cannot debit landing CP " + mode);
                h.assertTrue(BrakingTouchLandingProof.current(p) == null, "native packet scope cannot leak landing authority after return");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_and_native_zero_falls_remain_free_before_any_landing_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = {f.player(6, 8, 1), f.player(16, 8, 1), f.player(26, 8, 1)};
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real landing arm before native veto");
                final int mode = i; FallHook hook = new FallHook(p, e -> { if (mode == 0) e.setCanceled(true); if (mode == 1) e.setDamageMultiplier(0); }); f.hook(hook);
                if (i == 2) p.getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(20);
                double cp = HighPowerResources.cp(p); BigDecimal hp = hp(p); f.land(p, 8);
                h.assertTrue(hook.calls == 1, "actual accepted landing dispatches the real fall event");
                near(h, HighPowerResources.cp(p), cp, "canceled or originally harmless fall never pays " + mode);
                eq(h, hp(p), hp, "native veto or native safe distance keeps damage zero");
                h.assertFalse(BrakingTouch.active(p), "first real supported contact closes even a canceled or harmless arm");
            }
            ServerPlayer zero = owners[2]; zero.getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(3);
            double cp = HighPowerResources.cp(zero); h.assertTrue(f.start(zero), "fresh free arm before an exactly zero-distance landing");
            f.land(zero, 0); near(h, HighPowerResources.cp(zero), cp, "zero accumulated distance never pays");
            h.assertFalse(BrakingTouch.active(zero), "accepted supported zero-distance contact retires arm even when vanilla skips Block.fallOn");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_fall_event_modified_distance_is_reduced_after_observers_once(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real armed input before fall observer");
            FallHook hook = new FallHook(p, e -> { e.setDistance(6); e.setDamageMultiplier(.5F); }); f.hook(hook);
            BigDecimal health = hp(p); double cp = HighPowerResources.cp(p); f.land(p, 8);
            h.assertTrue(hook.calls == 1, "native fall observer runs once before the adapter");
            eq(h, hp(p), health, "event six-metre distance reduces to native safe three before its half multiplier");
            near(h, HighPowerResources.cp(p), cp - 10, "actual positive avoided native damage pays once even when effective result is zero"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void real_fall_event_reentry_cannot_steal_or_reuse_the_outer_contact(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "actual arm before recursive fall callback");
            for (ServerPlayer target : List.of(p, control)) f.hook(new FallHook(target, e -> {
                target.causeFallDamage(4, 1, target.damageSources().fall()); target.invulnerableTime = 0;
            }));
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), nativeHealth = hp(control);
            f.land(control, 8); f.land(p, 8);
            eq(h, health.subtract(hp(p)), nativeHealth.subtract(hp(control)), "nested synthetic fall and poisoned outer fall match native control damage");
            near(h, HighPowerResources.cp(p), cp, "reentrant opaque contact is rejected before payment"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void movement_and_release_inside_native_fall_event_cancel_before_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = {f.player(8, 8, 1), f.player(22, 8, 1)};
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; p.setShiftKeyDown(true); h.assertTrue(f.start(p), "actual arm before external fall observer"); final int mode = i;
                f.hook(new FallHook(p, e -> { if (mode == 0) p.setPos(p.position().add(.1, 0, 0)); else f.release(p); }));
                double cp = HighPowerResources.cp(p); BigDecimal health = hp(p); f.land(p, 8, false);
                near(h, HighPowerResources.cp(p), cp, "changed contact or released owner cannot pay through stale native scope");
                eq(h, health.subtract(hp(p)), BigDecimal.valueOf(5), "invalidated mitigation leaves actual vanilla fall damage intact");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void later_incoming_hurt_cancellation_preserves_paid_contact_mitigation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real one-contact landing arm");
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == p) e.setCanceled(true); } });
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p); f.land(p, 8);
            eq(h, hp(p), health, "later native incoming damage veto remains authoritative");
            near(h, HighPowerResources.cp(p), cp - 10, "already completed contact-distance mitigation remains paid after later hurt cancellation");
            p.invulnerableTime = 0; f.land(p, 8); near(h, HighPowerResources.cp(p), cp - 10, "late cancellation cannot restore a reusable contact token"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void large_finite_native_landing_never_rounds_three_metre_allowance_up_to_four(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        ServerPlayer ordinary = f.player(8, 18, 1), ordinaryControl = f.player(22, 18, 1);
        f.ready(() -> {
            final float distance = 16_777_224F;
            // With native safe distance zero and multiplier one, the exact integer-valued
            // native incoming amount directly witnesses the distance passed to calculateFallDamage.
            for (ServerPlayer target : List.of(p, control)) target.getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(0);
            List<ServerPlayer> owners = List.of(p, control, ordinary, ordinaryControl);
            float[] originalFall = new float[4], nativeAmount = new float[4]; int[] falls = new int[4], hits = new int[4];
            f.hook(new Object() {
                @SubscribeEvent public void fall(LivingFallEvent event) {
                    int index = owners.indexOf(event.getEntity());
                    if (index < 0) return; falls[index]++; originalFall[index] = event.getDistance();
                }
                @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) {
                    int index = owners.indexOf(event.getEntity());
                    if (index < 0) return; hits[index]++; nativeAmount[index] = event.getOriginalAmount();
                    h.assertTrue(event.getSource() == ((ServerPlayer) event.getEntity()).damageSources().fall(), "observed amount originates from the actual native fall source");
                    event.setCanceled(true); // Cancellation is after contact-distance settlement, preserving the native call without lethal damage.
                }
            });
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real authenticated crouch input arms the large finite landing");
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), controlHealth = hp(control);
            f.land(control, distance); f.land(p, distance);
            h.assertTrue(falls[0] == 1 && falls[1] == 1 && hits[0] == 1 && hits[1] == 1,
                    "both genuine accepted movement packets reach one native fall and incoming-damage callback");
            h.assertTrue(originalFall[0] == distance && originalFall[1] == distance && nativeAmount[1] == distance,
                    "paired native control preserves the same large finite original distance without event rewriting");
            double reduction = (double) nativeAmount[1] - nativeAmount[0];
            h.assertTrue(Float.isFinite(nativeAmount[0]) && nativeAmount[0] > 0 && reduction > 0 && reduction <= 3,
                    "actual native effective distance cannot receive four metres through float rounding: reduction=" + reduction);
            h.assertTrue(nativeAmount[0] == 16_777_222F && reduction == 2,
                    "the nearest safe representable effective distance grants two metres at this float boundary");
            near(h, HighPowerResources.cp(p), cp - 10, "one bounded positive native mitigation remains paid after late hurt cancellation");
            eq(h, hp(p), health, "later cancellation prevents large native HP loss"); eq(h, hp(control), controlHealth, "paired native cancellation also prevents control HP loss");
            near(h, ordinary.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 3, "second pair retains ordinary native safe fall distance");
            ordinary.setShiftKeyDown(true); h.assertTrue(f.start(ordinary), "fresh real input arms the ordinary-safe-distance boundary case");
            double ordinaryCp = HighPowerResources.cp(ordinary); BigDecimal ordinaryHealth = hp(ordinary), ordinaryControlHealth = hp(ordinaryControl);
            f.land(ordinaryControl, distance); f.land(ordinary, distance);
            h.assertTrue(falls[2] == 1 && falls[3] == 1 && hits[2] == 1 && hits[3] == 1
                    && originalFall[2] == distance && originalFall[3] == distance,
                    "ordinary-safe-distance pair also uses actual accepted native landing callbacks");
            h.assertTrue(nativeAmount[2] == 16_777_220F && nativeAmount[2] == nativeAmount[3],
                    "native subtraction rounds both original and safe candidate to the same integer damage, so original damage is retained");
            near(h, HighPowerResources.cp(ordinary), ordinaryCp, "a representable distance change with no native damage benefit must remain free");
            h.assertFalse(BrakingTouch.active(ordinary), "unproductive real contact consumes the arm without a paid receipt");
            eq(h, hp(ordinary), ordinaryHealth, "late native cancellation protects the no-op owner");
            eq(h, hp(ordinaryControl), ordinaryControlHealth, "late native cancellation protects the ordinary paired control");
            System.out.println("NEW413_LARGE_FINITE_NATIVE original=" + distance + " effective=" + nativeAmount[0] + " reduction=" + reduction
                    + " defaultSafeDamage=" + nativeAmount[2] + "/" + nativeAmount[3] + " noBenefitDebit=" + (ordinaryCp - HighPowerResources.cp(ordinary)));
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_protection_resistance_and_absorption_match_reduced_distance_control(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            for (ServerPlayer target : List.of(p, control)) {
                ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
                boots.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
                target.setItemSlot(EquipmentSlot.FEET, boots); target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2); target.setAbsorptionAmount(.25F);
            }
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real arm with ordinary native defenses equipped");
            BigDecimal health = hp(p), nativeHealth = hp(control); double cp = HighPowerResources.cp(p);
            f.land(control, 5); f.land(p, 8);
            eq(h, health.subtract(hp(p)), nativeHealth.subtract(hp(control)), "native Protection and Resistance follow identical reduced-distance damage");
            near(h, p.getAbsorptionAmount(), control.getAbsorptionAmount(), "native absorption is consumed exactly once after protection");
            h.assertTrue(health.subtract(hp(p)).signum() > 0, "paired controls witness genuine nonzero post-protection damage");
            near(h, HighPowerResources.cp(p), cp - 10, "native defenses do not duplicate or erase contact payment"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unsupported_feather_falling_refuses_free_and_keeps_native_paired_fall_protection(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            for (ServerPlayer target : List.of(p, control)) {
                ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
                boots.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);
                target.setItemSlot(EquipmentSlot.FEET, boots);
            }
            h.assertTrue(HighPowerCombat.unsupportedReason(p).isPresent(), "existing reviewed exact combat contract intentionally excludes Feather Falling");
            p.setShiftKeyDown(true); double cp = HighPowerResources.cp(p); float projectedBefore = p.getHealth(), controlProjectedBefore = control.getHealth();
            BigDecimal before = hp(p), nativeBefore = hp(control); float[] damage = new float[2], enchantment = new float[2]; int[] posts = new int[2];
            f.hook(new Object() { @SubscribeEvent public void post(LivingDamageEvent.Post event) {
                int index = event.getEntity() == p ? 0 : event.getEntity() == control ? 1 : -1;
                if (index < 0) return; posts[index]++; damage[index] = event.getNewDamage(); enchantment[index] = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
            } });
            h.assertFalse(f.start(p), "unreviewed enchantment cannot activate braking by bypassing the existing combat gate");
            f.land(control, 8); f.land(p, 8);
            BigDecimal loss = before.subtract(hp(p)), nativeLoss = nativeBefore.subtract(hp(control));
            System.out.println("NEW413_FEATHER_NATIVE projected=" + projectedBefore + "->" + p.getHealth() + " control=" + controlProjectedBefore + "->" + control.getHealth()
                    + " exactLoss=" + loss + " controlLoss=" + nativeLoss + " nativePost=" + damage[0] + "/" + damage[1] + " enchantmentReduction=" + enchantment[0] + "/" + enchantment[1]);
            eq(h, loss, nativeLoss, "rejected braking leaves ordinary exact Feather Falling damage equal to control");
            h.assertTrue(posts[0] == 1 && posts[1] == 1 && damage[0] > 0 && damage[0] == damage[1]
                    && enchantment[0] > 0 && enchantment[0] == enchantment[1], "both actual native Post events witness equal positive damage and genuine enchantment reduction");
            h.assertTrue(loss.signum() > 0 && !p.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "exact HP ledger records actual native damage despite rounded float projection");
            near(h, HighPowerResources.cp(p), cp, "unsupported enchantment and native fall never debit braking CP"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void disabled_native_fall_damage_rule_preserves_free_contact_and_no_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "real free arm before native fall-damage gamerule");
            var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_FALL_DAMAGE); boolean previous = rule.get();
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p);
            try {
                rule.set(false, p.server); f.land(p, 8);
                eq(h, hp(p), health, "native gamerule disables all actual fall damage");
                near(h, HighPowerResources.cp(p), cp, "disabled fall damage cannot charge for nonexistent mitigation");
                h.assertFalse(BrakingTouch.active(p), "first supported harmless contact retires unspent arm");
            } finally { rule.set(previous, p.server); }
            h.assertTrue(rule.get() == previous, "synchronous fixture restores native gamerule before any other test tick"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void existing_slow_falling_keeps_ordinary_native_fall_behavior_without_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), control = f.player(22, 8, 1);
        f.ready(() -> {
            for (ServerPlayer target : List.of(p, control)) target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            p.setShiftKeyDown(true); h.assertTrue(f.start(p), "free arm can precede a contact outside the reviewed native-effect scope");
            double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), nativeHealth = hp(control);
            f.land(control, 8); f.land(p, 8);
            eq(h, health.subtract(hp(p)), nativeHealth.subtract(hp(control)), "closed movement-effect scope leaves ordinary slow-falling native behavior unchanged");
            h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING) && p.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 0, "braking never consumes or replaces the native potion");
            near(h, HighPowerResources.cp(p), cp, "unsupported effect combination cannot pay for mitigation it did not perform"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void body_scan_overflow_refuses_without_mutation_or_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            List<ItemEntity> targets = new ArrayList<>();
            for (int i = 0; i < 65; i++) targets.add(f.toss(p, 1));
            double cp = HighPowerResources.cp(p); List<Vec3> motion = targets.stream().map(Entity::getDeltaMovement).toList();
            h.assertFalse(f.start(p), "sixty-five actual eligible bodies exceed the closed bounded native scan");
            for (int i = 0; i < targets.size(); i++) vector(h, targets.get(i).getDeltaMovement(), motion.get(i), "overflow never selects an arbitrary truncated candidate");
            near(h, HighPowerResources.cp(p), cp, "bounded scan refusal is free"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unknown_snowball_components_and_nonfinite_or_excess_motion_are_refused(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            for (int mode = 0; mode < 4; mode++) {
                Snowball snow = f.snowball(p, p);
                if (mode == 0) { ItemStack stack = new ItemStack(Items.SNOWBALL); stack.set(DataComponents.CUSTOM_NAME, Component.literal("unreviewed")); snow.setItem(stack); }
                if (mode == 1) snow.setDeltaMovement(4.001, 0, 0);
                if (mode == 2) snow.setDeltaMovement(Double.NaN, 0, 0);
                if (mode == 3) snow.setDeltaMovement(Vec3.ZERO);
                Vec3 motion = snow.getDeltaMovement(); h.assertFalse(f.start(p), "unknown component or unsupported motion refuses variant " + mode);
                h.assertTrue(snow.getDeltaMovement().equals(motion), "refused projectile keeps exact motion bits"); snow.discard();
            }
            near(h, HighPowerResources.cp(p), cp, "unsupported projectile states cannot debit CP"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_landing_loaded_guard_never_requests_missing_chunks(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            int far = 1_600_000; h.assertFalse(h.getLevel().hasChunk(far >> 4, far >> 4), "far native world chunk begins unloaded");
            try {
                var guard = BrakingTouchLandingProof.class.getDeclaredMethod("loaded", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.phys.AABB.class);
                guard.setAccessible(true);
                var body = new net.minecraft.world.phys.AABB(far, p.getY(), far, far + .6, p.getY() + 1.8, far + .6);
                h.assertFalse((Boolean) guard.invoke(null, h.getLevel(), body), "actual production guard refuses unloaded support geometry");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            h.assertFalse(h.getLevel().hasChunk(far >> 4, far >> 4), "read-only guard leaves absent chunks unloaded"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fake_ground_in_air_and_solid_support_removed_by_fall_callback_cannot_grant_mitigation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1), removed = f.player(22, 8, 1);
        f.ready(() -> {
            for (ServerPlayer target : List.of(p, removed)) { target.setShiftKeyDown(true); h.assertTrue(f.start(target), "genuine arm before contact refusal"); }
            double cp = HighPowerResources.cp(p); Vec3 air = f.anchors.get(p).add(0, 3, 0); p.setPos(air.add(0, .25, 0));
            p.setOnGround(false); p.fallDistance = 8; p.connection.resetPosition(); p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(air.x, air.y, air.z, true));
            near(h, HighPowerResources.cp(p), cp, "client ground boolean in empty air cannot mint a supported native landing");
            f.hook(new FallHook(removed, e -> h.getLevel().setBlockAndUpdate(BlockPos.containing(f.anchors.get(removed).add(0, -.1, 0)), Blocks.AIR.defaultBlockState())));
            double otherCp = HighPowerResources.cp(removed); BigDecimal health = hp(removed); f.land(removed, 8);
            near(h, HighPowerResources.cp(removed), otherCp, "support removed in callback invalidates captured ground proof before payment");
            eq(h, health.subtract(hp(removed)), BigDecimal.valueOf(5), "native fall still owns its original damage when support proof is lost"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unaffordable_native_landing_preserves_full_vanilla_damage_without_partial_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1);
        f.ready(() -> {
            p.setShiftKeyDown(true); f.cp(p, 9.999); h.assertTrue(f.start(p), "free landing preparation may wait before contact affordability is decided");
            BigDecimal health = hp(p); f.land(p, 8);
            near(h, HighPowerResources.cp(p), 9.999, "insufficient contact budget cannot partially debit");
            eq(h, health.subtract(hp(p)), BigDecimal.valueOf(5), "unpaid contact keeps full ordinary damage"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void finite_native_development_and_reload_preserve_injured_hp_s_reserve_shared_cp_and_q(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); h.assertFalse(state.learned(ID), "native fixture begins with only genuine learned Vector root");
            h.assertTrue(p.hurt(p.damageSources().magic(), 1), "real native exact HP injury precedes development");
            CompoundTag combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            combat.putString("shield", HighPowerCombat.shield(p).subtract(BigDecimal.valueOf(7)).toPlainString());
            CompoundTag reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION);
            reserve.putString("amount", "9"); reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "4"); reserve.putString("upper", "5");
            combat.put(MagneticDomainReserve.KEY, reserve); f.cp(p, 37);
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_deflection_strike", p.level().getGameTime() + 123);
            CompoundTag before = combat.copy(), budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            BigDecimal q = HighPowerGrowth.values(p).position(), earned = HighPowerCombat.earnedGrowthPosition(p); double xp = state.levelExperience;
            ItemStack developer = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, developer);
            DeveloperItemEnergy battery = new DeveloperItemEnergy(developer, DeveloperType.PORTABLE); battery.energy(2340);
            h.assertTrue(DevelopmentController.startSkill(p, ID), "native portable development admits actual learned deflection prerequisite");
            for (int tick = 0; tick < 77; tick++) DevelopmentController.tick(p, ignored -> {});
            h.assertFalse(state.learned(ID), "seventy-seven actual paid development ticks do not grant early ownership"); near(h, battery.energy(), 30, "only one native energy debit remains");
            h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "unfinished development preserves existing high-power ledgers");
            DevelopmentController.tick(p, ignored -> {});
            h.assertTrue(state.learned(ID) && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE && battery.energy() == 0, "exact final paid tick learns one NEW413 without free energy");
            h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "native completion preserves injured HP, base S, old reserve and shared CP byte for byte");
            eq(h, HighPowerGrowth.values(p).position(), q, "learning does not inflate active q"); eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "learning does not certify earned Q");
            h.assertTrue(state.levelExperience == xp && state.exp(ID) == 0, "learning grants neither level XP nor skill mastery");
            f.reload(p); h.assertTrue(AbilityStorage.get(p).learned(ID), "native save/load retains newly learned identity");
            h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "native reload preserves all existing high-power resource balances");
            eq(h, MagneticDomainReserve.reserve(p), BigDecimal.ZERO, "Vector learning grants no access to old electromagnetic reserves"); h.succeed();
        });
    }

    public static final class FallHook {
        final ServerPlayer owner; final Consumer<LivingFallEvent> action; int calls;
        FallHook(ServerPlayer owner, Consumer<LivingFallEvent> action) { this.owner = owner; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void fall(LivingFallEvent event) {
            if (event.getEntity() != owner || calls != 0) return; calls++; action.accept(event);
        }
    }

    private static CompoundTag nativeItem(ItemEntity item) {
        CompoundTag data = new CompoundTag(); item.addAdditionalSaveData(data); data.remove("NeoForgeData");
        data.putInt("ExactAge", item.getAge()); data.putInt("ExactLifespan", item.lifespan); data.putFloat("FallDistance", item.fallDistance);
        data.putBoolean("OnGround", item.onGround()); data.putBoolean("NoGravity", item.isNoGravity()); data.putBoolean("NoPhysics", item.noPhysics); return data;
    }
    private static CompoundTag inventory(ServerPlayer p) { CompoundTag data = new CompoundTag(); data.put("items", p.getInventory().save(new net.minecraft.nbt.ListTag())); return data; }
    private static BigDecimal hp(ServerPlayer p) { return new BigDecimal(HighPowerCombat.healthText(p)); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-12, why + ": " + actual + " != " + expected); }

    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z, int level) { return player(x, z, level, true); }
        ServerPlayer player(double x, double z, int level, boolean learned) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "BT" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, level), "explicit native connected Vector enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(HighPowerCategories.VECMANIP); s.setLevel(level);
            s.learn(HighPowerSkills.DEFLECTION_STRIKE); if (learned) s.learn(ID); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { if (p.isShiftKeyDown()) p.setPose(Pose.CROUCHING); String wire = wire(p); held.put(p, wire); return BrakingTouch.start(p, wire); }
        void release(ServerPlayer p) { BrakingTouch.release(p, held.get(p)); }
        void cp(ServerPlayer p, double value) { p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", value); }
        /** Range is eye-to-front-face distance, never entity center distance or inflated radius. */
        void aim(ServerPlayer p, Entity entity, double range) { entity.setPos(p.getX(), p.getEyeY() - entity.getBbHeight() / 2, p.getZ() + range + entity.getBbWidth() / 2); }
        ItemEntity toss(ServerPlayer p, int count) {
            p.getInventory().setItem(p.getInventory().selected, new ItemStack(Items.IRON_INGOT, count));
            h.assertTrue(p.drop(true), "actual native control-Q enters noncancelled toss provenance seam");
            ItemEntity item = findQ(p, count); aim(p, item, 1.4); item.setPickUpDelay(100); item.setDeltaMovement(.12, -.08, .17); return item;
        }
        ItemEntity findQ(ServerPlayer p, int count) {
            var found = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2), item -> !entities.contains(item)
                    && item.getItem().getCount() == count && SpatialRelayProvenance.proof(item) != null && SpatialRelayProvenance.proof(item).owner().equals(p.getUUID()));
            h.assertTrue(found.size() == 1, "one actual exact Q item has independently certified origin"); entities.add(found.getFirst()); return found.getFirst();
        }
        ItemEntity raw(ServerPlayer p, ItemStack stack) { ItemEntity item = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), stack); aim(p, item, 1.4); item.setDeltaMovement(.12, -.08, .17); entity(item); return item; }
        Snowball snowball(ServerPlayer target, ServerPlayer owner) { Snowball snow = new Snowball(h.getLevel(), owner); aim(target, snow, 1.4); snow.setDeltaMovement(.12, -.08, .17); entity(snow); return snow; }
        void entity(Entity entity) { h.assertTrue(h.getLevel().addFreshEntity(entity), "real native entity joined server world"); entities.add(entity); }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); BrakingTouch.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); }
        void land(ServerPlayer p, float distance) { land(p, distance, true); }
        void land(ServerPlayer p, float distance, boolean verifyPosition) {
            Vec3 floor = anchors.get(p); p.setNoGravity(false); p.setPos(floor.add(0, .25, 0)); p.setOnGround(false);
            p.fallDistance = distance; p.setDeltaMovement(0, -.25, 0); p.connection.resetPosition();
            p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(floor.x, floor.y, floor.z, true));
            if (verifyPosition) h.assertTrue(p.position().distanceToSqr(floor) < 1e-10, "real native movement packet accepts the exact supported floor position");
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { BrakingTouch.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
