package cn.academy.port.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
/** Modern equivalent of the classic mutable CalcEvent.SkillAttack stage. Server-thread only. */
public final class SkillAttackEvent extends Event {
    public final ServerPlayer player;
    public final String skill;
    public final Entity target;
    public double amount;
    private final boolean nativeAmount;
    public SkillAttackEvent(ServerPlayer player, String skill, Entity target, double amount) {
        this(player, skill, target, amount, false);
    }
    public SkillAttackEvent(ServerPlayer player, String skill, Entity target, double amount, boolean nativeAmount) {
        this.player = player;
        this.skill = skill;
        this.target = target;
        this.amount = amount;
        this.nativeAmount = nativeAmount;
    }
    /** Counters already start from actual shield or incoming damage. */
    public boolean nativeAmount() { return nativeAmount; }
}
