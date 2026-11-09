package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.rnd;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded FX4 release feedback for the original four systems. MotifDirector, Em/Md/Tp/VmMotifs and all OWN renderers keep their
 * choreography. These positions are the frozen visual aim, not a server hit receipt or an authoritative teleport destination.
 * OWN casts receive only optional release camera feedback and terrain traces; they never acquire this class's extra geometry.
 */
public final class CanonFxClassic {
    private static final int MAX_RELEASES=512;
    private static final Set<String> CATEGORIES=Set.of("electromaster","meltdowner","teleporter","vecmanip");
    private static final Map<Long,Long> RELEASED=new HashMap<>();
    private static Object world;
    private static long cleanedAt=Long.MIN_VALUE;

    /** Mirrors SkillMotifs.OWN. Keep these 17 independent renderers untouched by the additional FX4 geometry below. */
    private static final Set<String> OWN=Set.of("railgun","thunder_clap","rail_thunder_array","scatter_bomb","thermal_shell","disintegration_sweep",
            "multicore_canopy","shear_windblade","storm_wing","trajectory_return","vector_cage","multi_axis_reflection","wind_pressure_lift",
            "zenith_vector_dominion","plasma_cannon","vector_construct","multidimensional_landing_array");

    /** Explicit release roles: sensing, shields and movement must not inherit a fictitious attack at a distant aim point. */
    private static final Set<String> STRIKES=Set.of("arc_gen","forked_arc","conductive_circuit","iron_sand_lance","thunder_bolt","thunder_net",
            "dual_point","electron_bomb","meltdowner","ray_barrage","electron_missile",
            "threatening_teleport","flesh_ripping","deflection_strike","dir_shock","ground_shock","yaw_palm",
            "dir_blast","blood_retro");
    private static final Set<String> QUIET=Set.of("mine_detect","charging","body_intensify","mag_manip","magnetic_item_pull",
            "mine_ray_basic","mine_ray_expert","mine_ray_luck","point_meltdown","beam_etching","rad_intensify","scatter_locator_plate","dim_folding_theorem","space_fluct",
            "circulation_listening","circulation_relief","braking_touch","return_mark","recovery_mark","safe_landing_probe");
    private static final Set<String> MOVEMENT=Set.of("grounding_return","mag_movement","magnetic_ground_step","rail_relay","beam_step","jet_engine",
            "reverse_jet_brake","remote_scatter_relay","displaced_projection","phase_sidestep","cross_substitution","mark_teleport","penetrate_teleport",
            "corner_exchange","incident_bypass","location_teleport","emergency_return","shift_tp","spatial_pincer_corridor","spatial_relay",
            "flashing","phase_corridor","vec_accel");
    private static final Set<String> FIELDS=Set.of("magnetic_domain","folded_mirror_array","tactical_coordinate_formation");
    private static final Set<String> OWN_STRIKES=Set.of("railgun","thunder_clap","rail_thunder_array","scatter_bomb","disintegration_sweep",
            "shear_windblade","plasma_cannon","zenith_vector_dominion");

    private static final float[] EM={.37f,.83f,1f},EM_W={.9f,.97f,1f},IRON={.38f,.45f,.52f},MD={.43f,1f,.59f},MD_W={.85f,1f,.88f},
            TP={.65f,.43f,1f},TP_W={.9f,.85f,1f},VM={.7f,.84f,1f},VM_W={.94f,.98f,1f},STONE={.6f,.65f,.7f},WHITE={1f,1f,1f};
    private CanonFxClassic() {}

    /** Called with the shared FX4 renderer's unload/reload cleanup. */
    public static void clear(){RELEASED.clear();world=null;cleanedAt=Long.MIN_VALUE;}

