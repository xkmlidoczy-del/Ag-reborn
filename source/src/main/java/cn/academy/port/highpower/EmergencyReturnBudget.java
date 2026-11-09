package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.highpower.core.EmergencyReturnRules;
import cn.academy.port.highpower.core.ExactCombatState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Closed NEW342 base-S payment capability; no public generic teleporter shield spender. */
final class EmergencyReturnBudget {
    enum Result { NOT_PAID, PAID, PAID_OR_UNCERTAIN }

    private EmergencyReturnBudget() {}

    /** Minted only for the controller's currently authenticated automatic-return transaction. */
    static final class Payment {
        private final ServerPlayer owner;
        private final Object transaction;
        private final AbilityProgress ability;
        private final ServerLevel level;
        private final CompoundTag source, snapshot;
        private final ExactCombatState before;
        private boolean attempted;

        private Payment(ServerPlayer owner, Object transaction, ExactCombatState before) {
            this.owner = owner;
            this.transaction = transaction;
            this.ability = AbilityStorage.get(owner);
            this.level = owner.serverLevel();
            this.source = tag(owner);
            this.snapshot = source.copy();
            this.before = before;
        }

        /** No caller callbacks occur between this claim and the fixed resource commit. */
        boolean claim(ServerPlayer player, ExactCombatState current) {
            if (!before.equals(current) || !valid(player, this)) return false;
            attempted = true;
            // Controller consumes its one-shot transaction/mark and stamps the persistent
            // automatic fence before a resource writer may notify reentrant observers.
            return EmergencyReturn.claimAutomaticPayment(player, transaction);
        }

        boolean attempted() { return attempted; }
    }

    static Payment quote(ServerPlayer player, Object transaction) {
        if (!eligible(player) || transaction == null || !EmergencyReturn.validPayment(player, transaction)) return null;
        var base = HighPowerCombat.exactStateSnapshot(player);
        if (base.isEmpty() || EmergencyReturnRules.automaticSpend(base.get()) == null) return null;
        return new Payment(player, transaction, base.get());
    }

    /** The teleport callback must not replace or alter the quoted exact shared resources. */
    static boolean valid(ServerPlayer player, Payment payment) {
        if (payment == null || payment.attempted || payment.owner != player || !eligible(player)
                || payment.ability != AbilityStorage.get(player) || payment.level != player.serverLevel()
                || payment.source != tag(player) || !payment.snapshot.equals(payment.source)
                || !EmergencyReturn.validPayment(player, payment.transaction)) return false;
        return HighPowerCombat.exactStateSnapshot(player).filter(payment.before::equals).isPresent();
    }

    static Result pay(ServerPlayer player, Payment payment) {
        if (!valid(player, payment)) return Result.NOT_PAID;
        return HighPowerCombat.spendEmergencyReturn(player, payment);
    }

    private static boolean eligible(ServerPlayer player) {
        return player != null && !player.level().isClientSide && player.server.isSameThread()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && HighPowerCombat.emergencyReturnBenefitsActive(player)
                && HighPowerCombat.unsupportedReason(player).isEmpty();
    }

    private static CompoundTag tag(ServerPlayer player) {
        return player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
    }
}
