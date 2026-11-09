package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.TrajectoryReturn;
import cn.academy.port.highpower.core.CombatAmounts;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real native contacts test the interval after absorption observation but before commit. */
@GameTestHolder("academy_trajectory_return_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerTrajectoryReturnPostGuardRuntimeTests {
    private HighPowerTrajectoryReturnPostGuardRuntimeTests() { }

    @GameTest(template = "highpower_empty", batch = "highpower_trajectory_return", timeoutTicks = 20)
    public static void late_absorption_synthetic_post_cannot_settle_return_before_final_guard(GameTestHelper h) {
        // The same invalidation is irrelevant to an ordinary arrow's genuine native contact.
        try (Fixture f = new Fixture(h)) {
            Zombie target = f.target();
            Arrow arrow = f.arrow(new Vec3(0, 0, 1.5));
            boolean[] observed = {false};
            f.hook(new Object() {
                @SubscribeEvent(priority = EventPriority.LOWEST)
                public void incoming(LivingIncomingDamageEvent event) {
                    if (event.getEntity() != target) return;
                    event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                        observed[0] = true;
                        AbilityStorage.get(f.p).activated = false;
                        return reduction;
                    });
                }
            });
            fly(arrow);
            BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
            h.assertTrue(observed[0] && loss.signum() > 0 && loss.compareTo(BigDecimal.valueOf(100)) < 0,
                    "ordinary native control retains literal arrow damage despite unrelated caster invalidation");
        }

        // Without the forged notification the same final guard must refuse all enhanced HP damage.
        for (boolean synthetic : List.of(false, true)) try (Fixture f = new Fixture(h)) {
            Zombie target = f.target();
            Arrow arrow = f.arrow(new Vec3(0, 0, -1.5));
            boolean[] observed = {false};
            BigDecimal[] afterNotification = {null};
            f.hook(new Object() {
                @SubscribeEvent(priority = EventPriority.LOWEST)
                public void incoming(LivingIncomingDamageEvent event) {
                    if (event.getEntity() != target) return;
                    event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                        observed[0] = true;
                        // Zero absorption makes this the exact observed pre-commit amount. This is
                        // only an event notification; no additional hurt call or health write occurs.
                        if (synthetic) NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(target, container));
                        afterNotification[0] = hp(target);
                        AbilityStorage.get(f.p).activated = false;
                        return reduction;
                    });
                }
            });
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(f.cast(), "genuine native return is paid before the late absorption callback");
            fly(arrow);
            h.assertTrue(observed[0], "late absorption callback ran inside the real native contact");
            h.assertTrue(afterNotification[0].compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0,
                    "same-source synthetic Post must not debit exact HP before the final commit guard; synthetic="
                            + synthetic + ", observed=" + afterNotification[0]);
            h.assertTrue(hp(target).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0,
                    "caster invalidation at the final guard leaves exact HP untouched; synthetic=" + synthetic);
            h.assertTrue(Math.abs(HighPowerResources.cp(f.p) - (cp - 4.5)) < 1e-6,
                    "later refused native damage does not refund the completed reversal");
            h.assertTrue(TrajectoryReturn.receipts(f.p).stream().noneMatch(receipt ->
                            receipt.kind() == TrajectoryReturn.CONTACT
                                    && (receipt.status() == TrajectoryReturn.DAMAGED || receipt.status() == TrajectoryReturn.ABSORBED)),
                    "synthetic notification cannot become a confirmed NEW431 contact");
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "highpower_trajectory_return", timeoutTicks = 20)
    public static void late_absorption_synthetic_post_cannot_settle_airhammer_before_final_guard(GameTestHelper h) {
        for (boolean synthetic : List.of(false, true)) try (Fixture f = new Fixture(h)) {
            Zombie target = f.target();
            boolean[] observed = {false};
            BigDecimal[] afterNotification = {null};
            f.hook(new Object() {
                @SubscribeEvent(priority = EventPriority.LOWEST)
                public void incoming(LivingIncomingDamageEvent event) {
                    if (event.getEntity() != target) return;
                    event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                        observed[0] = true;
                        if (synthetic) NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(target, container));
                        afterNotification[0] = hp(target);
                        AbilityStorage.get(f.p).activated = false;
                        return reduction;
                    });
                }
            });
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(f.cast(), "empty-corridor input commits its one paid native air-hammer attempt");
            h.assertTrue(observed[0], "air-hammer reaches the late absorption callback through native hurt");
            h.assertTrue(afterNotification[0].compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0,
                    "air-hammer notification cannot debit exact HP before final commit; synthetic=" + synthetic);
            h.assertTrue(hp(target).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0,
                    "late caster invalidation leaves air-hammer victim HP untouched; synthetic=" + synthetic);
            h.assertTrue(Math.abs(HighPowerResources.cp(f.p) - (cp - 4.5)) < 1e-6,
                    "vetoed air-hammer still retains its one irreversible cast debit");
            h.assertTrue(TrajectoryReturn.receipts(f.p).stream().noneMatch(receipt ->
                            receipt.kind() == TrajectoryReturn.AIR_HAMMER
                                    && (receipt.status() == TrajectoryReturn.DAMAGED || receipt.status() == TrajectoryReturn.ABSORBED)),
                    "premature air-hammer notification cannot become a successful receipt");
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "highpower_trajectory_return", timeoutTicks = 20)
    public static void premature_notifications_preserve_one_genuine_arrow_and_airhammer_settlement(GameTestHelper h) {
        for (boolean returnedArrow : List.of(true, false)) for (boolean synthetic : List.of(false, true))
            try (Fixture f = new Fixture(h)) {
                Zombie target = f.target();
                Arrow arrow = returnedArrow ? f.arrow(new Vec3(0, 0, -1.5)) : null;
                BigDecimal raw = HighPowerGrowth.values(f.p).damage().multiply(BigDecimal.valueOf(2)).min(CombatAmounts.CAP);
                BigDecimal[] afterNotification = {null};
                int[] incoming = {0}, posts = {0};
                f.hook(new Object() {
                    @SubscribeEvent(priority = EventPriority.LOWEST)
                    public void incoming(LivingIncomingDamageEvent event) {
                        if (event.getEntity() != target) return;
                        incoming[0]++;
                        event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                            if (synthetic) NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(target, container));
                            afterNotification[0] = hp(target);
                            return reduction;
                        });
                    }

                    @SubscribeEvent public void post(LivingDamageEvent.Post event) {
                        if (event.getEntity() == target) posts[0]++;
                    }
                });
                double cp = HighPowerResources.cp(f.p);
                h.assertTrue(f.cast(), "genuine closed input starts the successful native branch");
                if (arrow != null) fly(arrow);
                h.assertTrue(afterNotification[0] != null && afterNotification[0].compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0,
                        "pre-commit notification leaves HP untouched before genuine settlement; arrow="
                                + returnedArrow + ", synthetic=" + synthetic);
                h.assertTrue(hp(target).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(raw)) == 0,
                        "genuine native Post settles exactly one frozen 2D after the ignored notification; arrow="
                                + returnedArrow + ", synthetic=" + synthetic);
                h.assertTrue(incoming[0] == 1 && posts[0] == (synthetic ? 2 : 1),
                        "one actual hurt produces one native Post alongside only the deliberate synthetic notification");
                int kind = returnedArrow ? TrajectoryReturn.CONTACT : TrajectoryReturn.AIR_HAMMER;
                h.assertTrue(TrajectoryReturn.receipts(f.p).stream().filter(receipt ->
                                receipt.kind() == kind && receipt.status() == TrajectoryReturn.DAMAGED).count() == 1,
                        "actual measured HP loss produces exactly one successful branch receipt");
                h.assertTrue(Math.abs(HighPowerResources.cp(f.p) - (cp - 4.5)) < 1e-6,
                        "successful notification controls retain one cast debit");
            }
        h.succeed();
    }

    private static BigDecimal hp(LivingEntity target) {
        return new BigDecimal(HighPowerCombat.healthText(target));
    }

    private static void fly(Arrow arrow) {
        for (int tick = 0; tick < 12 && !arrow.isRemoved(); tick++) arrow.tick();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerPlayer p;
        final EmbeddedChannel channel;
        final Skeleton shooter;
        final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();

        Fixture(GameTestHelper h) {
            this.h = h;
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),
                    "TRP" + UUID.randomUUID().toString().substring(0, 9)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks();
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundPlayerPositionPacket position)
                    p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                ReferenceCountUtil.release(packet);
            }
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true);
            Vec3 at = h.absoluteVec(new Vec3(8.5, 5, 5.5));
            p.moveTo(at.x, at.y, at.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p, 3), "declared connected caster enrollment");
            var state = AbilityStorage.get(p);
            state.selectCategory("vecmanip"); state.setLevel(3);
            for (String id : List.of("deflection_strike", "braking_touch", "yaw_palm", "trajectory_return")) state.learn(id);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, "trajectory_return", state::learned), "native input binding");
            HighPowerResources.initializeBudget(p);
            shooter = new Skeleton(EntityType.SKELETON, h.getLevel());
            add(shooter, p.getEyePosition().add(6, 0, 14));
        }

        void add(LivingEntity body, Vec3 center) {
            if (body instanceof Mob mob) mob.setNoAi(true);
            body.setNoGravity(true); body.setSilent(true);
            body.setPos(center.add(0, -body.getBbHeight() / 2, 0));
            body.getAttribute(Attributes.ARMOR).setBaseValue(0);
            body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(h.getLevel().addFreshEntity(body), "genuine body joins the native level");
            entities.add(body);
        }

        Zombie target() {
            Zombie target = EntityType.HUSK.create(h.getLevel());
            add(target, p.getEyePosition().add(0, 0, 12));
            h.assertTrue(HighPowerCombat.enroll(target, 5), "declared exact hostile target");
            return target;
        }

        Arrow arrow(Vec3 velocity) {
            Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            Vec3 at = p.getEyePosition().add(0, 0, 6);
            arrow.setPos(at); arrow.setDeltaMovement(velocity);
            h.assertTrue(h.getLevel().addFreshEntity(arrow), "native original arrow joins the level");
            entities.add(arrow);
            arrow.tick(); // Native Projectile.tick establishes leftOwner before the declared fixture reset.
            arrow.setPos(at); arrow.setDeltaMovement(velocity);
            return arrow;
        }

        boolean cast() {
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p));
            return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("trajectory_return_tap",
                    new HighPowerInput.Input(0, 1, p.getId(), epoch).wire()));
        }

        void hook(Object observer) { hooks.add(observer); NeoForge.EVENT_BUS.register(observer); }

        @Override public void close() {
            hooks.forEach(NeoForge.EVENT_BUS::unregister);
            TrajectoryReturn.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
            p.server.getPlayerList().remove(p); entities.forEach(Entity::discard); channel.finishAndReleaseAll();
        }
    }
}