    /** Called for real cast snapshots only; repeated render frames do not create another release burst. */
    public static void observe(long key,String category,String skill,int level,Vec3 hand,Vec3 end,Vec3 ground,boolean released,boolean local){
        var mc=Minecraft.getInstance();var current=mc.level;
        if(current!=world){clear();world=current;}
        if(!released){RELEASED.remove(key);return;}
        if(current==null||skill==null||category==null||!finite(hand)||!finite(end)||!finite(ground))return;
        if(!CATEGORIES.contains(category))return;
        long now=current.getGameTime();
        if(cleanedAt!=now){RELEASED.values().removeIf(last->now<last||now-last>200);cleanedAt=now;}
        if(RELEASED.putIfAbsent(key,now)!=null){RELEASED.put(key,now);return;}
        if(RELEASED.size()>MAX_RELEASES){RELEASED.remove(key);return;} // suppress overflow; never clear live keys and replay old casts
        if(!local&&hand.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>96*96)return;
        int tier=Math.max(1,Math.min(5,level));
        if(OWN.contains(skill)){ownFeedback(category,skill,tier,end,ground,local);return;}
        boolean strike=STRIKES.contains(skill),move=MOVEMENT.contains(skill),field=FIELDS.contains(skill),active=!QUIET.contains(skill);
        switch(category){
            case "electromaster" -> electro(tier,hand,end,ground,strike,move,field,local);
            case "meltdowner" -> melt(tier,hand,end,ground,strike,move,field,active,local);
            case "teleporter" -> teleport(tier,hand,end,ground,strike,move,field,local);
            case "vecmanip" -> vector(tier,hand,end,ground,strike,move,active,local);
            default -> { }
        }
    }

    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean nearSurface(Vec3 end,Vec3 ground){return end.distanceToSqr(ground)<2.5*2.5;}
    private static Vec3 foot(Vec3 hand){return hand.add(0,-1.2,0);}

    /** Queue only cosmetic work in the same world; a delayed burst cannot leak into a newly joined world. */
    private static void later(int ticks,Runnable work){
        var level=Minecraft.getInstance().level;
        CanonFxForms.later(ticks,()->{if(level!=null&&Minecraft.getInstance().level==level)work.run();});
    }
    private static void cameraLater(int ticks,Runnable work){
        var caster=Minecraft.getInstance().player;
        later(ticks,()->{if(caster!=null&&Minecraft.getInstance().player==caster)work.run();});
    }

    /** Release feedback, not a claim that the server accepted a hit at the visual endpoint. */
    private static void releaseCamera(int tier,boolean strike,boolean local){
        if(!local||!strike)return;
        CanonCamera.shake(.14+.105*tier,4+tier);CanonCamera.freeze(16+8*tier);
    }

    /** OWN only: preserve their existing particles, light, refraction, overlays, sound and choreography. */
    private static void ownFeedback(String category,String skill,int tier,Vec3 end,Vec3 ground,boolean local){
        if(!OWN_STRIKES.contains(skill))return;
        releaseCamera(tier,true,local);
        if(!nearSurface(end,ground))return;
        String trace=switch(category){case "electromaster"->"em_scorch_decal";case "meltdowner"->"md_molten_decal";
            case "vecmanip"->"vm_crack_decal";default->null;};
        if(trace!=null)CanonDecals.add(trace,ground,.65+.18*tier,WHITE,.68f,100+10*tier);
    }

    private static void burst(String tex,Vec3 at,int n,double speed,double gravity,double size,int life,float[] rgb,CanonFx4.Mode mode,CanonParticles.Land land){
        for(int k=0;k<n;k++){
            double ang=rnd(k*3+(int)(at.x*7))*Math.PI*2,el=.16+rnd(k+11)*.75,sp=speed*(.5+rnd(k+5));
            CanonParticles.spawn(tex,k,at,new Vec3(Math.cos(ang)*Math.cos(el)*sp,Math.sin(el)*sp+.025,Math.sin(ang)*Math.cos(el)*sp),gravity,.045,
                    size*(.65+.5*rnd(k+2)),.22,life,rgb,.86f,mode,land,true,0);
        }
    }
    /** Short finite paths of data/energy motes; no circle or category-wide signature is added. */
    private static void stream(String tex,Vec3 from,Vec3 to,int n,float[] rgb,double size,int life){
        Vec3 d=to.subtract(from);double len=d.length();if(len<.05)return;
        Vec3 step=d.scale(1/len).scale(.06);
        for(int k=0;k<n;k++){
            double f=(k+1.0)/(n+1);Vec3 p=from.add(d.scale(f));
            CanonParticles.spawn(tex,k,p,step,0,.02,size,.02,life,rgb,.7f,CanonFx4.Mode.ADD,CanonParticles.Land.NONE,true,k%3);
        }
    }
    private static void converge(String tex,Vec3 at,int n,float[] rgb,double radius){
        for(int k=0;k<n;k++){
            double a=k*2.399963,up=(rnd(k+17)-.5)*radius;
            Vec3 offset=new Vec3(Math.cos(a)*radius,up,Math.sin(a)*radius);
            CanonParticles.spawn(tex,k,at.add(offset),offset.scale(-.11),0,.018,.055,.05,12,rgb,.8f,CanonFx4.Mode.ADD,CanonParticles.Land.NONE,true,k%3);
        }
    }

