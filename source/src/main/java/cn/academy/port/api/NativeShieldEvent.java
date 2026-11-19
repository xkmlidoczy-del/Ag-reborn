package cn.academy.port.api;

import java.math.BigDecimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;

/** Remaining native damage after vanilla absorption and Academy's temporary defenses. */
public final class NativeShieldEvent extends Event {
    private final ServerPlayer player;
    private final DamageSource source;
    private BigDecimal remaining;

    public NativeShieldEvent(ServerPlayer player, DamageSource source, BigDecimal damage) {
        this.player = player;
        this.source = source;
        this.remaining = damage;
    }

    public ServerPlayer player() { return player; }
    public DamageSource source() { return source; }
    public BigDecimal remaining() { return remaining; }

    /** Returns the exact amount paid by the recipient's shield. */
    public BigDecimal absorb(BigDecimal available) {
        BigDecimal debit = remaining.min(available.max(BigDecimal.ZERO));
        remaining = remaining.subtract(debit);
        return debit;
    }
}
