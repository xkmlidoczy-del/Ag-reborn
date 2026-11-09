package cn.academy.port.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
/** Cancel to reflect an incoming reflectible skill, as in classic ReflectEvent. */
public final class SkillReflectEvent extends Event implements ICancellableEvent {
    public final ServerPlayer attacker;
    public final String skill;
    public final Entity target;
    public SkillReflectEvent(ServerPlayer attacker,String skill,Entity target) {this.attacker=attacker;this.skill=skill;this.target=target;}
}
