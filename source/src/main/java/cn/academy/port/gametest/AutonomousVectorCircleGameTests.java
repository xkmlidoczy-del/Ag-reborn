package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.VectorParryRules;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW452 native contract: real hurt/Arrow.tick, authenticated paid controls and genuine owner ticks.
 * Learned bits and starting balances are explicit fixtures; only the learning test claims acquisition. */
@GameTestHolder("academy_autonomous_vector_circle")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AutonomousVectorCircleGameTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_autonomous_vector_circle", ID = "autonomous_vector_circle";
    private AutonomousVectorCircleGameTests() {}

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), pos = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{64, 18, 64}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) pos.add(IntTag.valueOf(0));
        cell.put("pos", pos); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_autonomous_vector_circle", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void lv5_after_442_requires_complete_paid_advanced_learning_without_resource_or_xp_grants(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
            h.assertTrue(skill.level() == 5 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(MultiAxisReflection.ID, 0)))
                    && HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL == 452, "canonical LV5 NEW452 follows learned NEW442 at zero mastery");
            h.assertTrue(SkillCatalog.ALL.size() == 50 && cn.academy.port.preset.PresetSkills.IMPLEMENTED.size() == 35,
                    "append-only high-power extension does not change the classic catalog or presets");
            int count = ClassicRules.learningStimulations(5), ticks = count * DeveloperType.ADVANCED.ticksPerStimulation();
            double cost = DeveloperType.ADVANCED.actualConsumption(count); var machine = f.advanced(p, cost);
            state.unlearn(MultiAxisReflection.ID); h.assertFalse(f.learnRequest(p), "other branches cannot replace the exact NEW442 parent");
            near(h, machine.battery().getEnergy(), cost, "missing prerequisite consumes no machine IF");
            state.learn(MultiAxisReflection.ID); state.setLevel(4); h.assertFalse(f.learnRequest(p), "parent cannot bypass integer LV5"); state.setLevel(5);
            h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned circle cannot bind the ordinary input slot");
            f.shield(p, n("17.25")); f.reserve(p, "3", "5"); HighPowerGrowth.values(p); f.budget(p).putDouble("cp", 37);
            CompoundTag combat = f.combat(p).copy(), resources = f.budget(p).copy(); double xp = state.levelExperience;
            h.assertTrue(f.learnRequest(p), "real bound Advanced developer starts finite native NEW452 learning");
            f.develop(p, ticks - 1); h.assertFalse(state.learned(ID), "the final native paid tick remains mandatory");
            near(h, machine.battery().getEnergy(), DeveloperType.ADVANCED.energyPerTick(), "exactly one machine payment remains");
            f.develop(p, 1); h.assertTrue(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == xp, "only completed learning grants the skill, with no XP");
            near(h, machine.battery().getEnergy(), 0, "all finite native IF was paid");
            h.assertTrue(combat.equals(f.combat(p)) && resources.equals(f.budget(p)), "learning cannot refill HP, base S, either reserve, CP or growth");
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "genuinely learned NEW452 binds the canonical slot"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void full_three_dimensional_front_cone_honors_horizontal_vertical_and_pitched_facing(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(6);
        f.ready(() -> {
            double inside = Math.toRadians(79.99), outside = Math.toRadians(80.01);
            Vec3[] offsets = {new Vec3(2 * Math.sin(inside), 0, 2 * Math.cos(inside)), new Vec3(2 * Math.sin(outside), 0, 2 * Math.cos(outside)),
                    new Vec3(0, 2 * Math.sin(inside), 2 * Math.cos(inside)), new Vec3(0, 2 * Math.sin(outside), 2 * Math.cos(outside)), new Vec3(0, 2, 2), new Vec3(0, 2, 2)};
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); if (i >= 4) p.setXRot(i == 4 ? -60 : 60); f.arm(p, "20"); BigDecimal hp = hp(p);
                f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(offsets[i]))), 5); boolean protectedHit = i % 2 == 0;
                eq(h, shield(p), n(protectedHit ? "15" : "20"), "actual native cone classification " + i);
                eq(h, hp(p), protectedHit ? hp : hp.subtract(n("5")), "full 3D facing controls native residual " + i);
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == (protectedHit ? 7 : 8), "only positive eligible absorption uses quota");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void exact_eight_meter_melee_origin_is_closed_and_occluded_or_far_sources_keep_native_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); BigDecimal hp = hp(p);
                Zombie attacker = f.zombie(p.position().add(0, 0, i == 1 ? 8.001 : i == 0 ? 8 : 4));
                if (i == 2) { f.block(p.position().add(0, 0, 2), Blocks.STONE.defaultBlockState()); f.block(p.position().add(0, 1, 2), Blocks.STONE.defaultBlockState()); }
                f.hit(p, p.damageSources().mobAttack(attacker), 5);
                eq(h, shield(p), n(i == 0 ? "15" : "20"), "closed range and loaded native visibility " + i);
                eq(h, hp(p), i == 0 ? hp : hp.subtract(n("5")), "ineligible source retains exact ordinary HP loss");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void base_lower_upper_order_is_exact_and_final_partial_absorption_leaves_only_native_hp_residual(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.reserve(p, "3", "4"); f.arm(p, "2"); BigDecimal hp = hp(p), cap = n(HighPowerCombat.maxShieldText(p)); Zombie a = f.front(p);
            f.hit(p, p.damageSources().mobAttack(a), 6);
            eq(h, shield(p), BigDecimal.ZERO, "existing base pays first"); eq(h, share(p, "lower"), BigDecimal.ZERO, "existing lower pays second"); eq(h, share(p, "upper"), n("3"), "only final remainder reaches existing upper"); eq(h, hp(p), hp, "six existing S absorb six residual damage");
            p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, share(p, "upper"), BigDecimal.ZERO, "final three existing upper S are spent one to one"); eq(h, hp(p), hp.subtract(n("2")), "only uncovered two damage reaches HP once");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 6, "two positive absorptions consume exactly two entries");
            p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 3);
            eq(h, hp(p), hp.subtract(n("5")), "fully exhausted next hit follows ordinary native HP path");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 6, "zero S absorption does not consume a quota entry");
            eq(h, n(HighPowerCombat.maxShieldText(p)), cap, "base capacity is unchanged"); eq(h, share(p, "capacity"), cap, "reserve capacity remains exactly one S, total capacity exactly 2S"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_armor_resistance_enchantment_and_absorption_run_once_before_circle_settlement(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10), control = f.player(30, 10);
        f.ready(() -> {
            defenses(h, p); defenses(h, control); f.reserve(p, ".25", ".25"); f.arm(p, "1"); BigDecimal hp = hp(p);
            Watch a = f.watch(p), b = f.watch(control); f.hit(control, control.damageSources().mobAttack(f.front(control)), 20); f.hit(p, p.damageSources().mobAttack(f.front(p)), 20);
            h.assertTrue(a.posts == 1 && b.posts == 1 && a.damage > 1.5 && a.armor > 0 && a.effects > 0 && a.enchantments > 0 && a.absorption == .5F, "actual native mitigation precedes the circle");
            h.assertTrue(a.damage == b.damage && a.armor == b.armor && a.effects == b.effects && a.enchantments == b.enchantments && a.absorption == b.absorption, "every native mitigation stage matches the unprotected control");
            eq(h, total(p), BigDecimal.ZERO, "only available 1.5 S is spent"); eq(h, hp(p), hp.subtract(decimal(a.damage).subtract(n("1.5"))), "exact already-mitigated remainder reaches HP once");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "circle adds no movement beyond native knockback"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_cancel_zero_damage_resistance_and_full_absorption_preserve_all_s_and_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.reserve(p, "3", "4"); f.arm(p, "20"); BigDecimal hp = hp(p);
                if (i < 2) f.hook(new DamageGate(p, i));
                if (i == 2) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
                if (i == 3) { p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); p.setAbsorptionAmount(10); }
                f.hit(p, p.damageSources().mobAttack(f.front(p)), 5);
                eq(h, total(p), n("27"), "native canceled or zero residual spends no pool"); eq(h, hp(p), hp, "native zero result remains authoritative");
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8 && AbilityStorage.get(p).exp(ID) == 0, "native zero spends no quota and earns no mastery");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void full_native_held_shield_prevents_circle_debit_and_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); p.startUsingItem(InteractionHand.OFF_HAND);
            h.runAfterDelay(7, () -> {
                f.place(p); h.assertTrue(p.isBlocking(), "actual native shield raising delay elapsed"); f.reserve(p, "3", "4"); f.arm(p, "20"); BigDecimal hp = hp(p); Watch w = f.watch(p);
                h.assertFalse(f.hit(p, p.damageSources().mobAttack(f.front(p)), 5), "held shield blocks the real front hit");
                h.assertTrue(w.blocked && w.blockedDamage == 5, "native shield event owns the complete block");
                eq(h, total(p), n("27"), "full native block spends no base or reserve S"); eq(h, hp(p), hp, "full native block preserves HP");
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "full native block uses no circle entry"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void partial_native_held_shield_runs_before_one_to_one_circle_residual_and_matches_control(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10), control = f.player(30, 10);
        f.ready(() -> {
            for (ServerPlayer target : List.of(p, control)) { target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void shield(LivingShieldBlockEvent e) {
                if (e.getEntity() == p || e.getEntity() == control) { h.assertTrue(e.getOriginalBlock(), "real front held shield supplies the original native block"); e.setBlockedDamage(2); }
            } });
            h.runAfterDelay(7, () -> {
                f.place(p); f.place(control); h.assertTrue(p.isBlocking() && control.isBlocking(), "both real native held shields finished raising");
                f.reserve(p, ".5", ".5"); f.arm(p, "1"); eq(h, total(p), n("2"), "partial shield fixture owns exactly two total existing S");
                Watch actual = f.watch(p), ordinary = f.watch(control); BigDecimal hp = hp(p), controlHp = hp(control);
                h.assertTrue(f.hit(control, control.damageSources().mobAttack(f.front(control)), 5), "ordinary canonical mob hit keeps the three-point native partial-block residual");
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(f.front(p)), 5), "circle receives the same actual vanilla partial-block hit");
                h.assertTrue(actual.posts == 1 && ordinary.posts == 1 && actual.damage == 3 && ordinary.damage == 3
                        && actual.blocked && ordinary.blocked && actual.blockedDamage == 2 && ordinary.blockedDamage == 2, "native shield owns two points and only the same three-point post-native residual reaches both adapters");
                eq(h, hp(control), controlHp.subtract(n("3")), "independent inactive control pays the exact native residual");
                eq(h, total(p), BigDecimal.ZERO, "circle spends exactly its existing two S after the native shield's two-point block");
                eq(h, hp(p), hp.subtract(n("1")), "only final uncovered one point reaches exact HP once");
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 7, "one positive partial absorption spends one rolling entry");
                vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "native partial-shield knockback is identical to the independent control"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void plain_arrow_actual_native_step_absorbs_without_turn_or_extra_damage_and_ignores_far_shooter_distance(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10), control = f.player(30, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, f.zombie(p.position().add(0, 0, 12)), null), b = f.arrow(control, f.zombie(control.position().add(0, 0, 12)), null);
            Watch protectedHit = f.watch(p), ordinary = f.watch(control); BigDecimal hp = hp(p); b.tick(); a.tick();
            h.assertTrue(a.isRemoved() && b.isRemoved() && protectedHit.posts == 1 && ordinary.posts == 1 && protectedHit.damage > 0 && protectedHit.damage == ordinary.damage, "both actual Arrow ticks apply the same native body hit and retire normally");
            eq(h, shield(p), n("20").subtract(decimal(protectedHit.damage)), "same-step A within eight metres owns one 1:1 absorption even with distant shooter"); eq(h, hp(p), hp, "circle absorbs only native final arrow residual");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "native arrow impulse is unchanged"); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 7, "exact native arrow body hurt consumes one quota entry"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void forged_direct_arrow_hurt_has_no_same_step_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Zombie shooter = f.zombie(p.position().add(0, 0, 7)); Arrow a = f.arrow(p, shooter, null); BigDecimal hp = hp(p);
            h.assertTrue(AutonomousVectorArrowReceipts.beginTick(a) == null, "public begin method cannot manufacture a native tick wrapper");
            f.hit(p, p.damageSources().arrow(a, shooter), 5);
            eq(h, shield(p), n("20"), "a native-looking DamageSource without actual Arrow.tick owns no S"); eq(h, hp(p), hp.subtract(n("5")), "direct native hurt still follows its ordinary HP path");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "forged source cannot consume quota"); a.discard(); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void inside_rear_far_leased_and_nonplain_actual_arrows_cannot_borrow_circle_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(9); ServerPlayer insideControl = f.player(60, 46);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "100"); Zombie shooter = f.zombie(p.position().add(5, 0, 7)); ItemStack weapon = null;
                if (i == 7) { weapon = new ItemStack(Items.BOW); weapon.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH), 1); }
                Arrow a = f.arrow(p, shooter, weapon);
                switch (i) {
                    case 0 -> { a.setPos(p.position().add(0, 1, .1)); a.setDeltaMovement(0, 0, -.5); }
                    case 1 -> { a.setPos(p.position().add(0, 1, -2)); a.setDeltaMovement(0, 0, 3); }
                    case 2 -> { a.setPos(p.position().add(0, 1, 8.1)); a.setDeltaMovement(0, 0, -10); }
                    case 3 -> a.getPersistentData().putBoolean("academy:trajectory_return_used", true);
                    case 4 -> a.setCritArrow(true);
                    case 5 -> a.addEffect(new MobEffectInstance(MobEffects.POISON, 40));
                    case 6 -> a.setNoGravity(true);
                    case 7 -> { }
                    case 8 -> { CompoundTag saved = a.saveWithoutId(new CompoundTag()); saved.putByte("PierceLevel", (byte)1); a.load(saved); }
                    default -> throw new IllegalStateException();
                }
                BigDecimal hp = hp(p), resources = total(p); Watch w = f.watch(p);
                if (i == 0) {
                    Arrow control = f.arrow(insideControl, f.zombie(insideControl.position().add(0, 0, 7)), null);
                    control.setPos(insideControl.position().add(0, 1, .1)); control.setDeltaMovement(0, 0, -.5); Watch nativeHit = f.watch(insideControl); BigDecimal nativeHp = hp(insideControl), nativeResources = total(insideControl); int[] cues = {0};
                    for (ServerPlayer target : List.of(p, insideControl)) f.channels.get(target).pipeline().addFirst("inside_circle_cue_witness", new ChannelOutboundHandlerAdapter() {
                        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                            if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AutonomousVectorCircle.Visual v && v.owner().equals(target.getUUID()) && v.phase() == AutonomousVectorCircle.ABSORBED) cues[0]++;
                            super.write(ctx, message, promise);
                        }
                    });
                    h.assertTrue(p.getBoundingBox().inflate((double).3F).contains(a.position()) && insideControl.getBoundingBox().inflate((double).3F).contains(control.position()), "both actual A points begin inside the native inflated player body");
                    control.tick(); a.tick();
                    h.assertTrue(nativeHit.posts == 0 && w.posts == nativeHit.posts && w.damage == nativeHit.damage, "pinned vanilla has no first entry from this inside-body segment, and NEW452 preserves that exact native result");
                    eq(h, hp(insideControl), nativeHp, "unprotected inside-body control takes no invented native damage"); eq(h, total(insideControl), nativeResources, "unprotected native control has no resource debit");
                    f.drain(p); f.drain(insideControl); h.assertTrue(cues[0] == 0, "neither actual inside-body native path emits an absorption cue");
                    vector(h, a.position().subtract(p.position()), control.position().subtract(insideControl.position()), "inside-body protection adds no movement to native arrow control");
                    vector(h, a.getDeltaMovement(), control.getDeltaMovement(), "inside-body protection adds no velocity or deflection"); control.discard();
                } else { a.tick(); h.assertTrue(w.posts == 1 && w.damage > 0, "rejected arrow still makes one actual native body hit " + i); }
                eq(h, shield(p), n("100"), "ineligible actual arrow spends no base S " + i); eq(h, total(p), resources, "ineligible actual arrow spends no base or reserve S " + i); eq(h, hp(p), hp.subtract(decimal(w.damage)), "ineligible arrow keeps exact native damage " + i);
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "ineligible actual arrow spends no quota " + i); a.discard();
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_projectile_impact_cancellation_or_arrow_and_owner_mutation_revokes_same_step_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(6);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); Arrow a = f.arrow(p, f.zombie(p.position().add(0, 0, 7)), null); BigDecimal hp = hp(p);
                Watch w = f.watch(p); ImpactGate gate = new ImpactGate(a, p, i); f.hook(gate); a.tick();
                h.assertTrue(gate.calls == 1, "actual original ProjectileImpactEvent ran once " + i);
                eq(h, shield(p), n("20"), "impact callback cannot preserve stale receipt " + i); eq(h, hp(p), hp.subtract(decimal(w.damage)), "callback refusal leaves only actual native damage " + i);
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "invalidated receipt spends no quota");
                if (i == 0) h.assertTrue(w.posts == 0, "canceled impact never enters native body hurt"); else h.assertTrue(w.posts == 1, "mutated impact still uses ordinary native body hurt"); a.discard();
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unknown_damage_subclasses_spoofed_positions_and_allied_sources_stay_native(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(7);
        f.ready(() -> {
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("av" + UUID.randomUUID().toString().substring(0, 10)); f.teams.add(team);
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); Zombie a = f.front(p); DamageSource original = p.damageSources().mobAttack(a); BigDecimal hp = hp(p);
                if (i == 6) { board.addPlayerToTeam(p.getScoreboardName(), team); board.addPlayerToTeam(a.getScoreboardName(), team); }
                DamageSource source = switch (i) { case 0 -> p.damageSources().magic(); case 1 -> new DamageSource(original.typeHolder(), a) {};
                    case 2 -> new DamageSource(original.typeHolder(), a, a, p.position()); case 3 -> new DamageSource(original.typeHolder(), null, a);
                    case 4 -> new AbilityDamage.SkillDamageSource(p, "vecmanip.autonomous_vector_circle", false);
                    case 5 -> new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SONIC_BOOM), a);
                    default -> original; };
                Watch w = f.watch(p); f.hit(p, source, 5); eq(h, shield(p), n("20"), "unsupported or friendly source cannot claim defensive S " + i);
                eq(h, hp(p), hp.subtract(decimal(w.damage)), "ordinary exact native residual remains authoritative " + i); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "unsupported provenance spends no quota");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void genuine_native_post_replay_after_toggle_off_never_reuses_hurt_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            // Learned NEW452 may have naturally recovered reserve during spawn-protection wait.
            // This partial-absorption fixture intentionally owns exactly two total S.
            f.reserve(p, "0", "0"); f.arm(p, "2"); eq(h, total(p), n("2"), "declared partial replay fixture owns exactly two S across all three pools");
            BigDecimal hp = hp(p); LivingDamageEvent.Post[] captured = {null}; boolean[] inside = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || inside[0]) return; inside[0] = true; captured[0] = e; NeoForge.EVENT_BUS.post(e);
                eq(h, shield(p), n("2"), "recursive event dispatch cannot settle original S early"); eq(h, hp(p), hp, "recursive event dispatch cannot settle exact HP early");
            } });
            f.hit(p, p.damageSources().mobAttack(f.front(p)), 5); h.assertTrue(captured[0] != null, "real native body hurt produced the retained Post object");
            eq(h, shield(p), BigDecimal.ZERO, "only original native dispatch consumes two S"); eq(h, hp(p), hp.subtract(n("3")), "one original residual reaches HP");
            h.assertTrue(f.start(p) && !AutonomousVectorCircle.active(p), "fresh authenticated press turns off the toggle");
            NeoForge.EVENT_BUS.post(captured[0]); net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(p, new DamageContainer(captured[0].getSource(), 5));
            eq(h, shield(p), BigDecimal.ZERO, "old Post cannot spend or restore S after toggle off"); eq(h, hp(p), hp.subtract(n("3")), "old native-looking Post cannot replay HP settlement");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 7, "replay cannot create another positive absorption entry"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void accepted_and_rejected_nested_hurt_revoke_outer_capture_without_losing_native_hp_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); BigDecimal hp = hp(p); DamageSource source = p.damageSources().mobAttack(f.front(p)); boolean accepted = i == 0; boolean[] entered = {false};
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                    if (e.getEntity() != p || entered[0]) return; entered[0] = true;
                    if (accepted) { p.invulnerableTime = 0; h.assertTrue(f.hit(p, p.damageSources().magic(), 2), "nested native unknown damage accepts"); }
                    else h.assertFalse(f.hit(p, source, 5), "equal nested native damage is iframe-rejected");
                } });
                f.hit(p, source, 5); h.assertTrue(entered[0], "actual native post callback reentered hurt"); eq(h, shield(p), n("20"), "any nested hurt retires stale outer defense");
                eq(h, hp(p), hp.subtract(n(accepted ? "7" : "5")), "each actually accepted native damage settles exactly once"); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "revoked outer defense consumes no quota");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_post_owner_position_and_facing_changes_cannot_promote_or_preserve_stale_cone_capture(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); BigDecimal hp = hp(p); int mode = i; Zombie a = f.zombie(p.position().add(0, 0, i == 2 ? -2 : 2));
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                    if (e.getEntity() != p) return;
                    if (mode == 0) p.setPos(p.position().add(.01, 0, 0)); else p.setYRot(180);
                    if (mode == 2) a.setPos(p.position().add(0, 0, 2));
                } });
                f.hit(p, p.damageSources().mobAttack(a), 5); eq(h, shield(p), n("20"), "post callback cannot retain or promote stale geometry " + i);
                eq(h, hp(p), hp.subtract(n("5")), "invalidated geometry retains actual native HP damage"); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "invalidated capture consumes no quota");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void exact_resources_and_quota_are_published_before_visual_callback_and_later_mutation_is_retained(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.reserve(p, "3", "4"); f.arm(p, "2"); BigDecimal hp = hp(p); boolean[] observed = {false};
            f.channels.get(p).pipeline().addFirst("circle_commit_witness", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                    if (!observed[0] && message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AutonomousVectorCircle.Visual v
                            && v.owner().equals(p.getUUID()) && v.phase() == AutonomousVectorCircle.ABSORBED) {
                        observed[0] = true; eq(h, shield(p), BigDecimal.ZERO, "base debit is visible before circle visual callback");
                        eq(h, share(p, "lower"), BigDecimal.ZERO, "lower debit is visible before circle visual callback"); eq(h, share(p, "upper"), n("2"), "upper debit is visible before circle visual callback");
                        eq(h, hp(p), hp, "exact final HP is published before visual callback"); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 7, "quota is committed before mutable visual observer");
                        f.reserve(p, "0", "1.25");
                    }
                    super.write(ctx, message, promise);
                }
            });
            f.hit(p, p.damageSources().mobAttack(f.front(p)), 7); f.drain(p); h.assertTrue(observed[0], "actual native absorption emitted its committed visual packet");
            eq(h, share(p, "upper"), n("1.25"), "completed publication cannot overwrite a later observer's current resource mutation"); eq(h, hp(p), hp, "notification cannot cause a second HP settlement"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_physical_quota",timeoutTicks=115)
    public static void four_per_physical_tick_and_eight_per_rolling_twenty_owner_ticks_are_independent(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "100"); Zombie a = f.front(p); BigDecimal hp = hp(p);
            f.hits(p, a, 5, 1); eq(h, shield(p), n("96"), "only first four positive contacts in the physical tick absorb"); eq(h, hp(p), hp.subtract(n("1")), "fifth same-tick native contact reaches HP");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4 && !AutonomousVectorCircle.protecting(p), "physical-tick cap is exhausted with four rolling slots still displayed");
            for (int i = 0; i < 80; i++) { AutonomousVectorCircle.tick(p); AutonomousVectorCircle.playerTick(new PlayerTickEvent.Post(p)); }
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4 && !AutonomousVectorCircle.protecting(p), "synthetic duplicate maintenance cannot mint a physical tick");
            PlayerPosts clock = f.posts(p);
            clock.at(1, () -> { f.place(p); h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4, "next genuine owner tick exposes the remaining four rolling slots"); f.hits(p, a, 5, 1); eq(h, shield(p), n("92"), "second physical cohort fills the eight-entry rolling window"); eq(h, hp(p), hp.subtract(n("2")), "ninth rolling hit stays native"); });
            clock.at(2, () -> h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 0, "new physical tick cannot bypass exhausted rolling quota"));
            clock.at(19, () -> h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 0, "first cohort retains nineteen genuine tick debt"));
            clock.at(20, () -> h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4, "only first cohort retires at twenty completed owner ticks"));
            clock.at(21, () -> { int[] remaining = p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).getIntArray("remaining"); h.assertTrue(Arrays.stream(remaining).allMatch(v -> v == 0), "second cohort receives its own complete twenty genuine owner ticks"); eq(h, shield(p), n("92"), "quota expiry never refills S"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_reload_debt",timeoutTicks=115)
    public static void native_save_reload_and_inactive_aging_preserve_physical_and_rolling_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "100"); f.hits(p, f.front(p), 4, 1); h.assertTrue(AutonomousVectorCircle.release(p, f.held.get(p)) && AutonomousVectorCircle.active(p), "key release preserves toggle");
            h.assertTrue(f.start(p) && !AutonomousVectorCircle.active(p), "fresh authenticated press stops the paid toggle");
            CompoundTag quota = p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).copy(); f.reload(p);
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY)), "actual native save/load preserves all exact remaining ages");
            h.assertFalse(AutonomousVectorCircle.active(p) || f.start(p), "reload neither activates nor erases immediate restart debt");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4, "reload preserves four spent rolling entries and their physical admission debt"); eq(h, shield(p), n("96"), "reload never replenishes S");
            PlayerPosts clock = f.posts(p);
            clock.at(1, () -> h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4, "inactive genuine tick clears only physical admission cap"));
            clock.at(19, () -> h.assertTrue(Arrays.stream(p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).getIntArray("remaining")).filter(v -> v > 0).count() == 4, "inactive reload still owes four rolling entries through nineteen ticks"));
            clock.at(20, () -> { h.assertTrue(Arrays.stream(p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).getIntArray("remaining")).allMatch(v -> v == 0), "only real twentieth owner tick retires saved debt"); eq(h, shield(p), n("96"), "inactive quota aging does not refill within spend cooldown"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_clear_debt",timeoutTicks=100)
    public static void clone_and_controller_clear_preserve_saved_remaining_ages_and_payment_without_free_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "100"); f.hits(p, f.front(p), 4, 1);
            CompoundTag quota = p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).copy(), payment = p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).copy();
            ServerPlayer clone = new ServerPlayer(p.server, h.getLevel(), p.getGameProfile(), p.clientInformation()); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, p, false));
            h.assertTrue(quota.equals(clone.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY)) && payment.equals(clone.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY)), "native clone copies exact remaining quota and paid timing debt");
            h.assertFalse(AutonomousVectorCircle.active(p) || AutonomousVectorCircle.active(clone), "clone carries no active controller authority");
            AutonomousVectorCircle.clear(); h.assertTrue(quota.equals(p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY)), "controller cache clear cannot erase persisted debt");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 4 && !f.start(p), "same-tick controller clear preserves rolling debt and cannot restart through the physical payment fence"); eq(h, shield(p), n("96"), "clone and clear cannot refund S"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void activation_release_toggle_and_category_switch_never_heal_refill_move_or_award_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.hit(p, p.damageSources().magic(), 3); p.invulnerableTime = 0; f.place(p); f.shield(p, n("2")); f.reserve(p, "3", "4");
            var state = AbilityStorage.get(p); BigDecimal hp = hp(p), growth = HighPowerGrowth.values(p).position(), earned = HighPowerCombat.earnedGrowthPosition(p); double xp = state.levelExperience, cp = HighPowerResources.cp(p);
            Vec3 motion = new Vec3(.1, .05, -.1); p.setDeltaMovement(motion); h.assertTrue(f.start(p), "moving owner starts the paid toggle without a ground-sink condition");
            near(h, HighPowerResources.cp(p), cp - VectorParryRules.activationCost(growth), "activation reuses the existing NEW412 growth curve exactly");
            vector(h, p.getDeltaMovement(), motion, "activation adds no movement"); eq(h, total(p), n("9"), "activation creates no S"); eq(h, hp(p), hp, "activation cannot heal existing injury");
            f.place(p); f.hit(p, p.damageSources().mobAttack(f.front(p)), 7); eq(h, total(p), n("2"), "real contact spends only existing balances");
            h.assertTrue(state.exp(ID) == 0 && state.levelExperience == xp, "native absorption awards no mastery or level XP");
            eq(h, HighPowerGrowth.values(p).position(), growth, "contact grants no active growth"); eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "contact grants no certified growth");
            CompoundTag quota = p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).copy(); state.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); AutonomousVectorCircle.tick(p);
            h.assertFalse(AutonomousVectorCircle.active(p), "ordinary category switch ends paid ownership"); eq(h, total(p), n("2"), "category switch preserves depleted resources"); eq(h, hp(p), hp, "category switch cannot heal");
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY)), "category switch cannot erase spent quota"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void one_existing_recovery_budget_fills_base_then_lower_then_upper_and_active_circle_blocks_it(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            BigDecimal cap = n(HighPowerCombat.maxShieldText(ps.get(0))), one = cap.movePointLeft(2), half = cap.divide(n("2"));
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.shield(p, i < 2 ? cap.subtract(one.divide(n("2"))) : cap);
                f.reserve(p, i < 2 ? "0" : half.subtract(one.divide(n("2"))).toPlainString(), "7"); f.recoveryReady(p);
            }
            h.assertTrue(f.start(ps.get(1)), "active circle starts with saved depleted balances"); AbilityStorage.get(ps.get(3)).unlearn(ID);
            for (ServerPlayer p : ps) HighPowerCombat.tickShield(p);
            eq(h, shield(ps.get(0)), cap, "one recovery budget finishes base first"); eq(h, share(ps.get(0), "lower"), one.divide(n("2")), "only remaining half budget enters lower"); eq(h, share(ps.get(0), "upper"), n("7"), "base and lower use the complete single budget");
            eq(h, shield(ps.get(1)), cap.subtract(one.divide(n("2"))), "active paid circle blocks base recovery"); eq(h, share(ps.get(1), "lower"), BigDecimal.ZERO, "active paid circle blocks reserve recovery");
            eq(h, share(ps.get(2), "lower"), half, "lower fills before upper"); eq(h, share(ps.get(2), "upper"), n("7").add(one.divide(n("2"))), "only NEW452 allows remainder into upper");
            eq(h, share(ps.get(3), "lower"), half, "existing NEW442 still recovers lower"); eq(h, share(ps.get(3), "upper"), n("7"), "unlearning NEW452 preserves old lower-only eligibility");
            BigDecimal before = total(ps.get(2)); for (int i = 0; i < 40; i++) HighPowerCombat.tickShield(ps.get(2)); eq(h, total(ps.get(2)), before, "all learned paths share one existing one-percent S budget per real tick"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void older_vector_defenses_keep_their_original_base_or_lower_only_access_after_learning_circle(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            String[] ids = {VectorParry.ID, InertialAnchor.ID, CirculationRelief.ID, MultiAxisReflection.ID};
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.shield(p, n("1")); f.reserve(p, "0", "20"); f.bind(p, 1, ids[i]); BigDecimal hp = hp(p);
                boolean started = switch (i) { case 0 -> VectorParry.start(p, f.wire(p, 1)); case 1 -> InertialAnchor.start(p, f.wire(p, 1)); case 2 -> CirculationRelief.start(p, f.wire(p, 1)); default -> MultiAxisReflection.start(p, f.wire(p, 1)); };
                h.assertTrue(started, "older learned defense starts with independently declared balance " + i);
                if (i == 3) { Arrow a = f.arrow(p, f.zombie(p.position().add(0, 0, 7)), null); a.tick(); a.discard(); eq(h, shield(p), n("1"), "NEW442 refuses unaffordable whole reflection instead of partially spending"); }
                else if (i == 1) f.hit(p, p.damageSources().fall(), 5);
                else f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(i == 2 ? 2 : 0, 0, i == 2 ? 0 : 2))), 5);
                eq(h, share(p, "upper"), n("20"), "learning NEW452 never expands another defense's upper access " + i);
                eq(h, share(p, "lower"), BigDecimal.ZERO, "older base-only path cannot invent lower S"); h.assertTrue(hp(p).compareTo(hp) < 0, "uncovered actual native damage still reaches HP " + i);
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void circle_and_412_422_432_442_413_are_reciprocally_exclusive_without_conflict_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(5); List<ServerPlayer> circles = new ArrayList<>(); for (int i = 0; i < 5; i++) circles.add(f.player(10 + i % 3 * 18, 46 + i / 3 * 10)); String[] ids = {VectorParry.ID, InertialAnchor.ID, CirculationRelief.ID, MultiAxisReflection.ID, BrakingTouch.ID};
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.bind(p, 1, ids[i]);
                if (i == 4) { Snowball ball = new Snowball(h.getLevel(), p); ball.setPos(p.getX(), p.getEyeY() - ball.getBbHeight() / 2, p.getZ() + 1.4 + ball.getBbWidth() / 2); ball.setDeltaMovement(.12, -.08, .17); f.entity(ball); }
                h.assertTrue(f.otherStart(p, i, 1), "older controller genuinely starts " + ids[i]); double cp = HighPowerResources.cp(p);
                h.assertFalse(f.start(p), "active " + ids[i] + " refuses circle"); near(h, HighPowerResources.cp(p), cp, "reverse conflict refuses before payment");
            }
            for (int i = 0; i < ids.length; i++) {
                ServerPlayer circle = circles.get(i); f.bind(circle, 1, ids[i]); f.arm(circle, "20"); double cp = HighPowerResources.cp(circle);
                h.assertFalse(f.otherStart(circle, i, 1), "active circle refuses " + ids[i]);
                near(h, HighPowerResources.cp(circle), cp, "forward conflict refuses before payment");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_player_owned_arrow_is_eligible_but_same_team_player_arrow_is_not(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10), allyTarget = f.player(30, 10), shooter = f.player(10, 24), allyShooter = f.player(30, 24);
        f.ready(() -> {
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("ap" + UUID.randomUUID().toString().substring(0, 10)); f.teams.add(team); team.setAllowFriendlyFire(true);
            board.addPlayerToTeam(allyTarget.getScoreboardName(), team); board.addPlayerToTeam(allyShooter.getScoreboardName(), team);
            f.arm(p, "20"); f.arm(allyTarget, "20"); BigDecimal hp = hp(p), allyHp = hp(allyTarget);
            Arrow a = f.arrow(p, shooter, null), b = f.arrow(allyTarget, allyShooter, null); Watch x = f.watch(p), y = f.watch(allyTarget);
            f.arrowTick(a); f.arrowTick(b); h.assertTrue(x.posts == 1 && y.posts == 1 && x.damage > 0 && y.damage == x.damage, "both actual player-owned arrows follow identical PvP-enabled native body damage");
            eq(h, hp(p), hp, "ordinary nonallied native player arrow may use NEW452"); eq(h, shield(p), n("20").subtract(decimal(x.damage)), "ordinary player arrow debits its exact native residual");
            eq(h, shield(allyTarget), n("20"), "same-team player arrow cannot spend defensive S"); eq(h, hp(allyTarget), allyHp.subtract(decimal(y.damage)), "allowed friendly-fire damage remains native");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 7 && AutonomousVectorCircle.remainingHits(allyTarget) == 8, "only nonallied admitted player arrow spends one rolling slot"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void arrow_source_point_requires_eye_visibility_even_when_its_lower_native_path_is_clear(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, f.zombie(p.position().add(0, 0, 7)), null); a.setPos(p.position().add(0, .8, 2));
            f.block(p.position().add(0, 1, 1), Blocks.STONE_SLAB.defaultBlockState()); Watch w = f.watch(p); BigDecimal hp = hp(p); a.tick();
            h.assertTrue(w.posts == 1 && w.damage > 0, "lower horizontal native arrow path passes below the slab and genuinely hits body");
            eq(h, shield(p), n("20"), "slab hides A from owner eye and therefore refuses circle receipt"); eq(h, hp(p), hp.subtract(decimal(w.damage)), "occluded-source arrow retains ordinary native damage");
            h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "failed eye-to-A visibility consumes no quota"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_visibility_callback",timeoutTicks=100)
    public static void native_visibility_shape_callback_cannot_poison_frame_or_mutate_payment_then_debit_s(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("vs" + UUID.randomUUID().toString().substring(0, 10)); f.teams.add(team);
            h.assertTrue(NativeVisibilityBlock.block != null, "GameTest-only dynamic native visibility block registered before registry freeze");
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); Zombie a = f.zombie(p.position().add(0, 0, 4)); BigDecimal hp = hp(p); boolean[] called = {false}, armed = {false}; int[] cues = {0}; int mode = i;
                f.block(p.position().add(0, 1, 1), NativeVisibilityBlock.block.defaultBlockState());
                f.channels.get(p).pipeline().addFirst("circle_shape_witness", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                        if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AutonomousVectorCircle.Visual v && v.owner().equals(p.getUUID()) && v.phase() == AutonomousVectorCircle.ABSORBED) cues[0]++;
                        super.write(ctx, message, promise);
                    }
                });
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                    if (e.getEntity() != p || armed[0]) return; armed[0] = true;
                    NativeVisibilityBlock.onRead = () -> {
                        called[0] = true;
                        if (mode == 0) AbilityStorage.get(p).activated = false;
                        else if (mode == 1) p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).putInt("remaining", 0);
                        else if (mode == 2) { p.invulnerableTime = 0; h.assertTrue(f.hit(p, p.damageSources().magic(), 2), "real nested hurt runs inside native visibility shape callback"); }
                        else { board.addPlayerToTeam(p.getScoreboardName(), team); board.addPlayerToTeam(a.getScoreboardName(), team); }
                    };
                } });
                try {
                    f.hit(p, p.damageSources().mobAttack(a), 5); f.drain(p); h.assertTrue(called[0], "actual native shape lookup executed the adversarial callback during final capture validation");
                    eq(h, shield(p), n("20"), "late visibility callback invalidation cannot publish any S debit " + mode); eq(h, hp(p), hp.subtract(n(mode == 2 ? "7" : "5")), "every actual native HP transaction remains authoritative " + mode);
                    h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8 && cues[0] == 0, "invalidated final capture emits no quota debit or absorption cue " + mode);
                } finally { NativeVisibilityBlock.onRead = null; BlockPos at = BlockPos.containing(p.position().add(0, 1, 1)); h.getLevel().setBlockAndUpdate(at, f.previousBlocks.get(at)); }
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_temporary_zero_hp_is_absorbed_but_true_partial_lethal_damage_stays_lethal(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer full = f.player(10, 10), partial = f.player(30, 10);
        f.ready(() -> {
            f.arm(full, "100"); f.reserve(partial, "1", "1"); f.arm(partial, "1"); f.combat(partial).putString("health", "1"); BigDecimal hp = hp(full); boolean[] zero = {false}; int[] deaths = {0};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) { if (e.getEntity() == full) zero[0] = full.getHealth() == 0; }
                @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == full) deaths[0]++; if (e.getEntity() == partial) deaths[0] += 10; } });
            f.hit(full, full.damageSources().mobAttack(f.front(full)), 40); h.assertTrue(zero[0], "genuine native post sees temporary normalized zero before exact settlement");
            eq(h, hp(full), hp, "existing S protects preexisting exact HP through temporary native zero"); eq(h, shield(full), n("60"), "full absorption spends forty existing S");
            f.hit(partial, partial.damageSources().mobAttack(f.front(partial)), 5); eq(h, total(partial), BigDecimal.ZERO, "true lethal residual retains already committed base and reserve debit"); eq(h, hp(partial), BigDecimal.ZERO, "uncovered two damage is lethal to one exact HP");
            h.assertTrue(full.isAlive() && !partial.isAlive() && deaths[0] == 10 && !AutonomousVectorCircle.active(partial), "only actual lethal residual runs ordinary death and tears down ownership");
            h.assertTrue(AutonomousVectorCircle.remainingHits(full) == 7 && AutonomousVectorCircle.remainingHits(partial) == 7, "both actual positive absorptions retain their committed quota"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_paid_windows",timeoutTicks=115)
    public static void unpayable_twentieth_tick_renewal_revokes_protection_without_refund_or_free_sustain(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p); BigDecimal q = HighPowerGrowth.values(p).position(); f.arm(p, "24");
            near(h, HighPowerResources.cp(p), cp - VectorParryRules.activationCost(q), "existing NEW412 activation curve is charged once");
            f.hit(p, p.damageSources().mobAttack(f.front(p)), 4); long receipt = p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).getLong("receipt");
            double paid = HighPowerResources.cp(p); for (int i = 0; i < 20; i++) AutonomousVectorCircle.tick(p); near(h, HighPowerResources.cp(p), paid, "same physical window maintenance never pays twice");
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Pre e) { if (e.getServer() == p.server) f.budget(p).putDouble("cp", 0); } });
            PlayerPosts clock = f.posts(p);
            clock.at(20, () -> h.assertTrue(AutonomousVectorCircle.renewalPending(p) && !AutonomousVectorCircle.protecting(p), "twentieth genuine owner tick ends paid protection before renewal"));
            clock.at(21, () -> { h.assertFalse(AutonomousVectorCircle.active(p) || AutonomousVectorCircle.renewalPending(p), "unaffordable next renewal closes the paid toggle");
                h.assertTrue(p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).getLong("receipt") == receipt, "unpaid sustain never creates a new payment receipt"); eq(h, shield(p), n("20"), "sustain failure does not refill or spend S"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void wind_lift_and_circle_are_reciprocally_exclusive_at_real_integrated_admission_gate(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer circle = f.player(10, 10), lift = f.player(30, 10);
        f.ready(() -> {
            for (ServerPlayer p : List.of(circle, lift)) { f.bind(p, 1, WindPressureLift.ID); p.setNoGravity(false); p.setDeltaMovement(0, -.0784, 0); p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.setOnGround(true); }
            f.arm(circle, "20"); double cp = HighPowerResources.cp(circle); h.assertFalse(f.integratedLift(circle, 1), "active NEW452 refuses NEW443 through the supported integrated contract gate"); near(h, HighPowerResources.cp(circle), cp, "forward conflict consumes no lift payment");
            h.assertTrue(f.integratedLift(lift, 1) && WindPressureLift.active(lift), "declared native integrated fixture genuinely admits NEW443 before reverse conflict"); cp = HighPowerResources.cp(lift);
            h.assertFalse(f.start(lift), "active NEW443 refuses NEW452"); near(h, HighPowerResources.cp(lift), cp, "reverse conflict consumes no circle payment"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fake_same_source_post_during_native_incoming_cannot_settle_inactive_unknown_or_rear_hurt(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); if (i == 0) f.shield(p, n("20")); else f.arm(p, "20"); BigDecimal hp = hp(p); boolean[] injected = {false};
                DamageSource source = i == 1 ? p.damageSources().magic() : p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, i == 2 ? -2 : 2)));
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e) {
                    if (e.getEntity() != p || injected[0]) return; injected[0] = true;
                    NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, new DamageContainer(e.getSource(), 5)));
                    net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(p, new DamageContainer(e.getSource(), 5));
                    eq(h, hp(p), hp, "same-source synthetic Post has no exact HP authority inside actual native incoming");
                    eq(h, shield(p), n("20"), "same-source synthetic Post cannot spend S on refusal path");
                } });
                f.hit(p, source, 5); h.assertTrue(injected[0], "synthetic same-source dispatch ran inside actual native hurt");
                eq(h, hp(p), hp.subtract(n("5")), "original refused-defense native damage settles once and only once " + i); eq(h, shield(p), n("20"), "inactive, unknown and rear paths never spend circle S");
                h.assertTrue(AutonomousVectorCircle.remainingHits(p) == 8, "refused-defense replay cannot consume any rolling entry");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_relogin_debt",timeoutTicks=100)
    public static void real_logout_and_same_uuid_new_connection_preserve_balances_and_both_quota_fences(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer old = f.player(10, 10);
        f.ready(() -> {
            f.reserve(old, "3", "4"); f.arm(old, "100"); f.hits(old, f.front(old), 4, 1); AutonomousVectorCircle.abort(old);
            CompoundTag quota = old.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY).copy(), payment = old.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).copy(); double cp = HighPowerResources.cp(old); int entity = old.getId();
            ServerPlayer joined = f.rejoin(old);
            h.assertTrue(joined.getUUID().equals(old.getUUID()) && joined.getId() != entity && AbilityStorage.get(joined).learned(ID), "actual PlayerList login loads paid learned data on a new entity and connection without enrollment");
            h.assertTrue(quota.equals(joined.getPersistentData().getCompound(AutonomousVectorCircle.QUOTA_KEY)) && payment.equals(joined.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY)), "real relog retains exact physical payment and rolling remaining-age records");
            h.assertFalse(AutonomousVectorCircle.active(joined) || f.start(joined), "new connection cannot reactivate old context or erase immediate restart fence");
            h.assertTrue(AutonomousVectorCircle.remainingHits(joined) == 4, "same UUID rejoin cannot restore spent rolling quota"); eq(h, total(joined), n("103"), "real relog preserves already spent base and retained reserve"); near(h, HighPowerResources.cp(joined), cp, "real relog grants no free CP"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void balance_packets_publish_exact_owner_start_debit_and_zero_but_redact_observers_and_end(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10), observer = f.player(24, 10);
        f.ready(() -> {
            f.shield(p, n("2.625")); f.reserve(p, "3.25", "4.125"); BigDecimal hp = hp(p);
            String capacity = n(HighPowerCombat.maxShieldText(p)).multiply(n("2")).min(CombatAmounts.CAP).toPlainString();
            CompoundTag before = f.combat(p).copy(); List<AutonomousVectorCircle.Visual> owned = f.captureVisuals(p, p), viewed = f.captureVisuals(observer, p);
            h.assertTrue(f.start(p), "real authenticated activation publishes owner-only balance"); f.drain(p); f.drain(observer);
            AutonomousVectorCircle.Visual start = lastPhase(h, owned, AutonomousVectorCircle.ACTIVE);
            h.assertTrue(start.availableShield().equals("10") && start.shieldCapacity().equals(capacity), "owner activation displays exact canonical base plus both reserve shares and bounded existing 2S capacity");
            h.assertTrue(before.equals(f.combat(p)), "reading and publishing activation balances never changes the combat ledger");
            h.assertTrue(redacted(start).equals(lastPhase(h, viewed, AutonomousVectorCircle.ACTIVE)), "observer receives identical receipt and geometry with both balance fields empty");
            Zombie a = f.front(p); f.hit(p, p.damageSources().mobAttack(a), 8); f.drain(p); f.drain(observer);
            AutonomousVectorCircle.Visual debit = lastPhase(h, owned, AutonomousVectorCircle.ABSORBED);
            eq(h, total(p), n("2"), "native residual debits eight existing S before HUD publication");
            h.assertTrue(debit.availableShield().equals(CombatAmounts.decimalString(total(p))) && debit.shieldCapacity().equals(capacity)
                    && debit.remainingHits() == 7, "post-debit owner receipt exactly matches committed resources and quota");
            h.assertTrue(redacted(debit).equals(lastPhase(h, viewed, AutonomousVectorCircle.ABSORBED)), "post-debit observer receives no resource amount or capacity");
            p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 2); f.drain(p); f.drain(observer);
            AutonomousVectorCircle.Visual zero = lastPhase(h, owned, AutonomousVectorCircle.ABSORBED);
            h.assertTrue(zero.sequence() > debit.sequence() && zero.availableShield().equals("0") && zero.shieldCapacity().equals(capacity)
                    && !zero.ready() && zero.remainingHits() == 6, "real final exhaustion publishes known exact zero rather than unknown balance");
            eq(h, total(p), BigDecimal.ZERO, "balance display cannot replenish the exhausted pools"); eq(h, hp(p), hp, "all ten native damage was paid by exactly ten existing S");
            h.assertTrue(redacted(zero).equals(lastPhase(h, viewed, AutonomousVectorCircle.ABSORBED)), "zero-balance observer receipt remains redacted");
            h.assertTrue(f.start(p) && !AutonomousVectorCircle.active(p), "fresh authenticated input ends the exhausted toggle"); f.drain(p); f.drain(observer);
            AutonomousVectorCircle.Visual end = lastPhase(h, owned, AutonomousVectorCircle.ENDED);
            h.assertTrue(end.availableShield().isEmpty() && end.shieldCapacity().isEmpty() && end.remainingHits() == 0 && !end.ready(), "ENDED explicitly clears both owner HUD balance fields");
            h.assertTrue(end.equals(lastPhase(h, viewed, AutonomousVectorCircle.ENDED)), "owner and observer share the same fully empty terminal balance");
            for (AutonomousVectorCircle.Visual packet : owned) roundTrip(h, packet);
            for (AutonomousVectorCircle.Visual packet : viewed) { h.assertTrue(packet.availableShield().isEmpty() && packet.shieldCapacity().isEmpty(), "every observer packet keeps amounts private"); roundTrip(h, packet); }
            eq(h, total(p), BigDecimal.ZERO, "serialization, redaction and ending never change resources"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_balance_codec_accepts_canonical_bounded_decimals_and_rejects_malformed_wire_values(GameTestHelper h) {
        Vec3 point = h.absoluteVec(new Vec3(10, 2, 10)); String cap = CombatAmounts.CAP.toPlainString(), longest = "0." + "1".repeat(126);
        for (String[] values : new String[][]{{"", ""}, {"0", "0"}, {"0", cap}, {"9.25", cap}, {cap, cap}, {longest, "1"}})
            roundTrip(h, balanceVisual(point, AutonomousVectorCircle.ACTIVE, values[0], values[1]));
        AutonomousVectorCircle.Visual legacy = new AutonomousVectorCircle.Visual(452, new UUID(452, 1), 1, 1, 1, 1, AutonomousVectorCircle.ACTIVE,
                point, new Vec3(0, 0, 1), point, point.add(0, 1, 0), 8, true);
        h.assertTrue(legacy.availableShield().isEmpty() && legacy.shieldCapacity().isEmpty(), "compatible old constructor represents unknown balance without inventing zero"); roundTrip(h, legacy);
        roundTrip(h, balanceVisual(point, AutonomousVectorCircle.ENDED, "", ""));
        String[][] malformed = {{"", "1"}, {"0", ""}, {"-1", "1"}, {"-0", "1"}, {"+1", "1"}, {"01", "1"}, {"1.0", "1"}, {"1", "01"},
                {"1e0", "1"}, {"NaN", "1"}, {"Infinity", "1"}, {" 1", "1"}, {"1 ", "1"}, {".5", "1"}, {"１", "1"},
                {"2", "1"}, {cap, "99999999999999999999"}, {"100000000000000000001", cap}, {"0", "100000000000000000001"},
                {"0." + "1".repeat(127), "1"}, {"0", "0." + "1".repeat(127)}};
        for (int i = 0; i < malformed.length; i++) {
            String available = malformed[i][0], capacity = malformed[i][1]; boolean constructorRefused = false;
            try { balanceVisual(point, AutonomousVectorCircle.ACTIVE, available, capacity); } catch (IllegalArgumentException expected) { constructorRefused = true; }
            h.assertTrue(constructorRefused, "native record rejects malformed owner balance case " + i);
            malformedBalanceWire(h, legacy, available, capacity, "malformed owner balance case " + i);
        }
        for (boolean missingAvailable : new boolean[]{true, false}) {
            boolean refused = false; try { balanceVisual(point, AutonomousVectorCircle.ACTIVE, missingAvailable ? null : "0", missingAvailable ? "1" : null); }
            catch (IllegalArgumentException expected) { refused = true; }
            h.assertTrue(refused, "native record rejects a null balance field");
        }
        boolean endedRefused = false; try { balanceVisual(point, AutonomousVectorCircle.ENDED, "0", "1"); } catch (IllegalArgumentException expected) { endedRefused = true; }
        h.assertTrue(endedRefused, "terminal record cannot retain owner balance");
        malformedBalanceWire(h, balanceVisual(point, AutonomousVectorCircle.ENDED, "", ""), "0", "1", "ENDED balance disclosure"); h.succeed();
    }

    private static AutonomousVectorCircle.Visual balanceVisual(Vec3 point, int phase, String available, String capacity) {
        return new AutonomousVectorCircle.Visual(452, new UUID(452, 1), 1, 1, 1, 1, phase,
                point, new Vec3(0, 0, 1), point, point.add(0, 1, 0), phase == AutonomousVectorCircle.ENDED ? 0 : 8,
                phase == AutonomousVectorCircle.ACTIVE, available, capacity);
    }
    private static AutonomousVectorCircle.Visual redacted(AutonomousVectorCircle.Visual p) {
        return new AutonomousVectorCircle.Visual(p.entity(), p.owner(), p.input(), p.token(), p.epoch(), p.sequence(), p.phase(),
                p.point(), p.facing(), p.source(), p.contact(), p.remainingHits(), p.ready(), "", "");
    }
    private static AutonomousVectorCircle.Visual lastPhase(GameTestHelper h, List<AutonomousVectorCircle.Visual> packets, int phase) {
        AutonomousVectorCircle.Visual found = null; for (AutonomousVectorCircle.Visual packet : packets) if (packet.phase() == phase) found = packet;
        h.assertTrue(found != null, "actual native connection receives circle visual phase " + phase); return found;
    }
    private static void roundTrip(GameTestHelper h, AutonomousVectorCircle.Visual packet) {
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try { AutonomousVectorCircle.Visual.CODEC.encode(wire, packet); h.assertTrue(packet.equals(AutonomousVectorCircle.Visual.CODEC.decode(wire)) && wire.readableBytes() == 0, "native codec round trip preserves every bounded balance and geometry field"); }
        finally { wire.release(); }
    }
    private static void malformedBalanceWire(GameTestHelper h, AutonomousVectorCircle.Visual empty, String available, String capacity, String reason) {
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            AutonomousVectorCircle.Visual.CODEC.encode(wire, empty);
            // Each canonical empty UTF string ends in its one-byte zero length. Preserve the
            // complete genuine geometry header; replace only the untrusted incoming strings.
            wire.writerIndex(wire.writerIndex() - 2); wire.writeUtf(available); wire.writeUtf(capacity);
            boolean refused = false; try { AutonomousVectorCircle.Visual.CODEC.decode(wire); } catch (RuntimeException expected) { refused = true; }
            h.assertTrue(refused, "native decoder rejects " + reason);
        } finally { wire.release(); }
    }

    /** Registered only for this native suite and restored synchronously before the next world tick. */
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class NativeVisibilityBlock {
        static net.minecraft.world.level.block.Block block; static Runnable onRead;
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event) {
            if (!GameTestHooks.isGametestEnabled() || !Arrays.asList(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",")).contains("academy_autonomous_vector_circle") || !event.getRegistryKey().equals(Registries.BLOCK)) return;
            event.register(Registries.BLOCK, helper -> {
                block = new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().dynamicShape().noOcclusion()) {
                    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos at, net.minecraft.world.phys.shapes.CollisionContext context) {
                        if (onRead != null) { Runnable callback = onRead; onRead = null; callback.run(); }
                        return net.minecraft.world.phys.shapes.Shapes.empty();
                    }
                };
                helper.register(ResourceLocation.fromNamespaceAndPath("academy", "gametest_circle_visibility_callback"), block);
            });
        }
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity p) { return n(HighPowerCombat.healthText(p)); }
    private static BigDecimal shield(LivingEntity p) { return HighPowerCombat.shield(p); }
    private static BigDecimal decimal(float value) { return new BigDecimal(Float.toString(value)); }
    private static BigDecimal share(ServerPlayer p, String name) { CompoundTag r = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY); return r.contains(name) ? n(r.getString(name)) : BigDecimal.ZERO; }
    private static BigDecimal total(ServerPlayer p) { return shield(p).add(share(p, "lower")).add(share(p, "upper")); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void defenses(GameTestHelper h, ServerPlayer p) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4); helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        p.setItemSlot(EquipmentSlot.HEAD, helmet); p.getAttribute(Attributes.ARMOR).setBaseValue(5); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); p.setAbsorptionAmount(.5F);
    }
    public static final class DamageGate {
        final ServerPlayer p; final int mode;
        DamageGate(ServerPlayer p, int mode) { this.p = p; this.mode = mode; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == p && mode == 0) e.setCanceled(true); }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == p && mode == 1) e.setNewDamage(0); }
    }
    public static final class ImpactGate {
        final Arrow arrow; final ServerPlayer player; final int mode; int calls;
        ImpactGate(Arrow arrow, ServerPlayer player, int mode) { this.arrow = arrow; this.player = player; this.mode = mode; }
        @SubscribeEvent public void impact(ProjectileImpactEvent e) { if (e.getProjectile() != arrow) return; calls++; switch (mode) {
            case 0 -> e.setCanceled(true); case 1 -> arrow.setOwner(arrow.getOwner()); case 2 -> arrow.setBaseDamage(3);
            case 3 -> arrow.setDeltaMovement(arrow.getDeltaMovement().scale(.9)); case 4 -> player.setPos(player.position().add(.01, 0, 0)); case 5 -> player.setYRot(1); default -> throw new IllegalStateException();
        } }
    }
    public static final class Watch {
        final LivingEntity target; int posts; float damage, armor, effects, enchantments, absorption, blockedDamage; boolean blocked;
        Watch(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) { if (e.getEntity() != target) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION); }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
    }
    /** Only completed native owner callbacks age assertions; terminal GameTest notification is deferred. */
    public static final class PlayerPosts {
        final Fixture f; final ServerPlayer p; final Map<Integer, Runnable> checks = new HashMap<>(); int last, completed;
        PlayerPosts(Fixture f, ServerPlayer p) { this.f = f; this.p = p; last = p.server.getTickCount(); }
        void at(int count, Runnable action) { checks.put(count, action); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(PlayerTickEvent.Post e) {
            if (f.closed || f.terminalQueued || f.h.testInfo.isDone() || e.getEntity() != p || p.server.getTickCount() <= last) return;
            last = p.server.getTickCount(); Runnable action = checks.remove(++completed); if (action != null) try { action.run(); } catch (Throwable failure) { f.fail(failure); }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<net.minecraft.world.scores.PlayerTeam> teams = new ArrayList<>(); final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, String> held = new IdentityHashMap<>();
        MachineDeveloperBlockEntity machine; int sequence; boolean closed, terminalQueued; Throwable terminalFailure;
        Fixture(GameTestHelper h) { this.h = h; h.onEachTick(this::finishQueuedTerminal); h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        void succeed() { if (!closed && !terminalQueued) terminalQueued = true; }
        void fail(Throwable problem) { if (!closed && !terminalQueued) { terminalQueued = true; terminalFailure = problem; } }
        void finishQueuedTerminal() { if (!closed && terminalQueued && !h.testInfo.isDone()) { if (terminalFailure == null) h.succeed(); else h.testInfo.fail(terminalFailure); } }
        List<ServerPlayer> players(int count) { List<ServerPlayer> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(player(10 + i % 3 * 18, 10 + i / 3 * 20)); return result; }
        ServerPlayer player(double x, double z) { return player(x, z, true); }
        ServerPlayer player(double x, double z, boolean learned) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "AVC" + UUID.randomUUID().toString().substring(0, 9)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, 5), "explicit connected LV5 exact enrollment fixture"); var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(5);
            for (String skill : List.of(HighPowerSkills.DEFLECTION_STRIKE, VectorParry.ID, BrakingTouch.ID, InertialAnchor.ID, CirculationRelief.ID, TrajectoryReturn.ID, CirculationListening.ID, VectorCage.ID, MultiAxisReflection.ID, WindPressureLift.ID)) state.learn(skill);
            if (learned) state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, state::learned), "real learned ordinary input binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setShiftKeyDown(false); p.setPose(Pose.STANDING); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        void bind(ServerPlayer p, int slot, String id) { h.assertTrue(AbilityStorage.get(p).presets.edit(0, slot, id, AbilityStorage.get(p)::learned), "real learned conflict binding " + id); }
        String wire(ServerPlayer p, int slot) { long next = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, next); return new HighPowerInput.Input(slot, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p, 0); boolean result = AutonomousVectorCircle.start(p, wire); if (result && AutonomousVectorCircle.active(p)) held.put(p, wire); return result; }
        void arm(ServerPlayer p, String amount) { shield(p, n(amount)); h.assertTrue(start(p), "authenticated paid NEW452 toggle starts: native=" + AnchorDamagePostProof.available() + ", arrow=" + AutonomousVectorArrowReceipts.available() + ", unsupported=" + HighPowerCombat.unsupportedReason(p) + ", payment=" + p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY)); }
        boolean otherStart(ServerPlayer p, int mode, int slot) { return switch (mode) { case 0 -> VectorParry.start(p, wire(p, slot)); case 1 -> InertialAnchor.start(p, wire(p, slot)); case 2 -> CirculationRelief.start(p, wire(p, slot)); case 3 -> MultiAxisReflection.start(p, wire(p, slot)); case 4 -> BrakingTouch.start(p, wire(p, slot)); default -> throw new IllegalArgumentException(); }; }
        CompoundTag combat(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
        CompoundTag budget(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        void shield(ServerPlayer p, BigDecimal amount) { combat(p).putString("shield", amount.toPlainString()); }
        void reserve(ServerPlayer p, String lower, String upper) { CompoundTag r = new CompoundTag(); r.putInt("version", MagneticDomainReserve.VERSION); r.putString("capacity", HighPowerCombat.maxShieldText(p)); r.putString("lower", lower); r.putString("upper", upper); r.putString("amount", n(lower).add(n(upper)).toPlainString()); combat(p).put(MagneticDomainReserve.KEY, r); }
        void recoveryReady(ServerPlayer p) { combat(p).putLong("lastShieldSpend", p.level().getGameTime() - 61); combat(p).putLong("lastShieldTick", p.level().getGameTime() - 1); }
        Zombie front(ServerPlayer p) { return zombie(p.position().add(0, 0, 2)); }
        Zombie zombie(Vec3 at) { Zombie a = new Zombie(EntityType.ZOMBIE, h.getLevel()); a.setNoAi(true); a.setNoGravity(true); a.setSilent(true); a.setPos(at); a.getAttribute(Attributes.ARMOR).setBaseValue(0); a.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0); entity(a); return a; }
        Arrow arrow(ServerPlayer p, LivingEntity shooter, ItemStack weapon) { Arrow a = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon); a.setPos(p.position().add(0, 1, 2)); a.setDeltaMovement(0, 0, -3); entity(a); return a; }
        void entity(Entity e) { h.assertTrue(h.getLevel().addFreshEntity(e), "actual native entity joins server level"); entities.add(e); }
        boolean hit(ServerPlayer p, DamageSource source, float amount) { boolean previous = p.server.isPvpAllowed(); p.server.setPvpAllowed(true); try { return p.hurt(source, amount); } finally { p.server.setPvpAllowed(previous); } }
        void arrowTick(Arrow arrow) { boolean previous = h.getLevel().getServer().isPvpAllowed(); h.getLevel().getServer().setPvpAllowed(true); try { arrow.tick(); } finally { h.getLevel().getServer().setPvpAllowed(previous); } }
        boolean integratedLift(ServerPlayer p, int slot) { try { var method = WindPressureLift.class.getDeclaredMethod("toggle", ServerPlayer.class, String.class, boolean.class); method.setAccessible(true); return (boolean)method.invoke(null, p, wire(p, slot), true); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } }
        void hits(ServerPlayer p, Zombie a, int count, float amount) { for (int i = 0; i < count; i++) { p.invulnerableTime = 0; hit(p, p.damageSources().mobAttack(a), amount); } }
        void block(Vec3 at, BlockState state) { BlockPos pos = BlockPos.containing(at); previousBlocks.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Watch watch(LivingEntity p) { Watch result = new Watch(p); hook(result); return result; }
        PlayerPosts posts(ServerPlayer p) { PlayerPosts result = new PlayerPosts(this, p); hook(result); return result; }
        List<AutonomousVectorCircle.Visual> captureVisuals(ServerPlayer viewer, ServerPlayer owner) {
            List<AutonomousVectorCircle.Visual> packets = new ArrayList<>();
            channels.get(viewer).pipeline().addFirst("circle_balance_packets", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AutonomousVectorCircle.Visual v && v.owner().equals(owner.getUUID())) packets.add(v);
                    super.write(ctx, message, promise);
                }
            });
            return packets;
        }
        void drain(ServerPlayer p) { EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); AutonomousVectorCircle.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); AbilityStorage.get(p); }
        ServerPlayer rejoin(ServerPlayer old) {
            AbilityStorage.save(old); AutonomousVectorCircle.remove(old); AbilityStorage.remove(old); LegacySingleKeyOwnerEpoch.forget(old); old.server.getPlayerList().remove(old); players.remove(old); channels.remove(old).finishAndReleaseAll();
            var cookie = CommonListenerCookie.createInitial(old.getGameProfile(), false); ServerPlayer p = new ServerPlayer(old.server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel); NetworkRegistry.configureMockConnection(connection); p.server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            anchors.put(p, anchors.get(old)); p.setNoGravity(true); place(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        MachineDeveloperBlockEntity advanced(ServerPlayer p, double amount) {
            BlockPos base = h.absolutePos(new BlockPos(30, 1, 45)); if (machine == null) { var item = MachineDevelopers.ADVANCED_ITEM.get(); ItemStack stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack); h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "actual complete Advanced developer is placed"); machine = (MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base); }
            machine.endUsers(); machine.battery().load(amount); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0); h.assertTrue(machine.use(p), "real player-bound Advanced developer session opens"); return machine;
        }
        boolean learnRequest(ServerPlayer p) { return MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(ID, ++sequence).wire())); }
        void develop(ServerPlayer p, int ticks) { for (int i = 0; i < ticks; i++) DevelopmentController.tick(p, ignored -> {}); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); if (machine != null) machine.endUsers(); teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);
            for (ServerPlayer p : players) { AutonomousVectorCircle.remove(p); MultiAxisReflection.remove(p); WindPressureLift.remove(p); VectorCage.remove(p); CirculationRelief.remove(p); InertialAnchor.remove(p); VectorParry.remove(p); BrakingTouch.remove(p); DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); previousBlocks.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
