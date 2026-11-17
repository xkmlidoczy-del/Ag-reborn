package cn.academy.biohum;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** NT9's final attacks share the scene's save, real collision and block rollback. */
final class OthinusDuel {
    private enum Kind { MOON, SPEAR, CROSSBOW }

    static Attack moon(HumNpc caster,OthinusRewrite state,Vec3 point,Skills.Skill skill) {
        var level=(ServerLevel)caster.level();
        Vec3 ground=CombatSkills.ground(level,point);
        double ceiling=Math.min(level.getMaxBuildHeight()-3,ground.y+57);
        var above=new Vec3(ground.x,ceiling,ground.z);
        Payloads.cue(caster,skill.id(),Payloads.Cue.MOVE,above,ground,(float)skill.radius());
        return new Attack(Kind.MOON,caster.getUUID(),above,ground,level.getGameTime(),0);
    }

    static Attack spear(HumNpc caster,OthinusRewrite state,LivingEntity target,Skills.Skill skill) {
        var level=(ServerLevel)caster.level();
        var subject=state.subjectEntity(level);
        Vec3 start=CastMotion.release(caster,skill,caster.castYaw());
        LivingEntity victim=subject!=null?subject:target;
        Vec3 end=victim==null?Vec3.atCenterOf(state.center().north(12)):victim.getBoundingBox().getCenter();
        Payloads.cue(caster,skill.id(),Payloads.Cue.MOVE,start,end,5.8f);
        return new Attack(Kind.SPEAR,caster.getUUID(),start,end,level.getGameTime(),0);
    }

    static Attack crossbow(HumNpc caster,OthinusRewrite state,LivingEntity target,Skills.Skill skill) {
        var level=(ServerLevel)caster.level();
        var subject=state.subjectEntity(level);
        LivingEntity victim=subject!=null?subject:target;
        Vec3 end=victim==null?Vec3.atCenterOf(state.center().north(12)):victim.getBoundingBox().getCenter();
        Vec3 sky=Vec3.atCenterOf(state.center()).add(0,74,0);
        sky=new Vec3(sky.x,Math.min(level.getMaxBuildHeight()-2,sky.y),sky.z);
        Payloads.cue(caster,skill.id(),Payloads.Cue.FIELD,sky,sky,68);
        return new Attack(Kind.CROSSBOW,caster.getUUID(),sky,end,level.getGameTime(),0);
    }

    static final class Attack {
        private final Kind kind;
        private final UUID caster;
        private final Vec3 start,end;
        private final long began;
        private int cursor;

        private Attack(Kind kind,UUID caster,Vec3 start,Vec3 end,long began,int cursor){
            this.kind=kind;
            this.caster=caster;
            this.start=start;
            this.end=end;
            this.began=began;
            this.cursor=cursor;
        }

        boolean tick(ServerLevel level,OthinusRewrite state){
            if(!(level.getEntity(caster) instanceof HumNpc npc))return true;
            long age=level.getGameTime()-began;
            return switch(kind){
                case MOON -> moonTick(level,state,npc,age);
                case SPEAR -> spearTick(level,state,npc,age);
                case CROSSBOW -> arrowsTick(level,state,npc,age);
            };
        }

        private boolean moonTick(ServerLevel level,OthinusRewrite state,HumNpc npc,long age){
            if(age<34)return true;
            int radius=(int)Skills.get("othinus_black_moon").radius();
            if(cursor==0){
                Payloads.cue(npc,"othinus_black_moon",Payloads.Cue.IMPACT,end,end,radius);
                var original=state.subjectEntity(level);
                if(original!=null&&original.distanceToSqr(end)<radius*radius)
                    original.hurt(npc.damageSources().indirectMagic(npc,npc),10);
            }
            int diameter=radius*2+1;
            var origin=BlockPos.containing(end);
            int first=cursor;
            BlockPos changed=null;
            int stop=Math.min(diameter*diameter,cursor+68);
            for(;cursor<stop;cursor++){
                int x=cursor%diameter-radius,z=cursor/diameter-radius;
                int square=radius*radius-x*x-z*z;
                if(square<0)continue;
                int depth=(int)(Math.sqrt(square)*.66);
                for(int y=-depth;y<2;y++){
                    var pos=origin.offset(x,y,z);
                    if(state.inside(Vec3.atCenterOf(pos))&&state.carve(level,pos)&&changed==null)changed=pos;
                }
            }
            if(changed!=null&&level.getGameTime()%3==0){
                var impact=Vec3.atCenterOf(changed);
                Payloads.cue(npc,"othinus_black_moon",Payloads.Cue.TERRAIN,impact,impact,3);
            }
            if(cursor!=first)state.setDirty();
            return cursor<diameter*diameter;
        }

