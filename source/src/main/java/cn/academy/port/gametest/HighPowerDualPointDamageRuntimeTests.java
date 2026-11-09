package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real authenticated NEW211 ingress only; a declared SkillAttackEvent discount permits small native controls. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDualPointDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_dual_point_damage";
    private HighPowerDualPointDamageRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void new_md_source_does_not_expand_active_em_grounding_buffer_or_domain_interception(GameTestHelper h) {
        Fixture f = new Fixture(h); var caster = f.caster(6.5); var defender = f.player(6.5, HighPowerCategories.ELECTROMASTER, 5);
        defender.setPos(h.absoluteVec(new Vec3(6.5, 2, 12.5))); defender.connection.resetPosition();
        h.setBlock(new net.minecraft.core.BlockPos(6, 1, 12), net.minecraft.world.level.block.Blocks.STONE);
        var state = AbilityStorage.get(defender);
        for (String skill : List.of(GroundingReturn.ID, PotentialBuffer.ID, MagneticDomain.ID)) state.learn(skill);
        state.presets.edit(0, 0, GroundingReturn.ID, state::learned);
        state.presets.edit(0, 1, PotentialBuffer.ID, state::learned);
        state.presets.edit(0, 2, MagneticDomain.ID, state::learned);
        f.hook(new Discount(defender));
        h.runAfterDelay(61, () -> {
            defender.doTick(); defender.setPos(h.absoluteVec(new Vec3(6.5, 2, 12.5))); defender.setDeltaMovement(Vec3.ZERO); defender.connection.resetPosition();
            face(defender, 180); defender.setXRot(0); defender.setOnGround(true); defender.setShiftKeyDown(true);
            long epoch = LegacySingleKeyOwnerEpoch.current(defender, defender.connection, defender.serverLevel(), state);
            h.assertTrue(GroundingReturn.start(defender, new HighPowerInput.Input(0, 1, defender.getId(), epoch).wire()), "real EM grounding is active");
            h.assertTrue(PotentialBuffer.start(defender, new HighPowerInput.Input(1, 2, defender.getId(), epoch).wire()), "real EM front buffer is active");
            h.assertTrue(MagneticDomain.start(defender, new HighPowerInput.Input(2, 3, defender.getId(), epoch).wire()), "real paid EM domain is active");
            h.assertTrue(GroundingReturn.active(defender) && PotentialBuffer.active(defender) && MagneticDomain.active(defender), "all three native defense contexts coexist before the new source");
            BigDecimal shield = HighPowerCombat.shield(defender), reserve = MagneticDomainReserve.reserve(defender);
            f.aim(caster, defender);
            boolean oldPvp = caster.server.isPvpAllowed(); caster.server.setPvpAllowed(true);
            try { h.assertTrue(f.cast(caster), "actual NEW211 native player-target transaction"); }
            finally { caster.server.setPvpAllowed(oldPvp); }
            exact(h, defender, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(BigDecimal.valueOf(5)), "unreviewed MD beam is not reclassified as EM electrical or magnetic source");
            h.assertTrue(HighPowerCombat.shield(defender).compareTo(shield) == 0 && MagneticDomainReserve.reserve(defender).compareTo(reserve) == 0,
                    "new beam consumes neither existing shield nor EM-only reserve");
            h.assertTrue(state.exp(GroundingReturn.ID) == 0 && state.exp(PotentialBuffer.ID) == 0 && state.exp(MagneticDomain.ID) == 0,
                    "non-intercepted new source awards no defense mastery");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_hand_beam_retains_native_partial_armor_toughness_protection_resistance_and_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); var caster = f.caster(6); var exact = f.target(6, 12, true); var control = f.target(12, 12, false);
        for (var target : List.of(exact, control)) defenses(h, target);
        var hit = f.observe(exact); var nativeHit = f.observe(control); f.hook(new Discount(exact));
        f.aim(caster, exact); Vec3 origin = DualPoint.hand(caster, 0);
        BigDecimal mitigated = DamageMitigation.mitigate(BigDecimal.valueOf(5), new DefenseProfile(BigDecimal.valueOf(exact.getArmorValue()),
                BigDecimal.valueOf(exact.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(new BigDecimal(".5"));
        h.assertTrue(mitigated.signum() > 0 && mitigated.compareTo(BigDecimal.valueOf(5)) < 0, "fixture has partial, nonzero native mitigation");
        h.assertTrue(control.hurt(f.nativeSource(caster, origin), 5), "matching ordinary native PLAYER_ATTACK control accepts");
        h.assertTrue(f.cast(caster), "authenticated NEW211 creates its live emission permit");
        exact(h, exact, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(mitigated), "native reducers leave the exact residual, without aggregating another beam");
        h.assertTrue(hit.posts == 1 && hit.armor > 0 && hit.resistance > 0 && hit.enchantments > 0 && hit.absorption == .5F,
                "one native pipeline observes every ordinary partial reducer and consumes absorption once");
        h.assertTrue(hit.armor == nativeHit.armor && hit.resistance == nativeHit.resistance && hit.enchantments == nativeHit.enchantments
                && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                "small control has identical native reductions and matching bounded residual");
        h.assertTrue(exact.getAbsorptionAmount() == 0 && control.getAbsorptionAmount() == 0, "both native absorption pools are exhausted identically");
        source(h, hit.source, caster, origin);
        h.assertFalse(HighPowerCombat.attack(caster, "meltdowner.dual_point", exact, BigDecimal.ONE), "MD cannot use generic EM attack API");
        AbilityStorage.get(caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
        h.assertFalse(HighPowerCombat.attack(caster, "meltdowner.dual_point", exact, BigDecimal.ONE), "EM generic API cannot forge NEW211's named source either");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_hand_beam_front_and_rear_shields_match_fixed_player_attack_controls(GameTestHelper h) {
        Fixture f = new Fixture(h); var frontCaster = f.caster(6); var rearCaster = f.caster(18);
        var front = f.target(6, 12, true); var rear = f.target(18, 12, true);
        var frontControl = f.target(12, 12, false); var rearControl = f.target(24, 12, false);
        for (var target : List.of(front, frontControl)) raiseShield(target, 180);
        for (var target : List.of(rear, rearControl)) raiseShield(target, 0);
        f.hook(new Discount(front)); f.hook(new Discount(rear));
        var frontHit = f.observe(front); var rearHit = f.observe(rear); var frontNative = f.observe(frontControl); var rearNative = f.observe(rearControl);
        h.runAtTickTime(7, () -> {
            for (var target : List.of(front, frontControl)) face(target, 180);
            for (var target : List.of(rear, rearControl)) face(target, 0);
            h.assertTrue(front.isBlocking() && rear.isBlocking() && frontControl.isBlocking() && rearControl.isBlocking(), "real native shield raising delay completed");
            f.aim(frontCaster, front); f.aim(rearCaster, rear);
            Vec3 frontOrigin = DualPoint.hand(frontCaster, 0), rearOrigin = DualPoint.hand(rearCaster, 0);
            Vec3 nativeFrontOrigin = frontOrigin.add(frontControl.position().subtract(front.position()));
            Vec3 nativeRearOrigin = rearOrigin.add(rearControl.position().subtract(rear.position()));
            h.assertFalse(frontControl.hurt(f.nativeSource(frontCaster, nativeFrontOrigin), 5), "native front-facing shield blocks matching hand-origin source");
            h.assertTrue(rearControl.hurt(f.nativeSource(rearCaster, nativeRearOrigin), 5), "native rear source passes shield");
            h.assertTrue(f.cast(frontCaster) && f.cast(rearCaster), "both real casts are paid, independent of shield outcomes");
            exact(h, front, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "front block preserves all exact HP");
            exact(h, rear, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(BigDecimal.valueOf(5)), "rear hit delivers exactly its own five-point residual");
            h.assertTrue(frontHit.blocked && frontNative.blocked && frontHit.blockedDamage == frontNative.blockedDamage && frontHit.blockedDamage == 5,
                    "same native frontal shield decision and block amount");
            h.assertTrue(!rearHit.blocked && !rearNative.blocked && rearHit.damage == rearNative.damage && rearHit.damage == 5,
                    "same native rear shield decision and HP damage");
            source(h, frontHit.source, frontCaster, frontOrigin); source(h, rearHit.source, rearCaster, rearOrigin);
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_permit_revalidates_native_callback_cancellation_geometry_and_ordinary_targets(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (int stage = 0; stage < 4; stage++) for (boolean enrolled : List.of(true, false)) {
            double x = 6 + stage * 16 + (enrolled ? 0 : 8);
            var caster = f.caster(x); var target = f.target(x, 12, enrolled); f.aim(caster, target);
            var mutation = new CallbackMutation(target, stage); f.hook(mutation); var observation = f.observe(target);
            f.hook(new Discount(target));
            double before = HighPowerResources.cp(caster);
            h.assertTrue(f.cast(caster), "paid controller cast starts before callback at stage " + stage);
            h.assertTrue(mutation.called, "real skill/incoming/pre callback executes at stage " + stage);
            if (enrolled) exact(h, target, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "callback veto cannot debit exact HP");
            else h.assertTrue(target.getHealth() == target.getMaxHealth(), "late permit guard protects ordinary native HP too");
            h.assertTrue(HighPowerResources.cp(caster) < before, "native cancellation does not refund paid CP");
            h.assertTrue(AbilityStorage.get(caster).exp(HighPowerSkills.DUAL_POINT) == 0, "vetoed victim does not award skill XP");
            if (stage == 0 || stage == 1) h.assertTrue(observation.posts == 0, "early veto has no post-damage event");
        }
        h.succeed();
    }

    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void raiseShield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; }
    private static void exact(GameTestHelper h, LivingEntity target, BigDecimal expected, String message) { h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(expected) == 0, message + ": " + HighPowerCombat.healthText(target) + " expected " + expected); }
    private static void source(GameTestHelper h, DamageSource source, ServerPlayer caster, Vec3 origin) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == caster && source.getDirectEntity() == caster && origin.equals(source.getSourcePosition()) && origin.equals(source.sourcePositionRaw()),
                "source is fixed native PLAYER_ATTACK from the actual server hand, with player attribution and no projectile entity");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "beam source has no native defense bypass flags");
    }
    public static final class Discount {
        final LivingEntity target; Discount(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals("meltdowner.dual_point")) event.amount = 5; }
    }
    public static final class CallbackMutation {
        final LivingEntity target; final int stage; boolean called;
        CallbackMutation(LivingEntity target, int stage) { this.target = target; this.stage = stage; }
        private void move() { if (!called) { called = true; target.setPos(target.position().add(3, 0, 0)); } }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent event) { if (event.target == target && stage == 0) { called = true; event.amount = 0; } }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() != target) return; if (stage == 1) { called = true; event.setCanceled(true); } else if (stage == 2) move(); }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre event) { if (event.getEntity() == target && stage == 3) move(); }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int posts; boolean blocked; float damage, armor, resistance, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) source = event.getSource(); }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { blocked = event.getBlocked(); blockedDamage = event.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) { if (event.getEntity() != target) return; posts++; damage = event.getNewDamage(); armor = event.getReduction(DamageContainer.Reduction.ARMOR); resistance = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION); }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final List<EmbeddedChannel> channels = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        ServerPlayer caster(double x) { return player(x, HighPowerCategories.MELTDOWNER, 1); }
        ServerPlayer player(double x, String category, int level) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPDualDamage"), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setNoGravity(true); p.setGameMode(GameType.SURVIVAL); p.setPos(h.absoluteVec(new Vec3(x, 2, 5)));
            var s = AbilityStorage.get(p); s.changeCategoryClassic(category); s.setLevel(level); s.learn(HighPowerCategories.recoveryRoot(category)); s.setActivateState(true); s.overloadFine = true;
            s.presets.edit(0, 0, HighPowerCategories.recoveryRoot(category), s::learned);
            h.assertTrue(HighPowerCombat.enroll(p, level), "native connected high-power player " + HighPowerCombat.unsupportedReason(p)); HighPowerResources.initializeBudget(p); return p;
        }
        Villager target(double x, double z, boolean enrolled) {
            var target = new Villager(EntityType.VILLAGER, h.getLevel()); target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(x, 2, z)));
            h.getLevel().addFreshEntity(target); entities.add(target); if (enrolled) h.assertTrue(HighPowerCombat.enroll(target, 5), "native exact target fixture"); return target;
        }
        void aim(ServerPlayer caster, LivingEntity target) { Vec3 d = target.getBoundingBox().getCenter().subtract(caster.getEyePosition()); caster.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z))); caster.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))); caster.setShiftKeyDown(false); }
        boolean cast(ServerPlayer caster) { var s = AbilityStorage.get(caster); long epoch = LegacySingleKeyOwnerEpoch.current(caster, caster.connection, caster.serverLevel(), s); return DualPoint.tap(caster, new HighPowerInput.Input(0, 1, caster.getId(), epoch).wire()); }
        DamageSource nativeSource(ServerPlayer caster, Vec3 origin) { return new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), caster, caster, origin); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Observation observe(LivingEntity target) { var observation = new Observation(target); hook(observation); return observation; }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); for (var p : players) { DualPoint.remove(p); GroundingReturn.remove(p); PotentialBuffer.remove(p); MagneticDomain.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); } entities.forEach(Entity::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll); }
    }
}
