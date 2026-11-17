package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.HumNpc;
import cn.academy.biohum.Payloads;
import cn.academy.biohum.Skills;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Playback from real gameplay cues. The recorder hears this same path. */
public final class CombatAudio {
    private record Key(UUID actor,String skill,long cast,Payloads.Cue cue) {}
    private record Candidate(Key key,String sound,Vec3 at,float volume,float pitch,int priority,long until) {}
    private record Voice(SimpleSoundInstance sound,int priority) {}
    private static final Map<String,Map<String,String>> MAP=read();
    private static final Map<Key,Long> RECENT=new HashMap<>();
    private static final Map<Key,Candidate> QUEUED=new HashMap<>();
    private static final ArrayList<Voice> ACTIVE=new ArrayList<>();
    private static final Map<UUID,Capsule> CAPSULES=new HashMap<>();
    private static final Map<Payloads.Cast,Charge> CHARGES=new HashMap<>();
    private static ClientLevel world;

    public static void receive(Payloads.SkillCue cue){
        var mc=Minecraft.getInstance();
        resetFor(mc);
        var skill=Skills.get(cue.skill());
        if(world==null||skill==null)return;
        charge(mc,cue,skill);
        String stage=switch(cue.cue()){
            case WINDUP -> "windup";
            case RELEASE -> "release";
            case IMPACT -> "impact";
            case WALL -> "wall";
            case PARRY -> "parry";
            case CANCEL -> "cancel";
            case REFLECT -> "reflect";
            case WHIFF -> "whiff";
            case RECOVER -> "recover";
            case TERRAIN -> "terrain";
            case FIELD -> "field";
            case ILLUSION -> "illusion";
            case MOVE -> skill.action()==Skills.Action.BLINK||skill.action()==Skills.Action.CARRY
                    ||skill.id().equals("aim_share")?"field":"";
            case BEAM -> skill.id().equals("seven_flash")?"recover":"";
        };
        Map<String,String> sounds=MAP.get(skill.id());
        if(sounds==null||stage.isEmpty()||!sounds.containsKey(stage))return;
        var at=switch(cue.cue()){
            case WINDUP,RELEASE,RECOVER,CANCEL,REFLECT,BEAM,TERRAIN -> cue.from();
            default -> cue.to();
        };
        var eye=mc.gameRenderer.getMainCamera().getPosition();
        double distance=eye.distanceTo(at);
        double reach=skill.power()>26||skill.action()==Skills.Action.PHASE_WORLD?50:31;
        if(cue.cue()!=Payloads.Cue.ILLUSION&&distance>reach)return;
        var key=new Key(cue.actor(),cue.skill(),cue.started(),cue.cue());
        long now=world.getGameTime();
        if(RECENT.getOrDefault(key,Long.MIN_VALUE)>now)return;
        int rank=switch(cue.cue()){
            case TERRAIN,ILLUSION,REFLECT,PARRY,IMPACT -> 4;
            case RELEASE,CANCEL,WALL -> 3;
            case WINDUP,FIELD,MOVE -> 2;
            default -> 1;
        };
        if(skill.signature())rank++;
        float gain=switch(cue.cue()){
            case WINDUP,RECOVER,WHIFF -> .67f;
            case RELEASE -> .98f;
            case TERRAIN -> .96f;
            case PARRY,REFLECT -> .97f;
            case IMPACT -> skill.id().endsWith("_flinch")?.32f:.95f;
            case BEAM -> .43f;
            default -> .76f;
        };
        // Keep the next pulse of multi-part skills audible without stacking messages on one collision.
        int gap=cue.cue()==Payloads.Cue.TERRAIN?8:cue.cue()==Payloads.Cue.BEAM?3:cue.cue()==Payloads.Cue.IMPACT?3:2;
        float pitch=cue.cue()==Payloads.Cue.RELEASE||cue.cue()==Payloads.Cue.WINDUP?1
                :.962f+Math.floorMod(cue.actor().hashCode()+Long.hashCode(cue.time()),9)*.009f;
        var next=new Candidate(key,sounds.get(stage),at,gain,pitch,rank,now+gap);
        // Close impacts and high-priority cues share the render/audio onset of their packet.
        // Large volleys still use the priority queue for the remaining voices.
        ACTIVE.removeIf(voice->!mc.getSoundManager().isActive(voice.sound));
        if(ACTIVE.size()<20&&(rank>=4||skill.signature()||distance<=10)){
            play(mc,next);
            RECENT.put(key,next.until);
            return;
        }
        QUEUED.merge(key,next,(a,b)->eye.distanceToSqr(a.at)<=eye.distanceToSqr(b.at)?a:b);
    }