        private boolean spearTick(ServerLevel level,OthinusRewrite state,HumNpc npc,long age){
            if(age<12)return true;
            var original=state.subjectEntity(level);
            if(original!=null && OthinusRewrite.guardPrepared(original)
                    && original.getBoundingBox().inflate(.75).clip(start,end).isPresent()
                    && start.distanceToSqr(SkillTrajectory.surface(level,start,end).position())+.01>=start.distanceToSqr(end)){
                state.brokenSpear(level,npc,original.rightHand());
            }else{
                SkillTrajectory.beam(npc,Skills.get("gungnir"),start,end,state.issuer(),false,null,true);
                if(original!=null&&original.getHealth()<=1)original.setHealth(original.getMaxHealth());
            }
            return false;
        }

        private boolean arrowsTick(ServerLevel level,OthinusRewrite state,HumNpc npc,long age){
            int launch=22+cursor*5;
            if(age<launch)return true;
            var original=state.subjectEntity(level);
            Vec3 at=end;
            Vec3 from;
            if(cursor<9){
                double a=cursor*2.399963;
                double spread=cursor%3==0?0:9+(cursor%3)*8;
                at=end.add(Math.cos(a)*spread,0,Math.sin(a)*spread);
                from=start.add(Math.cos(a)*25,0,Math.sin(a)*25);
            }else{
                Vec3 line=end.subtract(npc.position()).normalize();
                from=npc.getEyePosition().subtract(line.scale(19)).add(0,1.3,0);
                at=end;
            }
            Vec3 stop=SkillTrajectory.surface(level,from,at).position();
            String skill="othinus_sky_crossbow";
            boolean guard=cursor<9&&original!=null&&OthinusRewrite.guardPrepared(original)
                    && original.distanceToSqr(at)<9;
            long cast=began-Skills.get(skill).windup();
            UUID subject=original==null?null:original.getUUID();
            if(guard){
                var hand=original.rightHand();
                var blockedEnd=SkillTrajectory.surface(level,hand,hand.add(11,5,9)).position();
                Payloads.cue(level,caster,skill,cast,Payloads.Cue.BEAM,from,hand,.72f,cursor,subject);
                Payloads.cue(level,caster,skill,cast,Payloads.Cue.REFLECT,hand,blockedEnd,1.1f,cursor,subject);
            }else{
                Payloads.cue(level,caster,skill,cast,Payloads.Cue.BEAM,from,stop,cursor==9?1.58f:1.0f,cursor,subject);
                Payloads.cue(level,caster,skill,cast,Payloads.Cue.IMPACT,stop,stop,2.6f,cursor,subject);
                var floor=BlockPos.containing(stop).below();
                boolean cracked=false;
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                    var p=floor.offset(x,0,z);
                    if(x*x+z*z<=5&&state.inside(Vec3.atCenterOf(p))&&WorldEffects.inWorld(level,p))
                        cracked|=state.crack(level,p);
                }
                if(cracked)Payloads.cue(level,caster,skill,cast,Payloads.Cue.TERRAIN,stop,stop,2,cursor,subject);
                if(original!=null&&cursor<9&&original.distanceToSqr(stop)<9){
                    original.invulnerableTime=0;
                    original.hurt(npc.damageSources().indirectMagic(npc,npc),8);
                }
            }
            if(cursor==9){
                state.finishedArrows();
                if(original!=null){
                    original.invulnerableTime=0;
                    original.hurt(npc.damageSources().indirectMagic(npc,npc),Math.max(1,original.getHealth()));
                    original.getNavigation().stop();
                }
            }
            cursor++;
            state.setDirty();
            return cursor<10;
        }

        CompoundTag save(){
            var tag=new CompoundTag();
            tag.putString("Kind",kind.name());
            tag.putUUID("Caster",caster);
            tag.put("Start",OthinusRewrite.vector(start));
            tag.put("End",OthinusRewrite.vector(end));
            tag.putLong("Began",began);
            tag.putInt("Cursor",cursor);
            return tag;
        }

        static Attack read(CompoundTag tag){
            return new Attack(Kind.valueOf(tag.getString("Kind")),tag.getUUID("Caster"),
                    OthinusRewrite.vector(tag.getCompound("Start")),OthinusRewrite.vector(tag.getCompound("End")),
                    tag.getLong("Began"),tag.getInt("Cursor"));
        }
    }

    private OthinusDuel(){}
}
