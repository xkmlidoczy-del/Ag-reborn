package cn.academy.biohum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Takes one threat snapshot per decision and runs the original server cast checks. */
final class CombatTactics {
    private static final Set<String> UTILITY=Set.of("material_body","saint_mercy","aim_track",
            "teleport_rescue","white_wing_flight","guts_guard","protect_takitsubo","frog_doctor_care");
    private static final Set<String> SUPPORT_DEVICES=Set.of("judgment_report","network_triage","enshuu_guidance",
            "ransuu_burst","byouri_switch","coin_ladder","immortal_talisman");
    private static final Set<String> ALLOW_DEFENSIVE_RANGE=Set.of("imagine_breaker","cover","vector_guard");
    private static final Set<String> WIDE=Set.of("wind_plasma","meltdowner_scatter","meltdowner_sweep","curtana_slice");
    private static final Set<String> TERRAIN=Set.of("railgun","wind_plasma","meltdowner",
            "meltdowner_sweep","wounded_beam","meltdowner_scatter","vector_building_throw",
            "iron_sand_lash","iron_sand_colossus","yuisen","curtana_slice","black_wings","white_wings");
    private record Choice(Skills.Skill skill,LivingEntity target,Vec3 point,double score) {}
    private record Situation(HumNpc npc, LivingEntity enemy, ServerLevel world,double range,
                             boolean sight,Skills.Skill enemyCast,boolean projectile,
                             int enemies,HumNpc support) {
        boolean low(){return npc.getHealth()<npc.getMaxHealth()*.55f;}
        boolean attacking(){return CharacterArts.threatenedBy(npc,enemy);}
        boolean remote(){return projectile||enemy instanceof RangedAttackMob||enemyCast!=null&&enemyCast.range()>6;}
        boolean air(){return !enemy.onGround()&&enemy.getBoundingBox().minY>npc.getY()+1.65*npc.phaseScale();}
    }

    static boolean choose(HumNpc npc,LivingEntity enemy) {
        var level=(ServerLevel)npc.level();
        if(npc.controlled())return false;
        if(npc.riposteUntil>level.getGameTime()&&npc.characterId.equals("kanzaki")
                &&CloseCombat.react(npc,enemy))return true;
        if(enemy instanceof HumNpc other&&npc.distanceTo(enemy)<3.5&&CloseCombat.incoming(npc,other)
                &&CloseCombat.guard(npc,enemy))return true;
        boolean shot=level.getEntitiesOfClass(Projectile.class,npc.getBoundingBox().inflate(9),p->{
            if(p.getOwner()==npc||p.getOwner()!=null&&npc.ally(p.getOwner())||p.onGround())return false;
            var v=p.getDeltaMovement();
            if(v.lengthSqr()<.024)return false;
            var toward=npc.getBoundingBox().getCenter().subtract(p.position());
            var closest=p.position().add(v.scale(Math.clamp(toward.dot(v)/v.lengthSqr(),0,9)));
            return npc.getBoundingBox().inflate(.68).contains(closest)
                    &&SkillTrajectory.surface(level,p.position(),closest).block()==null;
        }).size()>0;
        if(!WorldEffects.inWorld(level,enemy.blockPosition()))return false;
        int enemies=level.getEntitiesOfClass(Mob.class,enemy.getBoundingBox().inflate(5),
                other->npc.validTarget(other)&&(other==enemy||other.getTarget()==npc||other.getTarget()==npc.owner()&&npc.owner()!=null)).size();
        var support=level.getEntitiesOfClass(HumNpc.class,npc.getBoundingBox().inflate(10),
                other->other!=npc&&other.isAlive()&&npc.ally(other)
                        &&other.getHealth()<other.getMaxHealth()*.65f).stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        var s=new Situation(npc,enemy,level,npc.distanceTo(enemy),
                npc.hasLineOfSight(enemy),enemy instanceof HumNpc other?Skills.get(other.casting()):null,shot,enemies,support);
        var choices=new ArrayList<Choice>();
        long now=level.getGameTime();
        for(String id:npc.skills()){
            var skill=Skills.get(id);
            if(npc.readyAt(id)>now)continue;
            if(npc.suspended()&&!id.equals("aleister_interlock"))continue;
            if(!skill.combat()&&!UTILITY.contains(id)&&!supportAction(skill))continue;
            if(s.air()&&requiresGround(skill))continue;
            if(CloseCombat.isAttack(skill)&&!id.equals("imagine_breaker")&&!CloseCombat.canReach(npc,enemy,skill))continue;
            double score=score(s,skill);
            if(score<=0)continue;
            LivingEntity target=switch(skill.target()){
                case SELF,POINT->null;
                case ALLY->support;
                case OPTIONAL->skill.action()==Skills.Action.HEAL?support:enemy;
                default->enemy;
            };
            Vec3 point=null;
            if(skill.target()==Skills.Target.POINT){
                if(!s.sight||skill.action()!=Skills.Action.ROUTE&&s.range>skill.range())continue;
                point=skill.action()==Skills.Action.ROUTE?evacuation(npc,enemy):enemy.position();
                if(point==null)continue;
                if(id.equals("rune_relay")){
                    var side=enemy.position().subtract(npc.position()).cross(new Vec3(0,1,0)).normalize();
                    point=npc.position().add(side.scale(3));
                }
            }
            if(target!=null&&target!=support&&s.range>skill.range()
                    &&!ALLOW_DEFENSIVE_RANGE.contains(id))continue;
            if(TERRAIN.contains(id)&&!clearFriendlies(s,skill,point))continue;
            if(skill.action()!=Skills.Action.LEGACY&&skill.action()!=Skills.Action.CLOSE
                    &&!CombatSkills.available(npc,skill,target,point))continue;
            // Vary the chain without randomly selecting a bad action. The server sees the same last resolution.
            if(id.equals(npc.getPersistentData().getString("BioHumResolved")))score-=12;
            if(score>0)choices.add(new Choice(skill,target,point,score));
        }
        choices.sort(Comparator.comparingDouble(Choice::score).reversed().thenComparing(choice->choice.skill.id()));
        for(var c:choices){
            if(Abilities.cast(npc,c.skill.id(),c.target,c.point,null)){
                npc.getPersistentData().putString("BioHumAiSkill",c.skill.id());
                npc.getPersistentData().putLong("BioHumAiSkillAt",now);
                return true;
            }
        }
        return false;
    }

