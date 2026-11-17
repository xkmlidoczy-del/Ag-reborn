package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.macro.BeamImpactVfx;
import cn.academy.port.macro.MacroVfx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Film layers that share the ordinary renderer's depth test and vertex budget. */
final class SkillCinematicFx {
    private static final Vec3 UP=new Vec3(0,1,0), EAST=new Vec3(1,0,0), NORTH=new Vec3(0,0,1);
    private static final float[] STEEL={.064f,.084f,.14f}, WHITE={.96f,.98f,1};
    private static final float[] VIOLET={.61f,.22f,.97f}, ION={.36f,.74f,1};
    private static final float[] CHARGE={.40f,.65f,1}, RAIN={.24f,.59f,.97f};
    private static final float[] SHIELD={.26f,.87f,1}, HEALTH={1,.58f,.30f}, CONTROL={.96f,.55f,.93f};
    private static final float[] PAPER={1,.94f,.72f}, GREEN={.63f,1,.39f};
    private static final Set<String> NATIVE_ROUTES = Set.of("rail_thunder_array", "rail_relay",
            "remote_scatter_relay", "folded_mirror_array", "multicore_canopy");
    private static ClientLevel world;
    private record Effect(BeamImpactVfx.Scene scene,double deadline){}
    private static final class PaperPlacement {
        final BeamImpactVfx.Scene scene;
        final Block block;
        BlockState seen;
        double surface;

        PaperPlacement(BeamImpactVfx.Scene scene, Block block) {
            this.scene = scene;
            this.block = block;
        }
    }
    private record PaperDissolve(BeamImpactVfx.Scene scene,Vec3 point,long removalTick){}
    private static final List<Effect> RESULTS=new ArrayList<>();
    private static final Map<BlockPos,PaperPlacement> PAPER_PLACEMENTS=new LinkedHashMap<>();
    private static final List<PaperDissolve> PAPER_DISSOLVES=new ArrayList<>();
    private SkillCinematicFx(){}

    private static void sync(){
        var level=Minecraft.getInstance().level;
        if(world!=level){clear();world=level;}
    }
    static void accept(BeamImpactVfx.Scene scene){
        sync();
        if(world==null||VisualTierConfig.tier()==VisualTier.OFF)return;
        if(scene.style()!=BeamImpactVfx.Style.BEAM&&scene.style()!=BeamImpactVfx.Style.CONTACT&&scene.style()!=BeamImpactVfx.Style.PAPER)return;
        if(scene.tick()+scene.life()<=world.getGameTime())return;
        if(RESULTS.size()>=40)RESULTS.removeFirst();
        RESULTS.add(new Effect(scene,scene.tick()+scene.life()));
        if(scene.style()==BeamImpactVfx.Style.PAPER){
            for(var cell:scene.cells()){
                if(PAPER_PLACEMENTS.size()>=256)PAPER_PLACEMENTS.remove(PAPER_PLACEMENTS.keySet().iterator().next());
                PAPER_PLACEMENTS.put(cell.position(),new PaperPlacement(scene,Block.stateById(cell.originalState()).getBlock()));
            }
        }
    }
    static boolean any(){
        sync();
        return world!=null&&VisualTierConfig.tier()!=VisualTier.OFF&&(!RESULTS.isEmpty()||!PAPER_DISSOLVES.isEmpty());
    }
    static void clear(){RESULTS.clear();PAPER_PLACEMENTS.clear();PAPER_DISSOLVES.clear();world=null;}

    /** A confirmed placement owns its construction sheets in the upgraded tiers. */
    static boolean drawsPaperBuild(CanonFx.View view) {
        if (VisualTierConfig.tier() == VisualTier.OFF || !"paper_psychokinesis".equals(view.category())) return false;
        return RESULTS.stream().anyMatch(effect -> {
            var scene = effect.scene;
            return scene.style() == BeamImpactVfx.Style.PAPER && scene.owner().equals(view.owner())
                    && scene.skill().equals(view.skill()) && scene.from().distanceToSqr(view.origin()) < .04;
        });
    }

