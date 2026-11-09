/* AcademyCraft1.0.7 CalcEvent.SkillPerform adaptation. GPLv3; see NOTICE. */
package cn.academy.port.api;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
/** Synchronous server-side mutable costs. This event is deliberately noncancelable. */
public final class SkillPerformEvent extends Event {
    public final ServerPlayer player;
    /** Additional diagnostic metadata; empty for raw CPData-style perform. */
    public final String skill;
    public final boolean force,creative;
    public float cp,overload;
    /** Classic constructor order is overload, CP. */
    public SkillPerformEvent(ServerPlayer player,String skill,float overload,float cp,boolean force,boolean creative){
        this.player=player;this.skill=skill;this.overload=overload;this.cp=cp;this.force=force;this.creative=creative;
    }
}
