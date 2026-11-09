/* AcademyCraft1.0.7 MineRayExpert adaptation, Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import net.minecraft.server.level.ServerPlayer;
/** Distinct source skill configuration, executed by the canonical shared MRContext adapter. */
public final class MineRayExpert {
    public static final String ID=AdvancedMineRaySession.Tier.EXPERT.id;
    private static final AdvancedMineRaySession.Tier TYPE=AdvancedMineRaySession.Tier.EXPERT;
    private MineRayExpert(){}
    public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}
    public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;return AdvancedMineRay.start(p,TYPE,input);}
    public static void tick(ServerPlayer p){AdvancedMineRay.tick(p,TYPE);}
    public static boolean release(ServerPlayer p){return AdvancedMineRay.release(p,TYPE);}
    public static void abort(ServerPlayer p){release(p);}
    public static void remove(ServerPlayer p){AdvancedMineRay.remove(p,TYPE);}
    public static boolean active(ServerPlayer p){return AdvancedMineRay.active(p,TYPE);}
    public static int heldTicks(ServerPlayer p){return AdvancedMineRay.heldTicks(p,TYPE);}
    public static AdvancedMineRaySession.Cell target(ServerPlayer p){return AdvancedMineRay.target(p,TYPE);}
    public static void clear(){AdvancedMineRay.clear(TYPE);}
}
