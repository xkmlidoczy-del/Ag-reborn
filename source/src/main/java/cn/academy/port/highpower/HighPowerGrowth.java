package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;

/** Cached actual-progress numerics; integer levels still own skill unlocks and classic XP rules. */
public final class HighPowerGrowth {
    private static final ContinuousHighPowerGrowth.Curve CP_MAX=curve("200","600","2000","10000","100000");
    private static final ContinuousHighPowerGrowth.Curve CP_REGEN=curve("10","50","250","2500","25000");
    private static final ContinuousHighPowerGrowth.Curve ARC_COST=curve("10","5","2","0.5","0.1");
    private static final ContinuousHighPowerGrowth.Curve ARC_CD=curve("10","5","3","2","1");
    private static final ContinuousHighPowerGrowth.Curve TETHER_COST=curve("4","2","1","0.5","0.1");
    private static final ContinuousHighPowerGrowth.Curve LV2_COST=curve("5","5","2","0.5","0.1");
    private static final ContinuousHighPowerGrowth.Curve GROUND_CD=curve("60","60","20","5","1");
    private static final Map<ServerPlayer,Values> CACHE=new WeakHashMap<>();
    public record Values(BigDecimal position,BigDecimal earnedPosition,BigDecimal damage,
                         double cpMaximum,double cpRecovery,double arcCost,int arcCooldown,
                         double tetherCost,double lv2Cost,int groundingCooldown) {}
    private HighPowerGrowth() {}
    private static ContinuousHighPowerGrowth.Curve curve(String... values){return ContinuousHighPowerGrowth.curve(values);}
    /** Called on the owning server thread only; arithmetic recomputes only when the real coordinate changes. */
    public static Values values(ServerPlayer player){
        if(player==null||!player.server.isSameThread())throw new IllegalStateException("Growth requires owning server thread");
        var state=AbilityStorage.get(player);
        if(!HighPowerCategories.active(state))
            throw new IllegalStateException("Growth requires an admitted high-power category");
        BigDecimal earned=HighPowerCombat.earnedGrowthPosition(player);
        if(earned==null||earned.compareTo(BigDecimal.ONE)<0||earned.compareTo(BigDecimal.valueOf(5))>0)throw new IllegalStateException("Missing earned growth contract");
        BigDecimal current=HighPowerActiveGrowth.position(player);
        Values old=CACHE.get(player);
        if(old!=null&&old.position.compareTo(current)==0&&old.earnedPosition.compareTo(earned)==0)return old;
        Values next=new Values(current,earned,categoryDamage(state.category,ContinuousHighPowerGrowth.sample(current).damage()),
                CP_MAX.value(earned).doubleValue(),CP_REGEN.value(current).doubleValue(),ARC_COST.value(current).doubleValue(),
                ContinuousHighPowerGrowth.ceilCooldownTicks(ARC_CD.value(current)),TETHER_COST.value(current).doubleValue(),
                LV2_COST.value(current).doubleValue(),ContinuousHighPowerGrowth.ceilCooldownTicks(GROUND_CD.value(current)));
        CACHE.put(player,next);return next;
    }
    public static BigDecimal categoryDamage(String category,BigDecimal unit){return cn.academy.port.highpower.core.MobilityScale.categoryDamage(category,unit);}
    public static double cpMaximumAt(BigDecimal position){return CP_MAX.value(position).doubleValue();}
    /** The same numerical curves for real non-player ability owners. */
    public static double cpRecoveryAt(BigDecimal position){return CP_REGEN.value(position).doubleValue();}
    public static double arcCostAt(BigDecimal position){return ARC_COST.value(position).doubleValue();}
    public static void invalidate(ServerPlayer player){CACHE.remove(player);}
    public static void beforeMutation(ServerPlayer player){HighPowerActiveGrowth.beforeMutation(player);}
    public static boolean available(ServerPlayer player){
        try{return player!=null&&HighPowerCategories.active(AbilityStorage.get(player))&&HighPowerActiveGrowth.position(player).compareTo(BigDecimal.ONE)>=0;}
        catch(IllegalArgumentException|IllegalStateException invalid){return false;}
    }
}