    /** EM: fine sparks -> travelling charge -> scorch/light -> physical iron sand -> local blue-white strobe. */
    private static void electro(int tier,Vec3 hand,Vec3 end,Vec3 ground,boolean strike,boolean move,boolean field,boolean local){
        Vec3 at=strike?end:hand,base=strike?ground:foot(hand);
        burst("em_spark_sheet",at,4+tier,.065,.018,.08,14,EM_W,CanonFx4.Mode.ADD,CanonParticles.Land.STOP);
        if(tier>=2&&strike)stream("em_arc_sheet",hand,end,4,EM,.055,10);
        if(tier>=3){
            CanonDecals.light("common_ground_mask",base,.7+.13*tier,EM,.38f,12);
            if(strike&&nearSurface(end,ground))CanonDecals.add("em_scorch_decal",ground,.6+.12*tier,WHITE,.72f,125);
            if(strike||field)for(int k=0;k<3;k++){
                final int branch=k;later(2*k,()->burst("em_arc_sheet",base.add((rnd(branch+2)-.5)*1.3,.04,(rnd(branch+8)-.5)*1.3),2,.055,0,.11,8,EM,CanonFx4.Mode.ADD,CanonParticles.Land.NONE));
            }
        }
        if(tier>=4&&(strike||field)){
            burst("em_ironsand",base.add(0,.12,0),10,.09,.035,.055,32,IRON,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP);
            later(3,()->burst("em_spark_sheet",at,6,.085,.02,.085,12,EM_W,CanonFx4.Mode.ADD,CanonParticles.Land.STOP));
        }
        releaseCamera(tier,strike,local);
        if(local&&tier>=5&&(strike||field||move)){
            CanonCamera.darken(.25,15);CanonCamera.tint(EM_W,.23,5);
            cameraLater(3,()->CanonCamera.tint(EM,.2,9));
            if(strike||field)cameraLater(7,()->CanonCamera.tint(EM_W,.12,4));
        }
    }

    /** MD: high-energy specks -> inward charge -> beam-path heat -> molten trace/embers -> green-white release exposure. */
    private static void melt(int tier,Vec3 hand,Vec3 end,Vec3 ground,boolean strike,boolean move,boolean field,boolean active,boolean local){
        burst("md_ember_sheet",hand,3,.025,-.001,.045,14,MD,CanonFx4.Mode.ADD,CanonParticles.Land.NONE);
        if(tier>=2)converge("md_charge_sheet",hand,6,MD_W,.42);
        if(tier>=3&&strike){
            Vec3 d=end.subtract(hand);int n=Math.max(2,(int)Math.min(5,d.length()/3));
            for(int k=1;k<=n;k++)CanonWarps.add(hand.add(d.scale(k/(double)(n+1))),.35+.055*tier,.42f,false,MD_W,12,k);
        }
        if(tier>=3&&active&&!strike)CanonWarps.add(hand,.38,.22f,false,MD_W,10,0);
        if(tier>=4&&strike&&nearSurface(end,ground)){
            CanonDecals.add("md_molten_decal",ground,.7+.14*tier,WHITE,.8f,130);
            CanonDecals.light("common_ground_mask",ground,.85+.16*tier,MD,.36f,18);
            for(int k=0;k<9;k++)CanonParticles.spawn("md_ember_sheet",k,ground.add((rnd(k)-.5)*.8,.1,(rnd(k+3)-.5)*.8),
                    new Vec3((rnd(k+5)-.5)*.025,.035+.025*rnd(k+7),(rnd(k+9)-.5)*.025),-.0015,.02,.045+.02*rnd(k),0,
                    26+3*tier,MD,.82f,CanonFx4.Mode.ADD,CanonParticles.Land.NONE,false,k);
        }
        releaseCamera(tier,strike,local);
        if(local&&tier>=5&&active){
            CanonCamera.darken(.22,13);CanonCamera.tint(MD_W,.28,6);cameraLater(4,()->CanonCamera.tint(MD,.15,10));
        }
    }