    /** Observe the replicated collision blocks after placement. Unloading a chunk creates no dissolve. */
    static void tick() {
        sync();
        if (world == null || VisualTierConfig.tier() == VisualTier.OFF) {
            PAPER_PLACEMENTS.clear();
            PAPER_DISSOLVES.clear();
            return;
        }
        long now = world.getGameTime();
        PAPER_DISSOLVES.removeIf(effect -> now - effect.removalTick >= 16);
        var pending = PAPER_PLACEMENTS.entrySet().iterator();
        while (pending.hasNext()) {
            var entry = pending.next();
            var pos = entry.getKey();
            var placement = entry.getValue();
            if (!world.hasChunkAt(pos)) {
                if (placement.seen != null || now - placement.scene.tick() > 10) pending.remove();
                continue;
            }
            var state = world.getBlockState(pos);
            if (state.getBlock() == placement.block) {
                if (state != placement.seen) {
                    placement.seen = state;
                    placement.surface = paperSurface(pos, state);
                }
                continue;
            }
            if (placement.seen != null) {
                if (PAPER_DISSOLVES.size() >= 128) PAPER_DISSOLVES.removeFirst();
                var point = new Vec3(pos.getX() + .5, placement.surface, pos.getZ() + .5);
                PAPER_DISSOLVES.add(new PaperDissolve(placement.scene, point, now));
                pending.remove();
            } else if (now - placement.scene.tick() > 10) pending.remove();
        }
    }

    static void results(CanonFx.Canvas c,double now){
        sync();
        if(world==null)return;
        RESULTS.removeIf(s->now>=s.deadline);
        if(VisualTierConfig.tier()==VisualTier.OFF)return;
        int limit=c.limit(300);
        try{
            for(var effect:RESULTS){
                var v=effect.scene;
                double age=Math.max(0,now-v.tick());
                int before=c.left;
                if(v.style()==BeamImpactVfx.Style.CONTACT){
                    for(var contact:v.contacts()){
                        if(c.left<=30)break;
                        if(!MacroVfx.finite(contact.point())||!MacroVfx.finite(contact.normal())||c.cam.distanceToSqr(contact.point())>128*128)continue;
                        contact(c,contact,age);
                    }
                }else if(v.style()==BeamImpactVfx.Style.PAPER){
                    paper(c,v,age);
                }else if(v.style()==BeamImpactVfx.Style.BEAM&&v.skill().equals("meltdowner")&&AgEffectMaterials.beams()){
                    beamTips(c,v,age);
                }
                if(CanonFx.QA_BUDGET)c.record("result:"+v.skill()+":"+v.cast(),before);
            }
            for(var dissolve:PAPER_DISSOLVES){
                int before=c.left;
                dissolvePaper(c,dissolve,now-dissolve.removalTick);
                if(CanonFx.QA_BUDGET)c.record("paper_dissolve:"+dissolve.scene.skill()+":"+dissolve.scene.cast(),before);
            }
        }finally{c.restore(limit);}
    }

