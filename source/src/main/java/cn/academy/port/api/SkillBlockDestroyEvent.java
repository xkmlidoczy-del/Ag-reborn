/* AcademyCraft1.0.7 BlockDestroyEvent bridge, GPLv3; see NOTICE. */
package cn.academy.port.api;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
/** Source-style permission stage, including air probes. Cancel prevents the skill's block action. */
public final class SkillBlockDestroyEvent extends Event implements ICancellableEvent {
    public final ServerPlayer player;public final String skill;public final BlockPos position;
    public SkillBlockDestroyEvent(ServerPlayer player,String skill,BlockPos position){this.player=player;this.skill=skill;this.position=position.immutable();}
}