    private static boolean supportAction(Skills.Skill skill){
        return switch(skill.action()){
            case REPORT,ANALYZE,ROUTE,HEAL,FORMATION,NETWORK -> true;
            case DEVICE -> SUPPORT_DEVICES.contains(skill.id());
            default -> false;
        };
    }

    private static Vec3 evacuation(HumNpc npc,LivingEntity enemy){
        if(npc.distanceTo(enemy)>8)return null;
        Vec3 back=npc.position().subtract(enemy.position()).multiply(1,0,1).normalize();
        for(float angle:new float[]{0,.63f,-.63f}){
            var site=Movement.stand(npc,npc.position().add(back.yRot(angle).scale(4)));
            if(site!=null){
                var path=npc.getNavigation().createPath(site,0);
                if(path!=null&&path.canReach())return Vec3.atBottomCenterOf(site);
            }
        }
        return null;
    }

    private static boolean requiresGround(Skills.Skill skill){
        return switch(skill.action()){
            case UPHEAVE, VECTOR_THROW, IRON_COLOSSUS, RITUAL, CROSS, CROSS_ALIGN -> true;
            default -> skill.id().equals("leyline_trap")||skill.id().equals("saint_intercept");
        };
    }

    private static double score(Situation s,Skills.Skill k){
        var n=s.npc;
        var now=s.world.getGameTime();
        double d=s.range;
        boolean occult=s.enemyCast!=null&&(s.enemyCast.prop().startsWith("rite")
                ||s.enemyCast.action()==Skills.Action.RITUAL||s.enemyCast.action()==Skills.Action.VOICE);
        double authored=switch(k.id()){
            case "imagine_breaker" -> s.projectile||s.enemyCast!=null&&d<=3?150:0;
            case "punch" -> s.sight&&d<=2.6?75:0;
            case "spell_intercept","sheol_fear" -> s.sight&&occult?142:0;
            case "sister_plasma_interrupt","plasma_interrupt" -> s.sight&&s.enemyCast!=null
                    &&s.enemyCast.id().equals("wind_plasma")?184:0;
            case "railgun" -> d>=9.5&&s.sight?100:0;
            case "electric_shock" -> s.sight&&d<=12?86:0;
            case "magnetism","electro_magnetic_dash" -> 0;
            case "vector_guard","choker_calculate" -> !CombatSkills.reflects(n)
                    &&(s.remote()||d<8||s.attacking())?158:0;
            case "vector_impact" -> d<=3&&s.sight?105:0;
            case "vector_airlift" -> !s.sight&&d>=7&&d<=30||s.projectile&&d>5?113:0;
            case "vector_headwind" -> s.sight&&d>3&&d<=25?(s.projectile?153:96):0;
            case "wind_plasma" -> s.sight&&d>=12&&(s.enemies>=2||d>24)
                    &&!s.projectile&&s.enemyCast==null&&!s.low()?122:0;
            case "vector_building_throw","iron_sand_colossus","bethlehem_star" -> 0; // player-directed structural changes
            case "meltdowner" -> s.sight&&d>5&&(s.enemies<2||d>17)?103:0;
            case "meltdowner_sweep" -> s.sight&&(s.enemies>=2||d<=7)?99:63;
            case "meltdowner_scatter" -> s.sight&&s.enemies>=2&&d>8?137:0;
            case "dark_matter_wings" -> s.sight&&n.wings().isEmpty()?133:0;
            case "dark_feather_fan" -> s.sight&&d>=5?108:0;
            case "dark_matter_screen" -> s.remote()&&!n.wingsFolded()?163:0;
            case "white_wing_flight" -> !s.sight&&d>7?101:0;
            case "material_body" -> s.low()?171:0;
            case "guts_guard" -> s.projectile&&d<18?146:0;
            case "protect_takitsubo" -> s.support!=null&&s.support.characterId.equals("takitsubo")
                    &&s.enemy.distanceTo(s.support)<5&&n.distanceTo(s.support)>3?133:0;
            case "gunshot" -> s.sight&&d>=3?70:0;
            case "cover" -> s.sight&&d>4&&(s.remote()||s.low())&&now>=n.coverUntil?142:0;
            case "teleport_self" -> d>=5&&!s.sight||s.projectile?116:0;
            case "teleport_carry" -> 0;
            case "teleport_rescue" -> s.support!=null&&s.attacking()&&s.enemy.distanceTo(s.support)<5?140:0;
            case "aim_track" -> s.sight&&s.enemy instanceof HumNpc op&&op.profile().esper()
                    &&!op.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)?124:0;
            case "frog_doctor_care" -> s.support!=null&&n.distanceTo(s.support)<k.range()
                    &&n.hasLineOfSight(s.support)&&d>1.35?147:0;
            case "mental_stop" -> s.sight&&s.enemyCast!=null?142:s.sight?88:0;
            case "mental_retreat" -> s.sight&&d<5.5?125:0;
            case "mental_assist","mental_memory" -> 0;
            case "rune_summon" -> s.sight&&d>=5.5&&!s.projectile?123:0;
            case "rune_relay" -> n.hasFragileFamiliar(s.world)&&s.sight&&d>=4.5?151:0;
            case "flame_sword" -> d<7&&n.flameUntil<=now?134:0;
            case "fire_sever" -> s.sight&&d>=1.5?112:0;
            case "seven_flash" -> s.sight&&d>=3.4?(s.enemy instanceof HumNpc other&&other.characterId.equals("acqua")?118:97):0;
            case "yuisen" -> s.sight&&d<5.8?118:0;
            case "saint_intercept" -> s.sight&&d>=6&&d<=13&&(s.remote()||d>=9)?128:0;
            case "saint_mercy" -> n.getPersistentData().getLong("BioHumMercySuppressed")>now?159:0;
            case "saint_water_guard" -> s.projectile||s.low()?145:0;
            case "holy_right_intercept" -> s.sight&&s.attacking()&&s.remote()?149:0;
            case "holy_right" -> s.sight?112:0;
            case "aleister_interlock" -> s.attacking()?200:0;
            case "aleister_spiritual_rapier" -> s.sight&&d<=4.5&&!CharacterArts.marked(n,s.enemy)?156:0;
            case "aleister_spiritual_trip" -> s.sight&&d>=3.5&&!CharacterArts.marked(n,s.enemy)?(d>=6?122:136):0;
            case "aleister_staff" -> s.sight&&(CharacterArts.marked(n,s.enemy)||d>4.5)
                    ?(CharacterArts.marked(n,s.enemy)?178:66):0;
            case "aleister_karma" -> s.sight&&!s.projectile&&d>=4.3&&(s.enemyCast!=null||d>5.4)?127:0;
            case "vector_air_intercept","iron_sand_intercept" -> s.sight&&(s.air()||s.projectile)?157:0;
            case "iron_sand_deconstruct" -> s.sight&&s.enemy instanceof Construct?170:0;
            case "aim_share","meltdowner_aim_relay" -> s.sight?144:0;
            case "material_counterform" -> s.attacking()&&s.low()?161:0;
            case "ascalon_guard" -> s.attacking()&&d<6?134:0;
            case "holy_right_close","curtana_interpose","thunder_arc_parry","rune_fire_screen" -> s.sight&&d<=k.range()
                    &&(s.attacking()||s.projectile)?156:0;
            case "ars_magna_mute" -> s.sight&&s.enemyCast!=null?161:0;
            case "wonderland_corridor" -> 0;
            case "flight_gear_step" -> s.attacking()&&n.onGround()&&d<8?143:0;
            case "precedence_cut" -> s.sight||SkillTrajectory.priorityCanReach(s.world,n.getEyePosition(),s.enemy.getBoundingBox().getCenter())?93:0;
            case "lotus_wand" -> s.sight||s.attacking()?95:0;
            case "divine_punishment" -> s.attacking()?150:0;
            case "gungnir" -> 0;
            default -> -1;
        };
        if(authored>=0)return authored;
        if(supportAction(k))return switch(k.action()){
            case HEAL -> s.support!=null&&n.hasLineOfSight(s.support)&&n.distanceTo(s.support)<k.range()?133:0;
            case FORMATION -> s.support!=null&&n.distanceTo(s.support)<k.radius()?104:0;
            case ROUTE -> s.attacking()&&s.sight&&d<=7.5&&!CloseCombat.hasAttack(n)?98:0;
            case NETWORK,ANALYZE,REPORT,DEVICE -> s.attacking()&&s.sight&&d>2.4?82:0;
            default -> 0;
        };
        if(k.target()==Skills.Target.ALLY&&s.support==null)return 0;
        if(k.target()==Skills.Target.SELF)return switch(k.action()){
            case ARMOR,SHIELD -> s.attacking()&&(d<6||s.remote())?90:0;
            case FLY,BLINK -> d>6&&!s.sight?80:0;
            default -> 0;
        };
        if(!s.sight)return 0;
        if(k.action()==Skills.Action.PHASE_WORLD||k.action()==Skills.Action.REWRITE||k.action()==Skills.Action.RETURN
                ||k.action()==Skills.Action.DECOY||k.action()==Skills.Action.RITUAL||k.action()==Skills.Action.CARRY)return 0;
        if(k.action()==Skills.Action.SEAL&&s.enemyCast==null
                &&!(s.enemy instanceof FlameFamiliar)&&!(s.enemy instanceof RuneAnchor))return 0;
        if(k.action()==Skills.Action.DISRUPT&&s.enemyCast==null)return 0;
        double score=37+(k.signature()?8:0)+Math.min(24,k.power()*.8)+Math.min(5,s.enemies)*2
                -k.windup()*(d<4?.82:.47);
        if(k.range()>0&&d>k.range()*.87)score-=8;
        return score;
    }

    private static boolean clearFriendlies(Situation s,Skills.Skill skill,Vec3 point){
        Vec3 from=s.npc.getEyePosition();
        Vec3 aim=point==null?s.enemy.getBoundingBox().getCenter():point;
        Vec3 delta=aim.subtract(from);
        Vec3 end=from.add(delta.normalize().scale(skill.range()));
        if(skill.id().equals("wind_plasma"))end=SkillTrajectory.surface(s.world,from,end).position();
        double footprint=WIDE.contains(skill.id())
                ? Math.max(.75,skill.radius()):.45;
        for(var ally:s.world.getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(footprint),
                other->other!=s.npc&&s.npc.ally(other)&&other.isAlive())){
            if(ally.getBoundingBox().inflate(footprint).clip(from,end).isPresent()
                    ||ally.getBoundingBox().inflate(footprint).contains(end))return false;
        }
        return true;
    }

    static final class Movement {
        private UUID target;
        private Vec3 origin,checkpoint;
        private long lastProgress;
        private boolean pursuing;
        private int sample;

        void reset(){target=null;origin=null;checkpoint=null;pursuing=false;sample=0;}

        void progress(HumNpc npc,LivingEntity enemy){
            long now=npc.level().getGameTime();
            if(!enemy.getUUID().equals(target)){
                target=enemy.getUUID();
                origin=checkpoint=npc.position();
                lastProgress=now;
                sample=0;
            }else if(!pursuing||npc.position().distanceToSqr(checkpoint)>.36*.36){
                checkpoint=npc.position();
                lastProgress=now;
            }
        }

        void move(HumNpc npc,LivingEntity enemy){
            var level=(ServerLevel)npc.level();
            if(npc.suspended()||level.getGameTime()<npc.coverUntil)return;
            if(!npc.onGround()&&!npc.isInWater())return;
            double range=npc.distanceTo(enemy);
            double preferred=preferred(npc);
            boolean sight=npc.hasLineOfSight(enemy);
            boolean airborne=!enemy.onGround()&&enemy.getBoundingBox().minY>npc.getY()+1.65*npc.phaseScale();
            boolean civilian=!CloseCombat.hasAttack(npc)&&npc.skills().stream().map(Skills::get)
                    .noneMatch(skill->skill.combat()&&skill.target()!=Skills.Target.SELF);
            if(civilian)preferred=Math.max(preferred,6);
            boolean retreat=range<preferred*(civilian?.93:.56)&&(preferred>3||airborne);
            boolean pursue=!civilian&&(!sight||range>preferred+(preferred<=3?.06:3.6));
            if(airborne&&preferred<=3){
                retreat=range<6&&sight;
                pursue=false;
            }
            if(!retreat&&!pursue){
                pursuing=false;
                npc.getNavigation().stop();
                return;
            }
            if(!pursuing){pursuing=true;lastProgress=level.getGameTime();}
            long stalled=level.getGameTime()-lastProgress;
            if(stalled>=60){
                var stay=npc.owner()!=null&&npc.mode()!=2?npc.owner().position():origin;
                var safe=stand(npc,stay);
                npc.getNavigation().stop();
                npc.unreachableTarget=enemy.getUUID();
                npc.unreachableUntil=level.getGameTime()+70;
                npc.setTarget(null);
                npc.retreatTo=safe;
                npc.getPersistentData().putLong("BioHumUnreachableAt",level.getGameTime());
                pursuing=false;
                return;
            }
            if(stalled<20&&!npc.getNavigation().isDone())return;
            Vec3 front=enemy.position().subtract(npc.position()).multiply(1,0,1).normalize();
            if(front.lengthSqr()<.01)front=Vec3.directionFromRotation(0,npc.getYRot());
            Vec3 side=new Vec3(-front.z,0,front.x);
            Vec3 center=retreat?npc.position().subtract(front.scale(civilian?5:3.6)):enemy.position().subtract(front.scale(preferred));
            if(airborne)center=new Vec3(center.x,npc.getY(),center.z);
            int sign=(npc.getId()&1)==0?1:-1;
            for(int i=0;i<3;i++){
                int candidate=(sample+i)%9;
                int lateral=(candidate+1)/2*(candidate%2==1?sign:-sign);
                Vec3 at=center.add(side.scale(lateral*(sight?1.25:2.0)));
                var floor=stand(npc,at);
                if(floor==null)continue;
                var path=path(npc,floor);
                if(path==null||!path.canReach())continue;
                if(npc.getNavigation().moveTo(path,retreat?1.12:1.06)){
                    if(stalled>=20||!sight)npc.getPersistentData().putLong("BioHumRoutedAt",level.getGameTime());
                    break;
                }
            }
            sample=(sample+3)%9;
        }

        private static double preferred(HumNpc npc){
            if(npc.controlled())return 1.08;
            return switch(npc.characterId){
                case "mugino","mikoto","kakine","fiamma","crc" -> 13;
                case "stiyl" -> npc.flameUntil>npc.level().getGameTime()?1.46:4;
                case "hamazura" -> npc.getOffhandItem().isEmpty()?1.4:8.5;
                case "takitsubo","index","aleister" -> 8;
                case "misaki","accelerator","misaka_10032","misaka_worst" -> 6.5;
                default -> Math.max(1.04,CloseCombat.reach(npc)*.84);
            };
        }

        private static Path path(HumNpc npc,BlockPos at){return npc.getNavigation().createPath(at,0);}

        private static BlockPos stand(HumNpc npc,Vec3 at){
            var level=(ServerLevel)npc.level();
            var pos=BlockPos.containing(at);
            for(int y:new int[]{0,1,-1,2,-2}){
                var floor=pos.above(y);
                if(WorldEffects.inWorld(level,floor)&&Abilities.safeDestination(npc,Vec3.atBottomCenterOf(floor)))return floor;
            }
            return null;
        }
    }

    private CombatTactics(){}
}
