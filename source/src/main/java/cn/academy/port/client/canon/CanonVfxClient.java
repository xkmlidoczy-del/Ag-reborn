package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.canon.CanonVfxDesign;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** Client-only bounded particle geometry for server-confirmed canon skill events. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonVfxClient {
    private static final int MAX_EFFECTS=24, MAX_PARTICLES_PER_TICK=48;
    private static final List<Effect> EFFECTS=new ArrayList<>();
    private static ClientLevel world;
    /** Persistent areas and enhanced receipt clocks track game time through any impact freeze. */
    private record Effect(CanonVfx.Visual visual,CanonVfxDesign.Design design,Vec3 right,Vec3 up,Vec3 forward,Vec3 ownerStart,
                          net.minecraft.world.entity.Entity trackedTarget,double started,int life,boolean gameTime) {}
    private CanonVfxClient() {}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->CanonVfx.clientReceiver=CanonVfxClient::receive);}
    }
    private static void receive(CanonVfx.Visual v) { receive(v,true); }
    private static void receive(CanonVfx.Visual v,boolean serverConfirmed) {
        Minecraft mc=Minecraft.getInstance(); var level=mc.level;
        if(level!=world){EFFECTS.clear();PENDING_FLOAT_BINDINGS.clear();PENDING.clear();CanonMeshRenderer.clear();CanonFx.clear();CanonUltimates.clear();world=level;}
        if(level==null||mc.player==null||v==null||v.owner()==null||v.dimension()==null||v.phase()==null
                ||!level.dimension().location().equals(v.dimension())||v.duration()<1||v.duration()>CanonVfx.maximumDuration(v.skill())
                ||!finite(v.origin())||!finite(v.endpoint())||!finite(v.axis())||!finite(v.areaCentre())||Math.abs(v.axis().lengthSqr()-1)>.01||!Float.isFinite(v.radius())||v.radius()<0||v.radius()>CanonVfx.maximumRadius(v.skill())||v.origin().distanceToSqr(v.endpoint())>CanonVfx.maximumSpan(v.skill())*CanonVfx.maximumSpan(v.skill())
                ||v.origin().distanceToSqr(v.areaCentre())>CanonVfx.maximumSpan(v.skill())*CanonVfx.maximumSpan(v.skill())
                ||Math.min(cn.academy.port.macro.MacroVfx.distanceSquared(mc.player.getEyePosition(),v.origin(),v.endpoint()),v.radius()>0?mc.player.getEyePosition().distanceToSqr(v.areaCentre()):Double.POSITIVE_INFINITY)>Math.pow(72+v.radius(),2))return;
        var design=CanonVfxDesign.find(v.skill());if(design==null)return;
        Vec3 direction=v.axis();
        var entity=level.getEntity(v.entity());
        if(entity!=null&&!entity.getUUID().equals(v.owner()))return;
        if(direction.lengthSqr()<1e-6) direction=entity==null?new Vec3(0,0,1):entity.getViewVector(1);
        if(!finite(direction)||direction.lengthSqr()<1e-6) direction=new Vec3(0,0,1);
        Vec3 forward=direction.normalize(),right=forward.cross(new Vec3(0,1,0));
        if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);else right=right.normalize();
        Vec3 up=right.cross(forward).normalize();
        EFFECTS.removeIf(e->e.visual.owner().equals(v.owner()) && e.visual.skill().equals(v.skill())
                && (v.phase()==CanonVfx.Phase.START || v.phase()==CanonVfx.Phase.END));
        if(EFFECTS.size()>=MAX_EFFECTS)EFFECTS.remove(0);
        int life=v.phase()==CanonVfx.Phase.END?Math.max(8,v.duration()):v.duration();
        boolean gameTime = design.category().equals("mental_out")&&(v.phase()==CanonVfx.Phase.ACTIVE||v.duration()>200) || "hydro_domain".equals(v.skill()) || CanonFxLiquid.FIELD_SKILL.equals(v.skill())
                || design.category().equals("float_dial") && CanonFxFloat.role(v.skill()) == CanonFxFloat.Role.FIELD
                || CanonReceiptFx.replacesLegacy(v.skill());
        var effect=new Effect(v,design,right,up,forward,entity==null?null:entity.position(),cn.academy.port.canon.CanonOffenseSkills.find(v.skill())!=null?offenseTarget(level,v,design):trackedTarget(level,v,design),
                effectTime(gameTime,level.getGameTime()),life,gameTime);
        EFFECTS.add(effect);
        if(cn.academy.port.canon.CanonOffenseSkills.find(v.skill())==null&&effect.trackedTarget==null&&design.category().equals("float_dial")&&CanonFxFloat.role(v.skill())!=CanonFxFloat.Role.FIELD
                &&(v.phase()==CanonVfx.Phase.START||v.phase()==CanonVfx.Phase.HIT)&&trackedFloatCandidates(level,v).isEmpty())
            PENDING_FLOAT_BINDINGS.put(effect,3);
        if(serverConfirmed)CanonPoseClient.accept(v);
        CanonFxForms.onReceive(v,design.category(),tier(design.category(),v.skill()),entity!=null&&entity==mc.player,life);
        CanonEnvironment.canon(v,design.category(),tier(design.category(),v.skill()),entity==null?null:entity.position());
        if(CanonUltimates.ultimate(v.skill()))CanonUltimates.receive(v,entity!=null&&entity==mc.player,level.getGameTime());
        if(v.phase()==CanonVfx.Phase.START||v.phase()==CanonVfx.Phase.HIT)
            level.playLocalSound(v.origin().x,v.origin().y,v.origin().z,sound(design.category(),v.phase()==CanonVfx.Phase.HIT),SoundSource.PLAYERS,
                    .55f,.94f+(v.skill().hashCode()&7)*.025f,false);
    }
    private record Pending(CanonVfx.Visual visual,long at) {}
    /** The server supplies the frozen centre. Only explicitly maintained personal fields follow the caster. */
    private static Vec3 rangeCentre(CanonVfx.Visual v,Vec3 feet){
        if(v.radius()<=0)return null;
        if(java.util.Set.of("nitrogen_dome","dark_construct").contains(v.skill()))return feet;
        return v.areaCentre();
    }
    /** Only bind an unambiguous living target at the confirmed point; fields keep their frozen centre. */
    private static LivingEntity offenseTarget(ClientLevel level,CanonVfx.Visual v,CanonVfxDesign.Design design){
        if(v.radius()>0||!java.util.Set.of("mental_out","float_dial").contains(design.category())||v.phase()==CanonVfx.Phase.END)return null;
        boolean mental=design.category().equals("mental_out");
        var candidates=level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(v.endpoint(),v.endpoint()).inflate(1.2,2,1.2),
                e->e.getId()!=v.entity()&&e.isAlive()&&!e.isRemoved());
        java.util.function.ToDoubleFunction<LivingEntity> distance=e->(mental?e.getEyePosition():e.getBoundingBox().getCenter()).distanceToSqr(v.endpoint());
        candidates.removeIf(e->distance.applyAsDouble(e)>.8*.8);candidates.sort(java.util.Comparator.comparingDouble(distance));
        if(candidates.isEmpty()||candidates.size()>1&&distance.applyAsDouble(candidates.get(1))-distance.applyAsDouble(candidates.getFirst())<.05)return null;
        return candidates.getFirst();
    }
    /** Bind a target visual once to the unique entity at its server point; never reselect a nearby bystander as it moves. */
    private static net.minecraft.world.entity.Entity trackedTarget(ClientLevel level,CanonVfx.Visual v,CanonVfxDesign.Design design){
        boolean mental=design.category().equals("mental_out");
        if(mental){
            if(CanonFxMental.role(v.skill())==CanonFxMental.Role.FIELD&&!CanonFxMental.targetPointField(v.skill()))return null;
            if(CanonFxMental.role(v.skill())==CanonFxMental.Role.SCAN&&!java.util.Set.of("mental_probe","mental_intent_scan","mental_target_sense").contains(v.skill()))return null;
        }else {
            if(!design.category().equals("float_dial")||CanonFxFloat.role(v.skill())==CanonFxFloat.Role.FIELD)return null;
            return trackedFloatTarget(level,v);
        }
        net.minecraft.world.entity.Entity best=null;double distance=.75*.75;boolean ambiguous=false;
        boolean items=v.skill().startsWith("float_item_");
        for(var candidate:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(v.endpoint(),v.endpoint()).inflate(.8,2,.8),
                e->e.isAlive()&&!e.isRemoved()&&(!mental||e.getId()!=v.entity())
                        &&(items?e instanceof net.minecraft.world.entity.item.ItemEntity:e instanceof LivingEntity))){
            double d=(mental?candidate.getBoundingBox().getCenter():candidate.position()).distanceToSqr(v.endpoint());
            // Mental scans report a centre, while control ACTIVE packets may
            // report the same target's feet. Associate either documented
            // control point once; retain the unique-candidate/actor checks.
            if(mental&&CanonFxMental.role(v.skill())!=CanonFxMental.Role.SCAN)
                d=Math.min(d,candidate.position().distanceToSqr(v.endpoint()));
            if(d>.75*.75)continue;
            if(best!=null)ambiguous=true;
            if(d<distance){best=candidate;distance=d;}
        }
        return ambiguous?null:best;
    }

    /** The received move destination can lead a falling ally's interpolated render position. */
    private static net.minecraft.world.entity.Entity trackedFloatTarget(ClientLevel level,CanonVfx.Visual v){
        var matches=trackedFloatCandidates(level,v);
        return matches.size()==1?matches.getFirst():null;
    }
    private static List<net.minecraft.world.entity.Entity> trackedFloatCandidates(ClientLevel level,CanonVfx.Visual v){
        var matches=new ArrayList<net.minecraft.world.entity.Entity>(2);
        boolean items=v.skill().startsWith("float_item_");
        for(var candidate:level.entitiesForRendering()){
            if(!candidate.isAlive()||candidate.isRemoved()
                    ||!(items?candidate instanceof net.minecraft.world.entity.item.ItemEntity:candidate instanceof LivingEntity))continue;
            Vec3 networkPoint=new Vec3(candidate.lerpTargetX(),candidate.lerpTargetY(),candidate.lerpTargetZ());
            double d=candidate.position().distanceToSqr(v.endpoint());
            if(finite(networkPoint))d=Math.min(d,networkPoint.distanceToSqr(v.endpoint()));
            // Keep the original tight radius and ambiguity rejection. This is
            // a real received position, never velocity extrapolation or a wider
            // search for an unrelated entity. Binding still happens only once.
            if(d>.75*.75)continue;
            matches.add(candidate);
            if(matches.size()==2)return matches;
        }
        return matches;
    }
    /** VFX can precede the same server tick's entity position packet. Settle briefly, then bind once. */
    private static void settleFloatTargets(){
        PENDING_FLOAT_BINDINGS.keySet().removeIf(e->EFFECTS.stream().noneMatch(active->active==e));
        for(int i=0;i<EFFECTS.size();i++){
            Effect e=EFFECTS.get(i);Integer remaining=PENDING_FLOAT_BINDINGS.get(e);if(remaining==null)continue;
            var matches=trackedFloatCandidates(world,e.visual);
            if(matches.size()==1){
                EFFECTS.set(i,new Effect(e.visual,e.design,e.right,e.up,e.forward,e.ownerStart,matches.getFirst(),e.started,e.life,e.gameTime));
                PENDING_FLOAT_BINDINGS.remove(e);
            }else if(matches.size()>1||remaining<=1)PENDING_FLOAT_BINDINGS.remove(e);
            else PENDING_FLOAT_BINDINGS.put(e,remaining-1);
        }
    }
    private static final List<Pending> PENDING=new ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=world){EFFECTS.clear();PENDING_FLOAT_BINDINGS.clear();PENDING.clear();CanonMeshRenderer.clear();CanonFx.clear();CanonUltimates.clear();world=mc.level;}
        if(!PENDING.isEmpty()&&Minecraft.getInstance().level!=null){long at=Minecraft.getInstance().level.getGameTime();
            for(var p:List.copyOf(PENDING))if(at>=p.at()){PENDING.remove(p);receive(p.visual(),false);}}
        if(world==null||mc.player==null||mc.isPaused())return;
        EFFECTS.removeIf(e -> {
            double now = effectTime(e.gameTime, world.getGameTime());
            return now < e.started - 1 || now - e.started >= e.life;
        });
        settleFloatTargets();
    }
    @SubscribeEvent public static void mesh(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();if(world==null||world!=mc.level||mc.player==null)return;
        if(EFFECTS.isEmpty()){CanonFx.render(event,List.of());return;} // particles and ground traces outlive their effect
        double partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var views=new ArrayList<CanonMeshRenderer.View>();var fx=new ArrayList<CanonFx.View>();
        for(Effect e:List.copyOf(EFFECTS)){
            var motif=e.design.motif();
            var entity=world.getEntity(e.visual.entity());
            if(!(entity instanceof LivingEntity actor)||!actor.getUUID().equals(e.visual.owner())||actor.isRemoved())continue;
            if(e.trackedTarget!=null&&(e.trackedTarget.level()!=world||e.trackedTarget.isRemoved()||!e.trackedTarget.isAlive()))continue;
            Vec3 trackedPosition=e.trackedTarget!=null?e.trackedTarget.getPosition((float)partial):null;
            var targetBox=e.trackedTarget==null?null:e.trackedTarget.getBoundingBox().move(trackedPosition.subtract(e.trackedTarget.position()));
            var offense=cn.academy.port.canon.CanonOffenseSkills.find(e.visual.skill());
            Vec3 currentEndpoint=e.trackedTarget==null?e.visual.endpoint():offense!=null
                    ?e.design.category().equals("mental_out")?trackedPosition.add(0,((LivingEntity)e.trackedTarget).getEyeHeight(),0):targetBox.getCenter()
                    :e.design.category().equals("mental_out")?targetBox.getCenter():trackedPosition;
            // Systems on FX4 draw their own shells, tendrils and wings; only real paper structures keep the flat body.
            boolean placedPaper=CanonReceiptFx.paperPlacement(e.visual.skill());
            boolean solid=!placedPaper&&keepsSolidMesh(motif,e.visual.skill())
                    &&(!CanonFxForms.upgraded(e.design.category())||motif==CanonVfxDesign.Motif.PAPER_CONSTRUCT);
            boolean body=motif==CanonVfxDesign.Motif.DARK_WINGS||motif==CanonVfxDesign.Motif.NITROGEN_SHELL
                    ||motif==CanonVfxDesign.Motif.NITROGEN_PUNCH||motif==CanonVfxDesign.Motif.PAPER_WING;
            boolean endpoint=motif==CanonVfxDesign.Motif.FLOAT_LIFT||motif==CanonVfxDesign.Motif.FLOAT_RESCUE
                    ||motif==CanonVfxDesign.Motif.FLOAT_FIELD||motif==CanonVfxDesign.Motif.LIQUID_SCOUT
                    ||(motif==CanonVfxDesign.Motif.LIQUID_FIELD&&!e.visual.skill().startsWith("liquid_contact_"))||(motif==CanonVfxDesign.Motif.PAPER_CONSTRUCT
                        &&e.visual.origin().distanceToSqr(e.visual.endpoint())<.1);
            if(offense!=null){body=false;endpoint=false;}
            Vec3 origin=body?actor.getPosition((float)partial):endpoint?currentEndpoint:e.visual.origin();
            if(offense!=null&&!offense.body()&&e.visual.phase()==CanonVfx.Phase.START&&e.ownerStart!=null)
                origin=origin.add(actor.getPosition((float)partial).subtract(e.ownerStart));
            if(motif==CanonVfxDesign.Motif.PAPER_CONSTRUCT)origin=origin.add(0,-.5,0);
            if(motif==CanonVfxDesign.Motif.AERO_JET)origin=origin.add(0,.25,0);
            if(!finite(origin)||!world.hasChunkAt(BlockPos.containing(origin)))continue;
            float yaw=Mth.rotLerp((float)partial,actor.yBodyRotO,actor.yBodyRot);
            Vec3 forward=(body||endpoint)&&motif!=CanonVfxDesign.Motif.NITROGEN_PUNCH?Vec3.directionFromRotation(0,yaw):e.forward;
            if(motif==CanonVfxDesign.Motif.AERO_JET){Vec3 horizontal=forward.multiply(1,0,1);
                forward=horizontal.lengthSqr()>1e-6?horizontal.normalize():Vec3.directionFromRotation(0,yaw);}
            Vec3 right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);else right=right.normalize();
            double age=effectTime(e.gameTime,world.getGameTime()+partial)-e.started;if(age<0||age>=e.life)continue;
            double length=Math.sqrt(e.visual.origin().distanceToSqr(e.visual.endpoint()));
            if(motif==CanonVfxDesign.Motif.AERO_JET)length=Math.max(1.8,length);
            if(solid)views.add(new CanonMeshRenderer.View(e.visual.skill(),motif,origin,right,new Vec3(0,1,0),forward,e.visual.phase().ordinal(),
                    age,e.life,length,actor==mc.player));
            // Self-cast (target at or next to the caster): centre everything on the caster's feet so area and lift effects sit on the body.
            Vec3 feet=actor.getPosition((float)partial);
            // Dark blades/volleys use a server-confirmed target or item point,
            // including targets within melee range. Keep that segment even
            // when its endpoint is close to the owner.
            boolean darkTarget=e.design.category().equals("dark_matter")
                    &&(CanonFxDark.role(e.visual.skill())==CanonFxDark.Role.BLADE
                    ||CanonFxDark.role(e.visual.skill())==CanonFxDark.Role.VOLLEY);
            boolean barrier=cn.academy.port.canon.CanonExpansionCombatA.physicalBarrier(e.visual.skill());
            Vec3 barrierAxis=null,barrierCentre=null;
            if(barrier){
                Effect start=e;
                if(e.visual.phase()!=CanonVfx.Phase.START)for(Effect candidate:EFFECTS)if(candidate.visual.owner().equals(e.visual.owner())&&candidate.visual.skill().equals(e.visual.skill())&&candidate.visual.phase()==CanonVfx.Phase.START){start=candidate;break;}
                barrierAxis=start.forward;
                Vec3 eye=e.visual.skill().equals("nitrogen_shield_line")?start.visual.origin():feet.add(0,actor.getEyeHeight(),0);
                barrierCentre=cn.academy.port.canon.CanonExpansionCombatA.frontalBarrier(e.visual.skill())?eye.add(barrierAxis.scale(cn.academy.port.canon.CanonExpansionCombatA.barrierDistance(e.visual.skill()))):eye;
            }
            boolean targetAnchored=offense!=null||placedPaper||barrier||e.visual.phase()==CanonVfx.Phase.HIT||cn.academy.port.core.RangedSkillRanges.find(e.visual.skill())!=null||e.design.category().equals("mental_out")||motif==CanonVfxDesign.Motif.AERO_JET
                    ||darkTarget||e.visual.skill().equals("nitrogen_ally_guard")||e.visual.skill().equals("dark_wing_shelter")
                    ||e.trackedTarget!=null&&e.trackedTarget!=actor;
            boolean self=!targetAnchored&&(currentEndpoint.distanceToSqr(feet)<2.5*2.5||currentEndpoint.distanceToSqr(e.visual.origin())<1.5*1.5);
            fx.add(new CanonFx.View(e.visual.skill(),e.design.category(),motif,self?feet.add(0,1.2,0):offense!=null?origin:e.visual.origin(),self?feet:currentEndpoint,feet,yaw,
                    e.visual.phase().ordinal(),age,e.life,actor==mc.player,tier(e.design.category(),e.visual.skill()),targetBox,
                    e.visual.radius(),rangeCentre(e.visual,feet),barrierCentre,barrierAxis!=null?barrierAxis:e.visual.axis()));
        }
        CanonMeshRenderer.render(event,views);CanonFx.render(event,fx);
    }
    private static double effectTime(boolean gameTime, double now) {
        return gameTime ? now : CanonCamera.fxNow(now);
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static boolean attachesToOwner(CanonVfxDesign.Motif motif){return switch(motif){
        case DARK_WINGS,DARK_CONSTRUCT,NITROGEN_SHELL,NITROGEN_DOME,FLOAT_RESCUE,PAPER_ARMOR,PAPER_WING -> true;
        default -> false;
    };}
    /** Developer-only visual fixture. It does not cast a skill or prove server delivery. */
    @SubscribeEvent public static void qaCommands(RegisterClientCommandsEvent event){
        if(!Boolean.getBoolean("academy.visual.qa"))return;
        var root=Commands.literal("aca_canon_vfx");
        for(String skill:CanonVfxDesign.all().keySet()){
            var name=Commands.literal(skill);
            for(CanonVfx.Phase phase:CanonVfx.Phase.values())
                name.then(Commands.literal(phase.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx->preview(skill,phase,false,60))
                        .then(Commands.literal("self").executes(ctx->preview(skill,phase,true,60)))
                        // Real casts last 10-30 ticks; "<ticks>" previews that life and starts 8 ticks later so a capture script can catch frame 1.
                        .then(Commands.argument("ticks",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,200))
                                .executes(ctx->preview(skill,phase,false,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"ticks")))));
            root.then(name);
        }
        event.getDispatcher().register(root);
    }
    private static int preview(String skill,CanonVfx.Phase phase,boolean self,int ticks){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return 0;
        Vec3 view=mc.player.getViewVector(1),direction=new Vec3(view.x*.342-view.z*.940,0,view.x*.940+view.z*.342).normalize(),origin=mc.player.position().add(0,1.2,0).add(direction.scale(.6));
        Vec3 end=self?mc.player.position():origin.add(direction.scale(3.2));if(self)origin=mc.player.position().add(0,1.2,0);
        float radius=0;
        if(CanonFxLiquid.FIELD_SKILL.equals(skill)&&phase==CanonVfx.Phase.HIT){
            origin=origin.subtract(0,1.2,0);end=end.subtract(0,1.2,0);
            radius=(float)cn.academy.port.core.SkillTuning.number(skill,"radius");
        }else if(java.util.Set.of("hydro_domain","float_field","nitrogen_dome","aero_air_curtain","dark_panel_dome").contains(skill)
                &&phase==CanonVfx.Phase.START){
            radius=(float)cn.academy.port.core.SkillTuning.number(skill,"radius");
        }
        var visual=new CanonVfx.Visual(mc.player.getId(),mc.player.getUUID(),mc.level.dimension().location(),skill,origin,end,phase,ticks,radius);
        if(ticks==60)receive(visual,false);else PENDING.add(new Pending(visual,mc.level.getGameTime()+8));
        mc.player.displayClientMessage(Component.literal("Visual fixture: "+skill+" / "+phase+" (no skill cast)"),false);return 1;
    }
    private static final java.util.Map<String,Integer> TIERS=new java.util.HashMap<>();
    /** Skill level 1..5 from the shared catalogue; drives how elaborate the effect is. */
    private static int tier(String category,String skill){
        return TIERS.computeIfAbsent(skill,k->cn.academy.port.SkillCatalog.find(category,skill).map(s->s.level()).orElse(1));
    }
    /** Flat bodies stay only where they stand for something physical: wings/armour shells, real paper structures, liquid contact hands. */
    private static boolean keepsSolidMesh(CanonVfxDesign.Motif motif,String skill){
        return switch(motif){
            case NITROGEN_SHELL,NITROGEN_PUNCH,PAPER_CONSTRUCT -> true; // Dark Wings are drawn by the feather layer only
            case LIQUID_STRIKE,LIQUID_FIELD -> skill.startsWith("liquid_contact_")||skill.equals("liquid_grapple");
            default -> false;
        };
    }
    /** Original synthesised sounds (scripts/build-canon-fx-sounds.py); nothing borrowed from the base game. */
    private static SoundEvent sound(String category,boolean hit){
        String key=switch(category){
            case "mental_out" -> "mental"; case "dark_matter" -> "dark"; case "nitrogen_armor" -> "nitrogen"; case "aero_hand" -> "aero";
            case "hydro_hand" -> "hydro"; case "float_dial" -> "float"; case "liquid_shadow" -> "liquid"; default -> "paper";
        };
        return SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","canon."+key+(hit?".hit":".start")));
    }
    private static final java.util.Map<Effect,Integer> PENDING_FLOAT_BINDINGS=new java.util.IdentityHashMap<>();
}
