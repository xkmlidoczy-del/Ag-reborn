package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Declared LV5 controller/combat fixture; paid skill acquisition has its own native suite.
 * All combat is real Arrow.tick/hurt dispatch. Native login protection expires by actual ticks.
 * Explicit resource seeds use the validated shared transaction boundary and never fake a contact.
 */
final class ZeroGapArmorNativeFixture {
    final GameTestHelper h;
    final ServerPlayer p;
    final Zombie shooter;
    final EmbeddedChannel channel;
    final double offset;
    final List<Entity> entities = new ArrayList<>();
    final List<Object> hooks = new ArrayList<>();
    final List<BlockPos> blocks = new ArrayList<>();
    boolean closed;

    ZeroGapArmorNativeFixture(GameTestHelper h, double offset) {
        this.h = h; this.offset = offset;
        var server = h.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ZA" + UUID.randomUUID().toString().substring(0, 10)), false);
        p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false; place();
        var s = AbilityStorage.get(p); s.selectCategory("meltdowner"); s.setLevel(5);
        for (String id : List.of(DualPoint.ID, ScatterPlates.ID, DissipationCurtain.ID, ZeroGapArmor.ID)) s.learn(id);
        s.activated = true; s.overloadFine = true;
        h.assertTrue(HighPowerCombat.enroll(p, 5), "declared connected native LV5 owner");
        String[] bound = {ZeroGapArmor.ID, ScatterPlates.ID, DissipationCurtain.ID, DualPoint.ID};
        for (int slot = 0; slot < bound.length; slot++) h.assertTrue(s.presets.edit(0, slot, bound[slot], s::learned), "bind native slot " + slot);
        HighPowerResources.initializeBudget(p); ScatterPlateStorage.ensure(p);
        if (ZeroGapArmorStorage.read(p).isEmpty()) h.assertTrue(ZeroGapArmorStorage.apply(
                ZeroGapArmorStorage.prepareInitialization(ZeroGapArmorStorage.capture(p).orElseThrow())), "explicit valid252 fixture initialization");
        epoch(); shooter = mob(p.position().add(5, 0, 5));
        h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
        });
        // GameTest's callback map is unordered. The existing bounded pump runs once per
        // genuine ServerTick.Post, before next tick's ingress, and close() unregisters it.
        hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
    }

    void place() {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 18 + offset, 8.5));
        p.moveTo(at.x, at.y, at.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition();
    }
    long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
    String wire(long sequence) { return wire(0, sequence); }
    String wire(int slot, long sequence) { return new HighPowerInput.Input(slot, sequence, p.getId(), epoch()).wire(); }
    boolean request(String action, String wire) { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request(action, wire)); }
    boolean toggle(long sequence) { return request("hp_zero_gap_armor_toggle", wire(sequence)); }
    void start() { h.assertTrue(toggle(1), "native authenticated toggle pays startup"); h.assertFalse(ZeroGapArmor.active(p), "six-tick startup is unprotected"); }
    BigDecimal hp() { return new BigDecimal(HighPowerCombat.healthText(p)); }
    BigDecimal shield() { return HighPowerCombat.shield(p); }
    double cp() { return HighPowerResources.cp(p); }
    double mastery() { return AbilityStorage.get(p).exp(ZeroGapArmor.ID); }
    ZeroGapArmorStorage.Capture saved() { return ZeroGapArmorStorage.capture(p).orElseThrow(); }
    ZeroGapArmorShares armor() { return ZeroGapArmorStorage.read(p).orElseThrow(); }
    ZeroGapArmorTiming timing() { return saved().timing().orElseThrow(); }
    ZeroGapArmorGeometry.Snapshot pose() { return ZeroGapArmorGeometry.capture(vector(p.position()), p.getYRot(), timing()); }
    ZeroGapArmorGeometry.Plate front() {
        return pose().plates().stream().filter(ZeroGapArmorGeometry.Plate::active)
                .max(Comparator.comparingDouble(plate -> plate.normal().dot(vector(p.getLookAngle())))).orElseThrow();
    }
    Vec3 center() { return p.position().add(0, ZeroGapArmorGeometry.CENTER_HEIGHT, 0); }
    Arrow frontArrow(double radius, double speed) { return radial(front(), radius, speed, 0, null); }
    Arrow radial(ZeroGapArmorGeometry.Plate plate, double radius, double speed, double right, ItemStack weapon) {
        Vec3 normal = vec(plate.normal()), side = vec(ZeroGapArmorGeometry.frame(plate).right());
        return arrow(center().add(normal.scale(radius)).add(side.scale(right)), normal.scale(-speed), weapon);
    }
    Arrow arrow(Vec3 at, Vec3 velocity, ItemStack weapon) {
        Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon);
        arrow.setPos(at); arrow.setDeltaMovement(velocity); h.getLevel().addFreshEntity(arrow); entities.add(arrow); return arrow;
    }
    /** Crosses the real narrow plate, then diverges past the inflated native body. */
    Arrow graze() {
        var plate = front(); Vec3 normal = vec(plate.normal()), side = vec(ZeroGapArmorGeometry.frame(plate).right());
        return arrow(center().add(normal.scale(1.6)).add(side.scale(.25)), normal.scale(-.5).add(side.scale(.25)), null);
    }
    Zombie mob(Vec3 at) {
        Zombie result = EntityType.HUSK.create(h.getLevel()); result.setNoAi(true); result.setNoGravity(true); result.setSilent(true);
        result.getAttribute(Attributes.ARMOR).setBaseValue(0); result.setPos(at); h.getLevel().addFreshEntity(result); entities.add(result); return result;
    }
    void wall(Vec3 at) { var block = BlockPos.containing(at); h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState()); blocks.add(block); }
    void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }

    /** Explicit exact asymmetric currents. Every parent and every child is published atomically. */
    void seed(List<BigDecimal> bases, List<BigDecimal> reserves) {
        var captured = saved(); var before = captured.armor().orElseThrow();
        BigDecimal total = bases.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var base = new ExactCombatState(before.base().health(), before.base().maxHealth(), total, before.base().maxShield(), false);
        var parents = ScatterPlateShares.restore(total, base.maxShield(), List.of(bases.get(0).add(bases.get(1)), bases.get(2).add(bases.get(3)), bases.get(4).add(bases.get(5))));
        var halves = new SharedShieldReserveShares(reserves.get(0).add(reserves.get(2)).add(reserves.get(4)), reserves.get(1).add(reserves.get(3)).add(reserves.get(5)));
        var after = ZeroGapArmorShares.restore(base, parents, halves, bases, reserves);
        List<Integer> breaks = new ArrayList<>();
        for (int i = 0; i < 6; i++) if (before.plate(i).current().signum() > 0 && after.plate(i).current().signum() == 0) breaks.add(i);
        h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(captured, new ZeroGapArmorShares.Transaction(before, after, breaks))), "declared exact resource fixture");
        ZeroGapArmor.resourcesChanged(p, before, after);
    }
    void health(String value) {
        var captured = saved(); var before = armor();
        var base = new ExactCombatState(new BigDecimal(value), before.base().maxHealth(), before.base().shield(), before.base().maxShield(), false);
        h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(captured, before.reconcileParents(base, before.scatter(), before.reserves()))), "declared exact injury fixture");
    }
    void reload() {
        AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag());
        ZeroGapArmor.remove(p); ScatterPlates.remove(p); DissipationCurtain.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
        p.load(saved); place(); AbilityStorage.get(p); epoch();
    }
    void close() {
        if (closed) return; closed = true;
        hooks.forEach(NeoForge.EVENT_BUS::unregister); blocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        ZeroGapArmor.remove(p); ScatterPlates.remove(p); DissipationCurtain.remove(p); DualPoint.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
        p.server.getPlayerList().remove(p); entities.forEach(Entity::discard); channel.finishAndReleaseAll();
    }

    static void ready(GameTestHelper h, Runnable action, ZeroGapArmorNativeFixture... active) {
        h.runAfterDelay(61, () -> {
            for (var f : active) f.start();
            h.runAfterDelay(6, () -> { for (var f : active) h.assertTrue(ZeroGapArmor.active(f.p), "six genuine startup ticks elapsed"); action.run(); });
        });
    }
    static ZeroGapArmorNativeFixture[] fixtures(GameTestHelper h, int count) {
        var all = new ZeroGapArmorNativeFixture[count]; for (int i = 0; i < count; i++) all[i] = new ZeroGapArmorNativeFixture(h, i * 14); return all;
    }
    static List<BigDecimal> amounts(String... values) { return Arrays.stream(values).map(BigDecimal::new).toList(); }
    static List<BigDecimal> copies(String value) { return Collections.nCopies(6, new BigDecimal(value)); }
    static Vec3 vec(FoldedMirrorGeometry.Vector vector) { return new Vec3(vector.x(), vector.y(), vector.z()); }
    static FoldedMirrorGeometry.Vector vector(Vec3 vector) { return new FoldedMirrorGeometry.Vector(vector.x, vector.y, vector.z); }
    static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " / " + expected); }
    static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-8, why + ": " + actual + " / " + expected); }
}
