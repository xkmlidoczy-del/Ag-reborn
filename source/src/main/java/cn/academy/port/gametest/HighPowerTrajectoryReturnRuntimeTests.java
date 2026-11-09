package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
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
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW431: authenticated native casts and genuine Arrow.tick contacts, never manufactured hits or permits.
 * Learned flags and exact health are declared fixtures. Initial native Arrow.tick establishes leftOwner;
 * fixture placement occurs before casting. Accelerated flight still executes every ordinary native tick.
 */
@GameTestHolder("academy_trajectory_return_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerTrajectoryReturnRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_trajectory_return";
    private static final String ID = "trajectory_return", SKILL = "vecmanip.trajectory_return_air";
    private static final String USED = "academy:trajectory_return_used", FENCES = "academy:trajectory_return_fences";
    private HighPowerTrajectoryReturnRuntimeTests() { }

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {64, 16, 64}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_trajectory_return_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void five_active_q_anchors_reverse_four_arrows_with_one_whole_cast_payment(GameTestHelper h) {
        double[] costs = {18, 9, 4.5, 1.125, .1125}; int[] cooldowns = {60, 30, 16, 6, 1};
        for (int q = 1; q <= 5; q++) try (Fixture f = new Fixture(h, q)) {
            List<Arrow> arrows = new ArrayList<>(); Map<Arrow, Vec3> before = new IdentityHashMap<>();
            for (int i = 0; i < 5; i++) { Arrow arrow = f.arrow(4 + i, new Vec3(.02 * i, -.06, -1)); arrows.add(arrow); before.put(arrow, arrow.getDeltaMovement()); }
            double cp = f.cp(), xp = AbilityStorage.get(f.p).levelExperience; long tick = h.getLevel().getGameTime();
            BigDecimal active = HighPowerGrowth.values(f.p).position(), earned = HighPowerCombat.earnedGrowthPosition(f.p);
            eq(h, active, BigDecimal.valueOf(q), "declared genuine active q anchor");
            h.assertTrue(f.cast(), "four actual arrows admitted at active q " + q);
            for (int i = 0; i < 5; i++) {
                vector(h, arrows.get(i).getDeltaMovement(), before.get(arrows.get(i)).scale(i < 4 ? -1 : 1), "four-slot current XYZ reversal at q " + q);
                h.assertTrue(arrows.get(i).getPersistentData().getBoolean(USED) == (i < 4), "only the four committed originals obtain one-use tombstones");
            }
            near(h, f.cp(), cp - costs[q - 1], "all selected arrows share exactly one CP debit");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == cooldowns[q - 1]
                    && f.budget().getLong("next_" + ID) == tick + cooldowns[q - 1], "native and persisted numerical cooldown follow all five approved anchors");
            eq(h, HighPowerGrowth.values(f.p).position(), active, "cast never certifies active growth");
            eq(h, HighPowerCombat.earnedGrowthPosition(f.p), earned, "cast never grows earned capacity");
            h.assertTrue(AbilityStorage.get(f.p).exp(ID) == 0 && AbilityStorage.get(f.p).levelExperience == xp, "reversal cannot farm mastery or level XP");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void reversal_preserves_native_identity_payload_life_age_pickup_owner_and_position(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(7, new Vec3(.13, -.24, -1.2)); arrow.setBaseDamage(3.5); arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            arrow.getWeaponItem().setDamageValue(12); arrow.getPersistentData().putString("unrelated_fixture", "preserve");
            UUID uuid = arrow.getUUID(); int id = arrow.getId(), age = arrow.tickCount; Vec3 at = arrow.position(), velocity = arrow.getDeltaMovement();
            CompoundTag payload = nativePayload(arrow), data = arrow.getPersistentData().copy();
            h.assertTrue(f.cast(), "ordinary native bow arrow is accepted");
            vector(h, arrow.getDeltaMovement(), velocity.scale(-1), "exact current XYZ velocity is reversed once");
            h.assertTrue(arrow.getUUID().equals(uuid) && arrow.getId() == id && arrow.position().equals(at) && arrow.tickCount == age,
                    "reversal never replaces, teleports, advances or rewinds the original entity");
            h.assertTrue(nativePayload(arrow).equals(payload), "life, in-ground time, owner, leftOwner, pickup, baseDamage, item and bow stay byte-for-byte native");
            CompoundTag after = arrow.getPersistentData().copy(); after.remove(USED);
            h.assertTrue(after.equals(data) && arrow.getOwner() == f.shooter && !arrow.isNoGravity() && !arrow.isNoPhysics(), "only the denial tombstone is added; native gravity and collision remain enabled");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_bow_arrow_returns_to_its_original_skeleton_with_one_exact_contact(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.center(f.shooter, f.eye().add(0, 0, 12)); HighPowerCombat.enroll(f.shooter, 5);
            Watch watch = f.watch(f.shooter); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5));
            BigDecimal before = hp(f.shooter), amount = raw(f); double base = arrow.getBaseDamage();
            h.assertTrue(f.cast(), "real enemy bow arrow obtains one paid reversal"); fly(arrow, 12);
            eq(h, hp(f.shooter), before.subtract(amount), "original shooter is a valid native self-hit victim for exactly 2D");
            h.assertTrue(watch.incoming == 1 && watch.posts == 1 && watch.skills == 0 && arrow.isRemoved(), "one actual native contact owns one hurt and normal arrow discard");
            assertArrowSource(h, watch.source, arrow, f.shooter);
            h.assertTrue(arrow.getBaseDamage() == base && arrow.getOwner() == f.shooter, "boost never edits native owner or base damage");
            h.assertTrue(watch.source.getLocalizedDeathMessage(f.shooter).equals(f.shooter.damageSources().arrow(arrow, f.shooter).getLocalizedDeathMessage(f.shooter)), "ordinary localized original-shooter credit is retained");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void first_native_body_wins_without_homing_or_piercing_to_original_shooter(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.center(f.shooter, f.eye().add(0, 0, 14)); HighPowerCombat.enroll(f.shooter, 5);
            Zombie first = f.target(9, true); Arrow arrow = f.arrow(5, new Vec3(0, 0, -1.5));
            BigDecimal shooterHp = hp(f.shooter), firstHp = hp(first);
            h.assertTrue(f.cast(), "straight native return admitted"); fly(arrow, 10);
            eq(h, hp(first), firstHp.subtract(raw(f)), "first actual hostile body receives the bounded boost");
            eq(h, hp(f.shooter), shooterHp, "original shooter behind the first body receives no synthetic or piercing hit");
            h.assertTrue(arrow.isRemoved(), "ordinary successful arrow collision consumes the original");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void four_distinct_native_victims_total_eight_d_and_same_tick_victim_gets_one_boost(GameTestHelper h) {
        for (boolean sameVictim : List.of(false, true)) try (Fixture f = new Fixture(h, 3)) {
            List<Zombie> victims = new ArrayList<>(); List<Arrow> arrows = new ArrayList<>(), nativeControls = new ArrayList<>();
            Zombie nativeVictim = sameVictim ? f.targetAt(f.eye().add(8, 0, 12), true) : null;
            if (sameVictim) {
                victims.add(f.target(12, true));
                for (int i = 0; i < 4; i++) {
                    arrows.add(f.arrow(5 + i * .25, new Vec3(0, 0, -1.5)));
                    nativeControls.add(f.arrowAt(f.eye().add(8, 0, 5 + i * .25), new Vec3(0, 0, 1.5)));
                }
            } else {
                // A narrow common admission corridor fans only because the four incoming velocities differ.
                double[] side = {-.6, -.2, .2, .6};
                for (double x : side) {
                    Vec3 start = f.eye().add(x, 0, 5), velocity = new Vec3(x * .65, 0, 1.5);
                    victims.add(f.targetAt(start.add(velocity.scale(4)), true)); arrows.add(f.arrowAt(start, velocity.scale(-1)));
                }
            }
            h.assertTrue(f.cast(), "four ordinary native arrows reverse in one transaction");
            BigDecimal sum = BigDecimal.ZERO, ordinaryLater = BigDecimal.ZERO;
            for (int i = 0; i < arrows.size(); i++) {
                if (sameVictim) { victims.getFirst().invulnerableTime = 0; nativeVictim.invulnerableTime = 0; }
                fly(arrows.get(i), 12);
                if (sameVictim) {
                    BigDecimal beforeControl = hp(nativeVictim); fly(nativeControls.get(i), 12);
                    BigDecimal ordinaryLoss = beforeControl.subtract(hp(nativeVictim));
                    h.assertTrue(ordinaryLoss.signum() > 0, "paired ordinary native arrow actually makes its own contact");
                    if (i > 0) ordinaryLater = ordinaryLater.add(ordinaryLoss);
                }
            }
            for (Zombie victim : victims) sum = sum.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(victim)));
            if (sameVictim) eq(h, sum, raw(f).add(ordinaryLater),
                    "clearing iframes still permits only one same-tick2D; all three later arrows exactly match real ordinary controls");
            else eq(h, sum, raw(f).multiply(BigDecimal.valueOf(4)), "four native first contacts sum to precisely eight D");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void ordinary_unleased_bow_arrow_remains_literal_native_damage(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie victim = f.target(12, true); Watch watch = f.watch(victim); BigDecimal before = hp(victim);
            Arrow ordinary = f.arrowAt(f.eye().add(0, 0, 9), new Vec3(0, 0, 1.5)); fly(ordinary, 5);
            BigDecimal loss = before.subtract(hp(victim));
            h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.TEN) <= 0 && watch.incoming == 1 && watch.posts == 1 && watch.skills == 0,
                    "unknown ordinary arrow keeps literal native units and one normal contact");
            assertArrowSource(h, watch.source, ordinary, f.shooter);
            h.assertTrue(ordinary.isRemoved() && !ordinary.getPersistentData().contains(USED), "ordinary success and untouched arrow metadata remain native");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_armor_toughness_enchantment_resistance_absorption_reduce_once(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); defenses(h, target); Watch actual = f.watch(target);
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5)); BigDecimal before = hp(target);
            h.assertTrue(f.cast(), "actual return enters native defended body"); fly(arrow, 10);
            BigDecimal expected = DamageMitigation.mitigate(raw(f), new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()), BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(new BigDecimal(".5"));
            eq(h, hp(target), before.subtract(expected), "exact native mitigation applies armor, toughness, protection, resistance and actual absorption once");
            h.assertTrue(actual.posts == 1 && actual.armor > 0 && actual.effects > 0 && actual.enchantments > 0 && actual.absorption == .5F,
                    "real native damage container witnesses all ordinary mitigation stages");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void ordinary_native_full_absorption_consumes_pool_once_and_confirms_absorbed_receipt(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, false), control = f.targetAt(f.eye().add(8, 0, 12), false);
            for (Zombie body : List.of(target, control)) { body.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1)); body.setAbsorptionAmount(8); }
            Watch actual = f.watch(target), ordinary = f.watch(control);
            // A real incoming native modifier reduces this ordinary victim's operand before ordinary reducers.
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) event.setAmount(5); } });
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5)); Arrow baseline = f.arrowAt(f.eye().add(8, 0, 8), new Vec3(0, 0, 2)); baseline.setBaseDamage(2.5);
            h.assertTrue(f.cast(), "paid original reaches real fully absorbing ordinary native victim"); fly(arrow, 10); fly(baseline, 5);
            near(h, target.getHealth(), 20, "full absorption leaves native HP unchanged"); near(h, control.getHealth(), 20, "paired ordinary arrow control leaves HP unchanged");
            near(h, target.getAbsorptionAmount(), 3, "native pool pays exactly the accepted five-point amount"); near(h, control.getAbsorptionAmount(), 3, "paired genuine baseline consumes the same pool once");
            h.assertTrue(actual.incoming == 1 && actual.posts == 1 && actual.absorption == 5 && ordinary.posts == 1 && ordinary.absorption == 5,
                    "real native Post witnesses one fully absorbed body transaction");
            h.assertTrue(arrow.isRemoved() == baseline.isRemoved() && arrow.isRemoved(), "full absorption preserves the ordinary true-hit native arrow continuation");
            h.assertTrue(TrajectoryReturn.receipts(f.p).stream().anyMatch(receipt -> receipt.kind() == TrajectoryReturn.CONTACT
                    && receipt.status() == TrajectoryReturn.ABSORBED && target.getUUID().equals(receipt.victim())), "confirmed native pool loss publishes one absorbed contact receipt");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_front_shield_blocks_and_rear_shield_does_not(GameTestHelper h) {
        for (boolean front : List.of(true, false)) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); shield(target, front ? 180 : 0);
            for (int i = 0; i < 6; i++) target.tick();
            face(target, front ? 180 : 0); h.assertTrue(target.isBlocking(), "real six-tick native shield use is active");
            Watch watch = f.watch(target); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5)); BigDecimal before = hp(target);
            h.assertTrue(f.cast(), "plain arrow reverses toward shield-bearing hostile"); fly(arrow, 10);
            h.assertTrue(watch.shieldEvents > 0 && watch.originalShieldBlocked == front, "native shield-direction boolean follows actual arrow origin");
            eq(h, hp(target), front ? before : before.subtract(raw(f)), "only genuine front shield prevents native boosted damage");
            h.assertFalse(TrajectoryReturnArrowBridge.leased(arrow), "first native shield decision retires its one-use boost");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_equal_iframes_reject_second_actual_contact(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); Watch watch = f.watch(target);
            Arrow first = f.arrow(5, new Vec3(0, 0, -1.5)), second = f.arrow(6, new Vec3(0, 0, -1.5));
            BigDecimal before = hp(target); h.assertTrue(f.cast(), "two originals reverse"); fly(first, 12); fly(second, 12);
            eq(h, hp(target), before.subtract(raw(f)), "ordinary native iframes cannot replay or pool the second arrow");
            h.assertTrue(watch.incoming == 2 && watch.posts == 1, "two genuine native attempted contacts create only one HP commit");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void unsupported_native_projectiles_refuse_without_airhammer_fallback(GameTestHelper h) {
        for (int mode = 0; mode < 14; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie victim = f.target(15, true); Arrow arrow;
            if (mode == 0) {
                // Pinned Projectile.setOwner(null) leaves the existing owner intact. Construct the
                // ordinary arrow without an owner, retaining the same plain arrow and bow payload.
                Vec3 at = f.eye().add(0, 0, 6);
                arrow = new Arrow(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                f.addArrow(arrow, at, new Vec3(0, 0, -1));
                arrow.tick(); arrow.setPos(at); arrow.setDeltaMovement(0, 0, -1);
                h.assertTrue(arrow.getOwner() == null && !arrow.saveWithoutId(new CompoundTag()).hasUUID("Owner"),
                        "unknown-owner fixture has neither a cached native owner nor a serialized owner UUID");
            } else arrow = f.arrow(6, new Vec3(0, 0, -1));
            switch (mode) {
                case 0 -> { }
                case 1 -> arrow.setOwner(f.p);
                case 2 -> arrow.setCritArrow(true);
                case 3 -> arrow.setRemainingFireTicks(100);
                case 4 -> arrow.setNoGravity(true);
                case 5 -> arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
                case 6 -> { CompoundTag saved = arrow.saveWithoutId(new CompoundTag()); saved.putByte("PierceLevel", (byte)1); arrow.load(saved); }
                case 7 -> arrow.getPersistentData().putBoolean("academy:protected", true);
                case 8 -> arrow.getPersistentData().putBoolean("academy:immovable", true);
                case 9 -> arrow.getPickupItemStackOrigin().set(DataComponents.CUSTOM_NAME, Component.literal("Unknown payload"));
                case 10 -> { CompoundTag data = new CompoundTag(); data.putString("unreviewed", "payload"); arrow.getWeaponItem().set(DataComponents.CUSTOM_DATA, CustomData.of(data)); }
                case 11 -> arrow.setOwner(f.cow(f.eye().add(6, 0, 12)));
                case 12 -> { arrow.discard(); SpectralArrow spectral = new SpectralArrow(EntityType.SPECTRAL_ARROW, h.getLevel()); spectral.setOwner(f.shooter); f.addArrow(spectral, f.eye().add(0, 0, 6), new Vec3(0, 0, -1)); }
                case 13 -> { arrow.discard(); Snowball snowball = new Snowball(h.getLevel(), f.shooter); snowball.setPos(f.eye().add(0, 0, 6)); snowball.setDeltaMovement(0, 0, -1); h.getLevel().addFreshEntity(snowball); f.entities.add(snowball); }
            }
            double cp = f.cp(); Vec3 velocity = arrow.getDeltaMovement();
            h.assertFalse(f.cast(), "unknown/unsupported candidate rejects whole snapshot " + mode);
            near(h, f.cp(), cp, "unsupported candidate is free " + mode); eq(h, hp(victim), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "unsupported arrow presence never becomes an airhammer " + mode);
            vector(h, arrow.getDeltaMovement(), velocity, "unsupported original motion remains untouched " + mode);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void arrow_selection_uses_closed_point_seventy_five_radius_and_forty_meter_range(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 3)) {
            Vec3 point = switch (mode) {
                case 0 -> f.eye().add(.75, 0, 10);
                case 1 -> f.eye().add(.75001, 0, 10);
                case 2 -> f.eye().add(0, 0, 40);
                case 3 -> f.eye().add(0, 0, 40.01);
                default -> f.eye().add(0, 0, -.01);
            };
            Arrow arrow = f.arrowAt(point, new Vec3(0, 0, -.25)); Vec3 before = arrow.getDeltaMovement(); double cp = f.cp(); boolean accepted = mode == 0 || mode == 2;
            h.assertTrue(f.cast() == accepted, "server point corridor includes exact boundaries only " + mode);
            vector(h, arrow.getDeltaMovement(), before.scale(accepted ? -1 : 1), "only in-range native original reverses " + mode);
            near(h, f.cp(), cp - (accepted ? 4.5 : 0), "outside corridor does not invent a paid contact");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void bounded_scan_overflow_refuses_before_reversal_payment_or_fallback(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1)); Zombie target = f.target(15, true);
            for (int i = 0; i < 65; i++) f.cow(f.eye().add(.65, 0, 3 + i * .05));
            double cp = f.cp(); Vec3 velocity = arrow.getDeltaMovement();
            h.assertFalse(f.cast(), "more than64 broadphase bodies refuse the complete bounded scan");
            near(h, f.cp(), cp, "scan overflow is free"); vector(h, arrow.getDeltaMovement(), velocity, "overflow cannot partially mutate the early eligible arrow");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "overflow cannot select empty-candidate fallback");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void zero_cp_and_forged_or_replayed_input_do_not_mutate_native_arrows(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1)); Vec3 before = arrow.getDeltaMovement();
            String malformed = new HighPowerInput.Input(0, 1, f.p.getId() + 1, f.epoch()).wire();
            h.assertFalse(TrajectoryReturn.tap(f.p, malformed), "wrong native entity identity cannot claim the input");
            f.budget().putDouble("cp", 0); String wire = f.wire(); h.assertFalse(TrajectoryReturn.tap(f.p, wire), "failed whole-cast CP refuses without mutation");
            vector(h, arrow.getDeltaMovement(), before, "failed debit leaves exact original motion");
            h.assertFalse(arrow.getPersistentData().contains(USED) || TrajectoryReturnArrowBridge.leased(arrow), "failed debit cannot consume one-use arrow authority");
            f.budget().putDouble("cp", 100); h.assertFalse(TrajectoryReturn.tap(f.p, wire), "replaying the already consumed failed input cannot retry payment");
            vector(h, arrow.getDeltaMovement(), before, "replayed input cannot reverse");
        }
        h.succeed();
    }

    /** Separate synchronous batch: the legacy harness must exercise the production scaling listener here. */
    @GameTest(template=TEMPLATE, batch="highpower_trajectory_return_classic_identity", timeoutTicks=20)
    public static void normal_category_airhammer_retains_two_d_while_real_classic_railgun_still_scales(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        try (Fixture f = new Fixture(h, 3)) {
            h.assertFalse(EsperLegacyBypass.standAside(), "the genuine production scaling listener is active");
            Zombie target = f.target(8, true); Watch watch = f.watch(target);
            BigDecimal before = hp(target), damage = raw(f); double cp = f.cp();
            h.assertTrue(f.cast(), "real authenticated empty-corridor cast reaches native hurt under normal category rules");
            eq(h, before.subtract(hp(target)), damage, "airhammer keeps its closed two-D allocation without a second classic lift");
            h.assertTrue(watch.skills == 1 && watch.incoming == 1 && watch.posts == 1,
                    "one skill callback and one genuine native incoming/Post own the actual damage");
            near(h, f.cp(), cp - 4.5, "the accepted actual airhammer pays exactly once");

            var state = AbilityStorage.get(f.p); state.selectCategory("electromaster"); state.setLevel(3);
            state.learn("railgun"); state.activated = true; state.overloadFine = true;
            Zombie classicTarget = f.target(14, true); BigDecimal classicBefore = hp(classicTarget);
            BigDecimal expected = HighPowerGrowth.values(f.p).damage().multiply(new BigDecimal("2.2"));
            h.assertTrue(cn.academy.port.AbilityDamage.attack(f.p, "electromaster.railgun", classicTarget, 110),
                    "source-catalogued classic railgun still makes a real native hit");
            BigDecimal lost = classicBefore.subtract(hp(classicTarget));
            h.assertTrue(lost.subtract(expected).abs().compareTo(expected.multiply(new BigDecimal(".000001"))) <= 0,
                    "original advanced railgun still lifts110 points to2.2D: actual=" + lost + " expected=" + expected);
        } finally { EsperLegacyBypass.force(false); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void empty_corridor_airhammer_hits_only_first_hostile_for_two_d_and_charges_once(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie first = f.target(8, true), second = f.target(14, true); Watch watch = f.watch(first); double cp = f.cp();
            h.assertTrue(f.cast(), "truly empty projectile candidate set permits one thin airhammer");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(raw(f)), "first native hostile receives exactly two D");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "airhammer cannot pierce or pool another target"); near(h, f.cp(), cp - 4.5, "whole fallback pays once");
            h.assertTrue(watch.incoming == 1 && watch.posts == 1 && watch.skills == 1 && watch.source.getEntity() == f.p, "one normal native player damage call owns the fallback");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void empty_miss_nonhostile_body_and_solid_wall_block_airhammer_for_free(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = mode == 0 ? null : f.target(15, true);
            if (mode == 1) f.cow(f.eye().add(0, 0, 6));
            if (mode == 2) f.block(f.eye().add(0, 0, 6));
            if (mode == 3) { target.getPersistentData().putBoolean("academy:protected", true); }
            double cp = f.cp(); h.assertFalse(f.cast(), "empty, nonhostile, solid or protected first contact is refused " + mode);
            near(h, f.cp(), cp, "no legal first native contact is free");
            if (target != null) eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "blocker cannot be skipped to hostile behind it");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void airhammer_has_thin_geometry_and_forty_meter_endpoint(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(20, true); double half = target.getBbWidth() / 2.0;
            if (mode < 2) f.center(target, f.eye().add(half + (mode == 0 ? .034 : .08), 0, 20));
            else f.center(target, f.eye().add(0, 0, 40 + half + (mode == 2 ? -.02 : .08)));
            double cp = f.cp(); boolean contact = mode == 0 || mode == 2;
            h.assertTrue(f.cast() == contact, "actual thin first body and closed40m boundary govern fallback " + mode);
            eq(h, hp(target), contact ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(raw(f)) : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "airhammer never widens to arrow selection radius");
            near(h, f.cp(), cp - (contact ? 4.5 : 0), "geometric miss stays free");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_impact_cancel_and_damage_stage_veto_never_replay_old_operand(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); Watch watch = f.watch(target); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5));
            final int veto = mode; f.hook(new Object() {
                @SubscribeEvent public void impact(ProjectileImpactEvent event) { if (veto == 0 && event.getEntity() == arrow) event.setCanceled(true); }
                @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) { if (veto == 1) event.setCanceled(true); else if (veto == 2) event.setAmount(0); } }
                @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) { if (veto == 3 && event.getEntity() == target) event.setNewDamage(0); }
            });
            double cp = f.cp(); h.assertTrue(f.cast(), "paid reverse precedes real native veto " + mode); fly(arrow, 10);
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "native veto cannot replay ordinary arrow damage " + mode);
            near(h, f.cp(), cp - 4.5, "later veto does not refund the completed reversal");
            h.assertTrue(watch.incoming == (mode == 0 ? 0 : 1) && !TrajectoryReturnArrowBridge.leased(arrow), "first real decision consumes one native boost " + mode);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void impact_callback_reentry_consumes_boost_before_mutable_event(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5)); boolean[] entered = {false};
            f.hook(new Object() { @SubscribeEvent public void impact(ProjectileImpactEvent event) {
                if (event.getEntity() == arrow && event.getRayTraceResult() instanceof EntityHitResult && !entered[0]) { entered[0] = true; arrow.tick(); }
            } });
            h.assertTrue(f.cast(), "paid original has one native permit before reentry"); fly(arrow, 12);
            BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
            h.assertTrue(entered[0] && loss.signum() >= 0 && loss.compareTo(BigDecimal.valueOf(100)) < 0, "nested Arrow.tick cannot mint or reclaim either2D operand");
            h.assertFalse(TrajectoryReturnArrowBridge.leased(arrow), "reentrant first contact permanently retires its boost");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_contact_owner_payload_movement_and_caster_resource_changes_revoke_boost(GameTestHelper h) {
        for (int mode = 0; mode < 6; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5));
            h.assertTrue(f.cast(), "original receives paid boost before mutation " + mode);
            switch (mode) {
                case 0 -> arrow.setOwner(f.shooter);
                case 1 -> arrow.setBaseDamage(3);
                case 2 -> arrow.getWeaponItem().setDamageValue(3);
                case 3 -> arrow.setPos(arrow.position().add(.01, 0, 0));
                case 4, 5 -> { final int mutation = mode; f.hook(new Object() {
                    @SubscribeEvent(priority=EventPriority.LOW) public void incoming(LivingIncomingDamageEvent event) {
                        if (event.getEntity() != target) return;
                        if (mutation == 4) f.p.setPos(f.p.position().add(2, 0, 0));
                        else f.budget().putDouble("cp", f.cp() + 1);
                    }
                }); }
            }
            fly(arrow, 12); BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
            h.assertTrue(loss.signum() >= 0 && loss.compareTo(BigDecimal.valueOf(100)) < 0, "identity/resource/movement mutation cannot retain exact boost " + mode);
            h.assertFalse(TrajectoryReturnArrowBridge.leased(arrow), "mutation retires original authority " + mode);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void airhammer_callbacks_revalidate_target_owner_resources_and_reentry(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(12, true); final int mutation = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void attack(SkillAttackEvent event) {
                if (event.target != target || !event.skill.equals(SKILL) || called[0]) return; called[0] = true;
                switch (mutation) {
                    case 0 -> target.setPos(target.position().add(2, 0, 0));
                    case 1 -> f.p.setPos(f.p.position().add(2, 0, 0));
                    case 2 -> f.budget().putDouble("cp", f.cp() + 1);
                    case 3 -> { h.assertFalse(f.cast(), "callback cannot recursively consume another cast"); TrajectoryReturn.remove(f.p); }
                    case 4 -> event.amount = 0;
                }
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "paid fallback enters exactly one real skill callback " + mode);
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "post-callback frozen target and owner guards precede native HP write " + mode);
            near(h, f.cp(), cp - 4.5 + (mode == 2 ? 1 : 0), "one irreversible cast debit retains external resource changes");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_first_reverse_packet_mutation_or_reentry_stops_partial_batch_without_refund(GameTestHelper h) {
        for (boolean reentry : List.of(false, true)) try (Fixture f = new Fixture(h, 3)) {
            Arrow first = f.arrow(5, new Vec3(0, 0, -1)), second = f.arrow(7, new Vec3(0, 0, -1));
            Zombie target = f.target(12, true); Vec3 firstBefore = first.getDeltaMovement(), secondBefore = second.getDeltaMovement();
            double cp = f.cp(); int[] observations = {0}; Throwable[] failure = {null}; boolean[] casting = {false};
            ChannelOutboundHandlerAdapter observer = new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TrajectoryReturn.Visual visual
                            && visual.ownerUuid().equals(f.p.getUUID()) && visual.kind() == TrajectoryReturn.REVERSE && visual.slot() == 0 && observations[0]++ == 0) {
                        try {
                            h.assertTrue(casting[0] && f.p.server.isSameThread(), "real first reverse packet is sent synchronously within the native transaction");
                            vector(h, first.getDeltaMovement(), firstBefore.scale(-1), "first original already committed before its actual outbound receipt");
                            vector(h, second.getDeltaMovement(), secondBefore, "second frozen original has not yet been rewritten");
                            if (reentry) h.assertFalse(f.cast(), "packet callback cannot recursively admit another cast");
                            else second.setNoGravity(true);
                        } catch (Throwable problem) { failure[0] = problem; }
                    }
                    context.write(message, promise);
                }
            };
            f.channel.pipeline().addFirst("trajectory_partial_commit_observer", observer); boolean accepted;
            try { casting[0] = true; accepted = f.cast(); }
            finally { casting[0] = false; f.channel.pipeline().remove(observer); }
            if (failure[0] != null) throw new AssertionError("actual native outbound reversal observation failed", failure[0]);
            h.assertTrue(observations[0] == 1 && !accepted, "one actual outbound callback prevents completion of the remaining frozen batch");
            vector(h, first.getDeltaMovement(), firstBefore.scale(-1), "completed first reversal remains committed after partial failure");
            vector(h, second.getDeltaMovement(), secondBefore, "changed second arrow cannot be retried, replaced or silently reversed");
            near(h, f.cp(), cp - 4.5, "partial committed batch spends one whole-cast CP payment with no refund");
            h.assertTrue(first.getPersistentData().getBoolean(USED) && !second.getPersistentData().contains(USED), "one-use quota follows only the actual committed original");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "partial batch never switches into empty-candidate airhammer");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_contact_point_must_fit_both_frozen_and_current_caster_forty_meter_spheres(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 3)) {
            // At40.5m the native padded near face is39.9m away horizontally; the actual hit point,
            // including its native Y, is inside40m even though the hostile body center is outside.
            double distance = mode == 1 ? 41.5 : 40.5;
            Zombie target = f.target(distance, true); Arrow arrow = f.arrow(34, new Vec3(0, 0, -4)); Watch watch = f.watch(target);
            Vec3 frozen = f.p.position(); h.assertTrue(f.cast(), "in-range ordinary arrow is reversed before later range decision");
            if (mode == 1) f.p.setPos(frozen.add(0, 0, 3));
            if (mode == 2) f.p.setPos(frozen.add(0, 0, -3));
            fly(arrow, 5); BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
            h.assertTrue(watch.incoming == 1 && arrow.isRemoved(), "each range case is an actual native body hit with ordinary success continuation");
            if (mode == 0) eq(h, loss, raw(f), "native near-face contact inside both spheres receives2D despite body center outside40m");
            else h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.valueOf(100)) < 0,
                    "outside frozen or current caster40m contact retains only ordinary native arrow damage " + mode);
            final boolean inRange = mode == 0;
            h.assertTrue(TrajectoryReturn.receipts(f.p).stream().filter(receipt -> receipt.kind() == TrajectoryReturn.CONTACT)
                    .allMatch(receipt -> inRange || receipt.status() == TrajectoryReturn.REFUSED), "out-of-range real contact never publishes a boost success");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=40)
    public static void twenty_real_ticks_expire_boost_without_freezing_or_reversing_native_motion(GameTestHelper h) {
        Fixture f = new Fixture(h, 3); Arrow arrow = f.arrow(6, new Vec3(0, .1, -.25)); Vec3 start = arrow.position();
        h.assertTrue(f.cast() && TrajectoryReturnArrowBridge.leased(arrow), "actual slow original has a live20tick permit");
        h.runAfterDelay(21, () -> {
            h.assertTrue(!TrajectoryReturnArrowBridge.leased(arrow) && arrow.getPersistentData().getBoolean(USED), "twenty genuine server ticks retire boost and keep denial tombstone");
            h.assertTrue(!arrow.position().equals(start) && arrow.getDeltaMovement().y < 0 && !arrow.isNoGravity(), "native travel, drag and gravity continue throughout expiry");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void forty_meters_actual_flight_exhausts_boost_before_later_native_contact(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Zombie target = f.target(51, true); Arrow arrow = f.arrow(6, new Vec3(0, 0, -7.5));
            // The fixture starts safely in the corridor and then traverses genuine native flight segments.
            h.assertTrue(f.cast(), "fast reviewed ordinary original has one40m return budget"); fly(arrow, 12);
            BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
            h.assertTrue(loss.signum() >= 0 && loss.compareTo(BigDecimal.valueOf(100)) < 0, "native body beyond40m cannot consume2D even inside the20tick deadline");
            h.assertFalse(TrajectoryReturnArrowBridge.leased(arrow), "actual native segment sum exhausts boost");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void saved_arrow_and_owner_reload_keep_tombstone_and_do_not_resume_boost(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1)); h.assertTrue(f.cast(), "paid reversal before native serialization");
            CompoundTag arrowTag = arrow.saveWithoutId(new CompoundTag()), fences = f.p.getPersistentData().getCompound(FENCES).copy(); double cp = f.cp();
            f.reload(); h.assertFalse(TrajectoryReturnArrowBridge.leased(arrow), "owner native reload retires live flight authority");
            h.assertTrue(f.p.getPersistentData().getCompound(FENCES).equals(fences), "native player save/load preserves cooldown and rolling quota fences");
            near(h, f.cp(), cp, "reload cannot refund or refill the paid budget");
            arrow.discard(); Arrow restored = new Arrow(EntityType.ARROW, h.getLevel()); restored.load(arrowTag); h.assertTrue(h.getLevel().addFreshEntity(restored), "saved native original re-enters level"); f.entities.add(restored);
            h.assertTrue(restored.getUUID().equals(arrow.getUUID()) && restored.getPersistentData().getBoolean(USED), "same original UUID retains denial-only one-use tombstone");
            h.assertFalse(TrajectoryReturnArrowBridge.leased(restored) || TrajectoryReturnArrowBridge.eligibleArrow(f.p, restored), "neither owner reload nor arrow reload mints another flight permit");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void native_wall_embedding_and_pickup_preserve_one_original_arrow_item(GameTestHelper h) {
        Fixture f = new Fixture(h, 3);
        Arrow returned = f.arrow(6, new Vec3(0, 0, -1.5)); returned.pickup = AbstractArrow.Pickup.ALLOWED;
        f.block(f.eye().add(0, 0, 11)); int before = f.arrowItems();
        h.assertTrue(f.cast(), "native original reverses toward a real solid wall"); fly(returned, 20);
        h.assertTrue(returned.inGround && !returned.isRemoved(), "actual ordinary block impact embeds original arrow");
        for (int i = 0; i < 8; i++) returned.tick();
        int life = returned.saveWithoutId(new CompoundTag()).getShort("life");
        h.assertTrue(life > 0 && returned.shakeTime == 0 && !TrajectoryReturnArrowBridge.leased(returned), "native lifetime and pickup delay continue after impact");
        // Player.aiStep owns pickup discovery and excludes removed entities before playerTouch.
        // Keep the connected player overlapping for several genuine server ticks, including ticks
        // after collection, rather than calling playerTouch directly on an already discarded arrow.
        f.p.setPos(returned.position().add(0, -.5, -.75)); f.p.setDeltaMovement(Vec3.ZERO); f.p.connection.resetPosition();
        h.assertTrue(h.getLevel().getEntities(f.p, f.p.getBoundingBox().inflate(1, .5, 1)).contains(returned),
                "live embedded original lies inside the genuine native player pickup scan");
        h.runAfterDelay(3, () -> {
            h.assertTrue(returned.isRemoved() && f.arrowItems() == before + 1,
                    "actual server player ticks collect exactly one original arrow item");
        });
        h.runAfterDelay(7, () -> {
            h.assertTrue(returned.isRemoved() && f.arrowItems() == before + 1,
                    "later native pickup scans cannot duplicate the removed original item");
            h.assertFalse(h.getLevel().getEntities(f.p, f.p.getBoundingBox().inflate(1, .5, 1)).contains(returned),
                    "native entity lookup no longer exposes the collected original to player pickup");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void foreign_persistent_arrow_leases_refuse_new_return_without_fallback(GameTestHelper h) {
        for (String key : List.of(MagneticArrowSeparation.LEASE_KEY, MagneticDomainArrows.LEASE_KEY, IronSandLance.LEASE_KEY,
                "academy:ground_step_arrow_until", "academy:highpower_bracer_steering_until")) try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1)); Zombie target = f.target(12, true);
            arrow.getPersistentData().putString(key, "malformed_foreign_lease"); double cp = f.cp(); Vec3 velocity = arrow.getDeltaMovement();
            h.assertFalse(TrajectoryReturnArrowBridge.eligibleArrow(f.p, arrow) || f.cast(), "foreign shared-writer record fails closed: " + key);
            vector(h, arrow.getDeltaMovement(), velocity, "foreign native writer keeps original motion"); near(h, f.cp(), cp, "foreign claim costs no return CP");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "foreign arrow presence never invokes fallback");
        }
        try (Fixture f = new Fixture(h, 3)) {
            Arrow arrow = f.arrow(6, new Vec3(0, 0, -1)); h.assertTrue(f.cast(), "one genuine return lease is established");
            h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow) || ShortInterceptionArrowBridge.snapshot(f.p, arrow) != null,
                    "old native interception path cannot acquire the already returned original");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void friendly_monster_and_passive_first_contacts_keep_only_native_arrow_damage(GameTestHelper h) {
        for (boolean passive : List.of(true, false)) try (Fixture f = new Fixture(h, 3)) {
            LivingEntity target = passive ? f.cow(f.eye().add(0, 0, 12)) : f.target(12, true);
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("tr" + UUID.randomUUID().toString().substring(0, 10));
            try {
                board.addPlayerToTeam(f.p.getScoreboardName(), team); board.addPlayerToTeam(target.getScoreboardName(), team);
                Watch watch = f.watch(target); BigDecimal before = hp(target); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5));
                h.assertTrue(f.cast(), "hostile shooter remains valid while return path contains a friendly body"); fly(arrow, 12);
                BigDecimal loss = before.subtract(hp(target));
                h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.TEN) <= 0 && watch.skills == 0,
                        "first friendly/passive body receives only its ordinary literal native arrow amount");
                assertArrowSource(h, watch.source, arrow, f.shooter);
                h.assertTrue(arrow.isRemoved() && !TrajectoryReturnArrowBridge.leased(arrow), "ordinary first contact consumes arrow and boost opportunity");
            } finally { board.removePlayerTeam(team); }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=85)
    public static void friendly_connected_player_first_contact_never_receives_boost(GameTestHelper h) {
        Fixture f = new Fixture(h, 3), friend = new Fixture(h, 3, 8);
        h.runAfterDelay(61, () -> {
            friend.p.setPos(f.eye().add(0, -friend.p.getBbHeight() / 2, 12)); friend.p.connection.resetPosition();
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("trp" + UUID.randomUUID().toString().substring(0, 9));
            try {
                board.addPlayerToTeam(f.p.getScoreboardName(), team); board.addPlayerToTeam(friend.p.getScoreboardName(), team);
                Watch watch = f.watch(friend.p); BigDecimal health = hp(friend.p); Arrow arrow = f.arrow(6, new Vec3(0, 0, -1.5));
                h.assertTrue(f.cast(), "enemy skeleton arrow reverses despite a friendly player on the real return route"); fly(arrow, 12);
                BigDecimal loss = health.subtract(hp(friend.p));
                h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.TEN) <= 0 && watch.skills == 0 && watch.incoming == 1,
                        "after genuine login immunity expires, friendly player keeps only literal native skeleton-arrow damage");
                assertArrowSource(h, watch.source, arrow, f.shooter);
            } finally { board.removePlayerTeam(team); }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_entity_manager_unload_and_same_uuid_rejoin_never_resume_boost(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            Arrow original = f.arrow(20, new Vec3(0, 0, -1)); int beforeNativeTick = original.tickCount;
            // Arrow.tick does not advance Entity.tickCount; the pinned ServerLevel dispatcher does.
            h.getLevel().tickNonPassenger(original);
            h.assertTrue(original.tickCount == beforeNativeTick + 1, "native level dispatch advances original runtime age exactly once");
            h.assertTrue(f.cast(), "original arrow has paid live native lease before unload");
            CompoundTag saved = original.saveWithoutId(new CompoundTag()); int age = original.tickCount;
            PersistentEntitySectionManager<Entity> manager = manager(h); ChunkPos chunk = new ChunkPos(original.blockPosition());
            try { manager.updateChunkStatus(chunk, Visibility.HIDDEN); nativeUnload(manager, original); }
            finally { manager.updateChunkStatus(chunk, Visibility.TICKING); }
            h.assertTrue(original.isRemoved() && original.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                    "actual native entity manager performs final chunk-unload removal");
            Arrow restored = new Arrow(EntityType.ARROW, h.getLevel()); restored.load(saved);
            h.assertTrue(restored.saveWithoutId(new CompoundTag()).getShort("life") == saved.getShort("life"),
                    "native load restores the exact saved arrow lifetime before level insertion");
            h.assertTrue(original.tickCount == age && age > 0 && restored.tickCount == 0,
                    "runtime tickCount is genuinely advanced before unload and is not a serialized lifetime field");
            h.assertTrue(h.getLevel().addFreshEntity(restored), "same saved UUID rejoins through genuine level insertion"); f.entities.add(restored);
            TrajectoryReturnArrowBridge.prune(f.p.server);
            h.assertTrue(restored.getUUID().equals(original.getUUID()) && restored.getId() != original.getId()
                    && restored.getPersistentData().getBoolean(USED), "native reload retains UUID and one-use denial on a new runtime object");
            h.assertFalse(TrajectoryReturnArrowBridge.leased(original) || TrajectoryReturnArrowBridge.leased(restored)
                    || TrajectoryReturnArrowBridge.eligibleArrow(f.p, restored), "neither removed object nor same-UUID rejoin can resurrect damage or reverse again");
            h.assertTrue(restored.saveWithoutId(new CompoundTag()).getShort("life") == saved.getShort("life"),
                    "native insertion and boost pruning preserve the exact serialized arrow lifetime");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void real_server_ticks_enforce_four_tick_request_floor_and_rolling_four_arrow_quota(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); List<Arrow> first = new ArrayList<>();
        for (int i = 0; i < 4; i++) first.add(f.arrow(5 + i, new Vec3(0, .2, -.2)));
        h.assertTrue(f.cast(), "first native cast fills all four rolling quota slots"); first.forEach(Entity::discard);
        Arrow pending = f.arrow(10, new Vec3(0, .2, -.2)); double cp = f.cp();
        h.assertFalse(f.cast(), "new authenticated input cannot pass same-tick physical floor");
        h.runAfterDelay(4, () -> {
            double before = f.cp(); h.assertFalse(f.cast(), "expired four-tick cooldown still respects original rolling20tick quota"); near(h, f.cp(), before, "quota refusal adds no CP payment");
            h.assertFalse(pending.getPersistentData().contains(USED), "quota refusal cannot consume another arrow");
        });
        h.runAfterDelay(20, () -> {
            pending.discard(); Arrow fresh = f.arrow(10, new Vec3(0, 0, -1));
            h.assertTrue(f.cast() && fresh.getPersistentData().getBoolean(USED), "twenty actual server ticks release quota for a genuinely new original");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void free_empty_request_still_has_four_genuine_tick_floor(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); double cp = f.cp();
        h.assertFalse(f.cast(), "empty native miss is free"); near(h, f.cp(), cp, "empty miss cannot debit CP");
        Zombie target = f.target(12, true);
        h.assertFalse(f.cast(), "new target cannot bypass same-tick request floor after empty miss");
        h.runAfterDelay(3, () -> { h.assertFalse(f.cast(), "third genuine tick remains inside physical request floor"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no early airhammer"); });
        h.runAfterDelay(4, () -> { h.assertTrue(f.cast(), "fourth genuine tick allows a fresh authenticated opportunity"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(raw(f)), "eligible fresh contact receives one2D"); h.succeed(); });
    }

    @SuppressWarnings("unchecked") private static PersistentEntitySectionManager<Entity> manager(GameTestHelper h) {
        try { var field = ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true); return (PersistentEntitySectionManager<Entity>)field.get(h.getLevel()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native entity manager unavailable", failure); }
    }
    private static void nativeUnload(PersistentEntitySectionManager<Entity> manager, Entity entity) {
        try { var method = PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", EntityAccess.class); method.setAccessible(true); method.invoke(manager, entity); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native final unload failed", failure); }
    }

    private static BigDecimal raw(Fixture f) { return HighPowerGrowth.values(f.p).damage().multiply(BigDecimal.valueOf(2)).min(CombatAmounts.CAP); }
    private static BigDecimal hp(LivingEntity entity) { return HighPowerCombat.enabled(entity) ? new BigDecimal(HighPowerCombat.healthText(entity)) : new BigDecimal(Float.toString(entity.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-12, why + ": " + actual + " != " + expected); }
    private static void fly(Arrow arrow, int ticks) { for (int i = 0; i < ticks && !arrow.isRemoved(); i++) arrow.tick(); }
    private static CompoundTag nativePayload(Arrow arrow) { CompoundTag tag = arrow.saveWithoutId(new CompoundTag()); tag.remove("Motion"); tag.remove("NeoForgeData"); return tag; }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; }
    private static void shield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertArrowSource(GameTestHelper h, DamageSource source, Arrow arrow, LivingEntity shooter) {
        h.assertTrue(source != null && source.is(DamageTypes.ARROW) && source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() == arrow && source.getEntity() == shooter && source.sourcePositionRaw() == null,
                "original native arrow/direct entity/shooter and source-position mode remain intact");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "return grants no native defense bypass tags");
    }
    public static final class Watch {
        final LivingEntity target; int incoming, posts, skills, shieldEvents; DamageSource source;
        boolean originalShieldBlocked; float armor, effects, enchantments, absorption;
        Watch(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) { incoming++; source = event.getSource(); } }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { shieldEvents++; originalShieldBlocked = event.getOriginalBlock(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) { if (event.getEntity() == target) { posts++; armor = event.getReduction(DamageContainer.Reduction.ARMOR); effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION); } }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 anchor; final Skeleton shooter;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>();
        long inputs; boolean closed;
        Fixture(GameTestHelper h, int level) { this(h, level, 0); }
        Fixture(GameTestHelper h, int level, double offset) {
            this.h = h; anchor = h.absoluteVec(new Vec3(8.5 + offset, 5, 5.5));
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "TR" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); place(); h.assertTrue(HighPowerCombat.enroll(p, Math.max(3, level)), "declared genuine connected caster enrollment");
            var state = AbilityStorage.get(p); state.selectCategory("vecmanip"); state.setLevel(Math.max(3, level));
            for (String id : List.of("deflection_strike", "braking_touch", "yaw_palm", ID)) state.learn(id);
            state.activated = true; state.overloadFine = true; h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "real native input binding"); HighPowerResources.initializeBudget(p);
            if (level < 3) { state.setLevel(level); HighPowerGrowth.values(p); state.setLevel(3); HighPowerGrowth.values(p); state.activated = true; state.overloadFine = true; }
            shooter = new Skeleton(EntityType.SKELETON, h.getLevel()); add(shooter, eye().add(6, 0, 14));
            shooter.getAttribute(Attributes.ARMOR).setBaseValue(0); shooter.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) { }
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void acknowledgeLogin() { channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        void place() { p.moveTo(anchor.x, anchor.y, anchor.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Vec3 eye() { return p.getEyePosition(); }
        void center(Entity entity, Vec3 point) { entity.setPos(point.add(0, -entity.getBbHeight() / 2, 0)); }
        void add(LivingEntity entity, Vec3 point) { if (entity instanceof Mob mob) mob.setNoAi(true); entity.setNoGravity(true); entity.setSilent(true); center(entity, point); h.assertTrue(h.getLevel().addFreshEntity(entity), "genuine native body joins level"); entities.add(entity); }
        Zombie target(double distance, boolean exact) { return targetAt(eye().add(0, 0, distance), exact); }
        Zombie targetAt(Vec3 point, boolean exact) { Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); add(mob, point); if (exact) h.assertTrue(HighPowerCombat.enroll(mob, 5), "declared exact native hostile enrollment"); return mob; }
        Cow cow(Vec3 point) { Cow cow = new Cow(EntityType.COW, h.getLevel()); add(cow, point); return cow; }
        Arrow arrow(double distance, Vec3 velocity) { return arrowAt(eye().add(0, 0, distance), velocity); }
        Arrow arrowAt(Vec3 at, Vec3 velocity) { Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW)); addArrow(arrow, at, velocity); arrow.tick(); arrow.setPos(at); arrow.setDeltaMovement(velocity); return arrow; }
        void addArrow(AbstractArrow arrow, Vec3 at, Vec3 velocity) { arrow.setPos(at); arrow.setDeltaMovement(velocity); h.assertTrue(h.getLevel().addFreshEntity(arrow), "genuine ordinary projectile joins actual world"); entities.add(arrow); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire() { return new HighPowerInput.Input(0, ++inputs, p.getId(), epoch()).wire(); }
        boolean cast() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("trajectory_return_tap", wire())); }
        double cp() { return HighPowerResources.cp(p); }
        CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        int arrowItems() { int result = 0; for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack stack = p.getInventory().getItem(i); if (stack.is(Items.ARROW)) result += stack.getCount(); } return result; }
        void block(Vec3 at) { BlockPos pos = BlockPos.containing(at); previousBlocks.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Watch watch(LivingEntity target) { Watch watch = new Watch(target); hook(watch); return watch; }
        void reload() { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); TrajectoryReturn.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(); AbilityStorage.get(p); epoch(); }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); TrajectoryReturn.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); entities.forEach(Entity::discard); previousBlocks.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); channel.finishAndReleaseAll(); }
    }
}