    /** TP: coordinate specks -> short data trails -> dissolving fold traces -> spatial refraction -> chromatic tear. */
    private static void teleport(int tier,Vec3 hand,Vec3 end,Vec3 ground,boolean strike,boolean move,boolean field,boolean local){
        Vec3 marker=(strike||field)?end:hand;
        burst("tp_lattice",hand,3,.025,0,.065,10,TP_W,CanonFx4.Mode.ADD,CanonParticles.Land.NONE);
        if(tier>=2&&(strike||move||field))stream("tp_lattice",hand,hand.add(end.subtract(hand).normalize().scale(1.6)),5,TP,.07,12);
        if(tier>=3&&(strike||move||field)){
            // CanonDecals dissolves its trace. These are visual coordinate marks, not asserted departure/arrival receipts.
            CanonDecals.add("tp_fold_decal",foot(hand),.48,WHITE,.58f,38);
            if(strike&&nearSurface(end,ground))CanonDecals.add("tp_fold_decal",ground,.55+.1*tier,WHITE,.66f,45);
            later(2,()->burst("tp_rift_sheet",marker,4,.035,0,.1,14,TP_W,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE));
        }
        if(tier>=4&&(strike||move||field)){
            CanonWarps.add(hand,.48,.48f,false,TP_W,10,0);
            CanonWarps.add(marker,.6+.1*tier,.6f,false,TP_W,12,2);
        }
        if(tier>=5&&(strike||move||field))CanonWarps.add(marker,.75,.48f,true,TP_W,12,1);
        releaseCamera(tier,strike,local);
        if(local&&move)CanonCamera.fovKick(.025,7);
        if(local&&tier>=5&&(strike||move||field)){
            CanonCamera.tint(TP_W,.16,7);CanonCamera.darken(.14,10);
        }
    }

    /** VM: directional dust -> heavier motes -> pressure refraction -> fracture/debris -> caster-only pressure vignette/FOV. */
    private static void vector(int tier,Vec3 hand,Vec3 end,Vec3 ground,boolean strike,boolean move,boolean active,boolean local){
        Vec3 at=strike?end:hand;
        if(strike)burst("vm_debris_atlas",ground.add(0,.08,0),2+tier,.055,.04,.04,20,STONE,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP);
        if(tier>=2&&move)stream("vm_shear_streak",hand,hand.add(0,-.8,0),4,VM,.055,12);
        if(tier>=3&&active)CanonWarps.add(at,.6+.16*tier,.55f,false,VM_W,10,0);
        if(tier>=4&&strike&&nearSurface(end,ground)){
            CanonDecals.add("vm_crack_decal",ground,.65+.18*tier,WHITE,.8f,145);
            burst("vm_debris_atlas",ground.add(0,.12,0),13,.13,.042,.075,40,STONE,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP);
        }
        if(tier>=4&&active&&!strike)burst("vm_debris_atlas",foot(hand).add(0,.08,0),3,.035,.025,.035,18,STONE,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP);
        releaseCamera(tier,strike,local);
        if(local&&tier>=5&&active){
            CanonCamera.fovKick(-.035,11);CanonCamera.darken(.26,16);CanonCamera.edge("speed",.12,9);
            if(strike)later(2,()->burst("vm_debris_atlas",ground.add(0,.1,0),7,.08,.035,.045,26,STONE,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP));
        }
    }
    static boolean local(int entityId){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.getId()==entityId;}
}
