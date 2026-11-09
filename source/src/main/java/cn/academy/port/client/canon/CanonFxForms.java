package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfx;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Entry point of the FX4 presentation upgrade (docs/VFX_UPGRADE_PLAN_20261029.md). Systems listed in {@link #UPGRADED} are drawn by
 * their own FX4 script and no longer get the shared CanonFxTier ladder (ground sigil, light pillar, halo), which made every system
 * look alike. One-shot work (particles, decals, caster camera) is started here once per server-confirmed event, at the right tick.
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonFxForms {
    static final Set<String> UPGRADED=Set.of("hydro_hand","dark_matter","liquid_shadow","paper_psychokinesis","mental_out","nitrogen_armor","aero_hand","float_dial");
    private record Later(Runnable work,int[] left) {}
    private static final List<Later> QUEUE=new ArrayList<>();
    private CanonFxForms() {}

    static boolean upgraded(String category){return UPGRADED.contains(category);}

    /** Runs {@code work} after {@code ticks} client ticks (0 = now). Cleared when the world changes. */
    static void later(int ticks,Runnable work){if(ticks<=0)work.run();else if(QUEUE.size()<256)QUEUE.add(new Later(work,new int[]{ticks}));}
    static void clear(){QUEUE.clear();}

    /** Called once per received visual. {@code local} is true only for the client player's own cast. */
    static void onReceive(CanonVfx.Visual v,String category,int tier,boolean local,double life){
        if(cn.academy.port.canon.CanonOffenseSkills.find(v.skill())!=null){CanonFxOffense.onReceive(v,tier,local);return;}
        if(!upgraded(category))return;
        switch(category){
            case "hydro_hand" -> CanonFxHydro.onReceive(v,tier,local,life);
            case "dark_matter" -> CanonFxDark.onReceive(v,tier,local,life);
            case "liquid_shadow" -> CanonFxLiquid.onReceive(v,tier,local,life);
            case "paper_psychokinesis" -> CanonFxPaper.onReceive(v,tier,local,life);
            case "mental_out" -> CanonFxMental.onReceive(v,tier,local,life);
            case "nitrogen_armor" -> CanonFxNitrogen.onReceive(v,tier,local,life);
            case "aero_hand" -> CanonFxAero.onReceive(v,tier,local,life);
            case "float_dial" -> CanonFxFloat.onReceive(v,tier,local,life);
            default -> { }
        }
    }
    static void draw(CanonFx.Canvas c,CanonFx.View v){
        switch(v.category()){
            case "hydro_hand" -> CanonFxHydro.draw(c,v);
            case "dark_matter" -> CanonFxDark.draw(c,v);
            case "liquid_shadow" -> CanonFxLiquid.draw(c,v);
            case "paper_psychokinesis" -> CanonFxPaper.draw(c,v);
            case "mental_out" -> CanonFxMental.draw(c,v);
            case "nitrogen_armor" -> CanonFxNitrogen.draw(c,v);
            case "aero_hand" -> CanonFxAero.draw(c,v);
            case "float_dial" -> CanonFxFloat.draw(c,v);
            default -> { }
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null){QUEUE.clear();return;}
        if(mc.isPaused()||QUEUE.isEmpty())return;
        for(Later l:List.copyOf(QUEUE))if(--l.left()[0]<=0){QUEUE.remove(l);l.work().run();}
    }
}