    static void boundary(CanonFx.Canvas c,MacroVfx.Event v,Vec3 centre,float[] palette,double age,double opacity){
        if(v.boundary()==MacroVfx.Boundary.NONE||opacity<=0)return;
        if(v.boundary()==MacroVfx.Boundary.CURTAIN){
            curtainBoundary(c,centre,v.skill(),palette,opacity);
            return;
        }
        if(CanonExpansionCombatA.physicalBarrier(v.skill()))return;
        boolean search=v.boundary()==MacroVfx.Boundary.SEARCH;
        double r=v.actualRadius();
        double alpha=opacity*(search?.54:.82);
        AgModernVfx.terrainShockCrown(c,centre,r,palette,age,alpha,search?28:40,!search&&v.phase()==MacroVfx.Phase.HIT);
        if(r<6||VisualTierConfig.tier()==VisualTier.LOW)return;
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2;
            Vec3 at=centre.add(radial(a,r,0));
            double gy=AgModernVfx.groundY(at.x,centre.y,at.z);
            line(c,new Vec3(at.x,gy,at.z),new Vec3(at.x,gy+1.35,at.z),.19,palette,alpha*.78);
        }
        double turn=age*.028;
        for(int i=0;i<2;i++){
            Vec3 across=radial(turn+i*Math.PI/2,1,0);
            arc(c,centre,across,UP,r,Math.PI*.34,Math.PI*.66,6,.16,palette,alpha*.58,true);
        }
    }

    static void curtainBoundary(CanonFx.Canvas c,Vec3 centre,String skill,float[] palette,double opacity){
        box(c,CanonExpansionCombatA.barrierBox(centre,skill),palette,opacity*.44,true);
        box(c,CanonExpansionCombatA.barrierInnerBox(centre,skill),SHIELD,opacity*.86,false);
    }

    static boolean centerpiece(CanonFx.Canvas c,MacroVfx.Event v,Vec3 centre,double age,double fade,int detail){
        if (usesNativeRoute(v.skill())) {
            routeNode(c, v, age, fade);
            return true;
        }
        return switch(v.skill()){
            case "plasma_cannon" -> {plasma(c,v,centre,age,fade,detail);yield true;}
            case "thunder_clap" -> {thunder(c,v,centre,age,fade,detail);yield true;}
            case "electron_missile" -> {satellite(c,v,centre,age,fade);yield true;}
            case "mental_resonance_cascade" -> {resonance(c,v,centre,age,fade);yield true;}
            case "aero_air_curtain" -> true; // The two real boxes and confirmed arrow contacts own this view.
            case "tactical_coordinate_formation" -> {coordinates(c,v,age,fade);yield true;}
            case "paper_emergency_stairs","paper_catwalk","paper_switch_bridge" -> true; // Draw the committed cells, in order.
            default -> false;
        };
    }

    static boolean usesNativeRoute(String skill) {
        return NATIVE_ROUTES.contains(skill);
    }

    /** The native controllers already draw their actual segments; this adds a short marker at the received node. */
    private static void routeNode(CanonFx.Canvas c, MacroVfx.Event event, double age, double fade) {
        if (event.phase() == MacroVfx.Phase.END || age >= 15) return;
        boolean hit = event.phase() == MacroVfx.Phase.HIT;
        var direction = event.to().subtract(event.from());
        var point = direction.lengthSqr() > .04 || hit ? event.to() : event.from();
        Vec3 across = side(direction);
        Vec3 vertical = direction.lengthSqr() > .04 ? across.cross(direction).normalize() : UP;
        double spread = .35 + (hit ? .95 : .35) * ease(age / 15);
        double alpha = fade * (1 - ease(age / 15)) * (hit ? .75 : .44);
        float[] color = event.skill().startsWith("rail_") ? ION : GREEN;
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * (.28 + i * .5);
            arc(c, point, across, vertical, spread, angle, angle + Math.PI * .21, 3, hit ? .09 : .065, color, alpha, false);
        }
    }

    /** Compact near/middle-distance choreography. Existing native forms and exact paths remain the primary silhouettes. */
    static void accent(CanonFx.Canvas c,MacroVfx.Event v,Vec3 at,float[] tint,double age,double fade,int detail){
        if(v.phase()==MacroVfx.Phase.END||fade<=.01||at.distanceToSqr(c.cam)>112*112)return;
        var role=CinematicSkills.role(v.skill());
        var axis=v.to().subtract(v.from()).normalize();
        if(axis.lengthSqr()<.01)axis=NORTH;
        var edge=side(axis);
        double t=age*.14, e=fade*(detail>=24?.68:.52);
        int n=detail>=24?10:6;
        switch(role){
            case FEATURE -> { }
            case SCAN -> {
                double r=.9+2.4*ease(age/14);
                arc(c,at.add(0,.15,0),EAST,NORTH,r,t,t+Math.PI*.8,n,.081,tint,e,true);
            }
            case GUARD -> {
                var p=at.add(0,.85,0);
                arc(c,p,edge,UP,.93,Math.PI*.14,Math.PI*.87,n,.073,tint,e,false);
                arc(c,p,edge,UP,.98,Math.PI*1.12,Math.PI*1.87,n,.056,tint,e*.71,true);
            }
            case FORM -> {
                double open=ease(age/9);
                if(v.skill().startsWith("paper_")){
                    for(int i=0;i<3;i++){
                        var p=at.add(radial(t*.3+i*Math.TAU/3,1.35-open*.89,.9+open*.35));
                        c.fold("cinema/paper_fibre",p,radial(i*Math.TAU/3,1,0),UP,.22,.39,(1-open)*1.15+.16,PAPER,(float)e);
                    }
                }else{
                    for(int i=0;i<3;i++){
                        var p=at.add(radial(t*.3+i*Math.TAU/3,1.6-open*1.1,.52+i*.25));
                        line(c,p,p.add(0,.54,0),.08,tint,e);
                    }
                }
            }
            case TRANSIT -> {
                if(v.phase()==MacroVfx.Phase.HIT){
                    var p=at.add(0,.8,0);
                    for(int i=0;i<3;i++){
                        double f=(i-1)*.33;
                        var a=p.add(edge.scale(f*2)).add(0,-.72,0);
                        line(c,a,a.add(0,1.42-ease(age/11)*.62,0),.071,tint,e);
                    }
                }
            }
            case RAY -> {
                if(v.phase()==MacroVfx.Phase.HIT||v.phase()==MacroVfx.Phase.RELEASE){
                    var p=v.to();var up=axis.cross(edge).normalize();
                    arc(c,p,edge,up,.20+ease(age/9)*.63,t,t+Math.PI*1.1,n,.063,tint,e*(1-ease(age/20)),true);
                }
            }
            case LIFT -> {
                boolean press=v.skill().contains("collapse")||v.skill().contains("drop")||v.skill().contains("release");
                for(int i=0;i<3;i++){
                    double y=.34+i*.49+(press?-1:1)*.58*ease(age/16);
                    arc(c,at.add(0,y,0),EAST,NORTH,1.1+i*.23,t+i,t+i+2.1,n,.11,tint,e*(.69+i*.1),true);
                }
            }
            case DRIVE -> {
                var p=at.add(0,.55,0);
                for(int i=-1;i<=1;i++){
                    Vec3 a=p.add(edge.scale(i*.24));
                    c.ribbon("cinema/filament",true,List.of(a,a.subtract(axis.scale(.72+ease(age/16))),a.subtract(axis.scale(2.2)).add(0,-.21,0)),
                            u->.16*(1-u)+.04,u->e*(1-u),tint,2,-t);
                }
            }
            case FLOW -> {
                var p=at.add(0,.25,0);
                for(int i=0;i<2;i++){
                    var stream=new ArrayList<Vec3>();
                    for(int j=0;j<=n;j++){
                        double u=j/(double)n, a=t*.46+u*3.7+i*Math.PI;
                        stream.add(p.add(radial(a,1.7*(1-u)+.3,.24*Math.sin(u*Math.PI))));
                    }
                    c.ribbon("cinema/filament",true,stream,u->.28*u+.07,u->e*(.22+.60*u),tint,2,-t*.8);
                }
            }
            case FIELD, PULSE -> {
                if(age<17)c.ground("cinema/thunder_ripple",true,at.add(0,.10,0),
                        .74+1.64*ease(age/15),tint,(float)(e*(1-ease(age/17))*.65));
            }
            case LINK -> {
                c.neuralNode(at.add(0,.35,0),.31+.12*ease(age/10),tint,(float)(e*.76));
            }
        }
    }

    private static void plasma(CanonFx.Canvas c,MacroVfx.Event v,Vec3 p,double age,double e,int detail){
        if(v.phase()==MacroVfx.Phase.END){
            c.sheet("cinema/plasma_sheet",true,p,2.2+age*.09,age*.025,Math.min(7,(int)age/2),VIOLET,(float)(e*.55));
            return;
        }
        boolean hit=v.phase()==MacroVfx.Phase.HIT, charge=v.phase()==MacroVfx.Phase.CHARGE;
        double near=Math.clamp((p.distanceTo(c.cam)-1.5)/8,0,1);
        double t=age*.16;
        double r=hit?v.actualRadius()*AgModernVfx.snapEase(age/4.0):charge?2.2+3.8*ease(age/33):5.4;
        if(!hit){
            // Gyroscopic 3-axis vector magnetic confinement rings around the artificial sun.
            AgModernVfx.calculationArray(c,p,UP,r*1.08,"vecmanip",age,e*.68*near,24);
            AgModernVfx.calculationArray(c,p,new Vec3(Math.cos(t*.25),.35,Math.sin(t*.25)),r*.96,"vecmanip",-age*1.2,e*.54*near,20);
        }
        if(charge||v.phase()==MacroVfx.Phase.RELEASE&&age<6){
            var actor=world.getEntity(v.entity());
            if(actor instanceof net.minecraft.world.entity.LivingEntity caster&&caster.getUUID().equals(v.owner())){
                float partial=(float)Math.clamp(age-Math.floor(age),0,1);
                Vec3 center=caster.getEyePosition(partial).add(0,-.38,0).add(caster.getLookAngle().scale(.56));
                Vec3 lateral=side(caster.getLookAngle());
                for(int direction:new int[]{-1,1}){
                    Vec3 wrist=center.add(lateral.scale(.38*direction));
                    Vec3 sheath=p.add(lateral.scale(r*.7*direction));
                    var draw=CanonFx.wave(wrist,sheath,10,.28,2,t+direction,lateral);
                    c.ribbon("streak_ink",false,draw,u->.12+.22*u,u->e*.65,STEEL,2,0);
                    c.ribbon("cinema/filament",true,draw,u->.055+.12*u,u->e*(.62+.22*u),ION,3,-t);
                }
            }
        }
        if(!AgVolumeRenderer.owns(v.cast())||VisualTierConfig.tier()!=VisualTier.CINEMATIC)
            c.sheet("cinema/plasma_sheet",false,p,r*.88,-t*.11,(int)(age*.64)%8,WHITE,(float)(e*.8*near));
        c.sheet("cinema/plasma_sheet",true,p,r*.50,t*.30,(int)(age*.81+3)%8,ION,(float)(e*.73*near));
        int streams=detail>=32?6:4,samples=detail>=32?14:10;
        for(int k=0;k<streams;k++){
            var path=new ArrayList<Vec3>();
            double sign=k%2==0?1:-1, base=k*Math.TAU/streams+sign*t*.42;
            for(int j=0;j<=samples;j++){
                double u=j/(double)samples;
                double radius=r*(.98-.46*u)*(.91+.09*Math.sin(t+u*10+k));
                path.add(p.add(radial(base+sign*u*3.3,radius,r*(.52-.88*u)*Math.sin(base+u*3))));
            }
            c.ribbon("streak_ink",false,path,u->.41,u->e*.57*near,STEEL,2,0);
            c.ribbon("cinema/filament",true,path,u->.17+.19*Math.sin(Math.PI*u),u->e*(.42+.45*u)*near,k%2==0?ION:VIOLET,3,-t*.55);
        }
        if(hit){
            double blastR=v.actualRadius()*AgModernVfx.snapEase(age/4.0);
            double waveFade=e*(1-ease(age/28));
            AgModernVfx.fresnelDome(c,p,blastR,blastR,VIOLET,age,waveFade*.72,8,20);
            AgModernVfx.terrainShockCrown(c,p,blastR,ION,age,waveFade*.88,42,true);
            for(int i=0;i<6;i++){
                double a=i*Math.TAU/6+v.cast().hashCode()*.004;
                var ground=p.add(radial(a,blastR*.82,.24));
                AgModernVfx.fractalArc(c,p.add(0,.4,0),ground,3,v.cast().hashCode()+i*97L+(long)(age/2),1.0,.28,ION,waveFade*.74,false);
            }
        }
    }

    private static void thunder(CanonFx.Canvas c,MacroVfx.Event v,Vec3 at,double age,double fade,int detail){
        if(v.phase()==MacroVfx.Phase.END)return;
        boolean landed=v.phase()==MacroVfx.Phase.HIT;
        double near=Math.clamp((at.distanceTo(c.cam)-1.5)/7,0,1), energy=landed?1:ease((age+12)/62);
        Vec3 sky=at.add(0,landed?44:36,0);
        int arcs=detail>=32?5:3;
        long keyframe=(long)Math.floor(age/2.0);
        if(landed){
            double flash=(1-ease(age/16))*fade;
            AgModernVfx.calculationArray(c,sky,UP,Math.min(v.actualRadius()*.62,26.0),"electromaster",age,fade*.76,32);
            AgModernVfx.fractalArc(c,sky,at,4,v.cast().hashCode()^keyframe,1.35,.82+.48*flash,ION,fade*(.72+.28*flash),true);
            c.ribbon("cinema/thunder_channel",true,List.of(sky,at),u->1.8*(1.0-.25*u),u->fade*(.16+.58*flash)*near,ION,4,-age*.12);
            for(int k=0;k<arcs;k++){
                double a=k*Math.TAU/arcs+keyframe*.17;
                double rad=v.actualRadius()*(.32+.58*((k+1.0)/arcs));
                Vec3 tip=new Vec3(at.x+Math.cos(a)*rad,AgModernVfx.groundY(at.x+Math.cos(a)*rad,at.y,at.z+Math.sin(a)*rad)+.12,at.z+Math.sin(a)*rad);
                AgModernVfx.fractalArc(c,at.lerp(sky,.38+k*.08),tip,3,v.cast().hashCode()+k*631L+keyframe,1.1,.34,RAIN,fade*(.26+flash*.54),true);
            }
            double r=v.actualRadius()*AgModernVfx.snapEase(age/4.0);
            AgModernVfx.terrainShockCrown(c,at,r,ION,age,fade*(1-ease(age/24))*.90,44,true);
            AgModernVfx.calculationArray(c,at.add(0,.14,0),UP,r,"electromaster",age,flash*.74,36);
        }else{
            // Target telegraph ring + skyward gathering cumulonimbus calculation array & stepped leaders.
            double teleR=Math.max(2.5,v.actualRadius()*energy);
            AgModernVfx.terrainShockCrown(c,at,teleR,CHARGE,age,fade*.46,36,false);
            AgModernVfx.calculationArray(c,sky,UP,8+energy*14,"electromaster",age,fade*(.35+.45*energy),28);
            for(int i=0;i<arcs;i++){
                Vec3 cloud=sky.add(radial(i*Math.TAU/arcs-age*.026,10+energy*8,2+i*.8));
                c.sheet("cinema/plasma_sheet",false,cloud,5+energy*3.5,age*.025+i,i,RAIN,(float)(fade*.38));
                AgModernVfx.fractalArc(c,cloud,sky,3,v.cast().hashCode()+i*331L+keyframe,.85,.32,ION,fade*energy*.72,false);
            }
            if(energy>.55){
                Vec3 leaderEnd=sky.lerp(at,(energy-.55)/.45*.78);
                AgModernVfx.fractalArc(c,sky,leaderEnd,3,v.cast().hashCode()+991L+keyframe,.9,.24,CHARGE,fade*(energy-.5)*1.2,true);
            }
        }
    }

    private static void satellite(CanonFx.Canvas c,MacroVfx.Event v,Vec3 p,double age,double fade){
        if(v.phase()==MacroVfx.Phase.END)return;
        // The actual orbital objects and their shot origins are owned by the original electronic-ball renderer.
        arc(c,p.add(0,.17,0),EAST,NORTH,1.9,age*.11,age*.11+2.4,8,.085,GREEN,fade*.62,true);
        arc(c,p.add(0,1.2,0),EAST,UP,1.42,-age*.10,-age*.10+1.5,6,.062,GREEN,fade*.29,true);
    }

    private static void resonance(CanonFx.Canvas c,MacroVfx.Event v,Vec3 p,double age,double fade){
        if(v.phase()==MacroVfx.Phase.END)return;
        double r=v.phase()==MacroVfx.Phase.HIT?1+Math.min(4,age*.34):3.6-2.6*ease(age/16);
        c.ground("cinema/thunder_ripple",false,p.add(0,.12,0),r,CONTROL,(float)(fade*.47));
        for(int i=0;i<3;i++){
            Vec3 at=p.add(radial(age*.085+i*Math.TAU/3,r*.94,.15+i*.28));
            c.neuralNode(at,.36+i*.1,CONTROL,(float)(fade*.8));
        }
    }

    private static void coordinates(CanonFx.Canvas c,MacroVfx.Event v,double age,double fade){
        if(v.phase()!=MacroVfx.Phase.HIT||v.to().distanceToSqr(v.from())<.05)return;
        Vec3 axis=v.to().subtract(v.from()).normalize(), side=side(axis);
        for(int endpoint=0;endpoint<2;endpoint++){
            Vec3 at=(endpoint==0?v.from():v.to()).add(0,.95,0);
            double r=.85+.42*(1-ease(age/18));
            arc(c,at,side,UP,r,Math.PI*.12,Math.PI*1.98,12,.085,VIOLET,fade*.88,true);
            c.ground("cinema/thunder_ripple",true,at.add(0,-.89,0),r*1.4,ION,(float)(fade*.46));
            for(int sideSign:new int[]{-1,1}){
                Vec3 base=at.add(side.scale(r*sideSign));
                line(c,base.add(0,-.81,0),base.add(0,.82,0),.11,VIOLET,fade*.92);
            }
        }
    }

    private static void contact(CanonFx.Canvas c,BeamImpactVfx.Contact result,double age){
        if(age<0||age>16)return;
        double alpha=1-ease(age/16), r=.29+age*.032;
        Vec3 n=result.normal().lengthSqr()<.1?NORTH:result.normal().normalize();
        Vec3 right=side(n), up=n.cross(right).normalize(), p=result.point().add(n.scale(.06));
        switch(result.outcome()){
            case SHIELD, BOTH -> {
                arc(c,p,right,up,r,0,Math.TAU,6,.073,SHIELD,alpha*.94,false);
                arc(c,p,right,up,r*.66,0,Math.TAU,6,.048,SHIELD,alpha*.65,true);
                if(result.outcome()==BeamImpactVfx.Outcome.BOTH)
                    c.sprite("glow",true,p,r*.72,0,HEALTH,(float)(alpha*.8));
            }
            case HEALTH -> {
                c.sheet("cinema/thunder_impact",true,p,r*1.9,age*.06,Math.min(7,(int)age/2),HEALTH,(float)(alpha*.88));
                for(int i=0;i<4;i++){
                    double a=i*Math.PI/2+.28;
                    Vec3 d=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    line(c,p.add(d.scale(r*.30)),p.add(d.scale(r*1.51)),.055,HEALTH,alpha*.85);
                }
            }
            case CONTROL -> {
                c.neuralNode(p,.43+age*.016,CONTROL,(float)(alpha*.95));
                arc(c,p,right,up,r*1.68,0,Math.TAU,8,.055,CONTROL,alpha*.62,true);
            }
            case BLOCKED -> {
                line(c,p.add(right.scale(-r)),p.add(up.scale(r*.43)),.055,STEEL,alpha*.8);
                line(c,p.add(up.scale(r*.43)),p.add(right.scale(r)),.055,PAPER,alpha*.72);
            }
        }
    }

    private static void paper(CanonFx.Canvas c,BeamImpactVfx.Scene v,double age){
        Vec3 forward=paperAxis(v);
        for(int i=0;i<v.cells().size();i++){
            var cell=v.cells().get(i);
            if(!world.hasChunkAt(cell.position()))continue;
            double local=age-i*1.85;
            if(local<0||local>18)continue;
            var state=world.getBlockState(cell.position());
            if(state.getBlock()!=Block.stateById(cell.originalState()).getBlock())continue;
            Vec3 at=new Vec3(cell.position().getX()+.5,paperSurface(cell.position(),state),cell.position().getZ()+.5);
            if(c.cam.distanceToSqr(at)>128*128)continue;
            double open=ease(local/7), fade=(1-ease((local-9)/9));
            double fold=(1-open)*1.29;
            c.fold("cinema/paper_fibre",at.add(0,(1-open)*.62,0),side(forward),forward,
                    .34+.18*open,.29+.23*open,fold,PAPER,(float)(fade*.96));
            line(c,at.add(forward.scale(-.46)),at.add(forward.scale(.46)),.082,PAPER,fade*.64);
        }
    }

    private static double paperSurface(BlockPos pos,BlockState state){
        var shape=state.getShape(world,pos);
        return pos.getY()+(shape.isEmpty()?.5:shape.max(Direction.Axis.Y))+.035;
    }

    private static Vec3 paperAxis(BeamImpactVfx.Scene scene){
        Vec3 axis=scene.to().subtract(scene.from()).multiply(1,0,1).normalize();
        return axis.lengthSqr()<.01?NORTH:axis;
    }

    private static void dissolvePaper(CanonFx.Canvas c,PaperDissolve effect,double age){
        if(age>=16||c.cam.distanceToSqr(effect.point)>128*128||!world.hasChunkAt(BlockPos.containing(effect.point)))return;
        Vec3 axis=paperAxis(effect.scene), across=side(axis);
        double p=ease(age/16),alpha=(1-p)*.86;
        for(int i=-1;i<=1;i++){
            Vec3 point=effect.point.add(across.scale(i*(.24+p*.52))).add(axis.scale(.24*p)).add(0,.3*p-.75*p*p,0);
            c.fold("cinema/paper_fibre",point,across,axis,.26-.12*p,.29-.14*p,(1+i*.18)*p*1.5,PAPER,(float)alpha);
        }
    }

    private static void beamTips(CanonFx.Canvas c,BeamImpactVfx.Scene s,double age){
        if(age>18||s.from().distanceToSqr(s.to())<.02)return;
        Vec3 axis=s.to().subtract(s.from()).normalize(), right=side(axis), up=axis.cross(right).normalize();
        double e=(1-ease(age/18));
        double near=Math.clamp((s.from().distanceTo(c.cam)-1.0)/2.2,0,1);
        c.plane("cinema/md_head",true,s.from().add(axis.scale(.8)),right.scale(.76+age*.037),up.scale(.76+age*.037),GREEN,(float)(e*near*.72));
        c.plane("cinema/md_tail",true,s.to().subtract(axis.scale(.036)),right.scale(.72+age*.05),up.scale(.72+age*.05),GREEN,(float)(e*.75));
    }

    private static void box(CanonFx.Canvas c,AABB b,float[] tint,double e,boolean dashed){
        Vec3[] pts=new Vec3[8];
        for(int i=0;i<8;i++)pts[i]=new Vec3((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ);
        for(int i=0;i<8;i++)for(int bit=1;bit<=4;bit*=2)if((i&bit)==0){
            var a=pts[i];var end=pts[i|bit];
            if(dashed)for(int j=0;j<5;j++)line(c,a.lerp(end,j/5d),a.lerp(end,(j+.60)/5),.175,tint,e);
            else line(c,a,end,.088,tint,e);
        }
    }
    private static void arc(CanonFx.Canvas c,Vec3 p,Vec3 right,Vec3 up,double r,double start,double end,int n,double width,float[] rgb,double alpha,boolean dashed){
        var coords=new ArrayList<Vec3>(n+1);
        for(int i=0;i<=n;i++){double a=start+(end-start)*i/n;coords.add(p.add(right.scale(r*Math.cos(a))).add(up.scale(r*Math.sin(a))));}
        if(dashed){
            for(int i=0;i<n;i++)line(c,coords.get(i),coords.get(i).lerp(coords.get(i+1),.65),width,rgb,alpha);
        }else{
            c.ribbon("streak_ink",false,coords,u->width*1.32,u->alpha*.61,STEEL,1,0);
            c.ribbon("streak",true,coords,u->width*.72,u->alpha,rgb,1,0);
        }
    }
    private static List<Vec3> bolt(Vec3 start,Vec3 end,int segments,int seed,double width){
        Vec3 forward=end.subtract(start).normalize(), x=side(forward), y=forward.cross(x).normalize();
        var points=new ArrayList<Vec3>(segments+1);
        var random=new java.util.Random(seed);
        for(int i=0;i<=segments;i++){
            double f=i/(double)segments, s=width*Math.sin(Math.PI*f);
            points.add(start.lerp(end,f).add(x.scale((random.nextDouble()-.5)*2*s)).add(y.scale((random.nextDouble()-.5)*2*s)));
        }
        return points;
    }
    private static void line(CanonFx.Canvas c,Vec3 a,Vec3 b,double width,float[] colour,double alpha){
        var path=List.of(a,b);
        c.ribbon("streak_ink",false,path,u->width*1.4,u->alpha*.72,STEEL,1,0);
        c.ribbon("streak",true,path,u->width*.7,u->alpha,colour,1,0);
    }
    private static Vec3 side(Vec3 axis){var r=axis.cross(UP);return r.lengthSqr()<1e-8?EAST:r.normalize();}
    private static Vec3 radial(double a,double radius,double height){return new Vec3(Math.cos(a)*radius,height,Math.sin(a)*radius);}
    private static double ease(double f){double s=Math.clamp(f,0,1);return s*s*(3-2*s);}
}
