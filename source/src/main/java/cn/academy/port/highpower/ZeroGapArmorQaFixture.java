package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.ScatterPlateShares;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit, private-world QA resource spend. Never enrolls, heals, refills or fabricates a break. */
@EventBusSubscriber(modid = "academy")
public final class ZeroGapArmorQaFixture {
    private static final String PROPERTY = "academy.highpower.zeroGapArmorFixture";
    private static final String WORLD = "aca-hp-md252-test";
    private ZeroGapArmorQaFixture() {}

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (Boolean.getBoolean(PROPERTY)) dispatcher.register(Commands.literal("academyhp_qa_drain252")
                .requires(source -> source.hasPermission(2)).executes(context -> drain(context.getSource())));
    }

    private static boolean isolated(boolean enabled, boolean operator, boolean dedicated,
                                    boolean singleplayer, boolean published, Path root) {
        return enabled && operator && !dedicated && singleplayer && !published && root != null
                && root.getFileName() != null && WORLD.equals(root.getFileName().toString());
    }

    private static int drain(CommandSourceStack source) {
        var server = source.getServer();
        if (!Boolean.getBoolean(PROPERTY) || !source.hasPermission(2) || !server.isSameThread()
                || !(source.getEntity() instanceof ServerPlayer player)) return refuse(source);
        try {
            if (!isolated(true, true, server.isDedicatedServer(), server.isSingleplayer(), server.isPublished(),
                    server.getWorldPath(LevelResource.ROOT).toRealPath()) || player.server != server)
                return refuse(source);
            var captured = ZeroGapArmorStorage.capture(player).orElseThrow(() -> new IllegalStateException("Unavailable ledger"));
            if (!eligible(player)) return refuse(source);
            var prepared = prepare(captured);
            BigDecimal before = captured.armor().orElseThrow().total(), after = prepared.after().total();
            if (before.compareTo(after) > 0) {
                if (!ZeroGapArmorStorage.apply(prepared)) return refuse(source);
                // Existing spend semantics: only a real debit starts the ordinary recovery delay.
                player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)
                        .putLong("lastShieldSpend", player.level().getGameTime());
            }
            source.sendSuccess(() -> Component.literal("TEST-ONLY NEW252 drain: " + before.toPlainString()
                    + " -> " + after.toPlainString() + "; no heal/refill, capacity, CP or XP change."), false);
            return 1;
        } catch (IOException | IllegalArgumentException | IllegalStateException invalid) { return refuse(source); }
    }

    private static boolean eligible(ServerPlayer p) {
        var state = AbilityStorage.peek(p);
        return state != null && state.category.equals("meltdowner") && state.level == 5 && state.activated
                && state.overloadFine && !state.interfering && state.learned(ZeroGapArmor.ID)
                && state.learned(ScatterPlates.ID) && state.learned(DissipationCurtain.ID) && state.learned(DualPoint.ID)
                && SkillCatalog.enabled("meltdowner", ZeroGapArmor.ID) && p.isAlive() && !p.isRemoved()
                && p.connection != null && !p.hasDisconnected() && !p.isSpectator() && !p.getAbilities().instabuild
                && !p.getAbilities().invulnerable && !p.noPhysics && p.hurtTime == 0 && p.invulnerableTime == 0
                && !HighPowerCombat.nativeHurtInProgress(p) && !AbilityConsumption.busy(p)
                && !HighPowerResources.hasReservation(p) && HighPowerCombat.unsupportedReason(p).isEmpty()
                && ZeroGapArmor.paidUntil(p) == 0 && ScatterPlates.paidUntil(p) == 0
                && DissipationCurtain.paidUntil(p) == 0 && ThermalShell.paidUntil(p) == 0
                && MagneticDomain.paidUntil(p) == 0;
    }

    /** Read-only proposal; no public unguarded fixture mutator or alternate resource writer. */
    private static ZeroGapArmorStorage.Prepared prepare(ZeroGapArmorStorage.Capture captured) {
        ZeroGapArmorShares before = captured.armor().orElseThrow(() -> new IllegalStateException("Missing NEW252"));
        if (captured.timing().isEmpty() || before.base().isDead()) throw new IllegalStateException("Unavailable owner");
        List<BigDecimal> bases = new ArrayList<>(), reserves = new ArrayList<>(), parents = new ArrayList<>();
        BigDecimal shield = BigDecimal.ZERO, lower = BigDecimal.ZERO, upper = BigDecimal.ZERO;
        for (int i = 0; i < ZeroGapArmorShares.PLATE_COUNT; i++) {
            BigDecimal base = before.plate(i).baseCurrent().min(BigDecimal.ONE);
            BigDecimal reserve = before.plate(i).reserveCurrent().min(BigDecimal.ONE);
            bases.add(base); reserves.add(reserve); shield = shield.add(base);
            if ((i & 1) == 0) lower = lower.add(reserve); else upper = upper.add(reserve);
            if ((i & 1) == 1) parents.add(bases.get(i - 1).add(base));
        }
        ExactCombatState base = new ExactCombatState(before.base().health(), before.base().maxHealth(),
                shield, before.base().maxShield(), before.base().shieldEnabled());
        ScatterPlateShares scatter = ScatterPlateShares.restore(shield, base.maxShield(), parents);
        SharedShieldReserveShares reserve = new SharedShieldReserveShares(lower, upper).validate(base);
        ZeroGapArmorShares after = ZeroGapArmorShares.restore(base, scatter, reserve, bases, reserves);
        return ZeroGapArmorStorage.prepare(captured, new ZeroGapArmorShares.Transaction(before, after, List.of()));
    }

    private static int refuse(CommandSourceStack source) {
        source.sendFailure(Component.literal("TEST-ONLY drain refused: requires explicit fixture opt-in, OP2, private integrated "
                + WORLD + ", valid learned LV5 MD ledger, alive idle owner and no active defense. Nothing changed."));
        return 0;
    }
}