    public static void tick(Minecraft mc){
        resetFor(mc);
        if(world==null)return;
        var manager=mc.getSoundManager();
        long now=world.getGameTime();
        RECENT.values().removeIf(until->until<=now);
        ACTIVE.removeIf(voice->!manager.isActive(voice.sound));
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        CHARGES.entrySet().removeIf(entry->{
            var charge=entry.getValue();
            boolean done=now>charge.until||camera.distanceToSqr(charge.at)>26*26;
            if(done)manager.stop(charge);
            return done;
        });
        if(!QUEUED.isEmpty()){
            var choices=new ArrayList<>(QUEUED.values());
            choices.sort(Comparator.comparingDouble((Candidate c)->camera.distanceToSqr(c.at)/26-c.priority*5)
                    .thenComparing(c->c.key.skill));
            for(int i=0;i<Math.min(10,choices.size());i++){
                var sound=choices.get(i);
                if(ACTIVE.size()>=26){
                    var weaker=ACTIVE.stream().filter(v->v.priority<sound.priority).min(Comparator.comparingInt(Voice::priority));
                    if(weaker.isEmpty())continue;
                    manager.stop(weaker.get().sound);
                    ACTIVE.remove(weaker.get());
                }
                play(mc,sound);
                RECENT.put(sound.key,sound.until);
            }
            QUEUED.clear();
        }
        if(now%8!=0)return;
        var chamberSearch=new net.minecraft.world.phys.AABB(camera,camera).inflate(15);
        Set<UUID> present=new HashSet<>();
        for(var npc:world.getEntitiesOfClass(HumNpc.class,chamberSearch,npc->npc.suspended()&&npc.isAlive())){
            var id=npc.getUUID();
            Vec3 source=npc.position().add(0,.72,0);
            if(camera.distanceToSqr(source)>14*14)continue;
            // The actual building wall or terrain can muffle the pumps.
            var hit=world.clip(new net.minecraft.world.level.ClipContext(camera,source,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,mc.getCameraEntity()));
            boolean obstructed=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                    &&hit.getLocation().distanceToSqr(source)>3.3;
            if(obstructed)continue;
            present.add(id);
            var current=CAPSULES.get(id);
            if(current==null||current.isStopped()){
                current=new Capsule(npc);
                CAPSULES.put(id,current);
                manager.play(current);
            }
            current.targetVolume=.82f;
        }
        CAPSULES.entrySet().removeIf(entry->{
            var loop=entry.getValue();
            if(!present.contains(entry.getKey()))loop.targetVolume=0;
            return loop.isStopped();
        });
    }

    private static void play(Minecraft mc,Candidate sample){
        var p=sample.at;
        boolean privateSound=sample.key.cue==Payloads.Cue.ILLUSION;
        var sound=new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath(BioHum.ID,sample.sound),
                SoundSource.NEUTRAL,sample.volume,sample.pitch,RandomSource.create(sample.key.hashCode()),
                false,0,privateSound?SoundInstance.Attenuation.NONE:SoundInstance.Attenuation.LINEAR,
                privateSound?0:p.x,privateSound?0:p.y,privateSound?0:p.z,privateSound);
        mc.getSoundManager().play(sound);
        ACTIVE.add(new Voice(sound,sample.priority));
    }

    private static void charge(Minecraft mc,Payloads.SkillCue cue,Skills.Skill skill){
        var key=cue.cast();
        var manager=mc.getSoundManager();
        switch(cue.cue()){
            case WINDUP -> {
                String tone=switch(cue.skill()){
                    case "wind_plasma" -> "charge_wind";
                    case "rune_summon","apostles_cross" -> "charge_seal";
                    case "bethlehem_star","othinus_sky_crossbow","adikalika" -> "charge_sky";
                    default -> null;
                };
                if(tone!=null&&CHARGES.size()<6&&!CHARGES.containsKey(key)
                        &&mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(cue.from())<22*22){
                    var loop=new Charge(tone,cue.from(),world.getGameTime()+skill.windup()+3);
                    CHARGES.put(key,loop);
                    manager.play(loop);
                }
            }
            case RELEASE,CANCEL,RECOVER -> {
                var loop=CHARGES.remove(key);
                if(loop!=null)manager.stop(loop);
            }
            default -> {}
        }
    }

    private static final class Charge extends AbstractTickableSoundInstance {
        final Vec3 at;
        final long until;
        Charge(String tone,Vec3 at,long until){
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"space."+tone)),
                    SoundSource.NEUTRAL,RandomSource.create());
            this.at=at;
            this.until=until;
            looping=true;
            volume=.03f;
            x=at.x;y=at.y;z=at.z;
        }
        @Override public void tick(){volume=Math.min(.59f,volume+.044f);}
    }

    private static class Capsule extends AbstractTickableSoundInstance {
        final HumNpc actor;
        float targetVolume=.82f;
        Capsule(HumNpc npc){
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"space.capsule")),
                    SoundSource.AMBIENT,RandomSource.create());
            actor=npc;
            looping=true;
            delay=0;
            volume=.016f;
            move();
        }
        void move(){x=actor.getX();y=actor.getY()+.72;z=actor.getZ();}
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return actor.isAlive()&&actor.suspended();}
        @Override public void tick(){
            if(actor.isRemoved()||!actor.isAlive()||!actor.suspended()||actor.level()!=world)targetVolume=0;
            volume+=(targetVolume-volume)*.24f;
            if(targetVolume==0&&volume<.01f){stop();return;}
            move();
        }
    }

    private static void resetFor(Minecraft mc){
        if(mc.level==world)return;
        for(var loop:CAPSULES.values())mc.getSoundManager().stop(loop);
        for(var loop:CHARGES.values())mc.getSoundManager().stop(loop);
        for(var voice:ACTIVE)mc.getSoundManager().stop(voice.sound);
        CAPSULES.clear();CHARGES.clear();ACTIVE.clear();RECENT.clear();QUEUED.clear();
        world=mc.level;
    }

    private static Map<String,Map<String,String>> read(){
        try(var in=CombatAudio.class.getResourceAsStream("/assets/acabiohum/combat_audio.json")){
            if(in==null)throw new IllegalStateException("HUM combat audio is missing");
            return new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),
                    new TypeToken<Map<String,Map<String,String>>>(){}.getType());
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private CombatAudio(){}
}
